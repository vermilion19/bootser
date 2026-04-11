package com.booster.queryburstmsa.contracts.inventory;

public record InventoryReservationItem(
        Long productId,
        int quantity
) {
}
