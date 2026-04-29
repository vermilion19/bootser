# Batch Backfill 상세 구현 및 동작 정리

이 문서는 `apps/telemetryhub/batch-backfill`의 실제 코드 기준 동작을 정리한다.
기존 `batch-backfill-review.md`가 패키지 구조와 클래스 목록 중심이라면, 이 문서는 배치 잡이 실행될 때 어떤 순서로 동작하는지, 각 writer가 어떻게 데이터를 쓰는지에 초점을 둔다.

## 전체 실행 흐름

```text
Spring Batch Job 기동
  -> prepareBackfillPlanStep
       -> BackfillPlanService.prepareDefaultPlan()
       -> DefaultBackfillPlanFactory.createDefaultPlan()
       -> plan 로그 출력
  -> executeBackfillDryRunStep
       -> BackfillPlanService.prepareDefaultPlan() (다시 호출)
       -> RoutingBackfillSourceReader.readChunks()
            -> FileBackfillSourceReader (파일 없으면 StubBackfillSourceReader fallback)
       -> 청크 단위로 RoutingBackfillTargetWriter.write() 반복
            -> dryRun=true: DryRunBackfillTargetWriter (로그만)
            -> dryRun=false: target별 실제 writer 호출
       -> BackfillPlanService.recordExecution() (메트릭 기록)
```

## Spring Batch 잡 구조

잡 이름은 `telemetryhubBackfillJob`이며 두 Step으로 구성된다.

```text
telemetryhubBackfillJob
  -> prepareBackfillPlanStep  (tasklet)
  -> executeBackfillDryRunStep (tasklet)
```

두 Step 모두 Tasklet 방식이다. Spring Batch의 Chunk 방식이 아니라, Tasklet 내부에서 직접 청크 루프를 구현한다.

`RunIdIncrementer`가 설정되어 있어 매 실행마다 새 run ID가 부여되고, Spring Batch JobRepository에 실행 이력이 기록된다.

## BackfillPlan — 실행 계획

`DefaultBackfillPlanFactory`가 설정값으로 plan을 만든다.

```java
public BackfillPlan createDefaultPlan() {
    Instant to = Instant.now();
    Instant from = to.minus(properties.getDefaultLookback());

    return new BackfillPlan(
            "telemetryhub-default-backfill",
            properties.getSourceType(),
            properties.getDefaultTargets(),
            from,
            to,
            properties.getChunkSize(),
            properties.getOverwriteMode(),
            properties.isDryRun()
    );
}
```

`to`는 `Instant.now()`, `from`은 `to - defaultLookback`이다.
기본 lookback은 1일이므로 기본 실행 시 최근 24시간 분의 이벤트를 대상으로 한다.

중요한 점은 `prepareBackfillPlanStep`과 `executeBackfillDryRunStep` 두 step이 각각 `prepareDefaultPlan()`을 별도로 호출한다는 것이다. step 간에 plan 객체를 공유하지 않는다. 두 번 `Instant.now()`가 찍히므로 from/to가 미세하게 다를 수 있다.

`BackfillPlan` record의 구조는 다음과 같다.

```java
public record BackfillPlan(
        String jobName,
        BackfillSourceType sourceType,
        List<BackfillTarget> targets,
        Instant from,
        Instant to,
        int chunkSize,
        BackfillOverwriteMode overwriteMode,
        boolean dryRun
) {
}
```

## 소스 파일 읽기 — FileBackfillSourceReader

`RoutingBackfillSourceReader`는 먼저 `FileBackfillSourceReader`를 시도한다.

```java
public void readChunks(BackfillPlan plan, Consumer<List<BackfillRawEvent>> chunkConsumer) {
    try {
        fileBackfillSourceReader.readChunks(plan, chunkConsumer);
    } catch (RuntimeException exception) {
        if (!properties.isFallbackToStubWhenSourceMissing()) {
            throw exception;
        }
        log.warn("Backfill source reader fallback activated: ...");
        stubBackfillSourceReader.readChunks(plan, chunkConsumer);
    }
}
```

파일이 없거나 읽기 실패 시, `fallbackToStubWhenSourceMissing=true`(기본값)이면 `StubBackfillSourceReader`로 자동 fallback한다.

`FileBackfillSourceReader`는 NDJSON(Newline Delimited JSON) 파일을 한 줄씩 읽는다.

