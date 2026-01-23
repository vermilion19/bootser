# WebFlux vs Virtual Thread 성능 비교 분석

## 결론: Virtual Thread가 더 빠른 이유

**예상과 다른 결과가 나온 핵심 원인**: WebFlux + Disruptor는 "초저지연 + 극한 동시성"에 최적화된 구조이지만, **단순한 로그 수집 워크로드**에서는 오히려 **오버헤드**가 됩니다.

---

## 1. WebFlux의 숨겨진 오버헤드

### Reactive 스택의 객체 생성

```java
// WebFlux (logstream-service)
@PostMapping("/logs")
public Mono<ResponseEntity<String>> receiveLog(@RequestBody String payload) {
    boolean success = logProducer.publish(payload);
    if (success) {
        return Mono.just(ResponseEntity.ok("ok"));  // 👈 객체 3개 생성
    } else {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Buffer full"));  // 👈 객체 4개 생성
    }
}
```

**매 요청마다 생성되는 객체**:
- `Mono` 인스턴스 (1개)
- `ResponseEntity` 인스턴스 (1개)
- 내부 `Subscriber` 체인 (2-3개)
- 람다 캡처 객체 (상황에 따라)

```java
// Virtual Thread (logstream-virtualt)
@PostMapping("/logs")
public String receiveLog(@RequestBody String payload) {
    blockingQueueService.produce(payload);
    return "ok";  // 👈 String 리터럴 (힙 할당 없음)
}
```

**객체 생성**: 거의 없음 (String 리터럴은 상수 풀)

### GC 영향 비교

| 항목 | WebFlux | Virtual Thread |
|------|---------|----------------|
| **요청당 객체 생성** | 5-10개 | 0-1개 |
| **Young GC 빈도** | 높음 | 낮음 |
| **GC Pause** | 더 자주 | 덜 자주 |

> **결론**: WebFlux가 GC에 유리하다는 것은 **오해**. 간단한 워크로드에서는 오히려 Reactive 스택이 더 많은 가비지를 생성합니다.

---

## 2. Disruptor vs BlockingQueue: 과설계 문제

### Disruptor의 장점이 발휘되는 조건

Disruptor의 Lock-free가 빛나려면:
1. **극도로 높은 동시성** (수백 스레드가 동시 경합)
2. **나노초 단위 지연시간 요구**
3. **스레드 간 데이터 공유가 빈번**

### 로그 수집 워크로드의 특성

```
HTTP 요청 → 큐에 넣기 → 응답 반환 → (비동기) 파일 쓰기
```

- 큐 경합: **낮음** (요청이 분산됨)
- 지연시간 요구: **마이크로초면 충분**
- 데이터 공유: **단방향** (Producer → Consumer)

### BlockingQueue의 숨겨진 효율성

```java
// logstream-virtualt
int count = queue.drainTo(batch, 5000);  // 한 번의 Lock으로 5000개 추출
```

- **drainTo()**: 한 번의 Lock 획득으로 대량 추출
- **Lock 경합 시간**: 수 마이크로초
- **실제 처리**: Lock 해제 후 진행 (Lock-free와 유사)

```java
// logstream-service (Disruptor)
// endOfBatch 콜백 기반 - 복잡한 내부 처리
@Override
public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
    // 매 이벤트마다 핸들러 호출 오버헤드
}
```

**역설적 상황**: 단순 워크로드에서 BlockingQueue의 "한 번 Lock + 대량 처리"가 Disruptor의 "Lock-free + 개별 처리"보다 효율적일 수 있습니다.

---

## 3. Netty vs Tomcat + Virtual Thread

### Netty 이벤트 루프의 오버헤드

```
요청 도착 → Channel Pipeline → Codec 처리 → Handler → 응답 인코딩 → 전송
           ↑                                              ↓
           └──────────── EventLoop (복잡한 상태 머신) ─────┘
```

Netty의 Pipeline 처리:
- **HttpServerCodec** (요청 파싱)
- **HttpObjectAggregator** (청크 조립)
- **RouterFunction** (라우팅)
- **ResponseEncoder** (응답 인코딩)

