package com.booster.firstcomefirstserved.order.application;

import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * 주문 이벤트 발행자 (In-Memory Event Bus)
 *
 * Sinks.Many 특성:
 * - multicast(): Hot Publisher, 구독 시점부터 이벤트 수신
 * - replay(): 구독 전 이벤트도 버퍼링하여 재생
 * - onBackpressureBuffer(): 소비 속도가 느리면 버퍼에 담아둠
 */
@Component
public class OrderEventPublisher {

    private final Sinks.Many<OrderCreatedEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 이벤트 발행
     * - busyLooping: emit 실패 시 최대 3초간 재시도
     */
    public void publish(OrderCreatedEvent event) {
        sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(3)));
    }

    /**
     * 이벤트 구독용 Flux 반환
     */
    public Flux<OrderCreatedEvent> asFlux() {
        return sink.asFlux();
    }
}
