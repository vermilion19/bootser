package com.booster.queryburstmsa.order.web.dto;

import com.booster.queryburstmsa.order.domain.OrderStatus;
import com.booster.queryburstmsa.order.domain.entity.OrderEntity;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long orderId,
        Long memberId,
        OrderStatus status,
        long totalAmount,
        LocalDateTime orderedAt
) {
    public static OrderSummaryResponse from(OrderEntity order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt()
        );
    }
}
