package com.booster.queryburst.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 카테고리별 활성 상품 (인덱스: category_id + status)
    List<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status);

    // 가격 범위 검색 (인덱스 효과 비교용)
    List<Product> findByPriceBetweenAndStatus(Long minPrice, Long maxPrice, ProductStatus status);

    // Fetch Join: 상품 + 카테고리 + 판매자 한 번에 조회 (N+1 방지)
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.category
            JOIN FETCH p.seller
            WHERE p.status = :status
            """)
    List<Product> findWithCategoryAndSellerByStatus(@Param("status") ProductStatus status);

    // 카테고리별 판매중 상품 수 집계
    @Query("""
            SELECT c.name, COUNT(p)
            FROM Product p
            JOIN p.category c
            WHERE p.status = 'ACTIVE'
            GROUP BY c.name
            ORDER BY COUNT(p) DESC
            """)
    List<Object[]> countActiveByCategory();

    // 커서 기반 페이징 (OFFSET 대비 성능 비교용)
    @Query("""
            SELECT p FROM Product p
            WHERE p.status = :status
              AND p.id > :lastId
            ORDER BY p.id ASC
            """)
    List<Product> findByStatusWithCursor(
            @Param("status") ProductStatus status,
            @Param("lastId") Long lastId,
            org.springframework.data.domain.Pageable pageable
    );
}
