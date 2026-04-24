# TelemetryHub Status

## 현재 상태
- `device-simulator` MVP 완료
- `ingestion-service` MVP 1차 완료
- `stream-processor` 실시간 집계와 DB upsert 경계 완료
- `analytics-api` 조회 전용 골격 완료
- `region_heatmap` 집계 및 조회 API 완료
- `batch-backfill` 골격 완료
- `batch-backfill` dry-run replay 경계 완료
- `batch-backfill` 파일 기반 raw source reader 완료
- `batch-backfill` target별 실제 writer 완료
- `안전 점수`와 `주행 리포트`는 후순위로 보류
- 실제 MQTT broker 및 Kafka end-to-end smoke test는 아직 보류

## 완료
- `apps/telemetryhub` 멀티모듈 등록 완료
- `contracts` 기본 이벤트 계약 정의 완료
- `device-simulator` MVP 구현 및 문서화 완료
- `ingestion-service` MQTT inbound / bridge / Kafka publish 경계 구현 완료
- `ingestion-service` 실패 단계 분류 및 관측 보강 완료
- `stream-processor` `device_last_seen` 집계 완료
- `stream-processor` `events_per_minute` 집계 완료
- `stream-processor` `driving_event_counter` 집계 완료
- `stream-processor` `region_heatmap` 집계 완료
- `stream-processor` read model entity / repository / projection writer 완료
- `stream-processor` projection write metrics 및 `/stream/v1/metrics` API 완료
- scale readiness 기준 문서 작성 완료
- `analytics-api` 조회 API 골격 구현 완료
- `analytics-api` 지역 히트맵 조회 API 구현 완료
- `batch-backfill` Spring Batch 골격 및 backfill plan 모델 구현 완료
- `batch-backfill` stub source reader / routing writer / dry-run replay step 구현 완료
- `batch-backfill` NDJSON 파일 기반 source reader 및 fallback routing 구현 완료
- `batch-backfill` target별 실제 집계 writer 구현 완료

## device-simulator 완료 범위
- 제어 API
- 이벤트 생성기
- 시나리오 반영 loop
- publisher 라우팅 (`LOGGING` / `MEMORY` / `BRIDGE` / `MQTT`)
- 브로커 없는 `MEMORY` 검증 API
- 브로커 없는 simulator -> ingestion bridge 모드
- 실제 MQTT 연결 확장 지점

## ingestion-service 완료 범위
- raw 메시지 입력 API
- MQTT inbound adapter
- MQTT subscriber lifecycle
- topic 기반 이벤트 타입 판별
- payload normalize
- ingest time enrichment
- publisher 라우팅 (`LOGGING` / `MEMORY` / `KAFKA`)
- 실패 단계 분류 (`NORMALIZE` / `PUBLISH`)
- Kafka publish 관측

## stream-processor 완료 범위
- source topic 기반 Kafka Streams topology
- `device_last_seen` state store 집계
- `events_per_minute` state store 집계
- `driving_event_counter` state store 집계
- `region_heatmap` state store 집계
- read model JPA entity / repository
- topology 결과를 read model 테이블로 upsert 하는 projection writer
- projection write 성공/실패 관측
- `/stream/v1/metrics` 조회 API

## analytics-api 완료 범위
- 조회 전용 애플리케이션 골격
- 최신 디바이스 상태 조회
- 분당 이벤트 집계 조회
- driving event counter 조회
- 지역 히트맵 조회

## batch-backfill 완료 범위
- Spring Batch 애플리케이션 골격
- 기본 backfill plan 모델
- source type / target / overwrite mode 정의
- dry-run 기준 job / step 준비
- stub source 기반 replay 흐름
- routing writer 기반 target write 경계
- NDJSON 파일 기반 raw source reader
- target별 DB upsert writer

## 진행 예정
- 실제 broker + kafka 기반 smoke test
- `stream-processor` late event / dedup 정책 구체화
- 실제 broker + kafka 기반 smoke test

## 보류
- Docker 기반 MQTT broker 환경 구성
- 안전 점수 read model 및 API
- 주행 리포트 read model 및 API
- DLQ / schema registry / raw archive 확장
- 이상 징후 모델링

## 참고 문서
- 기획 문서: [Planning.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/Planning.md)
- scale readiness 기준: [scale-readiness.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/scale-readiness.md)
- analytics API 설명: [analytics-api-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/analytics-api-review.md)
- batch backfill 설명: [batch-backfill-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/batch-backfill-review.md)
- 시뮬레이터 구조 설명: [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- 인제션 구조 설명: [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- 실행 흐름 문서: [runtime-flows.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/runtime-flows.md)
- 스트림 프로세서 설계: [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md)
