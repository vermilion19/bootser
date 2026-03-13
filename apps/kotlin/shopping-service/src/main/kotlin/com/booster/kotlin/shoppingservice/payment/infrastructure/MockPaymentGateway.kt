package com.booster.kotlin.shoppingservice.payment.infrastructure

import com.booster.kotlin.shoppingservice.payment.domain.PaymentMethod
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 실제 PG 연동 없이 성공/실패를 시뮬레이션하는 Mock 결제 게이트웨이.
 * simulateSuccess=true → 결제 승인
 * simulateSuccess=false → 결제 실패
 */
@Component
class MockPaymentGateway {

    fun confirm(
        orderId: Long,
        amount: Long,
        method: PaymentMethod,
        simulateSuccess: Boolean,
    ): MockPaymentResult {
        return if (simulateSuccess) {
            MockPaymentResult(
                success = true,
                paymentKey = "mock_${orderId}_${UUID.randomUUID()}",
                approvedAmount = amount,
            )
        } else {
            MockPaymentResult(
                success = false,
                failureReason = "[Mock] 결제 실패 시뮬레이션 — 카드사 거절",
            )
        }
    }

    data class MockPaymentResult(
        val success: Boolean,
        val paymentKey: String? = null,
        val approvedAmount: Long? = null,
        val failureReason: String? = null,
    )
}