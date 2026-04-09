package com.booster.queryburst.ranking.web;

import com.booster.queryburst.ranking.application.RankingService;
import com.booster.queryburst.ranking.web.dto.response.ProductRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 실시간 인기 상품 랭킹 API.
 *
 * Redis Sorted Set 기반 — DB 쿼리 없이 O(log N) 응답.
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /**
     * 슬라이딩 윈도우 인기 상품 TOP N.
     *
     * @param windowHours 윈도우 크기 (기본 1시간, 최대 24시간)
     * @param size        반환할 상위 N개 (기본 10)
     *
     * 예시:
     *   GET /api/rankings/realtime?windowHours=1   → 최근 1시간 TOP 10
     *   GET /api/rankings/realtime?windowHours=6   → 최근 6시간 TOP 10
     *   GET /api/rankings/realtime?windowHours=24  → 최근 24시간 TOP 10
     */
    @GetMapping("/realtime")
    public ResponseEntity<List<ProductRankingResponse>> getRealtimeRanking(
            @RequestParam(defaultValue = "1") int windowHours,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (windowHours < 1 || windowHours > 24) {
            return ResponseEntity.badRequest().build();
        }

        List<ProductRankingResponse> result = rankingService.getTopProducts(windowHours, size)
                .stream()
                .map(ProductRankingResponse::from)
                .toList();

        return ResponseEntity.ok(result);
    }
}
