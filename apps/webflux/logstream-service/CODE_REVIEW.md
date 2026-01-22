# LogStream Service 코드 리뷰

WebFlux + Disruptor 기반 고성능 로그 처리기 분석

---

## 아키텍처 개요

```
┌─────────────────────────────────────┐
│         WebFlux (Netty)             │
│      논블로킹 비동기 HTTP 서버       │
└────────────┬────────────────────────┘
             │ POST /logs
             ↓
┌─────────────────────────────────────┐
│        LogController                │
│     WebFlux 리액티브 컨트롤러        │
└────────────┬────────────────────────┘
             │ logProducer.publish()
             ↓
┌─────────────────────────────────────┐
│   Disruptor RingBuffer (1M 슬롯)    │
│   ProducerType.MULTI (Lock-Free)    │
│   BlockingWaitStrategy              │
└────────────┬────────────────────────┘
             │ (별도 데몬 스레드)
             ↓
┌─────────────────────────────────────┐
│       LogEventHandler               │
│      배치 플러시 전략                │
└────────────┬────────────────────────┘
             │
             ↓
┌─────────────────────────────────────┐
│       LogFileWriter                 │
│   Direct ByteBuffer (4MB)           │
│   NIO FileChannel (Append)          │
└────────────┬────────────────────────┘
             ↓
        access_logs.txt
```

---

## 문제점 요약

| 우선순위 | 문제 | 위치 | 심각도 |
|---------|------|------|--------|
| P0 | `fileChannel.force()` 주석 처리 - 데이터 손실 | LogFileWriter.java:63 | 🔴 치명 |
| P0 | RingBuffer 용량 초과 예외 미처리 | LogProducer.java | 🔴 치명 |
| P0 | 버퍼 오버플로우 처리 없음 | LogFileWriter.java:47 | 🔴 치명 |
| P1 | RandomAccessFile 핸들 누수 가능성 | LogFileWriter.java:31-36 | 🟡 높음 |
| P1 | Graceful Shutdown 순서 이슈 | DisruptorConfig.java | 🟡 높음 |
| P2 | 모니터링/메트릭 없음 | 전체 | 🟠 중간 |
| P2 | 하드코딩된 설정값 | 전체 | 🟠 중간 |
| P3 | 테스트 코드 없음 | 전체 | 🟢 낮음 |
| P3 | System.out.println 사용 | DisruptorConfig.java | 🟢 낮음 |
| P3 | UUID 생성 오버헤드 | LogProducer.java | 🟢 낮음 |

---

## P0: 치명적 문제

### 1. 데이터 손실 가능성 - `force()` 주석 처리

**위치**: `LogFileWriter.java:63`

```java
public void flush() {
    // ...
    fileChannel.write(buffer);
    // fileChannel.force(false);  // ⚠️ 주석 처리됨!
}
```

**문제**:
- OS 크래시, 전원 장애 시 메모리에 있는 데이터 손실
- Page Cache에만 있고 디스크에 쓰이지 않음

**해결 방안**:
```java
public void flush() {
    if (buffer.position() > 0) {
        buffer.flip();
        try {
            fileChannel.write(buffer);
            fileChannel.force(false);  // 주석 해제 (metadata 제외, 데이터만 동기화)
        } catch (Exception e) {
            log.error("Failed to write to file", e);
        } finally {
            buffer.clear();
        }
    }
}
```

**성능 vs 안정성 트레이드오프**:
| 옵션 | 성능 | 안정성 |
|------|------|--------|
| `force()` 없음 | 최고 | 낮음 (OS 크래시 시 손실) |
| `force(false)` | 중간 | 중간 (데이터만 동기화) |
| `force(true)` | 낮음 | 최고 (metadata까지 동기화) |

---

### 2. RingBuffer 용량 초과 예외 미처리

**위치**: `LogProducer.java`

```java
public void publish(String payload) {
    RingBuffer<LogEvent> ringBuffer = disruptor.getRingBuffer();
    long sequence = ringBuffer.next();  // ⚠️ InsufficientCapacityException 발생 가능
    // ...
}
```

**문제**:
- RingBuffer가 가득 차면 예외 발생
- 클라이언트는 에러 응답 없이 서버 500 에러

