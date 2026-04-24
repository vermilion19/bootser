package com.booster.telemetryhub.batchbackfill.application.plan;

import com.booster.telemetryhub.batchbackfill.config.BatchBackfillProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DefaultBackfillPlanFactory {

    private final BatchBackfillProperties properties;

    public DefaultBackfillPlanFactory(BatchBackfillProperties properties) {
        this.properties = properties;
    }

    public BackfillPlan createDefaultPlan() {
        Instant to = Instant.now();
        Instant from = to.minus(properties.getDefaultLookback());

        return new BackfillPlan(
                "telemetryhub-default-backfill",
                properties.getSourceType(),
                properties.getDefaultTargets(),
                from,
                to,
                properties.getChunkSize(),
                properties.getOverwriteMode(),
                properties.isDryRun()
        );
    }
}
