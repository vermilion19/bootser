package com.booster.gathererservice.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinPriceService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 현재가를 Redis Key-Value로 저장 (덮어쓰기)
     */
    public void saveCurrentPrice(String code, Double price) {
        if (code == null || price == null) return;

        // Key: coin:price:KRW-BTC
        String key = "coin:price:" + code;

        // Value: 100000000 (문자열)
        redisTemplate.opsForValue().set(key, String.valueOf(price));

        // (선택) 디버깅 로그 - 너무 많이 찍히면 주석 처리
        // log.debug("Saved {} -> {}", key, price);
    }
}
