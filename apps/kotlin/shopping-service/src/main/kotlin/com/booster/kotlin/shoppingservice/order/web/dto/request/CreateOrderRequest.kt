package com.booster.kotlin.shoppingservice.order.web.dto.request

import com.booster.kotlin.shoppingservice.order.application.dto.CreateOrderCommand
import jakarta.validation.constraints.NotNull

data class CreateOrderRequest(
    @field:NotNull val addressId: Long,
) {
    fun toCommand(userId: Long) = CreateOrderCommand(userId, addressId)
}
