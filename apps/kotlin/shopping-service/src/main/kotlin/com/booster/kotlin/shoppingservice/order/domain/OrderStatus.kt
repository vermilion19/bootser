package com.booster.kotlin.shoppingservice.order.domain

enum class OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    CANCELED,
    REFUND_REQUESTED,
    REFUNDED;

    fun canCancel() = this in setOf(CREATED, PAYMENT_PENDING, PAID)
    fun canRequestRefund() = this == DELIVERED
}
