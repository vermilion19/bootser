package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class DuplicateCouponIssueException extends CoreException {

    public DuplicateCouponIssueException() {
        super(CouponErrorCode.DUPLICATE_COUPON_ISSUE);
    }

    public DuplicateCouponIssueException(Long couponId, Long userId) {
        super(CouponErrorCode.DUPLICATE_COUPON_ISSUE,
                "이미 발급받은 쿠폰입니다. couponId=" + couponId + ", userId=" + userId);
    }
}
