# Stream Processor 상세 구현 및 동작 정리

이 문서는 `apps/telemetryhub/stream-processor`의 실제 코드 기준 동작을 정리한다.
기존 `stream-processor-design.md`가 설계 의도 중심이라면, 이 문서는 각 토폴로지가 실제로 어떤 순서로 이벤트를 처리하는지, 버퍼 flush가 언제 일어나는지에 초점을 둔다.

## 전체 실행 흐름

```text
Kafka topic: telemetryhub.raw-events
  -> Kafka Streams 토폴로지 (4개 병렬)
       -> [1] DeviceLastSeenTopology
       -> [2] EventsPerMinuteTopology
       -> [3] DrivingEventCounterTopology
       -> [4] RegionHeatmapTopology
  -> 각 토폴로지 -> BufferedJdbcProjectionWriter (메모리 버퍼)
       -> 주기적 flush (500ms) 또는 batchSize(100) 도달 시
       -> PostgreSQL 집계 테이블 batch upsert
```

4개 토폴로지는 모두 같은 source topic(`telemetryhub.raw-events`)을 소비한다.
독립적인 KStream 인스턴스이며, 서로 공유하지 않는다.

## 입력 메시지 — RawEventMessage

Kafka에서 읽는 메시지는 `RawEventMessage`로 역직렬화된다.

```java
public record RawEventMessage(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        Instant ingestTime,
        String sourceTopic,
        String kafkaKey,
        String payload
) {
}
```

ingestion-service가 Kafka에 발행하는 `NormalizedRawEvent`와 필드가 동일하다. 별도 변환 없이 그대로 역직렬화된다.

## 공통 전처리 — Late Event 필터링과 중복 제거

EventsPerMinute, DrivingEventCounter, RegionHeatmap 세 토폴로지는 데이터 처리 전에 두 단계 공통 전처리를 적용한다.

```text
sourceStream
  -> LateEventPolicySupport.retainWithinGrace()  [늦은 이벤트 필터]
  -> RawEventDeduplicationSupport.deduplicate()  [중복 eventId 제거]
  -> 이후 집계 처리
```

DeviceLastSeen은 중복 제거 없이 late event 필터만 적용한다.

### LateEventPolicySupport — 늦은 이벤트 필터

```java
boolean isWithinGrace(RawEventMessage event, Duration lateEventGrace) {
    if (event == null || event.eventTime() == null || event.ingestTime() == null) {
        return true;  // null이면 통과
    }

    Duration delay = Duration.between(event.eventTime(), event.ingestTime());
    if (delay.isNegative()) {
        return true;  // ingestTime < eventTime (미래 이벤트)도 통과
    }
    return delay.compareTo(lateEventGrace) <= 0;
}
```

`delay = ingestTime - eventTime`이 `lateEventGrace`(기본 2분) 이하인 이벤트만 통과한다.
예를 들어 device가 30분 전 이벤트를 지금 보내면 delay=30분이므로 drop된다.

`delay`가 음수이면(eventTime이 ingestTime보다 미래) 필터를 통과한다. 시계 오차로 인한 경우다.
eventTime이나 ingestTime이 null이면 무조건 통과한다.

### RawEventDeduplicationSupport — eventId 중복 제거

중복 제거는 Kafka Streams의 `processValues()`와 영속 상태 저장소를 사용한다.

```java
// EventIdDeduplicationProcessor.process()
if (store.get(value.eventId()) != null) {
    context.forward(record.withValue(null));  // null로 교체해 전달
    return;
}

long seenAt = value.ingestTime() != null ? value.ingestTime().toEpochMilli() : System.currentTimeMillis();
store.put(value.eventId(), seenAt);
context.forward(record);
```

이미 본 eventId면 메시지 값을 `null`로 교체해 forward한다. 이후 `.filter((key, event) -> event != null)`로 drop된다.

처음 본 eventId면 상태 저장소에 `(eventId, seenAt)` 형태로 기록한다.

