# TelemetryHub Grafana Dashboard Interpretation Guide

## Purpose
이 문서는 `apps/telemetryhub`의 Grafana 대시보드를 볼 때 각 패널이 무엇을 의미하는지, 그래프가 움직일 때 어떻게 해석해야 하는지를 운영 관점에서 정리한다.

TelemetryHub의 흐름은 아래처럼 읽으면 된다.

```text
Device Simulator / MQTT / HTTP
  -> Ingestion Service
  -> Kafka
  -> Stream Processor
  -> Read Model DB
  -> Analytics API
  -> Dashboard
```

즉 대시보드는 단순히 숫자를 보여주는 화면이 아니라,

1. 이벤트가 들어오고 있는지
2. 스트림 처리가 따라가고 있는지
3. DB 반영이 되고 있는지
4. 최종 조회 API가 정상 응답하는지

를 한 번에 보는 관측 화면이다.

## First Rule
Grafana는 항상 한 패널만 보지 말고, 아래 순서로 읽는 게 안전하다.

1. `Service Status`로 서비스가 살아 있는지 본다.
2. `Ingestion` 요청량과 응답시간이 정상인지 본다.
3. `Consumer Lag`가 밀리고 있는지 본다.
4. `Projection Write Rate`가 실제로 DB 반영을 따라가고 있는지 본다.
5. `Analytics API RPS/Latency`가 최종 조회 품질을 유지하는지 본다.
6. `JVM Heap/Threads`로 메모리/스레드 이상이 있는지 본다.

## System Panels
### 1. Service Status
각 서비스의 `UP/DOWN` 상태다.

- `UP`: 프로세스가 살아 있고 Prometheus scrape 또는 health check가 성공 중
- `DOWN`: 서비스 장애, 포트 미노출, 네트워크 문제, scrape 실패 가능성

이 패널이 흔들리면 성능 문제가 아니라 먼저 가용성 문제를 의심해야 한다.

### 2. Ingestion Throughput / Latency
`ingestion-service`가 HTTP/MQTT 메시지를 얼마나 받고 얼마나 빨리 처리하는지 본다.

- 요청 수가 오르면: 외부 이벤트 유입이 늘고 있다
- 요청 수가 떨어지면: 유입 자체가 줄었거나 upstream이 멈췄다
- latency가 오르면: normalize, publish, Kafka broker 응답, 내부 queue가 느려질 수 있다
- error가 오르면: normalize 실패 또는 publish 실패 가능성이 크다

주의할 점은 ingestion이 빠르다고 끝이 아니라는 것이다. 뒤 단계가 못 따라가면 `Consumer Lag`가 올라간다.

### 3. Consumer Lag
Kafka에는 들어왔지만 `stream-processor`가 아직 처리하지 못한 이벤트 양이다.

- 잠깐 올랐다가 다시 내려오면: 정상 burst 흡수
- 계속 우상향하면: 유입 속도보다 처리 속도가 느리다
- 유입이 줄었는데도 안 내려오면: stream processing 또는 DB write 병목
- 급등 후 회복이 오래 걸리면: thread 수, partition 수, DB contention을 확인해야 한다

이 패널은 "지금 시스템이 밀리고 있는가"를 가장 직접적으로 보여준다.

### 4. Projection Write Rate (by type)
`stream-processor`가 집계 결과를 read model DB에 얼마나 반영하는지 보여준다.

Projection type은 네 가지다.

- `DEVICE_LAST_SEEN`
- `EVENTS_PER_MINUTE`
- `DRIVING_EVENT_COUNTER`
- `REGION_HEATMAP`

해석은 공통이다.

- write rate가 오르면: stream processor가 실제 DB 반영을 활발히 수행 중
- write rate가 0에 가까우면: 입력이 없거나 처리/쓰기 경로가 막혔다
- failure가 같이 오르면: DB upsert 실패, schema/index 문제, lock contention 가능성

`Consumer Lag`가 오르는데 `Projection Write Rate`가 낮으면, Kafka를 읽긴 읽는데 처리 또는 write가 밀린다는 뜻이다.

### 5. Analytics API RPS / Latency
대시보드나 클라이언트가 `analytics-api`를 얼마나 호출하고, 응답이 얼마나 느린지 보여준다.

- RPS 상승 + latency 안정: 정상 확장
- RPS 비슷한데 latency 상승: DB 조회 또는 쿼리 병목
- 특정 시간대만 latency 급증: 대시보드 새로고침, 부하 테스트, 특정 범위 조회 영향 가능성
- error가 같이 오르면: API 자체 실패 또는 downstream DB 문제

