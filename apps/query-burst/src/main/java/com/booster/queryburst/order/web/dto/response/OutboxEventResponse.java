package com.booster.queryburst.order.web.dto.response;

import com.booster.queryburst.order.application.dto.OutboxEventResult;
import com.booster.queryburst.order.domain.outbox.OutboxStatus;

import java.time.LocalDateTime;

public record OutboxEventResponse(
        Long id,
        String aggregateType,
        Long aggregateId,
        String eventType,
        OutboxStatus status,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime publishedAt
) {
    public static OutboxEventResponse from(OutboxEventResult result) {
        return new OutboxEventResponse(
                result.id(),
                result.aggregateType(),
                result.aggregateId(),
                result.eventType(),
                result.status(),
                result.retryCount(),
                result.createdAt(),
                result.publishedAt()
        );
    }
}
