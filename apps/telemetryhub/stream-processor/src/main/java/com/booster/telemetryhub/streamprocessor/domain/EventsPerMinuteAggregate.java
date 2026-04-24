package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record EventsPerMinuteAggregate(
        EventType eventType,
        Instant minuteBucketStart,
        long count
) {
    public static EventsPerMinuteAggregate first(EventsPerMinuteKey key) {
        return new EventsPerMinuteAggregate(
                key.eventType(),
                key.minuteBucketStart(),
                1
        );
    }

    public EventsPerMinuteAggregate increment() {
        return new EventsPerMinuteAggregate(
                eventType,
                minuteBucketStart,
                count + 1
        );
    }
}
