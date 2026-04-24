package com.booster.telemetryhub.ingestion.web.dto;

import com.booster.telemetryhub.ingestion.application.IngestionMetricsSnapshot;

import java.time.Instant;

public record IngestionMetricsResponse(
        long totalReceived,
        long totalPublished,
        long totalFailed,
        Instant lastReceivedAt,
        Instant lastPublishedAt
) {
    public static IngestionMetricsResponse from(IngestionMetricsSnapshot snapshot) {
        return new IngestionMetricsResponse(
                snapshot.totalReceived(),
                snapshot.totalPublished(),
                snapshot.totalFailed(),
                snapshot.lastReceivedAt(),
                snapshot.lastPublishedAt()
        );
    }
}
