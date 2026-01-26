package com.booster.firstcomefirstserved.order.application;

import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import com.booster.firstcomefirstserved.order.infrastructure.persistence.OrderEntity;
import com.booster.firstcomefirstserved.order.infrastructure.persistence.OrderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 주문 이벤트 워커 (비동기 DB 저장)
 *
 * 동작 흐름:
 * 1. OrderEventPublisher에서 이벤트 수신
 * 2. bufferTimeout으로 배치 그룹핑 (10개 또는 100ms)
 * 3. boundedElastic 스레드풀에서 DB 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventWorker {

    private final OrderEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    @PostConstruct
    public void init() {
        eventPublisher.asFlux()
                .bufferTimeout(10, Duration.ofMillis(100))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(eventList -> {
                    log.info("DB Batch Insert: {}건", eventList.size());

                    var entities = eventList.stream()
                            .map(this::toEntity)
                            .toList();

                    return orderRepository.saveAll(entities)
                            .collectList()
                            .doOnSuccess(saved -> log.debug("저장 완료: {}건", saved.size()));
                })
                .onErrorContinue((error, obj) -> {
                    log.error("DB 저장 실패: {}", error.getMessage());
                    // TODO: Dead Letter Queue에 저장
                })
                .subscribe();
    }

    private OrderEntity toEntity(OrderCreatedEvent event) {
        return OrderEntity.builder()
                .orderId(event.orderId())
                .userId(event.userId())
                .itemId(event.itemId())
                .quantity(event.quantity())
                .status(OrderStatus.COMPLETED.name())
                .createdAt(event.occurredAt())
                .build();
    }
}
