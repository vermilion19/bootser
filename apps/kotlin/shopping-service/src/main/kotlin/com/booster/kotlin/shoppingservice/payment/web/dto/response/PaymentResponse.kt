package com.booster.kotlin.shoppingservice.payment.web.dto.response

import com.booster.kotlin.shoppingservice.payment.domain.Payment
import com.booster.kotlin.shoppingservice.payment.domain.PaymentMethod
import com.booster.kotlin.shoppingservice.payment.domain.PaymentProvider
import com.booster.kotlin.shoppingservice.payment.domain.PaymentStatus
import java.time.LocalDateTime

data class PaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val provider: PaymentProvider,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val idempotencyKey: String,
    val paymentKey: String?,
    val requestedAmount: Long,
    val approvedAmount: Long?,
    val approvedAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            paymentId = payment.id,
            orderId = payment.orderId,
            provider = payment.provider,
            method = payment.method,
            status = payment.status,
            idempotencyKey = payment.idempotencyKey,
            paymentKey = payment.paymentKey,
            requestedAmount = payment.requestedAmount,
            approvedAmount = payment.approvedAmount,
            approvedAt = payment.approvedAt,
            failedAt = payment.failedAt,
        )
    }
}