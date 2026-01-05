package com.booster.storage.redis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class RedisTestConfig {
    @Bean
    @ServiceConnection(name = "redis") // 👈 호스트, 포트 자동 주입!
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:alpine"))
                .withExposedPorts(6379);
    }
}
