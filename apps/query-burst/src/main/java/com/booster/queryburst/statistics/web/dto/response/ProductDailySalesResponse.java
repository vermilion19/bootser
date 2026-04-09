package com.booster.queryburst.statistics.web.dto.response;

import com.booster.queryburst.statistics.domain.ProductDailySales;

import java.time.LocalDate;

public record ProductDailySalesResponse(
        LocalDate date,
        Long productId,
        Long soldCount,
        Long revenue
) {
    public static ProductDailySalesResponse from(ProductDailySales productDailySales) {
        return new ProductDailySalesResponse(
                productDailySales.getDate(),
                productDailySales.getProductId(),
                productDailySales.getSoldCount(),
                productDailySales.getRevenue()
        );
    }
}
