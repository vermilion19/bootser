package com.booster.kotlin.shoppingservice.cart.web.dto.request

import com.booster.kotlin.shoppingservice.cart.application.dto.UpdateCartItemCommand
import jakarta.validation.constraints.Min

data class UpdateCartItemRequest(
    @field:Min(1) val quantity: Int,
) {
    fun toCommand(userId: Long, cartItemId: Long) =
        UpdateCartItemCommand(userId, cartItemId, quantity)
}
