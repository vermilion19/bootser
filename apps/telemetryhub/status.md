# TelemetryHub Status

## 현재 상태
- 현재 기준으로 `device-simulator` MVP는 완료 상태다.
- 현재 기준으로 `ingestion-service` MVP 1차도 완료 상태다.
- 다음 우선순위는 실행 경로 문서화와 이후 `stream-processor` 설계다.
- 실제 MQTT broker 연동은 보류 상태이며, broker는 나중에 Docker로 띄운 뒤 smoke test를 붙일 예정이다.

## 완료
- `apps/telemetryhub` 멀티모듈 등록 완료
- 최상위 모듈 디렉토리 구성 완료
- `contracts` 모듈 기본 이벤트 계약 정의 완료
- `device-simulator` MVP 구현 완료
- `device-simulator` 구조 설명 문서 작성 완료
- `ingestion-service` 기본 골격 구현 완료
- `ingestion-service` MQTT inbound adapter 구현 완료
- simulator → ingestion HTTP bridge 모드 구현 완료
- `ingestion-service` 실제 MQTT subscriber 모드 구현 완료
- `ingestion-service` Kafka publish 실제 연동 모드 준비 완료
- `ingestion-service` 실패 단계 분류 및 Kafka publish 관측 보강 완료

## device-simulator 완료 범위
- 제어 API
- 이벤트 생성기
- 시뮬레이터 loop
- publisher 라우팅 (`LOGGING` / `MEMORY` / `MQTT`)
- 브로커 없는 `MEMORY` 모드 검증 API
- 실제 MQTT 연결 확장 지점 확보
- 실제 MQTT 클라이언트 어댑터 골격 추가

## 진행 예정
- observability 메트릭 세분화
- simulator/ingestion 연동 실행 문서 정리
- simulator → broker → ingestion → kafka 실행 경로 문서 정리

## 보류
- 실제 MQTT broker 연동 smoke test
- Docker 기반 broker 환경 구성
- stream processor 세부 구현
- batch backfill 세부 구현
- analytics api / serving db 구현
- OLAP / schema registry / DLQ 같은 확장 로드맵 항목

## 참고 문서
- 설계 기획서: [Planning.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/Planning.md)
- 시뮬레이터 구조 설명: [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- 인제스천 구조 설명: [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- 실행 흐름 문서: [runtime-flows.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/runtime-flows.md)
