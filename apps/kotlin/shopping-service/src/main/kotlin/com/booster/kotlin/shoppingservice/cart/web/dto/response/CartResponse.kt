package com.booster.kotlin.shoppingservice.cart.web.dto.response

import com.booster.kotlin.shoppingservice.cart.domain.Cart

data class CartResponse(
    val cartId: Long,
    val items: List<CartItemResponse>,
    val totalPrice: Long,
) {
    data class CartItemResponse(
        val cartItemId: Long,
        val variantId: Long,
        val quantity: Int,
        val unitPrice: Long,
        val totalPrice: Long,
    )

    companion object {
        fun from(cart: Cart) = CartResponse(
            cartId = cart.id,
            items = cart.findActiveItems().map {
                CartItemResponse(
                    cartItemId = it.id,
                    variantId = it.variantId,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    totalPrice = it.totalPrice(),
                )
            },
            totalPrice = cart.totalPrice(),
        )
    }
}