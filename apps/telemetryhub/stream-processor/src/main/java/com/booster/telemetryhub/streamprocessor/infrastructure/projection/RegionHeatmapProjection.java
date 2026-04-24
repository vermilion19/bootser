package com.booster.telemetryhub.streamprocessor.infrastructure.projection;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapKey;
import org.springframework.stereotype.Component;

@Component
public class RegionHeatmapProjection {

    private final StreamProcessorProperties properties;

    public RegionHeatmapProjection(StreamProcessorProperties properties) {
        this.properties = properties;
    }

    public RegionHeatmapKey project(RawEventMessage rawEventMessage) {
        if (rawEventMessage.eventType() != EventType.TELEMETRY) {
            return null;
        }

        TelemetryEvent telemetryEvent = JsonUtils.fromJson(rawEventMessage.payload(), TelemetryEvent.class);
        return RegionHeatmapKey.of(
                telemetryEvent.lat(),
                telemetryEvent.lon(),
                properties.getHeatmapGridSize(),
                rawEventMessage.eventTime()
        );
    }
}
