package com.booster.queryburst.statistics.event;

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
 * → ranking-consumer-group과 독립적으로 동일 토픽의 모든 메시지를 소비한다.
 *
 * 처리:
 *   ORDER_CREATED  → DailySalesSummary(카테고리별) + ProductDailySales(상품별) UPSERT
 *   ORDER_CANCELED → 위 두 테이블 역산(감소)
 *   기타           → 무시
 *
 * CQRS 가치:
 *   기존: SELECT SUM(total_amount) FROM orders JOIN order_item JOIN product
 *         → 3,000만 건 집계 → 수 초 소요
 *   개선: SELECT * FROM daily_sales_summary WHERE date = ? AND category_id = ?
 *         → 단순 조회 → 5ms 이내
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsEventConsumer {

    private final DailySalesSummaryRepository dailySalesSummaryRepository;
    private final ProductDailySalesRepository productDailySalesRepository;

    @Transactional
    @KafkaListener(
            topics = "order-events",
            groupId = "statistics-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(OrderEventPayload payload) {
        log.debug("[StatisticsConsumer] 이벤트 수신. type={}, orderId={}", payload.eventType(), payload.orderId());

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(payload);
            case "ORDER_CANCELED" -> handleOrderCanceled(payload);
            default -> log.debug("[StatisticsConsumer] 처리 대상 아님. type={}", payload.eventType());
        }
    }

    private void handleOrderCreated(OrderEventPayload payload) {
        LocalDate date = payload.occurredAt().toLocalDate();

        for (OrderEventPayload.OrderItemPayload item : payload.items()) {
            // 카테고리별 일별 매출 집계 UPSERT
            DailySalesSummary summary = dailySalesSummaryRepository
                    .findByDateAndCategoryId(date, item.categoryId())
                    .orElseGet(() -> DailySalesSummary.create(date, item.categoryId()));
            summary.addSales((long) item.quantity() * item.unitPrice());
            dailySalesSummaryRepository.save(summary);

            // 상품별 일별 판매 통계 UPSERT
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
