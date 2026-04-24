package com.booster.telemetryhub.contracts.common;

import java.time.Instant;

public record EventMetadata(
        String eventId,
        String deviceId,
        EventType eventType,
        Instant eventTime,
        Instant ingestTime
) {
}
