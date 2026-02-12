package com.booster.kotlin.chattingservice.config

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

@Configuration
@ConditionalOnProperty(name = ["chat.redis.enabled"], havingValue = "true", matchIfMissing = true)
class RedisConfig(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper
) {
    private val log = logger()

    // Redis 메시지 처리용 CoroutineScope (SupervisorJob: 개별 실패가 전체에 영향 안 줌)
    private val redisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Bean
    fun redisMessageListenerContainer(
        factory: ReactiveRedisConnectionFactory
    ): ReactiveRedisMessageListenerContainer {
        val container = ReactiveRedisMessageListenerContainer(factory)

        container.receive(ChannelTopic.of(ChatService.REDIS_TOPIC))
            .map { it.message }
            .doOnNext { json ->
                // Coroutine으로 비동기 브로드캐스트
                redisScope.launch {
                    try {
                        val message = objectMapper.readValue(json, ChatMessage::class.java)
                        chatService.broadcastToLocalUsers(message)
                    } catch (e: Exception) {
                        log.error("[REDIS] 메시지 역직렬화 실패: {}", e.message)
                    }
                }
            }
            .doOnError { e -> log.error("[REDIS] 리스너 에러: {}", e.message) }
            .subscribe()

        log.info("[REDIS] Pub/Sub 리스너 등록 완료: topic={}", ChatService.REDIS_TOPIC)
        return container
    }

    @PreDestroy
    fun destroy() {
        redisScope.cancel()
        log.info("[REDIS] CoroutineScope 종료")
    }
}