```java
try (var lines = Files.lines(sourcePath)) {
    List<BackfillRawEvent> chunk = new ArrayList<>(plan.chunkSize());
    for (String line : (Iterable<String>) lines::iterator) {
        if (line.isBlank()) continue;

        FileBackfillRawEventRecord record = JsonUtils.fromJson(line, FileBackfillRawEventRecord.class);
        BackfillRawEvent event = new BackfillRawEvent(
                record.eventType(),
                record.eventId(),
                record.deviceId(),
                record.eventTime(),
                record.ingestTime() != null ? record.ingestTime() : record.eventTime(),
                record.payload()
        );

        if (event.eventTime().isBefore(plan.from()) || event.eventTime().isAfter(plan.to())) {
            continue;  // 시간 범위 밖 이벤트 skip
        }

        chunk.add(event);
        if (chunk.size() >= plan.chunkSize()) {
            chunkConsumer.accept(List.copyOf(chunk));
            chunk.clear();
        }
    }
    if (!chunk.isEmpty()) {
        chunkConsumer.accept(List.copyOf(chunk));  // 마지막 남은 청크
    }
}
```

동작 순서를 정리하면 다음과 같다.

```text
1. 파일 경로 결정 (sourceType에 따라)
   - RAW_TOPIC_EXPORT -> rawTopicExportPath (기본: data/telemetryhub/raw-topic-export.ndjson)
   - RAW_ARCHIVE      -> rawArchivePath     (기본: data/telemetryhub/raw-archive.ndjson)
2. 파일 존재 확인 (없으면 IllegalStateException)
3. 줄 단위 읽기
4. 빈 줄 skip
5. JSON 파싱 -> BackfillRawEvent 변환
   - ingestTime이 null이면 eventTime으로 대체
6. plan.from ~ plan.to 범위 밖이면 skip
7. chunkSize만큼 쌓이면 chunkConsumer.accept() 호출
8. 마지막 남은 청크도 accept() 호출
```

전체 파일을 메모리에 올리지 않는다. `Files.lines()`는 스트림이고, chunk 단위로 consumer에 넘기는 streaming 방식이다.

### StubBackfillSourceReader

fallback 시 사용되는 stub reader는 고정된 3개 이벤트를 plan.from 기준으로 생성한다.

```java
chunkConsumer.accept(List.of(
        new BackfillRawEvent(TELEMETRY, "backfill-telemetry-1", "device-001", plan.from().plusSeconds(30), ...),
        new BackfillRawEvent(DEVICE_HEALTH, "backfill-health-1", "device-001", plan.from().plusSeconds(45), ...),
        new BackfillRawEvent(DRIVING_EVENT, "backfill-driving-1", "device-002", plan.from().plusSeconds(55), ...)
));
```

`payload`는 `{"stub":true,"type":"..."}` 같은 더미 JSON이다.
실제 writer가 payload를 파싱하는 경우(DrivingEventCounter, RegionHeatmap) stub payload로는 정상 동작하지 않을 수 있다.

## 쓰기 라우팅 — RoutingBackfillTargetWriter

`executeBackfillDryRunStep`의 청크 루프에서 target별로 writer를 호출한다.

```java
backfillSourceReader.readChunks(plan, events -> {
    totalReadEvents.addAndGet(events.size());
    long chunkWrites = plan.targets().stream()
            .mapToLong(target -> backfillTargetWriter.write(target, events, plan))
            .sum();
    totalWrites.addAndGet(chunkWrites);
});
```

하나의 청크(이벤트 리스트)가 **모든 target에 대해 순차적으로 write된다**.
target이 4개라면 같은 청크가 4번 처리된다.

`RoutingBackfillTargetWriter.write()`의 분기:

```java
public long write(BackfillTarget target, List<BackfillRawEvent> events, BackfillPlan plan) {
    if (plan.dryRun()) {
        return dryRunBackfillTargetWriter.write(target, events, plan);
    }
    return switch (target) {
        case DEVICE_LAST_SEEN      -> deviceLastSeenBackfillWriter.write(events, plan);
        case EVENTS_PER_MINUTE     -> eventsPerMinuteBackfillWriter.write(events, plan);
        case DRIVING_EVENT_COUNTER -> drivingEventCounterBackfillWriter.write(events, plan);
        case REGION_HEATMAP        -> regionHeatmapBackfillWriter.write(events, plan);
    };
}
```

`dryRun=true`이면 target에 상관없이 항상 `DryRunBackfillTargetWriter`가 실행된다.
기본값이 `dryRun=true`이므로 기본 실행에서는 DB에 아무것도 쓰지 않는다.

`DryRunBackfillTargetWriter`는 로그만 남기고 `events.size()`를 반환한다. 실제로 write한 수가 아니라 이벤트 수를 반환하는 점에 주의.

## 각 target writer 동작

### DEVICE_LAST_SEEN

청크 내 이벤트를 device별로 최신 1개만 남긴다. 같은 device의 여러 이벤트 중 `eventTime`이 가장 늦은 것을 선택한다.

