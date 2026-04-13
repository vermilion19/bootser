# ADR-001: Outbox Pattern으로 트랜잭션 안전한 이벤트 발행

- **Status**: Accepted
- **Date**: 2025-04-13

## Context

`order-service`는 주문 생성 시 두 가지 작업을 반드시 함께 수행해야 한다.

1. `orders` 테이블에 주문 저장 (DB 트랜잭션)
2. `order-events` 토픽으로 Kafka 이벤트 발행

이 두 작업은 서로 다른 시스템(RDBMS, Kafka)에 대한 쓰기이므로 원자성을 보장할 수 없다.

### 발생 가능한 장애 시나리오

| 순서 | 상황 | 결과 |
|------|------|------|
| DB 저장 성공 → Kafka 발행 실패 | 네트워크 단절, 브로커 다운 | 주문은 생성됐지만 analytics/ranking이 반영되지 않음 (데이터 불일치) |
| Kafka 발행 성공 → DB 커밋 실패 | DB 장애 | 존재하지 않는 주문에 대한 이벤트가 발행됨 (유령 이벤트) |

### 검토한 대안

**A. DB 저장 후 직접 Kafka 발행**
- 구현 단순
- Kafka 발행 실패 시 이벤트 유실. 재시도 로직을 애플리케이션 코드에 직접 구현해야 함
- 서비스 재시작 시 발행 대기 이벤트 소실

**B. Kafka Transactions (exactly-once semantics)**
- Kafka와 DB의 트랜잭션을 연동하는 것은 표준적으로 지원되지 않음
- Kafka 트랜잭션은 Kafka→Kafka 간 exactly-once에 한정됨

**C. Outbox Pattern (채택)**
- DB 트랜잭션 안에서 `outbox_events` 테이블에 이벤트 저장
- 별도 스케줄러(`OrderOutboxRelay`)가 polling하여 Kafka 발행
- DB 커밋 = 이벤트 저장 보장. 단일 트랜잭션으로 원자성 확보

## Decision

Outbox Pattern을 채택한다.

### 구현

```java
// OrderApplicationService.createOrder() — 단일 트랜잭션 내에서 처리
orderRepository.save(order);
outboxEventRepository.save(
    OutboxEventEntity.create(order.getId(), eventType.name(), JsonUtils.toJson(payload))
);
```

```java
// OrderOutboxRelay — 3초 주기 폴링
@Scheduled(fixedDelay = 3000)
public void relay() {
    List<OutboxEventEntity> candidates = claimPendingEvents(); // status=SENDING으로 선점
    for (OutboxEventEntity event : candidates) {
        kafkaTemplate.send(...).get();  // 동기 발행
        markPublished(event.getId());
    }
}
```

### 이벤트 상태 전이

```
PENDING → SENDING → PUBLISHED
                  ↘ FAILED
```

- `PENDING → SENDING`: relay가 처리 시작 시 선점 (중복 처리 방지)
- `SENDING → PUBLISHED`: Kafka 발행 성공
- `SENDING → FAILED`: Kafka 발행 예외 발생

## Consequences

**장점**
- DB 커밋과 이벤트 저장의 원자성 보장
- Kafka 브로커가 일시 다운되어도 이벤트 유실 없음 (at-least-once)
- 발행 이력이 DB에 남아 디버깅 용이

**단점 및 트레이드오프**
- `outbox_events` 테이블 증가 → 주기적 PUBLISHED 레코드 정리 필요
- polling 주기(3초)만큼의 이벤트 발행 지연 존재
- `order-service` scale-out 시 여러 인스턴스가 동일한 PENDING 이벤트를 중복 처리할 수 있음
  → `SENDING` 상태로 선점하여 완화하나, 근본 해결은 ShedLock 도입 필요 (→ TODO)