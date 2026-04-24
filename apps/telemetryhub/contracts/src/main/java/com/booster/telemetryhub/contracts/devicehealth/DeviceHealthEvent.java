package com.booster.telemetryhub.contracts.devicehealth;

import com.booster.telemetryhub.contracts.common.EventMetadata;

public record DeviceHealthEvent(
        EventMetadata metadata,
        double battery,
        double temperature,
        int signalStrength,
        String firmwareVersion,
        String errorCode
) {
}
