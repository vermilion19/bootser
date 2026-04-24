package com.booster.telemetryhub.ingestion.web.dto;

import com.booster.telemetryhub.ingestion.application.normalize.NormalizedRawEvent;

import java.time.Instant;

public record NormalizedRawEventResponse(
        String eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        Instant ingestTime,
        String sourceTopic,
        String kafkaKey,
        String payload
) {
    public static NormalizedRawEventResponse from(NormalizedRawEvent event) {
        return new NormalizedRawEventResponse(
                event.eventType().name(),
                event.eventId(),
                event.deviceId(),
                event.eventTime(),
                event.ingestTime(),
                event.sourceTopic(),
                event.kafkaKey(),
                event.payload()
        );
    }
}
