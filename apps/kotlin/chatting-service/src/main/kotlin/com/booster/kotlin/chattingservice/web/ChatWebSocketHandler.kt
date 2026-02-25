package com.booster.kotlin.chattingservice.web

import com.booster.kotlin.chattingservice.application.ChatService
import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

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
            val outputFlux = chatService.register(userId)
                .map { message -> session.textMessage(objectMapper.writeValueAsString(message)) }
                .asFlux()

            // 2. Heartbeat: 30초마다 WebSocket 프로토콜 레벨 Ping 프레임 전송
            //    브라우저가 자동으로 Pong 응답 → 연결 유지
            //    클라이언트가 사라지면 TCP 재전송 실패 → 세션 종료 → remove() 호출
            val pingFlux: Flux<WebSocketMessage> = Flux.interval(Duration.ofSeconds(30))
                .map { session.pingMessage { it.wrap(ByteArray(0)) } }

            // 3. Input: TEXT 프레임만 처리 (Pong 등 프로토콜 프레임 무시)
            //    flatMap + Dispatchers.Default: Netty 이벤트 루프 블로킹 없이 처리
            //    (기존 runBlocking 제거 → Netty 스레드 고갈 방지)
            val input = session.receive()
                .filter { it.type == WebSocketMessage.Type.TEXT }
                .map { it.payloadAsText }
                .flatMap { payload ->
                    mono(Dispatchers.Default) {
                        try {
                            val message = objectMapper.readValue(payload, ChatMessage::class.java)
                            chatService.handleMessage(message.withUserId(userId))
                        } catch (e: Exception) {
                            log.warn("[PARSE] 잘못된 JSON: userId={}", userId)
                        }
                    }
                }
                .doOnTerminate {
                    log.info("[CLOSE] userId={}", userId)
                    chatService.remove(userId)
                }
                .then()

            // 4. Input + Output(메시지 + Ping) Zip으로 세션 유지
            Mono.zip(input, session.send(outputFlux.mergeWith(pingFlux))).then().awaitSingleOrNull()
        }.then()
    }

    private fun extractUserId(session: WebSocketSession): String? {
        // TODO: [Phase 1 미구현] JWT 인증으로 교체 필요
        //   현재: ?userId=xxx Query Param을 그대로 신뢰 (포트폴리오 환경)
        //   목표: ?token=<JWT> → JwtProvider.extractUserId(token) → sub 클레임에서 userId 추출
        return UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
            .build()
            .queryParams
            .getFirst("userId")
    }
}
