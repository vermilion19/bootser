package com.booster.telemetryhub.devicesimulator.infrastructure;

public record MessageEnvelope(
        String topic,
        String key,
        String payload
) {
}
