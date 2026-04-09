package com.booster.queryburst.order.application;

import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberRepository;
import com.booster.queryburst.order.application.dto.*;
import com.booster.queryburst.order.domain.*;
import com.booster.queryburst.product.domain.Product;
import com.booster.queryburst.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // ── 조회 ──────────────────────────────────────────────────────────────────

    /**
     * 커서 기반 주문 목록 조회 (OFFSET 없음).
     *
     * memberId, status 필터 조합:
     * - memberId만: idx_orders_member_ordered_at 활용
     * - status만: idx_orders_status_ordered_at 활용
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryResult> getOrders(Long cursorId, Long memberId, OrderStatus status, int size) {
        return orderQueryRepository.findByCursor(cursorId, memberId, status, size);
    }

    /**
     * 주문 상세 조회 (주문 항목 포함).
     *
     * Member 페치 조인으로 N+1 방지.
     * OrderItem은 별도 쿼리로 조회 (컬렉션 페치 조인 시 페이징 불가 문제 회피).
     */
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

    /**
     * 월별 매출 집계.
     *
     * 쿼리 실습: DATE_TRUNC + GROUP BY
     * 인덱스: idx_orders_status_ordered_at (status, ordered_at)
     */
    @Transactional(readOnly = true)
    public List<MonthlySalesResult> getMonthlySales(LocalDateTime from, LocalDateTime to) {
        return orderQueryRepository.findMonthlySales(from, to);
    }

    /**
     * 상품별 판매 통계 TOP N.
     *
     * 커버링 인덱스: idx_order_item_covering (product_id, quantity, unit_price)
     */
    @Transactional(readOnly = true)
    public List<ProductSalesResult> getTopSellingProducts(int size) {
        return orderItemQueryRepository.findTopSellingProducts(size);
    }

    // ── 생성 ──────────────────────────────────────────────────────────────────

    /**
     * 주문 생성.
     *
     * 호출 전제: 분산 락이 이미 획득된 상태 (OrderFacade 책임).
     * 이 메서드의 책임: 펜싱 토큰 검증 → 재고 차감 → 주문 저장.
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

        return new OrderResult(order.getId(), totalAmount);
    }

    /**
     * Redis 장애 Fallback: DB 비관적 락으로 주문 생성.
     *
     * SELECT FOR UPDATE로 상품 행을 잠근 후 재고를 차감한다.
     * 트랜잭션 종료 시점에 락이 해제되므로 fencing token이 불필요하다.
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

        return new OrderResult(order.getId(), totalAmount);
    }

    // ── 상태 전환 ─────────────────────────────────────────────────────────────

    public void pay(Long orderId) {
        getOrderOrThrow(orderId).pay();
    }

    public void ship(Long orderId) {
        getOrderOrThrow(orderId).ship();
    }

    public void deliver(Long orderId) {
        getOrderOrThrow(orderId).deliver();
    }

    public void cancel(Long orderId) {
        getOrderOrThrow(orderId).cancel();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private Orders getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. id=" + orderId));
    }
}
