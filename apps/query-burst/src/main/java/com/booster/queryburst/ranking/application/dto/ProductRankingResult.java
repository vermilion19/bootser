package com.booster.queryburst.ranking.application.dto;

/**
 * 실시간 상품 랭킹 결과.
 *
 * Redis Sorted Set ZREVRANGE 결과를 담는다.
 * score = 해당 윈도우 기간 동안의 누적 판매 수량.
 */
public record ProductRankingResult(
        Long productId,
        double salesCount,
        int rank
) {
}
