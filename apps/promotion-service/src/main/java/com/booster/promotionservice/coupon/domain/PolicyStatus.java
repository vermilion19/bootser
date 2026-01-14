package com.booster.promotionservice.coupon.domain;

public enum PolicyStatus {
    PENDING,    // 발급 대기 (아직 시작 전)
    ACTIVE,     // 발급 진행 중
    EXHAUSTED,  // 소진 완료
    ENDED       // 기간 종료
}
