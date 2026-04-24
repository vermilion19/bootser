package com.booster.telemetryhub.devicesimulator.infrastructure.message;

import com.booster.common.JsonUtils;
import com.booster.telemetryhub.contracts.devicehealth.DeviceHealthEvent;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEvent;
import com.booster.telemetryhub.contracts.telemetry.TelemetryEvent;
import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SimulationEventSerializer {

    private final TelemetryTopicResolver topicResolver;

    public SimulationEventSerializer(TelemetryTopicResolver topicResolver) {
        this.topicResolver = topicResolver;
    }

    public List<MessageEnvelope> serialize(SimulationEventBatch batch) {
        List<MessageEnvelope> messages = new ArrayList<>(batch.totalCount());

        for (TelemetryEvent event : batch.telemetryEvents()) {
            messages.add(new MessageEnvelope(
                    topicResolver.telemetryTopic(event),
                    event.metadata().deviceId(),
                    toJson(event)
            ));
        }

        for (DeviceHealthEvent event : batch.deviceHealthEvents()) {
            messages.add(new MessageEnvelope(
                    topicResolver.deviceHealthTopic(event),
                    event.metadata().deviceId(),
                    toJson(event)
            ));
        }

        for (DrivingEvent event : batch.drivingEvents()) {
            messages.add(new MessageEnvelope(
                    topicResolver.drivingEventTopic(event),
                    event.metadata().deviceId(),
                    toJson(event)
            ));
        }

        return messages;
    }

    private String toJson(Object event) {
        return JsonUtils.toJson(event);
    }
}
