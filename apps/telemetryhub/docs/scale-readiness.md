# TelemetryHub Scale Readiness

## 업데이트 2026-04-24

최근 구현 변경으로 scale readiness가 아래처럼 실제로 좋아졌다.

- `ingestion-service`
  - 고유 MQTT `clientId` 처리 추가
  - shared subscription 지원 추가
  - MQTT callback thread와 애플리케이션 처리를 inbound queue + worker로 분리
  - Kafka producer 신뢰성 설정 명시
- `device-simulator`
  - lifecycle mutation 직렬화
  - in-memory published message buffer bounded 처리
- `stream-processor`
  - `device_last_seen` write 경로에서 오래된 값이 최신 상태를 덮지 못하게 보호
  - Docker runtime에서 persistent state dir와 standby replica 기본값 적용
  - dedup 및 명시적 late-event grace filtering 구현
- `batch-backfill`
  - full-file materialization 대신 chunk 기반 source 읽기
  - replay된 `device_last_seen` write가 ingest-time 의미를 보존

### 수정된 현재 판단

이전 상태와 비교하면 지금 남은 주요 scale/readiness 간극은 더 좁아졌다.

1. 실제 broker + Kafka end-to-end smoke 검증은 아직 남아 있다
2. DLQ 정책은 아직 구현되지 않았다
3. stream-processor write batching은 여전히 향후 병목 후보이다
4. late-event 처리는 이제 명시적이지만, 아직 full window-grace semantics는 아니다

## 목적
이 문서는 TelemetryHub 각 서비스가 `독립적으로 scale 하기 쉬운 상태인지`를 점검하기 위한 기준 문서다.

지금 당장 모든 서비스를 scale 최적화 상태로 만드는 것이 목적은 아니다. 대신 아래 두 가지를 먼저 달성하는 것이 목적이다.

1. 나중에 scale을 막는 구조적 실수를 미리 피한다.
2. 이후 `analytics-api`, `batch-backfill` 구현 시 같은 기준을 계속 적용한다.

## 평가 기준
서비스별로 아래 세 단계로 평가한다.

- `현재 괜찮음`
  - 지금 구조가 독립 스케일링에 비교적 유리하다.
- `위험`
  - 지금 구조가 병목, 강한 결합, 단일 인스턴스 의존으로 이어질 가능성이 높다.
- `나중에 보강`
  - 지금 당장 막히지는 않지만 운영 단계 전에 보강이 필요하다.

주요 체크 항목은 아래와 같다.

- stateless 인가
- 비동기 경계가 있는가
- partition / key 전략이 명확한가
- write 병목이 어디서 생기는가
- 중복 처리 / 재처리 / late event 대응 지점이 있는가
- 특정 인스턴스 메모리에만 의존하는가

## 전체 판단
현재 TelemetryHub는 `독립 스케일링에 맞는 방향으로 설계는 잘 가고 있지만, 운영 레벨로 쉽게 scale 되는 상태까지는 아직 아님` 이라고 보는 것이 정확하다.

좋은 점:
- simulator / ingestion / stream processor / future serving layer가 역할 기준으로 나뉘어 있다.
- ingestion 이후 경계가 Kafka 기반 비동기 흐름이라 서비스별 독립 스케일링에 유리하다.
- contracts 모듈이 있어 서비스 간 데이터 계약이 분리되어 있다.

아직 부족한 점:
- stream processor의 DB upsert가 병목 후보다.
- dedup / late event / idempotency / DLQ 정책이 아직 완성되지 않았다.
- 실제 broker / kafka / db를 붙인 운영형 smoke test가 아직 없다.

## 서비스별 평가

### 1. device-simulator
상태: `나중에 보강`

현재 괜찮음:
- 이벤트 생성, 제어 API, publish mode가 분리되어 있다.
- `MEMORY`, `BRIDGE`, `MQTT` 모드가 있어 환경별로 전환 가능하다.
- 실제 브로커가 없어도 ingestion까지 연결 검증이 가능하다.
- `telemetryhub.simulator.runtime.published-message-buffer-enabled=false` 로 로컬 메모리 버퍼를 끌 수 있다.

