package com.booster.dday.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄과 분산 락.
 *
 * <p>스케줄 작업이 여덟이다 (ARCHITECTURE §5.6 + SCHEMA §10 이 더한 보존 청소).
 * 전부 <b>같은 이름 규칙의 락</b>을 잡는다 — {@code dday-*}.
 *
 * <pre>
 *   dday-sync-HOLIDAY              주 1회 · POST /admin/sync/holiday
 *   dday-sync-SPORT_EVENT          10분   · POST /admin/sync/sport-event
 *   dday-sync-MOVIE                일 1회 · 수동
 *   dday-outbox-relay              3초      ← 지금 서 있는 것
 *   dday-cache-warm                플립 전 워밍
 *   dday-anniversary-notify        알림 스캔
 *   dday-occurrence-rollforward    매일 1/365
 *   dday-retention                 보존 청소
 * </pre>
 *
 * <h2>{@code @EnableSchedulerLock} 을 쓰지 않는다</h2>
 *
 * <p>{@code @SchedulerLock} 애노테이션은 스케줄 메서드에만 붙는다. 그런데 동기화는
 * 운영자가 {@code POST /admin/sync/{target}} 으로도 시작할 수 있고 <b>그것은 스케줄
 * 메서드가 아니다</b>. 애노테이션만 믿으면 스케줄러가 도는 중에 운영자가 눌러
 * 이중 실행이 된다 (§5.6). 그래서 {@link LockProvider} 를 직접 주입해 명령형으로
 * 잡는다 — 스케줄 경로와 수동 경로가 <b>같은 이름</b>을 쓰게.
 *
 * <h2>접두사를 {@code waiting-service} 와 다르게 둔다</h2>
 *
 * <p>같은 Redis 를 쓰면 락 키가 섞인다. {@code waiting-service} 는
 * {@code "waiting-lock"} 을 쓴다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "dday-lock");
    }
}
