package com.booster.promotionservice.coupon.exception;

import com.booster.core.web.exception.CoreException;

public class CouponSoldOutException extends CoreException {

    public CouponSoldOutException() {
        super(CouponErrorCode.COUPON_SOLD_OUT);
    }
}
