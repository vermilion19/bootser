# TelemetryHub Load Test Tuning Guide

이 문서는 `apps/telemetryhub/load-test/scripts` 아래 k6 테스트 스크립트를 수정할 때 어떤 값을 조정해야 하는지 설명한다.

## 공통 개념

### `stages`

`stages`는 시간이 지남에 따라 부하를 어떻게 올리고 내릴지 정의한다.

```js
stages: [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 100 },
]
```

- `duration`: 해당 단계가 유지되는 시간
- `target`: executor에 따라 VU 수 또는 초당 요청 수를 의미한다

`ramping-vus`에서는 `target`이 동시 사용자 수다. `ramping-arrival-rate`에서는 `target`이 초당 요청 수, 즉 RPS다.

### `thresholds`

`thresholds`는 테스트 성공/실패 기준이다.

```js
thresholds: {
  http_req_duration: ['p(95)<1000'],
  http_req_failed: ['rate<0.01'],
}
```

- `p(95)<1000`: 전체 요청 중 95%가 1000ms보다 빨라야 한다.
- `rate<0.01`: 실패율이 1%보다 낮아야 한다.
- `abortOnFail: true`: 기준을 넘으면 테스트를 중간에 중단한다.
- `delayAbortEval`: 테스트 시작 직후 워밍업 구간은 실패 판단에서 제외한다.

threshold를 완화하면 테스트가 통과하기 쉬워진다. threshold를 강화하면 성능 목표가 더 엄격해진다.

### `preAllocatedVUs`와 `maxVUs`

`ramping-arrival-rate`에서 k6가 목표 RPS를 만들기 위해 준비하는 실행 단위다.

```js
preAllocatedVUs: 100,
maxVUs: 500,
```

- `preAllocatedVUs`: 미리 확보할 VU 수
- `maxVUs`: 부족할 때 늘릴 수 있는 최대 VU 수

RPS를 높였는데 k6가 `dropped_iterations`를 많이 기록하면 애플리케이션이 아니라 k6 실행 자원이 부족할 수 있다. 이때는 `preAllocatedVUs`와 `maxVUs`를 늘린다.

## `e2e-data-flow.js`

이 파일은 데이터 정합성 검증용이다. 부하 한계 측정보다는 “보낸 이벤트가 analytics에서 다시 보이는지”를 확인한다.

### 이벤트 수와 실행 시간 조정

위치:

```js
e2e_verification: {
  executor: 'ramping-vus',
  stages: [
    { duration: '30s', target: 5 },
    { duration: '3m', target: 10 },
    { duration: '30s', target: 0 },
  ],
}
```

의미:

- `target: 5`, `target: 10`: 동시에 검증 이벤트를 보내는 VU 수
- `duration: '3m'`: 검증 이벤트를 발생시키는 주 실행 시간

수정 효과:

- `target`을 올리면 동시에 더 많은 device event를 검증한다.
- `duration`을 늘리면 총 이벤트 수가 증가한다.
- 너무 높이면 stream projection 지연이 커져 `e2e_latency_ms`가 악화될 수 있다.

### verification timeout 조정

위치:

```js
const maxAttempts = 20;
const retryInterval = 2;
```

의미:

- `maxAttempts`: analytics API를 최대 몇 번 조회할지
- `retryInterval`: 조회 실패 후 몇 초 기다릴지

대략적인 최대 검증 시간:

```text
3초 초기 대기 + (maxAttempts - 1) * retryInterval
```

현재 값이면 대략 `3 + 19 * 2 = 41초` 정도 기다린다.

수정 효과:

- `maxAttempts`를 늘리면 늦게 반영되는 이벤트를 더 잘 잡는다.
- `retryInterval`을 줄이면 더 자주 조회하지만 analytics 부하가 늘어난다.
- 둘 다 너무 작으면 실제로 DB에 들어오는 이벤트도 `Not Found`로 오판한다.

권장 조정:

