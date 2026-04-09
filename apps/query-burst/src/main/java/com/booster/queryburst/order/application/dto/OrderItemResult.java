package com.booster.queryburst.order.application.dto;

public record OrderItemResult(
        Long orderItemId,
        Long productId,
        String productName,
        int quantity,
        Long unitPrice,
        Long totalPrice
) {
}