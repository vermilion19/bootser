package com.booster.kotlin.shoppingservice.order.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface OrderRepository : JpaRepository<Order, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Order>
    fun findAllByStatusOrderByCreatedAtDesc(status: OrderStatus, pageable: Pageable): Page<Order>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Order>
    fun countByStatus(status: OrderStatus): Long
    fun countByCreatedAtBetween(from: LocalDateTime, to: LocalDateTime): Long
    @Query("SELECT COALESCE(SUM(o.totalPrice - o.discountAmount), 0) FROM Order o WHERE o.status IN :statuses")
    fun sumPaymentAmountByStatusIn(statuses: List<OrderStatus>): Long
}