- stream projection p95가 30초라면 최소 40초 이상 검증한다.
- latency SLO를 엄격하게 보고 싶다면 `maxAttempts`를 줄이는 대신 `e2e_latency_ms` threshold도 함께 봐야 한다.

### latency threshold 조정

위치:

```js
thresholds: {
  'e2e_latency_ms': ['p(95)<10000'],
  'http_req_failed': ['rate<0.05'],
}
```

의미:

- `e2e_latency_ms p95 < 10000`: 이벤트가 analytics에서 보이기까지 p95가 10초 미만이어야 한다.
- `http_req_failed < 0.05`: HTTP 실패율이 5% 미만이어야 한다.

수정 효과:

- `p(95)<35000`처럼 늘리면 현재 시스템의 약 30초 projection 지연을 허용한다.
- `p(95)<10000`을 유지하면 “10초 이내 반영”이라는 성능 목표를 강제한다.

구분:

- 데이터 정합성을 보려면 verification rate를 본다.
- 반영 속도를 보려면 `e2e_latency_ms` threshold를 본다.

### payload 수정

위치:

```js
const payload = JSON.stringify({
  metadata: {
    eventId,
    deviceId: testDeviceId,
    eventType: 'TELEMETRY',
    eventTime: testTimestamp.toISOString(),
    ingestTime: testTimestamp.toISOString(),
  },
  lat,
  lon,
  speed,
  heading,
  accelX,
  accelY,
});
```

의미:

이 구조는 `contracts` 모듈의 `TelemetryEvent`와 맞아야 한다.

수정 효과:

- 필드명을 잘못 바꾸면 ingestion에서 JSON deserialization error가 난다.
- `lat`, `lon`을 빼거나 `latitude`, `longitude`로 바꾸면 telemetry contract와 맞지 않는다.
- `metadata.eventType`과 MQTT topic suffix가 다르면 topic validation에 실패한다.

## `throughput-ingestion.js`

이 파일은 ingestion-service 처리량 한계를 찾는 용도다.

### RPS 단계 조정

위치:

```js
stages: [
  { duration: '30s', target: 50 },
  { duration: '30s', target: 200 },
  { duration: '30s', target: 500 },
  { duration: '30s', target: 1000 },
  { duration: '30s', target: 1500 },
  { duration: '30s', target: 2000 },
  { duration: '30s', target: 2500 },
  { duration: '20s', target: 50 },
]
```

의미:

`ramping-arrival-rate` executor이므로 `target`은 RPS다.

수정 효과:

- 목표 처리량을 더 높게 찾고 싶으면 `target`을 올린다.
- 각 단계의 안정 상태를 더 정확히 보고 싶으면 `duration`을 늘린다.
- 너무 짧으면 Kafka producer warm-up, JIT, connection pool warm-up 영향이 크게 섞인다.

### SLO 조정

위치:

```js
thresholds: {
  http_req_duration: [
    { threshold: 'p(95)<100', abortOnFail: true, delayAbortEval: '30s' },
  ],
  http_req_failed: [
    { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' },
  ],
}
```

의미:

- p95 latency 100ms 미만
- 실패율 1% 미만
- 기준 초과 시 테스트 중단

수정 효과:

- `p(95)<200`으로 바꾸면 latency 기준을 완화한다.
- `rate<0.001`로 바꾸면 실패율 기준을 강화한다.
- `abortOnFail: false`로 바꾸면 실패해도 끝까지 부하 곡선을 확인할 수 있다.

### VU 자원 조정

위치:

```js
preAllocatedVUs: 100,
maxVUs: 500,
```

수정 효과:

- `dropped_iterations`가 발생하면 이 값을 늘린다.
- 서버가 아닌 k6 실행 용량 부족으로 목표 RPS를 만들지 못하는 상황을 줄인다.

### payload 계약 주의

현재 이 스크립트의 payload 생성 함수는 legacy 형태다.

