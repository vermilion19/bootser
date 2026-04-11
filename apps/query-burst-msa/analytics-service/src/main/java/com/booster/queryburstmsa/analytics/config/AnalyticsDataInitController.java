package com.booster.queryburstmsa.analytics.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/data-init")
@RequiredArgsConstructor
public class AnalyticsDataInitController {

    private final AnalyticsDataInitializer dataInitializer;

    @PostMapping
    public ResponseEntity<String> start() {
        try {
            dataInitializer.initializeAsync();
            return ResponseEntity.accepted().body("analytics 테스트 데이터 적재를 시작했습니다.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok((dataInitializer.getRunning().get() ? "실행 중" : "미실행") + " | " + dataInitializer.getStatus().get());
    }
}
