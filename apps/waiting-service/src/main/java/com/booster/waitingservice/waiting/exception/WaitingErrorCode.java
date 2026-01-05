package com.booster.waitingservice.waiting.exception;

import com.booster.core.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WaitingErrorCode implements ErrorCode {

    // 대기 서비스 전용 에러들
    ALREADY_WAITING(400, "W-001", "이미 대기 중인 식당입니다."),
    SHOP_CLOSED(400, "W-002", "영업이 종료된 식당입니다."),
    INVALID_ENTRY_STATUS(400, "W-004", "입장 가능한 상태가 아닙니다. (호출된 상태만 입장 가능)"),
    INVALID_HEADCOUNT(400, "W-003", "인원 수는 1명 이상 10명 이하여야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}
