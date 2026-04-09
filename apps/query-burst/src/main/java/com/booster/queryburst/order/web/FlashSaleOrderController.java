package com.booster.queryburst.order.web;

import com.booster.queryburst.order.application.FlashSaleService;
import com.booster.queryburst.order.application.dto.OrderResult;
import com.booster.queryburst.order.web.dto.request.FlashSaleOrderRequest;
import com.booster.queryburst.order.web.dto.response.FlashSaleOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/flash-sales/orders")
@RequiredArgsConstructor
public class FlashSaleOrderController {

    private final FlashSaleService flashSaleService;

    @PostMapping
    public ResponseEntity<FlashSaleOrderResponse> createOrder(@RequestBody FlashSaleOrderRequest request) {
        OrderResult result = flashSaleService.requestOrder(request.memberId(), request.productId(), request.quantity());
        return ResponseEntity.accepted()
                .location(URI.create("/api/orders/" + result.orderId()))
                .body(FlashSaleOrderResponse.from(result));
    }
}
