# massive-sse-service 분석 보고서

> 목표: 10만 동시 접속자 지원 SSE 서비스

## 1. 현재 아키텍처

### 디렉토리 구조
```
massive-sse-service/
├── build.gradle
└── src/main/java/com/booster/massivesseservice/
    ├── MassiveSseServiceApplication.java
    ├── controller/
    │   └── PushController.java          # SSE 엔드포인트
    ├── service/
    │   └── NotificationService.java     # 구독/브로드캐스트 로직
    └── repository/
        └── UserConnectionRegistry.java  # 연결 관리 (메모리)
```

### 데이터 흐름
```
Client → GET /sse/connect/{userId}
           ↓
    PushController.connect()
           ↓
    NotificationService.subscribe()
           ↓
    UserConnectionRegistry.createConnection()
           ↓
    Sinks.Many<String> 생성 (버퍼=100)
           ↓
    Flux.merge(eventFlux, heartbeatFlux) 반환
           ↓
    HTTP Streaming (text/event-stream)
```

---

## 2. 핵심 컴포넌트 분석

### PushController.java
```java
@GetMapping(value = "/connect/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> connect(@PathVariable String userId) {
    return service.subscribe(userId);
}

@PostMapping("/broadcast")
public String broadcast(@RequestParam String message) {
    service.broadcast(message);
    return "Broadcasted!";
}
```

### NotificationService.java
```java
public Flux<String> subscribe(String userId) {
    Sinks.Many<String> sink = registry.createConnection(userId);

    // 이벤트 스트림
    Flux<String> eventFlux = sink.asFlux()
            .doOnCancel(() -> {
                log.info("User disconnected: {}", userId);
                registry.removeConnection(userId);
            });

    // 하트비트 (30초 간격)
    Flux<String> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
            .map(i -> "ping");

    return Flux.merge(eventFlux, heartbeatFlux);
}

public void broadcast(String message) {
    registry.getAll().forEach((userId, sink) -> {
        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result.isFailure()) {
            registry.removeConnection(userId);
        }
    });
}
```

### UserConnectionRegistry.java
```java
private final Map<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

public Sinks.Many<String> createConnection(String userId) {
    // 기존 연결 킥아웃
    if (userSinks.containsKey(userId)) {
        userSinks.get(userId).tryEmitComplete();
    }

    // 새 Sink 생성 (버퍼 100개)
    Sinks.Many<String> sink = Sinks.many().multicast()
            .onBackpressureBuffer(100, false);
    userSinks.put(userId, sink);
    return sink;
}
```

---

## 3. 현재 상태 평가

### 긍정적 요소

| 항목 | 평가 | 설명 |
|------|------|------|
| 동시성 모델 | ✅ 우수 | WebFlux 리액티브 모델 (Netty 기반) |
| 논블로킹 I/O | ✅ 우수 | 적은 스레드로 다수 연결 처리 가능 |
| 하트비트 | ✅ 양호 | 30초 간격 ping (프록시 타임아웃 방지) |
| 중복 연결 처리 | ✅ 양호 | 동일 userId 재연결 시 기존 연결 킥아웃 |

### 심각한 문제점

| 항목 | 심각도 | 문제 | 영향 |
|------|--------|------|------|
| 메모리 저장소 | 🔴 CRITICAL | 모든 연결이 JVM Heap에 저장 | 10만 연결 시 메모리 부족 위험 |
| 단일 인스턴스 | 🔴 CRITICAL | Scale-out 불가능 | 다중 서버 시 상태 동기화 없음 |
| 브로드캐스트 성능 | 🟠 HIGH | `getAll()` 순회 O(n) | 10만 연결 시 병목 발생 |
| 버퍼 크기 | 🟠 HIGH | 100개로 제한 | 느린 클라이언트 강제 종료 |
| Lombok 누락 | 🟡 MEDIUM | build.gradle에 없음 | 컴파일 오류 가능 |
| 설정 부재 | 🟡 MEDIUM | application.yml 최소 설정 | 기본값 의존 |

---

## 4. 10만 동시 접속 달성을 위한 개선 방안

### 4.1 필수 개선사항

#### (1) Redis 기반 분산 아키텍처

**현재 문제**: 단일 서버에서만 연결 상태 관리
```java
// AS-IS: 메모리 기반
private final Map<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();
```

**개선 방안**: Redis Pub/Sub + 로컬 Sink 분리
```java
// TO-BE: Redis Pub/Sub으로 크로스 인스턴스 메시지 전달
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final UserConnectionRegistry localRegistry;  // 로컬 Sink만 관리

    // 브로드캐스트: Redis로 발행 → 모든 인스턴스가 수신
    public Mono<Void> broadcast(String message) {
        return redisTemplate.convertAndSend("sse:broadcast", message).then();
    }

    // Redis 구독 → 로컬 클라이언트에 전달
    @PostConstruct
    public void subscribeToRedis() {
        redisTemplate.listenToChannel("sse:broadcast")
            .subscribe(msg -> localRegistry.broadcastToLocal(msg.getMessage()));
    }
}
```

#### (2) 브로드캐스트 성능 개선

**현재 문제**: 순차 처리 O(n)
```java
registry.getAll().forEach((userId, sink) -> {
    sink.tryEmitNext(message);  // 동기 순회
});
```

