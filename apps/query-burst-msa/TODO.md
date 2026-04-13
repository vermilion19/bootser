# query-burst-msa 포트폴리오 TODO

## 1순위 — 없으면 치명적

| 상태 | 항목 | 설명 |
|------|------|------|
| ✅ | **아키텍처 다이어그램** | README에 Mermaid 다이어그램 포함 |
| 🔲 | **부하테스트 결과** | k6 실행 후 TPS, 응답시간 그래프 캡처 → README에 삽입 |
| 🔲 | **Circuit Breaker** | order→catalog HTTP 호출에 Resilience4j CircuitBreaker 추가 |
| 🔲 | **Swagger / OpenAPI** | springdoc-openapi 의존성 추가, 각 서비스 API 문서화 |

## 2순위 — 있으면 크게 차별화

| 상태 | 항목 | 설명 |
|------|------|------|
| ✅ | **ADR 작성** | Outbox 패턴, 재고 예약 2-Phase, Redis 랭킹 설계 선택 이유 문서화 |
| 🔲 | **GitHub Actions CI** | 빌드 자동화 + 배지 추가 |
| 🔲 | **장애 시나리오 문서** | catalog 다운, Kafka lag 발생 시 시스템 동작 정리 |
| 🔲 | **Docker healthcheck** | DB/Kafka 준비 전 서비스 기동 방지 (`depends_on condition: service_healthy`) |

## 3순위 — 완성도

| 상태 | 항목 | 설명 |
|------|------|------|
| 🔲 | **전역 예외 처리** | 각 서비스에 `@RestControllerAdvice` 추가 |
| 🔲 | **요청 유효성 검증** | `@Valid` + ConstraintViolation 처리 |
| 🔲 | **단위/통합 테스트** | 핵심 로직(OrderApplicationService, RankingService 등) 테스트 |
| 🔲 | **Outbox ShedLock** | order-service scale-out 시 중복 relay 방지 (ShedLock + Redis) |

## 4순위 — 포트폴리오 자료

| 상태 | 항목 | 설명 |
|------|------|------|
| 🔲 | **README 이미지 삽입** | 아키텍처 다이어그램 + 부하테스트 결과 스크린샷 |
| 🔲 | **데모 GIF** | 주문 생성 → Kafka 전파 → 랭킹 반영 흐름 녹화 |
| 🔲 | **기술 블로그 포스팅** | Outbox 구현기, 재고 예약 설계, scale-out 실험 결과 |