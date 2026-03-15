package com.booster.kotlin.shoppingservice.order.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistory
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistoryRepository
import com.booster.kotlin.shoppingservice.order.exception.OrderException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminOrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
) {

    @Transactional(readOnly = true)
    fun getOrders(status: OrderStatus?, pageable: Pageable): Page<Order> =
        if (status != null) {
            orderRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
        } else {
            orderRepository.findAllByOrderByCreatedAtDesc(pageable)
        }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): Order =
        orderRepository.findById(orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

    fun updateStatus(orderId: Long, newStatus: OrderStatus): Order {
        val order = orderRepository.findById(orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

        val prevStatus = order.status

        when (newStatus) {
            OrderStatus.PREPARING -> order.prepare()
            OrderStatus.CANCELED -> order.cancel()
            else -> throw OrderException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED)
        }

        orderStatusHistoryRepository.save(
            OrderStatusHistory.create(order.id, prevStatus, newStatus)
        )

        return order
    }
}
