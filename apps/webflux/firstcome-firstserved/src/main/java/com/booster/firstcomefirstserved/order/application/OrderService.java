package com.booster.firstcomefirstserved.order.application;

import com.booster.core.webflux.exception.CoreException;
import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import com.booster.firstcomefirstserved.order.domain.exception.OrderErrorCode;
import com.booster.firstcomefirstserved.order.domain.port.StockPort;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import com.booster.firstcomefirstserved.order.web.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 주문 서비스 (Application Layer)
 *
 * 선착순 주문 처리 흐름:
 * 1. Redis에서 재고 원자적 차감 (Lua Script)
 * 2. 재고 부족 시 즉시 에러 반환
 * 3. 성공 시 이벤트 발행 → Worker가 비동기로 DB 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final StockPort stockPort;
    private final OrderEventPublisher eventPublisher;

    public Mono<OrderResponse> processOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        // stockPort.decrease()는 Mono<Boolean>을 반환
        // → 내부에서 다시 Mono를 반환하므로 flatMap 사용
        return stockPort.decrease(request.itemId(), request.quantity())
                .flatMap(isSuccess -> {
                    if (!isSuccess) {
                        // Mono.error()는 Mono<T>를 반환 → flatMap 필요
                        return Mono.error(new CoreException(OrderErrorCode.SOLD_OUT));
                    }

                    log.info("주문 접수 - orderId: {}, userId: {}", orderId, request.userId());

                    // 비동기 이벤트 발행 (Non-Blocking)
                    eventPublisher.publish(
                            OrderCreatedEvent.of(orderId, request.userId(), request.itemId(), request.quantity())
                    );

                    // Mono.just()는 Mono<T>를 반환 → flatMap 필요
                    return Mono.just(OrderResponse.of(
                            orderId,
                            OrderStatus.PROCESSING,
                            "주문이 접수되었습니다."
                    ));
                });
    }
}
