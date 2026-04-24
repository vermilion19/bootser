package com.booster.telemetryhub.batchbackfill.application.io;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record BackfillRawEvent(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        Instant ingestTime,
        String payload
) {
}
