package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;

import java.time.Instant;

public record DrivingEventCounterAggregate(
        String deviceId,
        DrivingEventType drivingEventType,
        Instant minuteBucketStart,
        long count
) {
    public static DrivingEventCounterAggregate first(DrivingEventCounterKey key) {
        return new DrivingEventCounterAggregate(
                key.deviceId(),
                key.drivingEventType(),
                key.minuteBucketStart(),
                1
        );
    }

    public DrivingEventCounterAggregate increment() {
        return new DrivingEventCounterAggregate(
                deviceId,
                drivingEventType,
                minuteBucketStart,
                count + 1
        );
    }
}
