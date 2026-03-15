package com.booster.kotlin.shoppingservice.shipment.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistory
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistoryRepository
import com.booster.kotlin.shoppingservice.order.exception.OrderException
import com.booster.kotlin.shoppingservice.shipment.application.dto.UpdateShipmentStatusCommand
import com.booster.kotlin.shoppingservice.shipment.domain.Shipment
import com.booster.kotlin.shoppingservice.shipment.domain.ShipmentHistory
import com.booster.kotlin.shoppingservice.shipment.domain.ShipmentHistoryRepository
import com.booster.kotlin.shoppingservice.shipment.domain.ShipmentRepository
import com.booster.kotlin.shoppingservice.shipment.domain.ShipmentStatus
import com.booster.kotlin.shoppingservice.shipment.exception.ShipmentException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ShipmentService(
    private val shipmentRepository: ShipmentRepository,
    private val shipmentHistoryRepository: ShipmentHistoryRepository,
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
) {

    /** 결제 성공 시 배송 정보 생성 (order status: PAID → PREPARING) */
    fun createForOrder(order: Order): Shipment {
        val shipment = shipmentRepository.save(
            Shipment.create(
                orderId = order.id,
                recipientName = order.recipientName,
                recipientPhone = order.recipientPhone,
                zipCode = order.zipCode,
                address1 = order.address1,
                address2 = order.address2,
            )
        )
        shipmentHistoryRepository.save(
            ShipmentHistory.create(shipment.id, null, ShipmentStatus.READY)
        )

        val prevOrderStatus = order.status
        order.prepare()
        orderStatusHistoryRepository.save(
            OrderStatusHistory.create(order.id, prevOrderStatus, OrderStatus.PREPARING)
        )

        return shipment
    }

    /** 관리자: 배송 상태 변경 (READY → SHIPPED → DELIVERED) */
    fun updateStatus(command: UpdateShipmentStatusCommand): Shipment {
        val shipment = shipmentRepository.findById(command.shipmentId)
            .orThrow { ShipmentException(ErrorCode.SHIPMENT_NOT_FOUND) }
        val order = orderRepository.findById(shipment.orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

        val prevShipmentStatus = shipment.status

        when (command.status) {
            "SHIPPED" -> {
                val tracking = command.trackingNumber
                    ?: throw ShipmentException(ErrorCode.TRACKING_NUMBER_REQUIRED)
                shipment.ship(tracking)
                val prevOrderStatus = order.status
                order.ship()
                orderStatusHistoryRepository.save(
                    OrderStatusHistory.create(order.id, prevOrderStatus, OrderStatus.SHIPPED)
                )
            }
            "DELIVERED" -> {
                shipment.deliver()
                val prevOrderStatus = order.status
                order.deliver()
                orderStatusHistoryRepository.save(
                    OrderStatusHistory.create(order.id, prevOrderStatus, OrderStatus.DELIVERED)
                )
            }
            else -> throw ShipmentException(ErrorCode.INVALID_SHIPMENT_STATUS)
        }

        shipmentHistoryRepository.save(
            ShipmentHistory.create(shipment.id, prevShipmentStatus, shipment.status, command.note)
        )

        return shipment
    }

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): Shipment =
        shipmentRepository.findByOrderId(orderId)
            .orThrow { ShipmentException(ErrorCode.SHIPMENT_NOT_FOUND) }
}
