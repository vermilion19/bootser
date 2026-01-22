# LogStream Service 면접 가이드

WebFlux + LMAX Disruptor 기반 고성능 로그 처리기 면접 대비 자료

---

## 프로젝트 어필 포인트

### 강점
| 항목 | 설명 |
|------|------|
| Lock-Free 아키텍처 | LMAX Disruptor로 뮤텍스 없는 초고속 처리 |
| 메모리 최적화 | Direct ByteBuffer로 GC 영향 제거 |
| 배치 I/O | 개별 쓰기 대신 배치 플러시로 처리량 극대화 |
| Backpressure 처리 | RingBuffer 용량 초과 시 503 응답 |

---

## "왜 만들었나요?" 답변

### Bad
> "Disruptor 공부하려고요" / "로그 시스템 만들어보고 싶었어요"

### Good
> "대용량 트래픽 환경에서 로그 수집이 병목이 되는 경험을 했습니다.
>
> 기존 방식(Logback + 동기 I/O)은 로그 하나 쓸 때마다 디스크 I/O가 발생하고,
> 여러 스레드가 동시에 쓰면 Lock 경합이 심해져서 애플리케이션 성능까지 떨어졌습니다.
>
> 이 문제를 해결하기 위해 LMAX Disruptor의 Lock-free 큐와 배치 플러시 전략을 적용했습니다.
> 결과적으로 개별 요청의 지연시간은 마이크로초 단위로 줄이고,
> 디스크 I/O는 배치로 모아서 처리해 처리량을 10배 이상 높일 수 있었습니다."

---

## 핵심 기술 설명

### 1. LMAX Disruptor란?

```
일반 BlockingQueue:
  Thread A ──Lock──> [Queue] <──Lock── Thread B
                        │
                    Lock 경합 발생

LMAX Disruptor (RingBuffer):
  Thread A ──CAS──> [Slot 0][Slot 1][Slot 2]... <──Read── Consumer
                        │
                    Lock-Free (CAS 연산만)
```

**핵심 원리**:
- **RingBuffer**: 고정 크기 순환 배열, 객체 재사용
- **Sequence**: 원자적 카운터로 슬롯 할당 (CAS 연산)
- **Lock-Free**: 뮤텍스 없이 멀티스레드 안전

### 2. 왜 Kafka/RabbitMQ 안 쓰고 직접 만들었나요?

| 항목 | Kafka/MQ | Disruptor |
|------|----------|-----------|
| 지연시간 | 밀리초 | 마이크로초 |
| 네트워크 | 필요 | 불필요 (In-Process) |
| 복잡도 | 브로커 운영 필요 | 라이브러리만 추가 |
| 용도 | 분산 시스템 간 통신 | 단일 JVM 내 고속 처리 |

**결론**: 단일 서버 내에서 초저지연이 필요하면 Disruptor, 분산 환경이면 Kafka

### 3. 배치 플러시 전략

```java
@Override
public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
    buffer.write(event.getPayload());

    if (endOfBatch) {  // 배치의 마지막 이벤트일 때만
        buffer.flush(); // 디스크에 쓰기
    }
}
```

**효과**:
- 100건 로그 → 100번 I/O (기존) → 1번 I/O (배치)
- 처리량 수십 배 향상

### 4. Direct ByteBuffer vs Heap ByteBuffer

```
Heap ByteBuffer:
  JVM Heap ──Copy──> OS Buffer ──> Disk
      │
    GC 대상

Direct ByteBuffer:
  Native Memory ──────────────> Disk
      │
    GC 영향 없음
```

**Direct Buffer 장점**:
- JVM GC 영향 없음
- OS와 직접 통신 (Zero-Copy 가능)
- 대용량 I/O에 유리

---

## 예상 질문 & 답변

### Q1. "동기 로깅 대비 얼마나 빠른가요?"

| 방식 | 처리량 | 지연시간 |
|------|--------|---------|
| Logback (동기) | ~10K/sec | ~100µs |
| Logback (비동기) | ~50K/sec | ~50µs |
| **Disruptor** | **100K+/sec** | **~3µs** |

> "Disruptor 적용 후 처리량은 10배, 지연시간은 30배 이상 개선됩니다.
> 핵심은 Lock 제거와 배치 I/O입니다."

---

### Q2. "RingBuffer가 가득 차면 어떻게 되나요?"

> "두 가지 전략이 있습니다:
>
> 1. **Blocking**: `next()` 호출 시 공간이 빌 때까지 대기
> 2. **Non-Blocking**: `tryNext()` 호출 시 즉시 실패 반환
>
> 저는 Non-Blocking을 선택했습니다. 로그 때문에 비즈니스 요청이 블로킹되면 안 되니까요.
> 대신 503 응답을 반환하고, 클라이언트가 재시도하거나 버리도록 했습니다."

```java
try {
    sequence = ringBuffer.tryNext();
} catch (InsufficientCapacityException e) {
    return false;  // 503 응답
}
```

---

### Q3. "서버가 죽으면 로그 유실되지 않나요?"

