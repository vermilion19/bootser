package com.booster.queryburst.order.event;

import com.booster.queryburst.common.kafka.ConsumerIdempotencyService;
import com.booster.queryburst.order.application.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderConsumer {

    private static final String GROUP_ID = "flash-sale-consumer-group";

    private final FlashSaleService flashSaleService;
    private final ConsumerIdempotencyService idempotencyService;

    @KafkaListener(
            topics = "flash-sale-orders",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(FlashSaleOrderPayload payload) {
        log.info("[FlashSaleConsumer] 이벤트 수신. orderId={}, productId={}, quantity={}",
                payload.orderId(), payload.productId(), payload.quantity());

        if (idempotencyService.isDuplicate(GROUP_ID, payload.orderId(), payload.eventType())) {
            return;
        }

        try {
            flashSaleService.processOrder(payload);
            idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
        } catch (Exception e) {
            flashSaleService.compensateStock(payload.productId(), payload.quantity());
            idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
            log.error("[FlashSaleConsumer] 주문 생성 실패. orderId={}", payload.orderId(), e);
        }
    }
}
