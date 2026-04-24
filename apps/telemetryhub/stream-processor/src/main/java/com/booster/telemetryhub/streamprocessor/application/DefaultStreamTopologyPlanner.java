package com.booster.telemetryhub.streamprocessor.application;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultStreamTopologyPlanner implements StreamTopologyPlanner {

    private final StreamProcessorProperties properties;

    public DefaultStreamTopologyPlanner(StreamProcessorProperties properties) {
        this.properties = properties;
    }

    @Override
    public StreamTopologyPlan plan() {
        return new StreamTopologyPlan(
                properties.getApplicationId(),
                properties.getSourceTopic(),
                List.of(
                        deviceLastSeenPlan(),
                        eventsPerMinutePlan(),
                        drivingEventCounterPlan()
                )
        );
    }

    private AggregationPlan deviceLastSeenPlan() {
        return new AggregationPlan(
                AggregationType.DEVICE_LAST_SEEN,
                List.of(EventType.TELEMETRY, EventType.DEVICE_HEALTH, EventType.DRIVING_EVENT),
                properties.getDeviceLastSeenWindow(),
                properties.getLateEventGrace(),
                "device-last-seen-store",
                "device_last_seen",
                "Keeps the latest observed eventTime and ingestTime per device for online/offline determination."
        );
    }

    private AggregationPlan eventsPerMinutePlan() {
        return new AggregationPlan(
                AggregationType.EVENTS_PER_MINUTE,
                List.of(EventType.TELEMETRY, EventType.DEVICE_HEALTH, EventType.DRIVING_EVENT),
                properties.getEventsPerMinuteWindow(),
                properties.getLateEventGrace(),
                "events-per-minute-store",
                "events_per_minute",
                "Counts all inbound events per minute grouped by event type."
        );
    }

    private AggregationPlan drivingEventCounterPlan() {
        return new AggregationPlan(
                AggregationType.DRIVING_EVENT_COUNTER,
                List.of(EventType.DRIVING_EVENT),
                properties.getDrivingEventCounterWindow(),
                properties.getLateEventGrace(),
                "driving-event-counter-store",
                "driving_event_counter",
                "Counts HARD_BRAKE, OVERSPEED, and CRASH events per device and time window."
        );
    }
}
