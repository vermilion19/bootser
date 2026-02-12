package com.booster.kotlin.chattingservice.web

import com.booster.kotlin.chattingservice.TestConfig
import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration," +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
    ]
)
@Import(TestConfig::class)
class ChatWebSocketHandlerTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var chatService: ChatService

    private val client = ReactorNettyWebSocketClient()
    private val objectMapper = jacksonObjectMapper()

    private fun wsUri(userId: String): URI =
        URI.create("ws://localhost:$port/ws/chat?userId=$userId")

    @Nested
    inner class Connection {

        @Test
        fun `userId 없이 접속하면 세션이 즉시 종료된다`() {
            val latch = CountDownLatch(1)

            client.execute(URI.create("ws://localhost:$port/ws/chat")) { session ->
                session.receive()
                    .doOnTerminate { latch.countDown() }
                    .then()
            }.subscribe()

            val closed = latch.await(3, TimeUnit.SECONDS)
            assertThat(closed).isTrue()
        }

        @Test
        fun `userId를 포함하면 정상 접속된다`() {
            val connected = CountDownLatch(1)

            client.execute(wsUri("conn-test-user")) { session ->
                connected.countDown()
                Mono.delay(Duration.ofMillis(500)).then(session.close())
            }.subscribe()

            val result = connected.await(3, TimeUnit.SECONDS)
            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class Messaging {

        @Test
        fun `같은 방에 입장한 두 유저가 메시지를 주고받을 수 있다`() {
            val receivedMessages = CopyOnWriteArrayList<ChatMessage>()
            val messageLatch = CountDownLatch(1)

            // 유저 B: 수신 대기
            client.execute(wsUri("msg-userB")) { session ->
                val enter = objectMapper.writeValueAsString(
                    ChatMessage.enter("msg-room", "msg-userB")
                )
                val sendEnter = session.send(Mono.just(session.textMessage(enter)))

                val receive = session.receive()
                    .map { it.payloadAsText }
                    .doOnNext { json ->
                        val msg = objectMapper.readValue<ChatMessage>(json)
                        if (msg.type == ChatMessage.Type.TALK && msg.userId == "msg-userA") {
                            receivedMessages.add(msg)
                            messageLatch.countDown()
                        }
                    }
                    .then()

                sendEnter.then(receive)
            }.subscribe()

            Thread.sleep(500)

            // 유저 A: 입장 + 메시지 전송
            client.execute(wsUri("msg-userA")) { session ->
                val enter = objectMapper.writeValueAsString(
                    ChatMessage.enter("msg-room", "msg-userA")
                )
                val talk = objectMapper.writeValueAsString(
                    ChatMessage.talk("msg-room", "msg-userA", "안녕하세요!")
                )

                session.send(
                    Flux.just(
                        session.textMessage(enter),
                        session.textMessage(talk)
                    ).delayElements(Duration.ofMillis(200))
                ).then(Mono.delay(Duration.ofSeconds(2))).then()
            }.subscribe()

            val received = messageLatch.await(5, TimeUnit.SECONDS)
            assertThat(received).isTrue()
            assertThat(receivedMessages).anyMatch {
                it.message == "안녕하세요!" && it.userId == "msg-userA"
            }
        }
    }

    @Nested
    inner class InvalidPayload {

        @Test
        fun `잘못된 JSON을 보내도 연결이 유지된다`() {
            val connectionMaintained = CountDownLatch(1)

            client.execute(wsUri("bad-json-user")) { session ->
                val badJson = session.textMessage("this is not json")
                val validJson = session.textMessage(
                    objectMapper.writeValueAsString(ChatMessage.ping("room-1", "bad-json-user"))
                )

                val send = session.send(
                    Flux.just(badJson, validJson)
                        .delayElements(Duration.ofMillis(100))
                )

                send.then(Mono.delay(Duration.ofMillis(500)))
                    .doOnTerminate { connectionMaintained.countDown() }
                    .then(session.close())
            }.subscribe()

            val result = connectionMaintained.await(3, TimeUnit.SECONDS)
            assertThat(result).isTrue()
        }
    }
}