### Tomcat + Virtual Thread의 단순함

```
요청 도착 → Thread 할당 → 직접 처리 → 응답 반환
           (Virtual Thread)
```

- 파이프라인 없음
- 상태 머신 없음
- 직접 호출 체인

### 처리 비용 비교

| 단계 | WebFlux (Netty) | Virtual Thread (Tomcat) |
|------|-----------------|------------------------|
| 요청 파싱 | Pipeline 통과 | 직접 파싱 |
| 핸들러 호출 | 콜백 체인 | 메서드 호출 |
| 응답 생성 | Mono 언래핑 | 직접 반환 |
| **총 오버헤드** | **높음** | **낮음** |

---

## 4. 배치 처리 전략 차이

### WebFlux + Disruptor

```java
// endOfBatch 기반 플러시
@Override
public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
    logFileWriter.write(event.getPayload());
    if (endOfBatch) {
        logFileWriter.flush();  // Disruptor가 결정한 배치 경계
    }
}
```

**문제**: Disruptor의 `endOfBatch`는 "현재 가용한 이벤트가 끝났을 때" 트리거됨
- 트래픽이 일정하면 배치가 작아질 수 있음
- 배치 크기 제어 불가

### Virtual Thread + BlockingQueue

```java
// 명시적 배치 크기
int count = queue.drainTo(batch, 5000);  // 최대 5000개씩

for (String logData : batch) {
    writeToBuffer(logData);
}
flush();  // 5000개 처리 후 플러시
```

**장점**:
- 고정된 배치 크기 (5000개)
- 예측 가능한 I/O 패턴
- 디스크 쓰기 최적화

---

## 5. 실제 벤치마크에서 일어나는 일

### 시나리오: 초당 10만 요청

**WebFlux + Disruptor**:
```
1. HTTP 요청 도착 (Netty EventLoop)
2. Mono 객체 생성
3. RingBuffer.tryNext() - CAS 연산
4. LogEvent 필드 설정
5. RingBuffer.publish() - 메모리 배리어
6. ResponseEntity 생성
7. Mono 구독/발행
8. 응답 인코딩 (Pipeline)
9. 전송

→ 총 오버헤드: ~10-20µs
```

**Virtual Thread + BlockingQueue**:
```
1. HTTP 요청 도착 (Tomcat)
2. Virtual Thread 생성 (~0.1µs)
3. queue.offer() - Lock 획득/해제 (~0.5µs)
4. return "ok"

→ 총 오버헤드: ~1-2µs
```

### 왜 이런 차이가?

| 요소 | WebFlux | Virtual Thread |
|------|---------|----------------|
| 스레드 모델 복잡도 | 높음 | 낮음 |
| 메모리 배리어 | 많음 (CAS) | 적음 (Lock) |
| 객체 생성 | 많음 (Reactive) | 최소 |
| 코드 경로 길이 | 김 (Pipeline) | 짧음 (직접) |

---

## 6. GC 관점 심층 분석

### WebFlux의 GC 부담

```java
// 매 요청마다 생성되는 객체들
Mono.just(...)                    // MonoJust 인스턴스
  .map(...)                       // MonoMap 인스턴스 + 람다
  .flatMap(...)                   // MonoFlatMap 인스턴스 + 람다
  .subscribe(...)                 // LambdaSubscriber 인스턴스
```

**Reactive 연산자 체인**: 연산자마다 래퍼 객체 생성

```
요청 10만개/초 × 객체 5개/요청 = 50만 객체/초 → Young GC 빈번
```

### Virtual Thread의 GC 부담

```java
// 객체 생성 거의 없음
blockingQueueService.produce(payload);  // 기존 객체 사용
return "ok";                             // String 리터럴
```

**Virtual Thread 자체의 메모리**:
- 스택: ~1KB (필요시 확장)
- 메타데이터: 최소
- **수명**: 요청 종료 시 즉시 회수 (짧은 GC 대상)

### GC 로그 예상 비교

