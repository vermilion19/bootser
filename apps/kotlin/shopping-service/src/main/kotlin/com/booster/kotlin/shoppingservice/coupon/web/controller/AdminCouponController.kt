package com.booster.kotlin.shoppingservice.coupon.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.coupon.application.CouponService
import com.booster.kotlin.shoppingservice.coupon.web.dto.request.CreateCouponRequest
import com.booster.kotlin.shoppingservice.coupon.web.dto.response.CouponResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/v1/coupons")
class AdminCouponController(
    private val couponService: CouponService,
) {

    /** 쿠폰 생성 (관리자 전용) */
    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateCouponRequest,
    ): ResponseEntity<ApiResponse<CouponResponse>> {
        val coupon = couponService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CouponResponse.from(coupon)))
    }
}