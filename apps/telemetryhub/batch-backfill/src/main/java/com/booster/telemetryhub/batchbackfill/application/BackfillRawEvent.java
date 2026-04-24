package com.booster.telemetryhub.batchbackfill.application;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record BackfillRawEvent(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        String payload
) {
}
