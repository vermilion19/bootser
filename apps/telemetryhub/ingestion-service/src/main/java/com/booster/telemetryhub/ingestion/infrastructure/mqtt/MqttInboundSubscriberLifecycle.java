package com.booster.telemetryhub.ingestion.infrastructure.mqtt;

import com.booster.telemetryhub.ingestion.application.mqtt.MqttInboundAdapter;
import com.booster.telemetryhub.ingestion.config.mqtt.IngestionMqttSubscriberProperties;
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
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
    private final AtomicReference<String> resolvedClientId = new AtomicReference<>();
    private final AtomicLong droppedMessages = new AtomicLong();

    private MqttClient client;
    private volatile BlockingQueue<InboundMessageEnvelope> inboundQueue;
    private volatile ExecutorService inboundWorkerExecutor;

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
    public synchronized void start() {
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
            running.set(true);
            initializeInboundProcessing();
            String actualClientId = resolveClientId();
            resolvedClientId.set(actualClientId);
            client = new MqttClient(properties.getBrokerUri(), actualClientId);
            client.setCallback(new InboundCallback());
            client.connect(connectOptions());
            subscribeAll();
            state.set(MqttSubscriberState.READY);
            running.set(true);
            log.info(
                    "MQTT inbound subscriber connected. brokerUri={}, clientId={}, subscriptions={}",
                    properties.getBrokerUri(),
                    actualClientId,
                    resolvedSubscriptions()
            );
        } catch (MqttException exception) {
            running.set(false);
            shutdownInboundProcessing();
            state.set(MqttSubscriberState.DEGRADED);
            lastError.set(exception.getMessage());
            log.warn("Failed to start MQTT inbound subscriber: brokerUri={}, reason={}", properties.getBrokerUri(), exception.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (client == null) {
            shutdownInboundProcessing();
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
            resolvedClientId.set(null);
            shutdownInboundProcessing();
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
                resolvedClientId.get() != null ? resolvedClientId.get() : properties.getClientId(),
                resolvedSubscriptions(),
                totalMessages.get(),
                inboundQueue != null ? inboundQueue.size() : 0,
                droppedMessages.get(),
                lastReceivedAt.get(),
                lastTopic.get(),
                lastError.get()
        );
    }

    private void initializeInboundProcessing() {
        inboundQueue = new LinkedBlockingQueue<>(properties.getInboundQueueCapacity());
        inboundWorkerExecutor = Executors.newFixedThreadPool(properties.getInboundWorkerThreads());
        java.util.stream.IntStream.range(0, properties.getInboundWorkerThreads())
                .forEach(index -> inboundWorkerExecutor.submit(this::runInboundWorker));
    }

    private void shutdownInboundProcessing() {
        if (inboundWorkerExecutor != null) {
            inboundWorkerExecutor.shutdownNow();
            try {
                inboundWorkerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        inboundWorkerExecutor = null;
        if (inboundQueue != null) {
            inboundQueue.clear();
        }
        inboundQueue = null;
    }

    private void runInboundWorker() {
        while (running.get() || (inboundQueue != null && !inboundQueue.isEmpty())) {
            try {
                if (inboundQueue == null) {
                    return;
                }
                InboundMessageEnvelope envelope = inboundQueue.poll(500, TimeUnit.MILLISECONDS);
                if (envelope == null) {
                    continue;
                }
                mqttInboundAdapter.receive(envelope.topic(), envelope.qos(), envelope.payload());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                lastError.set(exception.getMessage());
                log.warn("Failed to process inbound MQTT message: reason={}", exception.getMessage());
            }
        }
    }

    private void subscribeAll() throws MqttException {
        for (String subscription : resolvedSubscriptions()) {
            client.subscribe(subscription);
        }
    }

    private List<String> resolvedSubscriptions() {
        if (!properties.isSharedSubscriptionEnabled()) {
            return List.copyOf(properties.getSubscriptions());
        }

        return properties.getSubscriptions().stream()
                .map(this::toSharedSubscription)
                .toList();
    }

    private String toSharedSubscription(String subscription) {
        if (subscription.startsWith("$share/")) {
            return subscription;
        }
        return "$share/" + properties.getSharedSubscriptionGroup() + "/" + subscription;
    }

    private String resolveClientId() {
        String suffix = sanitizeSuffix(properties.getClientIdSuffix());
        if (!suffix.isBlank()) {
            return properties.getClientId() + "-" + suffix;
        }
        return properties.getClientId() + "-" + sanitizeSuffix(defaultInstanceSuffix());
    }

    private String defaultInstanceSuffix() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if (runtimeName != null && !runtimeName.isBlank()) {
            return runtimeName;
        }
        return UUID.randomUUID().toString();
    }

    private String sanitizeSuffix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "-");
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
            BlockingQueue<InboundMessageEnvelope> queue = inboundQueue;
            if (queue == null) {
                droppedMessages.incrementAndGet();
                lastError.set("inbound queue is not initialized");
                return;
            }
            boolean offered = queue.offer(new InboundMessageEnvelope(
                    topic,
                    message.getQos(),
                    new String(message.getPayload(), StandardCharsets.UTF_8)
            ));
            if (!offered) {
                droppedMessages.incrementAndGet();
                lastError.set("inbound queue is full");
                log.warn("Dropped inbound MQTT message because queue is full. topic={}", topic);
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    private record InboundMessageEnvelope(
            String topic,
            int qos,
            String payload
    ) {
    }
}
