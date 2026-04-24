package com.booster.telemetryhub.devicesimulator.application;

import com.booster.telemetryhub.contracts.common.EventMetadata;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeviceEventPreviewService {

    public SimulationEventBatch generatePreview(SimulatorRuntimeState runtimeState, int count) {
        List<TelemetryEvent> telemetryEvents = new ArrayList<>(count);
        List<DeviceHealthEvent> deviceHealthEvents = new ArrayList<>(count);
        List<DrivingEvent> drivingEvents = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String deviceId = "device-%05d".formatted(i + 1);
            telemetryEvents.add(createTelemetryEvent(deviceId, runtimeState.scenario()));
            deviceHealthEvents.add(createDeviceHealthEvent(deviceId, runtimeState.scenario()));

            DrivingEvent drivingEvent = createDrivingEvent(deviceId, runtimeState.scenario(), runtimeState.scenarioPercent());
            if (drivingEvent != null) {
                drivingEvents.add(drivingEvent);
            }
        }

        return new SimulationEventBatch(telemetryEvents, deviceHealthEvents, drivingEvents);
    }

    private TelemetryEvent createTelemetryEvent(String deviceId, SimulationScenario scenario) {
        double lat = randomBetween(37.4900, 37.5900);
        double lon = randomBetween(126.9200, 127.0800);
        double speed = randomBetween(0, 110);
        double heading = randomBetween(0, 359);
        double accelX = randomBetween(-3, 3);
        double accelY = randomBetween(-3, 3);

        if (scenario == SimulationScenario.LATE_EVENT) {
            speed = randomBetween(20, 60);
        }

        if (scenario == SimulationScenario.RECONNECT_STORM) {
            speed = randomBetween(0, 20);
        }

        return new TelemetryEvent(
                metadata(deviceId, EventType.TELEMETRY, scenario),
                lat,
                lon,
                speed,
                heading,
                accelX,
                accelY
        );
    }

    private DeviceHealthEvent createDeviceHealthEvent(String deviceId, SimulationScenario scenario) {
        double battery = randomBetween(15, 100);
        double temperature = randomBetween(20, 42);
        int signalStrength = ThreadLocalRandom.current().nextInt(1, 5);
        String firmwareVersion = "v1.0.%d".formatted(ThreadLocalRandom.current().nextInt(1, 10));
        String errorCode = null;

        if (scenario == SimulationScenario.RECONNECT_STORM) {
            signalStrength = ThreadLocalRandom.current().nextInt(1, 3);
        }

        if (scenario == SimulationScenario.DUPLICATE_EVENT) {
            errorCode = "DUPLICATE_RISK";
        }

        return new DeviceHealthEvent(
                metadata(deviceId, EventType.DEVICE_HEALTH, scenario),
                battery,
                temperature,
                signalStrength,
                firmwareVersion,
                errorCode
        );
    }

    private DrivingEvent createDrivingEvent(String deviceId, SimulationScenario scenario, int scenarioPercent) {
        int threshold = scenarioPercent > 0 ? scenarioPercent : 15;
        if (ThreadLocalRandom.current().nextInt(100) >= threshold) {
            return null;
        }

        DrivingEventType type = switch (scenario) {
            case RECONNECT_STORM -> DrivingEventType.HARD_BRAKE;
            case LATE_EVENT -> DrivingEventType.OVERSPEED;
            case DUPLICATE_EVENT -> DrivingEventType.CRASH;
            case STEADY -> randomDrivingEventType();
        };

        int severity = switch (type) {
            case HARD_BRAKE -> ThreadLocalRandom.current().nextInt(2, 6);
            case OVERSPEED -> ThreadLocalRandom.current().nextInt(1, 5);
            case CRASH -> ThreadLocalRandom.current().nextInt(4, 6);
        };

        return new DrivingEvent(
                metadata(deviceId, EventType.DRIVING_EVENT, scenario),
                type,
                severity,
                "scenario=%s".formatted(scenario.name())
        );
    }

    private EventMetadata metadata(String deviceId, EventType eventType, SimulationScenario scenario) {
        Instant ingestTime = Instant.now();
        Instant eventTime = scenario == SimulationScenario.LATE_EVENT
                ? ingestTime.minusSeconds(ThreadLocalRandom.current().nextLong(30, 180))
                : ingestTime;

        return new EventMetadata(
                UUID.randomUUID().toString(),
                deviceId,
                eventType,
                eventTime,
                ingestTime
        );
    }

    private DrivingEventType randomDrivingEventType() {
        DrivingEventType[] values = DrivingEventType.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private double randomBetween(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
