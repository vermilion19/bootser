package com.booster.firstcomefirstserved.order.domain.exception;

import com.booster.core.webflux.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    SOLD_OUT(400, "ORDER-001", "재고가 모두 소진되었습니다."),
    ORDER_FAILED(500, "ORDER-002", "주문 처리에 실패했습니다."),
    INVALID_QUANTITY(400, "ORDER-003", "주문 수량이 올바르지 않습니다."),
    ITEM_NOT_FOUND(404, "ORDER-004", "상품을 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "ORDER-005", "서버 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
