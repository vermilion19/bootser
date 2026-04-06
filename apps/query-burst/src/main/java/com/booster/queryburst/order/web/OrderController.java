package com.booster.queryburst.order.web;

import com.booster.queryburst.order.application.OrderFacade;
import com.booster.queryburst.order.application.dto.OrderResult;
import com.booster.queryburst.order.web.dto.request.OrderCreateRequest;
import com.booster.queryburst.order.web.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    /**
     * 주문 생성.
     *
     * - 분산 락 + 펜싱 토큰으로 동시성 제어
     * - Idempotency-Key 헤더: 클라이언트가 UUID를 발급하여 중복 요청 방지 (선택)
     *   동일 키로 재요청 시 캐시된 결과 반환 (24시간 유지)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        OrderResult result = orderFacade.placeOrder(request, idempotencyKey);
        return ResponseEntity
                .created(URI.create("/api/orders/" + result.orderId()))
                .body(OrderResponse.from(result));
    }
}
