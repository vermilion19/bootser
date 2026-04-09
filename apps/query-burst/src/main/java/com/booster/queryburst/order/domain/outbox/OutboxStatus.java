package com.booster.queryburst.order.domain.outbox;

public enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 재시도 초과 실패 (수동 처리 필요)
}
