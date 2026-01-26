package com.booster.firstcomefirstserved.order.infrastructure.adapter;

import com.booster.firstcomefirstserved.order.domain.port.StockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Redis 기반 재고 관리 어댑터
 * - Lua Script를 사용한 원자적 재고 차감
 */
@Component
@RequiredArgsConstructor
public class RedisStockAdapter implements StockPort {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final RedisScript<Long> decreaseStockScript = RedisScript.of(
            new ClassPathResource("scripts/decrease_stock.lua"), Long.class
    );

    private static final String KEY_PREFIX = "item:stock:";

    @Override
    public Mono<Boolean> decrease(Long itemId, int quantity) {
        String key = KEY_PREFIX + itemId;

        return redisTemplate.execute(decreaseStockScript, List.of(key), List.of(String.valueOf(quantity)))
                .next()
                .map(result -> result >= 0);
    }

    @Override
    public Mono<Boolean> set(Long itemId, int quantity) {
        String key = KEY_PREFIX + itemId;
        return redisTemplate.opsForValue().set(key, String.valueOf(quantity));
    }

    @Override
    public Mono<Long> get(Long itemId) {
        String key = KEY_PREFIX + itemId;
        return redisTemplate.opsForValue().get(key)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }
}
