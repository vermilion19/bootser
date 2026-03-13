package com.booster.kotlin.shoppingservice.payment.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.payment.application.PaymentService
import com.booster.kotlin.shoppingservice.payment.web.dto.request.ConfirmPaymentRequest
import com.booster.kotlin.shoppingservice.payment.web.dto.request.PaymentWebhookRequest
import com.booster.kotlin.shoppingservice.payment.web.dto.response.PaymentResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    /**
     * 결제 확인 (Mock PG 승인 요청).
     * simulateSuccess=false 전달 시 결제 실패 시뮬레이션.
     */
    @PostMapping("/confirm")
    fun confirm(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: ConfirmPaymentRequest,
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentService.confirm(request.toCommand(userId))
        return ResponseEntity.ok(ApiResponse.ok(PaymentResponse.from(payment)))
    }

    /**
     * Webhook 수신 (외부 PG → 서버 콜백).
     * 인증 불필요, providerEventId 기반 중복 처리 방지.
     */
    @PostMapping("/webhook")
    fun webhook(
        @RequestBody @Valid request: PaymentWebhookRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        paymentService.processWebhook(request.toCommand())
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    /**
     * 주문에 연결된 결제 이력 조회.
     */
    @GetMapping("/orders/{orderId}")
    fun getByOrder(
        @AuthenticationPrincipal userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<List<PaymentResponse>>> {
        val payments = paymentService.getByOrderId(userId, orderId).map { PaymentResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(payments))
    }
}