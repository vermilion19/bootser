package com.booster.promotionservice.coupon.web.dto.response;

import com.booster.promotionservice.coupon.domain.CouponStatus;
import com.booster.promotionservice.coupon.domain.IssuedCoupon;

import java.time.format.DateTimeFormatter;

public record CouponIssueResponse(
        Long couponId,
        Long couponPolicyId,
        Long userId,
        CouponStatus status,
        String issuedAt,
        String expireAt
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static CouponIssueResponse from(IssuedCoupon coupon) {
        return new CouponIssueResponse(
                coupon.getId(),
                coupon.getCouponPolicyId(),
                coupon.getUserId(),
                coupon.getStatus(),
                coupon.getIssuedAt().format(FORMATTER),
                coupon.getExpireAt().format(FORMATTER)
        );
    }
}
