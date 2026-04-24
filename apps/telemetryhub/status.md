# TelemetryHub Status

## 현재 상태
- 현재 기준으로 `device-simulator` MVP는 완료 상태다.
- 다음 우선순위는 `ingestion-service` 기본 구조와 수집 경계 설계다.
- 실제 MQTT broker 연동은 보류 상태이며, broker는 나중에 Docker로 띄운 뒤 smoke test를 붙일 예정이다.

## 완료
- `apps/telemetryhub` 멀티모듈 등록 완료
- 최상위 모듈 디렉토리 구성 완료
- `contracts` 모듈 기본 이벤트 계약 정의 완료
- `device-simulator` MVP 구현 완료
- `device-simulator` 구조 설명 문서 작성 완료

## device-simulator 완료 범위
- 제어 API
- 이벤트 생성기
- 시뮬레이터 loop
- publisher 라우팅 (`LOGGING` / `MEMORY` / `MQTT`)
- 브로커 없는 `MEMORY` 모드 검증 API
- 실제 MQTT 연결 확장 지점 확보
- 실제 MQTT 클라이언트 어댑터 골격 추가

## 진행 예정
- `ingestion-service` 기본 구조 설계
- MQTT subscribe 경계 설계
- raw event validation / enrichment 설계
- Kafka raw topic publish 구조 설계
- observability 기본 메트릭 정의

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
