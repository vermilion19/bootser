package com.booster.queryburst.product.domain;

import com.booster.queryburst.product.application.dto.ProductResult;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.booster.queryburst.product.domain.QProduct.product;
import static com.booster.queryburst.product.domain.QCategory.category;
import static com.booster.queryburst.member.domain.QMember.member;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 커서 기반 상품 목록 조회 (OFFSET 없음)
     *
     * 복합 인덱스 활용: idx_product_category_status_price (category_id, status, price)
     * - categoryId + status 조건이 있을 때 최적 인덱스 사용
     * - cursorId는 마지막 정렬 기준(id DESC)의 연속 지점
     */
    public List<ProductResult> findByCursor(
            Long cursorId,
            Long categoryId,
            ProductStatus status,
            Long minPrice,
            Long maxPrice,
            int size
    ) {
        BooleanBuilder condition = buildCondition(cursorId, categoryId, status, minPrice, maxPrice);

        return queryFactory
                .select(Projections.constructor(ProductResult.class,
                        product.id,
                        product.name,
                        product.price,
                        product.stock,
                        product.status,
                        category.id,
                        category.name,
                        member.id,
                        member.name
                ))
                .from(product)
                .join(product.category, category)
                .join(product.seller, member)
                .where(condition)
                .orderBy(product.id.desc())
                .limit(size + 1L)
                .fetch();
    }

    /**
     * 특정 카테고리 하위 상품 수 집계 (카테고리 통계용)
     */
    public long countByCategory(Long categoryId) {
        Long count = queryFactory
                .select(product.count())
                .from(product)
                .where(product.category.id.eq(categoryId))
                .fetchOne();
        return count != null ? count : 0L;
    }

    private BooleanBuilder buildCondition(
            Long cursorId,
            Long categoryId,
            ProductStatus status,
            Long minPrice,
            Long maxPrice
    ) {
        BooleanBuilder condition = new BooleanBuilder();

        if (cursorId != null) {
            condition.and(product.id.lt(cursorId));
        }
        if (categoryId != null) {
            condition.and(product.category.id.eq(categoryId));
        }
        if (status != null) {
            condition.and(product.status.eq(status));
        }
        if (minPrice != null) {
            condition.and(product.price.goe(minPrice));
        }
        if (maxPrice != null) {
            condition.and(product.price.loe(maxPrice));
        }

        return condition;
    }
}