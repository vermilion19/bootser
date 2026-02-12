package com.booster.kotlin.chattingservice

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

@TestConfiguration
class TestConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        return mock(ReactiveRedisConnectionFactory::class.java)
    }

    /**
     * Redis Pub/Sub를 시뮬레이션하는 mock template.
     * convertAndSend 호출 시 직접 broadcastToLocalUsers로 전달하여
     * 통합 테스트에서 메시지 수신/발신을 검증할 수 있게 한다.
     */
    @Bean
    fun reactiveStringRedisTemplate(@Lazy chatService: ChatService): ReactiveStringRedisTemplate {
        val mapper = jacksonObjectMapper()
        val template = mock(ReactiveStringRedisTemplate::class.java)
        `when`(template.convertAndSend(any<String>(), any<String>()))
            .thenAnswer { invocation ->
                val json = invocation.getArgument<String>(1)
                try {
                    val message = mapper.readValue<ChatMessage>(json)
                    kotlinx.coroutines.runBlocking {
                        chatService.broadcastToLocalUsers(message)
                    }
                } catch (_: Exception) {}
                Mono.just(1L)
            }
        return template
    }
}
