package com.booster.queryburst.order.web.dto.response;

import com.booster.queryburst.order.application.dto.OrderResult;

public record OrderResponse(
        Long orderId,
        Long totalAmount
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(result.orderId(), result.totalAmount());
    }
}
