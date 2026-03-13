package com.booster.kotlin.shoppingservice.order.domain

import com.booster.kotlin.shoppingservice.common.entity.BaseEntity
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

@Entity
@Table(name = "orders")
class Order(
    @Column(nullable = false) val userId: Long,
    // 배송지 스냅샷
    // 배송지 스냅샷
    @Column(nullable = false) val recipientName: String,
    @Column(nullable = false) val recipientPhone: String,
    @Column(nullable = false) val zipCode: String,
    @Column(nullable = false) val address1: String,
    @Column val address2: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED
        private set

    @Column(nullable = false)
    var totalPrice: Long = 0       // 상품 금액 합계
        private set

    @Column(nullable = false)
    var discountAmount: Long = 0   // 쿠폰 할인 금액
        private set

    val paymentAmount: Long get() = totalPrice - discountAmount

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()

    fun addItem(item: OrderItem) {
        items.add(item)
        totalPrice += item.totalPrice
    }

    fun applyDiscount(amount: Long) {
        require(amount > 0) { "할인 금액은 0보다 커야 합니다" }
        require(amount <= totalPrice) { "할인 금액이 주문 금액을 초과할 수 없습니다" }
        discountAmount = amount
    }

    fun toPaymentPending() {
        check(status == OrderStatus.CREATED) { "결제 대기 상태로 전환할 수 없습니다" }
        status = OrderStatus.PAYMENT_PENDING
    }

    fun pay() {
        check(status == OrderStatus.PAYMENT_PENDING) { "결제 완료 처리할 수 없습니다" }
        status = OrderStatus.PAID
    }

    fun fail() {
        check(status == OrderStatus.PAYMENT_PENDING) { "결제 실패 처리할 수 없습니다" }
        status = OrderStatus.PAYMENT_FAILED
    }

    fun cancel() {
        check(status.canCancel()) { "취소할 수 없는 주문 상태입니다" }
        status = OrderStatus.CANCELED
    }

    companion object {
        fun create(
            userId: Long,
            recipientName: String,
            recipientPhone: String,
            zipCode: String,
            address1: String,
            address2: String?,
        ) = Order(userId, recipientName, recipientPhone, zipCode, address1, address2)
    }
}
