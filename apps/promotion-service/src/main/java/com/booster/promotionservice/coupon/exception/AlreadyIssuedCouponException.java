package com.booster.promotionservice.coupon.exception;

import com.booster.core.web.exception.CoreException;

public class AlreadyIssuedCouponException extends CoreException {

    public AlreadyIssuedCouponException() {
        super(CouponErrorCode.ALREADY_ISSUED_COUPON);
    }
}
