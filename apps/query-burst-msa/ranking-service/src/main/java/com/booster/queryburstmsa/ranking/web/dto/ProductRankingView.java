package com.booster.queryburstmsa.ranking.web.dto;

public record ProductRankingView(
        Long productId,
        double score,
        int rank
) {
}
