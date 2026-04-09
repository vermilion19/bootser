package com.booster.queryburst.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화.
 *
 * @Scheduled 어노테이션이 동작하려면 @EnableScheduling이 필요하다.
 * OutboxMessageRelay의 Outbox 폴링 스케줄러가 여기에 의존한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