위험:
- 제어 plane과 생성 loop가 한 프로세스 안에 있어 대규모 분산 simulator로 보긴 어렵다.
- 메모리 버퍼와 로컬 metrics에 일부 의존한다.
- 실제 부하 발생기 관점의 worker 분산 모델은 아직 없다.

나중에 보강:
- simulator worker와 control API 분리
- device shard 전략
- ramp-up / ramp-down / burst profile 지원
- 여러 worker 인스턴스를 하나의 시나리오로 제어하는 방식

판단:
- 지금 단계에서는 충분하다.
- simulator는 운영 서비스보다 실험 도구에 가깝기 때문에, 본격 scale 최적화는 뒤로 미뤄도 된다.

### 2. ingestion-service
상태: `현재 괜찮음`

현재 괜찮음:
- MQTT inbound 이후 내부 처리가 비교적 stateless 하다.
- normalize / enrich / publish 경계가 분리되어 있다.
- publisher mode와 subscriber 경계가 분리되어 있다.
- Kafka로 넘기는 구조는 독립 스케일링에 유리하다.
- `telemetryhub.ingestion.runtime.recent-event-buffer-enabled=false` 로 최근 이벤트 메모리 버퍼를 끌 수 있다.

위험:
- 아직 메모리 모드와 개발용 recent event 조회가 남아 있다.
- invalid payload, DLQ, retry 정책이 운영 수준으로 정리되진 않았다.
- topic key / partition 전략이 아직 강하게 문서화되진 않았다.

나중에 보강:
- Kafka key 전략 고정
- DLQ topic 및 재처리 정책
- idempotency 또는 duplicate 허용 범위 명시
- broker reconnect / backpressure 관찰 항목 보강

판단:
- 현재 구조상 독립 scale 하기 가장 쉬운 서비스다.
- 이후 운영 안정화만 보강하면 된다.

현재 반영된 개선:
- Kafka raw topic key 전략을 설정으로 명시화했다.
- 기본값은 `DEVICE_ID`라서 같은 device 이벤트가 같은 partition으로 모이도록 유지한다.

### 3. stream-processor
상태: `위험`

현재 괜찮음:
- Kafka Streams 기반으로 입력 경계가 비동기다.
- topology가 집계별로 나뉘어 있다.
- state store와 read model write 경계가 분리되어 있다.
- projection write metrics가 있어 병목 관측 출발점은 있다.
- `num-stream-threads`, `state-dir`, `processing-guarantee`, `num-standby-replicas`를 외부 설정으로 조정할 수 있다.

위험:
- DB upsert가 직접 병목이 될 가능성이 높다.
- Kafka partition 수와 processor scale 수가 함께 맞물린다.
- late event, dedup, idempotency 정책이 아직 구체화되지 않았다.
- state store와 DB write 사이의 실패 복구 전략이 아직 없다.

나중에 보강:
- Kafka topic partition 전략과 key 전략 명문화
- projection write batching 또는 비동기화 검토
- write failure retry / DLQ / dead letter store 설계
- backfill과 실시간 집계 정합성 규칙 정리
- read model DB 인덱스와 contention 분석

판단:
- 구조 방향은 맞지만, 가장 먼저 scale 병목이 드러날 가능성이 높은 곳이다.
- 이후 구현에서 가장 보수적으로 다뤄야 한다.

현재 반영된 개선:
- projection write를 JPA `find -> save` 방식에서 JDBC upsert 방식으로 바꿨다.
- unique key 기준 충돌을 DB에 맡겨 round trip 수를 줄였다.
- 이 변경으로 bucket 집계 write contention과 ORM 오버헤드를 줄일 수 있다.

### 4. analytics-api
상태: `나중에 보강`

