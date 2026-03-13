package com.booster.kotlin.shoppingservice.order.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.order.application.OrderService
import com.booster.kotlin.shoppingservice.order.application.dto.CancelOrderCommand
import com.booster.kotlin.shoppingservice.order.web.dto.request.CreateOrderRequest
import com.booster.kotlin.shoppingservice.order.web.dto.response.OrderResponse
import com.booster.kotlin.shoppingservice.order.web.dto.response.OrderSummaryResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
) {

    @GetMapping
    fun getOrders(
        @AuthenticationPrincipal userId: Long,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> {
        val orders = orderService.getOrders(userId, pageable).map { OrderSummaryResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(orders))
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @AuthenticationPrincipal userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderService.getOrder(userId, orderId)
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)))
    }

    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: Long,
        @RequestBody @Valid request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderService.create(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(OrderResponse.from(order)))
    }

    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @AuthenticationPrincipal userId: Long,
        @PathVariable orderId: Long,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = orderService.cancel(CancelOrderCommand(userId, orderId, reason))
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)))
    }
}
