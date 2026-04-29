# Analytics API 상세 구현 및 동작 정리

이 문서는 `apps/telemetryhub/analytics-api`의 실제 코드 기준 동작을 정리한다.
기존 `analytics-api-review.md`가 패키지 구조와 클래스 목록 중심이라면, 이 문서는 각 API가 어떤 테이블을 어떻게 쿼리하는지, 필터링이 어떻게 동작하는지에 초점을 둔다.

## 전체 실행 흐름

```text
HTTP GET /analytics/v1/**
  -> AnalyticsController
  -> AnalyticsQueryService
  -> EntityManager.createNativeQuery()
  -> PostgreSQL 집계 테이블 직접 조회
  -> View record 매핑
  -> Response DTO 변환
```

모든 API는 단일 `AnalyticsQueryService`를 통해 처리된다.
별도의 Repository 인터페이스 없이 `EntityManager`로 native SQL을 직접 실행한다.

## 조회 대상 테이블

analytics-api는 stream-processor가 집계해서 쓴 4개의 테이블을 읽는다.

| 테이블 | 내용 | 집계 단위 |
|---|---|---|
| `telemetryhub_device_last_seen` | 디바이스별 마지막 수신 이벤트 정보 | device 단위 |
| `telemetryhub_events_per_minute` | 분당 이벤트 수 | eventType + 분 단위 |
| `telemetryhub_driving_event_counter` | 운전 이벤트 발생 수 | deviceId + drivingEventType + 분 단위 |
| `telemetryhub_region_heatmap` | 지역별 이벤트 밀도 | gridLat + gridLon + 분 단위 |

analytics-api는 이 테이블에 **쓰지 않는다**. 읽기 전용이다.

## API별 상세 동작

### GET /analytics/v1/devices/latest

최근 이벤트를 수신한 디바이스 목록을 반환한다.

```sql
select device_id,
       last_event_id,
       last_event_type,
       last_event_time,
       last_ingest_time,
       source_topic
from telemetryhub_device_last_seen
order by last_event_time desc
limit :limit
```

- 기본 limit은 20, 최대 200 (`@Max(200)`)
- `last_event_time` 내림차순 정렬이므로 가장 최근에 이벤트를 보낸 device가 먼저 온다
- 필터 조건 없음, 전체 조회

### GET /analytics/v1/devices/{deviceId}/latest

특정 device의 마지막 이벤트 정보를 반환한다.

```sql
select ...
from telemetryhub_device_last_seen
where device_id = :deviceId
```

해당 device의 기록이 없으면 `results.isEmpty()`가 true가 되고, `null`을 반환한다.
Controller에서 별도 처리 없이 `LatestDeviceResponse.from(null)`이 호출되는데, `from()` 메서드에서 null 체크가 있다.

```java
public static LatestDeviceResponse from(LatestDeviceView view) {
    if (view == null) {
        return null;
    }
    ...
}
```

따라서 device가 없으면 응답 body의 `data` 필드가 `null`로 내려간다.

### GET /analytics/v1/events-per-minute

시간 구간 내 분당 이벤트 수를 반환한다.

```java
public ApiResponse<List<EventsPerMinuteResponse>> eventsPerMinute(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) EventType eventType
) {
```

`from`, `to`는 필수 파라미터다. `eventType`은 선택이며 null이면 전체 타입 조회.

SQL은 `eventType` 여부에 따라 동적으로 구성된다.

```java
StringBuilder sql = new StringBuilder("""
        select event_type, minute_bucket_start, event_count
        from telemetryhub_events_per_minute
        where minute_bucket_start between :from and :to
        """);

if (eventType != null) {
    sql.append(" and event_type = :eventType");
}

sql.append(" order by minute_bucket_start asc, event_type asc");
```

`between :from and :to` 조건이므로 from, to 양 끝값을 포함한다.
결과는 `minute_bucket_start` 오름차순 → `event_type` 오름차순 정렬이다.

결과 매핑 시 `row[0]`(String)을 `EventType.valueOf()`로 변환한다.
DB에 잘못된 eventType 문자열이 저장되어 있으면 `IllegalArgumentException`이 발생한다.

### GET /analytics/v1/driving-events/counters

운전 이벤트 발생 횟수를 반환한다.

```java
public ApiResponse<List<DrivingEventCounterResponse>> drivingEventCounters(
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestParam(required = false) String deviceId,
        @RequestParam(required = false) DrivingEventType drivingEventType
) {
```

`deviceId`, `drivingEventType` 둘 다 선택 필터다. SQL이 동적으로 구성된다.

```java
if (deviceId != null && !deviceId.isBlank()) {
    sql.append(" and device_id = :deviceId");
}
if (drivingEventType != null) {
    sql.append(" and driving_event_type = :drivingEventType");
}
sql.append(" order by minute_bucket_start asc, device_id asc");
```

`deviceId`는 null 체크와 함께 blank 체크도 한다(`!deviceId.isBlank()`).
빈 문자열로 요청하면 device 필터 없이 전체 조회된다.

### GET /analytics/v1/heatmap/regions

