package com.booster.queryburst.order.application.dto;

import com.booster.queryburst.order.domain.outbox.OutboxEvent;
import com.booster.queryburst.order.domain.outbox.OutboxStatus;

import java.time.LocalDateTime;

public record OutboxEventResult(
        Long id,
        String aggregateType,
        Long aggregateId,
        String eventType,
        OutboxStatus status,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
    public static OutboxEventResult from(OutboxEvent event) {
        return new OutboxEventResult(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getStatus(),
                event.getRetryCount(),
                event.getCreatedAt(),
                event.getPublishedAt()
        );
    }
}
