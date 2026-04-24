package com.booster.telemetryhub.analyticsapi.web.dto;

import com.booster.telemetryhub.analyticsapi.application.query.DrivingEventCounterView;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;

import java.time.Instant;

public record DrivingEventCounterResponse(
        String deviceId,
        DrivingEventType drivingEventType,
        Instant minuteBucketStart,
        long eventCount
) {
    public static DrivingEventCounterResponse from(DrivingEventCounterView view) {
        return new DrivingEventCounterResponse(
                view.deviceId(),
                view.drivingEventType(),
                view.minuteBucketStart(),
                view.eventCount()
        );
    }
}
