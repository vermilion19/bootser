package com.booster.queryburstmsa.contracts.inventory;

public record InventoryReservationResultItem(
        Long productId,
        Long categoryId,
        int quantity,
        long unitPrice
) {
}
