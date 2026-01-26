# SimpleLoadTester 코드 리뷰

## 개요

| 항목 | 내용 |
|------|------|
| 파일 | `SimpleLoadTester.java` |
| 목적 | WebSocket 채팅 서비스 부하 테스트 |
| 기술 | Reactor Netty WebSocket Client |

---

## 1. 아키텍처 분석

### 1.1 전체 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                      SimpleLoadTester                           │
├─────────────────────────────────────────────────────────────────┤
│  1. 환경변수 로드 (URL, 연결수, 방ID, 메시지설정)               │
│  2. Flux.range(1, count)로 N개의 연결 생성                      │
│  3. 각 연결에서:                                                 │
│     ├─ ENTER 메시지 전송 (입장)                                 │
│     ├─ 주기적 TALK 메시지 전송                                  │
│     └─ 수신 메시지 로깅                                         │
│  4. 주기적 통계 출력 (10초 간격)                                │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Reactive Streams 구조

```
                    ┌──────────────────┐
                    │   Main Thread    │
                    └────────┬─────────┘
                             │
              Flux.range(1, count).flatMap()
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    Connection 1        Connection 2        Connection N
         │                   │                   │
    ┌────┴────┐         ┌────┴────┐         ┌────┴────┐
    │ Input   │         │ Input   │         │ Input   │
    │ (recv)  │         │ (recv)  │         │ (recv)  │
    ├─────────┤         ├─────────┤         ├─────────┤
    │ Output  │         │ Output  │         │ Output  │
    │ (send)  │         │ (send)  │         │ (send)  │
    └─────────┘         └─────────┘         └─────────┘
```

---

## 2. 코드 상세 리뷰

### 2.1 메시지 템플릿 정의 (Line 12-15)

```java
private static final String MSG_ENTER = """
        {"type":"ENTER","roomId":"%s","userId":"%s","message":"입장합니다"}""";
private static final String MSG_TALK = """
        {"type":"TALK","roomId":"%s","userId":"%s","message":"Hello #%d"}""";
```

**평가**: ✅ 좋음

- Text Block 사용으로 가독성 향상
- `%s`, `%d` 포맷터로 동적 값 삽입
- `static final`로 재사용 최적화

**개선 제안**:
- JSON 라이브러리(Jackson) 사용 고려 - 특수문자 이스케이프 처리 자동화
- 현재는 테스트 용도라 문자열 템플릿도 충분함

---

### 2.2 환경변수 설정 (Line 18-24)

```java
String baseUrl = System.getenv().getOrDefault("TARGET_URL", "ws://localhost:8080/ws/chat");
int count = Integer.parseInt(System.getenv().getOrDefault("CONN_COUNT", "300"));
String clientId = System.getenv().getOrDefault("CLIENT_ID", "tester-" + ProcessHandle.current().pid());
String roomId = System.getenv().getOrDefault("ROOM_ID", "load-test-room");
int messageCount = Integer.parseInt(System.getenv().getOrDefault("MSG_COUNT", "10"));
long messageIntervalSec = Long.parseLong(System.getenv().getOrDefault("MSG_INTERVAL_SEC", "5"));
```

**평가**: ✅ 좋음

| 환경변수 | 기본값 | 용도 |
|----------|--------|------|
| `TARGET_URL` | `ws://localhost:8080/ws/chat` | WebSocket 서버 주소 |
| `CONN_COUNT` | `300` | 동시 연결 수 |
| `CLIENT_ID` | `tester-{PID}` | 클라이언트 식별자 |
| `ROOM_ID` | `load-test-room` | 채팅방 ID |
| `MSG_COUNT` | `10` | 연결당 전송할 메시지 수 |
| `MSG_INTERVAL_SEC` | `5` | 메시지 전송 간격(초) |

**개선 제안**:
```java
// NumberFormatException 처리 추가
int count = parseIntOrDefault("CONN_COUNT", 300);

private static int parseIntOrDefault(String envKey, int defaultValue) {
    try {
        return Integer.parseInt(System.getenv().getOrDefault(envKey, String.valueOf(defaultValue)));
    } catch (NumberFormatException e) {
        System.err.println("[WARN] Invalid " + envKey + ", using default: " + defaultValue);
        return defaultValue;
    }
}
```

---

### 2.3 통계 카운터 (Line 26-28)

```java
AtomicInteger connected = new AtomicInteger(0);
AtomicInteger messagesSent = new AtomicInteger(0);
AtomicInteger messagesReceived = new AtomicInteger(0);
```

**평가**: ✅ 좋음

- `AtomicInteger` 사용으로 Thread-safe 보장
- 연결/송신/수신 3가지 핵심 지표 추적

