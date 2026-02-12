# Kotlin WebSocket Chatting Service

동시 10만 접속자를 지원하는 Kotlin + WebFlux 기반 실시간 채팅 서비스.

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot 4.0.1 + Spring WebFlux |
| Runtime | Reactor Netty (Non-blocking I/O) |
| Async | Kotlin Coroutines + Flow (`SharedFlow`, `suspend fun`) |
| Message Broker | Redis Pub/Sub (수평 확장) |
| Serialization | Jackson + jackson-module-kotlin |
| Observability | Spring Actuator + Micrometer + Prometheus |
| Test | JUnit 5 + MockitoKotlin + Coroutines Test |

## 아키텍처

```
Client A ──WebSocket──▶ [Instance 1]            [Instance 2] ◀──WebSocket── Client B
                          │                        ▲
                          ▼                        │
                     Redis Pub/Sub ────────────────┘
                     (chat.public)
```

- **Non-blocking I/O**: Reactor Netty 이벤트 루프로 소수 스레드가 수만 커넥션 처리
- **Kotlin Coroutines**: `SharedFlow` 기반 메시지 스트림, `suspend fun`으로 Redis 비동기 발행
- **Redis Pub/Sub**: 서버 인스턴스 간 메시지 동기화 (수평 확장)
- **채팅방 격리**: `rooms` 맵으로 방별 브로드캐스트 (전체 broadcast 아님)
- **Graceful Shutdown**: `@PreDestroy`에서 전체 클라이언트에 종료 메시지 전송 후 연결 정리
- **SupervisorJob**: Redis 리스너의 개별 메시지 처리 실패가 전체 리스너에 전파되지 않음

## 프로젝트 구조

```
chatting-service/
├── build.gradle.kts
├── docs/
│   └── README.md
└── src/
    ├── main/kotlin/com/booster/kotlin/chattingservice/
    │   ├── ChattingServiceApplication.kt       # Spring Boot 진입점
    │   ├── domain/
    │   │   └── ChatMessage.kt                  # 메시지 DTO (ENTER, TALK, LEAVE, PING)
    │   ├── application/
    │   │   └── ChatService.kt                  # 커넥션 관리 + Redis Pub/Sub + Micrometer 메트릭
    │   ├── config/
    │   │   ├── WebSocketConfig.kt              # /ws/chat 엔드포인트 매핑
    │   │   ├── RedisConfig.kt                  # Redis Pub/Sub 리스너 (CoroutineScope)
    │   │   └── ChatMetrics.kt                  # Prometheus 커스텀 Gauge 등록
    │   ├── web/
    │   │   └── ChatWebSocketHandler.kt         # WebSocket 핸들러 (Coroutine 기반)
    │   └── test/
    │       └── LoadTester.kt                   # 부하 테스트 도구
    └── test/kotlin/com/booster/kotlin/chattingservice/
        ├── ChattingServiceApplicationTests.kt  # 컨텍스트 로드 테스트
        ├── TestConfig.kt                       # Redis mock + broadcast 시뮬레이션
        ├── domain/
        │   └── ChatMessageTest.kt              # DTO 단위 테스트 (8개)
        ├── application/
        │   └── ChatServiceTest.kt              # 서비스 단위 테스트 (13개)
        └── web/
            └── ChatWebSocketHandlerTest.kt     # WebSocket 통합 테스트 (4개)
```

## 메시지 타입

| Type | 설명 | 예시 |
|------|------|------|
| `ENTER` | 채팅방 입장 | 유저가 방에 참여할 때 자동 broadcast |
| `TALK` | 일반 메시지 | 같은 방 유저에게만 전달 |
| `LEAVE` | 채팅방 퇴장 | 연결 끊김 시 자동 발행 |
| `PING` | 연결 유지 확인 | 서버에서 무시 (broadcast 안 함) |

## 메시지 JSON 포맷

