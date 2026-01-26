package com.booster.firstcomefirstserved.order.application;

import com.booster.core.webflux.exception.CoreException;
import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.domain.exception.OrderErrorCode;
import com.booster.firstcomefirstserved.order.infrastructure.adapter.RedisStockAdapter;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private RedisStockAdapter redisStockAdapter;
    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("재고 차감에 성공하면 주문 접수 상태(PROCESSING)를 반환한다")
    void processOrder_success() {
        OrderRequest request = new OrderRequest(10L, 100L, 1);

        given(redisStockAdapter.decrease(anyLong(), anyInt())).willReturn(Mono.just(true));

        StepVerifier.create(orderService.processOrder(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
                    assertThat(response.orderId()).isNotNull();
                    assertThat(response.message()).contains("접수되었습니다");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("재고가 부족하면 SOLD_OUT 예외가 발생한다")
    void processOrder_fail_no_stock() {
        OrderRequest request = new OrderRequest(1L, 100L, 1);

        // Mocking: Redis 어댑터가 "실패(false)"를 리턴한다고 가정
        given(redisStockAdapter.decrease(anyLong(), anyInt()))
                .willReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(orderService.processOrder(request))
                .expectErrorMatches(throwable ->
                        throwable instanceof CoreException &&
                                ((CoreException) throwable).getErrorCode() == OrderErrorCode.SOLD_OUT
                )
                .verify();
    }
}