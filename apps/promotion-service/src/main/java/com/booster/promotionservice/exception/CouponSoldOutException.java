package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class CouponSoldOutException extends CoreException {

    public CouponSoldOutException() {
        super(CouponErrorCode.COUPON_SOLD_OUT);
    }

    public CouponSoldOutException(Long couponId) {
        super(CouponErrorCode.COUPON_SOLD_OUT, "쿠폰이 모두 소진되었습니다. couponId=" + couponId);
    }
}
