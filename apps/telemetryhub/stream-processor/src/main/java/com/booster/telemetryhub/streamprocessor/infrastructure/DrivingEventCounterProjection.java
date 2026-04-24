package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterKey;
import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import org.springframework.stereotype.Component;

@Component
public class DrivingEventCounterProjection {

    public DrivingEventCounterKey project(RawEventMessage rawEventMessage) {
        if (rawEventMessage.eventType() != EventType.DRIVING_EVENT) {
            return null;
        }

        DrivingEvent drivingEvent = JsonUtils.fromJson(rawEventMessage.payload(), DrivingEvent.class);
        return DrivingEventCounterKey.of(
                rawEventMessage.deviceId(),
                drivingEvent.type(),
                rawEventMessage.eventTime()
        );
    }
}
