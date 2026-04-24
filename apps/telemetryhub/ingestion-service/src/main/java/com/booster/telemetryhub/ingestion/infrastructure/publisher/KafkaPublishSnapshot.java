package com.booster.telemetryhub.ingestion.infrastructure.publisher;

import java.time.Instant;

public record KafkaPublishSnapshot(
        boolean enabled,
        String rawTopic,
        long totalPublished,
        long totalFailed,
        Instant lastPublishedAt,
        String lastFailureReason
) {
    public static KafkaPublishSnapshot initial(boolean enabled, String rawTopic) {
        return new KafkaPublishSnapshot(enabled, rawTopic, 0, 0, null, null);
    }
}
