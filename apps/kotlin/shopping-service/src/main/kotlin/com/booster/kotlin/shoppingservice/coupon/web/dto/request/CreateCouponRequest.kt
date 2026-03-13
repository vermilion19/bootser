package com.booster.kotlin.shoppingservice.coupon.web.dto.request

import com.booster.kotlin.shoppingservice.coupon.application.dto.CreateCouponCommand
import com.booster.kotlin.shoppingservice.coupon.domain.CouponType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

data class CreateCouponRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    @field:NotNull val couponType: CouponType,
    @field:Positive val discountValue: Long,
    val maxDiscountAmount: Long? = null,
    val minOrderAmount: Long = 0,
    @field:NotNull val startAt: LocalDateTime,
    @field:NotNull val endAt: LocalDateTime,
    val productIds: List<Long> = emptyList(),
    val categoryIds: List<Long> = emptyList(),
) {
    fun toCommand() = CreateCouponCommand(
        code = code,
        name = name,
        couponType = couponType,
        discountValue = discountValue,
        maxDiscountAmount = maxDiscountAmount,
        minOrderAmount = minOrderAmount,
        startAt = startAt,
        endAt = endAt,
        productIds = productIds,
        categoryIds = categoryIds,
    )
}