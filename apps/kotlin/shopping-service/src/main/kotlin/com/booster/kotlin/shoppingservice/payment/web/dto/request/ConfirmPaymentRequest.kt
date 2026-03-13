package com.booster.kotlin.shoppingservice.payment.web.dto.request

import com.booster.kotlin.shoppingservice.payment.application.dto.ConfirmPaymentCommand
import com.booster.kotlin.shoppingservice.payment.domain.PaymentMethod
import com.booster.kotlin.shoppingservice.payment.domain.PaymentProvider
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class ConfirmPaymentRequest(
    @field:NotNull val orderId: Long,
    @field:NotNull val provider: PaymentProvider,
    @field:NotNull val method: PaymentMethod,
    @field:NotBlank val idempotencyKey: String,
    @field:Positive val requestedAmount: Long,
    val simulateSuccess: Boolean = true,   // Mock 전용: false 이면 결제 실패 시뮬레이션
) {
    fun toCommand(userId: Long) = ConfirmPaymentCommand(
        userId = userId,
        orderId = orderId,
        provider = provider,
        method = method,
        idempotencyKey = idempotencyKey,
        requestedAmount = requestedAmount,
        simulateSuccess = simulateSuccess,
    )
}