package com.booster.kotlin.shoppingservice.cart.application.dto

data class AddCartItemCommand(
    val userId: Long,
    val variantId: Long,
    val quantity: Int,
)

data class UpdateCartItemCommand(
    val userId: Long,
    val cartItemId: Long,
    val quantity: Int,
)
