package com.booster.firstcomefirstserved.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PROCESSING("처리 중"),   // Redis 대기열 진입 성공
    COMPLETED("주문 완료"),  // DB 저장 완료 (추후 사용)
    SOLD_OUT("재고 소진"),   // 재고 부족
    FAILED("주문 실패");     // 기타 에러

    private final String description;
}
