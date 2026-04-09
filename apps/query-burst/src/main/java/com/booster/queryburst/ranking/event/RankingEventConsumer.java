package com.booster.queryburst.ranking.event;

import com.booster.queryburst.common.kafka.ConsumerIdempotencyService;
import com.booster.queryburst.order.event.OrderEventPayload;
import com.booster.queryburst.ranking.application.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 이벤트 → 실시간 랭킹 반영 Consumer.
 *
 * Consumer Group: ranking-consumer-group
 * → order-events 토픽을 statistics-consumer-group과 독립적으로 소비한다.
 *
 * 처리:
 *   ORDER_CREATED  → Redis Sorted Set 판매량 증가 (ZINCRBY O(log N))
 *   ORDER_CANCELED → Redis Sorted Set 판매량 감소
 *   기타           → 무시
 *
 * 멱등성:
 *   Outbox At-Least-Once 특성상 동일 메시지가 중복 수신될 수 있다.
 *   ConsumerIdempotencyService로 orderId + eventType 기반 중복 처리를 차단한다.
 *   Key: "CONSUMER:ranking-consumer-group:{orderId}:{eventType}", TTL=25h
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingEventConsumer {

    private static final String GROUP_ID = "ranking-consumer-group";

    private final RankingService rankingService;
    private final ConsumerIdempotencyService idempotencyService;

    @KafkaListener(
            topics = "order-events",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderEventPayload payload) {
        log.debug("[RankingConsumer] 이벤트 수신. type={}, orderId={}", payload.eventType(), payload.orderId());

        // 멱등성 체크: 이미 처리된 이벤트면 스킵
        if (idempotencyService.isDuplicate(GROUP_ID, payload.orderId(), payload.eventType())) {
            return;
        }

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> payload.items().forEach(item ->
                    rankingService.incrementSales(item.productId(), item.quantity())
            );
            case "ORDER_CANCELED" -> payload.items().forEach(item ->
                    rankingService.decrementSales(item.productId(), item.quantity())
            );
            default -> {
                log.debug("[RankingConsumer] 처리 대상 아님. type={}", payload.eventType());
                return;  // 미처리 이벤트는 마킹 불필요
            }
        }

        // 처리 성공 후 마킹 (처리 전 마킹 시 장애 발생 후 재시도 불가)
        idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
    }
}
