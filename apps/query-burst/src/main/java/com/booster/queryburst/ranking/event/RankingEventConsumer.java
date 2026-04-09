package com.booster.queryburst.ranking.event;

import com.booster.queryburst.common.kafka.ConsumerIdempotencyService;
import com.booster.queryburst.order.event.OrderEventPayload;
import com.booster.queryburst.ranking.application.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
        log.debug("[RankingConsumer] event received. type={}, orderId={}", payload.eventType(), payload.orderId());

        if (!idempotencyService.tryStartProcessing(GROUP_ID, payload.orderId(), payload.eventType())) {
            return;
        }

        try {
            switch (payload.eventType()) {
                case "ORDER_CREATED" -> payload.items().forEach(item ->
                        rankingService.incrementSales(item.productId(), item.quantity())
                );
                case "ORDER_CANCELED" -> payload.items().forEach(item ->
                        rankingService.decrementSales(item.productId(), item.quantity())
                );
                default -> {
                    idempotencyService.clearProcessing(GROUP_ID, payload.orderId(), payload.eventType());
                    log.debug("[RankingConsumer] unsupported event. type={}", payload.eventType());
                    return;
                }
            }

            idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
        } catch (Exception e) {
            idempotencyService.clearProcessing(GROUP_ID, payload.orderId(), payload.eventType());
            throw e;
        }
    }
}
