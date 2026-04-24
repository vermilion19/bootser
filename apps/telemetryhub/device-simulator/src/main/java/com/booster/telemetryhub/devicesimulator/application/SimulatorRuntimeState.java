package com.booster.telemetryhub.devicesimulator.application;

import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import com.booster.telemetryhub.devicesimulator.domain.SimulatorStatus;

import java.time.Instant;

public record SimulatorRuntimeState(
        SimulatorStatus status,
        int deviceCount,
        int publishIntervalMs,
        int qos,
        int connectRampUpPerSecond,
        int scenarioPercent,
        SimulationScenario scenario,
        Instant updatedAt
) {
    public static SimulatorRuntimeState idle() {
        return new SimulatorRuntimeState(
                SimulatorStatus.IDLE,
                0,
                1000,
                0,
                100,
                0,
                SimulationScenario.STEADY,
                Instant.now()
        );
    }
}
