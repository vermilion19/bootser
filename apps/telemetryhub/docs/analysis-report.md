# TelemetryHub 종합 분석 보고서

> 분석 일시: 2026-04-24  
> 분석 범위: `apps/telemetryhub` 전체 (동시성 / 아키텍처·Scale-out / 로직 정합성)

---

## 전체 요약

구조 방향(MSA + Kafka 비동기 경계 + contracts 분리)은 올바르게 잡혀 있다.  
다만 **MQTT 다중 인스턴스 시 파이프라인 붕괴**, **stream-processor DB write 정합성**, **동시성 버그** 세 축에 걸쳐 운영 투입 전 반드시 수정해야 할 문제들이 집중되어 있다.

| Planning.md 목표 | 달성도 | 주요 갭 |
|-----------------|--------|---------|
| 신뢰성 (유실/중복/순서) | 30% | dedup 미구현, DLQ 없음, MQTT 중복 구독 |
| 확장성 (throughput/latency) | 40% | sink 건별 upsert, MQTT clientId 문제, partition 전략 부재 |
| 재현성 (backfill) | 20% | 파일 전체 로드 OOM, 멱등 없음, 실시간/배치 write 충돌 |
| 관측 가능성 | 60% | Kafka lag, DLQ, backfill 재시도 메트릭 부재 |

---

## 치명적 이슈 (즉시 수정)

### 1. MQTT 다중 인스턴스 시 파이프라인 붕괴

**파일**: `ingestion-service/infrastructure/MqttInboundSubscriberLifecycle.java:66`  
**파일**: `ingestion-service/config/IngestionMqttSubscriberProperties.java:14`

두 가지 문제가 겹쳐 있다.

**clientId 중복**  
모든 인스턴스가 `"telemetryhub-ingestion-service"` 고정값을 사용한다.  
MQTT 스펙상 동일 clientId로 connect하면 브로커가 기존 연결을 강제 종료한다.  
인스턴스를 2대로 늘리는 순간 서로 쫓아내는 connect flapping이 발생한다.

**일반 구독**  
여러 인스턴스가 같은 토픽을 일반 구독하면 모든 인스턴스가 동일 메시지를 수신한다.  
Kafka에 N배 중복 publish가 발생한다.

```java
// 수정: clientId에 suffix 추가
String clientId = properties.getClientId() + "-" + UUID.randomUUID();

// 수정: shared subscription 사용 (Mosquitto 2.0 지원)
"$share/telemetryhub-ingestion/telemetryhub/devices/+/telemetry"
```

**현재 vs 개선 흐름**

```
[현재 - 2개 인스턴스]
Device → Broker → Ingestion#1 (clientId=A) → Kafka (중복!)
               → Ingestion#2 (clientId=A) → Kafka (clientId 충돌로 서로 쫓아내기도 함)

[개선 후]
Device → Broker →($share/grp)→ Ingestion#1 (cid=A-1) → Kafka
                               Ingestion#2 (cid=A-2)
               (브로커가 group 내 1대에게만 전달 = 부하 분산, 중복 없음)
```

---

### 2. backfill과 실시간 write의 데이터 충돌

**파일**: `batch-backfill/infrastructure/DeviceLastSeenBackfillWriter.java:47-79`  
**파일**: `stream-processor/infrastructure/JpaDeviceLastSeenProjectionWriter.java:29-47`

backfill과 stream-processor가 동일 테이블에 동시에 write하는데 "누가 이겨야 하는지" 정책이 없다.  
backfill upsert에 `event_time` 비교 조건이 없어 최신 실시간 값을 과거 backfill 값이 덮어쓸 수 있다.

추가로 `DeviceLastSeenBackfillWriter.java:60`에서 복사 오류가 있다.

```java
// 현재 (잘못됨)
Timestamp.from(event.eventTime()),   // last_event_time
Timestamp.from(event.eventTime()),   // last_ingest_time ← eventTime으로 잘못 복사
```

`last_ingest_time`이 `eventTime`으로 저장되어 네트워크 지연 측정값이 전부 0으로 보인다.

