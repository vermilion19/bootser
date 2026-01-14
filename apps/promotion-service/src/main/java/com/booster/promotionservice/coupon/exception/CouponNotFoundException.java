package com.booster.promotionservice.coupon.exception;

import com.booster.core.web.exception.CoreException;

public class CouponNotFoundException extends CoreException {

    public CouponNotFoundException() {
        super(CouponErrorCode.COUPON_NOT_FOUND);
    }
}
