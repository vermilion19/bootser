package com.booster.promotionservice.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.promotionservice.application.CouponIssueService;
import com.booster.promotionservice.application.CouponService;
import com.booster.promotionservice.application.dto.CreateCouponRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CouponIssueService couponIssueService;

    @PostMapping
    public ApiResponse<Long> createCoupon(@RequestBody CreateCouponRequest request) {
        Long couponId = couponService.create(request);
        return ApiResponse.success(couponId);
    }


    @PostMapping("/{couponId}/issue")
    public ApiResponse<String> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId // 게이트웨이에서 토큰 파싱 후 헤더로 넘겨준다고 가정
    ) {
        couponIssueService.issueCoupon(couponId, userId);
        return ApiResponse.success("쿠폰 발급이 완료되었습니다."); // 메시지는 프론트 요구사항에 따라 변경 가능
    }
}