```json
{
  "type": "TALK",
  "roomId": "room-1",
  "userId": "user1",
  "message": "안녕하세요"
}
```

> `userId`는 클라이언트가 보내도 서버에서 쿼리 파라미터 값으로 강제 덮어씁니다 (위변조 방지).

## 설정 (application.yml)

| 항목 | 값 | 설명 |
|------|----|------|
| `server.port` | 8085 | 서비스 포트 |
| `server.shutdown` | graceful | Graceful Shutdown 활성화 |
| `spring.lifecycle.timeout-per-shutdown-phase` | 10s | 종료 대기 시간 |
| `spring.data.redis.host` | localhost | Redis 호스트 |
| `spring.data.redis.port` | 6379 | Redis 포트 |
| `spring.data.redis.lettuce.pool.max-active` | 16 | 최대 활성 커넥션 |
| `management.endpoints.web.exposure.include` | health,info,prometheus,metrics | Actuator 노출 엔드포인트 |

---

## 실행 방법

### 사전 준비

Redis가 실행 중이어야 합니다.

```bash
# Docker로 Redis 실행
docker run -d --name redis -p 6379:6379 redis:latest
```

### 서비스 실행

```bash
./gradlew :apps:kotlin:chatting-service:bootRun
```

서버가 `localhost:8085`에서 시작됩니다.

---

## 모니터링 (Actuator + Prometheus)

### Actuator 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /actuator/health` | 헬스 체크 (상세 정보 포함) |
| `GET /actuator/prometheus` | Prometheus 스크래핑용 메트릭 |
| `GET /actuator/metrics` | 전체 메트릭 목록 |
| `GET /actuator/metrics/chat.connections.active` | 현재 접속자 수 |

### 커스텀 메트릭

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `chat_connections_active` | Gauge | 현재 WebSocket 접속자 수 |
| `chat_rooms_active` | Gauge | 현재 활성 채팅방 수 |
| `chat_messages_published_total` | Counter | Redis로 발행된 메시지 누적 수 |
| `chat_messages_broadcast_total` | Counter | 로컬 유저에게 전달된 메시지 누적 수 |

### Prometheus 연동 예시

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'kotlin-chatting-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8085']
```

### Grafana 대시보드 쿼리 예시

```promql
# 실시간 접속자 수
chat_connections_active

# 초당 메시지 발행률
rate(chat_messages_published_total[1m])

# 초당 브로드캐스트률
rate(chat_messages_broadcast_total[1m])
```

---

## 테스트

### 단위 테스트 + 통합 테스트 (29개)

```bash
./gradlew :apps:kotlin:chatting-service:test
```

| 테스트 클래스 | 테스트 수 | 범위 |
|--------------|-----------|------|
| `ChatMessageTest` | 8개 | 팩토리 메서드, 불변성, JSON 직렬화/역직렬화 |
| `ChatServiceTest` | 13개 | register, handleMessage, broadcast, remove, cleanup |
| `ChatWebSocketHandlerTest` | 4개 | 접속 거부, 정상 접속, 메시지 송수신, 잘못된 JSON |
| `ChattingServiceApplicationTests` | 1개 | Spring 컨텍스트 로드 |

> 테스트 시 Redis 없이 동작합니다. `TestConfig`에서 `ReactiveStringRedisTemplate`을 mock하여 `broadcastToLocalUsers`를 직접 호출하는 방식으로 Redis Pub/Sub를 시뮬레이션합니다.

### 수동 테스트 (wscat)

```bash
# wscat 설치
npm install -g wscat

# 터미널 1 - 유저 A 접속
wscat -c "ws://localhost:8085/ws/chat?userId=userA"

