package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class CouponNotAvailableException extends CoreException {

    public CouponNotAvailableException() {
        super(CouponErrorCode.COUPON_NOT_AVAILABLE);
    }

    public CouponNotAvailableException(Long couponId) {
        super(CouponErrorCode.COUPON_NOT_AVAILABLE, "쿠폰 발급 가능 기간이 아닙니다. couponId=" + couponId);
    }
}
