# ADR-003: Redis Sorted Set 시간 버킷으로 실시간 상품 랭킹 구현

- **Status**: Accepted
- **Date**: 2025-04-13

## Context

`ranking-service`는 Kafka `order-events`를 구독하여 "최근 N시간 동안 가장 많이 팔린 상품 Top-K"를 실시간으로 제공해야 한다.

### 요구사항

- 주문 이벤트 발생 즉시(수 초 이내) 랭킹에 반영
- 주문 취소 시 해당 수량이 랭킹에서 차감
- 시간 윈도우(1~24시간) 범위를 파라미터로 받아 유연하게 조회 가능
- 높은 조회 트래픽에도 빠른 응답 (p95 < 200ms)
- 동일 이벤트가 중복으로 처리되어도 랭킹이 오염되지 않아야 함 (멱등성)

### 검토한 대안

**A. DB 집계 쿼리 (GROUP BY + SUM)**
- 구현 단순, 정확
- 대용량 주문 데이터에서 집계 쿼리 성능 저하
- 조회마다 전체 스캔 → 높은 트래픽 시 DB 병목

**B. 별도 집계 테이블 (analytics-service 방식)**
- 이벤트마다 집계 테이블을 upsert
- 정확하지만 `window` 개념을 집계 테이블로 표현하기 어려움
- 특정 시간 범위의 랭킹을 조회하려면 결국 범위 쿼리가 필요

**C. Redis Sorted Set + 시간 버킷 (채택)**
- 1시간 단위로 키를 분리(`RANK:hourly:yyyyMMddHH`)하여 이벤트 수량을 `addScore`
- 조회 시 요청한 윈도우 범위의 버킷을 임시 Sorted Set에 머지 후 Top-K 추출
- 메모리 기반으로 빠른 응답, 자동 TTL 만료

## Decision

Redis Sorted Set 기반 시간 버킷 전략을 채택한다.

### 구현

**이벤트 수신 시 (쓰기)**
```java
String key = "RANK:hourly:" + dateTime.format(HOUR_FMT); // e.g. RANK:hourly:2025041318
RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
double direction = eventType == ORDER_CREATED ? +1.0 : -1.0;
payload.items().forEach(item ->
    set.addScore(String.valueOf(item.productId()), item.quantity() * direction)
);
set.expire(Duration.ofHours(25)); // 25시간 후 자동 만료
```

**조회 시 (읽기)**
```java
// 요청한 windowHours 범위의 버킷을 임시 key에 머지
RScoredSortedSet<String> merged = redissonClient.getScoredSortedSet("ranking:window:tmp:" + ...);
for (int offset = 0; offset < windowHours; offset++) {
    RScoredSortedSet<String> bucket = getScoredSortedSet(hourlyKey(now.minusHours(offset)));
    bucket.entryRangeReversed(0, -1).forEach(e -> merged.addScore(e.getValue(), e.getScore()));
}
return merged.entryRangeReversed(0, size - 1); // Top-K
```

**멱등성**
```java
String eventKey = "ranking:event:" + eventType + ":" + orderId + ":" + occurredAt;
Boolean firstSeen = stringRedisTemplate.opsForValue().setIfAbsent(eventKey, "1", Duration.ofDays(7));
if (!Boolean.TRUE.equals(firstSeen)) return; // 중복 이벤트 무시
```

### 키 설계 요약

| 키 패턴 | TTL | 용도 |
|---------|-----|------|
| `RANK:hourly:yyyyMMddHH` | 25시간 | 시간별 상품 판매량 버킷 |
| `ranking:event:{type}:{orderId}:{time}` | 7일 | 이벤트 중복 처리 방지 |
| `ranking:window:{hours}:{ts}` | 조회 후 즉시 삭제 | 윈도우 머지용 임시 키 |

## Consequences

**장점**
- 이벤트 수신 즉시 랭킹 반영 (실시간)
- 버킷 TTL로 오래된 데이터 자동 정리
- `windowHours` 파라미터로 유연한 시간 범위 지원
- Redis `addScore`의 원자성으로 동시성 안전

**단점 및 트레이드오프**
- Redis 장애 시 랭킹 서비스 전체 불가 (DB 폴백 없음)
- 머지 연산이 O(windowHours × 버킷 크기)로, 윈도우가 크거나 상품이 많으면 조회 지연 가능
- 정확한 실시간 집계를 위해 Kafka Consumer가 이벤트를 순서대로 처리해야 함 (파티션 키 설계 중요)
