package com.booster.telemetryhub.devicesimulator.infrastructure;

public record MqttRetryPolicySnapshot(
        int maxAttempts,
        long initialDelayMs,
        double backoffMultiplier,
        long maxDelayMs,
        boolean jitterEnabled
) {
}
