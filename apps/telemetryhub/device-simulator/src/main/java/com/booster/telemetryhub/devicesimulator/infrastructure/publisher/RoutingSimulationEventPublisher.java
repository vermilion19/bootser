package com.booster.telemetryhub.devicesimulator.infrastructure.publisher;

import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventPublisher;
import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import com.booster.telemetryhub.devicesimulator.infrastructure.mqtt.MqttSimulationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class RoutingSimulationEventPublisher implements SimulationEventPublisher {

    private final LoggingSimulationEventPublisher loggingPublisher;
    private final InMemorySimulationEventPublisher memoryPublisher;
    private final BridgeSimulationEventPublisher bridgePublisher;
    private final MqttSimulationEventPublisher mqttPublisher;
    private final SimulatorPublisherProperties publisherProperties;

    public RoutingSimulationEventPublisher(
            LoggingSimulationEventPublisher loggingPublisher,
            InMemorySimulationEventPublisher memoryPublisher,
            BridgeSimulationEventPublisher bridgePublisher,
            MqttSimulationEventPublisher mqttPublisher,
            SimulatorPublisherProperties publisherProperties
    ) {
        this.loggingPublisher = loggingPublisher;
        this.memoryPublisher = memoryPublisher;
        this.bridgePublisher = bridgePublisher;
        this.mqttPublisher = mqttPublisher;
        this.publisherProperties = publisherProperties;
    }

    @Override
    public void publish(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
        if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.MQTT) {
            mqttPublisher.publishBatch(batch, runtimeState);
            return;
        }

        if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.MEMORY) {
            memoryPublisher.publishBatch(batch, runtimeState);
            return;
        }

        if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.BRIDGE) {
            bridgePublisher.publishBatch(batch, runtimeState);
            return;
        }

        loggingPublisher.publishBatch(batch, runtimeState);
    }
}
