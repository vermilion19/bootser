package com.booster.firstcomefirstserved.order.application;

import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import com.booster.firstcomefirstserved.order.infrastructure.adapter.RedisStockAdapter;
import com.booster.firstcomefirstserved.order.infrastructure.persistence.OrderEntity;
import com.booster.firstcomefirstserved.order.infrastructure.persistence.OrderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

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
    private final RedisStockAdapter redisStockAdapter;

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
                            .doOnSuccess(saved -> log.debug("저장 완료: {}건", saved.size()))
                            .then()
                            .onErrorResume(e -> revertStock(eventList,e));
                })
                .onErrorContinue((error, obj) -> {
                    log.error("DB 저장 실패: {}", error.getMessage());
                    // TODO: Dead Letter Queue에 저장
                })
                .subscribe();
    }

    private Mono<Void> revertStock(List<OrderCreatedEvent> eventList, Throwable throwable) {
        log.error("DB 저장 실패! 보상 트랜잭션(Rollback) 실행. 실패 건수: {}", eventList.size(), throwable);
        return Flux.fromIterable(eventList)
                .flatMap(event -> redisStockAdapter.increase(event.itemId(), event.quantity())
                        .doOnNext(newStock -> log.info("재고 롤백 완료. Item: {}, 복구된 수량: {}, 현재 재고: {}",
                                event.itemId(), event.quantity(), newStock))
                ).then();
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
