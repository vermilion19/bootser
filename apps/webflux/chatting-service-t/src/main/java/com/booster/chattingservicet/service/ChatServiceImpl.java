package com.booster.chattingservicet.service;

import com.booster.chattingservicet.dto.ChatMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // 로컬에 연결된 사용자들의 WebSocket 세션 (Key: userId, Value: Session)
    private final Map<String, WebSocketSession> localConnections = new ConcurrentHashMap<>();

    private static final String REDIS_TOPIC = "chat.public";

    @Override
    public void register(String userId, WebSocketSession session) {
        localConnections.put(userId, session);
        log.info("Registered user: {} (Total: {})", userId, localConnections.size());
    }

    @Override
    public void handleMessage(ChatMessage message) {
        if (message.type() == ChatMessage.Type.PING) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            // Blocking Redis publish (전통적인 플랫폼 스레드에서 실행)
            redisTemplate.convertAndSend(REDIS_TOPIC, json);
        } catch (Exception e) {
            log.error("Failed to publish message to Redis", e);
        }
    }

    @Override
    public void remove(String userId) {
        localConnections.remove(userId);
        log.debug("Removed user: {}", userId);
    }

    @Override
    public void broadcastToLocalUsers(ChatMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize message", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);

        // 모든 로컬 사용자에게 브로드캐스트
        // 전통적인 플랫폼 스레드에서 동기 I/O 처리
        localConnections.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    // synchronized: WebSocketSession은 thread-safe하지 않음
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send message to {}: {}", userId, e.getMessage());
                    // 실패한 세션은 제거
                    localConnections.remove(userId);
                }
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        log.info("Server is shutting down. Closing {} connections...", localConnections.size());

        ChatMessage byeMessage = new ChatMessage(
                ChatMessage.Type.TALK,
                "SYSTEM",
                "SYSTEM",
                "서버 종료로 인해 연결이 끊어집니다."
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(byeMessage);
        } catch (Exception e) {
            log.error("Failed to serialize bye message", e);
            json = "{\"type\":\"TALK\",\"message\":\"Server shutting down\"}";
        }

        TextMessage textMessage = new TextMessage(json);

        localConnections.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                        session.close();
                    }
                } catch (IOException e) {
                    log.debug("Error closing session for {}", userId);
                }
            }
        });

        localConnections.clear();
        log.info("All connections closed.");
    }
}
