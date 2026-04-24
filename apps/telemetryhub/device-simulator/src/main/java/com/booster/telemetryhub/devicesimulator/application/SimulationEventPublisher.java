package com.booster.telemetryhub.devicesimulator.application;

public interface SimulationEventPublisher {

    void publish(SimulationEventBatch batch, SimulatorRuntimeState runtimeState);
}
