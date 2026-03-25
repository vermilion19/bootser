package com.booster.queryburst.order.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburst.member.domain.Member;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 테이블
 * 목표 데이터 수: 3,000만 건
 *
 * 인덱스 설계 포인트:
 * - member_id: 회원별 주문 조회 (FK, 필수)
 * - ordered_at: 기간별 주문 조회
 * - status: 상태 필터 (카디널리티 낮음 → 단독보다 복합 인덱스 권장)
 * - (member_id, ordered_at): 회원별 최근 주문 조회 → 핵심 복합 인덱스
 * - (status, ordered_at): 상태별 기간 조회 → 관리자 대시보드용
 *
 * 쿼리 실습 포인트:
 * - Member JOIN으로 회원 정보 포함 주문 목록
 * - OrderItem + Product JOIN으로 주문 상세
 * - 월별 매출 집계 (DATE_TRUNC + GROUP BY)
 * - 회원 등급별 평균 주문금액 분석
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_member_id", columnList = "member_id"),
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_ordered_at", columnList = "ordered_at"),
                @Index(name = "idx_orders_member_ordered_at", columnList = "member_id, ordered_at"),
                @Index(name = "idx_orders_status_ordered_at", columnList = "status, ordered_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders extends BaseEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderStatus status;

    /** 주문 총액 (OrderItem.unitPrice * quantity 합산값 스냅샷) */
    @Column(nullable = false)
    private Long totalAmount;

    /** 주문 시각 (created_at과 별도로 비즈니스 주문 시각) */
    @Column(nullable = false)
    private LocalDateTime orderedAt;

    /**
     * [학습용 양방향 관계]
     *
     * 주의사항:
     * 1. N+1 문제: orders 목록 조회 후 getOrderItems() 호출 시 주문 수만큼 추가 쿼리 발생
     *    → 해결: fetch join 또는 @EntityGraph 사용
     *
     * 2. 무한 직렬화: Jackson이 Orders → OrderItem → Orders → ... 순환 참조
     *    → 해결: DTO 변환 후 반환 (엔티티 직접 반환 금지)
     *
     * 3. 컬렉션 초기화: new ArrayList<>() 필수 (null이면 add 시 NPE)
     */
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Orders create(Member member, Long totalAmount, LocalDateTime orderedAt) {
        Orders orders = new Orders();
        orders.id = SnowflakeGenerator.nextId();
        orders.member = member;
        orders.status = OrderStatus.PENDING;
        orders.totalAmount = totalAmount;
        orders.orderedAt = orderedAt;
        return orders;
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("결제 대기 상태에서만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void ship() {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
        this.status = OrderStatus.SHIPPED;
    }

    public void deliver() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("배송 중 상태에서만 배송 완료 처리할 수 있습니다.");
        }
        this.status = OrderStatus.DELIVERED;
    }

    public void cancel() {
        if (this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("배송 완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELED;
    }
}
