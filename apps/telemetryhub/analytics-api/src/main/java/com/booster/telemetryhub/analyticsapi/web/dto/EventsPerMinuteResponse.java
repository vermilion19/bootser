package com.booster.telemetryhub.analyticsapi.web.dto;

import com.booster.telemetryhub.analyticsapi.application.query.EventsPerMinuteView;
import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record EventsPerMinuteResponse(
        EventType eventType,
        Instant minuteBucketStart,
        long eventCount
) {
    public static EventsPerMinuteResponse from(EventsPerMinuteView view) {
        return new EventsPerMinuteResponse(
                view.eventType(),
                view.minuteBucketStart(),
                view.eventCount()
        );
    }
}
