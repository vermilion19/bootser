# TelemetryHub Stream Processor Design

## 목적
`stream-processor`는 Kafka raw topic에 적재된 이벤트를 실시간 집계로 변환하고, 그 결과를 state store와 read model 테이블에 반영하는 서비스다.

현재 이 서비스는 다음 집계를 담당한다.

- `device_last_seen`
- `events_per_minute`
- `driving_event_counter`
- `region_heatmap`

## 전체 구조
```text
Kafka raw topic
  -> Kafka Streams topology
     -> dedup / late-event filtering
     -> aggregate
     -> state store
     -> projection writer
        -> read model upsert
     -> metrics collector
```

## 현재 구현 포인트
### dedup
- `RawEventDeduplicationSupport`가 `eventId` 기준 중복 이벤트를 걸러낸다.
- count 계열 topology에 우선 적용되어 있다.

### late-event 필터
- `LateEventPolicySupport`가 `ingestTime - eventTime <= lateEventGrace` 규칙으로 늦게 온 이벤트를 걸러낸다.

### read model write 보호
- `device_last_seen` upsert는 더 오래된 이벤트가 최신 상태를 덮지 못하도록 `eventTime`, `ingestTime` 기준 조건을 둔다.

### 운영 관점
- Kafka Streams state dir는 지속 저장소를 사용하도록 외부 설정을 받는다.
- projection write 성공/실패는 메트릭으로 관측한다.

## 핵심 클래스 역할
### `DeviceLastSeenTopology`
- raw event를 읽어 device별 마지막 이벤트 상태를 유지한다.

### `EventsPerMinuteTopology`
- 이벤트를 분 단위 버킷으로 잘라 event type별 카운트를 올린다.

### `DrivingEventCounterTopology`
- `DRIVING_EVENT`만 대상으로 device + event subtype + minute bucket 기준 카운트를 올린다.

### `RegionHeatmapTopology`
- `TELEMETRY` 좌표를 grid로 내린 뒤 분 단위로 카운트한다.

### `Jpa*ProjectionWriter`
- topology 결과를 DB upsert로 반영한다.

### `StreamProcessorMetricsCollector`
- projection write 성공/실패를 projection 종류별로 수집한다.
- Micrometer counter도 함께 올린다.

### `StreamProcessorController`
- 현재는 메트릭 조회 API만 제공한다.

## API 상세 설명
## `GET /stream/v1/metrics`
stream-processor의 projection write 상태를 조회하는 API다.

### 요청 파라미터
- 없음

### 응답 필드
- `totalProjectionWrites`
  - 전체 projection write 성공 수
- `totalProjectionWriteFailures`
  - 전체 projection write 실패 수
- `deviceLastSeen`
  - `device_last_seen` projection별 통계
- `eventsPerMinute`
  - `events_per_minute` projection별 통계
- `drivingEventCounter`
  - `driving_event_counter` projection별 통계
- `regionHeatmap`
  - `region_heatmap` projection별 통계
- `lastSuccessTime`
  - 마지막 projection write 성공 시각
- `lastFailureTime`
  - 마지막 projection write 실패 시각
- `lastFailureProjection`
  - 마지막으로 실패한 projection 이름
- `lastFailureReason`
  - 마지막 실패 사유

### projection 하위 필드
각 projection 객체는 아래 두 필드를 가진다.

- `writes`
  - 해당 projection의 성공 write 수
- `failures`
  - 해당 projection의 실패 수

### 실제 동작 로직
1. `StreamProcessorController.metrics()`가 호출된다.
2. `StreamProcessorMetricsCollector.snapshot()`이 현재 스냅샷을 반환한다.
3. `StreamProcessorMetricsResponse.from(...)`이 응답 DTO로 변환한다.

### 메트릭이 쌓이는 위치
- 각 `Jpa*ProjectionWriter`에서 DB upsert 성공 시 `recordProjectionWriteSuccess(...)`
- 예외 발생 시 `recordProjectionWriteFailure(...)`

### 운영에서 보는 법
- `totalProjectionWriteFailures`가 증가하면 projection write에 문제가 생긴 것이다.
- 특정 projection의 `failures`만 증가하면 해당 read model 테이블이나 SQL upsert가 병목일 가능성이 크다.
- `lastFailureProjection`, `lastFailureReason`으로 마지막 실패 지점을 빠르게 좁힐 수 있다.

## 메트릭과 Micrometer 연계
현재 다음 counter가 올라간다.

- `telemetryhub.stream.projection.write.success`
- `telemetryhub.stream.projection.write.failure`

각 counter에는 `projection` tag가 붙는다.

## 현재 시점 평가
`stream-processor`는 설계 문서 수준을 넘어서 실제 Kafka Streams topology, state store, read model upsert, 운영 메트릭 API까지 들어간 상태다.

지금 가능한 것:
- raw topic 실시간 집계
- dedup
- late-event grace filtering
- read model upsert
- projection 실패 관측

아직 남아 있는 것:
- 실제 운영 부하 기준 smoke test
- Kafka partition 수와 thread 수 튜닝
- write batching이나 비동기 flush 전략 검토
