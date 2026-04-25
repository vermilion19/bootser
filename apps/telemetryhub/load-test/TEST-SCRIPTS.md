# TelemetryHub Load Test Scripts

이 문서는 `apps/telemetryhub/load-test/scripts` 아래 k6 스크립트가 각각 무엇을 검증하려는지 정리한다.

## 공통 실행 환경

모든 스크립트는 `apps/telemetryhub/load-test/docker-compose.yml`의 `k6` 서비스로 실행하는 것을 기준으로 한다.

```bash
docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
  run --rm k6 run /scripts/<script-name>.js
```

기본 대상 서비스는 다음 환경변수로 주입된다.

- `BASE_URL_INGESTION`: `ingestion-service`
- `BASE_URL_STREAM`: `stream-processor`
- `BASE_URL_ANALYTICS`: `analytics-api`
- `BASE_URL_SIMULATOR`: `device-simulator`

Prometheus remote write를 함께 사용할 때는 다음 형태로 실행한다.

```bash
docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/<script-name>.js
```

## `e2e-data-flow.js`

### 테스트 목표

Telemetry 이벤트가 전체 데이터 파이프라인을 끝까지 통과하는지 검증한다.

```text
k6
  -> ingestion-service
  -> Kafka telemetryhub.raw-events
  -> stream-processor
  -> PostgreSQL projection tables
  -> analytics-api
  -> k6 verification
```

### 검증 내용

- telemetry 이벤트를 ingestion API로 발행한다.
- 같은 `deviceId`를 analytics API에서 반복 조회한다.
- 조회에 성공하면 `Events Verified`로 기록한다.
- 검증 제한 시간 내에 찾지 못하면 `Events Not Found`로 기록한다.
- 별도 `data_flow_check` 시나리오가 ingestion/stream metrics를 주기적으로 확인한다.

### 주요 지표

- `e2e_events_sent_total`: 발행 성공 이벤트 수
- `e2e_events_verified_total`: analytics 조회 성공 이벤트 수
- `e2e_events_not_found_total`: 검증 시간 안에 못 찾은 이벤트 수
- `e2e_latency_ms`: 발행 후 analytics 조회 가능해지기까지 걸린 시간
- `e2e_verification_attempts_total`: 검증 조회 시도 수

### 합격 기준

- 데이터 정합성: `handleSummary`에서 verification rate `>= 99%`면 PASS로 출력한다.
- k6 threshold: `e2e_latency_ms p95 < 10000ms`, `http_req_failed < 5%`

### 해석 기준

이 테스트는 처리량보다 데이터 정합성에 초점을 둔다. `Events Sent`와 최종 DB row 수가 같지만 `Events Verified`가 낮다면, 실제 유실보다 verification timeout이 짧을 가능성이 있다. 현재 스크립트는 projection 지연을 고려해 최대 20회, 2초 간격으로 analytics를 조회한다.

## `throughput-ingestion.js`

### 테스트 목표

`ingestion-service`가 SLO를 유지하면서 처리할 수 있는 최대 RPS를 찾는다.

### 트래픽 모델

`ramping-arrival-rate` executor로 요청률을 직접 올린다.

- 50 RPS 워밍업
- 200, 500, 1000, 1500, 2000, 2500 RPS 단계 상승
- 마지막에 50 RPS로 쿨다운

### 검증 내용

- `/ingestion/v1/mqtt/messages`로 이벤트를 전송한다.
- 이벤트 타입은 telemetry, device-health, driving-event를 섞는다.
- p95 latency와 HTTP 실패율이 SLO를 넘는지 확인한다.
- threshold가 깨지면 `abortOnFail`로 테스트를 중단한다.

### 주요 지표

- `http_req_duration`: ingestion API 응답 시간
- `http_req_failed`: HTTP 실패율
- `ingestion_success_total`: 200 응답 수
- `ingestion_failure_total`: 실패 응답 수

### 합격 기준

- `http_req_duration p95 < 100ms`
- `http_req_failed < 1%`

### 주의

이 스크립트는 현재 telemetry 계약인 `metadata`, `lat`, `lon`, `accelX`, `accelY` 형태가 아니라 legacy payload 형태를 생성한다. 최신 ingestion 계약 기준으로 정확한 처리량을 재려면 payload 생성부를 contracts 모듈의 이벤트 구조에 맞춰야 한다.

## `throughput-analytics.js`

### 테스트 목표

`analytics-api`의 조회 API가 다양한 시간 범위와 쿼리 유형에서 SLO 안에 응답하는지 검증한다.

### 트래픽 모델

