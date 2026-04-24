package com.booster.telemetryhub.devicesimulator.application.runtime;

import java.time.Instant;

public record SimulatorBatchSummary(
        int requestedDevices,
        int telemetryCount,
        int deviceHealthCount,
        int drivingEventCount,
        Instant generatedAt
) {
    public static SimulatorBatchSummary empty() {
        return new SimulatorBatchSummary(0, 0, 0, 0, Instant.now());
    }
}
