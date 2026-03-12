package com.booster.kotlin.shoppingservice.inventory.application.dto

import com.booster.kotlin.shoppingservice.inventory.domain.InventoryHistory

data class AdjustInventoryCommand(
    val inventoryId: Long,
    val amount: Int,
    val reason: InventoryHistory.ChangeReason,
)
