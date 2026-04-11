package com.booster.queryburstmsa.contracts.event;

import java.time.LocalDateTime;
import java.util.List;

public record OrderEventPayload(
        OrderEventType eventType,
        Long orderId,
        Long memberId,
        Long totalAmount,
        String orderStatus,
        LocalDateTime occurredAt,
        List<OrderEventItem> items
) {
}
