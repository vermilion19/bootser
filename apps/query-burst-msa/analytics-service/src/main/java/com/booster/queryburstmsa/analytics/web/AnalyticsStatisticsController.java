package com.booster.queryburstmsa.analytics.web;

import com.booster.queryburstmsa.analytics.application.AnalyticsService;
import com.booster.queryburstmsa.analytics.web.dto.DailySalesSummaryView;
import com.booster.queryburstmsa.analytics.web.dto.ProductDailySalesView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/statistics")
public class AnalyticsStatisticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsStatisticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/daily-sales")
    public ResponseEntity<List<DailySalesSummaryView>> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getDailySales(from, to));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<ProductDailySalesView>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(analyticsService.getTopProducts(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/products/{productId}/trend")
    public ResponseEntity<List<ProductDailySalesView>> getProductTrend(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getProductTrend(productId, from, to));
    }
}
