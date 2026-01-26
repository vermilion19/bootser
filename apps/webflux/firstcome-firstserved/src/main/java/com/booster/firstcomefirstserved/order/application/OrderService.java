package com.booster.firstcomefirstserved.order.application;

import com.booster.firstcomefirstserved.common.error.BusinessException;
import com.booster.firstcomefirstserved.common.error.ErrorCode;
import com.booster.firstcomefirstserved.order.application.event.OrderEventPublisher;
import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.event.OrderCreatedEvent;
import com.booster.firstcomefirstserved.order.infrastructure.RedisStockAdapter;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import com.booster.firstcomefirstserved.order.web.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RedisStockAdapter redisStockAdapter;
    private final OrderEventPublisher eventPublisher;

    /**
     * 선착순 주문 처리 (Non-Blocking)
     */

    public Mono<OrderResponse> processOrder(OrderRequest request) {
        String tempOrderId = UUID.randomUUID().toString();

        return redisStockAdapter.decreaseStock(request.itemId(), request.quantity())
                .flatMap(isSuccess -> {
                    if (!isSuccess) {
                        return Mono.error(new BusinessException(ErrorCode.SOLD_OUT));
                    }
                    log.info("주문 접수 성공 - OrderId: {}, User: {}", tempOrderId, request.userId());

                    // [추가된 코드] 비동기 이벤트 발행
                    eventPublisher.publish(
                            OrderCreatedEvent.of(tempOrderId, request.userId(), request.itemId(), request.quantity())
                    );

                    return Mono.just(OrderResponse.of(
                            tempOrderId,
                            OrderStatus.PROCESSING,
                            "주문이 대기열에 접수되었습니다."
                    ));
                });
    }

}