```sql
-- 수정: 최신성 비교 조건 추가
ON CONFLICT (device_id) DO UPDATE SET ...
WHERE excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
```

---

### 3. KafkaIngestionPublisher Lost Update

**파일**: `ingestion-service/infrastructure/KafkaIngestionPublisher.java:40-83`

```java
// 현재 (잘못됨): get → 조합 → set (check-then-act)
KafkaPublishSnapshot current = snapshotRef.get();
snapshotRef.set(new KafkaPublishSnapshot(current.totalPublished() + 1, ...));

// 수정: updateAndGet으로 원자화
snapshotRef.updateAndGet(current ->
    new KafkaPublishSnapshot(current.totalPublished() + 1, ...));
```

HTTP 스레드 + MQTT 콜백 스레드가 동시에 `publish()`를 호출하면 두 스레드 모두 같은 `current`를 읽고 각자 +1로 set → Lost Update 발생. 운영 지표 신뢰도 붕괴.

---

### 4. DeviceSimulatorControlService 상태 전이 비원자

**파일**: `device-simulator/application/DeviceSimulatorControlService.java:32-97`

`runtimeState.get() → 필드 재조합 → runtimeState.set(next) → loopService.start/refresh`의 2단계 패턴이 `start`, `stop`, `scale`, `applyScenario` 4개 메서드 전부에 존재한다.

두 관리자가 동시에 `scale(100)`과 `applyScenario(FAULT, 50)`을 호출하면 한 변경이 소실된다.  
"상태는 STOPPED인데 루프는 살아있는" 상황도 발생할 수 있다.

```java
// 수정: 제어 메서드에 synchronized 추가 (호출 빈도 낮아 성능 영향 없음)
public synchronized SimulatorRuntimeState start(...) { ... }
public synchronized SimulatorRuntimeState stop() { ... }
public synchronized SimulatorRuntimeState scale(int devices) { ... }
public synchronized SimulatorRuntimeState applyScenario(...) { ... }
```

---

### 5. InMemory 버퍼 비원자 trim → burst 시 OOM

**파일**: `device-simulator/infrastructure/InMemoryPublishedMessageStore.java:21-33`  
**파일**: `ingestion-service/infrastructure/InMemoryNormalizedRawEventStore.java:22-31`

```java
// 현재 (잘못됨)
messages.addFirst(msg);
while (messages.size() > max) messages.pollLast(); // ConcurrentLinkedDeque.size()는 O(n)
```

- `ConcurrentLinkedDeque.size()`가 O(n)이라 고빈도 append에서 CPU 핫스팟
- addFirst + size + pollLast가 비원자 → burst 시 두 스레드가 동시에 addFirst만 쌓으면 max를 크게 초과한 채 메모리 성장 가능

```java
// 수정: capacity 제한 LinkedBlockingDeque 사용
private final LinkedBlockingDeque<T> messages = new LinkedBlockingDeque<>(maxSize);

public void append(T msg) {
    if (!messages.offerFirst(msg)) {
        messages.pollLast();
        messages.offerFirst(msg);
    }
}
```

---

### 6. JpaProjectionWriter - 트랜잭션 없음 + out-of-order 덮어쓰기

**파일**: `stream-processor/infrastructure/JpaDeviceLastSeenProjectionWriter.java:24-53`

- `@Transactional` 없음. Kafka Streams rebalance 중 두 stream thread가 같은 deviceId를 동시에 upsert하면 `last_event_time` 역전 가능.
- `ON CONFLICT DO UPDATE`에 최신성 비교 조건이 없어 out-of-order 이벤트가 최신 값을 덮어씀.

```sql
-- 수정: 최신성 비교 조건 추가
INSERT INTO telemetryhub_device_last_seen (...)
VALUES (...)
ON CONFLICT (device_id) DO UPDATE SET
    last_event_time = EXCLUDED.last_event_time,
    ...
WHERE EXCLUDED.last_event_time > telemetryhub_device_last_seen.last_event_time
```

---

## 높음 이슈 (단기 수정)

### 7. Kafka producer 설정 전무

**파일**: `ingestion-service/src/main/resources/application.yml`

