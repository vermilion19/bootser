package com.booster.kotlin.chattingservice.config

import com.booster.kotlin.chattingservice.web.ChatWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

@Configuration
class WebSocketConfig {

    @Bean
    fun webSocketHandlerMapping(chatWebSocketHandler: ChatWebSocketHandler): HandlerMapping {
        return SimpleUrlHandlerMapping(
            mapOf("/ws/chat" to chatWebSocketHandler),
            1 // 높은 우선순위 (정적 리소스보다 먼저 매칭)
        )
    }

    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }
}