현재 괜찮음:
- 아직 미구현이라 구조 선택 여지가 남아 있다.

위험:
- stream-processor의 read model과 너무 강하게 결합되면 scale이 어려워질 수 있다.
- serving query가 실시간 집계 테이블에 직접 과도하게 몰리면 DB 병목이 생길 수 있다.

나중에 보강:
- API는 가능하면 stateless 유지
- read model 조회 전용으로 설계
- 쓰기 경로와 분리
- 조회 패턴에 맞는 인덱스 / 캐시 / pagination 전략 도입
- 고비용 집계는 DB에서 즉석 계산하지 말고 precomputed read model 사용

판단:
- 아직 구현 전이라 가장 좋은 시점이다.
- 이 문서의 기준을 그대로 적용해서 설계하면 된다.

### 5. batch-backfill
상태: `나중에 보강`

현재 괜찮음:
- 아직 미구현이라 재처리 전략을 clean 하게 설계할 수 있다.

위험:
- 실시간 stream-processor와 같은 테이블을 동시에 갱신하면 충돌 위험이 있다.
- backfill 범위, overwrite 규칙, idempotency가 없으면 운영 중 재처리가 위험하다.

나중에 보강:
- backfill 전용 입력 경로
- replay 단위와 checkpoint
- overwrite / merge / skip 정책 명문화
- 실시간 처리와 재처리 충돌 방지 전략
- batch write 최적화

판단:
- 구현 전에 규칙을 정하면 된다.
- scale보다 정합성 설계가 우선이다.

## 지금 당장 고정해야 하는 기준
아래 항목은 지금부터 계속 지켜야 한다.

1. 서비스 간 비동기 경계를 유지한다.
2. 서비스는 가능하면 stateless 로 유지한다.
3. topic key 와 partition 기준을 명확히 한다.
4. read model 은 unique key 와 upsert 기준을 먼저 정의한다.
5. 중복 처리와 재처리를 넣을 자리를 미리 남겨둔다.
6. 개발용 메모리 저장소는 운영 경계와 분리된 모드로만 둔다.

운영 예시:
- simulator: `telemetryhub.simulator.runtime.published-message-buffer-enabled=false`
- ingestion: `telemetryhub.ingestion.runtime.recent-event-buffer-enabled=false`
- stream-processor: `telemetryhub.stream.num-stream-threads`, `telemetryhub.stream.state-dir`, `telemetryhub.stream.num-standby-replicas` 를 인스턴스 수와 파티션 수에 맞춰 조정
- ingestion Kafka key 전략: `telemetryhub.ingestion.publisher.kafka.key-strategy=DEVICE_ID`

## 나중에 해도 되는 것
아래 항목은 MVP 한 사이클 이후 실제 병목을 보고 보강해도 된다.

- autoscaling 기준
- thread 수 튜닝
- batch size 튜닝
- cache 세부 전략
- simulator 대규모 분산 실행 구조
- Kafka Streams suppression / repartition 최적화

## 다음 구현에 적용할 기준

### analytics-api 구현 시
- stateless 로 유지
- read model 조회 전용으로 설계
- stream-processor 내부 state store에 직접 의존하지 않기
- 고비용 집계는 조회 시점 계산보다 precomputed model 우선

### batch-backfill 구현 시
- 실시간 처리와 write 충돌을 먼저 정의
- idempotent 한 재처리 방식 우선
- replay 범위와 checkpoint를 명시
- “full rebuild” 와 “partial correction” 을 구분

## 결론
지금 당장 대규모 scale 리팩터링을 먼저 할 필요는 없다.

대신 지금부터 이 문서의 기준을 지키면서 다음 사이클을 진행하는 것이 맞다.

- ingestion-service 는 현재 방향 유지
- stream-processor 는 병목 후보로 보고 보수적으로 확장
- analytics-api / batch-backfill 은 이 기준을 반영해서 처음부터 설계
