package com.booster.queryburstmsa.ranking.web;

import com.booster.queryburstmsa.ranking.application.RankingService;
import com.booster.queryburstmsa.ranking.web.dto.ProductRankingView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/realtime")
    public ResponseEntity<List<ProductRankingView>> getRealtimeRanking(
            @RequestParam(defaultValue = "1") int windowHours,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (windowHours < 1 || windowHours > 24 || size < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(rankingService.getTopProducts(windowHours, size));
    }
}
