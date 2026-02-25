# Chatting Service 개선 계획

> **목표**: 단일 인스턴스 100k 연결 유지 + 수평 확장 용이한 구조
>
> 기준 문서: `docs/learning/websocket-deep-dive.md`

---

## 현재 구조 평가

### 잘 된 것들

| 항목 | 평가 |
|------|------|
| Reactor Netty + Coroutine 혼합 | 올바른 방향. 이벤트 루프 기반 I/O + 코루틴 비동기 |
| SharedFlow `extraBufferCapacity=64` | 버스트 트래픽 대응 버퍼 |
| Redis Pub/Sub 멀티 서버 구조 | 서버 간 메시지 전달 기반 존재 |
| SupervisorJob | Redis 리스너 개별 실패가 전체에 전파되지 않음 |
| Micrometer 메트릭 | 연결 수, 방 수, 메시지 카운터 |
| LoadTester 내장 | 부하 테스트 도구 |

### 문제점 요약

```
Critical  (100k 목표 달성에 직접 영향)
  ├── P0: runBlocking이 Netty 이벤트 루프 블로킹
  └── P0: Heartbeat/좀비 연결 감지 없음

Important (Scale-out 설계에 영향)
  ├── P1: 단일 Redis 채널로 모든 트래픽 집중
  ├── P1: 세션 레지스트리 없음
  ├── P1: 인증 없음 (userId를 Query Param으로 신뢰)
  └── P1: Graceful Shutdown 미흡

Nice-to-have (Production 완성도)
  ├── P2: 메시지 시퀀스 번호 + 재연결 복구
  └── P2: Rate Limiting per connection
```

---

## 문제점 상세

### [P0] runBlocking이 Netty 이벤트 루프를 블로킹

**위치**: `ChatWebSocketHandler.kt:61`

```kotlin
// 현재 코드
.doOnNext { payload -> handlePayload(payload, userId) }

private fun handlePayload(payload: String, userId: String) {
    kotlinx.coroutines.runBlocking {   // ← 문제
        chatService.handleMessage(message.withUserId(userId))
    }
}
```

**문제 원인:**

`doOnNext`는 Reactor 연산자로 Netty의 이벤트 루프 스레드에서 실행된다. `runBlocking`은 해당 스레드를 블로킹한다. Netty 이벤트 루프 스레드는 기본적으로 `CPU 코어 × 2`개밖에 없으므로, 메시지 처리 중 스레드가 고갈되면 신규 메시지 수신 자체가 멈춘다.

```
Netty 이벤트 루프 스레드 (기본 16개 @ 8-core)
│
├── Thread 1: [runBlocking 대기 중...] ← 블로킹
├── Thread 2: [runBlocking 대기 중...] ← 블로킹
├── ...
└── Thread 16: [runBlocking 대기 중...] ← 블로킹

→ 신규 연결, 신규 메시지 처리 불가 → 100k는커녕 수천 연결에서도 문제
```

**수정 방향:**

`doOnNext` 대신 `flatMap + mono(Dispatchers.Default)`로 별도 스레드 풀에 위임한다.

```kotlin
// 수정 방향
val input = session.receive()
    .map { it.payloadAsText }
    .flatMap { payload ->
        mono(Dispatchers.Default) {    // Coroutine Default Dispatcher (CPU-bound pool)
            val message = objectMapper.readValue(payload, ChatMessage::class.java)
            chatService.handleMessage(message.withUserId(userId))
        }.onErrorResume { e ->
            log.warn("[PARSE] 잘못된 메시지: userId={}", userId)
            Mono.empty()
        }
    }
    .doOnTerminate {
        log.info("[CLOSE] userId={}", userId)
        chatService.remove(userId)
    }
    .then()
```

---

### [P0] Heartbeat / 좀비 연결 감지 없음

**현재**: PING 타입 메시지를 클라이언트에서 보내면 서버가 무시하는 구조. 서버 → 클라이언트 방향 Ping 없음.

**문제 원인:**

모바일 환경에서 앱이 Background로 전환되거나 NAT 타임아웃이 발생하면, TCP FIN 없이 연결이 끊긴다. 서버는 이를 감지하지 못하고 `localConnections`에 좀비 연결이 쌓인다.

