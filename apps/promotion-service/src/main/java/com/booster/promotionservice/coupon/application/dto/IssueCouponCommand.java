package com.booster.promotionservice.coupon.application.dto;

public record IssueCouponCommand(
        Long couponPolicyId,
        Long userId
) {
    public static IssueCouponCommand of(Long couponPolicyId, Long userId) {
        return new IssueCouponCommand(couponPolicyId, userId);
    }
}
