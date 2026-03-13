package com.booster.kotlin.shoppingservice.payment.application.dto

import com.booster.kotlin.shoppingservice.payment.domain.PaymentMethod
import com.booster.kotlin.shoppingservice.payment.domain.PaymentProvider

data class ConfirmPaymentCommand(
    val userId: Long,
    val orderId: Long,
    val provider: PaymentProvider,
    val method: PaymentMethod,
    val idempotencyKey: String,
    val requestedAmount: Long,
    val simulateSuccess: Boolean = true,
)

data class ProcessWebhookCommand(
    val eventType: String,
    val paymentKey: String,
    val providerEventId: String,
    val payloadJson: String?,
)