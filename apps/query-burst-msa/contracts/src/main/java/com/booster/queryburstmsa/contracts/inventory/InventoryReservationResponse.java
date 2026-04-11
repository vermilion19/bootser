package com.booster.queryburstmsa.contracts.inventory;

import java.util.List;

public record InventoryReservationResponse(
        String reservationId,
        InventoryReservationStatus status,
        String reason,
        List<InventoryReservationResultItem> items
) {
}
