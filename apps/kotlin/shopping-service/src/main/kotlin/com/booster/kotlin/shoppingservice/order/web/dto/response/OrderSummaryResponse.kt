package com.booster.kotlin.shoppingservice.order.web.dto.response

import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import java.time.LocalDateTime

data class OrderSummaryResponse(
    val orderId: Long,
    val status: OrderStatus,
    val totalPrice: Long,
    val itemCount: Int,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(order: Order) = OrderSummaryResponse(
            orderId = order.id,
            status = order.status,
            totalPrice = order.totalPrice,
            itemCount = order.items.size,
            createdAt = order.createdAt,
        )
    }
}
