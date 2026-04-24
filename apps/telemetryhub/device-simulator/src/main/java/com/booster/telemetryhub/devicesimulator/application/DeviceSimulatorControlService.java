package com.booster.telemetryhub.devicesimulator.application;

import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import com.booster.telemetryhub.devicesimulator.domain.SimulatorStatus;
import com.booster.telemetryhub.devicesimulator.infrastructure.InMemoryPublishedMessageStore;
import com.booster.telemetryhub.devicesimulator.infrastructure.MemoryPublishedMessage;
import com.booster.telemetryhub.devicesimulator.infrastructure.MqttPublisherSnapshot;
import com.booster.telemetryhub.devicesimulator.infrastructure.MqttSimulationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DeviceSimulatorControlService {

    private final DeviceSimulatorLoopService loopService;
    private final InMemoryPublishedMessageStore inMemoryPublishedMessageStore;
    private final MqttSimulationEventPublisher mqttSimulationEventPublisher;
    private final AtomicReference<SimulatorRuntimeState> runtimeState = new AtomicReference<>(SimulatorRuntimeState.idle());

    public DeviceSimulatorControlService(
            DeviceSimulatorLoopService loopService,
            InMemoryPublishedMessageStore inMemoryPublishedMessageStore,
            MqttSimulationEventPublisher mqttSimulationEventPublisher
    ) {
        this.loopService = loopService;
        this.inMemoryPublishedMessageStore = inMemoryPublishedMessageStore;
        this.mqttSimulationEventPublisher = mqttSimulationEventPublisher;
    }

    public synchronized SimulatorRuntimeState start(int devices, int intervalMs, int qos, int connectRampUpPerSecond) {
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
        loopService.start(next);
        return next;
    }

    public synchronized SimulatorRuntimeState stop() {
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
        loopService.stop();
        return next;
    }

    public synchronized SimulatorRuntimeState scale(int devices) {
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
        loopService.refresh(next);
        return next;
    }

    public synchronized SimulatorRuntimeState applyScenario(SimulationScenario scenario, int percent) {
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
        loopService.refresh(next);
        return next;
    }

    public SimulatorRuntimeState getStatus() {
        return runtimeState.get();
    }

    public SimulatorMetricsSnapshot getMetrics() {
        return loopService.getMetrics();
    }

    public MqttPublisherSnapshot getMqttPublisherSnapshot() {
        return mqttSimulationEventPublisher.snapshot();
    }

    public java.util.List<MemoryPublishedMessage> getRecentPublishedMessages(int limit) {
        return inMemoryPublishedMessageStore.recent(limit);
    }

    public void clearPublishedMessages() {
        inMemoryPublishedMessageStore.clear();
    }
}
