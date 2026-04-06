package com.booster.queryburst.order.application;

import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberRepository;
import com.booster.queryburst.order.application.dto.OrderCreateCommand;
import com.booster.queryburst.order.application.dto.OrderItemCommand;
import com.booster.queryburst.order.application.dto.OrderResult;
import com.booster.queryburst.order.domain.OrderItem;
import com.booster.queryburst.order.domain.OrderItemQueryRepository;
import com.booster.queryburst.order.domain.OrderItemRepository;
import com.booster.queryburst.order.domain.OrderQueryRepository;
import com.booster.queryburst.order.domain.OrderRepository;
import com.booster.queryburst.order.domain.Orders;
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

            // 상품별 펜싱 토큰 조회 + 재고 차감 (Product 도메인 내부에서 토큰 검증)
            long fenceToken = command.fencingTokens().getOrDefault(item.productId(), 0L);
            product.decreaseStock(item.quantity(), fenceToken);

            OrderItem orderItem = OrderItem.create(order, product, item.quantity(), product.getPrice());
            orderItems.add(orderItem);
            totalAmount += orderItem.totalPrice();
        }

        orderItemRepository.saveAll(orderItems);

        // totalAmount 확정 후 Orders 업데이트
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
            // SELECT FOR UPDATE — 행 레벨 잠금
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
}
