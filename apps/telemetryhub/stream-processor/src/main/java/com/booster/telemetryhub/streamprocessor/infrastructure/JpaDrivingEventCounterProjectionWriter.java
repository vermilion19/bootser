package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.DrivingEventCounterProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JpaDrivingEventCounterProjectionWriter implements DrivingEventCounterProjectionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaDrivingEventCounterProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void upsert(DrivingEventCounterAggregate aggregate) {
        try {
            jdbcTemplate.update(
                    """
                    insert into telemetryhub_driving_event_counter
                        (id, device_id, driving_event_type, minute_bucket_start, event_count, created_at, updated_at)
                    values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                    on conflict (device_id, driving_event_type, minute_bucket_start) do update
                        set event_count = excluded.event_count,
                            updated_at = current_timestamp
                    """,
                    com.booster.common.SnowflakeGenerator.nextId(),
                    aggregate.deviceId(),
                    aggregate.drivingEventType().name(),
                    java.sql.Timestamp.from(aggregate.minuteBucketStart()),
                    aggregate.count()
            );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.DRIVING_EVENT_COUNTER);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.DRIVING_EVENT_COUNTER, exception);
            throw exception;
        }
    }
}
