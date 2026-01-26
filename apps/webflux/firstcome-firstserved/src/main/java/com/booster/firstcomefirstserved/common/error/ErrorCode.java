package com.booster.firstcomefirstserved.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SOLD_OUT("재고가 모두 소진되었습니다."),
    ORDER_FAILED("주문 처리에 실패했습니다."),
    INVALID_INPUT("잘못된 입력값입니다.");

    private final String message;
}