```
100k 연결 목표에서 좀비 연결 비율 10%만 되어도 10,000개의 메모리 누수
SharedFlow 버퍼(extraBufferCapacity=64)가 좀비 연결에서 계속 낭비됨
```

**수정 방향:**

서버 → 클라이언트 방향으로 주기적 Ping을 전송하고, Pong이 일정 시간 내 오지 않으면 연결을 정리한다.

```kotlin
// 수정 방향 (ChatWebSocketHandler.handle 내부)
val pingFlow = flow {
    while (true) {
        delay(30_000)           // 30초마다 ping
        emit(ChatMessage.ping())
    }
}.map { session.textMessage(objectMapper.writeValueAsString(it)) }

// outputFlow에 ping merge
val mergedOutput = merge(outputFlow, pingFlow)
session.send(mergedOutput.asFlux())

// Netty WebSocket 레벨 ping 사용도 가능 (WebSocketConfig에서 설정)
// → 이쪽이 더 권장. 프레임 레벨 ping/pong 처리
```

또는 Reactor Netty 레벨에서 설정:

```kotlin
// WebSocketConfig.kt
@Bean
fun webSocketHandlerMapping(): HandlerMapping {
    // ReactorNettyRequestUpgradeStrategy에서 maxFramePayloadLength, idleTimeout 설정
}
```

---

### [P1] 단일 Redis 채널로 모든 트래픽 집중

**위치**: `ChatService.kt:28`

```kotlin
const val REDIS_TOPIC = "chat.public"  // 모든 방, 모든 서버가 구독
```

**문제 원인:**

서버 N대, 채팅방 K개가 있을 때:
- 방 1의 메시지 → "chat.public" → **서버 1, 2, ..., N 모두 수신**
- 서버 i에 방 1 유저가 없어도 메시지를 수신 → 역직렬화 → 방 확인 → 버림

```
서버 10대, 채팅방 1000개:
메시지 1개 발행 → Redis에서 서버 10대에 전달
→ 9대의 서버가 불필요한 처리 (90% 낭비)
메시지 처리량이 늘수록 Redis 리스너 부하 폭증
```

**수정 방향:**

옵션 A (방 기반 동적 구독 — 복잡하지만 효율적):
```
유저가 방 입장 시: 해당 서버가 "chat.room.{roomId}" 채널 구독
유저가 모두 퇴장 시: 해당 서버가 채널 구독 해제
```

옵션 B (서버 ID 기반 라우팅 — 세션 레지스트리와 함께):
```
각 서버가 "chat.server.{instanceId}" 채널 구독
메시지 발행 시: userId → Redis로 instanceId 조회 → 해당 채널에 발행
→ 해당 서버만 메시지 수신
```

옵션 C (현재 구조 유지 + 방 필터 최적화):
```
메시지에 서버 정보 추가
브로드캐스트 전 room 멤버십 체크를 더 빠르게
```

> **권장**: 현재 단계에서는 옵션 B (세션 레지스트리와 함께 구현)

---

### [P1] 세션 레지스트리 없음

**현재**: 서버가 자신이 가진 `localConnections`만 알고 있음. 다른 서버의 연결 정보 없음.

**문제 원인:**

특정 유저에게 직접 메시지를 보내야 할 때 (귓속말, 시스템 알림, 강퇴) 라우팅 불가.
또한 서버 장애 시 어떤 유저가 어느 서버에 있는지 파악이 안 되어 모니터링/운영이 어려움.

**필요한 구조:**

```
Redis Hash: session:registry
  userId_A → instanceId_1
  userId_B → instanceId_2
  userId_C → instanceId_1

연결 시: HSET session:registry {userId} {instanceId} EX 3600
해제 시: HDEL session:registry {userId}

메시지 라우팅:
  HGET session:registry {targetUserId}
  → instanceId 확인
  → PUBLISH chat.server.{instanceId} {message}
```

---

### [P1] 인증 없음

**위치**: `ChatWebSocketHandler.kt:70`

```kotlin
return UriComponentsBuilder.fromUri(session.handshakeInfo.uri)
    .build()
    .queryParams
    .getFirst("userId")  // Query Param을 그대로 신뢰
```