**개선 방안**: 병렬 처리 + 배치
```java
public void broadcastToLocal(String message) {
    // 병렬 스트림으로 처리
    userSinks.values().parallelStream().forEach(sink -> {
        sink.tryEmitNext(message);
    });
}

// 또는 Flux 기반 비동기 처리
public Mono<Void> broadcastAsync(String message) {
    return Flux.fromIterable(userSinks.values())
        .parallel()
        .runOn(Schedulers.parallel())
        .doOnNext(sink -> sink.tryEmitNext(message))
        .then();
}
```

#### (3) 버퍼 및 Backpressure 전략 개선

**현재**: 버퍼 100개, 초과 시 연결 종료
```java
Sinks.many().multicast().onBackpressureBuffer(100, false);
```

**개선 방안**:
```java
// 버퍼 증가 + 오래된 메시지 드롭 전략
Sinks.Many<String> sink = Sinks.many().multicast()
    .onBackpressureBuffer(
        1000,                    // 버퍼 크기 증가
        BufferOverflowStrategy.DROP_OLDEST  // 오래된 메시지 드롭
    );
```

#### (4) WebFlux 설정 최적화

**application.yml 추가**:
```yaml
server:
  port: 8080
  netty:
    connection-timeout: 60s
    idle-timeout: 120s
    max-keep-alive-requests: 100000

spring:
  application:
    name: massive-sse-service
  webflux:
    base-path: /api

# Reactor Netty 설정
reactor:
  netty:
    ioWorkerCount: 16           # I/O 스레드 수
    pool:
      maxConnections: 100000    # 최대 연결 수
      pendingAcquireTimeout: 60s

# 모니터링
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: massive-sse-service
```

#### (5) build.gradle 의존성 보완

```gradle
dependencies {
    // 기존
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // 추가 필요
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Redis (분산 환경)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

    // 모니터링
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'

    // 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}
```

### 4.2 권장 개선사항

#### (1) 모니터링 메트릭 추가

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("sse.connections.active", activeConnections, AtomicInteger::get)
            .register(meterRegistry);
    }

    public Flux<String> subscribe(String userId) {
        activeConnections.incrementAndGet();
        // ...
        return flux.doFinally(signal -> activeConnections.decrementAndGet());
    }
}
```

#### (2) 연결 상태 헬스체크

```java
@RestController
@RequestMapping("/health")
public class HealthController {
    private final UserConnectionRegistry registry;

    @GetMapping("/connections")
    public Map<String, Object> connectionHealth() {
        return Map.of(
            "activeConnections", registry.count(),
            "status", registry.count() < 100000 ? "OK" : "WARNING"
        );
    }
}
```

#### (3) Graceful Shutdown

```java
@Component
@RequiredArgsConstructor
public class GracefulShutdown {
    private final UserConnectionRegistry registry;

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down... Closing {} connections", registry.count());
        registry.getAll().values().forEach(sink -> {
            sink.tryEmitNext("server-shutdown");
            sink.tryEmitComplete();
        });
    }
}
```

---

## 5. 권장 아키텍처 (10만+ 동시 접속)

```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │  (Sticky Session)│
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  SSE Server 1 │   │  SSE Server 2 │   │  SSE Server N │
│  (WebFlux)    │   │  (WebFlux)    │   │  (WebFlux)    │
│               │   │               │   │               │
│ Local Sinks   │   │ Local Sinks   │   │ Local Sinks   │
│ (30K users)   │   │ (30K users)   │   │ (40K users)   │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                    ┌───────▼───────┐
                    │  Redis Pub/Sub │
                    │  (Broadcast)   │
                    └───────────────┘
```

**핵심 포인트**:
1. **로컬 Sink**: 각 서버는 자신에게 연결된 클라이언트만 관리
2. **Redis Pub/Sub**: 브로드캐스트 메시지를 모든 서버에 전파
3. **Sticky Session**: 같은 사용자는 같은 서버로 라우팅
4. **수평 확장**: 서버 추가로 용량 증가

---

## 6. 최종 평가

| 항목 | 현재 | 목표 |
|------|------|------|
| 10만 동시 접속 준비도 | ⚠️ 50% | 100% |
| Scale-out 지원 | ❌ 불가 | ✅ 가능 |
| 브로드캐스트 성능 | O(n) 순차 | O(n/p) 병렬 |
| 모니터링 | ❌ 없음 | ✅ Prometheus |
| 장애 복구 | ❌ 없음 | ✅ Graceful Shutdown |

**결론**: 현재 상태로는 10만 동시 접속 **달성 불가능**. Redis Pub/Sub 도입과 브로드캐스트 병렬화가 필수.

---

## 7. 구현 우선순위

1. **[P0] Lombok 의존성 추가** - 컴파일 오류 해결
2. **[P0] Redis Pub/Sub 도입** - Scale-out 지원
3. **[P1] 브로드캐스트 병렬화** - 성능 개선
4. **[P1] WebFlux 설정 최적화** - 연결 제한 해제
5. **[P2] 모니터링 추가** - 운영 가시성
6. **[P2] 버퍼 전략 개선** - 느린 클라이언트 대응