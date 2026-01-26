package com.booster.chattingservicevt.handler;

import com.booster.chattingservicevt.dto.ChatMessage;
import com.booster.chattingservicevt.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    private static final String USER_ID_ATTR = "userId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        if (userId == null) {
            log.warn("Connection rejected: missing userId");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // userId를 세션 속성에 저장
        session.getAttributes().put(USER_ID_ATTR, userId);

        // 서비스에 등록
        chatService.register(userId, session);
        log.info("New connection: {}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);
        if (userId == null) {
            return;
        }

        String payload = message.getPayload();
        try {
            ChatMessage originMessage = objectMapper.readValue(payload, ChatMessage.class);
            ChatMessage validatedMessage = originMessage.withUserId(userId);
            chatService.handleMessage(validatedMessage);
        } catch (Exception e) {
            log.error("Invalid JSON from {}: {}", userId, payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            chatService.remove(userId);
            log.info("Connection closed: {} (status: {})", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);
        log.error("Transport error for {}: {}", userId, exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String getUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("userId");
    }
}
