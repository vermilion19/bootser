package com.booster.telemetryhub.analyticsapi.application.query;

import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;

import java.time.Instant;

public record DrivingEventCounterView(
        String deviceId,
        DrivingEventType drivingEventType,
        Instant minuteBucketStart,
        long eventCount
) {
}
