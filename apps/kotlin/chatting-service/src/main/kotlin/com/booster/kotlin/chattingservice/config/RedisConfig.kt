package com.booster.kotlin.chattingservice.config

import com.booster.kotlin.core.logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

/**
 * Redis 설정.
 *
 * ReactiveRedisMessageListenerContainer만 Bean으로 등록.
 * 채널 구독은 ChatService가 방(room) 단위로 동적 관리:
 *   - 첫 유저 입장 시 chat.room.{roomId} 채널 구독
 *   - 마지막 유저 퇴장 시 채널 구독 해제
 *
 * 이전 구조 (chat.public 단일 채널):
 *   모든 서버가 모든 메시지를 수신 → 불필요한 처리 발생
 *
 * 변경 후 구조 (chat.room.{roomId} 방별 채널):
 *   해당 방에 유저가 있는 서버만 구독 → 불필요한 수신 제거
 */
@Configuration
@ConditionalOnProperty(name = ["chat.redis.enabled"], havingValue = "true", matchIfMissing = true)
class RedisConfig {

    private val log = logger()

    @Bean
    fun redisMessageListenerContainer(
        factory: ReactiveRedisConnectionFactory
    ): ReactiveRedisMessageListenerContainer {
        log.info("[REDIS] ReactiveRedisMessageListenerContainer 생성 완료")
        return ReactiveRedisMessageListenerContainer(factory)
    }
}
