package com.booster.kotlin.shoppingservice.order.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OrderStatusHistoryRepository : JpaRepository<OrderStatusHistory, Long> {
    fun findAllByOrderIdOrderByCreatedAtAsc(orderId: Long): List<OrderStatusHistory>
}