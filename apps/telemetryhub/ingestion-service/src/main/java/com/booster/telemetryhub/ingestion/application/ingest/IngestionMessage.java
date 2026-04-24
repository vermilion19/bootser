package com.booster.telemetryhub.ingestion.application.ingest;

import java.time.Instant;

public record IngestionMessage(
        String topic,
        int qos,
        String payload,
        Instant receivedAt
) {
}
