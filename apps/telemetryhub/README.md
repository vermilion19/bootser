# TelemetryHub

커넥티드 디바이스(차량·블랙박스·IoT)에서 발생하는 텔레메트리 데이터를  
**고처리량으로 수집·집계·조회**하는 실시간 데이터 파이프라인.

---

## 왜 만들었나

IoT 디바이스 데이터에는 일반적인 웹 트래픽과 다른 문제가 있다.

- **지연 도착**: 네트워크 불안정으로 과거 이벤트가 나중에 들어온다
- **중복 전송**: QoS 재전송으로 같은 이벤트가 여러 번 들어온다
- **순서 뒤섞임**: 디바이스 시계 오류, 경로 차이로 순서가 보장되지 않는다
- **재처리 요구**: 집계 산식이 바뀌면 과거 데이터를 다시 계산해야 한다

단순히 "받아서 DB에 저장"하는 구조로는 이 네 가지를 다룰 수 없다.  
TelemetryHub는 이 문제들을 아키텍처 수준에서 다루도록 설계했다.

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│  Edge / Device Layer                                            │
│  Device Simulator(s)          (Optional) Real Devices          │
└──────────────┬──────────────────────────────────────────────────┘
               │ MQTT publish
               ▼
┌──────────────────────────┐
│  MQTT Broker (Mosquitto) │
└──────────────┬───────────┘
               │ subscribe
               ▼
┌──────────────────────────────────────────────────────────────┐
│  Ingestion Service                                           │
│  validate → normalize → enrich(ingestTime) → Kafka produce   │
└──────────────┬───────────────────────────────────────────────┘
               │ key=deviceId
               ▼
┌─────────────────────────────┐        ┌──────────────────────┐
│  Kafka (raw-events topic)   │───────▶│  Raw Archive (S3)    │
└──────────────┬──────────────┘        └──────────────────────┘
               │ consume
               ▼
┌──────────────────────────────────────────────────────────────┐
│  Stream Processor (Kafka Streams)                            │
│  dedup → late-event filter → window aggregate → DB upsert    │
└──────────────┬───────────────────────────────────────────────┘
               │ upsert
               ▼
┌──────────────────────────┐
│  PostgreSQL (read model) │◀────── Batch Backfill (재처리)
└──────────────┬───────────┘
               │ query
               ▼
┌──────────────────────────┐
│  Analytics API           │
└──────────────────────────┘
```

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| 디바이스 프로토콜 | MQTT (Paho), Mosquitto |
| 메시지 버스 | Apache Kafka |
| 스트림 처리 | Kafka Streams |
| 데이터베이스 | PostgreSQL |
| 배치 재처리 | Spring Batch |
| 모니터링 | Micrometer + Prometheus + Grafana |
| 컨테이너 | Docker Compose |

---

## 모듈 구조

```
apps/telemetryhub/
├── contracts/          # 서비스 간 이벤트 계약 (공유 DTO)
├── device-simulator/   # 가상 디바이스 생성기 + 장애 시나리오 주입
├── ingestion-service/  # MQTT 구독 → Kafka 발행
├── stream-processor/   # Kafka Streams 실시간 집계
├── analytics-api/      # 집계 결과 REST API
├── batch-backfill/     # 과거 데이터 재처리 (Spring Batch)
└── docker/             # Docker Compose 환경
```

---

## 핵심 설계 결정

### 1. MQTT + Kafka 이중 브로커 구조

MQTT와 Kafka는 역할이 다르다.

| | MQTT | Kafka |
|--|------|-------|
| 대상 | 수십만 IoT 디바이스 | 내부 서비스 간 통신 |
| 메시지 보관 | 없음 | 설정 가능 (7일 이상) |
| 재처리 | 불가 | offset reset으로 재소비 |
| 스케일-아웃 | Shared Subscription 제한 | 파티션 추가만으로 가능 |

MQTT로 디바이스를 받고, Kafka로 버퍼링 + 재처리 + 팬-아웃을 담당한다.  
Stream Processor가 느려지더라도 Kafka가 압력을 흡수한다.

### 2. Kafka Streams 선택 이유

단순 `@KafkaListener`로는 **deduplication**과 **상태 유지 집계**를 외부 저장소(Redis/DB) 없이 구현할 수 없다.

Kafka Streams는 **in-process 상태 저장소(RocksDB)** 를 내장한다.

```java
// eventId 기반 dedup: 외부 Redis 없이 로컬 RocksDB만으로 처리
if (store.get(value.eventId()) != null) {
    context.forward(record.withValue(null)); // 중복 → 버림
    return;
}
store.put(value.eventId(), seenAt);
```

→ [자세한 분석: docs/why-kafka-streams.md](docs/why-kafka-streams.md)

### 3. eventTime vs ingestTime 이중 저장

디바이스 시계는 믿을 수 없다 (RTC 오류, timezone 오류).

```java
record EventMetadata(
    Instant eventTime,   // 디바이스 발생 시각 (비즈니스 기준)
    Instant ingestTime   // 서버 수신 시각  (신뢰도 기준, 지연 측정용)
)
```

- 집계 기준: `eventTime` (실제 발생 시각이 비즈니스 의미에 맞음)
- 최신성 판단: `eventTime` 동일 시 `ingestTime` tiebreak
- 지연 모니터링: `ingestTime - eventTime` 분포로 네트워크 상태 추적

---

## 발견한 문제와 해결 과정

### 문제 1. 다중 인스턴스 시 MQTT 파이프라인 붕괴

**증상**: ingestion-service를 2대로 늘리면 서로 연결을 끊어냄 + Kafka에 중복 발행

**원인 분석**:

```
[AS-IS]
ingestion#1 (clientId="telemetryhub-ingestion") ──┐
ingestion#2 (clientId="telemetryhub-ingestion") ──┤ 동일 clientId
                                                   └→ 브로커가 먼저 연결된 쪽 강제 종료
                                                      + 일반 구독이라 두 인스턴스 모두 동일 메시지 수신
                                                      → Kafka에 2배 중복 produce
