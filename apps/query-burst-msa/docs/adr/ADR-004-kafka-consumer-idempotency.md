# ADR-004: Kafka Consumer 멱등성을 서비스 특성에 맞게 다르게 구현

- **Status**: Accepted
- **Date**: 2025-04-13

## Context

Kafka는 기본적으로 at-least-once 전달을 보장한다.  
네트워크 장애, Consumer 재시작, 리밸런싱 등의 상황에서 동일한 이벤트가 두 번 이상 전달될 수 있다.

`analytics-service`와 `ranking-service`는 모두 `order-events`를 구독하며, 중복 처리 시 데이터 오염이 발생한다.

- `analytics-service`: 중복 처리 시 일별 매출이 이중으로 집계됨
- `ranking-service`: 중복 처리 시 상품 판매량이 이중으로 증가함

### 두 서비스의 차이점

| 구분 | analytics-service | ranking-service |
|------|-------------------|-----------------|
| 저장소 | PostgreSQL | Redis |
| 상태 보존 | 영구 | TTL 기반 (최대 7일) |
| 처리 특성 | DB upsert, 집계 | Redis addScore |
| 장애 복구 | DB 재조회로 복구 가능 | Redis 유실 시 복구 어려움 |

## Decision

두 서비스의 저장소 특성에 맞게 멱등성 구현을 다르게 한다.

### analytics-service: DB unique key 기반

```java
// ProcessedOrderEventEntity — eventKey에 unique 제약
String eventKey = eventType + ":" + orderId + ":" + occurredAt;
if (processedOrderEventRepository.existsByEventKey(eventKey)) {
    return; // 이미 처리된 이벤트
}
// ... 집계 처리 ...
processedOrderEventRepository.save(ProcessedOrderEventEntity.create(consumerGroup, eventKey));
```

- `eventKey`에 DB unique constraint를 걸어 동시에 두 Consumer가 같은 이벤트를 처리하려 할 때 하나는 constraint violation으로 실패
- DB 트랜잭션과 멱등성 체크가 동일 트랜잭션 내에서 처리되어 원자성 보장
- 처리 이력이 영구적으로 보존되어 감사 추적 가능

### ranking-service: Redis setIfAbsent 기반

```java
String eventKey = "ranking:event:" + eventType + ":" + orderId + ":" + occurredAt;
Boolean firstSeen = stringRedisTemplate.opsForValue()
        .setIfAbsent(eventKey, "1", Duration.ofDays(7));
if (!Boolean.TRUE.equals(firstSeen)) {
    return; // 이미 처리된 이벤트
}
// ... Redis addScore ...
```

- Redis `SETNX`의 원자성을 활용하여 최초 처리 여부 판별
- 랭킹 데이터 자체가 Redis에 있으므로 멱등성 체크도 Redis에서 처리 (저장소 불일치 없음)
- TTL 7일: Kafka retention 기간 이후 재처리 시나리오를 고려한 값

### 이벤트 키 설계

```
{eventType}:{orderId}:{occurredAt}
예) ORDER_CREATED:1234567890:2025-04-13T18:00:00
```

- `orderId`만으로는 같은 주문의 서로 다른 이벤트 타입(CREATED, CANCELED)을 구분 불가
- `occurredAt`을 포함하여 동일 주문의 다른 이벤트도 개별 추적

## Consequences

**장점**
- 각 서비스의 저장소 특성을 활용하여 외부 의존성 추가 없이 멱등성 구현
- DB 기반(analytics): 영구 이력, 트랜잭션 원자성
- Redis 기반(ranking): 빠른 체크, 저장소 일관성

**단점 및 트레이드오프**
- `analytics-service`의 `processed_order_events` 테이블이 계속 증가 → 오래된 레코드 정리 정책 필요
- `ranking-service`의 멱등성 키가 TTL 만료 후 사라지므로, 7일 이상 지난 이벤트가 재처리되면 중복 처리 가능 (Kafka retention 기간과 TTL을 맞춰야 함)
- 두 서비스의 구현 방식이 달라 운영 시 각각의 특성을 이해해야 함
