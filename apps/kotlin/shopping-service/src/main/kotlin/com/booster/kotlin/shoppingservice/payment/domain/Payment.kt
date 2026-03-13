package com.booster.kotlin.shoppingservice.payment.domain

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.payment.exception.PaymentException
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class Payment(
    @Column(nullable = false) val orderId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val provider: PaymentProvider,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val method: PaymentMethod,
    @Column(nullable = false, unique = true) val idempotencyKey: String,
    @Column(nullable = false) val requestedAmount: Long,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.REQUESTED
        private set

    // PG사에서 발급한 결제 키 (승인 후 세팅)
    @Column(unique = true)
    var paymentKey: String? = null
        private set

    @Column
    var approvedAmount: Long? = null
        private set

    @Column
    var approvedAt: LocalDateTime? = null
        private set

    @Column
    var failedAt: LocalDateTime? = null
        private set

    @OneToMany(mappedBy = "payment", cascade = [CascadeType.ALL], orphanRemoval = true)
    val events: MutableList<PaymentEvent> = mutableListOf()

    fun approve(paymentKey: String, approvedAmount: Long) {
        check(status == PaymentStatus.REQUESTED) {
            throw PaymentException(ErrorCode.PAYMENT_INVALID_STATUS)
        }
        this.paymentKey = paymentKey
        this.approvedAmount = approvedAmount
        this.approvedAt = LocalDateTime.now()
        this.status = PaymentStatus.APPROVED
    }

    fun fail() {
        check(status == PaymentStatus.REQUESTED) {
            throw PaymentException(ErrorCode.PAYMENT_INVALID_STATUS)
        }
        this.failedAt = LocalDateTime.now()
        this.status = PaymentStatus.FAILED
    }

    fun cancel() {
        check(status.canCancel()) {
            throw PaymentException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED)
        }
        this.status = PaymentStatus.CANCELED
    }

    fun refund() {
        check(status.canRefund()) {
            throw PaymentException(ErrorCode.PAYMENT_REFUND_NOT_ALLOWED)
        }
        this.status = PaymentStatus.REFUNDED
    }

    fun addEvent(event: PaymentEvent) {
        events.add(event)
    }

    companion object {
        fun create(
            orderId: Long,
            provider: PaymentProvider,
            method: PaymentMethod,
            idempotencyKey: String,
            requestedAmount: Long,
        ) = Payment(orderId, provider, method, idempotencyKey, requestedAmount)
    }
}