package com.booster.queryburstmsa.order.web.dto;

import java.util.List;

public record OrderCreateRequest(
        Long memberId,
        List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            Long productId,
            int quantity,
            long unitPrice
    ) {
    }
}
