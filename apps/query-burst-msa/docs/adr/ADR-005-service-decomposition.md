# ADR-005: 서비스 분리 기준 — 쓰기/읽기 책임과 변경 주기

- **Status**: Accepted
- **Date**: 2025-04-13

## Context

`query-burst` 모놀리스를 MSA로 분리할 때 서비스 경계를 어떻게 나눌 것인지 결정해야 했다.  
서비스 경계를 잘못 나누면 과도한 서비스 간 통신이나 분산 트랜잭션이 늘어나 모놀리스보다 복잡해진다.

### 분리 원칙

1. **단일 책임**: 하나의 서비스는 하나의 비즈니스 도메인만 소유
2. **독립적 배포 가능**: 서비스 변경이 다른 서비스 배포에 영향을 주지 않아야 함
3. **DB 소유권 분리**: 서비스 간 DB 직접 참조 금지 (API or 이벤트로만 통신)
4. **쓰기/읽기 분리 (CQRS)**: 쓰기 일관성이 필요한 서비스와 읽기 최적화 서비스를 분리

## Decision

### 서비스 분리 결과

```
[Write Side]
order-service    — 주문 생성·상태 전이의 단일 진실 공급원
catalog-service  — 상품·재고의 단일 진실 공급원

[Read Side — Event-driven]
analytics-service — order-events 기반 일별 매출 Read Model (PostgreSQL)
ranking-service   — order-events 기반 실시간 랭킹 Read Model (Redis)

[독립 도메인]
member-service   — 회원 마스터, 다른 서비스와 느슨하게 연결
```

### 분리 이유 상세

**order-service / catalog-service 분리**

재고와 주문은 변경 주기와 부하 특성이 다르다.
- `catalog-service`: 상품 정보 변경 빈도 낮음, 읽기 트래픽 높음 (상품 탐색)
- `order-service`: 주문 생성 트래픽이 폭발적으로 증가할 수 있음

두 서비스를 분리함으로써 `catalog-service`는 읽기 캐싱 전략을, `order-service`는 쓰기 처리량 증대 전략을 독립적으로 적용할 수 있다.

**analytics-service / ranking-service 분리**

같은 `order-events`를 구독하지만 Read Model의 성격이 다르다.

| 구분 | analytics-service | ranking-service |
|------|-------------------|-----------------|
| 저장소 | PostgreSQL | Redis |
| 조회 패턴 | 기간별 집계 (날짜 범위 쿼리) | 실시간 Top-K |
| 갱신 주기 | 이벤트 발생 시 누적 | 이벤트 발생 시 즉시 |
| 확장 전략 | DB 인덱스/파티셔닝 | Redis Cluster |

하나의 서비스로 합치면 저장소 선택이 절충안이 되고, 각 조회 패턴에 최적화할 수 없다.

**member-service 분리**

회원 데이터는 다른 서비스에서 자주 참조되지만 `order-service`는 `memberId`만 저장하고 회원 정보를 직접 조회하지 않는다. 회원 서비스를 독립시켜 인증·프로필 변경이 주문 서비스 배포에 영향을 주지 않도록 한다.

### 서비스 간 통신 방식 선택

| 통신 | 방식 | 이유 |
|------|------|------|
| order → catalog (재고 예약) | 동기 HTTP | 주문 응답 전에 재고 확인 결과가 필요 (즉시 일관성) |
| order → analytics/ranking | 비동기 Kafka | 랭킹/통계는 약간의 지연 허용 가능, 느슨한 결합 필요 |

## Consequences

**장점**
- 각 서비스의 저장소, 확장 전략, 배포 주기를 독립적으로 결정 가능
- Write Side와 Read Side 분리로 쓰기 부하와 읽기 부하를 별개로 스케일아웃 가능
- 서비스 장애가 다른 서비스로 전파되는 범위 축소

**단점 및 트레이드오프**
- `order-service`가 `catalog-service`에 동기 의존 → catalog 장애 시 주문 불가 (Circuit Breaker로 완화 필요)
- analytics/ranking의 Read Model은 이벤트 처리 지연만큼 최신 데이터보다 늦음 (최종 일관성)
- 5개 서비스의 개별 배포·모니터링으로 운영 복잡도 증가
