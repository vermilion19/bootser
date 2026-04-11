package com.booster.queryburstmsa.analytics.domain.repository;

import com.booster.queryburstmsa.analytics.domain.entity.ProductDailySalesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductDailySalesRepository extends JpaRepository<ProductDailySalesEntity, Long> {
    Optional<ProductDailySalesEntity> findByDateAndProductId(LocalDate date, Long productId);
    List<ProductDailySalesEntity> findTop10ByDateOrderBySoldCountDesc(LocalDate date);
    List<ProductDailySalesEntity> findByDateBetweenAndProductIdOrderByDateAsc(LocalDate from, LocalDate to, Long productId);
}
