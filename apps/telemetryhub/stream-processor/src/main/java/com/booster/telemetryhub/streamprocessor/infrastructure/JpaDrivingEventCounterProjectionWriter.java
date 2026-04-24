package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.DrivingEventCounterProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;
import com.booster.telemetryhub.streamprocessor.domain.entity.DrivingEventCounterEntity;
import com.booster.telemetryhub.streamprocessor.domain.repository.DrivingEventCounterRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class JpaDrivingEventCounterProjectionWriter implements DrivingEventCounterProjectionWriter {

    private final DrivingEventCounterRepository repository;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaDrivingEventCounterProjectionWriter(
            DrivingEventCounterRepository repository,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.repository = repository;
        this.metricsCollector = metricsCollector;
    }

    @Override
    @Transactional
    public void upsert(DrivingEventCounterAggregate aggregate) {
        try {
            repository.findByDeviceIdAndDrivingEventTypeAndMinuteBucketStart(
                            aggregate.deviceId(),
                            aggregate.drivingEventType(),
                            aggregate.minuteBucketStart()
                    )
                    .ifPresentOrElse(
                            entity -> entity.apply(aggregate),
                            () -> repository.save(DrivingEventCounterEntity.create(aggregate))
                    );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.DRIVING_EVENT_COUNTER);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.DRIVING_EVENT_COUNTER, exception);
            throw exception;
        }
    }
}
