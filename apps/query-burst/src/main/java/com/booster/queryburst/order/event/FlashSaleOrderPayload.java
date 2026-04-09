package com.booster.queryburst.order.event;

import java.time.LocalDateTime;

public record FlashSaleOrderPayload(
        String eventType,
        Long orderId,
        Long memberId,
        Long productId,
        int quantity,
        LocalDateTime requestedAt
) {
    public static final String EVENT_TYPE = "FLASH_SALE_ORDER_REQUESTED";

    public static FlashSaleOrderPayload of(Long orderId, Long memberId, Long productId, int quantity) {
        return new FlashSaleOrderPayload(
                EVENT_TYPE,
                orderId,
                memberId,
                productId,
                quantity,
                LocalDateTime.now()
        );
    }
}