`acks`, `enable.idempotence`, `retries`, `linger.ms`, `compression.type` 설정이 하나도 없다.  
Spring Kafka 기본값에 의존 → IoT 규모에서 신뢰성/처리량 모두 보장 불가.

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 5
        compression.type: lz4
```

---

### 8. Kafka send() 콜백 누락

**파일**: `ingestion-service/infrastructure/KafkaIngestionPublisher.java:58-86`

`KafkaTemplate.send()`의 Future를 버리고 즉시 success 메트릭을 증가시킨다.  
broker ack 전에 성공으로 집계되고, async 실패는 전혀 감지하지 못한다.

```java
// 수정
stringKafkaTemplate.send(topic, key, payload)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            snapshotRef.updateAndGet(current -> /* failure snapshot */);
        } else {
            snapshotRef.updateAndGet(current -> /* success snapshot */);
        }
    });
```

---

### 9. MQTT 콜백 스레드에서 Kafka 동기 send

**파일**: `ingestion-service/infrastructure/MqttInboundSubscriberLifecycle.java:157-167`

Paho `messageArrived` 콜백 스레드가 `IngestionService.ingest()` → `KafkaTemplate.send()` 동기 경로를 직접 실행한다.  
Kafka 브로커 지연 시 MQTT 콜백 스레드가 블로킹 → 후속 메시지 수신 지연/유실.  
QoS 1에서는 ACK 지연으로 브로커가 재전송 → 중복 수신.

```java
// 수정: 콜백에서 queue에만 enqueue, 별도 워커에서 처리
private final LinkedBlockingQueue<MqttMessage> inboundQueue = new LinkedBlockingQueue<>(10_000);

@Override
public void messageArrived(String topic, MqttMessage message) {
    inboundQueue.offer(message); // non-blocking
}

// 별도 워커 스레드 (또는 Virtual Thread pool)
workerPool.execute(() -> {
    MqttMessage msg = inboundQueue.poll();
    mqttInboundAdapter.receive(...);
});
```

---

### 10. stream-processor sink 건별 upsert (처리량 병목)

**파일**: `stream-processor/infrastructure/JpaDeviceLastSeenProjectionWriter.java` 외 3개 writer

Kafka Streams `foreach`에서 레코드 1건마다 1 SQL round-trip이 발생한다.  
10만 대 디바이스 × 1Hz = 초당 10만 upsert 요구인데 PostgreSQL 단일 테이블 upsert 상한은 보통 5k~10k/s.

```java
// 수정: Punctuator로 N건 배치 후 batchUpdate
context.schedule(Duration.ofSeconds(1), PunctuationType.WALL_CLOCK_TIME, timestamp -> {
    if (!buffer.isEmpty()) {
        jdbcTemplate.batchUpdate(SQL, buffer);
        buffer.clear();
    }
});
```

---

### 11. stream-processor state-dir이 tmpdir

**파일**: `stream-processor/src/main/resources/application.yml:40`

```yaml
# 현재 (잘못됨)
state-dir: ${java.io.tmpdir}/telemetryhub-streams
num-standby-replicas: 0
```

컨테이너 재시작 시 state store가 전부 휘발되어 changelog topic에서 전량 restore가 필요하다.  
`standby=0`이라 인스턴스 장애 복구에 수 분~수십 분 소요.

```yaml
# 수정
state-dir: /var/lib/telemetryhub-streams   # PV 마운트 필요
num-standby-replicas: 1
```

---

### 12. batch-backfill 파일 전체 메모리 로드

**파일**: `batch-backfill/infrastructure/FileBackfillSourceReader.java:36-49`

```java
// 현재 (잘못됨): 파일 전체를 메모리에 로드
return Files.lines(sourcePath).toList();
```

raw archive가 GB 단위면 즉시 OOM.

```java
// 수정: Spring Batch FlatFileItemReader + chunk 처리
@Bean
public FlatFileItemReader<BackfillRawEvent> backfillReader() {
    return new FlatFileItemReaderBuilder<BackfillRawEvent>()
        .name("backfillReader")
        .resource(new FileSystemResource(sourcePath))
        .lineMapper(new BackfillRawEventLineMapper())
        .build();
}
```

---

### 13. MqttInboundSubscriberLifecycle client 필드 non-volatile

**파일**: `ingestion-service/infrastructure/MqttInboundSubscriberLifecycle.java:38`

`start()`(컨테이너 스레드)에서 대입하고 `stop()`(shutdown 스레드), Paho 내부 스레드에서 읽는다.  
가시성 보장 필요. 또한 `connectionLost` 콜백에서 재연결 로직이 없어 한 번 끊기면 `DEGRADED` 고정.

```java
// 수정
private volatile MqttClient client;
```

---

### 14. DeviceSimulatorLoopService start/stop 경합

**파일**: `device-simulator/application/DeviceSimulatorLoopService.java:30-53`

두 관리자가 동시에 `start()`를 호출하면 Executor에 두 개의 future가 등록되어 루프가 2배 빈도로 실행된다.

```java
// 수정: start/stop/refresh를 synchronized로 보호
public synchronized void start(SimulatorRuntimeState state) { ... }
public synchronized void stop() { ... }
public synchronized void refresh(SimulatorRuntimeState state) { ... }
```

---

### 15. analytics-api 연결 풀/캐시 미설정

**파일**: `analytics-api/src/main/resources/application.yml`

datasource/hikari 설정이 없어 기본 pool(10)으로 scale-out 시 DB connection 폭증.  
단순 조회 API임에도 캐시가 전혀 없어 동일 쿼리 반복 시 DB 부담.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      read-only: true
```

