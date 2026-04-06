package com.booster.queryburst.order.web.dto.request;

public record OrderItemRequest(
        Long productId,
        int quantity
) {}
