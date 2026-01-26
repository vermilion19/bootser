package com.booster.firstcomefirstserved.order.application.event;

import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@Component
public class OrderEventPublisher {

    // 1. Many: 여러 개의 이벤트를 처리
    // 2. Multicast: 여러 구독자(Worker)에게 전파 가능
    // 3. onBackpressureBuffer: 소비 속도가 느리면 버퍼에 담아둠
    private final Sinks.Many<OrderCreatedEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 이벤트 발행 (Publisher)
     * - Thread-Safe 하게 이벤트를 밀어 넣음
     */
    public void publish(OrderCreatedEvent event) {
        sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(3)));
    }

    /**
     * 이벤트 구독 (Subscriber 용)
     */
    public Flux<OrderCreatedEvent> asFlux() {
        return sink.asFlux();
    }
}