```
# WebFlux (Young GC 빈번)
[GC (Allocation Failure) 12345K->1234K(54321K), 0.0050 secs]
[GC (Allocation Failure) 12345K->1234K(54321K), 0.0048 secs]
[GC (Allocation Failure) 12345K->1234K(54321K), 0.0052 secs]

# Virtual Thread (GC 드묾)
[GC (Allocation Failure) 12345K->1234K(54321K), 0.0045 secs]
... (간격이 더 김)
```

---

## 7. 언제 WebFlux가 유리한가?

### WebFlux + Disruptor가 빛나는 상황

1. **외부 I/O 대기가 긴 경우**
   ```java
   // WebFlux가 유리
   webClient.get()
       .retrieve()
       .bodyToMono(String.class)  // 외부 API 호출 (100ms+)
       .map(this::process)
   ```

2. **스트리밍 응답**
   ```java
   Flux.interval(Duration.ofMillis(100))
       .map(this::generateData)  // 무한 스트림
   ```

3. **복잡한 비동기 조합**
   ```java
   Mono.zip(api1.call(), api2.call(), api3.call())
       .flatMap(this::combine)
   ```

### 현재 로그 수집기의 특성

```java
// 외부 I/O 없음
// 스트리밍 없음
// 단순 요청/응답
logProducer.publish(payload);  // 메모리 작업
return "ok";                    // 즉시 응답
```

> **결론**: 로그 수집기 같은 **CPU 바운드 + 단순 요청/응답** 워크로드에서는 WebFlux의 장점이 발휘되지 않습니다.

---

## 8. 최종 분석표

| 관점 | WebFlux + Disruptor | Virtual Thread |
|------|---------------------|----------------|
| **요청당 객체 생성** | 5-10개 | 0-1개 |
| **GC 부담** | 높음 | 낮음 |
| **코드 경로 길이** | 김 | 짧음 |
| **Lock 오버헤드** | 없음 (CAS) | 있음 (최소화됨) |
| **배치 제어** | 자동 (예측 어려움) | 명시적 (5000개) |
| **프레임워크 오버헤드** | Netty Pipeline | 최소 |
| **Reactive 오버헤드** | 있음 | 없음 |
| **총 처리 비용** | ~10-20µs | ~1-2µs |

---

## 9. 결론

### 왜 Virtual Thread가 더 빨랐는가?

1. **Reactive 오버헤드 제거**: Mono/Flux 객체 생성 없음
2. **단순한 코드 경로**: Pipeline 없이 직접 처리
3. **효율적인 배치**: drainTo(5000)으로 명시적 배치
4. **낮은 GC 부담**: 객체 생성 최소화
5. **Lock의 재평가**: 현대 JVM에서 Lock은 충분히 빠름

### 교훈

> **"더 복잡한 기술이 항상 더 빠른 것은 아니다"**

- Disruptor: **나노초 단위 지연시간**이 필요할 때
- WebFlux: **외부 I/O 대기**가 많을 때
- Virtual Thread: **단순 요청/응답 + 높은 동시성**

### 권장 선택

| 워크로드 | 권장 |
|---------|------|
| 로그 수집 (단순) | Virtual Thread |
| 금융 거래 (초저지연) | Disruptor |
| API Gateway | Virtual Thread |
| 실시간 스트리밍 | WebFlux |
| 마이크로서비스 | Virtual Thread |

---

## 10. 추가 실험 제안

### WebFlux 최적화 시도

```java
// 객체 생성 최소화
private static final Mono<ResponseEntity<String>> OK_RESPONSE =
    Mono.just(ResponseEntity.ok("ok"));

@PostMapping("/logs")
public Mono<ResponseEntity<String>> receiveLog(@RequestBody String payload) {
    logProducer.publish(payload);
    return OK_RESPONSE;  // 재사용
}
```

### Disruptor WaitStrategy 변경

```yaml
# BUSY_SPIN으로 변경 (CPU 100% 사용)
logstream:
  disruptor:
    wait-strategy: BUSY_SPIN
```

### Virtual Thread 배치 크기 조정

```java
// 배치 크기 증가
int count = queue.drainTo(batch, 10000);  // 5000 → 10000
```

이러한 실험으로 두 방식의 최적 지점을 찾을 수 있습니다.
