package com.booster.telemetryhub.batchbackfill.infrastructure.target;

import com.booster.telemetryhub.batchbackfill.application.io.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.io.BackfillTargetWriter;
import com.booster.telemetryhub.batchbackfill.application.plan.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.domain.BackfillTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DryRunBackfillTargetWriter implements BackfillTargetWriter {

    private static final Logger log = LoggerFactory.getLogger(DryRunBackfillTargetWriter.class);

    @Override
    public long write(BackfillTarget target, List<BackfillRawEvent> events, BackfillPlan plan) {
        log.info(
                "Dry-run backfill write: target={}, jobName={}, eventCount={}, overwriteMode={}, sourceType={}",
                target,
                plan.jobName(),
                events.size(),
                plan.overwriteMode(),
                plan.sourceType()
        );
        return events.size();
    }
}
