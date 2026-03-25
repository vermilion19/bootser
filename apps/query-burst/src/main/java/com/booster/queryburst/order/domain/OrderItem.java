package com.booster.queryburst.order.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburst.product.domain.Product;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 테이블
 * 목표 데이터 수: 1억 건 이상 (주문당 평균 3~4개 항목)
 *
 * 인덱스 설계 포인트:
 * - order_id: 주문별 항목 조회 (FK, 필수)
 * - product_id: 상품별 판매 내역 조회 (FK)
 * - (product_id, created_at): 상품별 기간 판매량 분석
 *
 * 커버링 인덱스 실습:
 * - (order_id, product_id, quantity, unit_price): SELECT 컬럼을 인덱스에 포함하여
 *   테이블 접근 없이 인덱스만으로 결과 반환
 *
 * 쿼리 실습 포인트:
 * - 3중 JOIN: OrderItem → Orders → Member (회원별 구매 통계)
 * - 3중 JOIN: OrderItem → Product → Category (카테고리별 매출)
 * - 윈도우 함수: ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY unit_price DESC)
 * - 서브쿼리: 최근 3개월 내 주문한 상품 목록
 */
@Entity
@Table(
        name = "order_item",
        indexes = {
                @Index(name = "idx_order_item_order_id", columnList = "order_id"),
                @Index(name = "idx_order_item_product_id", columnList = "product_id"),
                @Index(name = "idx_order_item_product_created_at", columnList = "product_id, created_at"),
                // 커버링 인덱스: 집계 쿼리 최적화 (product_id로 조회 시 quantity, unit_price 추가 읽기 없음)
                @Index(name = "idx_order_item_covering", columnList = "product_id, quantity, unit_price")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /** 주문 시점 가격 스냅샷 (상품 가격이 바뀌어도 주문 내역은 유지) */
    @Column(nullable = false)
    private Long unitPrice;

    public static OrderItem create(Orders order, Product product, int quantity, Long unitPrice) {
        OrderItem item = new OrderItem();
        item.id = SnowflakeGenerator.nextId();
        item.order = order;
        item.product = product;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        return item;
    }

    public Long totalPrice() {
        return unitPrice * quantity;
    }
}