중요한 점은 이 상태 저장소가 **무한히 커진다**는 것이다. TTL이나 eviction이 없다.
장기 운영 시 RocksDB 상태 저장소 크기가 계속 증가한다.

각 토폴로지마다 별도의 dedup store를 사용한다.
```text
events-per-minute-store-event-id-dedup-store
driving-event-counter-store-event-id-dedup-store
region-heatmap-store-event-id-dedup-store
```

## 토폴로지 1 — DeviceLastSeenTopology

디바이스별 가장 최근 이벤트 정보를 추적한다.

```text
sourceStream
  -> filter(null 제거)
  -> LateEventPolicySupport.retainWithinGrace()
  -> filter(null 제거)
  -> filter(TELEMETRY | DEVICE_HEALTH | DRIVING_EVENT)
  -> selectKey(deviceId)
  -> mapValues(DeviceLastSeenAggregate::from)
  -> groupByKey()
  -> reduce(DeviceLastSeenAggregate::merge)  [KTable]
  -> toStream()
  -> foreach(projectionWriter::upsert)
```

모든 eventType(TELEMETRY, DEVICE_HEALTH, DRIVING_EVENT)을 처리한다.

`DeviceLastSeenAggregate.merge()`는 두 집계 중 더 최신 이벤트를 선택한다.

```java
public DeviceLastSeenAggregate merge(DeviceLastSeenAggregate other) {
    boolean otherIsNewer = other.lastEventTime().isAfter(lastEventTime())
            || (other.lastEventTime().equals(lastEventTime())
                && other.lastIngestTime().isAfter(lastIngestTime()));
    return otherIsNewer ? other : this;
}
```

`eventTime` 기준으로 더 최신이면 교체, 동일하면 `ingestTime`으로 2차 비교한다.

**중복 제거를 적용하지 않는다.** 같은 eventId가 여러 번 와도 모두 처리된다.
이유는 "마지막으로 본 이벤트"를 추적하는 목적이라 중복 허용이 의도적이다.

DB에 쓰는 SQL은 다음과 같다.

```sql
insert into telemetryhub_device_last_seen (...)
on conflict (device_id) do update
    set last_event_id = excluded.last_event_id,
        ...
where excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
   or (
        excluded.last_event_time = telemetryhub_device_last_seen.last_event_time
        and excluded.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time
   )
```

DB에서도 더 최신 이벤트인 경우에만 update한다. KTable reduce + DB 조건 update의 이중 안전장치다.

## 토폴로지 2 — EventsPerMinuteTopology

분당 이벤트 발생 수를 집계한다. 모든 eventType을 포함한다.

```text
sourceStream
  -> filter(null 제거)
  -> LateEventPolicySupport (late event 제거)
  -> RawEventDeduplicationSupport (eventId 중복 제거)
  -> selectKey(EventsPerMinuteKey::from)  [(eventType, minuteBucketStart)]
  -> mapValues(EventsPerMinuteAggregate::first)  [count=1]
  -> groupByKey()
  -> reduce((agg, next) -> agg.increment())  [KTable]
  -> toStream()
  -> foreach(projectionWriter::upsert)
```

키 변환이 핵심이다.

```java
public static EventsPerMinuteKey from(RawEventMessage event) {
    return new EventsPerMinuteKey(
            event.eventType(),
            event.eventTime().truncatedTo(ChronoUnit.MINUTES)  // 분 단위로 버림
    );
}
```

`eventTime`을 분 단위로 truncate해서 같은 분에 들어온 이벤트를 같은 bucket으로 묶는다.

reduce는 단순 증가다. 처음 이벤트는 count=1, 이후 이벤트마다 +1.

```java
public EventsPerMinuteAggregate increment() {
    return new EventsPerMinuteAggregate(eventType, minuteBucketStart, count + 1);
}
```

DB upsert는 현재 KTable의 count를 그대로 덮어쓴다.

```sql
on conflict (event_type, minute_bucket_start) do update
    set event_count = excluded.event_count,
        updated_at = current_timestamp
```

