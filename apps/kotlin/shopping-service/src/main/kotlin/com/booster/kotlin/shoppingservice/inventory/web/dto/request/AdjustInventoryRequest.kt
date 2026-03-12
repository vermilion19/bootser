package com.booster.kotlin.shoppingservice.inventory.web.dto.request

import com.booster.kotlin.shoppingservice.inventory.application.dto.AdjustInventoryCommand
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistory

data class AdjustInventoryRequest(
    val amount: Int,
    val reason: InventoryHistory.ChangeReason,
) {
    fun toCommand(inventoryId: Long) = AdjustInventoryCommand(inventoryId, amount, reason)
}
