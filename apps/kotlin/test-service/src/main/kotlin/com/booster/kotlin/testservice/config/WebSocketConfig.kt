package com.booster.kotlin.testservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-chat") // ws://localhost:8080/ws-chat
            .setAllowedOriginPatterns("*") // CORS 허용 (모든 도메인)
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 클라이언트가 메시지를 보낼 때 붙여야 하는 prefix
        registry.setApplicationDestinationPrefixes("/app")
        // 클라이언트가 메시지를 구독(수신)할 때 쓰는 prefix
        registry.enableSimpleBroker("/topic")
    }
}