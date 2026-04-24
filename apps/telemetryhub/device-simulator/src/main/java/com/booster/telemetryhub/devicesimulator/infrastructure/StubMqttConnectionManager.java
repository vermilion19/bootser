package com.booster.telemetryhub.devicesimulator.infrastructure;

import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicReference;

public class StubMqttConnectionManager implements MqttConnectionManager {

    private final SimulatorPublisherProperties publisherProperties;
    private final AtomicReference<MqttConnectionState> connectionStateRef = new AtomicReference<>(MqttConnectionState.IDLE);

    public StubMqttConnectionManager(SimulatorPublisherProperties publisherProperties) {
        this.publisherProperties = publisherProperties;
        if (!publisherProperties.getMqtt().isEnabled()) {
            connectionStateRef.set(MqttConnectionState.DISABLED);
        }
    }

    @Override
    public MqttConnectionState connect() {
        if (!publisherProperties.getMqtt().isEnabled()) {
            connectionStateRef.set(MqttConnectionState.DISABLED);
            return connectionStateRef.get();
        }

        connectionStateRef.set(MqttConnectionState.READY);
        return connectionStateRef.get();
    }

    @Override
    public void disconnect() {
        connectionStateRef.set(publisherProperties.getMqtt().isEnabled() ? MqttConnectionState.IDLE : MqttConnectionState.DISABLED);
    }

    @Override
    public PublishResult publish(MessageEnvelope message) {
        if (connectionStateRef.get() != MqttConnectionState.READY) {
            return PublishResult.failure("mqtt connection is not ready");
        }

        return PublishResult.ok();
    }

    @PreDestroy
    void shutdown() {
        disconnect();
    }
}
