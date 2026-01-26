package com.booster.firstcomefirstserved.order.web.dto;

import com.booster.firstcomefirstserved.order.domain.OrderStatus;

public record OrderResponse(
        String orderId,
        OrderStatus status,
        String message
) {
    public static OrderResponse of(String orderId, OrderStatus status, String message) {
        return new OrderResponse(orderId, status, message);
    }
}
