package com.booster.telemetryhub.ingestion.application;

import java.time.Instant;

public record IngestionMessage(
        String topic,
        int qos,
        String payload,
        Instant receivedAt
) {
}
