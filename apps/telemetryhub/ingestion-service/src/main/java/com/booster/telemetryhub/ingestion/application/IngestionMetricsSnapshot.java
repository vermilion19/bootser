package com.booster.telemetryhub.ingestion.application;

import java.time.Instant;

public record IngestionMetricsSnapshot(
        long totalReceived,
        long totalPublished,
        long totalFailed,
        Instant lastReceivedAt,
        Instant lastPublishedAt,
        String lastFailureStage,
        String lastFailureReason
) {
    public static IngestionMetricsSnapshot empty() {
        return new IngestionMetricsSnapshot(0, 0, 0, null, null, null, null);
    }
}
