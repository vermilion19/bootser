# TelemetryHub 면접 질문집

> IoT 텔레메트리 데이터 파이프라인 프로젝트 기반 시니어 개발자 면접 질문  
> 아키텍처, 동시성, 데이터 일관성, 장애 대응, 성능 최적화를 중심으로 구성

---

## 목차

1. [아키텍처 설계](#1-아키텍처-설계)
2. [MQTT & 메시지 수집](#2-mqtt--메시지-수집)
3. [Kafka & Kafka Streams](#3-kafka--kafka-streams)
4. [동시성 & 스레드 안전성](#4-동시성--스레드-안전성)
5. [데이터 일관성 & 순서 보장](#5-데이터-일관성--순서-보장)
6. [장애 시나리오 대응](#6-장애-시나리오-대응)
7. [성능 최적화](#7-성능-최적화)
8. [운영 & 모니터링](#8-운영--모니터링)
9. [배치 재처리](#9-배치-재처리)

---

## 1. 아키텍처 설계

### 난이도: ★★★☆☆

---

### Q1. MQTT → Kafka 이중 메시지 브로커를 사용한 이유가 무엇인가요?

> 이 시스템은 MQTT 브로커(Mosquitto)와 Kafka를 함께 사용합니다.  
> MQTT로 바로 수집하면 되는데 왜 굳이 Kafka까지 도입했나요?

**힌트**:
- MQTT와 Kafka 각각의 강점이 다릅니다
- 디바이스 수가 100만 대가 되면 어떻게 될지 생각해보세요

<details>
<summary>모범 답안</summary>

#### 역할 분리의 이유

| 역할 | MQTT | Kafka |
|------|------|-------|
| 대상 | IoT 디바이스 (저전력, 불안정) | 내부 서비스 간 통신 |
| 연결 수 | 수십만 ~ 수백만 | 수십 (Kafka consumer group) |
| 메시지 보관 | 없음 (최신 1개, retain=true) | 설정 가능 (7일 이상) |
| 재처리 | 불가 | Consumer offset으로 재소비 가능 |
| 스케일-아웃 | Shared subscription 제한 | 파티션 추가만으로 가능 |

#### 핵심 이유

1. **버퍼링**: MQTT는 메시지를 저장하지 않으므로, Kafka가 Stream Processor 처리 속도 편차를 흡수
2. **재처리 가능**: 장애 후 Kafka offset을 초기화하면 raw data를 처음부터 재소비 가능
3. **팬-아웃**: 동일한 raw event를 Stream Processor / 배치 잡 / 분석 시스템이 각각 독립적으로 소비 가능

</details>

---

### Q2. Stream Processor가 집계하는 4가지 뷰(DeviceLastSeen, EventsPerMinute, DrivingEventCounter, RegionHeatmap)를 PostgreSQL에 저장하는 대신 Kafka Streams의 Interactive Queries로 직접 제공하는 방법은?

> 현재 집계 결과를 PostgreSQL에 upsert한 뒤 Analytics API가 조회합니다.  
> Kafka Streams의 Interactive Queries API를 사용하면 DB 없이도 조회가 가능한데, 왜 PostgreSQL을 선택했을까요?

**힌트**:
- Interactive Queries는 특정 파티션이 담당하는 노드에 라우팅 필요
- 다운타임 중 데이터는 어디에?

<details>
<summary>모범 답안</summary>

#### Interactive Queries의 한계

1. **라우팅 복잡성**: key-partitioned 상태 저장소이므로, `deviceId=A`의 데이터는 파티션 N을 담당하는 노드에만 존재. 클라이언트가 직접 노드를 알아야 하거나, API 게이트웨이가 라우팅해야 함
2. **서비스 종속성**: Stream Processor가 다운되면 조회 API도 함께 다운
3. **집계 범위 제한**: 시간 범위 쿼리(`from~to`)는 상태 저장소 스캔이 O(n)으로 비효율

#### PostgreSQL 선택의 이점

- 분리된 가용성 (Stream Processor 재시작 중에도 API 서빙 가능)
- 인덱스 기반 고효율 범위 쿼리
- Backfill 재처리 결과와 실시간 결과 동일 테이블 사용 가능

</details>

---

### Q3. `ingestion-service`를 3대로 스케일-아웃할 때, 같은 MQTT 토픽에서 메시지를 중복 없이 나눠 처리하려면 어떻게 해야 하나요?

> 현재 설정 파일에 `shared-subscription-enabled: false`로 되어 있습니다.  
> 3대로 스케일-아웃하면 어떤 문제가 생기나요?

**힌트**:
- MQTT 표준 구독은 모든 구독자에게 메시지를 복사해서 전달합니다

<details>
<summary>모범 답안</summary>

#### 문제: 메시지 트리플 처리

일반 구독(`telemetryhub/devices/+/telemetry`)으로 3대가 동일 토픽 구독 시, 각 인스턴스가 **동일한 메시지를 모두 수신** → Kafka에 3배 중복 발행.

#### 해결: MQTT 5.0 Shared Subscription

```
$share/{GroupName}/{토픽}
$share/telemetryhub-ingestion/telemetryhub/devices/+/telemetry
```

같은 `GroupName`의 구독자들이 라운드로빈으로 메시지를 분산 수신. Kafka Consumer Group과 동일한 개념.

#### 추가 주의사항

`clientId` 충돌도 별개로 해결 필요:
```java
// MQTT 브로커는 같은 clientId로 재연결 시 기존 연결을 강제 종료
// → instanceId 또는 UUID suffix 필수
String clientId = baseClientId + "-" + instanceId;
```

</details>

---

## 2. MQTT & 메시지 수집

### 난이도: ★★★★☆

---

### Q4. Ingestion Service가 MQTT 콜백 스레드에서 직접 Kafka `send()`를 호출하면 안 되는 이유는 무엇인가요?

> MQTT Paho 클라이언트는 메시지 수신 시 콜백 스레드에서 `messageArrived()`를 호출합니다.  
> 여기서 바로 Kafka `producer.send()`를 호출하면 어떤 문제가 생기나요?

**관련 코드**: `ingestion-service/MqttInboundSubscriberLifecycle.java`

**힌트**:
- Kafka send는 네트워크 I/O를 유발합니다
- MQTT Paho의 콜백 스레드는 몇 개일까요?

<details>
<summary>모범 답안</summary>

#### 문제: 콜백 스레드 블로킹

Paho MQTT는 기본적으로 **단일 콜백 스레드**에서 모든 `messageArrived()`를 순차 호출합니다. 이 스레드가 Kafka send 완료(또는 타임아웃)를 기다리는 동안 후속 MQTT 메시지들이 **큐에 대기** → 처리 지연 → MQTT 브로커 버퍼 초과 → 메시지 드롭.

#### 현재 구조 (올바른 설계)

```
MQTT Callback Thread
    ↓ put (non-blocking)
Inbound Queue (capacity: 10,000)
    ↓ poll
Worker Threads (별도 스레드풀)
    ↓
Kafka Producer.send()
```

`inbound-queue-capacity: 10000`, `inbound-worker-threads: 1` 설정으로 분리됨.  
큐 용량 초과 시 드롭 메트릭 발생.

#### 개선 포인트

Worker thread를 늘려 처리량 향상 가능하나, Kafka 파티션 수에 맞춰 조정 필요.

</details>

---

### Q5. `QoS=0`, `QoS=1`, `QoS=2` 각각에서 디바이스-브로커 연결이 끊어졌다 재연결되면 메시지는 어떻게 됩니까?

**힌트**:
- MQTT의 "At most once / At least once / Exactly once" 보장

<details>
<summary>모범 답안</summary>

| QoS | 보장 | 연결 끊김 시 |
|-----|------|--------------|
| 0 | At most once | 전달 안 됨 (저장 없음) |
| 1 | At least once | 재연결 후 재전송 (중복 가능) |
| 2 | Exactly once | 4-way handshake로 정확히 1회 보장 |

#### TelemetryHub에서의 선택

IoT 텔레메트리는 **QoS=1** 이 현실적 선택:
- QoS=0: GPS 좌표처럼 빠르게 갱신되는 데이터에 적합 (오래된 데이터는 가치 없음)
- QoS=2: 4-way handshake → 처리량 대폭 감소, IoT에는 과도한 오버헤드
- QoS=1 + `ingestion-service` 멱등 처리(eventId dedup): 현실적 최선

</details>

---

### Q6. Device Simulator의 `InMemoryPublishedMessageStore`가 `ConcurrentLinkedDeque`를 사용하는데, OOM이 발생할 수 있는 이유를 설명하세요.

> 코드에서 최대 500개로 제한한다고 되어 있지만, 실제로는 제한이 제대로 동작하지 않을 수 있습니다.

**힌트**:
- `deque.size()`의 시간 복잡도를 확인하세요

<details>
<summary>모범 답안</summary>

#### 문제: 비원자적 trim

```java
// 현재 (잘못된) 패턴
deque.addFirst(message);
while (deque.size() > MAX_SIZE) {  // size()가 O(n)
    deque.pollLast();
}
```

1. `ConcurrentLinkedDeque.size()`는 **O(n)** (전체 순회 필요)
2. `addFirst`와 `while (size > MAX)` 사이에 **다른 스레드가 addFirst** 가능 → 최대치 초과

#### 올바른 구현

```java
// LinkedBlockingDeque는 내부 count 필드(AtomicInteger)로 O(1) size
private final LinkedBlockingDeque<Message> store = 
    new LinkedBlockingDeque<>(MAX_SIZE);  // 생성자에 capacity 지정

public void add(Message message) {
    if (!store.offerFirst(message)) {
        store.pollLast();          // 꽉 찼으면 오래된 것 제거
        store.offerFirst(message); // 다시 시도
    }
}
```

capacity-bounded 큐를 사용하면 trim 로직 자체가 필요 없어짐.

</details>

---

## 3. Kafka & Kafka Streams

### 난이도: ★★★★☆

---

### Q7. Stream Processor의 `processing-guarantee: at_least_once`를 `exactly_once_v2`로 변경하면 어떤 트레이드오프가 생기나요?

**힌트**:
- exactly_once는 Kafka 트랜잭션 프로토콜을 사용합니다
- PostgreSQL upsert는 Kafka 트랜잭션 범위 밖입니다

<details>
<summary>모범 답안</summary>

#### exactly_once_v2 활성화 시 보장 범위

- **Kafka → Kafka**: topic-to-topic 처리는 exactly-once 보장 (트랜잭션 내 produce)
- **Kafka → PostgreSQL**: **보장 안 됨** (외부 시스템)

#### 트레이드오프

| 항목 | at_least_once | exactly_once_v2 |
|------|--------------|-----------------|
| 처리량 | 높음 | ~20-30% 감소 |
| 지연 | 낮음 | commit.interval.ms만큼 증가 |
| DB 중복 | upsert로 처리 | upsert로 처리 (동일) |
| Kafka 메시지 중복 | 가능 (재처리 시) | 없음 |

#### TelemetryHub에서의 권장

DB upsert에 **최신성 보호 WHERE 조건**이 있으면 `at_least_once` + 멱등 upsert 조합이 성능 대비 효율적.  
state store changelog topic에 중복 메시지가 쌓이지 않으려면 `exactly_once_v2` 사용 검토.

</details>

---

### Q8. Kafka Streams의 시간 윈도우 집계에서 `grace period`를 설정하지 않으면 늦게 도착한 이벤트는 어떻게 처리됩니까?

> `StreamProcessorProperties`에는 `late-event-grace: 2m`이 설정되어 있습니다.  
> 그런데 실제 토폴로지 코드에서 `.grace(Duration)` API를 사용하지 않으면 어떻게 될까요?

**관련 코드**: `stream-processor/StreamTopologyPlanner.java`

<details>
<summary>모범 답안</summary>

#### Grace Period 미사용 시 동작

Kafka Streams 기본값: grace period = 0  
→ 윈도우 종료 시각 이후에 도착한 이벤트는 **즉시 폐기** (drop)

예시:
- 윈도우: 10:00~10:01
- 이벤트 eventTime: 10:00:30, ingestTime: 10:03:00 (2.5분 지연)
- grace = 0이면 10:01 이후 도착 → 폐기
- grace = 2m이면 10:03까지 도착 → 집계에 포함

#### 올바른 코드

```java
TimeWindows windows = TimeWindows
    .ofSizeAndGrace(
        Duration.ofMinutes(1),      // window size
        Duration.ofMinutes(2)       // grace period
    );
```

#### TelemetryHub의 이슈

설정값만 존재하고 토폴로지에서 실제로 적용하지 않으면 늦은 도착 이벤트(LATE_EVENT 시나리오)가 모두 폐기되어 집계 결과 누락.

</details>

---

### Q9. Stream Processor를 2대로 스케일-아웃할 때 `state-dir`는 어떻게 관리해야 하나요?

**힌트**:
- 로컬 RocksDB 상태 저장소는 파티션 단위로 분배됩니다
- Pod 재시작 시 state-dir가 사라지면?

<details>
<summary>모범 답안</summary>

#### state-dir의 역할

Kafka Streams는 `state-dir`에 RocksDB 형태로 **집계 중간 상태**를 저장합니다.  
이 디렉토리가 없어지면(Pod 재시작, tmpdir 사용) Kafka changelog topic에서 전체 재구성(restore) 발생 → 수 분 이상 서비스 중단.

#### Kubernetes 환경 권장 설정

```yaml
# StatefulSet 사용
volumes:
  - name: streams-state
    persistentVolumeClaim:
      claimName: streams-state-pvc

volumeMounts:
  - name: streams-state
    mountPath: /var/lib/telemetryhub-streams

# application.yml
telemetryhub.stream.state-dir: /var/lib/telemetryhub-streams
```

#### standby replicas 설정

```yaml
telemetryhub.stream.num-standby-replicas: 1
```

핫 스탠바이가 state를 미리 복제해두어 rebalance 시 downtime 최소화.

#### 스케일-아웃 시 파티션 재분배

4 파티션, 2 인스턴스 → 각 인스턴스가 2 파티션씩 담당.  
파티션 A를 담당하던 인스턴스가 종료되면, 다른 인스턴스가 A를 인계받아 state 복구.

</details>

---

### Q10. Kafka 토픽의 메시지 키를 `deviceId`로 설정하는 이유와, 이때 특정 디바이스의 메시지만 폭발적으로 증가하면 어떤 문제가 생기나요?

**관련 코드**: `ingestion-service/KafkaEventKeyResolver.java`

<details>
<summary>모범 답안</summary>

#### deviceId를 키로 사용하는 이유

같은 키 → 같은 파티션 → **단일 디바이스 이벤트는 순서 보장**.  
Stream Processor에서 deviceId로 GroupBy 시 상태가 하나의 파티션에 집중 → 일관성 있는 집계.

#### Hot Partition 문제

특정 `deviceId`가 초당 10만 건을 발행하면:
- 해당 파티션만 Consumer lag 급증
- Stream Processor 한 스레드가 독점 → 다른 파티션 지연

#### 해결 방안

1. **복합 키**: `deviceId + 시간 bucket` 해시 → 파티션 분산
2. **파티셔너 커스터마이징**: 메시지 빈도 기반 동적 파티션 선택
3. **파티션 수 증가**: 더 많은 파티션으로 분산 (단, 리밸런싱 비용 발생)
4. **이상 디바이스 격리**: Ingestion에서 rate limit 적용 후 별도 토픽으로 격리

</details>

---

## 4. 동시성 & 스레드 안전성

### 난이도: ★★★★☆

---

### Q11. `KafkaIngestionPublisher`에서 메트릭 업데이트를 `AtomicReference.updateAndGet()`으로 하지 않고 단순 read-modify-write 패턴을 사용하면 어떤 문제가 생기나요?

> 아래 두 코드의 차이를 설명하세요.

```java
// 코드 A
Metrics m = metricsRef.get();
metricsRef.set(new Metrics(m.totalPublished() + 1, Instant.now()));

// 코드 B
metricsRef.updateAndGet(m -> new Metrics(m.totalPublished() + 1, Instant.now()));
```

<details>
<summary>모범 답안</summary>

#### 코드 A의 문제: Lost Update (ABA 문제)

```
Thread 1: get() → Metrics(published=100)
Thread 2: get() → Metrics(published=100)
Thread 1: set(Metrics(published=101))
Thread 2: set(Metrics(published=101))  ← Thread 1의 업데이트 덮어씀
```

결과: 2번 발행했지만 카운터는 1만 증가. **동시성이 높을수록 오차 심화**.

#### 코드 B의 보장: CAS (Compare-And-Swap)

`updateAndGet`은 내부적으로 CAS 루프:
```java
do {
    expected = metricsRef.get();
    newValue = updater.apply(expected);
} while (!metricsRef.compareAndSet(expected, newValue));
```

기대값과 현재값이 다르면 재시도 → 업데이트 손실 없음.

#### 성능 고려

경합이 매우 높으면 CAS 재시도가 많아져 오히려 throughput 감소.  
이 경우 `LongAdder` (stripe 방식) 사용이 더 효율적.

</details>

---

### Q12. `DeviceSimulatorControlService`의 `start()`, `stop()`, `scale()` 메서드에 `synchronized`가 없다면 어떤 경쟁 조건이 발생할 수 있나요?

**힌트**:
- 시뮬레이터를 시작하는 동시에 scale을 요청하면?

<details>
<summary>모범 답안</summary>

#### 경쟁 조건 시나리오

```
Thread 1 (start): runtimeState = STARTING
Thread 2 (scale): runtimeState 확인 → STARTING 이므로 scale 거부해야 하는데...
Thread 1 (start): loopService.start(state) 호출 중
Thread 2 (scale): 상태 변경 없이 loopService.scale() 호출 → 루프가 두 번 시작됨
```

#### 위험한 상황

1. **루프 중복 실행**: 두 스레드가 동시에 `loopService.start()`를 호출하면 이벤트 발행이 2배
2. **상태 불일치**: `runtimeState`는 갱신됐지만 `loopService`는 아직 이전 상태
3. **null 참조**: `loopService`가 초기화되기 전에 `scale()`이 호출되면 NPE

#### 올바른 설계

```java
public synchronized SimulatorRuntimeState start(StartRequest req) {
    if (isRunning()) throw new AlreadyRunningException();
    SimulatorRuntimeState next = SimulatorRuntimeState.of(req);
    runtimeState.set(next);    // 상태 먼저
    loopService.start(next);   // 루프는 나중
    return next;
}
```

상태 변경과 루프 시작을 **하나의 임계 구역**으로 묶어야 함.

</details>

---

## 5. 데이터 일관성 & 순서 보장

### 난이도: ★★★★☆

---

### Q13. `eventTime`(디바이스 발생 시각)과 `ingestTime`(서버 수신 시각) 중 어느 것을 집계의 기준 시간으로 사용해야 하나요?

> DeviceLastSeenAggregate의 최신성 비교 로직에서 `eventTime`을 우선합니다.  
> 디바이스 시계가 틀려있다면 어떤 문제가 생길까요?

<details>
<summary>모범 답안</summary>

#### 두 시각의 특성

| 속성 | eventTime | ingestTime |
|------|-----------|------------|
| 의미 | 이벤트가 실제로 발생한 시각 | 서버가 받은 시각 |
| 신뢰도 | 낮음 (디바이스 RTC 오류, timezone 오류 가능) | 높음 (서버 시계) |
| 지연 반영 | 없음 | 네트워크 지연 + 큐 지연 포함 |

#### 디바이스 시계 오류 시 문제

1. **과거 이벤트가 최신으로 판단**: 시계가 미래로 설정된 디바이스의 이벤트가 `DeviceLastSeen`을 오염
2. **윈도우 집계 왜곡**: 10분 전 eventTime을 가진 이벤트가 현재 윈도우에 들어오지 못하거나 잘못된 윈도우로 분류

#### 권장 전략

```java
// 허용 가능한 시계 편차 범위 검증
if (Math.abs(eventTime - ingestTime) > MAX_CLOCK_SKEW_MINUTES) {
    // 경보 또는 ingestTime으로 대체
    eventTime = ingestTime;
}
```

1. **집계 기준**: `eventTime` 사용 (실제 발생 시각이 비즈니스 의미에 맞음)
2. **최신성 보호**: `eventTime` 동일 시 `ingestTime`으로 tiebreak (현재 코드)
3. **시계 편차 모니터링**: `ingestTime - eventTime` 분포를 메트릭으로 추적

</details>

---

### Q14. Stream Processor가 재시작(또는 재배포)되어 Kafka offset을 처음부터 재소비할 때, PostgreSQL 집계 테이블에 데이터가 어떻게 되나요?

> `processing-guarantee: at_least_once` 환경에서 재처리가 발생하면?

<details>
<summary>모범 답안</summary>

#### 재소비 시 발생하는 일

1. Kafka offset을 auto.offset.reset 또는 수동으로 earliest로 설정
2. 7일치 raw event 재처리
3. 집계 결과가 PostgreSQL에 다시 upsert

#### 멱등성이 없다면

```sql
-- lastEventTime 비교 없이 무조건 upsert
INSERT INTO telemetryhub_device_last_seen (device_id, last_event_time, ...)
VALUES (?, ?, ...)
ON CONFLICT (device_id) DO UPDATE SET last_event_time = EXCLUDED.last_event_time;
```

→ 과거 이벤트로 최신 데이터가 덮여씀 (**데이터 롤백**)

#### 올바른 upsert (최신성 보호)

```sql
ON CONFLICT (device_id) DO UPDATE SET
    last_event_time = EXCLUDED.last_event_time,
    last_ingest_time = EXCLUDED.last_ingest_time
WHERE
    EXCLUDED.last_event_time > telemetryhub_device_last_seen.last_event_time
    OR (EXCLUDED.last_event_time = telemetryhub_device_last_seen.last_event_time
        AND EXCLUDED.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time)
```

→ 재처리 중 더 최신 데이터가 들어온 경우 무시 (멱등성 보장).

</details>

---

### Q15. `eventId` 기반 중복 제거(deduplication)가 없는 현재 구조에서, MQTT QoS=1로 인한 중복 메시지가 집계에 미치는 영향은?

**힌트**:
- HARD_BRAKE 이벤트가 2번 발행되면 `DrivingEventCounter`는?

<details>
<summary>모범 답안</summary>

#### 중복이 발생하는 경로

1. QoS=1 재전송: 브로커 ACK 유실 → 디바이스가 동일 메시지 재전송 (같은 eventId)
2. DUPLICATE_EVENT 시나리오: Simulator가 의도적으로 중복 발행
3. Ingestion Service 재시작: Kafka producer retry로 이미 발행된 메시지 중복

#### 집계 영향

- **DeviceLastSeen**: eventTime/ingestTime 비교로 두 번째 처리는 무시 → 영향 없음
- **EventsPerMinute**: 카운트 2 증가 → **오염**
- **DrivingEventCounter**: HARD_BRAKE가 2회로 기록 → **오염**
- **RegionHeatmap**: 동일 좌표 2번 카운팅 → **오염**

#### 해결 방안

```java
// Kafka Streams에서 eventId 기반 dedup
KStream<String, RawEvent> deduped = rawStream
    .transform(
        () -> new DeduplicationTransformer<>(Duration.ofHours(1), event -> event.eventId()),
        DEDUP_STORE_NAME
    );
```

Bloom Filter 또는 time-windowed Set으로 최근 N시간 eventId 추적.

</details>

---

## 6. 장애 시나리오 대응

### 난이도: ★★★★★

---

### Q16. MQTT 브로커가 5분간 다운되었다가 복구됩니다. 이 기간 동안 데이터는 어떻게 되고, 복구 후 파이프라인은 어떻게 동작해야 하나요?

<details>
<summary>모범 답안</summary>

#### 장애 전파 경로

```
MQTT Broker DOWN
    ↓
Ingestion Service: MQTT reconnect 시도 (지수 백오프)
    ↓
Inbound Queue: 비어있음 (새 메시지 없음)
    ↓
Kafka: 메시지 없음 (producer 중단)
    ↓
Stream Processor: idle (consumer lag=0)
    ↓
PostgreSQL: 업데이트 없음
```

#### 데이터 손실 여부

- QoS=0: 브로커 다운 중 디바이스가 발행한 메시지 **영구 손실**
- QoS=1/2: 디바이스가 브로커 복구를 기다려 재전송 → **손실 없음** (단, 디바이스 세션 유지 필요)

#### 복구 시 주의사항

1. **재전송 폭풍(RECONNECT_STORM)**: 수천 디바이스가 동시에 reconnect 시도 → 브로커 과부하  
   → 지수 백오프 + 지터(jitter) 적용 필수

2. **Ingestion Queue 과부하**: 재전송 메시지가 한꺼번에 유입  
   → Queue capacity 모니터링, Worker thread 동적 증가

3. **Kafka lag 급증**: 재전송 메시지 대량 유입 시 Stream Processor lag  
   → num-stream-threads 조정 또는 인스턴스 추가

#### RECONNECT_STORM 시뮬레이션

```bash
curl -X POST 'http://localhost:8091/sim/v1/scenario/RECONNECT_STORM?percent=100'
```

</details>

---

### Q17. PostgreSQL 장애로 인해 Stream Processor의 upsert가 계속 실패하는 상황에서 Kafka Streams는 어떻게 동작하나요?

<details>
<summary>모범 답안</summary>

#### 현재 코드의 문제

`ProjectionWriter`에 `@Transactional`이 없고 예외 처리가 없다면:

1. upsert 실패 → 예외 발생
2. Kafka Streams가 예외를 받으면 기본적으로 **task를 재처리**
3. 같은 메시지를 반복 retry → DB 계속 실패 → 루프
4. `default.deserialization.exception.handler`나 `ProductionExceptionHandler`가 없으면 **Streams 앱 크래시**

#### 올바른 예외 처리

```java
// StreamsBuilder에 예외 핸들러 설정
props.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
          ContinueProductionExceptionHandler.class);

// ProjectionWriter 내
try {
    repository.upsert(aggregate);
} catch (DataAccessException e) {
    // 실패 메트릭 기록
    meterRegistry.counter("projection.write.error").increment();
    // DLQ로 보내거나 로그 기록 후 계속 진행
}
```

#### 권장 설계

1. **Circuit Breaker**: DB 연속 실패 시 open → Streams는 fallback(skip/DLQ)
2. **DLQ (Dead Letter Queue)**: 처리 실패 이벤트를 별도 토픽에 저장 → 나중에 재처리
3. **Retry with backoff**: 일시적 DB 장애는 재시도, 지속적 실패는 DLQ

</details>

---

### Q18. Batch Backfill 실행 중 실시간 Stream Processor도 동시에 같은 PostgreSQL 테이블에 write하고 있습니다. 충돌을 어떻게 방지하나요?

<details>
<summary>모범 답안</summary>

#### 충돌 시나리오

```
시각 T1: Stream Processor → device-001의 last_event_time = 10:00:00 upsert
시각 T2: Backfill → device-001의 last_event_time = 09:00:00 upsert (과거 데이터)
결과: DB에 09:00:00 저장 → 최신 데이터 롤백
```

#### 해결책 1: 최신성 보호 WHERE 조건 (현재 권장)

```sql
ON CONFLICT (device_id) DO UPDATE SET
    last_event_time = EXCLUDED.last_event_time
WHERE EXCLUDED.last_event_time > telemetryhub_device_last_seen.last_event_time
```

Backfill의 과거 데이터가 더 최신인 현재 데이터를 덮어쓰지 않음.

#### 해결책 2: 파티션 분리 (대용량 시)

- Backfill은 별도 파티션/테이블에 쓰고, 완료 후 swap
- 실시간과 배치의 write 경합 자체를 없앰

#### 해결책 3: overwrite-mode: SKIP_EXISTING

```yaml
telemetryhub.backfill.overwrite-mode: SKIP_EXISTING
```

이미 존재하는 device_id는 건너뜀 → 실시간 데이터 보호.

#### dry-run 우선 실행

```yaml
telemetryhub.backfill.dry-run: true  # 기본값
```

실제 write 없이 처리량/충돌 여부 먼저 검증.

</details>

---

## 7. 성능 최적화

### 난이도: ★★★★★

---

### Q19. Stream Processor가 초당 100,000건을 처리해야 하는데, 현재 `ProjectionWriter`가 건별 upsert를 하고 있습니다. 병목 지점과 개선 방안을 설명하세요.

<details>
<summary>모범 답안</summary>

#### 현재 병목

```java
// 건별 upsert (현재)
@Override
public void process(Record<String, DeviceLastSeenAggregate> record) {
    repository.upsert(record.value());  // 매 메시지마다 DB 왕복
}
```

- 초당 10만 건 = 10만 번의 DB 커넥션 체크아웃 + 쿼리 + 응답
- PostgreSQL IOPS 한계, 커넥션 풀 고갈

#### 개선 1: Punctuator 기반 배치 처리

```java
// Kafka Streams Processor API
context.schedule(
    Duration.ofSeconds(1),
    PunctuationType.WALL_CLOCK_TIME,
    timestamp -> {
        // 1초마다 버퍼 flush
        List<DeviceLastSeenAggregate> batch = buffer.drain();
        repository.batchUpsert(batch);  // 단일 쿼리
    }
);

// SQL: unnest로 배치 upsert
INSERT INTO telemetryhub_device_last_seen (device_id, last_event_time, ...)
SELECT * FROM unnest(?::text[], ?::timestamptz[], ...)
ON CONFLICT (device_id) DO UPDATE SET ...
WHERE EXCLUDED.last_event_time > telemetryhub_device_last_seen.last_event_time
```

#### 개선 2: Kafka Streams State Store → 최종 결과만 write

현재: 모든 이벤트마다 write  
개선: State Store에서 집계 완료 후 윈도우 종료 시에만 write

```java
stream
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofMinutes(2)))
    .aggregate(...)
    .suppress(Suppressed.untilWindowCloses(unbounded()))  // 윈도우 닫힐 때만 emit
    .toStream()
    .foreach((key, value) -> writer.write(value));  // 윈도우당 1회
```

#### 개선 3: COPY 명령 (대용량 배치)

```java
// PostgreSQL COPY는 INSERT보다 10-50배 빠름
connection.getCopyAPI().copyIn(
    "COPY telemetryhub_device_last_seen FROM STDIN WITH CSV",
    csvInputStream
);
```

</details>

---

### Q20. Analytics API에서 `GET /heatmap/regions`를 1,000명이 동시에 요청할 때 예상되는 문제와 대응 방안은?

> 현재 Analytics API에는 캐시와 커넥션 풀 설정이 없습니다.

<details>
<summary>모범 답안</summary>

#### 예상 문제

1. **DB 커넥션 풀 고갈**
   - HikariCP 기본 풀 크기: 10
   - 1,000 요청 → 990개는 커넥션 대기 → 타임아웃

2. **히트맵 쿼리 비용**
   ```sql
   SELECT grid_lat, grid_lon, SUM(event_count)
   FROM telemetryhub_region_heatmap
   WHERE minute_bucket_start BETWEEN ? AND ?
     AND grid_lat BETWEEN ? AND ?
   GROUP BY grid_lat, grid_lon
   ```
   시간 범위가 넓으면 수백만 행 집계

3. **N중 동일 쿼리 중복 실행**: 동일 파라미터 요청 1,000개가 각각 DB 쿼리 실행

#### 대응 방안

**1. 커넥션 풀 최적화**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 300000
```

**2. 응답 캐시 (Spring Cache + Redis)**
```java
@Cacheable(value = "heatmap", key = "#from + '_' + #to + '_' + #region")
public List<RegionHeatmapView> getRegionHeatmap(Instant from, Instant to, ...) { ... }
```
동일 파라미터는 캐시 hit → DB 쿼리 1회로 수백 건 서빙

**3. 쿼리 최적화 인덱스**
```sql
CREATE INDEX idx_heatmap_bucket_region
ON telemetryhub_region_heatmap (minute_bucket_start, grid_lat, grid_lon);
```

**4. 결과 사전 집계 (Materialized View)**
```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_heatmap_hourly;
```
분 단위 raw를 시간 단위로 미리 집계.

</details>

---

## 8. 운영 & 모니터링

### 난이도: ★★★☆☆

---

### Q21. Prometheus + Grafana로 이 파이프라인의 건강 상태를 모니터링할 때 반드시 추적해야 할 핵심 메트릭 5가지는 무엇인가요?

<details>
<summary>모범 답안</summary>

#### 핵심 메트릭

| 메트릭 | 위치 | 경보 조건 |
|--------|------|----------|
| `kafka_consumer_lag` | Stream Processor | lag > 10,000 → 처리 지연 |
| `telemetryhub_ingestion_total_failed` | Ingestion Service | 증가 추세 → MQTT/Kafka 이상 |
| `mqtt_connection_status` | Ingestion Service | 0 → 브로커 연결 끊김 |
| `projection_write_error_total` | Stream Processor | 증가 → PostgreSQL 장애 |
| `ingestTime - eventTime` (p99) | Ingestion Service | > 30s → 디바이스 지연 또는 큐 적체 |

#### 추가 권장

- `jvm_memory_used_bytes`: OOM 사전 감지
- `hikaricp_connections_active`: DB 커넥션 풀 포화도
- `stream_processor_uptime`: 재시작 빈도 (잦으면 크래시 루프)

</details>

---

### Q22. Device Simulator의 `/sim/v1/scenario/LATE_EVENT?percent=30` API를 사용한 부하 테스트에서 무엇을 검증할 수 있나요?

<details>
<summary>모범 답안</summary>

#### 검증 목표

전체 이벤트의 30%가 **30~180초 지연** 발행될 때:

1. **Grace Period 동작**: 설정한 2분 이내 지연 이벤트가 올바른 윈도우에 집계되는지
2. **집계 정확성**: EventsPerMinute 값이 지연 이벤트 포함 시 올바른지
3. **DeviceLastSeen 최신성**: 과거 eventTime 이벤트가 더 최신 데이터를 덮어쓰지 않는지
4. **Consumer Lag 변화**: 지연 이벤트가 몰리면 Kafka lag 급증하는지

#### 테스트 시나리오

```bash
# 1. 100개 디바이스로 정상 발행 시작
curl -X POST 'http://localhost:8091/sim/v1/start?devices=100&intervalMs=500'

# 2. 5분 후 LATE_EVENT 30% 주입
curl -X POST 'http://localhost:8091/sim/v1/scenario/LATE_EVENT?percent=30'

# 3. Grafana에서 확인
# - kafka_consumer_lag 변화
# - events_per_minute 집계 값
# - projection_write_error 없는지

# 4. 5분 후 정상으로 복구
curl -X POST 'http://localhost:8091/sim/v1/scenario/NORMAL?percent=100'
```

#### 합격 기준

- Grace period 내 이벤트: 정상 집계
- Grace period 초과 이벤트: 로그/메트릭에 drop 기록
- DeviceLastSeen: 최신 eventTime 유지

</details>

---

## 9. 배치 재처리

### 난이도: ★★★☆☆

---

### Q23. `batch-backfill` 모듈이 Spring Batch를 사용하는 이유가 무엇인가요? 직접 while 루프로 처리하면 안 되나요?

<details>
<summary>모범 답안</summary>

#### Spring Batch를 사용하는 이유

| 기능 | 직접 구현 | Spring Batch |
|------|-----------|--------------|
| 청크 단위 처리 | 직접 구현 | `chunk(500)` 설정 |
| 실패 재시작 | 직접 구현 (offset 저장 등) | `JobRepository`에 자동 저장 |
| 스킵/재시도 정책 | 직접 구현 | `skip()`, `retry()` 설정 |
| 실행 이력 | 직접 구현 | DB에 자동 기록 |
| 병렬 처리 | 직접 구현 | `PartitionedStep` |

#### 핵심 이점: 체크포인트(Checkpoint)

500만 건 재처리 중 200만 건에서 장애 발생 시:
- 직접 구현: 처음부터 재시작
- Spring Batch: 마지막 성공한 청크부터 재시작 (`JobExecution` state 저장)

#### TelemetryHub에서의 활용

```java
@Bean
public Job telemetryhubBackfillJob(Step prepare, Step execute) {
    return jobBuilder.get("telemetryhubBackfillJob")
        .start(prepare)    // 계획 수립
        .next(execute)     // 청크 실행
        .build();
}
```

`dry-run: true` 기본값으로 실제 write 전에 계획 검증 가능.

</details>

---

### Q24. Backfill 소스가 `RAW_TOPIC_EXPORT` (NDJSON 파일)인데, 이 파일이 수 GB라면 어떻게 메모리 효율적으로 처리하나요?

<details>
<summary>모범 답안</summary>

#### 문제: 전체 파일 로드

```java
// 위험: 파일 전체를 메모리에 로드
List<RawEvent> events = objectMapper.readValue(file, List.class);  // OOM!
```

#### 올바른 구현: 스트리밍 파싱

NDJSON (Newline-Delimited JSON)은 한 줄 = 한 이벤트이므로:

```java
// 현재 구현 방향: 청크 단위 처리
try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
    List<RawEvent> chunk = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
        chunk.add(objectMapper.readValue(line, RawEvent.class));
        if (chunk.size() >= chunkSize) {  // 500
            process(chunk);
            chunk.clear();               // 메모리 해제
        }
    }
    if (!chunk.isEmpty()) process(chunk);  // 잔여 처리
}
```

#### 추가 최적화

- **Jackson MappingIterator**: 파일 스트리밍 파싱
  ```java
  MappingIterator<RawEvent> iter = mapper.readerFor(RawEvent.class)
      .readValues(new FileInputStream(file));
  while (iter.hasNext()) { /* 처리 */ }
  ```
- **시간 범위 필터**: `from~to` 범위 밖 이벤트 즉시 skip (I/O는 발생하지만 메모리에는 유지 안 함)
- **병렬 파티션 처리**: 파일을 디바이스 ID 기준으로 분할 후 멀티스레드 처리

</details>

---

## 추가 학습 키워드

| 키워드 | 설명 |
|--------|------|
| **Kafka Streams** | 스트리밍 처리 라이브러리, State Store, KTable, KStream 개념 |
| **MQTT Shared Subscription** | QoS, 공유 구독을 통한 부하 분산 |
| **at-least-once vs exactly-once** | 메시지 전달 보장 수준과 트레이드오프 |
| **Grace Period** | 늦게 도착한 이벤트를 윈도우에 포함하는 허용 시간 |
| **Hot Partition** | 특정 파티션에 트래픽 집중, 해결 방법 |
| **CAS (Compare-And-Swap)** | Lock-free 동시성 처리 기법 |
| **RECONNECT_STORM** | 재연결 폭풍 방지를 위한 지수 백오프 + jitter |
| **Materialized View** | 사전 집계된 뷰로 쿼리 최적화 |
| **Transactional Outbox** | DB 트랜잭션과 이벤트 발행을 원자적으로 처리하는 패턴 |
| **DLQ (Dead Letter Queue)** | 처리 실패 메시지를 별도 저장, 나중에 재처리 |