이 패널은 "조회 품질"을 의미한다. 데이터가 잘 쌓여도 API가 느리면 운영자는 결국 느리다고 느낀다.

### 6. JVM Heap / Threads
각 Java 서비스의 런타임 상태를 보는 보조 패널이다.

- heap이 톱니처럼 오르내리면: 일반적인 GC 패턴
- heap이 계속 우상향하면: 메모리 누수 또는 버퍼 적체 가능성
- thread 수가 급증하면: 요청 정체, 재시도, blocking 증가 가능성

이 패널은 원인 분석용이다. 보통 먼저 latency, lag, failure가 이상해지고 그 다음 JVM 패널에서 근거를 찾는다.

## Business Panels
관리자 대시보드 성격의 비즈니스 패널은 보통 아래 네 API 결과를 시각화한다.

- `GET /analytics/v1/devices/latest`
- `GET /analytics/v1/events-per-minute`
- `GET /analytics/v1/driving-events/counters`
- `GET /analytics/v1/heatmap/regions`

### 1. Latest Devices
각 디바이스의 "가장 최근 이벤트 상태"를 보여주는 패널이다.

보통 이런 의미를 가진다.

- 마지막 이벤트 시간
- 마지막 이벤트 타입
- 마지막 수집 시각
- source topic

숫자나 시간이 움직일 때의 의미:

- 최신 시각이 계속 현재 시각에 가깝게 갱신되면: 디바이스 데이터 유입이 살아 있다
- 일부 디바이스의 최신 시각이 오래 멈추면: 해당 디바이스 또는 해당 topic 유입이 끊겼다
- `last_event_time`은 과거인데 `last_ingest_time`만 최근이면: 지연 이벤트 또는 순서 뒤집힘이 들어왔을 가능성이 있다
- 최신 디바이스 목록이 계속 바뀌면: 여러 차량/기기가 활발하게 이벤트를 보내고 있다는 뜻이다
- 특정 몇 개 디바이스만 계속 상단에 고정되면: 트래픽 편중 가능성이 있다

운영에서 보는 포인트:

- "데이터가 아예 안 들어오는가?"
- "특정 디바이스만 멈췄는가?"
- "이벤트 시간과 적재 시간이 벌어지는가?"

### 2. Events Per Minute
분 단위로 이벤트 건수를 집계한 패널이다. `event_type`별로 쪼개질 수 있다.

숫자가 오르내릴 때의 의미:

- 전체 선이 오르면: 시스템 유입량 증가
- 전체 선이 내려가면: 유입량 감소
- 특정 `event_type`만 치솟으면: 특정 이벤트만 집중적으로 발생
- 주기적으로 톱니 모양이면: 정상적인 배치성/주기성 트래픽일 수 있다
- 갑자기 0으로 떨어지면: upstream 중단, ingestion 장애, consumer 정지 가능성

이 패널은 "현재 시스템에 얼마나 많은 이벤트가 들어오는가"를 보는 가장 직관적인 패널이다.

함께 봐야 할 것:

- `Events Per Minute` 상승
- `Consumer Lag`도 상승

이 조합이면 처리 capacity 부족이다.

### 3. Driving Event Counters
급가속, 급제동, 급회전, 과속 같은 운전 이벤트를 분 단위로 집계한 패널이다.

숫자가 오를 때의 의미:

- 특정 시간 구간에 위험 운전 이벤트가 많아졌다
- 특정 차량의 카운터가 오르면: 그 차량의 운전 패턴이 공격적이거나 이상하다
- 특정 `drivingEventType`만 오르면: 예를 들어 급제동이 많은 구간/환경이 생겼을 수 있다

숫자가 내려가거나 평평할 때의 의미:

- 운전 이벤트가 적게 발생 중
- driving event 자체 유입이 줄었을 수 있음
- 너무 오랫동안 0이면 수집 또는 분류 경로 문제도 의심해야 함

운영에서 주의할 해석:

- 이 패널 상승은 시스템 장애 신호가 아니라 비즈니스 이벤트 증가일 수 있다
- 하지만 이 값이 급등하면서 `Analytics Latency`나 `Projection Write Failure`도 오르면 read model write 부담이 커졌을 수 있다

### 4. Region Heatmap
위치 격자 단위로 이벤트 밀도를 보여주는 패널이다. 보통 텔레메트리 이벤트를 지도 또는 heatmap으로 표현한다.

