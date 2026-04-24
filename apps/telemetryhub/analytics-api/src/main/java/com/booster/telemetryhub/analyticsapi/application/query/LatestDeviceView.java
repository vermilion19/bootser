package com.booster.telemetryhub.analyticsapi.application.query;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record LatestDeviceView(
        String deviceId,
        String lastEventId,
        EventType lastEventType,
        Instant lastEventTime,
        Instant lastIngestTime,
        String sourceTopic
) {
}
