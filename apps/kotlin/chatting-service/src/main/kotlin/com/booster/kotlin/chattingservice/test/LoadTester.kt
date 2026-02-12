package com.booster.kotlin.chattingservice.test

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

/**
 * Kotlin Coroutine 기반 WebSocket 부하 테스터
 *
 * 환경변수:
 *   TARGET_URL       - WebSocket 엔드포인트 (기본: ws://localhost:8085/ws/chat)
 *   CONN_COUNT       - 동시 접속 수 (기본: 1000)
 *   CLIENT_ID        - 클라이언트 ID 접두사 (기본: tester-{PID})
 *   ROOM_ID          - 채팅방 ID (기본: load-test-room)
 *   MSG_COUNT        - 유저당 메시지 수 (기본: 10)
 *   MSG_INTERVAL_SEC - 메시지 전송 간격 초 (기본: 5)
 *   BATCH_SIZE       - 동시 연결 배치 크기 (기본: 500)
 *   BATCH_DELAY_MS   - 배치 간 대기 시간 ms (기본: 1000)
 */
fun main() = runBlocking {
    val config = LoadTestConfig.fromEnv()
    val stats = Stats()

    println(config.banner())

    // 배치 단위로 연결 생성 (한번에 10만개 연결 시도 방지)
    val batches = (1..config.connCount).chunked(config.batchSize)

    batches.forEachIndexed { batchIdx, batch ->
        println("[BATCH] ${batchIdx + 1}/${batches.size} 시작 (${batch.size}개 연결)")

        batch.forEach { i ->
            val userId = "${config.clientId}-$i"
            launchConnection(config, userId, stats)
        }

        if (batchIdx < batches.size - 1) {
            delay(config.batchDelayMs)
        }
    }

    println("[INFO] 전체 ${config.connCount}개 연결 시작 완료. 모니터링 중...")

    // 주기적 통계 출력
    while (isActive) {
        delay(10_000)
        println(stats.report())
    }
}

private fun CoroutineScope.launchConnection(
    config: LoadTestConfig,
    userId: String,
    stats: Stats
) {
    val wsUrl = "${config.baseUrl}?userId=$userId"

    HttpClient.create()
        .websocket()
        .uri(wsUrl)
        .handle { inbound, outbound ->
            val connNum = stats.connected.incrementAndGet()
            if (connNum % 500 == 0 || connNum <= 10) {
                println("[CONN] $userId connected (total: $connNum)")
            }

            // 수신 스트림
            val input = inbound.receive()
                .asString()
                .doOnNext { msg ->
                    val received = stats.messagesReceived.increment()
                    if (stats.messagesReceived.sum() % 1000 == 0L) {
                        println("[RECV] $userId: ${msg.truncate(80)}")
                    }
                }
                .doOnTerminate {
                    stats.connected.decrementAndGet()
                    stats.disconnected.increment()
                }
                .then()

            // 송신 스트림: ENTER → 주기적 TALK
            val messages = Flux.concat(
                Mono.just(enterMessage(config.roomId, userId)),
                Flux.interval(Duration.ofSeconds(config.msgIntervalSec))
                    .take(config.msgCount.toLong())
                    .map { seq -> talkMessage(config.roomId, userId, seq + 1) }
            ).doOnNext { stats.messagesSent.increment() }

            val output = outbound.sendString(messages).then()

            Mono.zip(input, output).then()
        }
        .doOnError { e ->
            stats.errors.increment()
            if (stats.errors.sum() % 100 == 0L) {
                System.err.println("[ERROR] $userId: ${e.message}")
            }
        }
        .retryWhen(
            Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .doBeforeRetry { signal ->
                    if (signal.totalRetries() % 10 == 0L) {
                        println("[RETRY] $userId attempt #${signal.totalRetries() + 1}")
                    }
                }
        )
        .subscribe()
}

private fun enterMessage(roomId: String, userId: String): String =
    """{"type":"ENTER","roomId":"$roomId","userId":"$userId","message":"입장합니다"}"""

private fun talkMessage(roomId: String, userId: String, seq: Long): String =
    """{"type":"TALK","roomId":"$roomId","userId":"$userId","message":"Hello #$seq"}"""

private fun String.truncate(maxLen: Int): String {
    val oneLine = replace("\n", " ").replace("\r", "")
    return if (oneLine.length <= maxLen) oneLine else oneLine.substring(0, maxLen) + "..."
}

data class LoadTestConfig(
    val baseUrl: String,
    val connCount: Int,
    val clientId: String,
    val roomId: String,
    val msgCount: Int,
    val msgIntervalSec: Long,
    val batchSize: Int,
    val batchDelayMs: Long
) {
    fun banner(): String = """
        |=== Kotlin LoadTester Configuration ===
        |  Target URL     : $baseUrl
        |  Client ID      : $clientId
        |  Connections     : $connCount
        |  Room ID         : $roomId
        |  Messages/User   : $msgCount
        |  Interval(sec)   : $msgIntervalSec
        |  Batch Size      : $batchSize
        |  Batch Delay(ms) : $batchDelayMs
        |========================================
    """.trimMargin()

    companion object {
        fun fromEnv(): LoadTestConfig {
            val env = System.getenv()
            return LoadTestConfig(
                baseUrl = env.getOrDefault("TARGET_URL", "ws://localhost:8085/ws/chat"),
                connCount = env.getOrDefault("CONN_COUNT", "1000").toInt(),
                clientId = env.getOrDefault("CLIENT_ID", "tester-${ProcessHandle.current().pid()}"),
                roomId = env.getOrDefault("ROOM_ID", "load-test-room"),
                msgCount = env.getOrDefault("MSG_COUNT", "10").toInt(),
                msgIntervalSec = env.getOrDefault("MSG_INTERVAL_SEC", "5").toLong(),
                batchSize = env.getOrDefault("BATCH_SIZE", "500").toInt(),
                batchDelayMs = env.getOrDefault("BATCH_DELAY_MS", "1000").toLong()
            )
        }
    }
}

class Stats {
    val connected = AtomicInteger(0)
    val messagesSent = LongAdder()
    val messagesReceived = LongAdder()
    val errors = LongAdder()
    val disconnected = LongAdder()

    fun report(): String =
        "[STAT] connected=${connected.get()}" +
            ", sent=${messagesSent.sum()}" +
            ", received=${messagesReceived.sum()}" +
            ", errors=${errors.sum()}" +
            ", disconnected=${disconnected.sum()}"
}
