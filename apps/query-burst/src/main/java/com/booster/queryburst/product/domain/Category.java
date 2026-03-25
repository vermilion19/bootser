package com.booster.queryburst.product.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 카테고리 (계층형, 최대 3단계)
 * 목표 데이터 수: ~1,000건 (소수의 마스터 데이터)
 *
 * 계층 구조 예시:
 * depth=1: 전자기기, 의류, 식품, 도서, 스포츠
 * depth=2: 스마트폰, 노트북, 태블릿 (전자기기 하위)
 * depth=3: 안드로이드폰, 아이폰 (스마트폰 하위)
 *
 * 쿼리 실습 포인트:
 * - 재귀 쿼리 (WITH RECURSIVE) 또는 경로 조회
 * - 카테고리 JOIN으로 상품 필터링
 * - 셀프 조인 (parent_id)
 */
@Entity
@Table(
        name = "category",
        indexes = {
                @Index(name = "idx_category_parent_id", columnList = "parent_id"),
                @Index(name = "idx_category_depth", columnList = "depth")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 상위 카테고리 (null이면 최상위 대분류) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** 1: 대분류, 2: 중분류, 3: 소분류 */
    @Column(nullable = false)
    private int depth;

    public static Category createRoot(Long id, String name) {
        Category category = new Category();
        category.id = id;
        category.name = name;
        category.parent = null;
        category.depth = 1;
        return category;
    }

    public static Category createChild(Long id, String name, Category parent) {
        Category category = new Category();
        category.id = id;
        category.name = name;
        category.parent = parent;
        category.depth = parent.depth + 1;
        return category;
    }
}
