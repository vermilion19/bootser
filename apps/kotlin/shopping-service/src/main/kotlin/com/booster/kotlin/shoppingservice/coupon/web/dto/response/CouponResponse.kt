package com.booster.kotlin.shoppingservice.coupon.web.dto.response

import com.booster.kotlin.shoppingservice.coupon.domain.Coupon
import com.booster.kotlin.shoppingservice.coupon.domain.CouponStatus
import com.booster.kotlin.shoppingservice.coupon.domain.CouponType
import com.booster.kotlin.shoppingservice.coupon.domain.UserCoupon
import com.booster.kotlin.shoppingservice.coupon.domain.UserCouponStatus
import java.time.LocalDateTime

data class CouponResponse(
    val couponId: Long,
    val code: String,
    val name: String,
    val couponType: CouponType,
    val discountValue: Long,
    val maxDiscountAmount: Long?,
    val minOrderAmount: Long,
    val status: CouponStatus,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
) {
    companion object {
        fun from(coupon: Coupon) = CouponResponse(
            couponId = coupon.id,
            code = coupon.code,
            name = coupon.name,
            couponType = coupon.couponType,
            discountValue = coupon.discountValue,
            maxDiscountAmount = coupon.maxDiscountAmount,
            minOrderAmount = coupon.minOrderAmount,
            status = coupon.status,
            startAt = coupon.startAt,
            endAt = coupon.endAt,
        )
    }
}

data class UserCouponResponse(
    val userCouponId: Long,
    val coupon: CouponResponse,
    val status: UserCouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
    val usedOrderId: Long?,
) {
    companion object {
        fun from(userCoupon: UserCoupon) = UserCouponResponse(
            userCouponId = userCoupon.id,
            coupon = CouponResponse.from(userCoupon.coupon),
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt,
            usedAt = userCoupon.usedAt,
            usedOrderId = userCoupon.usedOrderId,
        )
    }
}