---

## 중간 이슈 (중기 개선)

### 16. Grace period / Late event 처리 미구현

Planning.md 9.2에서 "늦게 도착하는 이벤트는 grace period 내 반영"이라 명시했으나  
모든 topology에서 `.grace(Duration)` API 미사용. 5분 이상 지연된 이벤트는 집계에서 누락.

```java
// 수정: grace period 설정 예시
TimeWindows windows = TimeWindows
    .ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofMinutes(5));
```

---

### 17. eventId 기반 dedup 없음

Planning.md 5.2 "consumer는 멱등 처리 전제"가 미충족.  
같은 eventId 이벤트가 2번 도착하면 `EventsPerMinuteTopology`에서 카운트 2 증가.

**수정 방향**: Kafka Streams에서 eventId 기반 KTable dedup 또는 ingestion 단계 Redis SETNX.

---

### 18. DLQ 없음

normalize/publish 실패 이벤트가 버려진다.  
stream-processor에서 DB write 예외 발생 시 at_least_once 보장에 의해 무한 재시도 루프 위험.

**수정 방향**: DLQ 토픽 (`telemetryhub.dlq`) 추가 + projection writer에서 예외 시 DLQ produce.

---

### 19. BackfillRawEvent에 ingestTime 필드 누락

**파일**: `batch-backfill/application/BackfillRawEvent.java`

contracts의 `EventMetadata`는 `ingestTime`을 포함하지만 backfill 처리 시 손실된다.  
이슈 #2의 복사 오류와 직결된다.

```java
// 수정: ingestTime 필드 추가
record BackfillRawEvent(
    EventType eventType,
    String eventId,
    String deviceId,
    Instant eventTime,
    Instant ingestTime,  // 추가
    String payload
) {}
```

---

### 20. Kafka 파티션 전략 미문서화

**파일**: `ingestion-service/infrastructure/KafkaEventKeyResolver.java`

key=deviceId 전략은 순서 보존에 맞지만 hot device 존재 시 hotspot 발생.  
파티션 수 결정 기준 문서 없음.

| 변수 | 권장 산식 |
|------|-----------|
| 파티션 수 | `ceil(peak msg/s ÷ consumer 처리량) × 2`, 최소 12 |
| replication.factor | 운영 3, dev 1 |
| min.insync.replicas | 2 (acks=all과 함께) |

단일 `raw-events` 토픽에 모든 eventType이 섞여 있어 eventType별 토픽 분리도 고려할 것.

---

## 누락된 요구사항 (Planning.md 대비)

