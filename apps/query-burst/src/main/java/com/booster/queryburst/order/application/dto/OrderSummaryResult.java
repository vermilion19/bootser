package com.booster.queryburst.order.application.dto;

import com.booster.queryburst.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderSummaryResult(
        Long orderId,
        Long memberId,
        String memberName,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt
) {
}
