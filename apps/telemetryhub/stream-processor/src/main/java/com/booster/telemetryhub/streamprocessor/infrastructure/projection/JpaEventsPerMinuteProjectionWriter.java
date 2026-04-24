package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.telemetryhub.streamprocessor.application.metrics.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.application.projection.EventsPerMinuteProjectionWriter;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JpaEventsPerMinuteProjectionWriter implements EventsPerMinuteProjectionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaEventsPerMinuteProjectionWriter(
            JdbcTemplate jdbcTemplate,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void upsert(EventsPerMinuteAggregate aggregate) {
        try {
            jdbcTemplate.update(
                    """
                    insert into telemetryhub_events_per_minute
                        (id, event_type, minute_bucket_start, event_count, created_at, updated_at)
                    values (?, ?, ?, ?, current_timestamp, current_timestamp)
                    on conflict (event_type, minute_bucket_start) do update
                        set event_count = excluded.event_count,
                            updated_at = current_timestamp
                    """,
                    com.booster.common.SnowflakeGenerator.nextId(),
                    aggregate.eventType().name(),
                    java.sql.Timestamp.from(aggregate.minuteBucketStart()),
                    aggregate.count()
            );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.EVENTS_PER_MINUTE);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.EVENTS_PER_MINUTE, exception);
            throw exception;
        }
    }
}