지역별 이벤트 밀도를 반환한다.

```java
public ApiResponse<List<RegionHeatmapResponse>> regionHeatmap(
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestParam(required = false) Double minLat,
        @RequestParam(required = false) Double maxLat,
        @RequestParam(required = false) Double minLon,
        @RequestParam(required = false) Double maxLon
) {
```

위도/경도 범위 필터 4개가 모두 선택적이다. 개별적으로 적용되므로 minLat만 주면 해당 위도 이상만 필터링된다.

핵심은 SQL에서 `group by`가 있다는 점이다.

```sql
select grid_lat, grid_lon, minute_bucket_start,
       sum(event_count) as total_event_count
from telemetryhub_region_heatmap
where minute_bucket_start between :from and :to
  [and grid_lat >= :minLat]
  [and grid_lat <= :maxLat]
  [and grid_lon >= :minLon]
  [and grid_lon <= :maxLon]
group by grid_lat, grid_lon, minute_bucket_start
order by minute_bucket_start asc, total_event_count desc
```

이미 집계된 테이블(`telemetryhub_region_heatmap`)에서 다시 `sum(event_count)`을 하는 이유는 같은 grid + minute에 여러 레코드가 쌓일 수 있기 때문이다. 조회 시점에 한 번 더 합산한다.

정렬은 `minute_bucket_start` 오름차순 → `total_event_count` 내림차순이다. 같은 시간대에서 이벤트가 많은 지역이 먼저 온다.

## 결과 매핑 구조

View record와 Response DTO가 1:1로 대응된다.

```text
native query result (Object[])
  -> View record (LatestDeviceView, EventsPerMinuteView, ...)
  -> Response DTO (LatestDeviceResponse, EventsPerMinuteResponse, ...)
```

`AnalyticsQueryService`가 `Object[]`를 View record로 변환하고, Controller에서 `ResponseDTO.from(view)`로 변환한다.

View와 Response의 필드가 동일하므로 사실상 변환 계층이 하나 더 있는 구조다.
현재 코드에서 View와 Response 사이에 가공 로직은 없다.

## Instant 변환 처리

native query 결과의 시간 타입은 DB 드라이버와 설정에 따라 다양하게 올 수 있다.
`AnalyticsQueryService`는 이를 처리하는 `toInstant()` 헬퍼를 갖고 있다.

```java
private Instant toInstant(Object value) {
    if (value instanceof Timestamp timestamp) {
        return timestamp.toInstant();
    }
    if (value instanceof java.time.OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant();
    }
    if (value instanceof java.time.LocalDateTime localDateTime) {
        return localDateTime.toInstant(java.time.ZoneOffset.UTC);
    }
    if (value instanceof Instant instant) {
        return instant;
    }
    throw new IllegalStateException("Unsupported time value: " + value);
}
```

PostgreSQL JDBC 드라이버는 `timestamp with time zone` 컬럼을 `Timestamp`로 반환한다.
`LocalDateTime`은 `ZoneOffset.UTC`를 가정해 변환한다.

## API별 역할 요약

| API | 필수 파라미터 | 선택 파라미터 | 조회 테이블 |
|---|---|---|---|
| `GET /analytics/v1/devices/latest` | - | `limit` (기본 20) | `telemetryhub_device_last_seen` |
| `GET /analytics/v1/devices/{deviceId}/latest` | `deviceId` (path) | - | `telemetryhub_device_last_seen` |
| `GET /analytics/v1/events-per-minute` | `from`, `to` | `eventType` | `telemetryhub_events_per_minute` |
| `GET /analytics/v1/driving-events/counters` | `from`, `to` | `deviceId`, `drivingEventType` | `telemetryhub_driving_event_counter` |
| `GET /analytics/v1/heatmap/regions` | `from`, `to` | `minLat`, `maxLat`, `minLon`, `maxLon` | `telemetryhub_region_heatmap` |

## 현재 코드에서 주의할 점

1. **device 없을 때 null 반환**: `GET /analytics/v1/devices/{deviceId}/latest`에서 device 기록이 없으면 `data: null`이 응답된다. 404 처리 없이 200 OK와 null body를 반환한다.

2. **heatmap의 이중 집계**: `telemetryhub_region_heatmap`은 이미 집계 테이블이지만, 조회 시 `sum(event_count) group by`를 다시 적용한다. 같은 grid+minute에 레코드가 중복될 수 있는 구조임을 의미한다.

3. **시간 범위 `between` 포함**: `between :from and :to`는 from, to를 모두 포함한다. from과 to가 동일하면 해당 시각의 레코드만 조회된다.

4. **deviceId blank 처리**: `deviceId=` (빈 문자열)로 요청하면 device 필터가 적용되지 않고 전체가 조회된다. null과 빈 문자열을 동일하게 처리한다.

5. **DB 타입 불일치 시 예외**: native query 결과를 `EventType.valueOf()`, `DrivingEventType.valueOf()`로 변환하는 부분이 있다. DB에 잘못된 문자열이 들어있으면 런타임에 `IllegalArgumentException`이 발생한다.