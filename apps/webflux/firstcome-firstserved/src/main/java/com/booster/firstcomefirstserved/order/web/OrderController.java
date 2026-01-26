package com.booster.firstcomefirstserved.order.web;

import com.booster.firstcomefirstserved.common.response.ApiResponse;
import com.booster.firstcomefirstserved.order.application.OrderService;
import com.booster.firstcomefirstserved.order.infrastructure.repository.OrderRepository;
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

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<OrderResponse>>> createOrder(@Valid @RequestBody Mono<OrderRequest> requestMono) {
        return requestMono
                .flatMap(orderService::processOrder)
                .map(orderResponse -> ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.ok(orderResponse))
                );
    }

    @GetMapping("/count")
    public Mono<Long> getOrderCount() {
        return orderRepository.count();
    }
}
