package com.booster.kotlin.shoppingservice.inventory.web.dto.response

import com.booster.kotlin.shoppingservice.inventory.domain.Inventory

data class InventoryResponse(
    val id: Long,
    val variantId: Long,
    val quantity: Int,
) {
    companion object {
        fun from(inventory: Inventory) = InventoryResponse(
            id = inventory.id,
            variantId = inventory.variantId,
            quantity = inventory.quantity,
        )
    }
}
