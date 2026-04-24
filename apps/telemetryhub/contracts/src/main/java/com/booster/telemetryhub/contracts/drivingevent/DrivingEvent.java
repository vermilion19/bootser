package com.booster.telemetryhub.contracts.drivingevent;

import com.booster.telemetryhub.contracts.common.EventMetadata;

public record DrivingEvent(
        EventMetadata metadata,
        DrivingEventType type,
        int severity,
        String context
) {
}
