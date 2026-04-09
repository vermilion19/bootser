package com.booster.queryburst.order.web.dto.request;

public record FlashSaleOrderRequest(
        Long memberId,
        Long productId,
        int quantity
) {
}
