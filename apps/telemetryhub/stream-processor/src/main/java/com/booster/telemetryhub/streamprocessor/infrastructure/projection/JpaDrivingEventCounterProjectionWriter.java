package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.application.projection.DrivingEventCounterProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class JpaDrivingEventCounterProjectionWriter
        extends BufferedJdbcProjectionWriter<DrivingEventCounterAggregate, String>
        implements DrivingEventCounterProjectionWriter {

    private static final String UPSERT_SQL = """
            insert into telemetryhub_driving_event_counter
                (id, device_id, driving_event_type, minute_bucket_start, event_count, created_at, updated_at)
            values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
            on conflict (device_id, driving_event_type, minute_bucket_start) do update
                set event_count = excluded.event_count,
                    updated_at = current_timestamp
            """;

    public JpaDrivingEventCounterProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector,
            StreamProcessorProperties properties
    ) {
        super(jdbcTemplate, metricsCollector, properties, ProjectionType.DRIVING_EVENT_COUNTER);
    }

    @Override
    public void upsert(DrivingEventCounterAggregate aggregate) {
        upsertBuffered(aggregate);
    }

    @Override
    protected String bufferKey(DrivingEventCounterAggregate aggregate) {
        return aggregate.deviceId() + ":" + aggregate.drivingEventType().name() + ":" + aggregate.minuteBucketStart();
    }

    @Override
    protected DrivingEventCounterAggregate mergeAggregates(
            DrivingEventCounterAggregate left,
            DrivingEventCounterAggregate right
    ) {
        return right.count() >= left.count() ? right : left;
    }

    @Override
    protected void batchUpsert(JdbcTemplate jdbcTemplate, List<DrivingEventCounterAggregate> aggregates) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                aggregates,
                aggregates.size(),
                this::bindStatement
        );
    }

    private void bindStatement(PreparedStatement statement, DrivingEventCounterAggregate aggregate) throws SQLException {
        statement.setLong(1, com.booster.common.SnowflakeGenerator.nextId());
        statement.setString(2, aggregate.deviceId());
        statement.setString(3, aggregate.drivingEventType().name());
        statement.setTimestamp(4, Timestamp.from(aggregate.minuteBucketStart()));
        statement.setLong(5, aggregate.count());
    }
}
