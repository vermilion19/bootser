package com.booster.queryburst.order.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka order-events 토픽 메시지 포맷.
 *
 * Consumer Group별 용도:
 * - ranking-consumer-group : ORDER_CREATED → Redis Sorted Set 판매량 증가
 * - statistics-consumer-group : ORDER_CREATED/ORDER_CANCELED → 통계 테이블 UPSERT/롤백
 * - (향후) notification-consumer-group : 모든 이벤트 → 알림 발송
 */
public record OrderEventPayload(
        String eventType,          // ORDER_CREATED | ORDER_CANCELED | ORDER_STATUS_CHANGED
        Long orderId,
        Long memberId,
        Long totalAmount,
        String orderStatus,        // Orders.status 스냅샷
        LocalDateTime occurredAt,
        List<OrderItemPayload> items  // 상태변경 이벤트는 빈 리스트
) {
    public record OrderItemPayload(
            Long productId,
            Long categoryId,
            int quantity,
            Long unitPrice
    ) {}
}
