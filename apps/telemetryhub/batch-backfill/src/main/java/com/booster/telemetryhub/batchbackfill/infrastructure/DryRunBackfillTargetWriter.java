package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.batchbackfill.application.BackfillPlan;
import com.booster.telemetryhub.batchbackfill.application.BackfillRawEvent;
import com.booster.telemetryhub.batchbackfill.application.BackfillTargetWriter;
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