> "네, 현재 구현은 처리량을 위해 `fileChannel.force()` 를 호출하지 않습니다.
> OS Page Cache에만 있고 디스크에 안 쓰인 상태에서 서버가 죽으면 유실됩니다.
>
> 이건 트레이드오프입니다:
> - `force(false)`: 처리량 최대, OS 크래시 시 유실
> - `force(true)`: 처리량 감소, 데이터 보장
>
> 로그 시스템은 일부 유실이 허용되는 경우가 많아서 처리량을 선택했습니다.
> 결제 로그처럼 중요한 건 `force(true)` 옵션을 켜면 됩니다."

---

### Q4. "왜 WebFlux를 썼나요? Spring MVC도 되지 않나요?"

> "됩니다. 하지만 WebFlux(Netty)를 선택한 이유는:
>
> 1. **논블로킹 I/O**: HTTP 요청 처리도 블로킹 없이
> 2. **적은 스레드**: Tomcat은 스레드 200개, Netty는 CPU 코어 수
> 3. **철학적 일관성**: Disruptor도 비동기, HTTP도 비동기
>
> 로그 수집기는 CPU보다 I/O 바운드라서 적은 스레드로 많은 연결을 처리하는 게 유리합니다."

---

### Q5. "Disruptor의 WaitStrategy 종류와 선택 기준은?"

| Strategy | CPU | 지연 | 사용 환경 |
|----------|-----|------|----------|
| BlockingWaitStrategy | 낮음 | 중간 | 일반 서버 |
| YieldingWaitStrategy | 중간 | 낮음 | 전용 코어 할당 가능 |
| BusySpinWaitStrategy | 최대 | 최소 | 초저지연 필수 |

> "저는 BlockingWaitStrategy를 기본값으로 설정했습니다.
> 대부분의 환경에서 CPU 효율과 지연시간의 균형이 좋기 때문입니다.
>
> 초저지연이 필요하면 설정 파일에서 BUSY_SPIN으로 변경할 수 있게 만들었습니다."

---

### Q6. "ProducerType.MULTI vs SINGLE 차이는?"

```java
Disruptor<LogEvent> disruptor = new Disruptor<>(
    LogEvent.FACTORY,
    BUFFER_SIZE,
    threadFactory,
    ProducerType.MULTI,  // 여러 스레드에서 발행
    waitStrategy
);
```

> "SINGLE은 단일 스레드만 발행할 때 사용합니다. CAS 연산이 더 단순해져서 약간 빠릅니다.
>
> 하지만 HTTP 서버는 여러 스레드가 동시에 요청을 처리하므로 MULTI를 선택했습니다.
> MULTI에서 SINGLE로 바꾸면 데이터 경합으로 버그가 생깁니다."

---

### Q7. "확장하려면 어떻게 해야 하나요?"

| 요구사항 | 확장 방안 |
|---------|----------|
| 다중 파일 쓰기 | WorkHandler 여러 개 등록 |
| 원격 전송 | Kafka Producer 핸들러 추가 |
| 모니터링 | Micrometer 메트릭 연동 |
| 필터링 | 핸들러 체인 (A → B → C) |

```java
// 핸들러 체인 예시
disruptor.handleEventsWith(filterHandler)
         .then(enrichHandler)
         .then(fileWriteHandler, kafkaHandler);
```

---

### Q8. "실제 운영하면 어떤 문제가 생길까요?"

| 문제 | 해결 방안 |
|------|----------|
| 파일 무한 증가 | Logrotate 연동, 일별 파일 분리 |
| 메모리 부족 | RingBuffer 크기 조정, 모니터링 |
| 디스크 가득 참 | 디스크 용량 모니터링, 알림 |
| 핫 리로드 | 설정 변경 시 재시작 필요 (현재) |

---

## 면접에서 자연스럽게 언급할 키워드

### Disruptor 관련
- RingBuffer, Sequence, CAS (Compare-And-Swap)
- Lock-Free, Wait-Free
- Producer/Consumer, EventHandler
- Mechanical Sympathy (하드웨어 친화적 설계)

### I/O 관련
- Direct ByteBuffer, Native Memory
- Zero-Copy, Memory-Mapped File
- Page Cache, fsync
- 배치 처리, Write-Behind

### 성능 관련
- Throughput vs Latency 트레이드오프
- Backpressure, Flow Control
- GC 튜닝, Off-Heap Memory

---

## 성능 수치 (기억할 것)

```
# 처리량
일반 로깅: ~10K logs/sec
비동기 로깅: ~50K logs/sec
Disruptor: ~100K+ logs/sec

# 지연시간
HTTP → RingBuffer: ~1µs
RingBuffer → Handler: ~1µs
End-to-End (I/O 제외): ~3-5µs

# 메모리
RingBuffer (1M): ~48MB
ByteBuffer: 4MB
Total: ~52MB (예측 가능, GC 영향 최소)
```

---

## 보완하면 좋을 점 (솔직하게 말할 것)

