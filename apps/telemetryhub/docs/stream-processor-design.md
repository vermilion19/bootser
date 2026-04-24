# TelemetryHub Stream Processor Design

## 목적
`stream-processor`는 `ingestion-service`가 Kafka raw topic에 적재한 이벤트를 실시간 집계로 변환하고, 그 결과를 state store와 read model 테이블에 반영하는 모듈이다.

이 모듈의 현재 목표는 다음 세 가지를 먼저 안정적으로 만드는 것이다.

1. raw topic을 어떤 방식으로 읽을지 고정
2. 어떤 집계를 어떤 key 기준으로 만들지 고정
3. 집계 결과를 어디까지 저장할지 고정

## MVP 집계 범위
현재 MVP 실시간 집계는 아래 세 가지다.

1. `device_last_seen`
2. `events_per_minute`
3. `driving_event_counter`

이후 가장 단순한 serving 확장을 위해 `region_heatmap` 집계가 추가되었다.

이 집계 계획은 [DefaultStreamTopologyPlanner.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/application/DefaultStreamTopologyPlanner.java) 에서 코드로 고정되어 있다.

## 전체 구조
```text
Kafka raw topic
  -> Kafka Streams topology
     -> state store materialization
     -> projection writer
        -> read model table upsert
```

구현 단위는 아래와 같다.

```text
StreamProcessorApplication
  -> StreamProcessorProperties
  -> StreamTopologyPlanner
     -> DefaultStreamTopologyPlanner
  -> DeviceLastSeenTopology
  -> EventsPerMinuteTopology
  -> DrivingEventCounterTopology
  -> Jpa*ProjectionWriter
  -> JPA read model entities / repositories
```

## 패키지별 역할

### bootstrap
- `StreamProcessorApplication`
  - Spring Boot 시작점
  - `StorageDbConfig`를 import 해서 JPA 설정을 함께 로딩

### config
- `StreamProcessorProperties`
  - source topic
  - application id
  - 각 집계 window
  - late event grace
- `KafkaStreamsConfig`
  - Kafka Streams 기본 설정 bean

기본 설정은 [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/resources/application.yml) 에 들어 있다.

- source topic: `telemetryhub.raw-events`
- device last seen window: `5m`
- events per minute window: `1m`
- driving event counter window: `1m`
- late event grace: `2m`

### application
- `AggregationPlan`
  - 집계 하나의 정의
- `StreamTopologyPlan`
  - 전체 topology 계획
- `StreamTopologyPlanner`
  - topology 계획 제공 인터페이스
- `DefaultStreamTopologyPlanner`
  - 현재 MVP 집계 계획 구현
- `DeviceLastSeenProjectionWriter`
- `EventsPerMinuteProjectionWriter`
- `DrivingEventCounterProjectionWriter`
- `RegionHeatmapProjectionWriter`
  - topology에서 DB upsert를 직접 모르도록 분리한 쓰기 포트

### domain
- `RawEventMessage`
  - raw topic value 모델
- `DeviceLastSeenAggregate`
- `EventsPerMinuteKey`
- `EventsPerMinuteAggregate`
- `DrivingEventCounterKey`
- `DrivingEventCounterAggregate`
- `RegionHeatmapKey`
- `RegionHeatmapAggregate`
  - Kafka Streams 집계 중간 모델

### domain.entity / domain.repository
- `DeviceLastSeenEntity` / `DeviceLastSeenRepository`
- `EventsPerMinuteEntity` / `EventsPerMinuteRepository`
- `DrivingEventCounterEntity` / `DrivingEventCounterRepository`
- `RegionHeatmapEntity` / `RegionHeatmapRepository`
  - read model 저장용 JPA entity와 repository

### infrastructure
- `DeviceLastSeenTopology`
- `EventsPerMinuteTopology`
- `DrivingEventCounterTopology`
  - 실제 Kafka Streams topology 정의
- `JsonSerdeFactory`
  - JSON serde 생성
- `DrivingEventCounterProjection`
- `RegionHeatmapProjection`
- raw payload에서 driving event key를 추출
- raw payload에서 telemetry 좌표를 grid key로 변환
- `JpaDeviceLastSeenProjectionWriter`
- `JpaEventsPerMinuteProjectionWriter`
- `JpaDrivingEventCounterProjectionWriter`
- `JpaRegionHeatmapProjectionWriter`
  - write port 구현체
- `StreamProcessorMetricsCollector`
  - projection write 성공/실패 관측 수집기
- `StreamProcessorController`
  - `/stream/v1/metrics` API 제공

## 현재 집계 설계

### 1. device_last_seen
- source event types
  - `TELEMETRY`
  - `DEVICE_HEALTH`
  - `DRIVING_EVENT`
- 목적
  - 디바이스별 가장 최신 이벤트 시각과 ingest 시각 유지
  - 이후 online/offline 판별의 기초 데이터로 사용
- state store
  - `device-last-seen-store`
- target table
  - `telemetryhub_device_last_seen`

처리 방식:
- raw event를 읽는다
- `deviceId` 기준으로 key를 재설정한다
- `DeviceLastSeenAggregate`로 변환한다
- 더 최신 `eventTime` / `ingestTime` 기준으로 merge 한다
- state store에 반영한다
- projection writer가 read model 테이블에 upsert 한다

### 2. events_per_minute
- source event types
  - `TELEMETRY`
  - `DEVICE_HEALTH`
  - `DRIVING_EVENT`
