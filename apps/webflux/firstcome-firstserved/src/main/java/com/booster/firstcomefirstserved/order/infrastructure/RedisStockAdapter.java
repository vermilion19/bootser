package com.booster.firstcomefirstserved.order.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisStockAdapter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    // Lua Script 로드
    private final RedisScript<Long> decreaseStockScript = RedisScript.of(
            new ClassPathResource("scripts/decrease_stock.lua"), Long.class
    );


    /**
     * 재고 차감 시도 (Atomic)
     * @param itemId 상품 ID
     * @param quantity 차감 수량
     * @return true: 차감 성공, false: 재고 부족
     */
    public Mono<Boolean> decreaseStock(Long itemId, int quantity) {
        String key = "item:stock:" + itemId;

        return redisTemplate.execute(decreaseStockScript, List.of(key), List.of(String.valueOf(quantity)))
                .next()
                .map(result -> result >= 0);
    }

    public Mono<Boolean> setStock(Long itemId, int quantity) {
        String key = "item:stock:" + itemId;
        return redisTemplate.opsForValue().set(key, String.valueOf(quantity));
    }

}
