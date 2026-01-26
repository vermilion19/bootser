package com.booster.firstcomefirstserved.order.domain.event;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        String orderId,
        Long userId,
        Long itemId,
        int quantity,
        LocalDateTime occurredAt
) {
    public static OrderCreatedEvent of(String orderId, Long userId, Long itemId, int quantity) {
        return new OrderCreatedEvent(orderId, userId, itemId, quantity, LocalDateTime.now());
    }
}
