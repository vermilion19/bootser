# TelemetryHub 부하 테스트 가이드

> 목적 없이 부하 테스트를 돌리면 숫자만 쌓이고 결론이 없다.
> 테스트 전에 **무엇을 검증할지**, **어떤 숫자가 나오면 성공인지** 먼저 정한다.

---

## 시스템 아키텍처 이해

```
┌─────────────────┐    HTTP/MQTT    ┌────────────────────┐
│ Device          │ ───────────────→│ Ingestion Service  │
│ Simulator       │                 │ (port 8092)        │
│ (port 8091)     │                 └─────────┬──────────┘
└─────────────────┘                           │ Kafka
                                              ▼
                                    ┌────────────────────┐
                                    │ Stream Processor   │
                                    │ (port 8093)        │
                                    │ Kafka Streams      │
                                    └─────────┬──────────┘
                                              │ DB Write
                                              ▼
                                    ┌────────────────────┐
                                    │ Analytics API      │◄── Dashboard
                                    │ (port 8094)        │    조회
                                    └────────────────────┘
```

---

## 테스트 전에 반드시 정할 것

### 1. 무엇을 검증하고 싶은가?

| 유형 | 질문 예시 |
|---|---|
| **수집 처리량** | Ingestion Service가 초당 몇 건의 이벤트를 처리할 수 있는가? |
| **스트림 지연** | Kafka Streams가 데이터를 얼마나 빠르게 처리하는가? |
| **분석 쿼리 성능** | Analytics API의 집계 쿼리가 SLO 내에 응답하는가? |
| **E2E 지연** | 디바이스 이벤트 발생 → 대시보드 반영까지 몇 초 걸리는가? |
| **버스트 내성** | 갑작스런 트래픽 폭증 시 시스템이 안정적인가? |

### 2. 합격 기준 (threshold)을 먼저 정한다

테스트 결과를 보고 나서 기준을 정하면 후기 합리화가 된다.
**반드시 실행 전에 작성한다.**

```
예시:
- Ingestion p95 < 100ms, 에러율 < 1%  → 합격
- Analytics p95 < 500ms, 에러율 < 0.1% → 합격
- Stream Processor Consumer Lag < 1000 유지 → 합격
```

### 3. 결과가 나오면 무엇을 바꿀 것인가?

| 결과 | 다음 액션 |
|---|---|
| Kafka Producer 병목 | batch.size, linger.ms 튜닝 |
| Consumer Lag 급증 | Kafka Streams 스레드 수 증가, 파티션 추가 |
| Analytics 쿼리 느림 | DB 인덱스 추가, 쿼리 최적화 |
| Ingestion OOM | 버퍼 크기 조정, 배압(backpressure) 구현 |

---

## 이 프로젝트에서 검증할 수 있는 항목

### A. Ingestion Service 처리량 한계

**검증하려는 것**
> 단일 Ingestion Service 인스턴스가 초당 몇 건의 이벤트를 처리할 수 있는가?

**방법**
- `throughput-ingestion.js` 실행
- RPS를 단계적으로 올리며 한계점 탐색

**합격 기준 (예시)**
```
p95 < 100ms, 에러율 < 1% 유지하면서 최소 1000 RPS 처리
```

**Grafana에서 볼 것**
```promql
rate(http_server_requests_seconds_count{application="telemetryhub-ingestion-service"}[1m])
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="telemetryhub-ingestion-service"}[5m])) by (le))
```

---

### B. Stream Processor Consumer Lag

**검증하려는 것**
> 대량 이벤트 주입 시 Kafka Streams Consumer Lag이 얼마나 쌓이며, 얼마나 빨리 복구되는가?

**방법**
1. `throughput-ingestion.js`로 대량 이벤트 주입
2. Grafana에서 Consumer Lag 메트릭 확인
3. 주입 중단 후 Lag 복구 시간 측정

**합격 기준 (예시)**
```
- 버스트 중 Consumer Lag < 5000 유지
- 버스트 종료 후 30초 이내 Lag 정상화
```

