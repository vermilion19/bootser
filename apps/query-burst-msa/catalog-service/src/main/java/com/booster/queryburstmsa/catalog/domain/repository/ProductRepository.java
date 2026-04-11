package com.booster.queryburstmsa.catalog.domain.repository;

import com.booster.queryburstmsa.catalog.domain.ProductStatus;
import com.booster.queryburstmsa.catalog.domain.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    @Query("""
            select p
            from ProductEntity p
            where (:cursor is null or p.id < :cursor)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:status is null or p.status = :status)
            order by p.id desc
            """)
    List<ProductEntity> findProducts(
            @Param("cursor") Long cursor,
            @Param("categoryId") Long categoryId,
            @Param("status") ProductStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id in :ids")
    List<ProductEntity> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