KTable commit 전까지 중간 상태가 여러 번 DB에 write될 수 있다.
예를 들어 count=3일 때 flush되면 count=3이 DB에 쓰이고, 이후 count=5가 되면 다시 count=5가 DB에 쓰인다.

버퍼 내 merge 로직은 더 큰 count를 선택한다.

```java
protected EventsPerMinuteAggregate mergeAggregates(EventsPerMinuteAggregate left, EventsPerMinuteAggregate right) {
    return right.count() >= left.count() ? right : left;
}
```

## 토폴로지 3 — DrivingEventCounterTopology

운전 이벤트(HARD_BRAKE, OVERSPEED, CRASH) 발생 횟수를 device + 분 단위로 집계한다.

```text
sourceStream
  -> filter(null 제거)
  -> filter(DRIVING_EVENT만)
  -> LateEventPolicySupport
  -> RawEventDeduplicationSupport
  -> map(DrivingEventCounterProjection::project)  [payload 파싱 -> DrivingEventCounterKey]
  -> filter(projectedKey != null)
  -> mapValues(DrivingEventCounterAggregate::first)
  -> groupByKey()
  -> reduce((agg, next) -> agg.increment())
  -> toStream()
  -> foreach(projectionWriter::upsert)
```

TELEMETRY, DEVICE_HEALTH는 소스 스트림에서 이미 필터링된다.

`DrivingEventCounterProjection`이 payload를 파싱해서 `drivingEventType`을 꺼낸다.

```java
public DrivingEventCounterKey project(RawEventMessage rawEventMessage) {
    if (rawEventMessage.eventType() != EventType.DRIVING_EVENT) {
        return null;
    }
    DrivingEvent drivingEvent = JsonUtils.fromJson(rawEventMessage.payload(), DrivingEvent.class);
    return DrivingEventCounterKey.of(
            rawEventMessage.deviceId(),
            drivingEvent.type(),
            rawEventMessage.eventTime()
    );
}
```

집계 키는 `(deviceId, drivingEventType, minuteBucketStart)` 조합이다.

파싱 실패 시 `RuntimeException`이 발생한다. Kafka Streams는 이를 처리하지 못한 레코드로 보고 `processing.exception.handler` 정책에 따라 처리한다. 기본 설정(`at_least_once`)에서는 재시도를 반복할 수 있다.

## 토폴로지 4 — RegionHeatmapTopology

TELEMETRY 이벤트의 GPS 좌표를 격자로 변환해 분당 밀도를 집계한다.

```text
sourceStream
  -> filter(null 제거)
  -> filter(TELEMETRY만)
  -> LateEventPolicySupport
  -> RawEventDeduplicationSupport
  -> map(RegionHeatmapProjection::project)  [payload 파싱 -> RegionHeatmapKey]
  -> filter(projectedKey != null)
  -> mapValues(RegionHeatmapAggregate::first)
  -> groupByKey()
  -> reduce((agg, next) -> agg.increment())
  -> toStream()
  -> foreach(projectionWriter::upsert)
```

`RegionHeatmapProjection`이 payload에서 lat, lon을 꺼내 격자 좌표로 변환한다.

```java
public RegionHeatmapKey project(RawEventMessage rawEventMessage) {
    TelemetryEvent telemetryEvent = JsonUtils.fromJson(rawEventMessage.payload(), TelemetryEvent.class);
    return RegionHeatmapKey.of(
            telemetryEvent.lat(),
            telemetryEvent.lon(),
            properties.getHeatmapGridSize(),  // 기본 0.01도
            rawEventMessage.eventTime()
    );
}
```

격자 변환은 `floorToGrid()`로 수행한다.

```java
private static double floorToGrid(double value, double gridSize) {
    return Math.floor(value / gridSize) * gridSize;
}
```

`gridSize=0.01`이면 약 1.1km 단위 격자다.
`lat=37.5234` → `37.52`, `lon=127.0456` → `127.04`

