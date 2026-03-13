package com.booster.kotlin.shoppingservice.order.application

import com.booster.kotlin.shoppingservice.cart.domain.CartRepository
import com.booster.kotlin.shoppingservice.catalog.domain.ProductVariantRepository
import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import com.booster.kotlin.shoppingservice.order.application.dto.CancelOrderCommand
import com.booster.kotlin.shoppingservice.order.application.dto.CreateOrderCommand
import com.booster.kotlin.shoppingservice.order.domain.Order
import com.booster.kotlin.shoppingservice.order.domain.OrderItem
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistory
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistoryRepository
import com.booster.kotlin.shoppingservice.order.exception.OrderException
import com.booster.kotlin.shoppingservice.user.domain.UserAddressRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val cartRepository: CartRepository,
    private val variantRepository: ProductVariantRepository,
    private val inventoryRepository: InventoryRepository,
    private val userAddressRepository: UserAddressRepository,
) {
    @Transactional(readOnly = true)
    fun getOrders(userId: Long, pageable: Pageable): Page<Order> =
        orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)

    @Transactional(readOnly = true)
    fun getOrder(userId: Long, orderId: Long): Order {
        val order = orderRepository.findById(orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.userId != userId) throw OrderException(ErrorCode.FORBIDDEN)
        return order
    }

    fun create(command: CreateOrderCommand): Order {
        val cart = cartRepository.findByUserId(command.userId)
            ?: throw OrderException(ErrorCode.CART_NOT_FOUND)

        val activeItems = cart.findActiveItems()
        if (activeItems.isEmpty()) throw OrderException(ErrorCode.CART_EMPTY)

        val address = userAddressRepository.findById(command.addressId)
            .orThrow { OrderException(ErrorCode.ADDRESS_NOT_FOUND) }
        if (address.user.id != command.userId) throw OrderException(ErrorCode.FORBIDDEN)

        val order = Order.create(
            userId = command.userId,
            recipientName = address.recipientName,
            recipientPhone = address.recipientPhone,
            zipCode = address.zipCode,
            address1 = address.address1,
            address2 = address.address2,
        )
        orderRepository.save(order)

        for (cartItem in activeItems) {
            val variant = variantRepository.findById(cartItem.variantId)
                .orThrow { OrderException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND) }

            // 가격 재검증 (장바구니 가격 신뢰 금지)
            val currentPrice = variant.finalPrice()

            // 재고 검증 + 차감 (낙관적 락 @Version 적용됨)
            val inventory = inventoryRepository.findByVariantId(cartItem.variantId)
                ?: throw OrderException(ErrorCode.INVENTORY_NOT_FOUND)
            inventory.decrease(cartItem.quantity)

            val orderItem = OrderItem.create(
                order = order,
                variantId = variant.id,
                productName = variant.product.name,
                variantSku = variant.sku,
                unitPrice = currentPrice,
                quantity = cartItem.quantity,
            )
            order.addItem(orderItem)

            // 장바구니 항목 제거
            cartItem.softDelete()
        }

        orderStatusHistoryRepository.save(
            OrderStatusHistory.create(order.id, null, OrderStatus.CREATED)
        )

        return order
    }

    fun cancel(command: CancelOrderCommand): Order {
        val order = orderRepository.findById(command.orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

        if (order.userId != command.userId) throw OrderException(ErrorCode.FORBIDDEN)

        val prevStatus = order.status
        order.cancel()

        // 재고 복구
        order.items.forEach { item ->
            val inventory = inventoryRepository.findByVariantId(item.variantId)
                ?: throw OrderException(ErrorCode.INVENTORY_NOT_FOUND)
            inventory.increase(item.quantity)
        }

        orderStatusHistoryRepository.save(
            OrderStatusHistory.create(order.id, prevStatus, OrderStatus.CANCELED, command.reason)
        )

        return order
    }

}