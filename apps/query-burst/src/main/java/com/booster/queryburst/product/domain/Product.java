package com.booster.queryburst.product.domain;

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

    public static Product create(Long id, String name, Long price, int stock,
                                 ProductStatus status, Category category, Member seller) {
        Product product = new Product();
        product.id = id;
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

    public void decreaseStock(int quantity) {
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
