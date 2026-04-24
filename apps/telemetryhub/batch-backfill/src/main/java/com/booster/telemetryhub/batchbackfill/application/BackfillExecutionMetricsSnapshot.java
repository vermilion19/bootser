package com.booster.telemetryhub.batchbackfill.application;

import java.time.Instant;
import java.util.List;

public record BackfillExecutionMetricsSnapshot(
        String jobName,
        List<String> targets,
        boolean dryRun,
        long totalReadEvents,
        long totalWrites,
        Instant lastExecutionTime,
        String sourceType,
        String overwriteMode
) {
    public static BackfillExecutionMetricsSnapshot empty() {
        return new BackfillExecutionMetricsSnapshot(
                null,
                List.of(),
                true,
                0,
                0,
                null,
                null,
                null
        );
    }
}
