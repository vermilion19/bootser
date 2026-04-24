# TelemetryHub Stream Processor Ops Guide

## 목적
이 문서는 `stream-processor`를 scale-out 해서 운영할 때 확인해야 할 체크리스트와 튜닝 포인트를 정리한다.

## 현재 write 경로 요약
- Kafka Streams topology가 aggregate를 만든다.
- projection writer는 key 단위로 짧은 시간 동안 aggregate를 coalesce 한다.
- flush 시점에는 `JdbcTemplate.batchUpdate`로 read model 테이블에 upsert 한다.

즉 write 경로는 `foreach -> 단건 upsert`가 아니라 `foreach -> in-memory coalesce -> batch upsert` 구조다.

## 운영 체크리스트

### 1. Kafka partition
- `telemetryhub.raw-events` partition 수가 `stream-processor` 활성 task 수 이상인지 확인한다.
- replica 수를 늘릴 때는 partition 수가 먼저 늘어나는지 확인한다.
- `DEVICE_ID` key 전략이 유지되는지 확인한다.

### 2. Streams 인스턴스 설정
- `TELEMETRYHUB_STREAM_NUM_STREAM_THREADS`가 partition 수보다 과도하게 크지 않은지 확인한다.
- `TELEMETRYHUB_STREAM_NUM_STANDBY_REPLICAS`가 복구 시간 목표에 맞는지 확인한다.
- `TELEMETRYHUB_STREAM_STATE_DIR`가 replica별 local volume에 매핑되는지 확인한다.

### 3. Projection batch 설정
- `TELEMETRYHUB_STREAM_PROJECTION_BATCH_BATCH_SIZE`
- `TELEMETRYHUB_STREAM_PROJECTION_BATCH_FLUSH_INTERVAL`
- `TELEMETRYHUB_STREAM_PROJECTION_BATCH_MAX_BUFFERED_ENTRIES`

기본 권장 시작값:
- batch size: `200`
- flush interval: `250ms`
- max buffered entries: `10000`

### 4. DB 확인
- projection table unique index가 실제 upsert key와 일치하는지 확인한다.
- write latency와 lock wait를 확인한다.
- hot bucket 또는 hot device로 충돌이 몰리는지 확인한다.
- DB connection pool saturation이 없는지 확인한다.

### 5. 관측 포인트
- `/actuator/prometheus`에서 projection success/failure 카운터를 본다.
- Kafka consumer lag를 본다.
- DB CPU, lock wait, row update rate를 본다.
- rebalance 이후 restore 시간이 목표 범위인지 확인한다.

## 튜닝 가이드

### 1. 처리량이 부족할 때
- 먼저 Kafka partition 수를 늘린다.
- 다음으로 `stream-processor` replica 수를 늘린다.
- 마지막으로 인스턴스 내부 `num-stream-threads`를 조정한다.

### 2. DB contention이 높을 때
- batch size를 올려 round trip 수를 줄인다.
- flush interval을 약간 늘려 같은 key write를 더 많이 coalesce 한다.
- write hotspot이 특정 bucket에 몰리면 bucket granularity 또는 write model 분리를 검토한다.
- DB 인덱스 수와 넓이를 줄여 write amplification을 낮춘다.

### 3. 지연이 높을 때
- batch size를 낮추고 flush interval을 줄인다.
- replica를 늘려 task를 분산한다.
- standby replica 수가 과도하면 restore 비용과 메모리 사용을 같이 점검한다.

### 4. 메모리 압박이 있을 때
- `max-buffered-entries`를 줄인다.
- flush interval을 줄여 버퍼 체류 시간을 낮춘다.
- batch size를 과도하게 높이지 않는다.

## 부하 테스트 순서
1. source topic partition 수를 명시한다.
2. `stream-processor=1`에서 baseline을 측정한다.
3. `stream-processor=2`, `stream-processor=3`으로 replica를 늘린다.
4. 각 단계에서 Kafka lag, DB write latency, projection failure를 기록한다.
5. 병목이 Kafka인지 DB인지 먼저 분리한다.

## 남는 제약
- `device_last_seen`는 key 충돌이 device 단위로 집중될 수 있다.
- minute bucket aggregate는 이벤트가 몰리는 시간대에 unique index contention이 커질 수 있다.
- backfill과 실시간 write가 같은 read model을 동시에 갱신하면 contention이 더 커질 수 있다.