```js
{
  deviceId,
  eventType: 'TELEMETRY',
  timestamp,
  latitude,
  longitude,
}
```

최신 telemetry contract 기준으로 정확한 ingestion 처리량을 측정하려면 `e2e-data-flow.js`처럼 `metadata`, `lat`, `lon`, `accelX`, `accelY` 형태로 바꿔야 한다.

## `throughput-analytics.js`

이 파일은 analytics-api 읽기 성능을 측정한다.

### VU 단계 조정

위치:

```js
stages: [
  { duration: '30s', target: 20 },
  { duration: '1m', target: 50 },
  { duration: '2m', target: 100 },
  { duration: '1m', target: 150 },
  { duration: '1m', target: 50 },
  { duration: '30s', target: 0 },
]
```

의미:

`ramping-vus` executor이므로 `target`은 동시 사용자 수다.

수정 효과:

- `target`을 올리면 analytics API 동시 조회 부하가 증가한다.
- `duration`을 늘리면 캐시, DB buffer, connection pool 안정 상태를 더 잘 볼 수 있다.

### 쿼리 비율 조정

위치:

```js
const rand = Math.random();

if (rand < 0.3) {
  queryLatestDevices();
} else if (rand < 0.6) {
  queryEventsPerMinute();
} else if (rand < 0.8) {
  queryDrivingEventCounters();
} else {
  queryRegionHeatmap();
}
```

의미:

- 최신 디바이스 조회 30%
- 분당 이벤트 조회 30%
- 운전 이벤트 카운터 20%
- 히트맵 20%

수정 효과:

- 특정 API 병목을 더 강하게 보고 싶으면 해당 비율을 높인다.
- 예를 들어 히트맵 병목을 보려면 마지막 구간 비율을 50% 이상으로 늘린다.

### 시간 범위 조정

위치:

```js
const hoursOptions = [1, 6, 24, 168];
```

의미:

1시간, 6시간, 24시간, 7일 범위 쿼리를 섞는다.

수정 효과:

- 긴 범위를 늘리면 집계 테이블 scan 범위가 커져 DB 부하가 커진다.
- 짧은 범위만 두면 대시보드 실시간 조회 성능에 가깝다.

### SLO 조정

위치:

```js
thresholds: {
  http_req_duration: ['p(95)<2000'],
  http_req_failed: ['rate<0.01'],
  analytics_latest_devices_duration: ['p(95)<200'],
  analytics_events_per_minute_duration: ['p(95)<1000'],
  analytics_driving_counters_duration: ['p(95)<1000'],
  analytics_heatmap_duration: ['p(95)<1500'],
}
```

수정 효과:

- API별 성능 목표를 독립적으로 조정할 수 있다.
- 특정 API만 실패한다면 전체 threshold보다 해당 custom trend를 먼저 본다.

## `realistic-iot-scenario.js`

이 파일은 운영과 비슷한 복합 부하를 재현한다.

### 정상 운영 부하 조정

위치:

```js
normal_operation: {
  executor: 'ramping-vus',
  stages: [
    { duration: '30s', target: 100 },
    { duration: '5m', target: 200 },
    { duration: '30s', target: 100 },
  ],
}
```

의미:

동시 VU를 늘려 지속적인 디바이스 이벤트 전송 부하를 만든다.

수정 효과:

- `target`을 올리면 평상시 write 부하가 커진다.
- `duration`을 늘리면 장시간 안정성, memory, lag 증가 여부를 보기 좋다.

### 버스트 부하 조정

위치:

```js
rush_hour_burst: {
  executor: 'ramping-arrival-rate',
  stages: [
    { duration: '1m', target: 100 },
    { duration: '15s', target: 1000 },
    { duration: '2m', target: 1000 },
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
  ],
}
```

의미:

출퇴근 시간처럼 RPS가 급격히 상승하는 상황을 만든다.

수정 효과:

