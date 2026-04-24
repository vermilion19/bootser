package com.booster.telemetryhub.batchbackfill.application.plan;

import com.booster.telemetryhub.batchbackfill.domain.BackfillOverwriteMode;
import com.booster.telemetryhub.batchbackfill.domain.BackfillSourceType;
import com.booster.telemetryhub.batchbackfill.domain.BackfillTarget;

import java.time.Instant;
import java.util.List;

public record BackfillPlan(
        String jobName,
        BackfillSourceType sourceType,
        List<BackfillTarget> targets,
        Instant from,
        Instant to,
        int chunkSize,
        BackfillOverwriteMode overwriteMode,
        boolean dryRun
) {
}