**해결 방안**:
```java
public boolean publish(String payload) {
    RingBuffer<LogEvent> ringBuffer = disruptor.getRingBuffer();

    // tryNext: 실패 시 -1 반환 (블로킹하지 않음)
    long sequence;
    try {
        sequence = ringBuffer.tryNext();
    } catch (InsufficientCapacityException e) {
        log.warn("Disruptor buffer full, dropping log");
        return false;
    }

    try {
        LogEvent event = ringBuffer.get(sequence);
        event.set(UUID.randomUUID().toString(), payload, System.currentTimeMillis());
    } finally {
        ringBuffer.publish(sequence);
    }
    return true;
}
```

**Controller 수정**:
```java
@PostMapping("/logs")
public Mono<ResponseEntity<String>> receiveLog(@RequestBody String payload) {
    boolean success = logProducer.publish(payload);
    if (success) {
        return Mono.just(ResponseEntity.ok("ok"));
    } else {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Buffer full, try again later"));
    }
}
```

---

### 3. 버퍼 오버플로우 처리 없음

**위치**: `LogFileWriter.java:47-51`

```java
public void write(String logData) {
    byte[] bytes = (logData + "\n").getBytes(StandardCharsets.UTF_8);
    if (buffer.remaining() < bytes.length) {
        flush();
    }
    buffer.put(bytes);  // ⚠️ 여전히 넘칠 수 있음!
}
```

**문제**:
- 4MB 버퍼보다 큰 로그 하나가 들어오면 `BufferOverflowException`
- UTF-8은 가변 길이이므로 예측 어려움

**해결 방안**:
```java
public void write(String logData) {
    byte[] bytes = (logData + "\n").getBytes(StandardCharsets.UTF_8);

    // 매우 큰 로그는 직접 쓰기
    if (bytes.length > BUFFER_SIZE) {
        flush();  // 기존 버퍼 플러시
        try {
            fileChannel.write(ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            log.error("Failed to write large log directly", e);
        }
        return;
    }

    // 일반 로그는 버퍼에 추가
    if (buffer.remaining() < bytes.length) {
        flush();
    }
    buffer.put(bytes);
}
```

---

## P1: 중요 문제

### 4. RandomAccessFile 핸들 누수 가능성

**위치**: `LogFileWriter.java:31-36`

```java
public void init() {
    try {
        File file = new File(FILE_PATH);
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        this.fileChannel = raf.getChannel();
        // ⚠️ raf 참조를 잃음! GC 시 문제 가능
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

**해결 방안**:
```java
private RandomAccessFile raf;

@PostConstruct
public void init() {
    try {
        File file = new File(FILE_PATH);
        this.raf = new RandomAccessFile(file, "rw");
        this.fileChannel = raf.getChannel();
        fileChannel.position(fileChannel.size());
    } catch (Exception e) {
        throw new RuntimeException("Failed to initialize log file", e);
    }
}