`ramping-vus` executor로 읽기 부하를 증가시킨다.

- 20 VU 워밍업
- 50 VU
- 100 VU 유지
- 150 VU 스파이크
- 50 VU 복구
- 0 VU 쿨다운

### 검증 내용

다음 API들을 가중치로 섞어 호출한다.

- 최신 디바이스 조회: `/analytics/v1/devices/latest`
- 분당 이벤트 집계 조회
- 운전 이벤트 카운터 조회
- 지역 히트맵 조회

시간 범위는 1시간, 6시간, 24시간, 7일 범위를 섞어 사용한다.

### 주요 지표

- `analytics_latest_devices_duration`
- `analytics_events_per_minute_duration`
- `analytics_driving_counters_duration`
- `analytics_heatmap_duration`
- `analytics_slow_queries_total`
- `analytics_slow_query_1h_total`
- `analytics_slow_query_24h_total`
- `analytics_slow_query_7d_total`

### 합격 기준

- 전체 `http_req_duration p95 < 2000ms`
- 전체 `http_req_failed < 1%`
- 최신 디바이스 조회 p95 `< 200ms`
- 분당 이벤트 집계 p95 `< 1000ms`
- 운전 이벤트 카운터 p95 `< 1000ms`
- 히트맵 조회 p95 `< 1500ms`

### 해석 기준

이 테스트는 analytics read path만 본다. 신뢰도 있는 결과를 얻으려면 projection 테이블에 충분한 데이터가 있어야 한다. 데이터가 거의 없으면 쿼리가 빠르게 보일 수 있고, 반대로 시간 범위가 넓을수록 집계 테이블 크기와 인덱스 상태의 영향을 크게 받는다.

## `realistic-iot-scenario.js`

### 테스트 목표

실제 차량 텔레매틱스 플랫폼에 가까운 복합 트래픽을 동시에 발생시켜, 전체 시스템이 혼합 부하에서 버티는지 확인한다.

### 시나리오

- `normal_operation`: 다수 차량이 지속적으로 telemetry/device-health/driving-event를 전송한다.
- `rush_hour_burst`: 출퇴근 시간처럼 짧은 시간에 요청률이 급증한다.
- `network_recovery`: 네트워크 복구 후 밀린 데이터를 batch로 전송한다.
- `dashboard_load`: 관리자 대시보드가 analytics API를 동시에 조회한다.
- `realtime_monitor`: ingestion, stream, recent events 상태를 지속적으로 polling한다.

### 검증 내용

- 정상 운영 중 ingestion 성공률을 확인한다.
- burst 구간에서 latency와 실패율이 얼마나 악화되는지 확인한다.
- batch ingestion이 recovery 상황에서 얼마나 버티는지 본다.
- analytics dashboard 조회와 realtime monitoring이 write 부하와 함께 동작하는지 본다.

### 주요 지표

- `ingestion_success_total`
- `ingestion_failure_total`
- `burst_events_sent_total`
- `ingestion_error_rate`
- `batch_ingestion_duration`
- `analytics_slow_query_total`
- 시나리오별 `http_req_duration`

### 합격 기준

- normal operation p95 `< 200ms`
- rush hour burst p95 `< 500ms`
- network recovery p95 `< 1000ms`
- dashboard load p95 `< 2000ms`
- realtime monitor p95 `< 300ms`
- 전체 HTTP 실패율 `< 5%`
- ingestion error rate `< 2%`

### 해석 기준

이 스크립트는 단일 병목을 정밀 측정하기보다 시스템 전체의 복합 부하 반응을 보는 용도다. 장애 원인을 좁힐 때는 이 결과만 보지 말고 Grafana의 ingestion publish, stream projection writes, Kafka lag, DB connection pool, analytics latency 패널을 함께 확인해야 한다.

## 어떤 스크립트를 언제 쓰는가

| 목적 | 사용할 스크립트 |
| --- | --- |
| 데이터가 끝까지 도달하는지 확인 | `e2e-data-flow.js` |
| ingestion-service 최대 처리량 탐색 | `throughput-ingestion.js` |
| analytics-api 조회 성능 측정 | `throughput-analytics.js` |
| 실제 운영과 비슷한 혼합 부하 재현 | `realistic-iot-scenario.js` |

## 테스트 후 정리

E2E나 부하 테스트 후 projection 테이블에 테스트 데이터가 남을 수 있다. E2E 데이터만 정리하려면 `device_id like 'e2e-%'` 기준으로 `telemetryhub_device_last_seen`을 지우고, 해당 시간대의 집계 테이블도 함께 정리한다.
