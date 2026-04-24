package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.DeviceLastSeenProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
import com.booster.telemetryhub.streamprocessor.domain.entity.DeviceLastSeenEntity;
import com.booster.telemetryhub.streamprocessor.domain.repository.DeviceLastSeenRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class JpaDeviceLastSeenProjectionWriter implements DeviceLastSeenProjectionWriter {

    private final DeviceLastSeenRepository repository;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaDeviceLastSeenProjectionWriter(
            DeviceLastSeenRepository repository,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.repository = repository;
        this.metricsCollector = metricsCollector;
    }

    @Override
    @Transactional
    public void upsert(DeviceLastSeenAggregate aggregate) {
        try {
            repository.findByDeviceId(aggregate.deviceId())
                    .ifPresentOrElse(
                            entity -> entity.apply(aggregate),
                            () -> repository.save(DeviceLastSeenEntity.create(aggregate))
                    );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.DEVICE_LAST_SEEN);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.DEVICE_LAST_SEEN, exception);
            throw exception;
        }
    }
}
