package com.booster.telemetryhub.devicesimulator.application;

import java.time.Instant;

public record SimulatorMetricsSnapshot(
        long cycleCount,
        long telemetryCount,
        long deviceHealthCount,
        long drivingEventCount,
        Instant lastPublishedAt,
        SimulatorBatchSummary lastBatch
) {
    public static SimulatorMetricsSnapshot empty() {
        return new SimulatorMetricsSnapshot(0, 0, 0, 0, null, SimulatorBatchSummary.empty());
    }
}
