package com.booster.queryburst.lock;

public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String key, Throwable cause) {
        super("Redis 연결 장애 — 분산 락 사용 불가. key=" + key, cause);
    }
}
