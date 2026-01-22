# LogStream Service

WebFlux + LMAX Disruptor 기반 고성능 로그 수집 서비스

## 개요

초당 수십만 건의 로그를 처리할 수 있는 비동기 로그 수집기입니다.

- **WebFlux (Netty)**: 논블로킹 HTTP 서버
- **LMAX Disruptor**: Lock-free 고성능 큐
- **Direct ByteBuffer**: GC 영향 없는 메모리 관리
- **배치 플러시**: I/O 최소화

## 아키텍처

```
┌─────────────────────────────────────┐
│         WebFlux (Netty)             │
│      논블로킹 비동기 HTTP 서버       │
└────────────┬────────────────────────┘
             │ POST /logs
             ↓
┌─────────────────────────────────────┐
│        LogController                │
│     리액티브 컨트롤러               │
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
│      배치 플러시 전략               │
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

## 실행

```bash
# 빌드
./gradlew :apps:webflux:logstream-service:build

# 실행
./gradlew :apps:webflux:logstream-service:bootRun
```

## API

### 로그 전송

```bash
curl -X POST http://localhost:8080/logs \
  -H "Content-Type: text/plain" \
  -d "2024-01-22 10:30:45 INFO UserService - User logged in: userId=123"
```

**응답**:
- `200 OK`: 로그 수신 성공
- `503 Service Unavailable`: 버퍼 가득 참 (잠시 후 재시도)

## 설정

```yaml
# application.yml
logstream:
  disruptor:
    buffer-size: 1048576      # RingBuffer 슬롯 수 (2의 제곱수)
    wait-strategy: BLOCKING   # BLOCKING, YIELDING, BUSY_SPIN
  file:
    path: access_logs.txt     # 로그 파일 경로
    buffer-size: 4194304      # Direct ByteBuffer 크기 (4MB)
```

### Wait Strategy 선택 가이드

| Strategy | CPU 사용 | 지연시간 | 사용 환경 |
|----------|---------|---------|----------|
| `BLOCKING` | 낮음 | 중간 | 일반 서버 (권장) |
| `YIELDING` | 중간 | 낮음 | 전용 서버 |
| `BUSY_SPIN` | 최대 | 최소 | 초저지연 필수 |

## 성능 특성

### 처리량

| 환경 | 예상 처리량 |
|------|------------|
| 일반 서버 | 100K+ logs/sec |
| 전용 서버 (BUSY_SPIN) | 500K+ logs/sec |

### 지연시간

| 구간 | 지연 |
|------|------|
| HTTP → RingBuffer | ~1µs |
| RingBuffer → Handler | ~1µs |
| Handler → 버퍼 쓰기 | ~0.1µs |
| **End-to-End (I/O 제외)** | **~3-5µs** |

### 메모리 사용량

| 컴포넌트 | 크기 |
|---------|------|
| Disruptor RingBuffer (1M) | ~48MB |
| Direct ByteBuffer | 4MB |
| **Total** | **~52MB** |

## 핵심 설계

### 1. Lock-Free 발행

```java
// LogProducer.java
long sequence = ringBuffer.tryNext();  // CAS 연산, 블로킹 없음
try {
    LogEvent event = ringBuffer.get(sequence);
    event.set(id, payload, timestamp);
} finally {
    ringBuffer.publish(sequence);
}
```

### 2. 배치 플러시

```java
// LogEventHandler.java
@Override
public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
    logFileWriter.write(event.getPayload());
    if (endOfBatch) {
        logFileWriter.flush();  // 배치 단위로 I/O
    }
}
```

### 3. Direct Buffer

```java
// LogFileWriter.java
// GC 영향 없는 네이티브 메모리
ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024);
```

### 4. Graceful Shutdown

```java
// DisruptorConfig.java
disruptor.shutdown(5, TimeUnit.SECONDS);  // 기존 이벤트 처리 대기
logEventHandler.forceFlush();             // 마지막 버퍼 플러시
```

## 부하 테스트

```bash
# k6 설치 필요
k6 run loadtest.js
```

```javascript
// loadtest.js
import http from 'k6/http';

export const options = {
  vus: 100,
  duration: '30s',
};

export default function () {
  http.post('http://localhost:8080/logs', 'test log message');
}
```

## 프로젝트 구조

```
src/main/java/com/booster/logstreamservice/
├── LogStreamServiceApplication.java    # 메인
├── config/
│   ├── DisruptorConfig.java           # Disruptor 설정
│   └── LogStreamProperties.java       # 설정 프로퍼티
├── controller/
│   └── LogController.java             # REST API
├── service/
│   └── LogProducer.java               # 로그 발행자
├── event/
│   └── LogEvent.java                  # 이벤트 모델
└── handler/
    ├── LogEventHandler.java           # 이벤트 소비자
    └── LogFileWriter.java             # 파일 쓰기
```

## 기술 스택

- Java 25
- Spring Boot 4.0 (WebFlux)
- LMAX Disruptor 4.0.0
- Netty (WebFlux 내장)

## 주의사항

### 데이터 내구성

현재 `fileChannel.force()` 호출이 비활성화되어 있습니다.
- **장점**: 최대 처리량
- **단점**: OS 크래시 시 일부 데이터 손실 가능

프로덕션에서 데이터 손실이 허용되지 않는 경우:
```java
// LogFileWriter.java - flush() 메서드
fileChannel.force(false);  // 주석 해제
```

### 버퍼 크기 튜닝

- **RingBuffer**: 트래픽 급증 대비 여유있게 설정
- **ByteBuffer**: 로그 크기 * 예상 배치 수

```yaml
# 고트래픽 환경 예시
logstream:
  disruptor:
    buffer-size: 4194304  # 4M
  file:
    buffer-size: 16777216 # 16MB
```
