package com.booster.kotlin.shoppingservice.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.EntityListeners
import java.time.LocalDateTime

@Entity
@Table(name = "payment_events")
@EntityListeners(AuditingEntityListener::class)
class PaymentEvent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    val payment: Payment,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: PaymentEventType,

    // PG사 이벤트 ID (Webhook 수신 시 세팅, Mock일 경우 null)
    @Column
    val providerEventId: String? = null,

    // Webhook payload 또는 PG 응답 원문 (JSON 문자열)
    @Column(columnDefinition = "TEXT")
    val payloadJson: String? = null,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreatedDate
    @Column(updatable = false)
    lateinit var createdAt: LocalDateTime

    companion object {
        fun create(
            payment: Payment,
            eventType: PaymentEventType,
            providerEventId: String? = null,
            payloadJson: String? = null,
        ) = PaymentEvent(payment, eventType, providerEventId, payloadJson)
    }
}