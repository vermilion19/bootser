package com.booster.telemetryhub.devicesimulator.infrastructure.message;

import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import org.springframework.stereotype.Component;

@Component
public class TelemetryTopicResolver {

    public String telemetryTopic(TelemetryEvent event) {
        return "telemetryhub/devices/%s/telemetry".formatted(event.metadata().deviceId());
    }

    public String deviceHealthTopic(DeviceHealthEvent event) {
        return "telemetryhub/devices/%s/device-health".formatted(event.metadata().deviceId());
    }

    public String drivingEventTopic(DrivingEvent event) {
        return "telemetryhub/devices/%s/driving-event".formatted(event.metadata().deviceId());
    }
}
