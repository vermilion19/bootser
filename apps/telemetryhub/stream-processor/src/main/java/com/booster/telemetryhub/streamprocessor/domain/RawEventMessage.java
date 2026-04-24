package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record RawEventMessage(
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