| 요구사항 | 현황 | 비고 |
|----------|------|------|
| Grace period / late event | ❌ 미구현 | 모든 topology에서 `.grace()` 없음 |
| eventId 기반 dedup | ❌ 미구현 | Planning.md 5.2 명시 |
| DLQ / 재처리 자동화 | ❌ 미구현 | v2 로드맵이지만 위험 높음 |
| Schema Registry (Avro/Protobuf) | ❌ 미구현 | 현재 JSON 무방비 |
| Kafka consumer lag 메트릭 | ❌ 없음 | Prometheus에 미노출 |
| backfill 실행 이력 테이블 | ❌ 없음 | 같은 파일 2번 실행 시 2배 write |

---

## 우선순위 요약

| 순위 | 이슈 | 파일 | 영향 |
|------|------|------|------|
| 1 | MQTT clientId 중복 + 공유구독 미사용 | `MqttInboundSubscriberLifecycle.java` | 인스턴스 2대 = 파이프라인 붕괴 |
| 2 | backfill ↔ 실시간 write 충돌 + ingestTime 복사 오류 | `DeviceLastSeenBackfillWriter.java:60` | 데이터 역행, 지연 측정 왜곡 |
| 3 | KafkaIngestionPublisher Lost Update | `KafkaIngestionPublisher.java:40-83` | 운영 지표 신뢰 불가 |
| 4 | ControlService 상태 전이 비원자 | `DeviceSimulatorControlService.java` | 루프 중복 실행 |
| 5 | InMemory 버퍼 비원자 trim | `InMemoryPublishedMessageStore.java` | burst 시 OOM |
| 6 | ProjectionWriter @Transactional 없음 + 최신성 비교 없음 | `JpaDeviceLastSeenProjectionWriter.java` | 집계값 역전 |
| 7 | Kafka producer 설정 전무 | `ingestion-service/application.yml` | 유실, 처리량 한계 |
| 8 | send() 콜백 없음 | `KafkaIngestionPublisher.java:58` | 비동기 실패 무시 |
| 9 | MQTT 콜백에서 Kafka 동기 send | `MqttInboundSubscriberLifecycle.java:157` | MQTT inbound 블로킹 |
| 10 | stream-processor sink 건별 upsert | `JpaDeviceLastSeenProjectionWriter.java` | 처리량 병목 |
| 11 | state-dir=tmpdir + standby=0 | `stream-processor/application.yml` | 장애 복구 수 분~ |
| 12 | backfill 파일 전체 로드 | `FileBackfillSourceReader.java:36` | GB 파일 즉시 OOM |

---

## 단계별 수정 로드맵

### Phase 1 — scale-out 전 필수 (설정/소규모 변경)
- [ ] MQTT clientId suffix + shared subscription 전환 (이슈 #1)
- [ ] Kafka producer acks/idempotence/compression 설정 (이슈 #7)
- [ ] stream-processor state-dir PV 마운트 + standby=1 (이슈 #11)
- [ ] send() 콜백 처리 (이슈 #8)

### Phase 2 — 실제 scale 병목 해결 (로직 변경)
- [ ] KafkaIngestionPublisher `updateAndGet` 원자화 (이슈 #3)
- [ ] ControlService / LoopService `synchronized` 추가 (이슈 #4, #14)
- [ ] InMemory 버퍼 `LinkedBlockingDeque` 전환 (이슈 #5)
- [ ] ProjectionWriter `@Transactional` + WHERE 조건 추가 (이슈 #6)
- [ ] MQTT 콜백 → inbound queue 분리 (이슈 #9)
- [ ] sink Punctuator 기반 배치 upsert (이슈 #10)

### Phase 3 — 운영 안정화 (구조 추가)
- [ ] backfill ingestTime 필드 추가 + write 충돌 정책 정의 (이슈 #2, #19)
- [ ] backfill FlatFileItemReader + chunk 처리 (이슈 #12)
- [ ] Grace period `.grace(Duration.ofMinutes(5))` 추가 (이슈 #16)
- [ ] eventId 기반 dedup (이슈 #17)
- [ ] DLQ 토픽 + retry 파이프라인 (이슈 #18)
- [ ] analytics-api Hikari pool + Redis 캐시 레이어 (이슈 #15)
- [ ] Kafka consumer lag Prometheus 메트릭 노출