집계 키는 `(gridLat, gridLon, minuteBucketStart)` 조합이다.

## BufferedJdbcProjectionWriter — 버퍼와 flush 메커니즘

4개 projection writer 모두 `BufferedJdbcProjectionWriter`를 상속한다.
KTable의 `foreach`에서 `projectionWriter.upsert()`를 호출할 때마다 DB에 직접 쓰지 않고, 메모리 버퍼에 쌓았다가 일괄 flush한다.

### 버퍼 구조

```java
private final Map<K, T> bufferedAggregates = new LinkedHashMap<>();
```

`LinkedHashMap`으로 같은 키의 집계는 merge되어 하나만 유지된다.

```java
protected final void upsertBuffered(T aggregate) {
    List<T> aggregatesToFlush = null;

    synchronized (bufferMonitor) {
        bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
        if (bufferedAggregates.size() >= projectionBatchProperties.getBatchSize()
                || bufferedAggregates.size() >= projectionBatchProperties.getMaxBufferedEntries()) {
            aggregatesToFlush = drainBufferLocked();
        }
    }

    if (aggregatesToFlush != null && !aggregatesToFlush.isEmpty()) {
        flushAggregates(aggregatesToFlush, true);  // throwOnFailure=true
    }
}
```

### flush 트리거

flush는 두 가지 경로로 발생한다.

| 트리거 | 조건 | throwOnFailure |
|---|---|---|
| 즉시 flush | 버퍼 크기 >= batchSize(100) 또는 >= maxBufferedEntries(5000) | true (예외 전파) |
| 주기 flush | 500ms마다 스케줄러 실행 | false (예외 로그만) |

즉시 flush가 실패하면 예외가 Kafka Streams 처리 스레드로 전파된다.
주기 flush가 실패하면 로그만 남기고, 실패한 집계는 버퍼로 다시 집어넣는다.

### flush 실패 시 재큐

```java
private void requeueAggregates(List<T> aggregates) {
    synchronized (bufferMonitor) {
        for (T aggregate : aggregates) {
            bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
        }
        if (bufferedAggregates.size() > projectionBatchProperties.getMaxBufferedEntries()) {
            log.warn("Projection buffer grew beyond maxBufferedEntries={} ...");
        }
    }
}
```

재큐 시 현재 버퍼와 merge된다. 버퍼가 `maxBufferedEntries`를 초과하면 경고 로그를 남기지만 drop하지 않는다.

### 종료 시 flush

```java
@PreDestroy
void flushOnShutdown() {
    try {
        flushBufferedAggregates(false);
    } finally {
        flushExecutor.shutdown();
        flushExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

애플리케이션 종료 시 버퍼에 남은 집계를 flush한다.

### DB upsert 방식 비교

| 토폴로지 | 충돌 처리 | 비고 |
|---|---|---|
| DeviceLastSeen | `ON CONFLICT (device_id) DO UPDATE WHERE eventTime 조건` | 더 최신 이벤트만 update |
| EventsPerMinute | `ON CONFLICT (event_type, minute_bucket_start) DO UPDATE` | count 무조건 덮어쓰기 |
| DrivingEventCounter | `ON CONFLICT (device_id, driving_event_type, minute_bucket_start) DO UPDATE` | count 무조건 덮어쓰기 |
| RegionHeatmap | `ON CONFLICT (grid_lat, grid_lon, minute_bucket_start) DO UPDATE` | count 무조건 덮어쓰기 |

## KTable commit과 DB write의 관계

`commitIntervalMs=1000`이므로 Kafka Streams는 1초마다 offset을 commit한다.
KTable의 `toStream().foreach()`는 commit과 무관하게 KTable 상태가 바뀔 때마다 호출된다.

commit 전에 DB write가 먼저 일어날 수 있다. 즉 DB에는 집계 결과가 반영됐지만 offset은 아직 commit 안 된 상태가 존재한다.
재시작 시 같은 이벤트를 다시 처리해서 DB를 다시 upsert할 수 있다. `at_least_once` 보장 하에서 허용된 동작이다.

## 메트릭 — StreamProcessorMetricsCollector

projection write 성공/실패를 추적한다.

두 가지 경로로 메트릭을 기록한다.

1. `AtomicReference<StreamProcessorMetricsSnapshot>`: 서비스 내부에서 `/stream/v1/metrics`로 조회 가능
2. `MeterRegistry` Counter: Prometheus 수집 대상

Prometheus 메트릭 이름:
```text
telemetryhub.stream.projection.write.success{projection="DEVICE_LAST_SEEN"}
telemetryhub.stream.projection.write.failure{projection="EVENTS_PER_MINUTE"}
...
```

## 설정 구조

```yaml
telemetryhub:
  stream:
    application-id: telemetryhub-stream-processor
    source-topic: telemetryhub.raw-events
    late-event-grace: PT2M          # ISO-8601 Duration, 기본 2분
    heatmap-grid-size: 0.01         # 위도/경도 격자 단위 (도)
    num-stream-threads: 1
    processing-guarantee: at_least_once
    state-dir: /tmp/telemetryhub-streams
    num-standby-replicas: 0
    commit-interval-ms: 1000

    projection-batch:
      batch-size: 100               # 즉시 flush 임계치
      flush-interval: PT0.5S        # 주기 flush 간격 (500ms)
      max-buffered-entries: 5000    # 경고 임계치
