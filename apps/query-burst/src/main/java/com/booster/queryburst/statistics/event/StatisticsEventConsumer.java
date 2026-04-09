package com.booster.queryburst.statistics.event;

import com.booster.queryburst.common.kafka.ConsumerIdempotencyService;
import com.booster.queryburst.order.event.OrderEventPayload;
import com.booster.queryburst.statistics.domain.DailySalesSummary;
import com.booster.queryburst.statistics.domain.DailySalesSummaryRepository;
import com.booster.queryburst.statistics.domain.ProductDailySales;
import com.booster.queryburst.statistics.domain.ProductDailySalesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 주문 이벤트 → CQRS 통계 테이블 갱신 Consumer.
 *
 * Consumer Group: statistics-consumer-group
 *
 * 처리:
 *   ORDER_CREATED  → DailySalesSummary(카테고리별) + ProductDailySales(상품별) UPSERT
 *   ORDER_CANCELED → 위 두 테이블 역산(감소)
 *   기타           → 무시
 *
 * 멱등성:
 *   ConsumerIdempotencyService로 orderId + eventType 기반 중복 처리를 차단한다.
 *   트랜잭션 커밋 성공 후 마킹하여 처리 실패 시 재시도 가능성을 보존한다.
 *
 *   [주의] @Transactional + @KafkaListener self-invocation 문제:
 *   두 어노테이션이 같은 메서드에 붙어있으면 @Transactional이 AOP 프록시를 통해 적용된다.
 *   @KafkaListener는 Spring이 별도 인프라로 호출하므로 프록시를 통해 진입 → 정상 동작.
 *   (같은 클래스 내부에서 this.consume()을 직접 호출하는 경우만 문제가 됨)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsEventConsumer {

    private static final String GROUP_ID = "statistics-consumer-group";

    private final DailySalesSummaryRepository dailySalesSummaryRepository;
    private final ProductDailySalesRepository productDailySalesRepository;
    private final ConsumerIdempotencyService idempotencyService;

    @Transactional
    @KafkaListener(
            topics = "order-events",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderEventPayload payload) {
        log.debug("[StatisticsConsumer] 이벤트 수신. type={}, orderId={}", payload.eventType(), payload.orderId());

        // 멱등성 체크: 이미 처리된 이벤트면 스킵
        if (idempotencyService.isDuplicate(GROUP_ID, payload.orderId(), payload.eventType())) {
            return;
        }

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(payload);
            case "ORDER_CANCELED" -> handleOrderCanceled(payload);
            default -> {
                log.debug("[StatisticsConsumer] 처리 대상 아님. type={}", payload.eventType());
                return;  // 미처리 이벤트는 마킹 불필요
            }
        }

        // 트랜잭션 커밋 성공 후 마킹
        // (처리 전 마킹 시 DB 롤백 발생해도 Redis는 이미 마킹 → 재시도 불가)
        idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
    }

    private void handleOrderCreated(OrderEventPayload payload) {
        LocalDate date = payload.occurredAt().toLocalDate();

        for (OrderEventPayload.OrderItemPayload item : payload.items()) {
            DailySalesSummary summary = dailySalesSummaryRepository
                    .findByDateAndCategoryId(date, item.categoryId())
                    .orElseGet(() -> DailySalesSummary.create(date, item.categoryId()));
            summary.addSales((long) item.quantity() * item.unitPrice());
            dailySalesSummaryRepository.save(summary);

            ProductDailySales productSales = productDailySalesRepository
                    .findByDateAndProductId(date, item.productId())
                    .orElseGet(() -> ProductDailySales.create(date, item.productId()));
            productSales.addSales(item.quantity(), item.unitPrice());
            productDailySalesRepository.save(productSales);
        }

        log.debug("[StatisticsConsumer] ORDER_CREATED 통계 반영 완료. orderId={}", payload.orderId());
    }

    private void handleOrderCanceled(OrderEventPayload payload) {
        LocalDate date = payload.occurredAt().toLocalDate();

        for (OrderEventPayload.OrderItemPayload item : payload.items()) {
            dailySalesSummaryRepository
                    .findByDateAndCategoryId(date, item.categoryId())
                    .ifPresent(summary -> {
                        summary.subtractSales((long) item.quantity() * item.unitPrice());
                        dailySalesSummaryRepository.save(summary);
                    });

            productDailySalesRepository
                    .findByDateAndProductId(date, item.productId())
                    .ifPresent(productSales -> {
                        productSales.subtractSales(item.quantity(), item.unitPrice());
                        productDailySalesRepository.save(productSales);
                    });
        }

        log.debug("[StatisticsConsumer] ORDER_CANCELED 통계 역산 완료. orderId={}", payload.orderId());
    }
}
