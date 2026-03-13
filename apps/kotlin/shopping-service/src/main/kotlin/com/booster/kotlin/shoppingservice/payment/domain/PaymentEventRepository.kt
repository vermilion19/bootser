package com.booster.kotlin.shoppingservice.payment.domain

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentEventRepository : JpaRepository<PaymentEvent, Long> {
    fun findByPaymentIdOrderByCreatedAtAsc(paymentId: Long): List<PaymentEvent>
    fun existsByProviderEventId(providerEventId: String): Boolean
}