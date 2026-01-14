package com.booster.promotionservice.coupon.exception;

import com.booster.core.web.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {

    COUPON_POLICY_NOT_FOUND(404, "CP001", "쿠폰 정책을 찾을 수 없습니다."),
    COUPON_NOT_FOUND(404, "CP002", "쿠폰을 찾을 수 없습니다."),
    COUPON_SOLD_OUT(409, "CP003", "쿠폰이 모두 소진되었습니다."),
    ALREADY_ISSUED_COUPON(409, "CP004", "이미 발급받은 쿠폰입니다."),
    COUPON_NOT_ISSUABLE(400, "CP005", "현재 발급 가능한 상태가 아닙니다."),
    COUPON_ALREADY_USED(400, "CP006", "이미 사용된 쿠폰입니다."),
    COUPON_EXPIRED(400, "CP007", "만료된 쿠폰입니다.");

    private final int status;
    private final String code;
    private final String message;
}