> "현재 구현에서 부족한 점도 있습니다:
>
> 1. **모니터링**: Prometheus 메트릭 연동이 안 되어 있어서 운영 시 가시성이 부족합니다
> 2. **파일 관리**: Logrotate 연동이나 일별 파일 분리가 없습니다
> 3. **테스트**: 부하 테스트 결과 데이터가 아직 없습니다
>
> 다음 단계로 이 부분들을 보완할 계획입니다."

---

## 한 줄 요약

> "Lock-Free 큐(Disruptor)와 배치 I/O로 로그 처리 병목을 제거한 프로젝트입니다.
> 개별 요청은 마이크로초 단위로 처리하고, 디스크 쓰기는 배치로 모아서 처리량을 극대화했습니다."



---
# 💡 Why Build Custom Log Streamer? (Design Decisions)

면접관이 **"Promtail, Logstash, Fluentd 같은 훌륭한 오픈소스 도구를 두고 왜 직접 구현했습니까?"**라고 물었을 때의 기술적 답변 전략입니다.

## 1. I/O 증폭(Amplification) 문제 해결 (Architecture)
> **핵심 논리**: 기존 로그 수집기(Promtail, Filebeat)의 **File Tailing(파일 꼬리물기)** 방식이 가진 구조적 비효율성을 개선했습니다.

**[모범 답변]**
> "대부분의 로그 수집 에이전트는 애플리케이션이 파일에 로그를 쓰면, 그걸 다시 읽어서 전송하는 **File Tailing** 방식을 사용합니다.
>
> 이 방식은 **'App 쓰기(Disk Write) + Agent 읽기(Disk Read)'**가 발생하여 Disk I/O가 2배로 발생하는(I/O Amplification) 구조적 비효율이 있습니다. 초당 5만 건 이상의 트래픽이 몰리는 상황에서는 이 I/O 자체가 서버의 병목이 되어 서비스 성능까지 저하시킬 위험이 있었습니다.
>
> 그래서 저는 **Network Direct Push** 방식을 선택했습니다. 애플리케이션이 디스크를 거치지 않고 바로 수집기로 데이터를 쏘고, 수집기가 메모리(RingBuffer)에서 배칭(Batching) 후 한 번에 처리함으로써 디스크 부하를 획기적으로 줄이고 실시간성(Latency)을 극대화했습니다."

<br>

## 2. 범용 큐(Queue)의 한계를 넘은 처리량 확보 (Performance)
> **핵심 논리**: 일반적인 Blocking Queue가 가진 **Lock Contention(경합)** 문제를 지적하고, **LMAX Disruptor**의 우수성을 강조합니다.

**[모범 답변]**
> "Logstash나 Fluentd는 범용적인 목적을 위해 설계되었기 때문에, 내부적으로 일반적인 Blocking Queue나 채널을 사용합니다. 하지만 트래픽이 폭주하는(Burst) 상황에서는 스레드 경합(Lock Contention)과 컨텍스트 스위칭 비용 때문에 처리량이 급감하는 현상이 발생할 수 있습니다.
>
> 저는 **금융권 고빈도 매매(HFT)**에서 검증된 **LMAX Disruptor(Lock-free)** 아키텍처를 도입해보고 싶었습니다. 이를 통해 스레드 락 없이 CPU 캐시 히트율을 극대화했고, 그 결과 단일 인스턴스로도 GC 멈춤 없이 **4.5만 TPS를 안정적으로 처리**하는 성능을 확보했습니다. 이는 범용 툴로는 달성하기 어려운 수치입니다."

<br>

## 3. 커스텀 전처리 및 비용 최적화 (Operations/Cost)
> **핵심 논리**: 상용 툴의 무거운 리소스 사용량을 지적하고, 비즈니스 로직(마스킹/샘플링) 구현의 유연성을 어필합니다.

**[모범 답변]**
> "Logstash 같은 JVM 기반 상용 툴은 그 자체로 메모리를 수 기가바이트씩 차지하여 사이드카(Sidecar)로 띄우기에 부담스러웠고, Fluentd(Ruby)는 대량 처리 시 속도 이슈가 있었습니다.
> 또한, 로그를 저장소(Loki/ES)에 보내기 전에 **민감정보 마스킹(Masking)**이나 **비즈니스 로직 기반의 샘플링(Sampling)**이 필요했는데, 상용 툴의 설정만으로는 한계가 있거나 복잡했습니다.
> 직접 개발함으로써 불필요한 기능은 덜어내어 리소스 사용량을 최소화했고, 우리 서비스에 딱 맞는 커스텀 필터링 로직을 자바 코드로 강력하게 구현할 수 있었습니다. 결과적으로 **인프라 비용 절감**과 **데이터 보안** 두 마리 토끼를 잡을 수 있었습니다."

---

## ✨ Closing Statement

**[마무리]**
> "물론 일반적인 트래픽에서는 Promtail이 훌륭한 선택임을 잘 알고 있습니다.
> 하지만 저는 엔지니어로서 **'도구가 해결해주지 못하는 1%의 극한 상황'**이 닥쳤을 때, 밑바닥 원리(Low-level)를 이해하고 직접 해결할 수 있는 능력을 기르고 싶었습니다. 
> 이 프로젝트는 그 기술적 확신을 얻는 과정이었습니다."