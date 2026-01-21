package com.booster.gathererservice.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinPriceService {

    private final StringRedisTemplate redisTemplate;

    // SSE 초기 데이터 TTL (gatherer-service 장애 시 오래된 데이터 자동 만료)
    private static final Duration TRADE_DATA_TTL = Duration.ofMinutes(1);

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

    /**
     * 전체 거래 데이터를 Redis에 저장 (SSE 초기 데이터 전송용)
     * TTL 1분: gatherer-service가 정상 동작하면 계속 갱신되고, 장애 시 자동 만료
     */
    public void saveLatestTradeData(String code, String jsonPayload) {
        if (code == null || jsonPayload == null) return;

        // Key: coin:data:KRW-BTC
        String key = "coin:data:" + code;
        redisTemplate.opsForValue().set(key, jsonPayload, TRADE_DATA_TTL);
    }
}
