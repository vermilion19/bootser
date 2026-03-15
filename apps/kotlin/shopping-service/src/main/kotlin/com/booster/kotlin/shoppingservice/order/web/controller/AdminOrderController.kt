package com.booster.kotlin.shoppingservice.order.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.order.application.AdminOrderService
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.web.dto.response.OrderResponse
import com.booster.kotlin.shoppingservice.order.web.dto.response.OrderSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/v1/orders")
class AdminOrderController(
    private val adminOrderService: AdminOrderService,
) {

    /** 전체 주문 목록 조회 (관리자) */
    @GetMapping
    fun getOrders(
        @RequestParam(required = false) status: OrderStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> {
        val orders = adminOrderService.getOrders(status, pageable).map { OrderSummaryResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.ok(orders))
    }

    /** 주문 상세 조회 (관리자) */
    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = adminOrderService.getOrder(orderId)
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)))
    }

    /** 주문 상태 변경 (관리자) */
    @PatchMapping("/{orderId}/status")
    fun updateStatus(
        @PathVariable orderId: Long,
        @RequestParam status: OrderStatus,
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val order = adminOrderService.updateStatus(orderId, status)
        return ResponseEntity.ok(ApiResponse.ok(OrderResponse.from(order)))
    }
}
