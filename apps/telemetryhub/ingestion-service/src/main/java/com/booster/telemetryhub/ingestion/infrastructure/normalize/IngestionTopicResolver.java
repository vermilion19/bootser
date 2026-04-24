package com.booster.telemetryhub.ingestion.infrastructure.normalize;

import com.booster.core.webflux.exception.CommonErrorCode;
import com.booster.core.webflux.exception.CoreException;
import com.booster.telemetryhub.contracts.common.EventType;
import org.springframework.stereotype.Component;

@Component
public class IngestionTopicResolver {

    public EventType resolve(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "topic must not be blank");
        }

        if (topic.endsWith("/telemetry")) {
            return EventType.TELEMETRY;
        }
        if (topic.endsWith("/device-health")) {
            return EventType.DEVICE_HEALTH;
        }
        if (topic.endsWith("/driving-event")) {
            return EventType.DRIVING_EVENT;
        }

        throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "unsupported topic: " + topic);
    }
}
