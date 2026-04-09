package com.booster.queryburst.statistics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductDailySalesRepository extends JpaRepository<ProductDailySales, Long> {

    Optional<ProductDailySales> findByDateAndProductId(LocalDate date, Long productId);

    /**
     * 날짜별 판매량 TOP N 조회.
     * 인덱스: idx_product_daily_date_sold_count (date, sold_count)
     */
    List<ProductDailySales> findTop10ByDateOrderBySoldCountDesc(LocalDate date);

    List<ProductDailySales> findByDateBetweenAndProductIdOrderByDateAsc(LocalDate from, LocalDate to, Long productId);
}
