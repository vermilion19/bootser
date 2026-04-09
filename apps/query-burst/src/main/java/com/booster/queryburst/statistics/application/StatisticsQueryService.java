package com.booster.queryburst.statistics.application;

import com.booster.queryburst.statistics.domain.DailySalesSummary;
import com.booster.queryburst.statistics.domain.DailySalesSummaryRepository;
import com.booster.queryburst.statistics.domain.ProductDailySales;
import com.booster.queryburst.statistics.domain.ProductDailySalesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * CQRS Read Side: 통계 조회 서비스.
 *
 * 비정규화된 daily_sales_summary, product_daily_sales 테이블에서 조회한다.
 * 조인 없이 단순 SELECT → 대용량에서도 일관된 빠른 응답.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatisticsQueryService {

    private final DailySalesSummaryRepository dailySalesSummaryRepository;
    private final ProductDailySalesRepository productDailySalesRepository;

    /**
     * 기간별 카테고리 매출 집계 조회.
     * 인덱스: idx_daily_sales_date_category (date, category_id)
     */
    public List<DailySalesSummary> getDailySalesSummary(LocalDate from, LocalDate to) {
        return dailySalesSummaryRepository.findByDateBetweenOrderByDateAsc(from, to);
    }

    /**
     * 특정 날짜 상품 판매 TOP 10.
     * 인덱스: idx_product_daily_date_sold_count (date, sold_count)
     */
    public List<ProductDailySales> getTopProductsByDate(LocalDate date) {
        return productDailySalesRepository.findTop10ByDateOrderBySoldCountDesc(date);
    }

    /**
     * 특정 상품의 기간별 판매 추이.
     */
    public List<ProductDailySales> getProductSalesTrend(Long productId, LocalDate from, LocalDate to) {
        return productDailySalesRepository
                .findByDateBetweenAndProductIdOrderByDateAsc(from, to, productId);
    }
}
