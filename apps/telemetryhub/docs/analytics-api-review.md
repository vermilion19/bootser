# TelemetryHub Analytics API Review

## 목적
`analytics-api`는 `stream-processor`가 만들어 둔 read model 테이블을 조회 전용 API로 노출하는 서비스다.

이 서비스의 목표는 단순하다.

- write 경로와 조회 경로를 분리한다.
- stream-processor 내부 구현과 느슨하게 연결된다.
- 운영에서 필요한 조회 API를 별도 서비스로 제공한다.

## 전체 구조
```text
AnalyticsController
  -> AnalyticsQueryService
     -> native SQL
        -> read model tables
```

## 왜 native query를 쓰는가
현재 `analytics-api`는 `stream-processor`의 entity를 직접 참조하지 않는다.

이렇게 한 이유는 다음과 같다.

- 서비스 간 컴파일 결합을 낮추기 위해
- 조회 전용 서비스로 유지하기 위해
- read model 테이블이 바뀌더라도 query 레이어만 수정하면 되게 하기 위해

## 조회 대상 테이블
- `telemetryhub_device_last_seen`
- `telemetryhub_events_per_minute`
- `telemetryhub_driving_event_counter`
- `telemetryhub_region_heatmap`

## 핵심 클래스 역할
### `AnalyticsController`
- 조회 API 진입점이다.
- 요청 파라미터를 받고 서비스 결과를 응답 DTO로 변환한다.

### `AnalyticsQueryService`
- native query를 실행한다.
- optional 필터 조건을 동적으로 붙인다.
- DB 결과를 view model로 매핑한다.

## API 상세 설명
## `GET /analytics/v1/devices/latest`
최근 이벤트 기준으로 최신 device 상태 목록을 조회한다.

### 쿼리 파라미터
- `limit`
  - 기본값 `20`
  - 최소 `1`, 최대 `200`

### 응답 필드
- `deviceId`
  - 디바이스 ID
- `lastEventId`
  - 마지막 이벤트 ID
- `lastEventType`
  - 마지막 이벤트 타입
- `lastEventTime`
  - 마지막 이벤트 발생 시각
- `lastIngestTime`
  - 마지막으로 ingestion된 시각
- `sourceTopic`
  - 이벤트가 들어온 원본 topic

### 실제 동작 로직
1. `AnalyticsController.latestDevices()`가 `limit`를 받는다.
2. `AnalyticsQueryService.getLatestDevices(limit)`를 호출한다.
3. SQL은 `telemetryhub_device_last_seen`를 `last_event_time desc`로 정렬한다.
4. `limit :limit`을 적용한다.
5. 결과를 `LatestDeviceResponse`로 변환한다.

### 용도
- 최근 활성 디바이스 목록 확인
- 대시보드 첫 화면
- 운영자가 마지막 이벤트 기준으로 장비 상태 점검

## `GET /analytics/v1/devices/{deviceId}/latest`
특정 디바이스의 최신 상태를 조회한다.

### 경로 파라미터
- `deviceId`
  - 조회할 디바이스 ID

### 응답 필드
`GET /analytics/v1/devices/latest`의 단건 버전과 동일하다.

### 실제 동작 로직
1. `AnalyticsController.latestDevice(deviceId)`가 호출된다.
2. `AnalyticsQueryService.getLatestDevice(deviceId)`가 실행된다.
3. SQL은 `where device_id = :deviceId` 조건으로 한 건을 찾는다.
4. 결과가 없으면 현재 구현 기준 `null` payload가 될 수 있다.

### 주의할 점
- 현재는 not found를 별도 예외로 바꾸지 않는다.
- API 사용 측에서 null 결과를 염두에 두는 편이 안전하다.

## `GET /analytics/v1/events-per-minute`
분 단위 이벤트 집계를 조회한다.

### 쿼리 파라미터
- `from`
  - 필수
  - ISO-8601 시각
- `to`
  - 필수
  - ISO-8601 시각
- `eventType`
  - 선택
  - `TELEMETRY`, `DEVICE_HEALTH`, `DRIVING_EVENT`

### 응답 필드
- `eventType`
  - 이벤트 타입
- `minuteBucketStart`
  - 분 버킷 시작 시각
