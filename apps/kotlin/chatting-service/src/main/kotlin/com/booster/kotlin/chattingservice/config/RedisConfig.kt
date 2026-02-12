package com.booster.kotlin.chattingservice.config

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

@Configuration
class RedisConfig(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper
) {
    private val log = logger()

    @Bean
    fun redisMessageListenerContainer(
        factory: ReactiveRedisConnectionFactory
    ): ReactiveRedisMessageListenerContainer {
        val container = ReactiveRedisMessageListenerContainer(factory)

        container.receive(ChannelTopic.of(ChatService.REDIS_TOPIC))
            .map { it.message }
            .doOnNext { json ->
                try {
                    val message = objectMapper.readValue(json, ChatMessage::class.java)
                    chatService.broadcastToLocalUsers(message)
                } catch (e: Exception) {
                    log.error("[REDIS] 메시지 역직렬화 실패: {}", e.message)
                }
            }
            .doOnError { e -> log.error("[REDIS] 리스너 에러: {}", e.message) }
            .subscribe()

        log.info("[REDIS] Pub/Sub 리스너 등록 완료: topic={}", ChatService.REDIS_TOPIC)
        return container
    }
}
