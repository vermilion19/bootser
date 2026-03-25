package com.booster.queryburst.order.domain;

/**
 * 주문 상태
 * - 상태별 분포: DELIVERED(50%) > SHIPPED(20%) > PAID(15%) > CANCELED(10%) > PENDING(5%)
 * - 상태 필터링은 카디널리티가 낮아 단독 인덱스보다 복합 인덱스가 효과적
 */
public enum OrderStatus {
    PENDING,    // 결제 대기
    PAID,       // 결제 완료
    SHIPPED,    // 배송 중
    DELIVERED,  // 배송 완료
    CANCELED    // 취소
}
