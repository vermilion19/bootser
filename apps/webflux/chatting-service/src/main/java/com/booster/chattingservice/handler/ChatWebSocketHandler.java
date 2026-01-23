package com.booster.chattingservice.handler;

import com.booster.chattingservice.dto.ChatMessage;
import com.booster.chattingservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 1. Query Parameter에서 userId 추출 (예: ws://localhost:8080/ws/chat?userId=user1)
        String userId = getUserId(session);
        if (userId == null) {
            return session.close(); // ID 없으면 컷
        }

        log.info("New connection: {}", userId);

        // 2. [Input] 클라이언트 -> 서버 (Receive)
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> {
                    try {
                        // JSON 파싱 (Zero-copy는 아니지만 최대한 가볍게)
                        ChatMessage originMessage = objectMapper.readValue(payload, ChatMessage.class);
                        ChatMessage validatedMessage = originMessage.withUserId(userId);
                        return chatService.handleMessage(validatedMessage);
                    } catch (Exception e) {
                        log.error("Invalid JSON from {}: {}", userId, payload);
                        return Mono.empty(); // 에러 나도 연결은 끊지 않음 (Robustness)
                    }
                })
                .doOnTerminate(() -> {
                    // 연결 끊기면 자원 정리
                    log.info("Connection closed: {}", userId);
                    chatService.remove(userId);
                })
                .then();

        // 3. [Output] 서버 -> 클라이언트 (Send)
        // ChatService에서 나오는 Flux<ChatMessage>를 WebSocketMessage로 변환해서 전송
        Flux<WebSocketMessage> outputFlux = chatService.register(userId)
                .map(message -> {
                    try {
                        String json = objectMapper.writeValueAsString(message);
                        return session.textMessage(json);
                    } catch (Exception e) {
                        log.error("JSON Error", e);
                        return session.textMessage("Error"); // 혹은 빈 메시지
                    }
                });

        // 4. 입력과 출력을 Zip해서 세션 유지 (둘 중 하나라도 끝나면 종료됨)
        return Mono.zip(input, session.send(outputFlux)).then();
    }

    // URL에서 userId 파싱하는 헬퍼 메소드
    private String getUserId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("userId");
    }
}
