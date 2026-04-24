package com.booster.telemetryhub.ingestion.application.normalize;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record NormalizedRawEvent(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        Instant ingestTime,
        String sourceTopic,
        String kafkaKey,
        String payload
) {
}
