package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record DrivingEventCounterKey(
        String deviceId,
        DrivingEventType drivingEventType,
        Instant minuteBucketStart
) {
    public static DrivingEventCounterKey of(
            String deviceId,
            DrivingEventType drivingEventType,
            Instant eventTime
    ) {
        return new DrivingEventCounterKey(
                deviceId,
                drivingEventType,
                eventTime.truncatedTo(ChronoUnit.MINUTES)
        );
    }
}
