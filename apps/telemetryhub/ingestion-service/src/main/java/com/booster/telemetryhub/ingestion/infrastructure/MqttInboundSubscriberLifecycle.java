package com.booster.telemetryhub.ingestion.infrastructure;

import com.booster.telemetryhub.ingestion.application.MqttInboundAdapter;
import com.booster.telemetryhub.ingestion.config.IngestionMqttSubscriberProperties;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MqttInboundSubscriberLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundSubscriberLifecycle.class);

    private final IngestionMqttSubscriberProperties properties;
    private final MqttInboundAdapter mqttInboundAdapter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicReference<Instant> lastReceivedAt = new AtomicReference<>();
    private final AtomicReference<String> lastTopic = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<MqttSubscriberState> state = new AtomicReference<>(MqttSubscriberState.IDLE);

    private MqttClient client;

    public MqttInboundSubscriberLifecycle(
            IngestionMqttSubscriberProperties properties,
            MqttInboundAdapter mqttInboundAdapter
    ) {
        this.properties = properties;
        this.mqttInboundAdapter = mqttInboundAdapter;
        if (!properties.isEnabled()) {
            state.set(MqttSubscriberState.DISABLED);
        }
    }

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            state.set(MqttSubscriberState.DISABLED);
            return;
        }

        if (!properties.isRealClientEnabled()) {
            state.set(MqttSubscriberState.IDLE);
            log.info("MQTT inbound subscriber is enabled but real client is disabled. brokerUri={}", properties.getBrokerUri());
            return;
        }

        try {
            state.set(MqttSubscriberState.CONNECTING);
            client = new MqttClient(properties.getBrokerUri(), properties.getClientId());
            client.setCallback(new InboundCallback());
            client.connect(connectOptions());
            subscribeAll();
            state.set(MqttSubscriberState.READY);
            running.set(true);
            log.info("MQTT inbound subscriber connected. brokerUri={}, subscriptions={}", properties.getBrokerUri(), properties.getSubscriptions());
        } catch (MqttException exception) {
            state.set(MqttSubscriberState.DEGRADED);
            lastError.set(exception.getMessage());
            log.warn("Failed to start MQTT inbound subscriber: brokerUri={}, reason={}", properties.getBrokerUri(), exception.getMessage());
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (client == null) {
            state.set(properties.isEnabled() ? MqttSubscriberState.IDLE : MqttSubscriberState.DISABLED);
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException exception) {
            lastError.set(exception.getMessage());
        } finally {
            client = null;
            state.set(properties.isEnabled() ? MqttSubscriberState.IDLE : MqttSubscriberState.DISABLED);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    public MqttSubscriberSnapshot snapshot() {
        return new MqttSubscriberSnapshot(
                state.get(),
                properties.getBrokerUri(),
                properties.getClientId(),
                List.copyOf(properties.getSubscriptions()),
                totalMessages.get(),
                lastReceivedAt.get(),
                lastTopic.get(),
                lastError.get()
        );
    }

    private void subscribeAll() throws MqttException {
        for (String subscription : properties.getSubscriptions()) {
            client.subscribe(subscription);
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        return options;
    }

    @PreDestroy
    void shutdown() {
        stop();
    }

    private class InboundCallback implements MqttCallback {
        @Override
        public void connectionLost(Throwable cause) {
            running.set(false);
            state.set(MqttSubscriberState.DEGRADED);
            lastError.set(cause != null ? cause.getMessage() : "connection lost");
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            Instant receivedAt = Instant.now();
            totalMessages.incrementAndGet();
            lastReceivedAt.set(receivedAt);
            lastTopic.set(topic);
            mqttInboundAdapter.receive(
                    topic,
                    message.getQos(),
                    new String(message.getPayload(), StandardCharsets.UTF_8)
            );
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
