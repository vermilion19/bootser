package com.booster.firstcomefirstserved.order.web;

import com.booster.firstcomefirstserved.order.infrastructure.adapter.RedisStockAdapter;
import com.booster.firstcomefirstserved.order.web.dto.OrderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class IdempotencyTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RedisStockAdapter redisStockAdapter;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setup() {
        // 테스트용 재고 100개 세팅
        redisStockAdapter.set(1L, 100).block();
        // 기존 멱등성 키 초기화
        redisTemplate.keys("idempotency:*").flatMap(redisTemplate::delete).blockLast();
    }

    @Test
    @DisplayName("멱등성: 동일한 키로 두 번 요청하면 재고는 한 번만 차감된다")
    void testIdempotency() {
        // Given
        String key = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(100L, 1L, 1);

        // When 1: 첫 번째 요청 (정상 처리)
        webTestClient.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        // When 2: 두 번째 요청 (중복 키)
        webTestClient.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is2xxSuccessful(); // AOP에서 ResponseEntity.ok()로 리턴하므로 200 OK (상태코드 정책에 따라 다름)

        // Then: 재고 확인 (100 -> 99 이어야 함. 98이면 실패)
        // RedisStockAdapter에 getStock 메서드가 없다면 RedisTemplate으로 직접 조회
        String stockStr = redisTemplate.opsForValue().get("item:stock:1").block();
        assertThat(stockStr).isEqualTo("99");
    }
}
