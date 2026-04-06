package com.booster.queryburst.product.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.queryburst.member.domain.Member;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 테이블
 * 목표 데이터 수: 100만 건
 *
 * 인덱스 설계 포인트:
 * - category_id: 카테고리별 상품 목록 조회 (FK 인덱스 - 필수)
 * - seller_id: 판매자별 상품 조회
 * - status: 상태 필터링 (카디널리티 낮음 → 부분 인덱스 고려)
 * - (category_id, status, price): 카테고리 내 가격 범위 + 상태 필터 복합 인덱스
 * - (status, price): 가격 범위 검색 시 사용
 *
 * 조인 실습:
 * - Product ↔ Category: 카테고리별 상품 검색
 * - Product ↔ Member(seller): 판매자 정보 포함 조회
 * - Product ↔ OrderItem: 판매량 집계
 */
@Entity
@Table(
        name = "product",
        indexes = {
                @Index(name = "idx_product_category_id", columnList = "category_id"),
                @Index(name = "idx_product_seller_id", columnList = "seller_id"),
                @Index(name = "idx_product_status", columnList = "status"),
                @Index(name = "idx_product_price", columnList = "price"),
                @Index(name = "idx_product_category_status_price", columnList = "category_id, status, price")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private int stock;

    /**
     * 마지막으로 적용된 펜싱 토큰.
     *
     * 재고 차감 시 요청의 fencingToken > lastFenceToken 인 경우에만 처리한다.
     * 이를 통해 오래된 락 보유자(stale writer)의 쓰기를 DB 레벨에서 거부한다.
     */
    @Column(nullable = false)
    private long lastFenceToken = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 판매자 (Member와 조인 실습) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    public static Product create(String name, Long price, int stock,
                                 ProductStatus status, Category category, Member seller) {
        Product product = new Product();
        product.id = SnowflakeGenerator.nextId();
        product.name = name;
        product.price = price;
        product.stock = stock;
        product.status = status;
        product.category = category;
        product.seller = seller;
        return product;
    }

    public void updatePrice(Long price) {
        this.price = price;
    }

    public void decreaseStock(int quantity, long fenceToken) {
        if (fenceToken <= this.lastFenceToken) {
            throw new StaleTokenException(
                    "오래된 요청입니다. productId=%d, 요청 token=%d, 마지막 적용 token=%d"
                            .formatted(this.id, fenceToken, this.lastFenceToken));
        }
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + this.stock);
        }
        this.stock -= quantity;
        this.lastFenceToken = fenceToken;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    /**
     * Redis 장애 Fallback 전용 재고 차감.
     *
     * 호출 전제: DB 비관적 락(SELECT FOR UPDATE)이 이미 획득된 상태.
     * DB 락이 동시성을 보장하므로 fencing token 검증을 생략한다.
     * lastFenceToken은 갱신하지 않아 Redis 복구 후 Redis 경로와 호환된다.
     */
    public void decreaseStockFallback(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + this.stock);
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }
}
