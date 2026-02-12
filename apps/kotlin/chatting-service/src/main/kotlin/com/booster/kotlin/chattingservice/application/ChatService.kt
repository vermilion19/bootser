package com.booster.kotlin.chattingservice.application

import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.chattingservice.domain.ChatMessage.Type
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class ChatService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val registry: MeterRegistry
) {
    private val log = logger()

    companion object {
        const val REDIS_TOPIC = "chat.public"
    }

    // userId → SharedFlow (Kotlin 네이티브 비동기 스트림)
    private val localConnections = ConcurrentHashMap<String, MutableSharedFlow<ChatMessage>>()

    // roomId → Set<userId>
    private val rooms = ConcurrentHashMap<String, MutableSet<String>>()

    private val connectionCount = AtomicInteger(0)

    // Micrometer 카운터
    private val messagePublishedCounter = Counter.builder("chat.messages.published")
        .description("Redis로 발행된 메시지 수")
        .register(registry)
    private val messageBroadcastCounter = Counter.builder("chat.messages.broadcast")
        .description("로컬 유저에게 브로드캐스트된 메시지 수")
        .register(registry)

    /**
     * 유저 등록 후 메시지 수신용 Flow 반환
     * SharedFlow: 여러 collector가 가능하며, replay=64로 일시적 유실 방지
     */
    fun register(userId: String): Flow<ChatMessage> {
        val flow = MutableSharedFlow<ChatMessage>(
            replay = 0,
            extraBufferCapacity = 64 // 클라이언트가 느려도 64개까지 버퍼링
        )
        localConnections[userId] = flow
        val count = connectionCount.incrementAndGet()
        log.info("[CONNECT] userId={}, total={}", userId, count)
        return flow.asSharedFlow()
    }

    /**
     * 클라이언트로부터 수신한 메시지 처리
     * suspend fun으로 Redis 발행을 non-blocking 대기
     */
    suspend fun handleMessage(message: ChatMessage) {
        when (message.type) {
            Type.PING -> return
            Type.ENTER -> joinRoom(message.roomId, message.userId)
            Type.LEAVE -> leaveRoom(message.roomId, message.userId)
            else -> {}
        }
        publishToRedis(message)
    }

    /**
     * Redis에서 수신한 메시지를 같은 방 로컬 유저에게 전달
     * suspend fun으로 SharedFlow.emit() 호출
     */
    suspend fun broadcastToLocalUsers(message: ChatMessage) {
        val userIds = rooms[message.roomId] ?: return

        userIds.forEach { userId ->
            localConnections[userId]?.let { flow ->
                val emitted = flow.tryEmit(message)
                if (emitted) {
                    messageBroadcastCounter.increment()
                } else {
                    log.warn("[BROADCAST] buffer full, drop: userId={}", userId)
                }
            }
        }
    }

    fun remove(userId: String) {
        localConnections.remove(userId) ?: return
        val count = connectionCount.decrementAndGet()
        log.info("[DISCONNECT] userId={}, total={}", userId, count)

        rooms.forEach { (roomId, members) ->
            if (members.remove(userId)) {
                // LEAVE 이벤트는 fire-and-forget (remove는 non-suspend)
                val json = objectMapper.writeValueAsString(ChatMessage.leave(roomId, userId))
                redisTemplate.convertAndSend(REDIS_TOPIC, json)
                    .doOnError { e -> log.error("[REDIS] leave publish failed: {}", e.message) }
                    .subscribe()

                if (members.isEmpty()) {
                    rooms.remove(roomId)
                }
            }
        }
    }

    fun getConnectionCount(): Int = connectionCount.get()

    fun getRoomCount(): Int = rooms.size

    fun getRoomUserCount(roomId: String): Int = rooms[roomId]?.size ?: 0

    @PreDestroy
    fun cleanup() {
        log.info("[SHUTDOWN] 서버 종료, 전체 연결 정리: {}개", connectionCount.get())

        localConnections.forEach { (_, flow) ->
            flow.tryEmit(ChatMessage.system("SYSTEM", "서버가 종료됩니다."))
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

    private suspend fun publishToRedis(message: ChatMessage) {
        try {
            val json = objectMapper.writeValueAsString(message)
            redisTemplate.convertAndSend(REDIS_TOPIC, json).awaitSingleOrNull()
            messagePublishedCounter.increment()
        } catch (e: Exception) {
            log.error("[REDIS] publish failed: {}", e.message)
        }
    }
}
