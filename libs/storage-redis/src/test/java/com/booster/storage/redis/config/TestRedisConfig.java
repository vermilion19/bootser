package com.booster.storage.redis.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;

    public TestRedisConfig() {
        this.redisServer = new RedisServer(6380);
    }

    @PostConstruct
    public void startRedis() {
        try {
            redisServer.start();
        } catch (Exception e) {
            // 이미 서버가 실행 중인 경우를 대비
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
