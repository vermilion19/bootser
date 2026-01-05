package com.booster.waitingservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // Redis를 이용해 잠금을 관리합니다.
        // 환경 변수(ENV) 등으로 테이블 접두사를 구분할 수도 있습니다.
        return new RedisLockProvider(connectionFactory, "waiting-lock");
    }
}