# 터미널 2 - 유저 B 접속
wscat -c "ws://localhost:8085/ws/chat?userId=userB"
```

접속 후 아래 JSON을 입력합니다.

```json
{"type":"ENTER","roomId":"room-1","userId":"userA","message":""}
{"type":"ENTER","roomId":"room-1","userId":"userB","message":""}
{"type":"TALK","roomId":"room-1","userId":"userA","message":"안녕하세요!"}
```

### 부하 테스트 (LoadTester)

#### JAR 빌드

```bash
./gradlew :apps:kotlin:chatting-service:loadTesterJar
```

#### 기본 실행 (1,000 커넥션)

```bash
java -jar apps/kotlin/chatting-service/build/libs/load-tester.jar
```

#### 환경변수로 설정 변경

```bash
# 10만 커넥션 테스트
CONN_COUNT=100000 \
BATCH_SIZE=1000 \
BATCH_DELAY_MS=2000 \
MSG_COUNT=5 \
MSG_INTERVAL_SEC=10 \
java -jar apps/kotlin/chatting-service/build/libs/load-tester.jar
```

#### 환경변수 목록

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `TARGET_URL` | `ws://localhost:8085/ws/chat` | WebSocket 엔드포인트 |
| `CONN_COUNT` | `1000` | 동시 접속 수 |
| `CLIENT_ID` | `tester-{PID}` | 클라이언트 ID 접두사 |
| `ROOM_ID` | `load-test-room` | 채팅방 ID |
| `MSG_COUNT` | `10` | 유저당 전송 메시지 수 |
| `MSG_INTERVAL_SEC` | `5` | 메시지 전송 간격 (초) |
| `BATCH_SIZE` | `500` | 동시 연결 배치 크기 |
| `BATCH_DELAY_MS` | `1000` | 배치 간 대기 시간 (ms) |

#### 출력 예시

```
=== Kotlin LoadTester Configuration ===
  Target URL     : ws://localhost:8085/ws/chat
  Client ID      : tester-12345
  Connections     : 1000
  Room ID         : load-test-room
  Messages/User   : 10
  Interval(sec)   : 5
  Batch Size      : 500
  Batch Delay(ms) : 1000
========================================
[BATCH] 1/2 시작 (500개 연결)
[CONN] tester-12345-1 connected (total: 1)
...
[BATCH] 2/2 시작 (500개 연결)
...
[INFO] 전체 1000개 연결 시작 완료. 모니터링 중...
[STAT] connected=1000, sent=2000, received=150000, errors=0, disconnected=0
```

### 10만 동시 접속 테스트 시 OS 튜닝

10만 커넥션을 테스트하려면 OS 수준 설정이 필요합니다.

#### Linux (서버/테스터 모두)

```bash
# 파일 디스크립터 제한 해제
ulimit -n 200000

# sysctl 설정
sudo sysctl -w net.core.somaxconn=65535
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sudo sysctl -w net.ipv4.tcp_tw_reuse=1
```

#### JVM 옵션 (서버)

```bash
java -Xms2g -Xmx4g \
     -Dreactor.netty.ioWorkerCount=8 \
     -jar chatting-service.jar
```

#### JVM 옵션 (테스터)

```bash
java -Xms1g -Xmx2g \
     -Dreactor.netty.pool.maxConnections=200000 \
     -jar load-tester.jar
```

### 수평 확장 테스트 (다중 인스턴스)

Redis Pub/Sub를 통해 인스턴스 간 메시지가 동기화되는지 확인합니다.

```bash
# 인스턴스 1 (포트 8085)
SERVER_PORT=8085 ./gradlew :apps:kotlin:chatting-service:bootRun

# 인스턴스 2 (포트 8086)
SERVER_PORT=8086 ./gradlew :apps:kotlin:chatting-service:bootRun

# 유저 A → 인스턴스 1에 접속
wscat -c "ws://localhost:8085/ws/chat?userId=userA"

# 유저 B → 인스턴스 2에 접속
wscat -c "ws://localhost:8086/ws/chat?userId=userB"

# 같은 방에 입장 후 메시지 전송 → 서로 다른 인스턴스에서도 수신 확인
```
