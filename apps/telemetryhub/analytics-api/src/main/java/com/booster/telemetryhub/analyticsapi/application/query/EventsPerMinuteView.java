package com.booster.telemetryhub.analyticsapi.application.query;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record EventsPerMinuteView(
        EventType eventType,
        Instant minuteBucketStart,
        long eventCount
) {
}
