package com.booster.kotlin.shoppingservice.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.EntityListeners
import java.time.LocalDateTime

@Entity
@Table(
    name = "order_coupon_usages",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_coupon_id"])], // 쿠폰 1회 사용 보장
)
@EntityListeners(AuditingEntityListener::class)
class OrderCouponUsage(
    @Column(nullable = false) val orderId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id", nullable = false)
    val userCoupon: UserCoupon,

    @Column(nullable = false) val couponCodeSnapshot: String,
    @Column(nullable = false) val couponNameSnapshot: String,
    @Column(nullable = false) val discountAmount: Long,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreatedDate
    @Column(updatable = false)
    lateinit var createdAt: LocalDateTime

    companion object {
        fun create(
            orderId: Long,
            userCoupon: UserCoupon,
            discountAmount: Long,
        ) = OrderCouponUsage(
            orderId = orderId,
            userCoupon = userCoupon,
            couponCodeSnapshot = userCoupon.coupon.code,
            couponNameSnapshot = userCoupon.coupon.name,
            discountAmount = discountAmount,
        )
    }
}