package com.booster.telemetryhub.devicesimulator.web.dto;

import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import com.booster.telemetryhub.devicesimulator.domain.SimulatorStatus;

import java.time.Instant;

public record SimulatorStatusResponse(
        SimulatorStatus status,
        int deviceCount,
        int publishIntervalMs,
        int qos,
        int connectRampUpPerSecond,
        int scenarioPercent,
        SimulationScenario scenario,
        Instant updatedAt
) {
    public static SimulatorStatusResponse from(SimulatorRuntimeState state) {
        return new SimulatorStatusResponse(
                state.status(),
                state.deviceCount(),
                state.publishIntervalMs(),
                state.qos(),
                state.connectRampUpPerSecond(),
                state.scenarioPercent(),
                state.scenario(),
                state.updatedAt()
        );
    }
}
