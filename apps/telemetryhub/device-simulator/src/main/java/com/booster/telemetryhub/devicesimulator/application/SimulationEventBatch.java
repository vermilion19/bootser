package com.booster.telemetryhub.devicesimulator.application;

import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;

import java.util.List;

public record SimulationEventBatch(
        List<TelemetryEvent> telemetryEvents,
        List<DeviceHealthEvent> deviceHealthEvents,
        List<DrivingEvent> drivingEvents
) {
    public int totalCount() {
        return telemetryEvents.size() + deviceHealthEvents.size() + drivingEvents.size();
    }
}
