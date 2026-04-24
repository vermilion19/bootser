package com.booster.telemetryhub.streamprocessor.domain;

import com.booster.telemetryhub.contracts.common.EventType;

import java.time.Instant;

public record DeviceLastSeenAggregate(
        String deviceId,
        String lastEventId,
        EventType lastEventType,
        Instant lastEventTime,
        Instant lastIngestTime,
        String sourceTopic
) {
    public static DeviceLastSeenAggregate from(RawEventMessage event) {
        return new DeviceLastSeenAggregate(
                event.deviceId(),
                event.eventId(),
                event.eventType(),
                event.eventTime(),
                event.ingestTime(),
                event.sourceTopic()
        );
    }

    public DeviceLastSeenAggregate merge(DeviceLastSeenAggregate other) {
        if (other == null) {
            return this;
        }

        boolean otherIsNewer = other.lastEventTime().isAfter(lastEventTime())
                || (other.lastEventTime().equals(lastEventTime()) && other.lastIngestTime().isAfter(lastIngestTime()));

        return otherIsNewer ? other : this;
    }
}
