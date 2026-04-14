package com.booster.queryburstmsa.order.infrastructure;

import com.booster.queryburstmsa.contracts.inventory.InventoryReservationRequest;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationResponse;
import com.booster.queryburstmsa.contracts.inventory.InventoryReservationStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

@Component
public class CatalogServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceClient.class);

    private final RestClient restClient;
    private final CircuitBreaker reserveCb;
    private final CircuitBreaker commitCb;
    private final CircuitBreaker releaseCb;

    public CatalogServiceClient(
            @Value("${query-burst-msa.clients.catalog.base-url}") String baseUrl,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        // RestClient에 커넥트/읽기 타임아웃 명시 — CB TimeLimiter보다 짧게 설정해야
        // 소켓 레벨에서 먼저 실패를 감지하고 CB가 이를 실패로 기록할 수 있음
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        // CatalogCircuitBreakerConfig에서 정의한 named 인스턴스 사용
        this.reserveCb = circuitBreakerFactory.create("catalog-reserve");
        this.commitCb  = circuitBreakerFactory.create("catalog-commit");
        this.releaseCb = circuitBreakerFactory.create("catalog-release");
    }

    /**
     * 재고 예약 — 주문 생성의 핵심 경로
     *
     * CB OPEN 시: 즉시 REJECTED 반환 (catalog-service 호출 없음)
     * 예외 발생 시: 실패로 기록 후 REJECTED 반환
     */
    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        return reserveCb.run(
                () -> {
                    InventoryReservationResponse response = restClient.post()
                            .uri("/internal/inventory/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(InventoryReservationResponse.class);
                    if (response == null) {
                        throw new RestClientException("catalog-service: empty response on reserve");
                    }
                    return response;
                },
                throwable -> reserveFallback(throwable)
        );
    }

    /**
     * 재고 확정 — 결제 시 호출
     *
     * 실패 시에도 주문은 PAID 상태로 진행 (보상 트랜잭션은 운영자가 처리)
     */
    public InventoryReservationResponse commit(String reservationId) {
        return commitCb.run(
                () -> {
                    InventoryReservationResponse response = restClient.post()
                            .uri("/internal/inventory/reservations/{id}/commit", reservationId)
                            .retrieve()
                            .body(InventoryReservationResponse.class);
                    if (response == null) {
                        throw new RestClientException("catalog-service: empty response on commit");
                    }
                    return response;
                },
                throwable -> commitReleaseFallback(reservationId, "commit", throwable)
        );
    }

    /**
     * 재고 반환 — 주문 취소 시 호출
     *
     * 실패 시 재고 불일치 발생 → 모니터링 필요 (CIRCUIT_OPEN 로그 확인)
     */
    public InventoryReservationResponse release(String reservationId) {
        return releaseCb.run(
                () -> {
                    InventoryReservationResponse response = restClient.post()
                            .uri("/internal/inventory/reservations/{id}/release", reservationId)
                            .retrieve()
                            .body(InventoryReservationResponse.class);
                    if (response == null) {
                        throw new RestClientException("catalog-service: empty response on release");
                    }
                    return response;
                },
                throwable -> commitReleaseFallback(reservationId, "release", throwable)
        );
    }

    private InventoryReservationResponse reserveFallback(Throwable throwable) {
        boolean circuitOpen = throwable instanceof CallNotPermittedException;
        String reason = circuitOpen ? "CIRCUIT_OPEN" : "CATALOG_UNAVAILABLE";
        if (circuitOpen) {
            log.warn("[CB] catalog-service reserve 차단됨 (서킷 OPEN)");
        } else {
            log.warn("[CB] catalog-service reserve 실패: {}", throwable.getMessage());
        }
        return new InventoryReservationResponse(null, InventoryReservationStatus.REJECTED, reason, List.of());
    }

    private InventoryReservationResponse commitReleaseFallback(String reservationId, String op, Throwable throwable) {
        boolean circuitOpen = throwable instanceof CallNotPermittedException;
        String reason = circuitOpen ? "CIRCUIT_OPEN" : "CATALOG_UNAVAILABLE";
        if (circuitOpen) {
            log.warn("[CB] catalog-service {} 차단됨 (서킷 OPEN) reservationId={}", op, reservationId);
        } else {
            log.warn("[CB] catalog-service {} 실패 reservationId={}: {}", op, reservationId, throwable.getMessage());
        }
        return new InventoryReservationResponse(reservationId, InventoryReservationStatus.REJECTED, reason, List.of());
    }
}