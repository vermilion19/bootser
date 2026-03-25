package com.booster.queryburst.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 최상위 카테고리 조회 (depth=1)
    List<Category> findByDepth(int depth);

    // 특정 부모의 하위 카테고리
    List<Category> findByParentId(Long parentId);

    // 계층 전체 조회 (fetch join으로 N+1 방지)
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.depth <= :maxDepth")
    List<Category> findAllWithParentByDepthLessThanEqual(@Param("maxDepth") int maxDepth);
}
