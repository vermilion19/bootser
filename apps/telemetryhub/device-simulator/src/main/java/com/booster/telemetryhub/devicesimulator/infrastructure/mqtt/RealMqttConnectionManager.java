package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import jakarta.annotation.PreDestroy;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.MessageEnvelope;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class RealMqttConnectionManager implements MqttConnectionManager {

    private final SimulatorPublisherProperties publisherProperties;
    private final AtomicReference<MqttClient> clientRef = new AtomicReference<>();

    public RealMqttConnectionManager(SimulatorPublisherProperties publisherProperties) {
        this.publisherProperties = publisherProperties;
    }

    @Override
    public synchronized MqttConnectionState connect() {
        if (!publisherProperties.getMqtt().isEnabled()) {
            disconnect();
            return MqttConnectionState.DISABLED;
        }

        try {
            MqttClient client = clientRef.get();
            if (client == null) {
                client = new MqttClient(
                        publisherProperties.getMqtt().getBrokerUri(),
                        publisherProperties.getMqtt().getClientId()
                );
                clientRef.set(client);
            }

            if (!client.isConnected()) {
                client.connect(connectOptions());
            }

            return client.isConnected() ? MqttConnectionState.READY : MqttConnectionState.DEGRADED;
        } catch (MqttException e) {
            return MqttConnectionState.DEGRADED;
        }
    }

    @Override
    public synchronized void disconnect() {
        MqttClient client = clientRef.getAndSet(null);
        if (client == null) {
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException ignored) {
        }
    }

    @Override
    public synchronized PublishResult publish(MessageEnvelope message) {
        MqttClient client = clientRef.get();
        if (client == null || !client.isConnected()) {
            return PublishResult.failure("mqtt client is disconnected");
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(message.payload().getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(publisherProperties.getMqtt().getQos());
            mqttMessage.setRetained(publisherProperties.getMqtt().isRetain());
            client.publish(message.topic(), mqttMessage);
            return PublishResult.ok();
        } catch (MqttException e) {
            return PublishResult.failure("mqtt publish failed: " + e.getMessage());
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(publisherProperties.getMqtt().getConnectionTimeoutSeconds());
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        return options;
    }

    @PreDestroy
    void shutdown() {
        disconnect();
    }
}
