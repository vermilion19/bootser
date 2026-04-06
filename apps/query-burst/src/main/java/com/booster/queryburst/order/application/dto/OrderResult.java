package com.booster.queryburst.order.application.dto;

public record OrderResult(
        Long orderId,
        Long totalAmount
) {}
