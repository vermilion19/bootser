package com.booster.kotlin.chattingservice.application

import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.chattingservice.infrastructure.SessionRegistryService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.data.redis.core.ReactiveListOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ChatServiceHistoryTest {

    private lateinit var chatService: ChatService
    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var opsForValue: ReactiveValueOperations<String, String>
    private lateinit var opsForList: ReactiveListOperations<String, String>

    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate::class.java)
        opsForValue = mock()
        opsForList = mock()

        `when`(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.just(1L))
        `when`(redisTemplate.opsForValue()).thenReturn(opsForValue)
        `when`(redisTemplate.opsForList()).thenReturn(opsForList)
        `when`(opsForValue.increment(any())).thenReturn(Mono.just(1L))
        `when`(opsForList.rightPush(any(), any())).thenReturn(Mono.just(1L))
        `when`(opsForList.trim(any(), any(), any())).thenReturn(Mono.just(true))
        `when`(opsForList.range(any(), any(), any())).thenReturn(Flux.empty())

        val sessionRegistry = mock(SessionRegistryService::class.java)
        val listenerContainer = mock(ReactiveRedisMessageListenerContainer::class.java)
        `when`(listenerContainer.receive(any<ChannelTopic>())).thenReturn(Flux.empty())

        chatService = ChatService(
            redisTemplate, mapper, SimpleMeterRegistry(),
            listenerContainer, sessionRegistry, "test-instance",
            gracefulShutdownDelayMs = 0
        )
    }

    @Nested
    inner class SequenceAssignment {

        @Test
        fun `TALK 메시지 발행 시 seq 카운터가 증가한다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            chatService.handleMessage(ChatMessage.talk("room-1", "user1", "hello"))

            verify(opsForValue).increment("chat.seq.room-1")
        }

        @Test
        fun `TALK 메시지 발행 시 히스토리에 저장된다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            chatService.handleMessage(ChatMessage.talk("room-1", "user1", "hello"))

            verify(opsForList).rightPush(eq("chat.history.room-1"), any())
            verify(opsForList).trim(eq("chat.history.room-1"), any(), any())
        }

        @Test
        fun `ENTER 메시지는 히스토리에 저장되지 않는다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            verify(opsForList, never()).rightPush(any(), any())
        }

        @Test
        fun `LEAVE 메시지는 히스토리에 저장되지 않는다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.leave("room-1", "user1"))

            verify(opsForList, never()).rightPush(any(), any())
        }
    }

    @Nested
    inner class ReconnectionReplay {

        @Test
        fun `lastSeq가 있으면 해당 seq 이후 메시지를 재전송한다`() = runTest {
            val msg1 = ChatMessage.talk("room-1", "other", "first").copy(seq = 5L)
            val msg2 = ChatMessage.talk("room-1", "other", "second").copy(seq = 6L)
            `when`(opsForList.range("chat.history.room-1", 0, -1))
                .thenReturn(Flux.just(mapper.writeValueAsString(msg1), mapper.writeValueAsString(msg2)))

            val flow = chatService.register("user1")
            val received = async { flow.take(2).toList() }
            delay(50)

            chatService.handleMessage(ChatMessage.enter("room-1", "user1").copy(lastSeq = 4L))

            val messages = withTimeout(2000) { received.await() }
            assertThat(messages.map { it.seq }).containsExactly(5L, 6L)
        }

        @Test
        fun `lastSeq보다 오래된 메시지는 재전송하지 않는다`() = runTest {
            val oldMsg = ChatMessage.talk("room-1", "other", "old").copy(seq = 3L)
            val newMsg = ChatMessage.talk("room-1", "other", "new").copy(seq = 7L)
            `when`(opsForList.range("chat.history.room-1", 0, -1))
                .thenReturn(Flux.just(mapper.writeValueAsString(oldMsg), mapper.writeValueAsString(newMsg)))

            val flow = chatService.register("user1")
            val received = async { flow.first() }
            delay(50)

            // lastSeq=5 → seq=3인 메시지는 재전송 안 함, seq=7만 재전송
            chatService.handleMessage(ChatMessage.enter("room-1", "user1").copy(lastSeq = 5L))

            val message = withTimeout(2000) { received.await() }
            assertThat(message.seq).isEqualTo(7L)
            assertThat(message.message).isEqualTo("new")
        }

        @Test
        fun `lastSeq=0이면 히스토리 조회를 하지 않는다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            verify(opsForList, never()).range(any(), any(), any())
        }

        @Test
        fun `방에 히스토리가 없으면 재전송하지 않는다`() = runTest {
            `when`(opsForList.range("chat.history.room-1", 0, -1)).thenReturn(Flux.empty())

            val flow = chatService.register("user1")

            val received = async {
                flow.first()
            }
            delay(50)

            // 히스토리 없음 → 재전송 메시지 없음 → 이후 브로드캐스트로 받아야 함
            chatService.handleMessage(ChatMessage.enter("room-1", "user1").copy(lastSeq = 10L))
            chatService.broadcastToLocalUsers(ChatMessage.talk("room-1", "user1", "live message"))

            val message = withTimeout(2000) { received.await() }
            assertThat(message.message).isEqualTo("live message")
        }
    }
}
