package com.booster.kotlin.chattingservice.web

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
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

        // 1. Input: 클라이언트 → 서버 (수신)
        val input = session.receive()
            .map { it.payloadAsText }
            .doOnNext { payload -> handlePayload(payload, userId) }
            .doOnTerminate {
                log.info("[CLOSE] userId={}", userId)
                chatService.remove(userId)
            }
            .then()

        // 2. Output: 서버 → 클라이언트 (송신)
        val output = chatService.register(userId)
            .map { message ->
                session.textMessage(objectMapper.writeValueAsString(message))
            }

        // 3. Input + Output을 Zip하여 세션 유지 (둘 중 하나 종료 시 세션 종료)
        return Mono.zip(input, session.send(output)).then()
    }

    private fun handlePayload(payload: String, userId: String) {
        try {
            val message = objectMapper.readValue(payload, ChatMessage::class.java)
            // userId를 서버에서 강제 주입 (클라이언트 위변조 방지)
            chatService.handleMessage(message.withUserId(userId))
        } catch (e: Exception) {
            log.warn("[PARSE] 잘못된 JSON: userId={}, payload={}", userId, payload)
            // 파싱 에러에도 연결 유지 (Robustness)
        }
    }

    private fun extractUserId(session: WebSocketSession): String? {
        return UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
            .build()
            .queryParams
            .getFirst("userId")
    }
}
