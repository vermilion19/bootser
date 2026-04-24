package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record EventsPerMinuteKey(
        EventType eventType,
        Instant minuteBucketStart
) {
    public static EventsPerMinuteKey from(RawEventMessage event) {
        return new EventsPerMinuteKey(
                event.eventType(),
                event.eventTime().truncatedTo(ChronoUnit.MINUTES)
        );
    }
}