**개선 제안**:
- 에러 카운터 추가 고려: `AtomicInteger errors = new AtomicInteger(0);`
- 지연시간(latency) 측정 추가 고려

---

### 2.4 WebSocket URL 구성 (Line 44-45)

```java
String userId = clientId + "-" + i;
String wsUrl = baseUrl + "?userId=" + userId;
```

**평가**: ✅ 좋음 (기존 헤더 방식에서 수정됨)

- 서버의 `ChatWebSocketHandler`가 쿼리 파라미터로 `userId`를 파싱
- URL 인코딩이 필요한 특수문자가 userId에 포함되면 문제 가능

**개선 제안**:
```java
String wsUrl = baseUrl + "?userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8);
```

---

### 2.5 입력 스트림 처리 (Line 56-70)

```java
Mono<Void> input = in.receive()
        .asString()
        .doOnNext(msg -> {
            int received = messagesReceived.incrementAndGet();
            if (received % 500 == 0 || received <= 5) {
                System.out.println("[RECV] " + userId + ": " + truncate(msg, 80));
                System.out.flush();
            }
        })
        .doOnTerminate(() -> {
            connected.decrementAndGet();
            System.out.println("[DISC] " + userId + " disconnected");
            System.out.flush();
        })
        .then();
```

**평가**: ✅ 좋음

| 요소 | 설명 |
|------|------|
| `in.receive().asString()` | WebSocket 프레임을 문자열로 변환 |
| `doOnNext()` | 수신 시 카운터 증가 + 샘플링 로깅 |
| `doOnTerminate()` | 연결 종료 시 정리 |
| 샘플링 (`% 500`) | 로그 폭주 방지 |

**개선 제안**:
- `doOnError()` 추가로 수신 오류 처리

```java
.doOnError(e -> System.err.println("[RECV_ERR] " + userId + ": " + e.getMessage()))
```

---

### 2.6 출력 스트림 처리 (Line 72-84)

```java
Flux<String> messageStream = Flux.concat(
        Mono.just(MSG_ENTER.formatted(roomId, userId)),
        Flux.interval(Duration.ofSeconds(messageIntervalSec))
                .take(messageCount)
                .map(seq -> MSG_TALK.formatted(roomId, userId, seq + 1))
).doOnNext(msg -> {
    int sent = messagesSent.incrementAndGet();
    if (sent % 500 == 0 || sent <= 5) {
        System.out.println("[SEND] " + userId + ": " + truncate(msg, 80));
        System.out.flush();
    }
});
```

**평가**: ✅ 좋음

```
시간 흐름:
  0초: ENTER 메시지 전송
  5초: TALK #1
 10초: TALK #2
  ...
 50초: TALK #10 (완료)
```

- `Flux.concat()`으로 순차 실행 보장
- `Flux.interval()`로 non-blocking 주기 전송
- `take(messageCount)`로 무한 스트림 제한

**개선 제안**:
- 초기 딜레이 추가로 연결 안정화 후 메시지 전송

```java
Flux.interval(Duration.ofSeconds(1), Duration.ofSeconds(messageIntervalSec))
    // 1초 후 시작, 이후 5초 간격
```

---

### 2.7 입출력 병합 (Line 88)

```java
return Mono.zip(input, output).then();
```

**평가**: ✅ 좋음

- `Mono.zip()`으로 input/output 동시 실행
- 둘 중 하나라도 종료되면 전체 종료
- 서버의 `Mono.zip(input, session.send(outputFlux)).then()` 패턴과 일치

**주의점**:
- output이 먼저 완료되면(모든 메시지 전송 후) input도 함께 종료됨
- 계속 수신만 하려면 output에 `Mono.never()` 추가 필요

---

### 2.8 재시도 전략 (Line 92-98)

```java
.retryWhen(
        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(5))
                .doBeforeRetry(signal ->
                        System.out.println("[RETRY] " + userId + " attempt #" + (signal.totalRetries() + 1))
                )
)
```

**평가**: ⚠️ 주의 필요

| 설정 | 값 | 의미 |
|------|-----|------|
| 최대 재시도 | `Long.MAX_VALUE` | 무한 재시도 |
| 초기 백오프 | 1초 | 첫 재시도 대기 |
| 최대 백오프 | 5초 | 최대 대기 시간 |

**개선 제안**:
- 무한 재시도는 부하 테스트에선 OK, 프로덕션에선 제한 필요
- Jitter 추가로 Thundering Herd 방지

```java
Retry.backoff(100, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(5))
        .jitter(0.5)  // 50% 랜덤 지터
```

---

### 2.9 통계 모니터링 (Line 108-115)

```java
Flux.interval(Duration.ofSeconds(10))
        .doOnNext(tick -> {
            System.out.println("[STAT] connected=" + connected.get()
                    + ", sent=" + messagesSent.get()
                    + ", received=" + messagesReceived.get());
            System.out.flush();
        })
        .blockLast();
```

