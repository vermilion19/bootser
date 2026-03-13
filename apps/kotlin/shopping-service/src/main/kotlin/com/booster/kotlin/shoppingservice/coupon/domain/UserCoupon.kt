package com.booster.kotlin.shoppingservice.coupon.domain

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.coupon.exception.CouponException
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
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import jakarta.persistence.EntityListeners
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "coupon_id"])], // 중복 발급 방지
)
@EntityListeners(AuditingEntityListener::class)
class UserCoupon(
    @Column(nullable = false) val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    val coupon: Coupon,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserCouponStatus = UserCouponStatus.AVAILABLE
        private set

    @CreatedDate
    @Column(updatable = false)
    lateinit var issuedAt: LocalDateTime

    @Column
    var usedAt: LocalDateTime? = null
        private set

    @Column
    var usedOrderId: Long? = null
        private set

    fun use(orderId: Long) {
        check(status == UserCouponStatus.AVAILABLE) {
            throw CouponException(ErrorCode.COUPON_NOT_AVAILABLE)
        }
        check(coupon.isAvailable()) {
            throw CouponException(ErrorCode.COUPON_EXPIRED)
        }
        status = UserCouponStatus.USED
        usedAt = LocalDateTime.now()
        usedOrderId = orderId
    }

    fun expire() {
        if (status == UserCouponStatus.AVAILABLE) {
            status = UserCouponStatus.EXPIRED
        }
    }

    companion object {
        fun create(userId: Long, coupon: Coupon) = UserCoupon(userId, coupon)
    }
}