package com.booster.telemetryhub.devicesimulator.application;

import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import com.booster.telemetryhub.devicesimulator.domain.SimulatorStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DeviceSimulatorControlService {

    private final AtomicReference<SimulatorRuntimeState> runtimeState = new AtomicReference<>(SimulatorRuntimeState.idle());

    public SimulatorRuntimeState start(int devices, int intervalMs, int qos, int connectRampUpPerSecond) {
        SimulatorRuntimeState next = new SimulatorRuntimeState(
                SimulatorStatus.RUNNING,
                devices,
                intervalMs,
                qos,
                connectRampUpPerSecond,
                runtimeState.get().scenarioPercent(),
                runtimeState.get().scenario(),
                Instant.now()
        );
        runtimeState.set(next);
        return next;
    }

    public SimulatorRuntimeState stop() {
        SimulatorRuntimeState current = runtimeState.get();
        SimulatorRuntimeState next = new SimulatorRuntimeState(
                SimulatorStatus.STOPPED,
                current.deviceCount(),
                current.publishIntervalMs(),
                current.qos(),
                current.connectRampUpPerSecond(),
                current.scenarioPercent(),
                current.scenario(),
                Instant.now()
        );
        runtimeState.set(next);
        return next;
    }

    public SimulatorRuntimeState scale(int devices) {
        SimulatorRuntimeState current = runtimeState.get();
        SimulatorRuntimeState next = new SimulatorRuntimeState(
                current.status(),
                devices,
                current.publishIntervalMs(),
                current.qos(),
                current.connectRampUpPerSecond(),
                current.scenarioPercent(),
                current.scenario(),
                Instant.now()
        );
        runtimeState.set(next);
        return next;
    }

    public SimulatorRuntimeState applyScenario(SimulationScenario scenario, int percent) {
        SimulatorRuntimeState current = runtimeState.get();
        SimulatorRuntimeState next = new SimulatorRuntimeState(
                current.status(),
                current.deviceCount(),
                current.publishIntervalMs(),
                current.qos(),
                current.connectRampUpPerSecond(),
                percent,
                scenario,
                Instant.now()
        );
        runtimeState.set(next);
        return next;
    }

    public SimulatorRuntimeState getStatus() {
        return runtimeState.get();
    }
}
