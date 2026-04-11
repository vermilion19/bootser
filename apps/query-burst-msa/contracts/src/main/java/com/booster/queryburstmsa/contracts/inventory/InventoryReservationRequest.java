package com.booster.queryburstmsa.contracts.inventory;

import java.util.List;

public record InventoryReservationRequest(
        String requestId,
        Long orderId,
        Long memberId,
        List<InventoryReservationItem> items
) {
}
