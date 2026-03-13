package com.booster.kotlin.shoppingservice.coupon.domain

import org.springframework.data.jpa.repository.JpaRepository

interface OrderCouponUsageRepository : JpaRepository<OrderCouponUsage, Long> {
    fun findByOrderId(orderId: Long): OrderCouponUsage?
}