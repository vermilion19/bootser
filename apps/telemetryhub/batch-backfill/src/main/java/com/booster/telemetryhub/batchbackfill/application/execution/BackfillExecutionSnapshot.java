package com.booster.telemetryhub.batchbackfill.application.execution;

import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import java.time.Instant;
import java.util.List;

public record BackfillExecutionSnapshot(
        String jobName,
        List<String> targets,
        Instant from,
        Instant to,
        boolean dryRun,
        String sourceType,
        String overwriteMode,
        int chunkSize,
        Instant lastPreparedAt
) {
    public static BackfillExecutionSnapshot from(BackfillPlan plan, Instant preparedAt) {
        return new BackfillExecutionSnapshot(
                plan.jobName(),
                plan.targets().stream().map(Enum::name).toList(),
                plan.from(),
                plan.to(),
                plan.dryRun(),
                plan.sourceType().name(),
                plan.overwriteMode().name(),
                plan.chunkSize(),
                preparedAt
        );
    }
}
