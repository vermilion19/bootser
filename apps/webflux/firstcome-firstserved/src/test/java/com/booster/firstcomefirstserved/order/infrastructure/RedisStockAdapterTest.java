package com.booster.firstcomefirstserved.order.infrastructure;

import com.booster.firstcomefirstserved.order.infrastructure.adapter.RedisStockAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisStockAdapterTest {

    @Autowired
    private RedisStockAdapter redisStockAdapter;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private final Long ITEM_ID = 9999L; // 테스트용 아이템 ID

    @BeforeEach
    void setup() {
        // 테스트 시작 전 재고를 10개로 셋팅
        // block()을 써서 확실하게 데이터가 들어간 뒤 테스트 시작 (테스트 환경이므로 허용)
        redisStockAdapter.set(ITEM_ID, 10).block();
    }

    @AfterEach
    void cleanup() {
        // 테스트 후 데이터 정리
        redisTemplate.delete("item:stock:" + ITEM_ID).block();
    }

    @Test
    @DisplayName("Lua Script: 재고가 충분하면 차감에 성공한다 (10 -> 9)")
    void decreaseStock_success() {
        StepVerifier.create(redisStockAdapter.decrease(ITEM_ID, 1))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Lua Script: 재고보다 많이 주문하면 실패한다 (10 -> 주문 11)")
    void decreaseStock_fail_over_quantity() {
        StepVerifier.create(redisStockAdapter.decrease(ITEM_ID, 11))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Lua Script: 연속으로 차감해도 정합성이 보장된다")
    void decreaseStock_concurrency() {
        // 5번 연속 차감 시도 -> 모두 true여야 함
        StepVerifier.create(redisStockAdapter.decrease(ITEM_ID, 2))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(redisStockAdapter.decrease(ITEM_ID, 3))
                .expectNext(true)
                .verifyComplete();

        // 남은 재고 검증: 10 - 2 - 3 = 5
        StepVerifier.create(redisTemplate.opsForValue().get("item:stock:" + ITEM_ID))
                .expectNext("5")
                .verifyComplete();
    }

}