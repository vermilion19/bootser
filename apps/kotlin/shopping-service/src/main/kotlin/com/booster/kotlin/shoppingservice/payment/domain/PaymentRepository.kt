package com.booster.kotlin.shoppingservice.payment.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<Payment>
    fun findByOrderId(orderId: Long): List<Payment>
    fun findByPaymentKey(paymentKey: String): Optional<Payment>
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
}