package com.booster.queryburst.order.domain;

import com.booster.queryburst.order.application.dto.MonthlySalesResult;
import com.booster.queryburst.order.application.dto.OrderSummaryResult;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.booster.queryburst.order.domain.QOrders.orders;
import static com.booster.queryburst.member.domain.QMember.member;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 커서 기반 주문 목록 조회 (OFFSET 없음).
     *
     * 복합 인덱스 활용:
     * - memberId 있으면: idx_orders_member_ordered_at (member_id, ordered_at)
     * - status만 있으면: idx_orders_status_ordered_at (status, ordered_at)
     */
    public List<OrderSummaryResult> findByCursor(Long cursorId, Long memberId, OrderStatus status, int size) {
        BooleanBuilder condition = new BooleanBuilder();

        if (cursorId != null) {
            condition.and(orders.id.lt(cursorId));
        }
        if (memberId != null) {
            condition.and(orders.member.id.eq(memberId));
        }
        if (status != null) {
            condition.and(orders.status.eq(status));
        }

        return queryFactory
                .select(Projections.constructor(OrderSummaryResult.class,
                        orders.id,
                        member.id,
                        member.name,
                        orders.status,
                        orders.totalAmount,
                        orders.orderedAt
                ))
                .from(orders)
                .join(orders.member, member)
                .where(condition)
                .orderBy(orders.id.desc())
                .limit(size + 1L)
                .fetch();
    }

    /**
     * 주문 단건 조회 (Member 페치 조인 — N+1 방지).
     */
    public Optional<Orders> findByIdWithMember(Long orderId) {
        Orders result = queryFactory
                .selectFrom(orders)
                .join(orders.member, member).fetchJoin()
                .where(orders.id.eq(orderId))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    /**
     * 월별 매출 집계.
     *
     * 쿼리 실습: DATE_TRUNC('month', ordered_at) + GROUP BY
     * 인덱스: idx_orders_status_ordered_at — DELIVERED 필터 + 기간 조건
     *
     * 참고: JPA로 DATE_TRUNC 표현이 어려우므로 실제 집계 쿼리는
     *       네이티브 SQL로 구현하는 것을 권장. 여기서는 QueryDSL로 근사 구현.
     */
    public List<MonthlySalesResult> findMonthlySales(LocalDateTime from, LocalDateTime to) {
        return queryFactory
                .select(Projections.constructor(MonthlySalesResult.class,
                        orders.orderedAt.year(),
                        orders.orderedAt.month(),
                        orders.count(),
                        orders.totalAmount.sum()
                ))
                .from(orders)
                .where(
                        orders.status.eq(OrderStatus.DELIVERED),
                        orders.orderedAt.between(from, to)
                )
                .groupBy(orders.orderedAt.year(), orders.orderedAt.month())
                .orderBy(orders.orderedAt.year().asc(), orders.orderedAt.month().asc())
                .fetch();
    }
}
