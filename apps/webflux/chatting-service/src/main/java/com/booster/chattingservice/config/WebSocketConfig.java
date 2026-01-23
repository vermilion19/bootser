package com.booster.chattingservice.config;

import com.booster.chattingservice.handler.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    /**
     * URL Mapping
     * ws://localhost:8080/ws/chat -> ChatWebSocketHandler
     */
    @Bean
    public HandlerMapping handlerMapping(ChatWebSocketHandler chatWebSocketHandler) {
        // 우선순위를 높게 잡아서 정적 리소스 핸들러보다 먼저 동작하게 함
        return new SimpleUrlHandlerMapping(Map.of("/ws/chat", chatWebSocketHandler), 1);
    }

    /**
     * WebFlux에서 WebSocket을 실행하기 위한 어댑터 (필수)
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
