package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.application.projection.EventsPerMinuteProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class JpaEventsPerMinuteProjectionWriter
        extends BufferedJdbcProjectionWriter<EventsPerMinuteAggregate, String>
        implements EventsPerMinuteProjectionWriter {

    private static final String UPSERT_SQL = """
            insert into telemetryhub_events_per_minute
                (id, event_type, minute_bucket_start, event_count, created_at, updated_at)
            values (?, ?, ?, ?, current_timestamp, current_timestamp)
            on conflict (event_type, minute_bucket_start) do update
                set event_count = excluded.event_count,
                    updated_at = current_timestamp
            """;

    public JpaEventsPerMinuteProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector,
            StreamProcessorProperties properties
    ) {
        super(jdbcTemplate, metricsCollector, properties, ProjectionType.EVENTS_PER_MINUTE);
    }

    @Override
    public void upsert(EventsPerMinuteAggregate aggregate) {
        upsertBuffered(aggregate);
    }

    @Override
    protected String bufferKey(EventsPerMinuteAggregate aggregate) {
        return aggregate.eventType().name() + ":" + aggregate.minuteBucketStart();
    }

    @Override
    protected EventsPerMinuteAggregate mergeAggregates(EventsPerMinuteAggregate left, EventsPerMinuteAggregate right) {
        return right.count() >= left.count() ? right : left;
    }

    @Override
    protected void batchUpsert(JdbcTemplate jdbcTemplate, List<EventsPerMinuteAggregate> aggregates) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                aggregates,
                aggregates.size(),
                this::bindStatement
        );
    }

    private void bindStatement(PreparedStatement statement, EventsPerMinuteAggregate aggregate) throws SQLException {
        statement.setLong(1, com.booster.common.SnowflakeGenerator.nextId());
        statement.setString(2, aggregate.eventType().name());
        statement.setTimestamp(3, Timestamp.from(aggregate.minuteBucketStart()));
        statement.setLong(4, aggregate.count());
    }
}
