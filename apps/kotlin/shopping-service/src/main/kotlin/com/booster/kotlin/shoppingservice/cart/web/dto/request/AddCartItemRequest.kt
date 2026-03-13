package com.booster.kotlin.shoppingservice.cart.web.dto.request

import com.booster.kotlin.shoppingservice.cart.application.dto.AddCartItemCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class AddCartItemRequest(
    val variantId: Long,
    @field:Min(1) val quantity: Int,
) {
    fun toCommand(userId: Long) = AddCartItemCommand(userId, variantId, quantity)
}
