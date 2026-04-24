# TelemetryHub Status

## Update 2026-04-24
- `device-simulator`, `ingestion-service`, `stream-processor`, `analytics-api`, `batch-backfill` 패키지 구조를 역할별 하위 패키지로 재정리
- 서비스 리뷰 문서를 새 패키지 구조 기준으로 갱신
- 리팩터링 후 각 모듈 컴파일 확인 완료

## 현재 상태
- `device-simulator` MVP 완료
- `ingestion-service` MVP 1차 완료
- `stream-processor` 실시간 집계와 DB upsert 경계 완료
- `analytics-api` 조회 전용 골격 완료
- `batch-backfill` plan/source/target/job 골격 완료

## 최근 구조 리팩터링 요약
### `device-simulator`
- `application.control`
- `application.preview`
- `application.publisher`
- `application.runtime`
- `config.mqtt`
- `infrastructure.message`
- `infrastructure.mqtt`
- `infrastructure.publisher`
- `infrastructure.store`

### `ingestion-service`
- `application.ingest`
- `application.normalize`
- `application.publisher`
- `application.metrics`
- `application.mqtt`
- `application.failure`
- `config.publisher`
- `config.mqtt`
- `config.runtime`
- `infrastructure.normalize`
- `infrastructure.publisher`
- `infrastructure.mqtt`
- `infrastructure.store`

### `stream-processor`
- `application.plan`
- `application.projection`
- `application.metrics`
- `infrastructure.topology`
- `infrastructure.projection`
- `infrastructure.serde`

### `analytics-api`
- `application.query`

### `batch-backfill`
- `application.plan`
- `application.io`
- `application.execution`
- `infrastructure.job`
- `infrastructure.source`
- `infrastructure.target`

## 참고 문서
- 기획 문서: [Planning.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/Planning.md)
- 시뮬레이터 구조: [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- 인제스천 구조: [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- 스트림 프로세서 구조: [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md)
- 애널리틱스 API 구조: [analytics-api-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/analytics-api-review.md)
- 배치 백필 구조: [batch-backfill-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/batch-backfill-review.md)
- 실행 흐름: [runtime-flows.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/runtime-flows.md)
- 스케일 기준: [scale-readiness.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/scale-readiness.md)
