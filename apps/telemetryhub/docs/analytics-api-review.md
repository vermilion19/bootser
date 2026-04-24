# TelemetryHub Analytics API Review

## 목적
`analytics-api`는 `stream-processor`가 적재한 read model을 조회 전용으로 노출하는 서비스다.

현재 구현은 read model 조회에만 집중한 최소 골격이다. 핵심 목적은 다음 두 가지다.

1. `stream-processor`와 컴파일 레벨로 강하게 결합하지 않는다.
2. serving query를 별도 서비스에서 제공할 수 있는 경계를 먼저 만든다.

## 현재 구조
- [AnalyticsApiApplication.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/AnalyticsApiApplication.java)
  - 애플리케이션 시작점
  - `StorageDbConfig`를 import 해서 DB 접근 설정을 공유한다.
- [AnalyticsQueryService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/application/AnalyticsQueryService.java)
  - read model 테이블을 native query로 읽는 조회 서비스
- [AnalyticsController.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/web/AnalyticsController.java)
  - 조회 API 진입점

## 왜 native query인가
현재 `analytics-api`는 `stream-processor`의 JPA entity를 직접 참조하지 않는다.

이렇게 한 이유:
- 서비스 간 컴파일 결합을 줄이기 위해서
- `analytics-api`를 조회 전용 서비스로 유지하기 위해서
- 나중에 read model 저장 방식이 바뀌어도 서비스 경계를 유지하기 위해서

즉 현재 구조는 `analytics-api -> read model table` 경로만 알고, `stream-processor` 내부 구현은 모르는 상태다.

## 현재 API

### 1. 최신 디바이스 상태
- `GET /analytics/v1/devices/latest`
- `GET /analytics/v1/devices/{deviceId}/latest`

조회 대상 테이블:
- `telemetryhub_device_last_seen`

목적:
- 디바이스별 마지막 이벤트 시각과 ingest 시각 확인
- 기본적인 최근 활동 조회

### 2. 분당 이벤트 집계
- `GET /analytics/v1/events-per-minute`

조회 대상 테이블:
- `telemetryhub_events_per_minute`

파라미터:
- `from`
- `to`
- `eventType` optional

목적:
- 시간 구간별 event type 유입량 조회

### 3. driving event 카운터
- `GET /analytics/v1/driving-events/counters`

조회 대상 테이블:
- `telemetryhub_driving_event_counter`

파라미터:
- `from`
- `to`
- `deviceId` optional
- `drivingEventType` optional

목적:
- 급정거 / 충격 / 과속 같은 driving event 집계 조회

### 4. 지역 히트맵
- `GET /analytics/v1/heatmap/regions`

조회 대상 테이블:
- `telemetryhub_region_heatmap`

파라미터:
- `from`
- `to`
- `minLat` optional
- `maxLat` optional
- `minLon` optional
- `maxLon` optional

목적:
- telemetry 위치 이벤트를 고정 grid 버킷 단위로 집계한 지역별 분포 조회
- 가장 단순한 heatmap serving API 제공

## scale 관점 평가
현재 구현은 scale-readiness 기준에서 비교적 안전한 방향이다.

좋은 점:
- stateless 하다
- read model 조회 전용이다
- write 경로와 분리되어 있다
- stream-processor state store에 직접 의존하지 않는다

주의점:
- 현재는 native query 기반이라 조회 패턴이 많아지면 전용 repository 또는 query layer 정리가 필요할 수 있다
- 캐시, pagination, 인덱스 최적화는 아직 없다
- 고비용 조합 조회는 아직 없다
- heatmap은 현재 geohash가 아니라 고정 소수점 grid 기반이다

## 다음 단계
- 안전 점수 / 주행 리포트 / 지역 히트맵 조회 모델 추가
- 안전 점수 / 주행 리포트 조회 모델 추가
- pagination 및 정렬 정책 추가
- API별 쿼리 비용 점검
- 필요 시 전용 query repository 또는 projection DTO 최적화
