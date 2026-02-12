package com.booster.kotlin.chattingservice.application

import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

class ChatServiceTest {

    private lateinit var chatService: ChatService
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @BeforeEach
    fun setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate::class.java)
        `when`(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.just(1L))

        chatService = ChatService(redisTemplate, jacksonObjectMapper(), SimpleMeterRegistry())
    }

    @Nested
    inner class Register {

        @Test
        fun `register 호출 시 커넥션 카운트가 증가한다`() {
            assertThat(chatService.getConnectionCount()).isEqualTo(0)

            chatService.register("user1")
            assertThat(chatService.getConnectionCount()).isEqualTo(1)

            chatService.register("user2")
            assertThat(chatService.getConnectionCount()).isEqualTo(2)
        }

        @Test
        fun `register는 메시지를 수신할 수 있는 Flow를 반환한다`() = runTest {
            val flow = chatService.register("user1")

            // 방 입장
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            // Flow collector를 먼저 시작한 후 메시지 broadcast
            val received = async {
                flow.first()
            }

            // collector가 구독할 시간을 확보
            delay(50)

            // broadcast → Flow로 메시지 전달
            chatService.broadcastToLocalUsers(ChatMessage.talk("room-1", "user1", "hello"))

            val message = withTimeout(2000) { received.await() }
            assertThat(message.message).isEqualTo("hello")
        }
    }

    @Nested
    inner class HandleMessage {

        @Test
        fun `PING 메시지는 Redis에 발행하지 않고 무시한다`() = runTest {
            chatService.handleMessage(ChatMessage.ping("room-1", "user1"))

            assertThat(chatService.getRoomCount()).isEqualTo(0)
        }

        @Test
        fun `ENTER 메시지는 방에 유저를 추가한다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))

            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(1)
        }

        @Test
        fun `LEAVE 메시지는 방에서 유저를 제거한다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(1)

            chatService.handleMessage(ChatMessage.leave("room-1", "user1"))
            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(0)
        }

        @Test
        fun `같은 방에 여러 유저가 입장할 수 있다`() = runTest {
            chatService.register("user1")
            chatService.register("user2")
            chatService.register("user3")

            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.enter("room-1", "user2"))
            chatService.handleMessage(ChatMessage.enter("room-1", "user3"))

            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(3)
        }

        @Test
        fun `유저는 여러 방에 동시에 입장할 수 있다`() = runTest {
            chatService.register("user1")

            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.enter("room-2", "user1"))

            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(1)
            assertThat(chatService.getRoomUserCount("room-2")).isEqualTo(1)
            assertThat(chatService.getRoomCount()).isEqualTo(2)
        }
    }

    @Nested
    inner class Broadcast {

        @Test
        fun `같은 방 유저에게만 메시지가 전달된다`() = runTest {
            val flowRoom1User = chatService.register("user1")
            chatService.register("user2")

            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.enter("room-2", "user2"))

            // room-1 유저의 collector
            val received = async {
                flowRoom1User.first()
            }
            delay(50)

            // room-1에만 broadcast
            chatService.broadcastToLocalUsers(ChatMessage.talk("room-1", "user1", "hello room-1"))

            val message = withTimeout(2000) { received.await() }
            assertThat(message.message).isEqualTo("hello room-1")
        }

        @Test
        fun `존재하지 않는 방에 브로드캐스트해도 에러가 발생하지 않는다`() = runTest {
            val message = ChatMessage.talk("nonexistent-room", "user1", "hello")
            chatService.broadcastToLocalUsers(message)
        }
    }

    @Nested
    inner class Remove {

        @Test
        fun `remove 호출 시 커넥션 카운트가 감소한다`() {
            chatService.register("user1")
            chatService.register("user2")
            assertThat(chatService.getConnectionCount()).isEqualTo(2)

            chatService.remove("user1")
            assertThat(chatService.getConnectionCount()).isEqualTo(1)
        }

        @Test
        fun `remove 호출 시 모든 방에서 유저가 제거된다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.enter("room-2", "user1"))

            chatService.remove("user1")

            assertThat(chatService.getRoomUserCount("room-1")).isEqualTo(0)
            assertThat(chatService.getRoomUserCount("room-2")).isEqualTo(0)
        }

        @Test
        fun `이미 제거된 유저를 다시 remove해도 에러가 발생하지 않는다`() {
            chatService.register("user1")
            chatService.remove("user1")
            chatService.remove("user1")
            assertThat(chatService.getConnectionCount()).isEqualTo(0)
        }

        @Test
        fun `마지막 유저가 방을 나가면 방이 제거된다`() = runTest {
            chatService.register("user1")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            assertThat(chatService.getRoomCount()).isEqualTo(1)

            chatService.remove("user1")
            assertThat(chatService.getRoomCount()).isEqualTo(0)
        }

        @Test
        fun `존재하지 않는 유저를 remove해도 에러가 발생하지 않는다`() {
            chatService.remove("ghost-user")
            assertThat(chatService.getConnectionCount()).isEqualTo(0)
        }
    }

    @Nested
    inner class Cleanup {

        @Test
        fun `cleanup 호출 시 모든 방이 정리된다`() = runTest {
            chatService.register("user1")
            chatService.register("user2")
            chatService.handleMessage(ChatMessage.enter("room-1", "user1"))
            chatService.handleMessage(ChatMessage.enter("room-1", "user2"))

            chatService.cleanup()

            assertThat(chatService.getRoomCount()).isEqualTo(0)
        }
    }
}
