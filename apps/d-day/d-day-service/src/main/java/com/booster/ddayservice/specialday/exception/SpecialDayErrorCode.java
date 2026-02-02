package com.booster.ddayservice.specialday.exception;

import com.booster.core.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SpecialDayErrorCode implements ErrorCode {

    INVALID_COUNTRY_CODE(400, "SD-001", "유효하지 않은 국가 코드입니다."),
    INVALID_TIMEZONE(400, "SD-002", "유효하지 않은 타임존입니다."),
    SYNC_FAILED(500, "SD-003", "공휴일 동기화에 실패했습니다."),
    SPECIAL_DAY_NOT_FOUND(404, "SD-004", "특별한 날을 찾을 수 없습니다."),
    UNAUTHORIZED(401, "SD-005", "인증이 필요합니다."),
    FORBIDDEN(403, "SD-006", "권한이 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
