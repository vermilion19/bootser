package com.booster.telemetryhub.devicesimulator.web.dto;

import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import com.booster.telemetryhub.devicesimulator.application.SimulationEventBatch;

import java.util.List;

public record SimulatorPreviewResponse(
        List<TelemetryEvent> telemetryEvents,
        List<DeviceHealthEvent> deviceHealthEvents,
        List<DrivingEvent> drivingEvents
) {
    public static SimulatorPreviewResponse from(SimulationEventBatch batch) {
        return new SimulatorPreviewResponse(
                batch.telemetryEvents(),
                batch.deviceHealthEvents(),
                batch.drivingEvents()
        );
    }
}
