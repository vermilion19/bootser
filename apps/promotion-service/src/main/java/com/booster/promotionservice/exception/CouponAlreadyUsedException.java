package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class CouponAlreadyUsedException extends CoreException {

    public CouponAlreadyUsedException() {
        super(CouponErrorCode.COUPON_ALREADY_USED);
    }
}
