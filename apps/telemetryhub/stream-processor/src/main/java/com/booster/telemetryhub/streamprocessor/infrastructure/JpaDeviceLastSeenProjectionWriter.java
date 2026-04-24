package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.DeviceLastSeenProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JpaDeviceLastSeenProjectionWriter implements DeviceLastSeenProjectionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaDeviceLastSeenProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void upsert(DeviceLastSeenAggregate aggregate) {
        try {
            jdbcTemplate.update(
                    """
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
                    """,
                    com.booster.common.SnowflakeGenerator.nextId(),
                    aggregate.deviceId(),
                    aggregate.lastEventId(),
                    aggregate.lastEventType().name(),
                    java.sql.Timestamp.from(aggregate.lastEventTime()),
                    java.sql.Timestamp.from(aggregate.lastIngestTime()),
                    aggregate.sourceTopic()
            );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.DEVICE_LAST_SEEN);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.DEVICE_LAST_SEEN, exception);
            throw exception;
        }
    }
}
