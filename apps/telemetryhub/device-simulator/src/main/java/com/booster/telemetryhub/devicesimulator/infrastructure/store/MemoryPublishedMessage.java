package com.booster.telemetryhub.devicesimulator.infrastructure.store;

import java.time.Instant;

public record MemoryPublishedMessage(
        String topic,
        String key,
        String payload,
        Instant publishedAt
) {
}
