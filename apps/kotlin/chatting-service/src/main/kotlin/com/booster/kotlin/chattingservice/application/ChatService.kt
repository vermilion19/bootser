package com.booster.kotlin.chattingservice.application

import com.booster.kotlin.chattingservice.domain.ChatMessage
import com.booster.kotlin.chattingservice.domain.ChatMessage.Type
import com.booster.kotlin.chattingservice.infrastructure.SessionRegistryService
import com.booster.kotlin.core.logger
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class ChatService(
    private val redisTemplate: org.springframework.data.redis.core.ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val registry: MeterRegistry,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val sessionRegistry: SessionRegistryService,
    @Value("\${chat.instance-id}") private val instanceId: String,
    @Value("\${chat.graceful-shutdown-delay-ms:3000}") private val gracefulShutdownDelayMs: Long = 3000,
    @Value("\${chat.history.max-size:100}") private val historyMaxSize: Int = 100,
) {
    private val log = logger()

    companion object {
        fun roomChannel(roomId: String) = "chat.room.$roomId"
        fun historyKey(roomId: String) = "chat.history.$roomId"
        fun seqKey(roomId: String) = "chat.seq.$roomId"
    }

    // userId → SharedFlow (메시지 수신 스트림)
    private val localConnections = ConcurrentHashMap<String, MutableSharedFlow<ChatMessage>>()

    // roomId → Set<userId>
    private val rooms = ConcurrentHashMap<String, MutableSet<String>>()

    // roomId → Redis 구독 Disposable (방별 동적 구독 관리)
    private val roomSubscriptions = ConcurrentHashMap<String, Disposable>()

    // Redis 메시지 처리용 CoroutineScope (SupervisorJob: 개별 실패 격리)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val connectionCount = AtomicInteger(0)

    private val messagePublishedCounter = Counter.builder("chat.messages.published")
        .description("Redis로 발행된 메시지 수")
        .register(registry)
    private val messageBroadcastCounter = Counter.builder("chat.messages.broadcast")
        .description("로컬 유저에게 브로드캐스트된 메시지 수")
        .register(registry)

    /**
     * 유저 등록.
     * - 메시지 수신용 SharedFlow 생성
     * - 세션 레지스트리에 userId → instanceId 등록
     */
    fun register(userId: String): Flow<ChatMessage> {
        val flow = MutableSharedFlow<ChatMessage>(
            replay = 0,
            extraBufferCapacity = 64
        )
        localConnections[userId] = flow
        val count = connectionCount.incrementAndGet()
        log.info("[CONNECT] userId={}, instanceId={}, total={}", userId, instanceId, count)

        sessionRegistry.register(userId, instanceId)

        return flow.asSharedFlow()
    }

    /**
     * 클라이언트 메시지 처리.
     * 방 입장/퇴장 반영 후 Redis 발행.
     */
    suspend fun handleMessage(message: ChatMessage) {
        when (message.type) {
            Type.PING -> return
            Type.ENTER -> {
                joinRoom(message.roomId, message.userId)
                if (message.lastSeq > 0) replayMissedMessages(message.roomId, message.userId, message.lastSeq)
            }
            Type.LEAVE -> leaveRoom(message.roomId, message.userId)
            Type.TALK -> {
                if (rooms[message.roomId]?.contains(message.userId) != true) {
                    log.warn("[REJECT] 미입장 방 TALK 차단: userId={}, roomId={}", message.userId, message.roomId)
                    return
                }
            }
        }
        publishToRedis(message)
    }

    /**
     * Redis에서 수신한 메시지를 같은 방의 로컬 유저에게 전달.
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

    /**
     * 유저 연결 해제.
     * - 세션 레지스트리에서 제거
     * - 참여 중인 모든 방에서 퇴장 처리
     * - 방이 비면 Redis 구독 해제
     */
    fun remove(userId: String) {
        localConnections.remove(userId) ?: return
        val count = connectionCount.decrementAndGet()
        log.info("[DISCONNECT] userId={}, total={}", userId, count)

        sessionRegistry.unregister(userId)

        rooms.forEach { (roomId, members) ->
            if (members.remove(userId)) {
                val json = objectMapper.writeValueAsString(ChatMessage.leave(roomId, userId))
                redisTemplate.convertAndSend(roomChannel(roomId), json)
                    .doOnError { e -> log.error("[REDIS] leave publish failed: {}", e.message) }
                    .subscribe()

                if (members.isEmpty()) {
                    rooms.remove(roomId)
                    unsubscribeFromRoom(roomId)
                }
            }
        }
    }

    fun getConnectionCount(): Int = connectionCount.get()
    fun getRoomCount(): Int = rooms.size
    fun getRoomUserCount(roomId: String): Int = rooms[roomId]?.size ?: 0

    @PreDestroy
    fun cleanup() {
        log.info("[SHUTDOWN] 종료 시작, 연결 수: {}개", connectionCount.get())

        // 1. 종료 예고 메시지 전송
        localConnections.forEach { (_, flow) ->
            flow.tryEmit(ChatMessage.system("SYSTEM", "서버가 곧 종료됩니다. 재연결을 준비해주세요."))
        }

        // 2. 클라이언트 재연결 여유 (Spring lifecycle timeout-per-shutdown-phase 범위 내)
        if (gracefulShutdownDelayMs > 0) {
            Thread.sleep(gracefulShutdownDelayMs)
        }

        // 3. 연결 및 방별 Redis 구독 정리
        roomSubscriptions.values.forEach { it.dispose() }
        roomSubscriptions.clear()
        localConnections.clear()
        rooms.clear()
        scope.cancel()
        log.info("[SHUTDOWN] 완료")
    }

    // --- 내부 메서드 ---

    private fun joinRoom(roomId: String, userId: String) {
        rooms.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(userId)
        subscribeToRoomIfNeeded(roomId)
        log.debug("[JOIN] roomId={}, userId={}, roomSize={}", roomId, userId, rooms[roomId]?.size)
    }

    private fun leaveRoom(roomId: String, userId: String) {
        rooms[roomId]?.let { members ->
            members.remove(userId)
            if (members.isEmpty()) {
                rooms.remove(roomId)
                unsubscribeFromRoom(roomId)
            }
        }
        log.debug("[LEAVE] roomId={}, userId={}", roomId, userId)
    }

    private suspend fun publishToRedis(message: ChatMessage) {
        try {
            val messageToPublish = if (message.type == Type.TALK) {
                // TALK 메시지: seq 부여 후 방별 히스토리에 저장
                val seq = redisTemplate.opsForValue()
                    .increment(seqKey(message.roomId))
                    .awaitSingleOrNull()
                    ?: throw IllegalStateException("Redis seq increment returned null: roomId=${message.roomId}")
                val withSeq = message.copy(seq = seq)
                val json = objectMapper.writeValueAsString(withSeq)
                redisTemplate.opsForList()
                    .rightPush(historyKey(message.roomId), json)
                    .awaitSingleOrNull()
                redisTemplate.opsForList()
                    .trim(historyKey(message.roomId), -historyMaxSize.toLong(), -1)
                    .awaitSingleOrNull()
                withSeq
            } else {
                message
            }
            val json = objectMapper.writeValueAsString(messageToPublish)
            redisTemplate.convertAndSend(roomChannel(messageToPublish.roomId), json).awaitSingleOrNull()
            messagePublishedCounter.increment()
        } catch (e: Exception) {
            log.error("[REDIS] publish failed: {}", e.message)
        }
    }

    /**
     * 재연결 복구: 히스토리에서 lastSeq 이후 메시지를 유저 Flow로 즉시 전송.
     */
    private suspend fun replayMissedMessages(roomId: String, userId: String, lastSeq: Long) {
        val flow = localConnections[userId] ?: return
        val jsonList = redisTemplate.opsForList()
            .range(historyKey(roomId), 0, -1)
            .collectList()
            .awaitSingleOrNull()
            ?: return

        var replayed = 0
        jsonList
            .mapNotNull { json ->
                runCatching { objectMapper.readValue(json, ChatMessage::class.java) }.getOrNull()
            }
            .filter { it.seq > lastSeq }
            .forEach { msg ->
                flow.tryEmit(msg)
                replayed++
            }
        log.info("[REPLAY] roomId={}, userId={}, lastSeq={}, replayed={}개", roomId, userId, lastSeq, replayed)
    }

    /**
     * 방의 첫 번째 유저가 입장할 때 해당 방의 Redis 채널을 구독.
     * computeIfAbsent로 중복 구독 방지 (원자적 처리).
     */
    private fun subscribeToRoomIfNeeded(roomId: String) {
        roomSubscriptions.computeIfAbsent(roomId) {
            log.debug("[SUB] 방 구독 시작: roomId={}", roomId)
            listenerContainer.receive(ChannelTopic.of(roomChannel(roomId)))
                .map { it.message }
                .subscribe { json ->
                    scope.launch {
                        try {
                            val message = objectMapper.readValue(json, ChatMessage::class.java)
                            broadcastToLocalUsers(message)
                        } catch (e: Exception) {
                            log.error("[REDIS] 역직렬화 실패: roomId={}, {}", roomId, e.message)
                        }
                    }
                }
        }
    }

    /**
     * 방의 마지막 유저가 퇴장할 때 Redis 채널 구독 해제 및 키 정리.
     * - 히스토리 키(chat.history.{roomId}): 재연결 복구용 메시지 이력
     * - 시퀀스 키(chat.seq.{roomId}): 메시지 순번 카운터
     * 비활성 방 데이터가 Redis에 영구 누적되는 메모리 누수 방지.
     */
    private fun unsubscribeFromRoom(roomId: String) {
        roomSubscriptions.remove(roomId)?.dispose()
        redisTemplate.delete(historyKey(roomId), seqKey(roomId))
            .doOnError { e -> log.error("[REDIS] 방 키 삭제 실패: roomId={}, {}", roomId, e.message) }
            .subscribe()
        log.debug("[UNSUB] 방 구독 해제 및 키 삭제: roomId={}", roomId)
    }
}
