package com.booster.core.webflux.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT_VALUE(400, "C001", "잘못된 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C002", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "C003", "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(401, "C004", "인증이 필요합니다."),
    FORBIDDEN(403, "C005", "접근 권한이 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
