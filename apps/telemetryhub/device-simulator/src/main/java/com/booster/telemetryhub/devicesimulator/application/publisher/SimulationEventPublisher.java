package com.booster.telemetryhub.devicesimulator.application.publisher;

import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;

public interface SimulationEventPublisher {

    void publish(SimulationEventBatch batch, SimulatorRuntimeState runtimeState);
}