```

**해결**:

```java
// clientId에 인스턴스별 suffix 추가
String clientId = properties.getClientId() + "-" + instanceId;
```

```yaml
# Shared Subscription 활성화 (Mosquitto 2.0)
# $share/{group}/{topic} → group 내 1대에게만 전달
telemetryhub.ingestion.mqtt.shared-subscription-enabled: true
```

```
[TO-BE]
ingestion#1 (clientId="...-1") ─── $share/grp/topic ──┐
ingestion#2 (clientId="...-2") ─── $share/grp/topic ──┤ 브로커가 분산 전달
                                                        └→ 중복 없이 부하 분산
```

---

### 문제 2. Stream Processor DB 쓰기 병목

**증상**: 디바이스 수가 늘어날수록 Kafka consumer lag이 선형으로 증가

**원인 분석**:

집계 이벤트가 들어올 때마다 DB에 개별 upsert를 실행했다.

```java
// AS-IS: 이벤트 1건 = DB 왕복 1회
.foreach((key, aggregate) -> repository.upsert(aggregate));
```

초당 10,000건 이벤트 → 10,000번의 DB 커넥션 체크아웃 + 쿼리 + 응답.  
처리량이 올라갈수록 DB IOPS가 한계에 도달해 lag이 쌓인다.

**해결**: `BufferedJdbcProjectionWriter` — 버퍼에 누적 후 배치 flush

```java
// TO-BE: 버퍼에 쌓다가 batchSize 도달 또는 flushInterval마다 한 번에 처리
protected final void upsertBuffered(T aggregate) {
    synchronized (bufferMonitor) {
        bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
        if (bufferedAggregates.size() >= batchSize) {
            aggregatesToFlush = drainBufferLocked();
        }
    }
    if (aggregatesToFlush != null) {
        batchUpsert(jdbcTemplate, aggregatesToFlush); // 단일 쿼리로 N건 처리
    }
}
```

```sql
-- unnest로 배열을 한 번에 upsert
INSERT INTO telemetryhub_device_last_seen (device_id, last_event_time, ...)
SELECT * FROM unnest(?::text[], ?::timestamptz[], ...)
ON CONFLICT (device_id) DO UPDATE SET ...
WHERE excluded.last_event_time > telemetryhub_device_last_seen.last_event_time
```

**부하 테스트 결과**:

> 아래는 Device Simulator로 측정한 결과다.  
> 측정 환경: [CPU / Memory / Kafka 파티션 수 / PostgreSQL 스펙 기입]

| 측정 항목 | 건별 upsert (AS-IS) | 배치 upsert (TO-BE) | 개선율 |
|-----------|---------------------|---------------------|--------|
| DB write TPS | `___` tps | `___` tps | `___`% ↑ |
| Kafka consumer lag (p95) | `___` ms | `___` ms | `___`% ↓ |
| End-to-end latency (p95) | `___` ms | `___` ms | `___`% ↓ |
| DB connection pool 사용률 | `___`% | `___`% | `___`% ↓ |

> 부하 테스트 재현 방법은 아래 [성능 테스트 실행 방법](#성능-테스트-실행-방법) 참고.

---

### 문제 3. Backfill과 실시간 write 충돌

**증상**: 과거 데이터 재처리 중 실시간으로 들어온 최신 값이 과거 값으로 덮어씌워짐

**원인**:

```java
// AS-IS: 최신성 비교 없이 무조건 덮어씀
ON CONFLICT (device_id) DO UPDATE SET last_event_time = EXCLUDED.last_event_time
```

backfill이 `09:00:00` 이벤트를 처리하는 순간, 실시간으로 이미 저장된 `10:00:00`가 날아간다.

**해결**:

```sql
-- TO-BE: 더 최신인 경우에만 갱신
ON CONFLICT (device_id) DO UPDATE SET
    last_event_time = EXCLUDED.last_event_time
