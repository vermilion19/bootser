package com.booster.queryburst.order.domain;

import com.booster.queryburst.order.application.dto.OrderItemResult;
import com.booster.queryburst.order.application.dto.ProductSalesResult;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.booster.queryburst.order.domain.QOrderItem.orderItem;
import static com.booster.queryburst.product.domain.QProduct.product;

@Repository
@RequiredArgsConstructor
public class OrderItemQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 주문 상세 항목 조회 (Product 페치 조인 — N+1 방지).
     *
     * 인덱스: idx_order_item_order_id (order_id)
     */
    public List<OrderItemResult> findByOrderId(Long orderId) {
        return queryFactory
                .select(Projections.constructor(OrderItemResult.class,
                        orderItem.id,
                        product.id,
                        product.name,
                        orderItem.quantity,
                        orderItem.unitPrice,
                        orderItem.quantity.multiply(orderItem.unitPrice)
                ))
                .from(orderItem)
                .join(orderItem.product, product)
                .where(orderItem.order.id.eq(orderId))
                .orderBy(orderItem.id.asc())
                .fetch();
    }

    /**
     * 상품별 판매 통계 TOP N 조회.
     *
     * 커버링 인덱스 활용: idx_order_item_covering (product_id, quantity, unit_price)
     *   → 집계 시 테이블 접근 없이 인덱스만으로 처리 가능
     *
     * 쿼리 실습: GROUP BY + SUM + ORDER BY totalQuantity DESC
     */
    public List<ProductSalesResult> findTopSellingProducts(int size) {
        return queryFactory
                .select(Projections.constructor(ProductSalesResult.class,
                        product.id,
                        product.name,
                        orderItem.quantity.sum().castToNum(Long.class),
                        orderItem.quantity.multiply(orderItem.unitPrice).sum()
                ))
                .from(orderItem)
                .join(orderItem.product, product)
                .groupBy(product.id, product.name)
                .orderBy(orderItem.quantity.sum().desc())
                .limit(size)
                .fetch();
    }
}
