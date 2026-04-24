package com.booster.telemetryhub.devicesimulator.infrastructure;

import java.time.Instant;

public record MemoryPublishedMessage(
        String topic,
        String key,
        String payload,
        Instant publishedAt
) {
}