**문제 원인:**

누구든 `?userId=admin` 또는 `?userId=otherUser`로 접속해 다른 사람인 척할 수 있음. 현재는 서버에서 `withUserId(userId)`로 덮어쓰지만, 근본적으로 userId 자체의 유효성 검증이 없음.

**수정 방향:**

Handshake 단계에서 Authorization 헤더의 JWT를 검증하고, JWT에서 userId를 추출한다.

```kotlin
// 수정 방향
private fun extractUserId(session: WebSocketSession): String? {
    val token = session.handshakeInfo.headers.getFirst("Authorization")
        ?.removePrefix("Bearer ")
        ?: return null

    return jwtProvider.extractUserId(token)   // 검증 + userId 추출
}
```

---

### [P1] Graceful Shutdown 미흡

**위치**: `ChatService.kt:122`

```kotlin
@PreDestroy
fun cleanup() {
    localConnections.forEach { (_, flow) ->
        flow.tryEmit(ChatMessage.system("SYSTEM", "서버가 종료됩니다."))
    }
    localConnections.clear()   // emit 직후 clear → 메시지 전달 보장 안 됨
    rooms.clear()
}
```

**문제 원인:**

`tryEmit`은 비동기 emit이다. 메시지가 클라이언트에 실제로 전달되기 전에 `clear()`로 연결이 제거된다. 클라이언트가 종료 메시지를 받지 못한 채 연결이 끊기면, Exponential Backoff 없이 즉시 재연결을 시도한다. 서버 N대 Rolling Update 중 각 서버의 수천~수만 연결이 동시에 다른 서버로 몰리면 **Thundering Herd**가 발생한다.

**수정 방향:**

```
1. 신규 연결 수락 중지 (서버를 Maintenance 상태로)
2. 기존 연결에 "X초 후 종료" 메시지 전송
3. Grace Period 대기 (클라이언트가 다른 서버로 재연결 완료할 시간)
4. 남은 연결 정리
```

```yaml
# application.yml (현재 설정되어 있음)
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 현재 10s → 30s로 증가 권장
```

---

### [P2] 메시지 시퀀스 번호 + 재연결 복구

**현재**: 연결이 끊기면 그 사이 메시지를 복구할 방법 없음.

**필요한 구조:**

```
각 방의 메시지에 sequence number 부여
Redis List에 최근 N개 메시지 보관 (RPUSH + LTRIM)

재연결 시:
  클라이언트: "lastSeq=1234로 재연결"
  서버: seq > 1234인 메시지를 Redis에서 조회 후 전송
```

구현 복잡도가 높아 Phase 2로 분류.

---

### [P2] Rate Limiting per connection

**현재**: 연결당 메시지 빈도 제한 없음.

```
악성 클라이언트가 초당 수천 개의 메시지를 전송하면:
→ Redis Pub/Sub 과부하
→ 같은 방의 다른 유저 SharedFlow 버퍼 소진
→ 서버 전체 성능 저하
```

**수정 방향:**

```kotlin
// RateLimiter: 연결당 초당 N개 제한
// Guava RateLimiter 또는 Bucket4j 사용
val rateLimiters = ConcurrentHashMap<String, RateLimiter>()
```

---

## 구현 우선순위

| 단계 | 항목 | 파일 | 난이도 | 상태 |
|------|------|------|--------|------|
| **Phase 1** | `runBlocking` → `flatMap+mono` 교체 | `ChatWebSocketHandler.kt` | 낮음 | ✅ 완료 |
| **Phase 1** | 서버 → 클라이언트 Ping 구현 | `ChatWebSocketHandler.kt` | 낮음 | ✅ 완료 |
| **Phase 1** | JWT 인증 (Handshake 검증) | `ChatWebSocketHandler.kt` + 신규 `JwtProvider.kt` | 낮음 | ⏸ TODO — 포트폴리오 환경 생략. 코드 내 TODO 주석 참고 |
| **Phase 2** | 세션 레지스트리 (Redis: userId→instanceId) | `ChatService.kt` + `RedisConfig.kt` | 중간 | 미구현 |
| **Phase 2** | 서버 ID 기반 Redis 채널 라우팅 | `ChatService.kt` + `RedisConfig.kt` | 중간 | 미구현 |
| **Phase 2** | Graceful Shutdown 개선 (Grace Period) | `ChatService.kt` + `application.yml` | 중간 | 미구현 |
| **Phase 3** | Rate Limiting per connection | `ChatWebSocketHandler.kt` | 중간 | 미구현 |
| **Phase 3** | 메시지 시퀀스 + 재연결 복구 | `ChatService.kt` + Redis | 높음 | 미구현 |

