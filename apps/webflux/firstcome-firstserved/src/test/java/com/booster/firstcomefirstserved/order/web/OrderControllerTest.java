package com.booster.firstcomefirstserved.order.web;

import com.booster.firstcomefirstserved.order.application.OrderService;
import com.booster.firstcomefirstserved.order.domain.OrderStatus;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import com.booster.firstcomefirstserved.order.web.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;


@WebFluxTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("정상 요청 시 202 Accepted 응답을 반환한다")
    void createOrder_success() {
        // Given
        OrderRequest request = new OrderRequest(1L, 100L, 1);
        OrderResponse response = OrderResponse.of("uuid", OrderStatus.PROCESSING, "Success");

        given(orderService.processOrder(any(OrderRequest.class)))
                .willReturn(Mono.just(response));

        // When & Then
        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.status").isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("수량이 0 이하이면 400 Bad Request가 발생한다")
    void createOrder_validation_fail() {
        // Given: 수량이 0인 잘못된 요청
        OrderRequest request = new OrderRequest(1L, 100L, 0);

        // When & Then
        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("주문 수량은 최소 1개 이상이어야 합니다.");
    }
}