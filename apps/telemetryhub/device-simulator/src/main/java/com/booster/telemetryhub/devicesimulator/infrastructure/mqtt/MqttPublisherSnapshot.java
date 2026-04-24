package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

import java.time.Instant;

public record MqttPublisherSnapshot(
        MqttConnectionState connectionState,
        String brokerUri,
        String clientId,
        int qos,
        boolean retain,
        long lastPublishedMessageCount,
        long consecutiveFailureCount,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastFailureReason,
        MqttRetryPolicySnapshot retryPolicy
) {
}
