package com.booster.queryburstmsa.order.web;

import com.booster.queryburstmsa.order.application.OrderApplicationService;
import com.booster.queryburstmsa.order.web.dto.OrderCreateRequest;
import com.booster.queryburstmsa.order.web.dto.OrderResponse;
import com.booster.queryburstmsa.order.web.dto.OrderSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> getOrders(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(orderApplicationService.getOrders(cursor, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderResponse response = orderApplicationService.getOrder(orderId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        OrderResponse created = orderApplicationService.createOrder(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/orders/" + created.orderId())).body(created);
    }

    @PatchMapping("/{orderId}/pay")
    public ResponseEntity<Void> pay(@PathVariable Long orderId) {
        orderApplicationService.pay(orderId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{orderId}/ship")
    public ResponseEntity<Void> ship(@PathVariable Long orderId) {
        orderApplicationService.ship(orderId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<Void> deliver(@PathVariable Long orderId) {
        orderApplicationService.deliver(orderId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancel(@PathVariable Long orderId) {
        orderApplicationService.cancel(orderId);
        return ResponseEntity.noContent().build();
    }
}
