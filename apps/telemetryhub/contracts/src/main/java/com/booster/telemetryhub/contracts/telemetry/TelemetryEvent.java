package com.booster.telemetryhub.contracts.telemetry;

import com.booster.telemetryhub.contracts.common.EventMetadata;

public record TelemetryEvent(
        EventMetadata metadata,
        double lat,
        double lon,
        double speed,
        double heading,
        double accelX,
        double accelY
) {
}
