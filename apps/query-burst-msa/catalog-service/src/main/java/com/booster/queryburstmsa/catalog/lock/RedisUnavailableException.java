package com.booster.queryburstmsa.catalog.lock;

public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String key, Throwable cause) {
        super("Redis unavailable for distributed lock. key=" + key, cause);
    }
}