**평가**: ✅ 좋음

- 기존 `Mono.never().block()` 대비 개선
- 10초마다 상태 확인 가능
- `blockLast()`로 메인 스레드 유지

**개선 제안**:
- TPS(초당 처리량) 계산 추가

```java
AtomicLong lastSent = new AtomicLong(0);
Flux.interval(Duration.ofSeconds(10))
        .doOnNext(tick -> {
            long currentSent = messagesSent.get();
            long tps = (currentSent - lastSent.get()) / 10;
            lastSent.set(currentSent);
            System.out.println("[STAT] connected=" + connected.get()
                    + ", sent=" + currentSent
                    + ", received=" + messagesReceived.get()
                    + ", tps=" + tps);
        })
```

---

### 2.10 유틸리티 메서드 (Line 117-121)

```java
private static String truncate(String str, int maxLen) {
    if (str == null) return "";
    String oneLine = str.replace("\n", " ").replace("\r", "");
    return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
}
```

**평가**: ✅ 좋음

- null-safe 처리
- 줄바꿈 제거로 로그 가독성 향상
- 긴 메시지 truncate로 로그 폭주 방지

---

## 3. 장점 요약

| 항목 | 설명 |
|------|------|
| ✅ Reactive 패턴 | Non-blocking I/O로 적은 리소스로 많은 연결 처리 |
| ✅ 설정 외부화 | 환경변수로 동작 제어 가능 |
| ✅ 샘플링 로깅 | 로그 폭주 방지 (500건당 1건) |
| ✅ 통계 모니터링 | 실시간 연결/송수신 현황 확인 |
| ✅ 재시도 전략 | Exponential backoff로 안정적 재연결 |
| ✅ 프로토콜 준수 | 서버의 메시지 형식(ChatMessage) 준수 |

---

## 4. 개선 권장 사항

### 4.1 우선순위 높음

1. **URL 인코딩 추가**
   ```java
   import java.net.URLEncoder;
   import java.nio.charset.StandardCharsets;

   String wsUrl = baseUrl + "?userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8);
   ```

2. **에러 카운터 추가**
   ```java
   AtomicInteger errors = new AtomicInteger(0);
   // doOnError에서 errors.incrementAndGet()
   ```

### 4.2 우선순위 중간

3. **Graceful Shutdown 지원**
   ```java
   Runtime.getRuntime().addShutdownHook(new Thread(() -> {
       System.out.println("[SHUTDOWN] Final stats: connected=" + connected.get()
               + ", sent=" + messagesSent.get()
               + ", received=" + messagesReceived.get());
   }));
   ```

4. **TPS 계산 추가** (위 코드 참조)

### 4.3 우선순위 낮음

5. **JSON 라이브러리 사용** - 테스트 용도라 현재도 충분
6. **Jitter 추가** - 대규모 테스트 시 권장

---

## 5. 실행 예시

```bash
# 기본 실행 (300 연결, 10 메시지, 5초 간격)
java -cp . com.booster.chattingservice.test.SimpleLoadTester

# 커스텀 설정
export TARGET_URL="ws://chat-server:8080/ws/chat"
export CONN_COUNT=1000
export ROOM_ID="stress-test"
export MSG_COUNT=100
export MSG_INTERVAL_SEC=1
java -cp . com.booster.chattingservice.test.SimpleLoadTester
```

**예상 출력**:
```
=== SimpleLoadTester Configuration ===
  Target URL    : ws://localhost:8080/ws/chat
  Client ID     : tester-12345
  Connections   : 300
  Room ID       : load-test-room
  Messages/User : 10
  Interval(sec) : 5
=======================================
[CONN] tester-12345-1 connected (total: 1)
[SEND] tester-12345-1: {"type":"ENTER","roomId":"load-test-room","userId":"tester-12345-1"...
[RECV] tester-12345-1: {"type":"ENTER","roomId":"load-test-room","userId":"tester-12345-1"...
[CONN] tester-12345-2 connected (total: 2)
...
[STAT] connected=300, sent=3000, received=900000
```

---

## 6. 결론

**종합 평가**: ⭐⭐⭐⭐ (4/5)

이 테스터는 WebSocket 채팅 서비스의 부하를 효과적으로 시뮬레이션할 수 있습니다. Reactor Netty의 non-blocking 특성을 잘 활용하여 적은 리소스로 많은 연결을 처리하며, 환경변수를 통한 유연한 설정이 가능합니다.

주요 개선점으로는 URL 인코딩 처리와 에러 카운터 추가가 있으며, 이는 실제 부하 테스트 시 더 정확한 결과 분석을 가능하게 합니다.