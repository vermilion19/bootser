package com.booster.queryburstmsa.analytics.web.dto;

import com.booster.queryburstmsa.analytics.domain.entity.DailySalesSummaryEntity;

import java.time.LocalDate;

public record DailySalesSummaryView(
        LocalDate date,
        Long categoryId,
        long totalAmount,
        long orderCount
) {
    public static DailySalesSummaryView from(DailySalesSummaryEntity entity) {
        return new DailySalesSummaryView(
                entity.getDate(),
                entity.getCategoryId(),
                entity.getTotalAmount(),
                entity.getOrderCount()
        );
    }
}