```java
Map<String, BackfillRawEvent> latestByDevice = events.stream()
        .collect(Collectors.toMap(
                BackfillRawEvent::deviceId,
                event -> event,
                this::pickLatest  // eventTime 비교, 최신 선택
        ));
```

이후 device별로 `overwriteMode`에 따라 다른 SQL을 실행한다.

| overwriteMode | SQL |
|---|---|
| `SKIP_EXISTING` | `ON CONFLICT (device_id) DO NOTHING` |
| `MERGE` (기본) | `ON CONFLICT DO UPDATE ... WHERE excluded.last_event_time > ...` |

`MERGE` 모드에서는 기존 레코드보다 더 최신 이벤트인 경우에만 업데이트한다.

```sql
on conflict (device_id) do update
    set last_event_id = excluded.last_event_id,
        ...
where excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
   or (
        excluded.last_event_time = telemetryhub_device_last_seen.last_event_time
        and excluded.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time
   )
```

동일 `eventTime`이면 `ingestTime`으로 2차 비교한다.

`source_topic`은 `"batch-backfill"` 고정값으로 설정된다. 원본 topic 정보를 보존하지 않는다.

### EVENTS_PER_MINUTE

청크 내 이벤트를 `(eventType, minuteBucketStart)` 조합으로 group by하여 카운트한다.

```java
Map<Key, Long> aggregates = events.stream()
        .collect(Collectors.groupingBy(
                event -> new Key(event.eventType().name(), event.eventTime().truncatedTo(ChronoUnit.MINUTES)),
                Collectors.counting()
        ));
```

`OVERWRITE` 모드이면 먼저 해당 시간 범위의 기존 데이터를 delete한다.

```java
if (plan.overwriteMode() == BackfillOverwriteMode.OVERWRITE) {
    jdbcTemplate.update(
            "delete from telemetryhub_events_per_minute where minute_bucket_start between ? and ?",
            Timestamp.from(plan.from()), Timestamp.from(plan.to())
    );
}
```

이후 집계별로 기존 레코드 존재 여부를 `select count(*)`로 확인하고 insert 또는 update를 수행한다.

```text
기존 레코드 없음 -> insert
기존 레코드 있음 + SKIP_EXISTING -> 건너뜀
기존 레코드 있음 + MERGE -> update (event_count 덮어쓰기)
```

`MERGE`에서 update 시 집계 카운트를 합산하지 않고 현재 청크의 count로 덮어쓴다.
청크가 나뉘어 같은 minute bucket이 여러 청크에 걸쳐있으면 마지막 청크 값으로 덮어써진다.

### DRIVING_EVENT_COUNTER

`DRIVING_EVENT` 타입의 이벤트만 필터링한다.

```java
Map<Key, Long> aggregates = events.stream()
        .filter(event -> event.eventType() == EventType.DRIVING_EVENT)
        .map(event -> {
            DrivingEvent drivingEvent = JsonUtils.fromJson(event.payload(), DrivingEvent.class);
            return new Key(
                    event.deviceId(),
                    drivingEvent.type().name(),
                    event.eventTime().truncatedTo(ChronoUnit.MINUTES)
            );
        })
        .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
```

**payload를 다시 파싱**해서 `DrivingEvent.type()`을 꺼낸다.
`BackfillRawEvent.eventType()`이 `DRIVING_EVENT`임을 알지만, drivingEventType(HARD_BRAKE, OVERSPEED, CRASH)은 payload 안에만 있기 때문이다.

stub reader fallback 시에는 payload가 `{"stub":true,"type":"driving_event"}`이므로 `DrivingEvent`로 파싱이 실패한다.

집계 키는 `(deviceId, drivingEventType, minuteBucketStart)` 조합이다.
write 로직은 EVENTS_PER_MINUTE와 동일한 패턴이다.

### REGION_HEATMAP

`TELEMETRY` 타입의 이벤트만 필터링한다.

```java
Map<Key, Long> aggregates = events.stream()
        .filter(event -> event.eventType() == EventType.TELEMETRY)
        .map(event -> {
            TelemetryEvent telemetryEvent = JsonUtils.fromJson(event.payload(), TelemetryEvent.class);
            return new Key(
                    floorToGrid(telemetryEvent.lat()),
                    floorToGrid(telemetryEvent.lon()),
                    event.eventTime().truncatedTo(ChronoUnit.MINUTES)
            );
        })
        .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
```

마찬가지로 payload를 파싱해서 `lat`, `lon`을 꺼낸다.

핵심은 `floorToGrid()` 처리다.

```java
private double floorToGrid(double value) {
    double gridSize = properties.getHeatmapGridSize();
    return Math.floor(value / gridSize) * gridSize;
}
```

기본 `gridSize=0.01`이므로 좌표를 0.01도 단위 격자로 내림 정렬한다.
예를 들어 `lat=37.5234`이면 `Math.floor(37.5234 / 0.01) * 0.01 = 37.52`가 된다.

