package com.booster.queryburst.order.application.dto;

public record OrderItemCommand(
        Long productId,
        int quantity
) {}
