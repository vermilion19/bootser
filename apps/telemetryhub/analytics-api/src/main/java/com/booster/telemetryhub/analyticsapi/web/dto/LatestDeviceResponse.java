package com.booster.telemetryhub.analyticsapi.web.dto;

import com.booster.telemetryhub.analyticsapi.application.query.LatestDeviceView;
import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record LatestDeviceResponse(
        String deviceId,
        String lastEventId,
        EventType lastEventType,
        Instant lastEventTime,
        Instant lastIngestTime,
        String sourceTopic
) {
    public static LatestDeviceResponse from(LatestDeviceView view) {
        if (view == null) {
            return null;
        }

        return new LatestDeviceResponse(
                view.deviceId(),
                view.lastEventId(),
                view.lastEventType(),
                view.lastEventTime(),
                view.lastIngestTime(),
                view.sourceTopic()
        );
    }
}
