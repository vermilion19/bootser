package com.booster.telemetryhub.batchbackfill.infrastructure;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record FileBackfillRawEventRecord(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        String payload
) {
}
