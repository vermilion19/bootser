package com.booster.kotlin.chattingservice

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveListOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@TestConfiguration
class TestConfig {

    // objectMapper()는 JacksonConfig가 Bean 등록 → 여기서 중복 정의 시 BeanDefinitionOverrideException

    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        return mock(ReactiveRedisConnectionFactory::class.java)
    }

    @Bean
    fun reactiveRedisMessageListenerContainer(): ReactiveRedisMessageListenerContainer {
        val container = mock(ReactiveRedisMessageListenerContainer::class.java)
        `when`(container.receive(any<ChannelTopic>())).thenReturn(Flux.empty())
        return container
    }

    /**
     * Redis Pub/Sub를 시뮬레이션하는 mock template.
     *
     * - convertAndSend: broadcastToLocalUsers를 직접 호출하여 Pub/Sub 흐름 재현
     * - opsForValue: TALK 메시지 seq 증가 (Phase 3-B 추가) 처리
     * - opsForList: TALK 메시지 히스토리 저장 처리
     * - delete: 방 소멸 시 히스토리/seq 키 삭제 처리
     */
    @Bean
    fun reactiveStringRedisTemplate(@Lazy chatService: ChatService): ReactiveStringRedisTemplate {
        val mapper = jacksonObjectMapper()
        val template = mock(ReactiveStringRedisTemplate::class.java)

        // seq 증가, 세션 등록 (TALK 메시지 발행, SessionRegistryService.register 시 호출)
        val opsForValue = mock<ReactiveValueOperations<String, String>>()
        `when`(opsForValue.increment(any())).thenReturn(Mono.just(1L))
        `when`(opsForValue.set(any<String>(), any<String>(), any<Duration>())).thenReturn(Mono.just(true))
        `when`(template.opsForValue()).thenReturn(opsForValue)

        // 히스토리 저장 (TALK 메시지 발행 시 호출)
        val opsForList = mock<ReactiveListOperations<String, String>>()
        `when`(opsForList.rightPush(any(), any())).thenReturn(Mono.just(1L))
        `when`(opsForList.trim(any(), any(), any())).thenReturn(Mono.just(true))
        `when`(opsForList.range(any(), any(), any())).thenReturn(Flux.empty())
        `when`(template.opsForList()).thenReturn(opsForList)

        // 방 키 삭제 (마지막 유저 퇴장 시 호출: 2-key), 세션 해제 (SessionRegistryService.unregister: 1-key)
        `when`(template.delete(any<String>())).thenReturn(Mono.just(1L))
        `when`(template.delete(any<String>(), any<String>())).thenReturn(Mono.just(2L))

        // Pub/Sub 시뮬레이션: convertAndSend → broadcastToLocalUsers 직접 호출
        // runBlocking → mono(Dispatchers.Default) 교체 이유:
        //   thenAnswer는 Netty 이벤트 루프에서 호출될 수 있음
        //   runBlocking은 이벤트 루프 스레드를 블로킹하여 데드락 위험
        `when`(template.convertAndSend(any<String>(), any<String>()))
            .thenAnswer { invocation ->
                val json = invocation.getArgument<String>(1)
                mono(Dispatchers.Default) {
                    try {
                        val message = mapper.readValue<ChatMessage>(json)
                        chatService.broadcastToLocalUsers(message)
                    } catch (_: Exception) {}
                    1L
                }
            }

        return template
    }
}
