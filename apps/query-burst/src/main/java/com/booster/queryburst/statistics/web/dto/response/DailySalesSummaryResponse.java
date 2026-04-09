package com.booster.queryburst.statistics.web.dto.response;

import com.booster.queryburst.statistics.domain.DailySalesSummary;

import java.time.LocalDate;

public record DailySalesSummaryResponse(
        LocalDate date,
        Long categoryId,
        Long totalAmount,
        Long orderCount
) {
    public static DailySalesSummaryResponse from(DailySalesSummary summary) {
        return new DailySalesSummaryResponse(
                summary.getDate(),
                summary.getCategoryId(),
                summary.getTotalAmount(),
                summary.getOrderCount()
        );
    }
}
