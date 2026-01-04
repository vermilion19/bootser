package com.booster.waitingservice.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;

@Slf4j
//@Profile("out") // 프로파일 변경 (work -> out)
//@Configuration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        try {
            redisServer = RedisServer.newRedisServer()
                    .port(6379)
                    .setting("maxmemory 32M")
                    .build();

            redisServer.start();
            log.info("✅ Embedded Redis started on port 6379 (Profile: out)");
        } catch (Exception e) {
            log.error("❌ Failed to start Embedded Redis", e);
        }
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
            log.info("🛑 Embedded Redis stopped");
        }
    }
}
