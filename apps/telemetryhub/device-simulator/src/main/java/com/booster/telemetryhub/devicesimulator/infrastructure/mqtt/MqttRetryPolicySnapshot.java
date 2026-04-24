package com.booster.telemetryhub.devicesimulator.infrastructure.mqtt;

public record MqttRetryPolicySnapshot(
        int maxAttempts,
        long initialDelayMs,
        double backoffMultiplier,
        long maxDelayMs,
        boolean jitterEnabled
) {
}
