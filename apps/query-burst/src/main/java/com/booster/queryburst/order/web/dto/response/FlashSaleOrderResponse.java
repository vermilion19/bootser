package com.booster.queryburst.order.web.dto.response;

import com.booster.queryburst.order.application.dto.OrderResult;

public record FlashSaleOrderResponse(
        Long orderId,
        Long totalAmount,
        String status
) {
    public static FlashSaleOrderResponse from(OrderResult result) {
        return new FlashSaleOrderResponse(result.orderId(), result.totalAmount(), "ACCEPTED");
    }
}
