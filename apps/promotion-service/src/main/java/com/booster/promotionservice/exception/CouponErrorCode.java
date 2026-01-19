package com.booster.promotionservice.exception;

import com.booster.core.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CouponErrorCode implements ErrorCode {

    // 쿠폰 조회 관련
    COUPON_NOT_FOUND(404, "C-001", "쿠폰을 찾을 수 없습니다."),

    // 쿠폰 발급 관련
    COUPON_SOLD_OUT(400, "C-002", "쿠폰이 모두 소진되었습니다."),
    DUPLICATE_COUPON_ISSUE(409, "C-003", "이미 발급받은 쿠폰입니다."),
    COUPON_NOT_AVAILABLE(400, "C-004", "쿠폰 발급 가능 기간이 아닙니다."),

    // 쿠폰 사용 관련
    COUPON_ALREADY_USED(400, "C-005", "이미 사용된 쿠폰입니다."),
    COUPON_EXPIRED(400, "C-006", "만료된 쿠폰입니다."),

    // 메시지 처리 관련
    INVALID_MESSAGE_FORMAT(400, "C-007", "잘못된 메시지 형식입니다.");

    private final int status;
    private final String code;
    private final String message;
}