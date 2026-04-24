package com.booster.telemetryhub.streamprocessor.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class StreamProcessorMetricsCollector {

    private final AtomicReference<StreamProcessorMetricsSnapshot> snapshotRef =
            new AtomicReference<>(StreamProcessorMetricsSnapshot.empty());
    private final MeterRegistry meterRegistry;

    public StreamProcessorMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordProjectionWriteSuccess(ProjectionType projectionType) {
        snapshotRef.updateAndGet(current -> {
            long deviceLastSeenWrites = current.deviceLastSeenWrites();
            long eventsPerMinuteWrites = current.eventsPerMinuteWrites();
            long drivingEventCounterWrites = current.drivingEventCounterWrites();
            long regionHeatmapWrites = current.regionHeatmapWrites();

            switch (projectionType) {
                case DEVICE_LAST_SEEN -> deviceLastSeenWrites++;
                case EVENTS_PER_MINUTE -> eventsPerMinuteWrites++;
                case DRIVING_EVENT_COUNTER -> drivingEventCounterWrites++;
                case REGION_HEATMAP -> regionHeatmapWrites++;
            }

            return new StreamProcessorMetricsSnapshot(
                    current.totalProjectionWrites() + 1,
                    current.totalProjectionWriteFailures(),
                    deviceLastSeenWrites,
                    current.deviceLastSeenFailures(),
                    eventsPerMinuteWrites,
                    current.eventsPerMinuteFailures(),
                    drivingEventCounterWrites,
                    current.drivingEventCounterFailures(),
                    regionHeatmapWrites,
                    current.regionHeatmapFailures(),
                    Instant.now(),
                    current.lastFailureTime(),
                    current.lastFailureProjection(),
                    current.lastFailureReason()
            );
        });

        counter("telemetryhub.stream.projection.write.success", projectionType).increment();
    }

    public void recordProjectionWriteFailure(ProjectionType projectionType, Exception exception) {
        snapshotRef.updateAndGet(current -> {
            long deviceLastSeenFailures = current.deviceLastSeenFailures();
            long eventsPerMinuteFailures = current.eventsPerMinuteFailures();
            long drivingEventCounterFailures = current.drivingEventCounterFailures();
            long regionHeatmapFailures = current.regionHeatmapFailures();

            switch (projectionType) {
                case DEVICE_LAST_SEEN -> deviceLastSeenFailures++;
                case EVENTS_PER_MINUTE -> eventsPerMinuteFailures++;
                case DRIVING_EVENT_COUNTER -> drivingEventCounterFailures++;
                case REGION_HEATMAP -> regionHeatmapFailures++;
            }

            return new StreamProcessorMetricsSnapshot(
                    current.totalProjectionWrites(),
                    current.totalProjectionWriteFailures() + 1,
                    current.deviceLastSeenWrites(),
                    deviceLastSeenFailures,
                    current.eventsPerMinuteWrites(),
                    eventsPerMinuteFailures,
                    current.drivingEventCounterWrites(),
                    drivingEventCounterFailures,
                    current.regionHeatmapWrites(),
                    regionHeatmapFailures,
                    current.lastSuccessTime(),
                    Instant.now(),
                    projectionType,
                    exception.getMessage()
            );
        });

        counter("telemetryhub.stream.projection.write.failure", projectionType).increment();
    }

    public StreamProcessorMetricsSnapshot snapshot() {
        return snapshotRef.get();
    }

    private Counter counter(String name, ProjectionType projectionType) {
        return Counter.builder(name)
                .tag("projection", projectionType.name())
                .register(meterRegistry);
    }
}