WHERE
    EXCLUDED.last_event_time > telemetryhub_device_last_seen.last_event_time
    OR (EXCLUDED.last_event_time = telemetryhub_device_last_seen.last_event_time
        AND EXCLUDED.last_ingest_time >= telemetryhub_device_last_seen.last_ingest_time)
```

backfill 실행 중에도 실시간 집계가 항상 더 최신 데이터를 보호한다.

---

### 문제 4. AtomicReference 동시성 버그

**증상**: 고부하 시 Kafka publish 메트릭 카운터 손실

**원인**:

```java
// AS-IS: check-then-act, 두 스레드가 동시에 get() 하면 한 쪽 업데이트 유실
KafkaPublishSnapshot current = snapshotRef.get();
snapshotRef.set(new KafkaPublishSnapshot(current.totalPublished() + 1, ...));
```

**해결**:

```java
// TO-BE: CAS 루프로 원자적 업데이트 보장
snapshotRef.updateAndGet(current ->
    new KafkaPublishSnapshot(current.totalPublished() + 1, Instant.now()));
```

---

## 성능 테스트 실행 방법

### 환경 시작

```bash
# 전체 스택 시작
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build

# ingestion 3대, stream-processor 2대로 스케일-아웃
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build \
  --scale ingestion-service=3 \
  --scale stream-processor=2
```

### 부하 생성

```bash
# 디바이스 1,000대, 500ms 간격으로 발행 시작
curl -X POST 'http://localhost:8091/sim/v1/start?devices=1000&intervalMs=500'

# 상태 확인
curl 'http://localhost:8091/sim/v1/status'

# 메트릭 확인
curl 'http://localhost:8091/sim/v1/metrics'
```

### 장애 시나리오 주입

```bash
# 늦은 도착 이벤트 30% 주입 (Late Event 처리 검증)
curl -X POST 'http://localhost:8091/sim/v1/scenario/LATE_EVENT?percent=30'

# 재연결 폭풍 시뮬레이션 (Thundering Herd 검증)
curl -X POST 'http://localhost:8091/sim/v1/scenario/RECONNECT_STORM?percent=50'

# 정상 복구
curl -X POST 'http://localhost:8091/sim/v1/stop'
```

### 측정 지표 (Prometheus / Grafana)

| 지표 | 의미 | 관찰 포인트 |
|------|------|-------------|
| `kafka_consumer_lag` | 처리 적체 | 부하 증가 시 lag 안정 여부 |
| `telemetryhub_ingestion_total_published` | 수집 처리량 | TPS 확인 |
| `telemetryhub_stream_projection_write_success_total` | DB 쓰기 처리량 | 배치 flush 단위 확인 |
| `telemetryhub_stream_projection_write_failure_total` | DB 쓰기 실패 | 이상 시 경보 |
| `ingestTime - eventTime` (계산) | 네트워크 지연 | LATE_EVENT 시나리오 시 분포 변화 |

---

## 로컬 실행 (전체 스택)

```bash
# 1. 전체 서비스 시작
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d

# 2. 시뮬레이터로 이벤트 발행
curl -X POST 'http://localhost:8091/sim/v1/start?devices=100&intervalMs=1000'

# 3. 집계 결과 조회
curl 'http://localhost:8094/analytics/v1/devices/latest?limit=10'
curl 'http://localhost:8094/analytics/v1/events-per-minute?from=2026-04-27T00:00:00Z&to=2026-04-27T23:59:59Z'
```

| 서비스 | 포트 | 역할 |
|--------|------|------|
| Device Simulator | 8091 | 가상 디바이스 제어 API |
| Ingestion Service | 8092 | MQTT 수집 |
| Stream Processor | 8093 | 실시간 집계 |
| Analytics API | 8094 | 조회 API |
| MQTT Broker | 1883 | Mosquitto |

---

## 문서

| 문서 | 내용 |
|------|------|
| [Planning.md](Planning.md) | 전체 설계 기획서 |
| [docs/why-kafka-streams.md](docs/why-kafka-streams.md) | Kafka Streams 선택 이유 분석 |
| [docs/interview-questions.md](docs/interview-questions.md) | 기술 면접 질문 24개 |
| [docs/scale-readiness.md](docs/scale-readiness.md) | 서비스별 스케일-아웃 준비도 평가 |
| [docs/analysis-report.md](docs/analysis-report.md) | 전체 코드 분석 보고서 |