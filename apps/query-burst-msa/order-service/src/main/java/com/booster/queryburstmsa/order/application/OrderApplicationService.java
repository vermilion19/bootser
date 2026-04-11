package com.booster.queryburstmsa.order.application;

import com.booster.common.JsonUtils;
import com.booster.common.SnowflakeGenerator;
import com.booster.queryburstmsa.contracts.event.OrderEventItem;
import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import com.booster.queryburstmsa.contracts.event.OrderEventType;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationItem;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationRequest;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResponse;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationStatus;
import com.booster.queryburstmsa.order.domain.OrderStatus;
import com.booster.queryburstmsa.order.domain.entity.OrderEntity;
import com.booster.queryburstmsa.order.domain.entity.OutboxEventEntity;
import com.booster.queryburstmsa.order.domain.repository.OrderRepository;
import com.booster.queryburstmsa.order.domain.repository.OutboxEventRepository;
import com.booster.queryburstmsa.order.infrastructure.CatalogServiceClient;
import com.booster.queryburstmsa.order.web.dto.OrderCreateRequest;
import com.booster.queryburstmsa.order.web.dto.OrderResponse;
import com.booster.queryburstmsa.order.web.dto.OrderSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CatalogServiceClient catalogServiceClient;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            CatalogServiceClient catalogServiceClient
    ) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.catalogServiceClient = catalogServiceClient;
    }

    public List<OrderSummaryResponse> getOrders(Long cursor, int size) {
        return orderRepository.findOrders(cursor, PageRequest.of(0, size)).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    public OrderResponse getOrder(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .map(OrderResponse::from)
                .orElse(null);
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, String idempotencyKey) {
        long orderId = SnowflakeGenerator.nextId();
        String requestId = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        InventoryReservationResponse reservationResponse = catalogServiceClient.reserve(new InventoryReservationRequest(
                requestId,
                orderId,
                request.memberId(),
                request.items().stream()
                        .map(item -> new InventoryReservationItem(item.productId(), item.quantity()))
                        .toList()
        ));

        long totalAmount = reservationResponse.items().stream()
                .mapToLong(item -> item.unitPrice() * item.quantity())
                .sum();

        OrderStatus status = reservationResponse.status() == InventoryReservationStatus.RESERVED
                ? OrderStatus.STOCK_RESERVED
                : OrderStatus.REJECTED;

        OrderEntity order = OrderEntity.create(orderId, request.memberId(), reservationResponse.reservationId(), status, totalAmount);
        reservationResponse.items().forEach(item -> order.addItem(item.productId(), item.categoryId(), item.quantity(), item.unitPrice()));
        orderRepository.save(order);

        appendOutboxEvent(order, OrderEventType.ORDER_CREATED, true);
        return OrderResponse.from(order);
    }

    @Transactional
    public void pay(Long orderId) {
        orderRepository.findWithItemsById(orderId).ifPresent(order -> {
            if (order.getReservationId() != null) {
                catalogServiceClient.commit(order.getReservationId());
            }
            order.pay();
            appendOutboxEvent(order, OrderEventType.ORDER_STATUS_CHANGED, false);
        });
    }

    @Transactional
    public void ship(Long orderId) {
        orderRepository.findWithItemsById(orderId).ifPresent(order -> {
            order.ship();
            appendOutboxEvent(order, OrderEventType.ORDER_STATUS_CHANGED, false);
        });
    }

    @Transactional
    public void deliver(Long orderId) {
        orderRepository.findWithItemsById(orderId).ifPresent(order -> {
            order.deliver();
            appendOutboxEvent(order, OrderEventType.ORDER_STATUS_CHANGED, false);
        });
    }

    @Transactional
    public void cancel(Long orderId) {
        orderRepository.findWithItemsById(orderId).ifPresent(order -> {
            if (order.getReservationId() != null && order.getStatus() == OrderStatus.STOCK_RESERVED) {
                catalogServiceClient.release(order.getReservationId());
            }
            order.cancel();
            appendOutboxEvent(order, OrderEventType.ORDER_CANCELED, true);
        });
    }

    private void appendOutboxEvent(OrderEntity order, OrderEventType eventType, boolean includeItems) {
        OrderEventPayload payload = new OrderEventPayload(
                eventType,
                order.getId(),
                order.getMemberId(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getOrderedAt(),
                includeItems
                        ? order.getItems().stream()
                        .map(item -> new OrderEventItem(item.getProductId(), item.getCategoryId(), item.getQuantity(), item.getUnitPrice()))
                        .toList()
                        : List.of()
        );
        outboxEventRepository.save(OutboxEventEntity.create(order.getId(), payload.eventType().name(), JsonUtils.toJson(payload)));
    }
}
