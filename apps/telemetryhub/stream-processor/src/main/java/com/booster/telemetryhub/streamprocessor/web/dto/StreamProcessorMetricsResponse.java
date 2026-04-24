package com.booster.telemetryhub.streamprocessor.web.dto;

import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsSnapshot;

import java.time.Instant;

public record StreamProcessorMetricsResponse(
        long totalProjectionWrites,
        long totalProjectionWriteFailures,
        ProjectionMetricsResponse deviceLastSeen,
        ProjectionMetricsResponse eventsPerMinute,
        ProjectionMetricsResponse drivingEventCounter,
        ProjectionMetricsResponse regionHeatmap,
        Instant lastSuccessTime,
        Instant lastFailureTime,
        String lastFailureProjection,
        String lastFailureReason
) {
    public static StreamProcessorMetricsResponse from(StreamProcessorMetricsSnapshot snapshot) {
        return new StreamProcessorMetricsResponse(
                snapshot.totalProjectionWrites(),
                snapshot.totalProjectionWriteFailures(),
                new ProjectionMetricsResponse(snapshot.deviceLastSeenWrites(), snapshot.deviceLastSeenFailures()),
                new ProjectionMetricsResponse(snapshot.eventsPerMinuteWrites(), snapshot.eventsPerMinuteFailures()),
                new ProjectionMetricsResponse(snapshot.drivingEventCounterWrites(), snapshot.drivingEventCounterFailures()),
                new ProjectionMetricsResponse(snapshot.regionHeatmapWrites(), snapshot.regionHeatmapFailures()),
                snapshot.lastSuccessTime(),
                snapshot.lastFailureTime(),
                snapshot.lastFailureProjection() == null ? null : snapshot.lastFailureProjection().name(),
                snapshot.lastFailureReason()
        );
    }

    public record ProjectionMetricsResponse(
            long writes,
            long failures
    ) {
    }
}