@PreDestroy
public void cleanup() {
    try {
        forceFlush();
        if (fileChannel != null) fileChannel.close();
        if (raf != null) raf.close();
    } catch (Exception e) {
        log.error("Failed to close file resources", e);
    }
}
```

---

### 5. Graceful Shutdown 순서 이슈

**위치**: `DisruptorConfig.java`

```java
@Override
public void stop() {
    disruptor.shutdown();      // 이미 닫힘
    logEventHandler.forceFlush();  // 너무 늦음?
}
```

**해결 방안**:
```java
@Override
public void stop() {
    log.info("Graceful Shutdown Initiated: Flushing remaining logs...");

    // 1. 새 이벤트 발행 중지 (halt는 즉시 중지)
    // disruptor.halt();  // 필요시

    // 2. 기존 이벤트 처리 완료 대기
    try {
        disruptor.shutdown(5, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        log.warn("Disruptor shutdown timed out, forcing...");
        disruptor.halt();
    }

    // 3. 마지막 버퍼 플러시
    logEventHandler.forceFlush();

    log.info("Graceful Shutdown Complete");
}
```

---

## P2: 개선 권장

### 6. 모니터링/메트릭 없음

**현재**: 10만 건마다 로그만 출력

**개선 방안**:
```java
@Component
@RequiredArgsConstructor
public class LogMetrics {
    private final MeterRegistry registry;

    private Counter processedCounter;
    private Timer flushTimer;
    private AtomicLong bufferUsage = new AtomicLong(0);

    @PostConstruct
    public void init() {
        processedCounter = registry.counter("log.processed.total");
        flushTimer = registry.timer("log.flush.duration");
        registry.gauge("log.buffer.usage.bytes", bufferUsage);
    }

    public void recordProcessed() {
        processedCounter.increment();
    }

    public void recordFlush(long bytes, long durationNs) {
        flushTimer.record(durationNs, TimeUnit.NANOSECONDS);
        bufferUsage.set(bytes);
    }
}
```

---

### 7. 하드코딩된 설정값

**현재**:
```java
private static final int BUFFER_SIZE = 1024 * 1024;  // DisruptorConfig
private static final int BUFFER_SIZE = 1024 * 1024 * 4;  // LogFileWriter
private static final String FILE_PATH = "access_logs.txt";
```

**개선 방안**:
```yaml
# application.yml
logstream:
  disruptor:
    buffer-size: 1048576
    producer-type: MULTI
    wait-strategy: BLOCKING
  file:
    path: /var/log/access_logs.txt
    buffer-size: 4194304  # 4MB
    force-on-flush: true
```

```java
@ConfigurationProperties(prefix = "logstream")
public record LogStreamProperties(
    DisruptorConfig disruptor,
    FileConfig file
) {
    public record DisruptorConfig(int bufferSize, String producerType, String waitStrategy) {}
    public record FileConfig(String path, int bufferSize, boolean forceOnFlush) {}
}
```

---

## P3: 사소한 개선

### 8. System.out.println 사용

**현재**:
```java
System.out.println(">>> Graceful Shutdown Initiated: ...");
```

**개선**:
```java
log.info("Graceful Shutdown Initiated: Flushing remaining logs...");
```

---

### 9. UUID 생성 오버헤드

**현재**:
```java
event.set(UUID.randomUUID().toString(), payload, System.currentTimeMillis());
```

**문제**: `UUID.randomUUID()`는 보안 난수 생성 → 비용 높음

**개선 방안**:
```java
// Snowflake ID 또는 원자 카운터 사용
private final AtomicLong idGenerator = new AtomicLong(0);

event.set(String.valueOf(idGenerator.incrementAndGet()), payload, System.currentTimeMillis());
```

---

## 성능 특성

### 예상 처리량

| 컴포넌트 | 지연시간 |
|---------|---------|
| HTTP Request → Controller | ~1µs |
| Controller → LogProducer | ~0.1µs |
| LogProducer → RingBuffer | ~0.1µs (CAS) |
| RingBuffer → Handler | ~1µs |
| Handler → FileWriter | ~0.1µs |
| **Total (I/O 제외)** | **~3-5µs** |

### 메모리 사용량

| 컴포넌트 | 크기 |
|---------|------|
| Disruptor RingBuffer (1M) | ~48MB |
| Direct ByteBuffer | 4MB |
| **Total** | **~52MB** |

---

## 강점

1. **극한의 성능**: Disruptor Lock-free + 배치 플러시
2. **낮은 지연시간**: WebFlux(Netty) + Direct Buffer
3. **메모리 효율**: RingBuffer 재사용, GC 영향 최소화
4. **확장성**: Multi-Producer 지원
5. **Graceful Shutdown**: Spring Lifecycle 연동

---

## 요약 체크리스트

- [x] ~~`fileChannel.force(false)` 주석 해제~~ (의도적으로 처리량 우선)
- [x] `InsufficientCapacityException` 처리 추가 ✅
- [x] 대용량 로그 버퍼 오버플로우 처리 ✅
- [x] RandomAccessFile 필드로 보관 + PreDestroy 정리 ✅
- [x] Graceful Shutdown 타임아웃 추가 ✅
- [ ] 메트릭/모니터링 추가 (추후)
- [x] 설정 외부화 ✅
- [x] System.out.println → log.info ✅
- [x] UUID → AtomicLong 최적화 ✅
- [ ] 테스트 코드 작성 (추후)
