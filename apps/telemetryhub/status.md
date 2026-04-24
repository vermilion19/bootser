# TelemetryHub Status

## 현재 상태
- `device-simulator` MVP 완료
- `ingestion-service` MVP 1차 완료
- `stream-processor` MVP 실시간 집계 3종 완료
- `stream-processor` DB upsert 경계 1차 완료
- `stream-processor` projection write metrics / API 완료
- 실제 MQTT broker 및 Kafka end-to-end smoke test는 아직 보류

## 완료
- `apps/telemetryhub` 멀티모듈 등록 완료
- 최상위 모듈 디렉토리 구성 완료
- `contracts` 모듈 기본 이벤트 계약 정의 완료
- `device-simulator` MVP 구현 완료
- `device-simulator` 구조 설명 문서 작성 완료
- `ingestion-service` 기본 골격 구현 완료
- `ingestion-service` MQTT inbound adapter 구현 완료
- simulator -> ingestion HTTP bridge 모드 구현 완료
- `ingestion-service` 실제 MQTT subscriber 모드 구현 완료
- `ingestion-service` Kafka publish 실제 연동 모드 준비 완료
- `ingestion-service` 실패 단계 분류 및 Kafka publish 관측 보강 완료
- `ingestion-service` 리뷰 문서 및 실행 흐름 문서 작성 완료
- `stream-processor` 설계 골격 및 설계 문서 작성 완료
- `stream-processor` `device_last_seen` Kafka Streams topology 구현 완료
- `stream-processor` `events_per_minute` Kafka Streams topology 구현 완료
- `stream-processor` `driving_event_counter` Kafka Streams topology 구현 완료
- `stream-processor` read model entity / repository / projection writer 추가 완료
- `stream-processor` topology -> DB upsert 경계 연결 완료
- `stream-processor` projection write 성공/실패 metrics 추가 완료
- `stream-processor` `/stream/v1/metrics` API 추가 완료

## device-simulator 완료 범위
- 제어 API
- 이벤트 생성기
- 시나리오 반영 loop
- publisher 라우팅 (`LOGGING` / `MEMORY` / `BRIDGE` / `MQTT`)
- 브로커 없는 `MEMORY` 모드 검증 API
- 브로커 없는 simulator -> ingestion bridge 모드
- 실제 MQTT 연결 확장 지점

## ingestion-service 완료 범위
- raw 메시지 입력 API
- MQTT inbound adapter
- MQTT subscriber lifecycle
- topic 기반 이벤트 타입 판별
- payload 파싱 및 normalize
- ingest time enrichment
- publisher 라우팅 (`LOGGING` / `MEMORY` / `KAFKA`)
- 실패 단계 분류 (`NORMALIZE` / `PUBLISH`)
- Kafka publish 관측 정보

## stream-processor 완료 범위
- source topic 기반 Kafka Streams topology 구성
- `device_last_seen` state store 집계
- `events_per_minute` state store 집계
- `driving_event_counter` state store 집계
- read model JPA entity / repository 구성
- topology 결과를 read model 테이블로 upsert 하는 projection writer 구성
- projection write 성공/실패 관측
- `/stream/v1/metrics` 조회 API

## 진행 예정
- `stream-processor` late event / dedup 정책 구체화
- read model 조회 경계를 `analytics-api`에서 사용하도록 연결
- 실제 broker + kafka 기반 smoke test

## 보류
- Docker 기반 MQTT broker 환경 구성
- `batch-backfill` 모듈 구현
- `analytics-api` / serving DB 구현
- DLQ / schema registry / raw archive 확장
- 지역별 히트맵, 안전 점수, 이상 징후 모델링

## 참고 문서
- 기획 문서: [Planning.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/Planning.md)
- 시뮬레이터 구조 설명: [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- 인제션 구조 설명: [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- 실행 흐름 문서: [runtime-flows.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/runtime-flows.md)
- 스트림 프로세서 설계: [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md)
