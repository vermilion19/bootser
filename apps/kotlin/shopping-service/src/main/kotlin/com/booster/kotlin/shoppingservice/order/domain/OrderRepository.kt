package com.booster.kotlin.shoppingservice.order.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Order>
}