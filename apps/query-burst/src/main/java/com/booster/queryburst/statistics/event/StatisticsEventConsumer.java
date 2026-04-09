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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;

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
        log.debug("[StatisticsConsumer] event received. type={}, orderId={}", payload.eventType(), payload.orderId());

        if (!idempotencyService.tryStartProcessing(GROUP_ID, payload.orderId(), payload.eventType())) {
            return;
        }

        registerSynchronization(payload);

        switch (payload.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(payload);
            case "ORDER_CANCELED" -> handleOrderCanceled(payload);
            default -> {
                idempotencyService.clearProcessing(GROUP_ID, payload.orderId(), payload.eventType());
                log.debug("[StatisticsConsumer] unsupported event. type={}", payload.eventType());
            }
        }
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

        log.debug("[StatisticsConsumer] ORDER_CREATED applied. orderId={}", payload.orderId());
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

        log.debug("[StatisticsConsumer] ORDER_CANCELED applied. orderId={}", payload.orderId());
    }

    private void registerSynchronization(OrderEventPayload payload) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    idempotencyService.clearProcessing(GROUP_ID, payload.orderId(), payload.eventType());
                }
            }
        });
    }
}