- peak `target`을 올리면 순간 처리량 한계를 본다.
- peak `duration`을 늘리면 Kafka lag와 DB projection 지연이 누적되는지 볼 수 있다.

### 네트워크 복구 부하 조정

위치:

```js
network_recovery: {
  executor: 'ramping-arrival-rate',
  stages: [
    { duration: '30s', target: 10 },
    { duration: '10s', target: 500 },
    { duration: '1m', target: 500 },
    { duration: '30s', target: 50 },
  ],
}
```

의미:

끊겼던 디바이스들이 한꺼번에 밀린 데이터를 보내는 상황을 만든다.

수정 효과:

- `target: 500`을 올리면 복구 순간의 backlog 처리 능력을 본다.
- batch payload 크기나 batch count를 늘리면 ingestion batch path와 Kafka producer 부하가 커진다.

### 대시보드/모니터링 부하 조정

위치:

```js
dashboard_load: {
  executor: 'ramping-vus',
  stages: [
    { duration: '30s', target: 10 },
    { duration: '5m', target: 30 },
    { duration: '30s', target: 5 },
  ],
}

realtime_monitor: {
  executor: 'constant-vus',
  vus: 5,
  duration: '6m',
}
```

수정 효과:

- `dashboard_load.target`을 올리면 analytics API 동시 조회 부하가 커진다.
- `realtime_monitor.vus`를 올리면 metrics/recent-events polling 부하가 커진다.
- write 부하와 read 부하가 섞였을 때 DB connection pool이나 CPU 병목을 보기 좋다.

### 시나리오 시작 시간 조정

위치:

```js
startTime: '1m'
startTime: '4m'
```

의미:

다른 시나리오가 시작된 뒤 특정 시점에 부하를 겹친다.

수정 효과:

- `startTime`을 앞당기면 부하가 더 많이 겹친다.
- `startTime`을 늦추면 각 시나리오 영향이 분리되어 원인 분석이 쉬워진다.

## 수정 후 확인할 것

스크립트를 수정한 뒤에는 다음을 확인한다.

1. k6가 목표한 부하를 실제로 만들었는지
   - `dropped_iterations`
   - `vus_max`
   - `http_reqs`

2. 애플리케이션이 요청을 받았는지
   - ingestion `/ingestion/v1/metrics`
   - stream `/stream/v1/metrics`
   - analytics 응답 코드

3. 시스템 내부 병목이 생겼는지
   - Kafka publish rate
   - Kafka lag
   - stream projection writes/failures
   - PostgreSQL connection pool
   - analytics query latency

4. 데이터가 남았는지
   - E2E 테스트 후 `telemetryhub_device_last_seen`에 `e2e-%` device가 남을 수 있다.
   - 부하 테스트 후 집계 테이블에도 테스트 시간대 bucket이 남을 수 있다.

## 자주 하는 조정

| 상황 | 조정할 값 | 의미 |
| --- | --- | --- |
| E2E에서 `Not Found`가 많지만 DB에는 나중에 들어옴 | `maxAttempts`, `retryInterval` 증가 | projection 지연을 더 오래 기다림 |
| E2E latency threshold만 실패 | `e2e_latency_ms` threshold 조정 또는 stream 성능 개선 | 데이터 정합성과 반영 속도 기준을 분리 |
| ingestion 처리량 한계를 더 찾고 싶음 | `throughput-ingestion.js`의 RPS `target` 증가 | 더 높은 초당 요청률 생성 |
| k6가 목표 RPS를 못 만듦 | `preAllocatedVUs`, `maxVUs` 증가 | k6 실행 자원 확대 |
| analytics 특정 API만 느림 | query 비율과 해당 custom threshold 조정 | 병목 API를 집중 측정 |
| 운영과 비슷한 혼합 부하를 만들고 싶음 | `realistic-iot-scenario.js`의 `startTime`, `target`, `duration` 조정 | write/read/monitoring 부하를 겹치게 설계 |