화면에서 색이 진해지거나 수치가 오를 때의 의미:

- 해당 지역에서 텔레메트리 이벤트가 많이 들어오고 있다
- 차량/디바이스가 특정 지역에 몰려 있다
- 실시간 운영 관점에서는 "지금 어디에 활동이 집중되는가"를 보여준다

반대로 색이 옅어지거나 사라질 때의 의미:

- 해당 지역 활동 감소
- 디바이스 이동
- 해당 좌표 범위의 수집 중단 가능성

주의할 점:

- heatmap은 이벤트 수를 보여주지, 반드시 "문제 지역"을 뜻하지는 않는다
- 붉은색이 많다고 장애가 아니라 단순히 이벤트 밀집일 수 있다
- 특정 지역만 완전히 비면 실제 교통 변화인지, 좌표 필터/수집 문제인지 같이 봐야 한다

## How To Read Moving Graphs
그래프가 빠르게 움직여도 아래처럼 읽으면 된다.

### 정상 패턴
1. `Ingestion` 요청량이 오른다.
2. 잠시 뒤 `Projection Write Rate`가 오른다.
3. `Consumer Lag`는 잠깐 오르더라도 다시 내려온다.
4. `Analytics API Latency`는 안정적이다.
5. 비즈니스 패널 수치도 자연스럽게 반영된다.

이 경우는 데이터가 들어오고, 처리되고, 조회까지 잘 되는 상태다.

### 처리 병목 패턴
1. `Ingestion`은 높은데
2. `Consumer Lag`가 계속 오른다.
3. `Projection Write Rate`가 기대보다 낮다.

이 경우는 stream processing 또는 DB write가 따라가지 못하는 상태다.

### 조회 병목 패턴
1. `Projection Write Rate`는 정상이다.
2. `Consumer Lag`도 안정적이다.
3. 그런데 `Analytics API Latency`만 오른다.

이 경우는 적재는 되지만 조회 쿼리 또는 DB read가 느린 상태다.

### 장애 패턴
1. `Service Status`가 흔들린다.
2. 여러 패널이 동시에 비정상으로 무너진다.

이 경우는 성능 최적화보다 먼저 서비스 장애 복구가 우선이다.

## Quick Diagnosis Table
| 보이는 현상 | 가장 먼저 의심할 것 |
|---|---|
| Ingestion latency 급등 | Kafka publish 지연, normalize 비용, 내부 queue 적체 |
| Consumer Lag 지속 상승 | stream processor 처리 부족, DB write 병목 |
| Projection failure 증가 | DB upsert 실패, lock contention, schema/index 문제 |
| Analytics latency만 상승 | analytics query, DB read 성능 문제 |
| Latest Devices 갱신 정지 | 디바이스 유입 중단, ingestion 문제 |
| Events Per Minute 0으로 급락 | upstream 중단, ingestion/consumer 정지 |
| Driving Event Counter 급등 | 실제 위험 운전 이벤트 증가 또는 특정 차량 편중 |
| Heatmap 특정 지역만 사라짐 | 지역 활동 감소 또는 좌표 수집/필터 문제 |

## Practical Reading Tips
- 그래프 한 개만 보고 결론 내리지 않는다.
- "유입", "처리", "적재", "조회" 중 어느 단계가 문제인지 먼저 분리한다.
- 비즈니스 패널 상승과 시스템 패널 상승을 구분한다.
- `Driving Event Counter`와 `Heatmap` 증가는 장애가 아니라 정상 활동 증가일 수 있다.
- 장애 판단은 `failure`, `lag`, `latency`, `status`를 같이 본다.

## Related Code And Docs
- [apps/telemetryhub/load-test/LOAD-TEST-GUIDE.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/load-test/LOAD-TEST-GUIDE.md:236)
- [apps/telemetryhub/docs/docker-runtime.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/docker-runtime.md:137)
- [apps/telemetryhub/docs/stream-processor-ops-guide.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-ops-guide.md:31)
- [apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/application/metrics/StreamProcessorMetricsCollector.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/application/metrics/StreamProcessorMetricsCollector.java:21)
- [apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/application/ingest/IngestionService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/application/ingest/IngestionService.java:34)
- [apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/web/AnalyticsController.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/web/AnalyticsController.java:35)
- [apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/application/query/AnalyticsQueryService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/analytics-api/src/main/java/com/booster/telemetryhub/analyticsapi/application/query/AnalyticsQueryService.java:22)
