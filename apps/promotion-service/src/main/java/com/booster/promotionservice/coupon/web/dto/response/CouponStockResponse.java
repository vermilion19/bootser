package com.booster.promotionservice.coupon.web.dto.response;

public record CouponStockResponse(
        Long couponPolicyId,
        int remainingStock,
        boolean available
) {
    public static CouponStockResponse of(Long policyId, int stock) {
        return new CouponStockResponse(policyId, stock, stock > 0);
    }
}
