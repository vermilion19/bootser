package com.booster.kotlin.shoppingservice.payment.application

import com.booster.kotlin.shoppingservice.common.exception.ErrorCode
import com.booster.kotlin.shoppingservice.common.exception.orThrow
import com.booster.kotlin.shoppingservice.inventory.domain.InventoryRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistory
import com.booster.kotlin.shoppingservice.order.domain.OrderStatusHistoryRepository
import com.booster.kotlin.shoppingservice.order.exception.OrderException
import com.booster.kotlin.shoppingservice.payment.application.dto.ConfirmPaymentCommand
import com.booster.kotlin.shoppingservice.payment.application.dto.ProcessWebhookCommand
import com.booster.kotlin.shoppingservice.payment.domain.Payment
import com.booster.kotlin.shoppingservice.payment.domain.PaymentEvent
import com.booster.kotlin.shoppingservice.payment.domain.PaymentEventRepository
import com.booster.kotlin.shoppingservice.payment.domain.PaymentEventType
import com.booster.kotlin.shoppingservice.payment.domain.PaymentRepository
import com.booster.kotlin.shoppingservice.payment.exception.PaymentException
import com.booster.kotlin.shoppingservice.payment.infrastructure.MockPaymentGateway
import com.booster.kotlin.shoppingservice.shipment.application.ShipmentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val inventoryRepository: InventoryRepository,
    private val mockPaymentGateway: MockPaymentGateway,
    private val shipmentService: ShipmentService,
) {

    /**
     * 결제 확인 처리.
     * 멱등성 키가 동일한 경우 기존 결제 결과를 그대로 반환한다 (중복 결제 방지).
     */
    fun confirm(command: ConfirmPaymentCommand): Payment {
        // 멱등성 체크: 동일한 키가 이미 존재하면 기존 결과 반환
        if (paymentRepository.existsByIdempotencyKey(command.idempotencyKey)) {
            throw PaymentException(ErrorCode.PAYMENT_DUPLICATE)
        }

        val order = orderRepository.findById(command.orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }

        if (order.userId != command.userId) throw PaymentException(ErrorCode.FORBIDDEN)

        // 주문 금액 검증 (쿠폰 할인 반영된 실결제 금액 기준)
        if (order.paymentAmount != command.requestedAmount) {
            throw PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }

        // 주문 상태 전이: CREATED → PAYMENT_PENDING
        val prevStatus = order.status
        order.toPaymentPending()
        orderStatusHistoryRepository.save(
            OrderStatusHistory.create(order.id, prevStatus, OrderStatus.PAYMENT_PENDING)
        )

        // 결제 생성
        val payment = paymentRepository.save(
            Payment.create(
                orderId = order.id,
                provider = command.provider,
                method = command.method,
                idempotencyKey = command.idempotencyKey,
                requestedAmount = command.requestedAmount,
            )
        )

        // Mock PG 호출
        val result = mockPaymentGateway.confirm(
            orderId = order.id,
            amount = command.requestedAmount,
            method = command.method,
            simulateSuccess = command.simulateSuccess,
        )

        if (result.success) {
            // 결제 승인
            require(result.paymentKey != null && result.approvedAmount != null) {
                "Mock PG 성공 응답에 paymentKey 또는 approvedAmount가 없습니다"
            }
            payment.approve(result.paymentKey, result.approvedAmount)
            payment.addEvent(
                PaymentEvent.create(payment, PaymentEventType.PAYMENT_APPROVED)
            )

            // 주문 상태 전이: PAYMENT_PENDING → PAID
            val pendingStatus = order.status
            order.pay()
            orderStatusHistoryRepository.save(
                OrderStatusHistory.create(order.id, pendingStatus, OrderStatus.PAID)
            )

            // 배송 정보 생성 (Order: PAID → PREPARING)
            shipmentService.createForOrder(order)
        } else {
            // 결제 실패
            payment.fail()
            payment.addEvent(
                PaymentEvent.create(
                    payment = payment,
                    eventType = PaymentEventType.PAYMENT_FAILED,
                    payloadJson = result.failureReason,
                )
            )

            // 주문 상태 전이: PAYMENT_PENDING → PAYMENT_FAILED
            val pendingStatus = order.status
            order.fail()
            orderStatusHistoryRepository.save(
                OrderStatusHistory.create(
                    orderId = order.id,
                    fromStatus = pendingStatus,
                    toStatus = OrderStatus.PAYMENT_FAILED,
                    reason = result.failureReason,
                )
            )

            // 재고 복구
            order.items.forEach { item ->
                val inventory = inventoryRepository.findByVariantId(item.variantId)
                    ?: throw PaymentException(ErrorCode.INVENTORY_NOT_FOUND)
                inventory.increase(item.quantity)
            }
        }

        return payment
    }

    /**
     * Webhook 수신 처리.
     * providerEventId 중복 수신 방지 (At-least-once 환경 대응).
     */
    fun processWebhook(command: ProcessWebhookCommand) {
        // Webhook 중복 수신 방지
        if (paymentEventRepository.existsByProviderEventId(command.providerEventId)) {
            throw PaymentException(ErrorCode.PAYMENT_WEBHOOK_DUPLICATE)
        }

        val payment = paymentRepository.findByPaymentKey(command.paymentKey)
            .orThrow { PaymentException(ErrorCode.PAYMENT_NOT_FOUND) }

        val eventType = runCatching { PaymentEventType.valueOf(command.eventType) }
            .getOrDefault(PaymentEventType.WEBHOOK_RECEIVED)

        val event = PaymentEvent.create(
            payment = payment,
            eventType = eventType,
            providerEventId = command.providerEventId,
            payloadJson = command.payloadJson,
        )
        payment.addEvent(event)
    }

    @Transactional(readOnly = true)
    fun getByOrderId(userId: Long, orderId: Long): List<Payment> {
        val order = orderRepository.findById(orderId)
            .orThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.userId != userId) throw PaymentException(ErrorCode.FORBIDDEN)
        return paymentRepository.findByOrderId(orderId)
    }
}