- `eventCount`
  - 해당 버킷 카운트

### 실제 동작 로직
1. `AnalyticsController.eventsPerMinute(...)`가 기간과 optional eventType을 받는다.
2. `AnalyticsQueryService.getEventsPerMinute(from, to, eventType)`를 호출한다.
3. SQL은 `telemetryhub_events_per_minute`에서 `minute_bucket_start between :from and :to`를 건다.
4. `eventType`이 있으면 `and event_type = :eventType`를 추가한다.
5. 정렬은 `minute_bucket_start asc, event_type asc`다.

### 용도
- 시간대별 유입량 관측
- 이벤트 타입별 볼륨 비교
- 대시보드 시계열 그래프

## `GET /analytics/v1/driving-events/counters`
급정거, 충격, 과속 같은 driving event 집계를 조회한다.

### 쿼리 파라미터
- `from`
  - 필수
- `to`
  - 필수
- `deviceId`
  - 선택
  - 특정 디바이스만 필터링
- `drivingEventType`
  - 선택
  - 특정 driving event subtype만 필터링

### 응답 필드
- `deviceId`
  - 디바이스 ID
- `drivingEventType`
  - driving event subtype
- `minuteBucketStart`
  - 분 버킷 시작 시각
- `eventCount`
  - 해당 버킷 카운트

### 실제 동작 로직
1. `AnalyticsController.drivingEventCounters(...)`가 기간과 optional 필터를 받는다.
2. `AnalyticsQueryService.getDrivingEventCounters(...)`를 호출한다.
3. SQL은 `telemetryhub_driving_event_counter`를 조회한다.
4. `deviceId`가 있으면 `and device_id = :deviceId`
5. `drivingEventType`이 있으면 `and driving_event_type = :drivingEventType`
6. 정렬은 `minute_bucket_start asc, device_id asc`

### 용도
- 특정 차량의 위험 이벤트 추세 확인
- 전체 fleet에서 특정 위험 이벤트 집중 구간 분석

## `GET /analytics/v1/heatmap/regions`
지역 히트맵 집계를 조회한다.

### 쿼리 파라미터
- `from`
  - 필수
- `to`
  - 필수
- `minLat`
  - 선택
- `maxLat`
  - 선택
- `minLon`
  - 선택
- `maxLon`
  - 선택

### 응답 필드
- `gridLat`
  - grid 기준 위도
- `gridLon`
  - grid 기준 경도
- `minuteBucketStart`
  - 분 버킷 시작 시각
- `eventCount`
  - 해당 grid와 시간 버킷의 카운트

### 실제 동작 로직
1. `AnalyticsController.regionHeatmap(...)`가 기간과 optional bounding box를 받는다.
2. `AnalyticsQueryService.getRegionHeatmap(...)`를 호출한다.
3. SQL은 `telemetryhub_region_heatmap`를 조회한다.
4. 위도/경도 필터가 있으면 각각 조건을 붙인다.
5. `group by grid_lat, grid_lon, minute_bucket_start`
6. `sum(event_count)`로 집계한 뒤 반환한다.
7. 정렬은 `minute_bucket_start asc, total_event_count desc`

### 용도
- 특정 지역에서 이벤트 밀도가 높은 구간 확인
- 지도 시각화용 기초 데이터

## API 사용 시 주의점
### 시간 파라미터
- `from`, `to`는 모두 ISO-8601 형식이어야 한다.
- 예: `2026-04-24T10:00:00Z`

### 페이징
- 현재는 정식 pagination이 없다.
- `devices/latest`만 `limit`를 받는다.

### 캐시
- 현재 조회 결과 캐시는 없다.
- 요청 빈도가 매우 높아지면 query layer 최적화가 필요할 수 있다.

## 현재 시점 평가
`analytics-api`는 조회 전용 서비스로서 MVP 역할을 충분히 수행한다.

지금 가능한 것:
- 최신 디바이스 상태 조회
- 분당 이벤트 집계 조회
- driving event 집계 조회
- 지역 히트맵 조회

아직 남아 있는 것:
- 안전 점수 조회
- 주행 리포트 조회
- 정렬/페이징 강화
- 고비용 쿼리 최적화
