package com.booster.queryburstmsa.analytics.web.dto;

import com.booster.queryburstmsa.analytics.domain.entity.ProductDailySalesEntity;

import java.time.LocalDate;

public record ProductDailySalesView(
        LocalDate date,
        Long productId,
        int soldCount,
        long revenue
) {
    public static ProductDailySalesView from(ProductDailySalesEntity entity) {
        return new ProductDailySalesView(
                entity.getDate(),
                entity.getProductId(),
                entity.getSoldCount(),
                entity.getRevenue()
        );
    }
}
