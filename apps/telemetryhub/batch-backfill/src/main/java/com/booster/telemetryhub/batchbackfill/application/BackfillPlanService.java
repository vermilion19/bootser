package com.booster.telemetryhub.batchbackfill.application;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BackfillPlanService {

    private final DefaultBackfillPlanFactory backfillPlanFactory;
    private final AtomicReference<BackfillExecutionSnapshot> snapshotRef = new AtomicReference<>();

    public BackfillPlanService(DefaultBackfillPlanFactory backfillPlanFactory) {
        this.backfillPlanFactory = backfillPlanFactory;
    }

    public BackfillPlan prepareDefaultPlan() {
        BackfillPlan plan = backfillPlanFactory.createDefaultPlan();
        snapshotRef.set(BackfillExecutionSnapshot.from(plan, Instant.now()));
        return plan;
    }

    public BackfillExecutionSnapshot latestSnapshot() {
        if (snapshotRef.get() == null) {
            prepareDefaultPlan();
        }
        return snapshotRef.get();
    }
}
