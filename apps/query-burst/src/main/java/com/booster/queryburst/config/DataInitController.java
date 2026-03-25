package com.booster.queryburst.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping("/api/data-init")
@RequiredArgsConstructor
public class DataInitController {

    private final DataInitializer dataInitializer;

    /**
     * 더미 데이터 적재 시작 (비동기 - 즉시 반환)
     * POST /api/data-init
     */
    @PostMapping
    public ResponseEntity<String> start() {
        try {
            dataInitializer.initializeAsync();
            return ResponseEntity.accepted()
                .body("더미 데이터 적재를 시작했습니다. GET /api/data-init/status 로 진행 상황을 확인하세요.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    /**
     * 적재 진행 상황 조회
     * GET /api/data-init/status
     */
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        String running = dataInitializer.getRunning().get() ? "실행 중" : "미실행";
        return ResponseEntity.ok("상태: " + running + " | " + dataInitializer.getStatus().get());
    }
}
