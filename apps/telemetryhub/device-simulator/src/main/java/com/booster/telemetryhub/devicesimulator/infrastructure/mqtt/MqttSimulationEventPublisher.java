package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.runtime.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.config.SimulatorPublisherProperties;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.MessageEnvelope;
import com.booster.telemetryhub.devicesimulator.infrastructure.message.SimulationEventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MqttSimulationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttSimulationEventPublisher.class);

    private final SimulationEventSerializer eventSerializer;
    private final SimulatorPublisherProperties publisherProperties;
    private final MqttConnectionManager connectionManager;
    private final AtomicReference<MqttPublisherSnapshot> snapshotRef;

    public MqttSimulationEventPublisher(
            SimulationEventSerializer eventSerializer,
            SimulatorPublisherProperties publisherProperties,
            MqttConnectionManager connectionManager
    ) {
        this.eventSerializer = eventSerializer;
        this.publisherProperties = publisherProperties;
        this.connectionManager = connectionManager;
        this.snapshotRef = new AtomicReference<>(initialSnapshot());
    }

    public void publishBatch(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
        List<MessageEnvelope> messages = eventSerializer.serialize(batch);
        Instant attemptedAt = Instant.now();

        if (!publisherProperties.getMqtt().isEnabled()) {
            snapshotRef.set(currentSnapshot(MqttConnectionState.DISABLED, 0, attemptedAt, "mqtt.enabled=false"));
            log.warn(
                    "MQTT publisher mode selected but mqtt.enabled=false. brokerUri={}, totalMessages={}, scenario={}",
                    publisherProperties.getMqtt().getBrokerUri(),
                    messages.size(),
                    runtimeState.scenario()
            );
            return;
        }

        MqttConnectionState connectionState = connectionManager.connect();
        snapshotRef.set(currentSnapshot(connectionState, 0, attemptedAt, null));

        if (connectionState != MqttConnectionState.READY) {
            snapshotRef.set(currentSnapshot(MqttConnectionState.DEGRADED, 0, attemptedAt, "mqtt connection is not ready"));
            return;
        }

        long publishedCount = 0;
        String failureReason = null;
        for (MessageEnvelope message : messages) {
            MqttConnectionManager.PublishResult result = connectionManager.publish(message);
            if (!result.successful()) {
                failureReason = result.failureReason();
                break;
            }
            publishedCount++;
        }

        if (failureReason != null) {
            snapshotRef.set(currentSnapshot(MqttConnectionState.DEGRADED, publishedCount, attemptedAt, failureReason));
            log.warn(
                    "MQTT publisher skeleton failed during publish: brokerUri={}, publishedCount={}, totalMessages={}, reason={}",
                    publisherProperties.getMqtt().getBrokerUri(),
                    publishedCount,
                    messages.size(),
                    failureReason
            );
            return;
        }

        snapshotRef.set(successSnapshot(publishedCount, attemptedAt));
        log.info(
            "MQTT publisher skeleton prepared: brokerUri={}, clientId={}, qos={}, retain={}, totalMessages={}, scenario={}",
            publisherProperties.getMqtt().getBrokerUri(),
                publisherProperties.getMqtt().getClientId(),
                publisherProperties.getMqtt().getQos(),
                publisherProperties.getMqtt().isRetain(),
            messages.size(),
            runtimeState.scenario()
        );
    }

    public MqttPublisherSnapshot snapshot() {
        return snapshotRef.get();
    }

    private MqttPublisherSnapshot initialSnapshot() {
        return new MqttPublisherSnapshot(
                publisherProperties.getMqtt().isEnabled() ? MqttConnectionState.IDLE : MqttConnectionState.DISABLED,
                publisherProperties.getMqtt().getBrokerUri(),
                publisherProperties.getMqtt().getClientId(),
                publisherProperties.getMqtt().getQos(),
                publisherProperties.getMqtt().isRetain(),
                0,
                0,
                null,
                null,
                null,
                retryPolicySnapshot()
        );
    }

    private MqttPublisherSnapshot currentSnapshot(
            MqttConnectionState state,
            long lastPublishedMessageCount,
            Instant lastAttemptAt,
            String lastFailureReason
    ) {
        MqttPublisherSnapshot current = snapshotRef.get();
        long failureCount = lastFailureReason == null ? 0 : current.consecutiveFailureCount() + 1;

        return new MqttPublisherSnapshot(
                state,
                publisherProperties.getMqtt().getBrokerUri(),
                publisherProperties.getMqtt().getClientId(),
                publisherProperties.getMqtt().getQos(),
                publisherProperties.getMqtt().isRetain(),
                lastPublishedMessageCount,
                failureCount,
                lastAttemptAt,
                current.lastSuccessAt(),
                lastFailureReason,
                retryPolicySnapshot()
        );
    }

    private MqttPublisherSnapshot successSnapshot(long lastPublishedMessageCount, Instant attemptedAt) {
        return new MqttPublisherSnapshot(
                MqttConnectionState.READY,
                publisherProperties.getMqtt().getBrokerUri(),
                publisherProperties.getMqtt().getClientId(),
                publisherProperties.getMqtt().getQos(),
                publisherProperties.getMqtt().isRetain(),
                lastPublishedMessageCount,
                0,
                attemptedAt,
                attemptedAt,
                null,
                retryPolicySnapshot()
        );
    }

    private MqttRetryPolicySnapshot retryPolicySnapshot() {
        SimulatorPublisherProperties.Retry retry = publisherProperties.getMqtt().getRetry();
        return new MqttRetryPolicySnapshot(
                retry.getMaxAttempts(),
                retry.getInitialDelayMs(),
                retry.getBackoffMultiplier(),
                retry.getMaxDelayMs(),
                retry.isJitterEnabled()
        );
    }
}
