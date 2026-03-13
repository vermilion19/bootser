package com.booster.kotlin.shoppingservice.coupon.application.dto

import com.booster.kotlin.shoppingservice.coupon.domain.CouponType
import java.time.LocalDateTime

data class CreateCouponCommand(
    val code: String,
    val name: String,
    val couponType: CouponType,
    val discountValue: Long,
    val maxDiscountAmount: Long?,
    val minOrderAmount: Long,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val productIds: List<Long> = emptyList(),
    val categoryIds: List<Long> = emptyList(),
)