---

## 100k 연결 달성을 위한 체크리스트

### 코드 레벨

- [ ] `runBlocking` 제거 (Netty 스레드 블로킹 해소)
- [ ] Heartbeat 구현 (좀비 연결 정리)
- [ ] SharedFlow overflow 전략 명시 (`onBufferOverflow = DROP_OLDEST`)

### JVM 레벨

```bash
# 권장 JVM 옵션
java -Xms2g -Xmx4g \
     -XX:+UseZGC \                         # ZGC: 연결 객체는 장수 → GC 일시정지 최소화
     -Dreactor.netty.ioWorkerCount=16 \    # Netty I/O 스레드 수 (CPU 코어 * 2)
     -jar chatting-service.jar
```

### OS 레벨 (Linux)

```bash
# 파일 디스크립터 제한 (연결 1개 = FD 1개)
ulimit -n 200000

# 또는 /etc/security/limits.conf
* soft nofile 200000
* hard nofile 200000

# TCP 튜닝
sysctl -w net.core.somaxconn=65535          # 연결 백로그 큐 크기
sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sysctl -w net.ipv4.tcp_tw_reuse=1           # TIME_WAIT 소켓 재사용
sysctl -w net.ipv4.tcp_keepalive_time=60    # keepalive 시작 시간 (초)
sysctl -w net.ipv4.tcp_keepalive_intvl=10   # keepalive 재시도 간격
sysctl -w net.ipv4.tcp_keepalive_probes=6   # keepalive 재시도 횟수
```

### 로드밸런서 레벨

```
WebSocket은 Stateful → Sticky Session 필요
Nginx: ip_hash 또는 sticky cookie
AWS ALB: 고정 세션 활성화

Connection Draining: 배포 시 신규 연결 차단 + Grace Period
```

---

## Scale-out 완성 후 목표 아키텍처

```
                        ┌─────────────────────────────────────────┐
                        │            Redis                         │
                        │  session:registry (userId → instanceId) │
                        │  chat.server.instance-1 (Pub/Sub)       │
                        │  chat.server.instance-2 (Pub/Sub)       │
                        │  chat.room.{roomId} (최근 메시지 버퍼)  │
                        └────────────────┬────────────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              │                          │                          │
     ┌────────▼─────────┐    ┌──────────▼────────┐    ┌───────────▼────────┐
     │   Instance 1      │    │    Instance 2      │    │    Instance 3       │
     │  (30k 연결)       │    │   (35k 연결)       │    │   (35k 연결)        │
     │                   │    │                    │    │                     │
     │ localConnections  │    │ localConnections   │    │ localConnections    │
     │ userId_A, B, C... │    │ userId_D, E, F...  │    │ userId_G, H, I...  │
     └───────────────────┘    └────────────────────┘    └─────────────────────┘
              ▲                          ▲                          ▲
              │  WebSocket               │  WebSocket               │  WebSocket
     ┌────────┴─────────────────────────┴──────────────────────────┴────┐
     │                          L7 Load Balancer                         │
     │                    (Sticky Session + Connection Draining)         │
     └────────────────────────────────────────────────────────────────────┘
```

**메시지 라우팅 흐름 (개선 후):**

```
유저 A (Instance 1) → 유저 B에게 메시지
  1. ChatService.publishToRedis(message)
  2. Redis: HGET session:registry userB → "instance-2"
  3. PUBLISH chat.server.instance-2 {message}
  4. Instance 2의 Redis 리스너 수신
  5. localConnections[userB].tryEmit(message)
  6. 유저 B의 WebSocket 세션으로 전달
```