**Grafana에서 볼 것**
```promql
kafka_consumer_fetch_manager_metrics_records_lag{application="telemetryhub-stream-processor"}
rate(telemetryhub_stream_projection_write_success_total[1m])
```

---

### C. Analytics API 쿼리 성능

**검증하려는 것**
> 시간 범위별 집계 쿼리가 SLO 내에 응답하는가?

**방법**
- `throughput-analytics.js` 실행
- 시간 범위(1시간/24시간/7일)별 응답시간 비교

**합격 기준 (예시)**
```
- 1시간 범위 조회: p95 < 200ms
- 24시간 범위 조회: p95 < 500ms
- 7일 범위 조회: p95 < 2000ms
```

---

### D. E2E 데이터 플로우 검증

**검증하려는 것**
> 이벤트 발생 → Ingestion → Stream Processing → Analytics 조회까지 전체 경로가 정상 동작하는가?

**방법**
- `e2e-data-flow.js` 실행
- 특정 deviceId로 이벤트 발행 후 Analytics에서 조회 가능 확인

**합격 기준**
```
- 이벤트 발행 후 10초 이내 Analytics에서 조회 가능
- 데이터 누락 없음 (발행 수 == 조회 수)
```

---

### E. 현실적 IoT 시나리오

**검증하려는 것**
> 수천 대의 디바이스가 동시에 데이터를 전송하는 상황에서 시스템이 안정적인가?

**시나리오**
1. **정상 운영**: 1000대 디바이스가 1초 간격으로 이벤트 전송
2. **버스트**: 출퇴근 시간 트래픽 5배 급증
3. **장애 복구**: 네트워크 복구 후 밀린 데이터 일괄 전송

**스크립트**: `realistic-iot-scenario.js`

---

## 스크립트 목록

| 파일 | 목적 |
|---|---|
| `throughput-ingestion.js` | **[처리량 한계]** Ingestion Service RPS 한계 탐색 |
| `throughput-analytics.js` | **[처리량 한계]** Analytics API 쿼리 성능 측정 |
| `e2e-data-flow.js` | **[데이터 플로우]** 수집→처리→분석 E2E 검증 |
| `realistic-iot-scenario.js` | **[현실 시나리오]** 대량 디바이스 + 버스트 트래픽 |

---

## 실행 명령어

### 사전 준비

```bash
# 1. 서비스 실행 (필수)
./gradlew :apps:telemetryhub:ingestion-service:bootRun &
./gradlew :apps:telemetryhub:stream-processor:bootRun &
./gradlew :apps:telemetryhub:analytics-api:bootRun &

# 2. Observability 스택 실행 (Grafana 확인용)
cd dockers/observability && docker-compose up -d

# 3. Kafka 실행 (필수)
cd dockers/infrastructure && docker-compose up -d kafka
```

### 테스트 실행

```bash
# Grafana 연동 실행 (권장 — 실시간 메트릭 확인)
docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/<스크립트명>

# Grafana 없이 터미널 출력만
docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
  run --rm k6 run /scripts/<스크립트명>

# 예시: 처리량 한계 테스트
docker compose -f apps/telemetryhub/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-ingestion.js
```

---

## 결과 해석 가이드

```
한계 지점에서 무엇을 봐야 하는가?

Ingestion 응답시간 급등, CPU 낮음
  → Kafka Producer 병목 (broker 응답 대기)
    └─ batch.size, linger.ms 튜닝 필요

Consumer Lag 지속 증가
  → Stream Processor 처리 속도 < 유입 속도
    ├─ num.stream.threads 증가
    └─ 파티션 수 증가 검토

Analytics p99 급증, 나머지는 정상
  → 특정 쿼리만 느림 (시간 범위 큰 경우)
    └─ 날짜 인덱스, 파티셔닝 검토

모든 서비스 동시에 느려짐
  → 공유 자원(DB, Kafka) 병목
```

---

## Grafana 대시보드

TelemetryHub 전용 대시보드: **TelemetryHub - Application Metrics**

주요 패널:
- Service Status (UP/DOWN)
- Projection Write Rate (by type)
- Consumer Lag
- Analytics API RPS/Latency
- JVM Heap/Threads
