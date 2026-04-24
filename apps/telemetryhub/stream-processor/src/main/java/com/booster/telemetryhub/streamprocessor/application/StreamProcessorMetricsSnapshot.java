package com.booster.telemetryhub.streamprocessor.application;

import java.time.Instant;

public record StreamProcessorMetricsSnapshot(
        long totalProjectionWrites,
        long totalProjectionWriteFailures,
        long deviceLastSeenWrites,
        long deviceLastSeenFailures,
        long eventsPerMinuteWrites,
        long eventsPerMinuteFailures,
        long drivingEventCounterWrites,
        long drivingEventCounterFailures,
        long regionHeatmapWrites,
        long regionHeatmapFailures,
        Instant lastSuccessTime,
        Instant lastFailureTime,
        ProjectionType lastFailureProjection,
        String lastFailureReason
) {
    public static StreamProcessorMetricsSnapshot empty() {
        return new StreamProcessorMetricsSnapshot(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null
        );
    }
}
