package com.booster.kotlin.shoppingservice.coupon.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.coupon.application.CouponService
import com.booster.kotlin.shoppingservice.coupon.web.dto.response.UserCouponResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponService: CouponService,
) {

    /** 쿠폰 발급 */
    @PostMapping("/{couponId}/issue")
    fun issue(
        @AuthenticationPrincipal userId: Long,
        @PathVariable couponId: Long,
    ): ResponseEntity<ApiResponse<UserCouponResponse>> {
        val userCoupon = couponService.issue(userId, couponId)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(UserCouponResponse.from(userCoupon)))
    }

    /** 내 쿠폰 목록 조회 (기본: 사용 가능한 쿠폰만, all=true 시 전체) */
    @GetMapping("/me")
    fun getMyCoupons(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "false") all: Boolean,
    ): ResponseEntity<ApiResponse<List<UserCouponResponse>>> {
        val coupons = if (all) couponService.getAllMyCoupons(userId)
                      else couponService.getAvailableCoupons(userId)
        return ResponseEntity.ok(ApiResponse.ok(coupons.map { UserCouponResponse.from(it) }))
    }
}