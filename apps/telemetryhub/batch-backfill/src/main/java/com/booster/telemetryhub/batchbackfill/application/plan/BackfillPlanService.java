package com.booster.telemetryhub.batchbackfill.application.plan;

import com.booster.telemetryhub.batchbackfill.application.execution.BackfillExecutionMetricsSnapshot;
import com.booster.telemetryhub.batchbackfill.application.execution.BackfillExecutionSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BackfillPlanService {

    private final DefaultBackfillPlanFactory backfillPlanFactory;
    private final AtomicReference<BackfillExecutionSnapshot> snapshotRef = new AtomicReference<>();
    private final AtomicReference<BackfillExecutionMetricsSnapshot> metricsRef =
            new AtomicReference<>(BackfillExecutionMetricsSnapshot.empty());

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

    public void recordExecution(BackfillPlan plan, long totalReadEvents, long totalWrites) {
        metricsRef.set(new BackfillExecutionMetricsSnapshot(
                plan.jobName(),
                plan.targets().stream().map(Enum::name).toList(),
                plan.dryRun(),
                totalReadEvents,
                totalWrites,
                Instant.now(),
                plan.sourceType().name(),
                plan.overwriteMode().name()
        ));
    }

    public BackfillExecutionMetricsSnapshot latestMetrics() {
        return metricsRef.get();
    }
}
