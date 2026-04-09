package com.booster.queryburst.ranking.web.dto.response;

import com.booster.queryburst.ranking.application.dto.ProductRankingResult;

public record ProductRankingResponse(
        int rank,
        Long productId,
        long salesCount
) {
    public static ProductRankingResponse from(ProductRankingResult result) {
        return new ProductRankingResponse(
                result.rank(),
                result.productId(),
                (long) result.salesCount()
        );
    }
}
