package com.booster.firstcomefirstserved.order.application.worker;

import com.booster.firstcomefirstserved.order.application.event.OrderEventPublisher;
import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import com.booster.firstcomefirstserved.order.infrastructure.entity.OrderEntity;
import com.booster.firstcomefirstserved.order.infrastructure.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventWorker {

    private final OrderEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    @PostConstruct
    public void init() {
        eventPublisher.asFlux()
                // [핵심 최적화: Grouping]
                // 데이터가 10개 모이거나, 100ms가 지나면 묶어서 처리
                // -> DB Insert 쿼리 횟수를 1/10로 줄임
                .bufferTimeout(10, Duration.ofMillis(100))
                // 병렬 처리보다는 순차적 쓰기 보장을 위해 publishOn 사용 가능 (선택사항)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(eventList -> {
                    log.info("DB Batch Insert 시작: {}건", eventList.size());

                    var entities = eventList.stream()
                            .map(this::toEntity)
                            .toList();

                    return orderRepository.saveAll(entities).then();
                })
                .onErrorContinue((throwable,o )->{
                    log.error("DB 저장 중 에러 발생: {}", throwable.getMessage());
                    // TODO: 에러난 데이터는 Dead Letter Queue(DLQ)에 별도 저장해야 함
                })
                .subscribe();
    }


    private OrderEntity toEntity(OrderCreatedEvent event) {
        return OrderEntity.builder()
                .orderId(event.orderId())
                .userId(event.userId())
                .itemId(event.itemId())
                .status(OrderStatus.COMPLETED.name()) // DB에는 완료 상태로 저장
                .createdAt(event.occurredAt())
                .build();
    }
}