집계 키는 `(gridLat, gridLon, minuteBucketStart)` 조합이다.
write 로직은 EVENTS_PER_MINUTE와 동일한 패턴이다.

## OverwriteMode별 동작 정리

| overwriteMode | DEVICE_LAST_SEEN | EVENTS_PER_MINUTE / DRIVING_EVENT_COUNTER / REGION_HEATMAP |
|---|---|---|
| `MERGE` (기본) | 기존보다 최신 eventTime이면 update | 기존 있으면 count 덮어쓰기, 없으면 insert |
| `SKIP_EXISTING` | `ON CONFLICT DO NOTHING` | 기존 있으면 skip, 없으면 insert |
| `OVERWRITE` | (지원 안 함, MERGE와 동일 동작) | 먼저 시간 범위 delete 후 insert |

`DEVICE_LAST_SEEN`에서 `OVERWRITE` 모드를 코드로 처리하지 않아 실제로는 `MERGE`와 동일하게 동작한다.

## 설정 구조

```yaml
telemetryhub:
  backfill:
    default-lookback: P1D           # ISO-8601 Duration, 기본 1일
    chunk-size: 500
    dry-run: true                   # 기본 dryRun=true, DB에 쓰지 않음
    source-type: RAW_TOPIC_EXPORT   # RAW_TOPIC_EXPORT | RAW_ARCHIVE
    overwrite-mode: MERGE           # MERGE | OVERWRITE | SKIP_EXISTING
    heatmap-grid-size: 0.01         # 위도/경도 격자 단위 (도)
    raw-topic-export-path: data/telemetryhub/raw-topic-export.ndjson
    raw-archive-path: data/telemetryhub/raw-archive.ndjson
    fallback-to-stub-when-source-missing: true
    default-targets:
      - DEVICE_LAST_SEEN
      - EVENTS_PER_MINUTE
      - DRIVING_EVENT_COUNTER
      - REGION_HEATMAP
```

## 현재 코드에서 주의할 점

1. **기본값은 dryRun=true**: 설정을 바꾸지 않으면 DB에 아무것도 쓰지 않는다. 실제 backfill을 수행하려면 `dry-run: false`로 설정해야 한다.

2. **plan을 두 step에서 각각 새로 생성**: `prepareBackfillPlanStep`과 `executeBackfillDryRunStep`이 각각 `prepareDefaultPlan()`을 호출해 plan을 새로 만든다. 두 번 `Instant.now()`가 찍히므로 from/to 범위가 미세하게 다를 수 있다.

3. **MERGE 모드에서 청크 간 덮어쓰기**: 같은 집계 키(minute bucket)가 여러 청크에 걸쳐 있으면, 나중 청크의 count가 앞 청크 count를 덮어쓴다. 합산이 아니라 replace다. 파일 전체를 한 번에 집계하지 않고 청크 단위로 처리하는 한계다.

4. **DRIVING_EVENT_COUNTER, REGION_HEATMAP은 payload 재파싱**: Stub fallback 시 payload가 더미 JSON이므로 파싱 실패가 발생한다. stub을 통한 dry-run에서 이 두 writer는 정상 동작하지 않는다.

5. **DEVICE_LAST_SEEN의 source_topic은 항상 "batch-backfill"**: 원본 MQTT topic 정보가 유실된다. stream-processor가 실시간으로 쓴 레코드와 source_topic 값이 다르다.

6. **OVERWRITE 모드의 DEVICE_LAST_SEEN 미처리**: `OVERWRITE` 케이스가 switch 문에 없어 `MERGE`와 동일하게 동작한다. 시간 범위 delete 없이 update만 한다.

7. **N+1 쿼리**: EVENTS_PER_MINUTE, DRIVING_EVENT_COUNTER, REGION_HEATMAP writer는 집계 키별로 `select count(*)`와 insert/update를 개별 실행한다. 청크 내 집계 키 수만큼 쿼리가 발생한다.

## 요약

`batch-backfill`은 다음 세 가지 역할을 합친 배치 애플리케이션이다.

```text
소스 파일 읽기 (NDJSON, 청크 단위 streaming)
  + 이벤트 집계 (eventType/device/좌표/분 단위)
  + 집계 테이블 backfill (MERGE | OVERWRITE | SKIP_EXISTING)
```

실제 실행 흐름을 이해할 때는 `BatchBackfillJobConfig → RoutingBackfillSourceReader → RoutingBackfillTargetWriter → 각 target writer` 순서로 따라가면 된다.
기본 설정이 `dryRun=true`, `fallbackToStub=true`이므로 파일이 없어도 기동은 되지만 실제 DB 반영은 없다.