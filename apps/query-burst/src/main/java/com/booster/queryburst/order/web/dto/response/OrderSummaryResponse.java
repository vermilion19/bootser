package com.booster.queryburst.order.web.dto.response;

import com.booster.queryburst.order.application.dto.OrderSummaryResult;
import com.booster.queryburst.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long orderId,
        Long memberId,
        String memberName,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt
) {
    public static OrderSummaryResponse from(OrderSummaryResult result) {
        return new OrderSummaryResponse(
                result.orderId(),
                result.memberId(),
                result.memberName(),
                result.status(),
                result.totalAmount(),
                result.orderedAt()
        );
    }
}
