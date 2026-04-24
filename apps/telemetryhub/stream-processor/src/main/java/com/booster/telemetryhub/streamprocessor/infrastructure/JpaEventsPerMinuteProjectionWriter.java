package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.EventsPerMinuteProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;
import com.booster.telemetryhub.streamprocessor.domain.entity.EventsPerMinuteEntity;
import com.booster.telemetryhub.streamprocessor.domain.repository.EventsPerMinuteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class JpaEventsPerMinuteProjectionWriter implements EventsPerMinuteProjectionWriter {

    private final EventsPerMinuteRepository repository;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaEventsPerMinuteProjectionWriter(
            EventsPerMinuteRepository repository,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.repository = repository;
        this.metricsCollector = metricsCollector;
    }

    @Override
    @Transactional
    public void upsert(EventsPerMinuteAggregate aggregate) {
        try {
            repository.findByEventTypeAndMinuteBucketStart(
                            aggregate.eventType(),
                            aggregate.minuteBucketStart()
                    )
                    .ifPresentOrElse(
                            entity -> entity.apply(aggregate),
                            () -> repository.save(EventsPerMinuteEntity.create(aggregate))
                    );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.EVENTS_PER_MINUTE);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.EVENTS_PER_MINUTE, exception);
            throw exception;
        }
    }
}
