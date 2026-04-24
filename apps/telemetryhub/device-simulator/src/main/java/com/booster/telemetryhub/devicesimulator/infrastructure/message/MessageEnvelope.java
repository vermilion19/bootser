package com.booster.telemetryhub.devicesimulator.infrastructure.message;

public record MessageEnvelope(
        String topic,
        String key,
        String payload
) {
}
