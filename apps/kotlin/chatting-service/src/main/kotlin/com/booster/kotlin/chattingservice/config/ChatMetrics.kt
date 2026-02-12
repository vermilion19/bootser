package com.booster.kotlin.chattingservice.config

import com.booster.kotlin.chattingservice.application.ChatService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration

/**
 * 채팅 서비스 커스텀 메트릭 등록
 *
 * Prometheus 엔드포인트 (/actuator/prometheus)에서 조회 가능:
 *   - chat_connections_active: 현재 WebSocket 접속자 수
 *   - chat_rooms_active: 현재 활성 채팅방 수
 *   - chat_messages_published_total: Redis로 발행된 메시지 수 (ChatService에서 Counter 등록)
 *   - chat_messages_broadcast_total: 로컬 유저에게 전달된 메시지 수 (ChatService에서 Counter 등록)
 */
@Configuration
class ChatMetrics(
    registry: MeterRegistry,
    chatService: ChatService
) {
    init {
        // Gauge: 현재 WebSocket 접속자 수 (실시간 변동값)
        registry.gauge("chat.connections.active", chatService) {
            it.getConnectionCount().toDouble()
        }

        // Gauge: 현재 활성 채팅방 수
        registry.gauge("chat.rooms.active", chatService) {
            it.getRoomCount().toDouble()
        }
    }
}
