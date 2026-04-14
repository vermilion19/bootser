package com.booster.queryburstmsa.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * catalog-service 호출 전용 서킷브레이커 설정
 *
 * 인스턴스 분리 이유:
 *   - catalog-reserve : 주문 생성의 핵심 경로. 실패 시 주문 자체가 REJECTED → 엄격한 기준
 *   - catalog-commit  : 결제 시 재고 확정. 실패해도 재시도 가능 → 중간 기준
 *   - catalog-release : 주문 취소 시 재고 반환. 실패 시 수동 보상 → 중간 기준
 *
 * 상태 전이:
 *   CLOSED → (실패율 >= threshold) → OPEN → (waitDuration 후) → HALF_OPEN → (프로브 성공) → CLOSED
 */
@Configuration
class CatalogCircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> catalogCircuitBreakerCustomizer() {
        // reserve: 주문 생성 핵심 경로 — 민감하게 반응
        CircuitBreakerConfig reserveConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                          // 50% 실패 시 OPEN
                .waitDurationInOpenState(Duration.ofSeconds(10))   // 10초 후 HALF_OPEN 시도
                .slidingWindowSize(50)                             // 최근 50건 기준
                .minimumNumberOfCalls(10)                          // 최소 10건 이후 집계
                .permittedNumberOfCallsInHalfOpenState(5)          // HALF_OPEN에서 프로브 5건
                .recordExceptions(Exception.class)                 // 모든 예외를 실패로 기록
                .build();

        // commit / release: 보조 경로 — 상대적으로 관대하게
        CircuitBreakerConfig commitReleaseConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(Exception.class)
                .build();

        TimeLimiterConfig reserveTimeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build();

        TimeLimiterConfig commitReleaseTimeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .build();

        return factory -> {
            factory.configure(
                    (Resilience4JConfigBuilder builder) -> builder
                            .circuitBreakerConfig(reserveConfig)
                            .timeLimiterConfig(reserveTimeLimiter),
                    "catalog-reserve"
            );
            factory.configure(
                    (Resilience4JConfigBuilder builder) -> builder
                            .circuitBreakerConfig(commitReleaseConfig)
                            .timeLimiterConfig(commitReleaseTimeLimiter),
                    "catalog-commit", "catalog-release"
            );
        };
    }
}