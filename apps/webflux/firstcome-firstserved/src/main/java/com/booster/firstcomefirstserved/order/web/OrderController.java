package com.booster.firstcomefirstserved.order.web;

import com.booster.core.webflux.response.ApiResponse;
import com.booster.firstcomefirstserved.order.application.OrderService;
import com.booster.firstcomefirstserved.order.domain.port.StockPort;
import com.booster.firstcomefirstserved.order.infrastructure.persistence.OrderRepository;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import com.booster.firstcomefirstserved.order.web.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final StockPort stockPort;

    /**
     * 선착순 주문 API
     *
     * @RequestBody Mono<T>: WebFlux에서 요청 본문을 Non-Blocking으로 읽음
     * flatMap 사용 이유: requestMono.flatMap()에서 orderService.processOrder()가 Mono를 반환
     */
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<OrderResponse>>> createOrder(
            @Valid @RequestBody Mono<OrderRequest> requestMono
    ) {
        // requestMono: Mono<OrderRequest>
        // orderService.processOrder(): Mono<OrderResponse> 반환
        // → Mono 안에서 Mono를 반환하므로 flatMap 필요
        return requestMono
                .flatMap(orderService::processOrder)
                .map(response -> ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.success(response)));
    }

    /**
     * 주문 건수 조회
     */
    @GetMapping("/count")
    public Mono<ResponseEntity<ApiResponse<Long>>> getOrderCount() {
        // orderRepository.count(): Mono<Long> 반환
        // map 사용: Long → ResponseEntity<ApiResponse<Long>> (동기 변환)
        return orderRepository.count()
                .map(count -> ResponseEntity.ok(ApiResponse.success(count)));
    }

    /**
     * 현재 재고 조회
     */
    @GetMapping("/stock/{itemId}")
    public Mono<ResponseEntity<ApiResponse<Long>>> getStock(@PathVariable Long itemId) {
        return stockPort.get(itemId)
                .map(stock -> ResponseEntity.ok(ApiResponse.success(stock)));
    }
}
