package com.booster.kotlin.shoppingservice.order.web.dto.response

import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus

data class OrderResponse(
    val orderId: Long,
    val status: OrderStatus,
    val totalPrice: Long,
    val recipientName: String,
    val address1: String,
    val address2: String?,
    val items: List<OrderItemResponse>,
) {
    data class OrderItemResponse(
        val orderItemId: Long,
        val productName: String,
        val variantSku: String,
        val unitPrice: Long,
        val quantity: Int,
        val totalPrice: Long,
    )

    companion object {
        fun from(order: Order) = OrderResponse(
            orderId = order.id,
            status = order.status,
            totalPrice = order.totalPrice,
            recipientName = order.recipientName,
            address1 = order.address1,
            address2 = order.address2,
            items = order.items.map {
                OrderItemResponse(
                    orderItemId = it.id,
                    productName = it.productName,
                    variantSku = it.variantSku,
                    unitPrice = it.unitPrice,
                    quantity = it.quantity,
                    totalPrice = it.totalPrice,
                )
            },
        )
    }
}
