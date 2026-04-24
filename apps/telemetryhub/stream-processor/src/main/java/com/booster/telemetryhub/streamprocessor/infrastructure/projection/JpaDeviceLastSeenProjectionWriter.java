package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.application.projection.DeviceLastSeenProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class JpaDeviceLastSeenProjectionWriter
        extends BufferedJdbcProjectionWriter<DeviceLastSeenAggregate, String>
        implements DeviceLastSeenProjectionWriter {

    private static final String UPSERT_SQL = """
            insert into telemetryhub_device_last_seen
                (id, device_id, last_event_id, last_event_type, last_event_time, last_ingest_time, source_topic, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
            on conflict (device_id) do update
                set last_event_id = excluded.last_event_id,
                    last_event_type = excluded.last_event_type,
                    last_event_time = excluded.last_event_time,
                    last_ingest_time = excluded.last_ingest_time,
                    source_topic = excluded.source_topic,
                    updated_at = current_timestamp
            where excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
               or (
                    excluded.last_event_time = telemetryhub_device_last_seen.last_event_time
                    and excluded.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time
               )
            """;

    public JpaDeviceLastSeenProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector,
            StreamProcessorProperties properties
    ) {
        super(jdbcTemplate, metricsCollector, properties, ProjectionType.DEVICE_LAST_SEEN);
    }

    @Override
    public void upsert(DeviceLastSeenAggregate aggregate) {
        upsertBuffered(aggregate);
    }

    @Override
    protected String bufferKey(DeviceLastSeenAggregate aggregate) {
        return aggregate.deviceId();
    }

    @Override
    protected DeviceLastSeenAggregate mergeAggregates(DeviceLastSeenAggregate left, DeviceLastSeenAggregate right) {
        return left.merge(right);
    }

    @Override
    protected void batchUpsert(JdbcTemplate jdbcTemplate, List<DeviceLastSeenAggregate> aggregates) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                aggregates,
                aggregates.size(),
                this::bindStatement
        );
    }

    private void bindStatement(PreparedStatement statement, DeviceLastSeenAggregate aggregate) throws SQLException {
        statement.setLong(1, com.booster.common.SnowflakeGenerator.nextId());
        statement.setString(2, aggregate.deviceId());
        statement.setString(3, aggregate.lastEventId());
        statement.setString(4, aggregate.lastEventType().name());
        statement.setTimestamp(5, Timestamp.from(aggregate.lastEventTime()));
        statement.setTimestamp(6, Timestamp.from(aggregate.lastIngestTime()));
        statement.setString(7, aggregate.sourceTopic());
    }
}