- 목적
  - 분 단위 수신량 집계
  - event type별 유입량 관측
- state store
  - `events-per-minute-store`
- target table
  - `telemetryhub_events_per_minute`

처리 방식:
- raw event를 읽는다
- `eventTime`을 분 단위 bucket으로 자른다
- `eventType + minuteBucketStart` 기준 key를 만든다
- count를 증가시킨다
- state store에 반영한다
- projection writer가 read model 테이블에 upsert 한다

### 3. driving_event_counter
- source event types
  - `DRIVING_EVENT`
- 목적
  - 급정거, 과속, 충격 같은 driving event 카운트 집계
- state store
  - `driving-event-counter-store`
- target table
  - `telemetryhub_driving_event_counter`

처리 방식:
- raw event를 읽는다
- `DRIVING_EVENT`만 필터링한다
- payload를 `DrivingEvent` 기준으로 해석해 event subtype을 뽑는다
- `deviceId + drivingEventType + minuteBucketStart` 기준 key를 만든다
- count를 증가시킨다
- state store에 반영한다
- projection writer가 read model 테이블에 upsert 한다

### 4. region_heatmap
- source event types
  - `TELEMETRY`
- 목적
  - 위치 이벤트를 고정 grid bucket 기준으로 집계
  - 지역별 이벤트 밀도 조회의 기초 데이터 제공
- state store
  - `region-heatmap-store`
- target table
  - `telemetryhub_region_heatmap`

처리 방식:
- raw event를 읽는다
- `TELEMETRY`만 필터링한다
- payload를 `TelemetryEvent`로 파싱한다
- `lat/lon`을 `0.01` grid bucket으로 내린다
- `gridLat + gridLon + minuteBucketStart` 기준 key를 만든다
- count를 증가시킨다
- state store에 반영한다
- projection writer가 read model 테이블에 upsert 한다

## DB upsert 경계
이번 단계에서 추가된 핵심은 `state store만 채우는 topology`에서 `read model table까지 반영하는 topology`로 한 단계 더 나아간 것이다.

설계 의도는 단순하다.

- topology는 aggregate를 만든다
- DB write는 writer port가 맡는다
- JPA 구현은 infrastructure adapter가 맡는다

즉 Kafka Streams 코드가 repository를 직접 모르도록 한 뒤, 아래 경계로 연결했다.

```text
Topology
  -> ProjectionWriter port
     -> JpaProjectionWriter
        -> Repository
           -> Read model table
```

이 방식의 장점:
- topology 코드와 저장 구현을 분리할 수 있다
- 나중에 JPA 대신 다른 저장 방식으로 바꾸기 쉽다
- read model 테이블 이름과 unique key를 명시적으로 고정할 수 있다

현재 upsert 방식:
- `device_last_seen`
  - `deviceId` 기준 조회 후 update 또는 insert
- `events_per_minute`
  - `eventType + minuteBucketStart` 기준 조회 후 update 또는 insert
- `driving_event_counter`
  - `deviceId + drivingEventType + minuteBucketStart` 기준 조회 후 update 또는 insert

## observability / metrics
현재 `stream-processor`는 projection write 결과를 별도로 관측한다.

수집 항목:
- 전체 projection write 성공 수
- 전체 projection write 실패 수
- `device_last_seen` write/실패 수
- `events_per_minute` write/실패 수
- `driving_event_counter` write/실패 수
- 마지막 성공 시각
- 마지막 실패 시각
- 마지막 실패 projection
- 마지막 실패 이유

이 값은 [StreamProcessorMetricsCollector.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/application/StreamProcessorMetricsCollector.java) 에서 관리한다.

또한 Micrometer counter도 함께 올린다.

- `telemetryhub.stream.projection.write.success`
- `telemetryhub.stream.projection.write.failure`

각 counter에는 `projection` tag가 붙는다.

외부 조회는 아래 API로 가능하다.

- `GET /stream/v1/metrics`

이 API는 [StreamProcessorController.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/web/StreamProcessorController.java) 에서 제공한다.

## out-of-order / late event 처리 방향
현재 설계는 Planning에서 정한 기준을 그대로 따른다.

- 기준 시간은 `eventTime`
- 늦게 도착한 이벤트는 grace period 안에서는 반영 가능
- grace period 밖 이벤트는 실시간 처리보다 batch/backfill 보정 대상으로 넘긴다

현재 기본 grace는 `2m` 이다.

다만 현재 구현은 `eventTime` 버킷팅과 latest merge 중심의 1차 구현이다. dedup, watermark, suppression 같은 고급 처리는 아직 들어가지 않았다.

## 현재 시점 평가
현재 `stream-processor`는 MVP 기준으로 다음을 갖췄다.

- Kafka raw topic 읽기
- JSON serde
- 실시간 집계 3종 state store materialization
- read model JPA entity / repository
- topology 결과를 DB에 upsert 하는 writer 경계
- projection write metrics 및 조회 API

즉 지금은 `stream-processor skeleton` 수준을 넘어서, `state store + read model` 까지 닿는 1차 구현 상태라고 볼 수 있다.

## 남은 작업
- write 실패 처리 정책 정리
- dedup / late event 정책 구체화
- state store 조회 또는 downstream serving 전략 정리
- `analytics-api`와 read model 연결
- batch backfill과의 정합성 전략 정리
