package com.booster.kotlin.shoppingservice.payment.domain

enum class PaymentEventType {
    PAYMENT_REQUESTED,
    PAYMENT_APPROVED,
    PAYMENT_FAILED,
    PAYMENT_CANCELED,
    REFUND_REQUESTED,
    REFUND_COMPLETED,
    WEBHOOK_RECEIVED,
}