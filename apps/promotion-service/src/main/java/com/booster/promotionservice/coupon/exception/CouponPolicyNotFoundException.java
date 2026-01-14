package com.booster.promotionservice.coupon.exception;

import com.booster.core.web.exception.CoreException;

public class CouponPolicyNotFoundException extends CoreException {

    public CouponPolicyNotFoundException() {
        super(CouponErrorCode.COUPON_POLICY_NOT_FOUND);
    }
}
