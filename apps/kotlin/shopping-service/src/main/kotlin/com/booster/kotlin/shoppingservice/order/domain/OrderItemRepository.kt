package com.booster.kotlin.shoppingservice.order.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemRepository: JpaRepository<OrderItem, Long> {
}