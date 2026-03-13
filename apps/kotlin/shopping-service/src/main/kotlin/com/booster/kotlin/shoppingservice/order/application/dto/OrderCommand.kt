package com.booster.kotlin.shoppingservice.order.application.dto

data class CreateOrderCommand(
    val userId: Long,
    val addressId: Long,
    val userCouponId: Long? = null,
)

data class CancelOrderCommand(
    val userId: Long,
    val orderId: Long,
    val reason: String?,
)

