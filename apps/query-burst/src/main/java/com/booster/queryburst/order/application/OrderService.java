package com.booster.queryburst.order.application;

import com.booster.common.JsonUtils;
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

    // ── 조회 ──────────────────────────────────────────────────────────────────

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

    // ── 생성 ──────────────────────────────────────────────────────────────────

    /**
     * 주문 생성 + Outbox 이벤트 저장 (같은 트랜잭션).
     *
     * Outbox 패턴: Orders/OrderItem INSERT와 OutboxEvent INSERT가 단일 트랜잭션으로 묶인다.
     * → DB 커밋 성공 = 이벤트 발행 보장 (OutboxMessageRelay가 3초 이내 Kafka로 발행)
     *
     * @throws com.booster.queryburst.product.domain.StaleTokenException 오래된 락 보유자의 요청인 경우
     * @throws IllegalStateException 재고 부족인 경우
     */
    public OrderResult createOrder(OrderCreateCommand command) {
        Member member = memberRepository.findById(command.memberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + command.memberId()));

        List<OrderItem> orderItems = new ArrayList<>();
        long totalAmount = 0L;

        Orders order = Orders.create(member, 0L, LocalDateTime.now());
        orderRepository.save(order);

        for (OrderItemCommand item : command.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + item.productId()));

            long fenceToken = command.fencingTokens().getOrDefault(item.productId(), 0L);
            product.decreaseStock(item.quantity(), fenceToken);

            OrderItem orderItem = OrderItem.create(order, product, item.quantity(), product.getPrice());
            orderItems.add(orderItem);
            totalAmount += orderItem.totalPrice();
        }

        orderItemRepository.saveAll(orderItems);
        order.updateTotalAmount(totalAmount);

        // Outbox: ORDER_CREATED 이벤트 저장
        outboxEventRepository.save(OutboxEvent.create(
                "ORDER",
                order.getId(),
                "ORDER_CREATED",
                toJson(buildCreatedPayload(order, orderItems))
        ));

        return new OrderResult(order.getId(), totalAmount);
    }

    /**
     * Redis 장애 Fallback: DB 비관적 락으로 주문 생성 + Outbox 이벤트 저장.
     */
    public OrderResult createOrderWithPessimisticLock(Long memberId, List<OrderItemCommand> items) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + memberId));

        List<OrderItem> orderItems = new ArrayList<>();
        long totalAmount = 0L;

        Orders order = Orders.create(member, 0L, LocalDateTime.now());
        orderRepository.save(order);

        for (OrderItemCommand item : items) {
            Product product = productRepository.findByIdWithLock(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. id=" + item.productId()));

            product.decreaseStockFallback(item.quantity());

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

    // ── 상태 전환 ─────────────────────────────────────────────────────────────

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

    /**
     * 주문 취소 + Outbox ORDER_CANCELED 이벤트 저장.
     *
     * 통계 Consumer가 ORDER_CANCELED 이벤트로 DailySalesSummary를 역산(감소)한다.
     * 이를 위해 items(상품/카테고리 정보)를 payload에 포함한다.
     */
    public void cancel(Long orderId) {
        Orders order = getOrderOrThrow(orderId);
        order.cancel();

        // items 포함: 통계 Consumer가 역산에 사용
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        outboxEventRepository.save(OutboxEvent.create(
                "ORDER",
                orderId,
                "ORDER_CANCELED",
                toJson(buildPayload("ORDER_CANCELED", order, items))
        ));
    }

    // ── private ───────────────────────────────────────────────────────────────

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
}