```

## 현재 코드에서 주의할 점

1. **dedup store에 TTL 없음**: `RawEventDeduplicationSupport`의 eventId 저장소는 만료되지 않는다. 장기 운영 시 RocksDB 상태 저장소가 계속 증가하며, 재시작 후 상태가 복구되어도 오래된 eventId를 기억한다.

2. **DeviceLastSeen은 중복 제거 없음**: 같은 eventId의 이벤트가 여러 번 오면 모두 처리된다. 의도된 설계이지만, eventTime이 동일하면 ingestTime이 더 늦은 것을 선택하므로 실질적인 영향은 없다.

3. **commit 전 DB write 가능**: `at_least_once` 보장으로 재시작 시 이벤트가 재처리되고 DB upsert가 중복 실행될 수 있다. 집계 writer들은 모두 upsert 방식이므로 멱등성은 보장된다.

4. **payload 재파싱**: DrivingEventCounter와 RegionHeatmap은 `RawEventMessage.payload()`를 다시 JSON 파싱한다. 파싱 실패 시 해당 이벤트 처리가 실패하고, `at_least_once`에서는 무한 재시도가 발생할 수 있다.

5. **EventsPerMinute의 count 덮어쓰기**: KTable 상태와 DB 상태가 항상 동기화되는 것은 아니다. 버퍼 flush 시점에 KTable의 현재 count로 DB를 덮어쓰기 때문에, 중간 flush에서 낮은 count가 DB에 쓰인 뒤 더 높은 count로 다시 덮어써진다. 최종값은 정확하지만 중간 상태는 일시적으로 과소 집계로 보일 수 있다.

6. **4개 토폴로지가 같은 topic을 독립 소비**: Kafka consumer group이 같은(`applicationId`)으로 묶이지만, 내부적으로 각 토폴로지가 동일 파티션을 처리한다. Scale-out 시 `numStreamThreads`를 늘리거나 인스턴스를 추가하면 파티션이 분산된다.

## 요약

`stream-processor`는 다음 세 가지 역할을 합친 애플리케이션이다.

```text
Kafka Streams 소비 (4개 독립 토폴로지)
  + 공통 전처리 (late event 필터 + eventId 중복 제거)
  + 집계 결과 DB 반영 (버퍼 → batch upsert)
```

실제 동작을 이해할 때는 `{AggregationType}Topology → LateEventPolicySupport → RawEventDeduplicationSupport → reduce(KTable) → BufferedJdbcProjectionWriter → batchUpsert` 순서로 따라가면 된다.