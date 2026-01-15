package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class CouponNotFoundException extends CoreException {

    public CouponNotFoundException() {
        super(CouponErrorCode.COUPON_NOT_FOUND);
    }

    public CouponNotFoundException(Long couponId) {
        super(CouponErrorCode.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다. couponId=" + couponId);
    }
}
