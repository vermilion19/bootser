package com.booster.coinservice.domain.enums;

public enum OrderStatus {
    PENDING,  // 대기 중 (지정가 주문이 아직 가격 도달 안 함)
    FILLED,   // 체결 완료 (거래 끝)
    CANCELED  // 취소됨
}
