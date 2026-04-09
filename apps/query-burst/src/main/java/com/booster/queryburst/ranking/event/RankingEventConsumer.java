package com.booster.queryburst.ranking.event;

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
 *   동일 토픽을 두 Consumer Group이 구독하면 각 그룹이 모든 메시지를 받는다.
 *
 * 처리:
 *   ORDER_CREATED  → Redis Sorted Set 판매량 증가 (ZINCRBY O(log N))
 *   ORDER_CANCELED → Redis Sorted Set 판매량 감소
 *   기타           → 무시
 *
 * 멱등성:
 *   ZINCRBY/감소는 항상 같은 결과를 내지 않으므로(중복 시 과다 집계),
 *   Outbox At-Least-Once 특성상 중복 메시지 가능성을 감안한 설계가 필요하다.
 *   실습 단계에서는 단순 구현, 실제 운영 시 orderId 기반 Redis 처리 여부 추적 권장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingEventConsumer {

    private final RankingService rankingService;

    @KafkaListener(
            topics = "order-events",
            groupId = "ranking-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderEventPayload payload) {
        log.debug("[RankingConsumer] 이벤트 수신. type={}, orderId={}", payload.eventType(), payload.orderId());

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> payload.items().forEach(item ->
                    rankingService.incrementSales(item.productId(), item.quantity())
            );
            case "ORDER_CANCELED" -> payload.items().forEach(item ->
                    rankingService.decrementSales(item.productId(), item.quantity())
            );
            default -> log.debug("[RankingConsumer] 처리 대상 아님. type={}", payload.eventType());
        }
    }
}
