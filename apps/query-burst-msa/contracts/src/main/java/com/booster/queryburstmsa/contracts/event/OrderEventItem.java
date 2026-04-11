package com.booster.queryburstmsa.contracts.event;

public record OrderEventItem(
        Long productId,
        Long categoryId,
        int quantity,
        Long unitPrice
) {
}
