package com.booster.kotlin.chattingservice.application

import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.chattingservice.domain.ChatMessage.Type
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class ChatService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = logger()

    companion object {
        const val REDIS_TOPIC = "chat.public"
    }

    // userId → Sink (로컬 인스턴스의 WebSocket 커넥션)
    private val localConnections = ConcurrentHashMap<String, Sinks.Many<ChatMessage>>()

    // roomId → Set<userId> (채팅방별 유저 목록)
    private val rooms = ConcurrentHashMap<String, MutableSet<String>>()

    private val connectionCount = AtomicInteger(0)

    fun register(userId: String): Flux<ChatMessage> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<ChatMessage>()
        localConnections[userId] = sink
        val count = connectionCount.incrementAndGet()
        log.info("[CONNECT] userId={}, total={}", userId, count)

        return sink.asFlux()
            .doOnCancel { remove(userId) }
            .doOnTerminate { remove(userId) }
    }

    fun handleMessage(message: ChatMessage) {
        when (message.type) {
            Type.PING -> return
            Type.ENTER -> joinRoom(message.roomId, message.userId)
            Type.LEAVE -> leaveRoom(message.roomId, message.userId)
            else -> {}
        }
        publishToRedis(message)
    }

    fun broadcastToLocalUsers(message: ChatMessage) {
        val roomId = message.roomId
        val userIds = rooms[roomId] ?: return

        userIds.forEach { userId ->
            localConnections[userId]?.let { sink ->
                val result = sink.tryEmitNext(message)
                if (result.isFailure) {
                    log.warn("[BROADCAST] emit failed: userId={}, result={}", userId, result)
                }
            }
        }
    }

    fun remove(userId: String) {
        val sink = localConnections.remove(userId) ?: return
        val count = connectionCount.decrementAndGet()
        log.info("[DISCONNECT] userId={}, total={}", userId, count)

        // 모든 방에서 제거 + LEAVE 이벤트 발행
        rooms.forEach { (roomId, members) ->
            if (members.remove(userId)) {
                publishToRedis(ChatMessage.leave(roomId, userId))
                if (members.isEmpty()) {
                    rooms.remove(roomId)
                }
            }
        }

        sink.tryEmitComplete()
    }

    fun getConnectionCount(): Int = connectionCount.get()

    @PreDestroy
    fun cleanup() {
        log.info("[SHUTDOWN] 서버 종료, 전체 연결 정리: {}개", connectionCount.get())

        localConnections.forEach { (userId, sink) ->
            rooms.forEach { (roomId, _) ->
                val bye = ChatMessage.system(roomId, "서버가 종료됩니다.")
                sink.tryEmitNext(bye)
            }
            sink.tryEmitComplete()
        }
        localConnections.clear()
        rooms.clear()
    }

    private fun joinRoom(roomId: String, userId: String) {
        rooms.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(userId)
        log.debug("[JOIN] roomId={}, userId={}, roomSize={}", roomId, userId, rooms[roomId]?.size)
    }

    private fun leaveRoom(roomId: String, userId: String) {
        rooms[roomId]?.let { members ->
            members.remove(userId)
            if (members.isEmpty()) {
                rooms.remove(roomId)
            }
        }
        log.debug("[LEAVE] roomId={}, userId={}", roomId, userId)
    }

    private fun publishToRedis(message: ChatMessage) {
        val json = objectMapper.writeValueAsString(message)
        redisTemplate.convertAndSend(REDIS_TOPIC, json)
            .doOnError { e -> log.error("[REDIS] publish failed: {}", e.message) }
            .subscribe()
    }
}
