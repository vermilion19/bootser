package com.booster.kotlin.shoppingservice.coupon.domain

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.coupon.exception.CouponException
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
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.EntityListeners
import java.time.LocalDateTime

@Entity
@Table(name = "coupons")
@EntityListeners(AuditingEntityListener::class)
class Coupon(
    @Column(nullable = false, unique = true) val code: String,
    @Column(nullable = false) val name: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val couponType: CouponType,
    @Column(nullable = false) val discountValue: Long,      // FIXED: 원, RATE: % (1~100)
    @Column val maxDiscountAmount: Long?,                   // RATE 전용 할인 상한
    @Column(nullable = false) val minOrderAmount: Long,     // 최소 주문 금액
    @Column(nullable = false) val startAt: LocalDateTime,
    @Column(nullable = false) val endAt: LocalDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CouponStatus = CouponStatus.ACTIVE
        private set

    @CreatedDate
    @Column(updatable = false)
    lateinit var createdAt: LocalDateTime

    @OneToMany(mappedBy = "coupon", cascade = [CascadeType.ALL], orphanRemoval = true)
    val productTargets: MutableList<CouponProductTarget> = mutableListOf()

    @OneToMany(mappedBy = "coupon", cascade = [CascadeType.ALL], orphanRemoval = true)
    val categoryTargets: MutableList<CouponCategoryTarget> = mutableListOf()

    fun isAvailable(): Boolean =
        status == CouponStatus.ACTIVE && LocalDateTime.now() in startAt..endAt

    fun deactivate() {
        status = CouponStatus.INACTIVE
    }

    /**
     * 주문 금액에 대한 실제 할인 금액 계산.
     * - FIXED: discountValue 원 (주문 금액 초과 불가)
     * - RATE: 주문 금액 × discountValue% (maxDiscountAmount 상한 적용)
     */
    fun calculateDiscount(orderAmount: Long): Long {
        if (orderAmount < minOrderAmount) {
            throw CouponException(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET)
        }
        return when (couponType) {
            CouponType.FIXED -> discountValue.coerceAtMost(orderAmount)
            CouponType.RATE -> {
                val discount = orderAmount * discountValue / 100
                maxDiscountAmount?.let { discount.coerceAtMost(it) } ?: discount
            }
        }
    }

    companion object {
        fun create(
            code: String,
            name: String,
            couponType: CouponType,
            discountValue: Long,
            maxDiscountAmount: Long?,
            minOrderAmount: Long,
            startAt: LocalDateTime,
            endAt: LocalDateTime,
        ) = Coupon(code, name, couponType, discountValue, maxDiscountAmount, minOrderAmount, startAt, endAt)
    }
}