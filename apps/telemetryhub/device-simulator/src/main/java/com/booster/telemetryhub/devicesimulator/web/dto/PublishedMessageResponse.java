package com.booster.telemetryhub.devicesimulator.web.dto;

import com.booster.telemetryhub.devicesimulator.infrastructure.MemoryPublishedMessage;

import java.time.Instant;

public record PublishedMessageResponse(
        String topic,
        String key,
        String payload,
        Instant publishedAt
) {
    public static PublishedMessageResponse from(MemoryPublishedMessage message) {
        return new PublishedMessageResponse(
                message.topic(),
                message.key(),
                message.payload(),
                message.publishedAt()
        );
    }
}
