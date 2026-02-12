package com.booster.kotlin.chattingservice.web

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@Component
class ChatWebSocketHandler(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper
) : WebSocketHandler {

    private val log = logger()

    override fun handle(session: WebSocketSession): Mono<Void> {
        val userId = extractUserId(session)
            ?: return session.close().also { log.warn("[REJECT] userId 없이 접속 시도") }

        log.info("[CONNECT] userId={}", userId)

        // Coroutine 컨텍스트에서 WebSocket 세션 처리
        return mono {
            // 1. Output: Kotlin Flow → Reactor Flux 변환하여 전송
            val outputFlow = chatService.register(userId)
                .map { message ->
                    session.textMessage(objectMapper.writeValueAsString(message))
                }
            val outputFlux = outputFlow.asFlux()

            // 2. Input: 클라이언트 수신 메시지를 coroutine으로 처리
            val input = session.receive()
                .map { it.payloadAsText }
                .doOnNext { payload -> handlePayload(payload, userId) }
                .doOnTerminate {
                    log.info("[CLOSE] userId={}", userId)
                    chatService.remove(userId)
                }
                .then()

            // 3. Input + Output Zip으로 세션 유지
            Mono.zip(input, session.send(outputFlux)).then().awaitSingleOrNull()
        }.then()
    }

    private fun handlePayload(payload: String, userId: String) {
        try {
            val message = objectMapper.readValue(payload, ChatMessage::class.java)
            // userId를 서버에서 강제 주입 (클라이언트 위변조 방지)
            // handleMessage가 suspend fun이지만, doOnNext 내에서는 blocking 호출
            // Redis 발행의 suspend는 내부에서 fire-and-forget으로 처리
            kotlinx.coroutines.runBlocking {
                chatService.handleMessage(message.withUserId(userId))
            }
        } catch (e: Exception) {
            log.warn("[PARSE] 잘못된 JSON: userId={}, payload={}", userId, payload)
        }
    }

    private fun extractUserId(session: WebSocketSession): String? {
        return UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
            .build()
            .queryParams
            .getFirst("userId")
    }
}
