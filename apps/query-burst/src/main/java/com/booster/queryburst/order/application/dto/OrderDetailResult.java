package com.booster.queryburst.order.application.dto;

import com.booster.queryburst.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResult(
        Long orderId,
        Long memberId,
        String memberName,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt,
        List<OrderItemResult> items
) {
}
