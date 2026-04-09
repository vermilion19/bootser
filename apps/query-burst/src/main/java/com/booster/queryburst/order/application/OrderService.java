package com.booster.queryburst.order.application;

import com.booster.common.JsonUtils;
import com.booster.common.SnowflakeGenerator;
import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberRepository;
import com.booster.queryburst.order.application.dto.*;
import com.booster.queryburst.order.domain.*;
import com.booster.queryburst.order.domain.outbox.OutboxEvent;
import com.booster.queryburst.order.domain.outbox.OutboxEventRepository;
import com.booster.queryburst.order.event.OrderEventPayload;
import com.booster.queryburst.product.domain.Product;
import com.booster.queryburst.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemQueryRepository orderItemQueryRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public List<OrderSummaryResult> getOrders(Long cursorId, Long memberId, OrderStatus status, int size) {
        return orderQueryRepository.findByCursor(cursorId, memberId, status, size);
    }

    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(Long orderId) {
        Orders order = orderQueryRepository.findByIdWithMember(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. id=" + orderId));

        List<OrderItemResult> items = orderItemQueryRepository.findByOrderId(orderId);

        return new OrderDetailResult(
                order.getId(),
                order.getMember().getId(),
                order.getMember().getName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                items
        );
    }

    @Transactional(readOnly = true)
    public List<MonthlySalesResult> getMonthlySales(LocalDateTime from, LocalDateTime to) {
        return orderQueryRepository.findMonthlySales(from, to);
    }

    @Transactional(readOnly = true)
    public List<ProductSalesResult> getTopSellingProducts(int size) {
        return orderItemQueryRepository.findTopSellingProducts(size);
    }

    public OrderResult createOrder(OrderCreateCommand command) {
        return createOrderInternal(
                SnowflakeGenerator.nextId(),
                command.memberId(),
                command.items(),
                item -> productRepository.findById(item.productId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + item.productId())),
                (product, item) -> {
                    long fenceToken = command.fencingTokens().getOrDefault(item.productId(), 0L);
                    product.decreaseStock(item.quantity(), fenceToken);
                }
        );
    }

    public OrderResult createOrderWithPessimisticLock(Long memberId, List<OrderItemCommand> items) {
        return createOrderInternal(
                SnowflakeGenerator.nextId(),
                memberId,
                items,
                item -> productRepository.findByIdWithLock(item.productId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + item.productId())),
                (product, item) -> product.decreaseStockFallback(item.quantity())
        );
    }

    public OrderResult createFlashSaleOrder(Long orderId, Long memberId, List<OrderItemCommand> items) {
        return createOrderInternal(
                orderId,
                memberId,
                items,
                item -> productRepository.findByIdWithLock(item.productId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + item.productId())),
                (product, item) -> product.decreaseStockFallback(item.quantity())
        );
    }

    public void pay(Long orderId) {
        Orders order = getOrderOrThrow(orderId);
        order.pay();
        saveStatusChangedEvent(order);
    }

    public void ship(Long orderId) {
        Orders order = getOrderOrThrow(orderId);
        order.ship();
        saveStatusChangedEvent(order);
    }

    public void deliver(Long orderId) {
        Orders order = getOrderOrThrow(orderId);
        order.deliver();
        saveStatusChangedEvent(order);
    }

    public void cancel(Long orderId) {
        Orders order = getOrderOrThrow(orderId);
        order.cancel();

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        outboxEventRepository.save(OutboxEvent.create(
                "ORDER",
                orderId,
                "ORDER_CANCELED",
                toJson(buildPayload("ORDER_CANCELED", order, items))
        ));
    }

    private OrderResult createOrderInternal(
            Long orderId,
            Long memberId,
            List<OrderItemCommand> items,
            ProductLoader productLoader,
            StockDecrement stockDecrement
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + memberId));

        List<OrderItem> orderItems = new ArrayList<>();
        long totalAmount = 0L;

        Orders order = Orders.createWithId(orderId, member, 0L, LocalDateTime.now());
        orderRepository.save(order);

        for (OrderItemCommand item : items) {
            Product product = productLoader.load(item);
            stockDecrement.decrease(product, item);

            OrderItem orderItem = OrderItem.create(order, product, item.quantity(), product.getPrice());
            orderItems.add(orderItem);
            totalAmount += orderItem.totalPrice();
        }

        orderItemRepository.saveAll(orderItems);
        order.updateTotalAmount(totalAmount);

        outboxEventRepository.save(OutboxEvent.create(
                "ORDER",
                order.getId(),
                "ORDER_CREATED",
                toJson(buildCreatedPayload(order, orderItems))
        ));

        return new OrderResult(order.getId(), totalAmount);
    }

    private Orders getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. id=" + orderId));
    }

    private void saveStatusChangedEvent(Orders order) {
        outboxEventRepository.save(OutboxEvent.create(
                "ORDER",
                order.getId(),
                "ORDER_STATUS_CHANGED",
                toJson(buildPayload("ORDER_STATUS_CHANGED", order, List.of()))
        ));
    }

    private OrderEventPayload buildCreatedPayload(Orders order, List<OrderItem> items) {
        return buildPayload("ORDER_CREATED", order, items);
    }

    private OrderEventPayload buildPayload(String eventType, Orders order, List<OrderItem> items) {
        List<OrderEventPayload.OrderItemPayload> itemPayloads = items.stream()
                .map(item -> new OrderEventPayload.OrderItemPayload(
                        item.getProduct().getId(),
                        item.getProduct().getCategory().getId(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        return new OrderEventPayload(
                eventType,
                order.getId(),
                order.getMember().getId(),
                order.getTotalAmount(),
                order.getStatus().name(),
                LocalDateTime.now(),
                itemPayloads
        );
    }

    private String toJson(OrderEventPayload payload) {
        try {
            return JsonUtils.MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("OrderEventPayload 직렬화 실패", e);
        }
    }

    private interface ProductLoader {
        Product load(OrderItemCommand item);
    }

    private interface StockDecrement {
        void decrease(Product product, OrderItemCommand item);
    }
}
