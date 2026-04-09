# query-burst 동시성 문제 분석

분석 일자: 2026-04-09

---

## 요약 (심각도 순)

| 순위 | 문제 | 위치 | 심각도 | 영향 |
|------|------|------|--------|------|
| 1 | markProcessed()가 @Transactional 커밋 전에 실행 | StatisticsEventConsumer:72 | 높음 | DB 롤백 시 이벤트 영구 유실 |
| 2 | isDuplicate()-markProcessed() TOCTOU gap | ConsumerIdempotencyService:58-76 | 높음 | 통계/랭킹 이중 집계 |
| 3 | Redis 락 ↔ DB 비관적 락 전환 구간 보호 공백 | OrderFacade:85-91 | 높음 | 재고 이중 차감 (낮은 확률) |
| 4 | decrementSales() Read-Modify-Write 비원자적 | RankingService | 중간 | 랭킹 부정확 |
| 5 | 트랜잭션 내 동기 Kafka 블로킹 | OutboxMessageRelay | 중간 | DB 커넥션 풀 고갈 |
| 6 | 서버 시각 기반 토큰 리필 | RateLimitAspect:113 | 낮음 | Rate Limit 우회 가능성 |

---

## 문제 1: markProcessed()가 @Transactional 커밋 전에 실행

**파일**: `statistics/event/StatisticsEventConsumer.java:72`  
**심각도**: 높음 — 이벤트 영구 유실

### 문제

`@Transactional` 메서드 내부에서 `markProcessed()`를 호출하므로, DB 커밋 **전**에 Redis에 PROCESSED 마킹이 된다.

```
T1  consume() 진입 → 트랜잭션 시작
T2  handleOrderCreated() → DB UPDATE (미커밋 상태)
T3  markProcessed() → Redis에 PROCESSED SET  ← 커밋 전 마킹!
T4  트랜잭션 커밋 실패 → DB 롤백
T5  Kafka가 동일 메시지 재전달 → isDuplicate()=true → 처리 건너뜀 → 영구 유실
```

주석에는 "트랜잭션 커밋 성공 후 마킹"이라고 적혀 있지만 실제 코드는 반대로 동작한다.

### 해결 방법

**방법 A: `TransactionSynchronizationManager` 활용**
```java
// markProcessed() 호출 전에 트랜잭션 afterCommit 훅 등록
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
        }
    }
);
```

**방법 B: TransactionTemplate으로 분리 (OutboxMessageRelay와 동일 패턴)**
```java
// @Transactional 제거
public void consume(OrderEventPayload payload) {
    if (idempotencyService.isDuplicate(...)) return;

    transactionTemplate.execute(status -> {
        switch (payload.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(payload);
            case "ORDER_CANCELED" -> handleOrderCanceled(payload);
            default -> { return null; }
        }
        return null;
    });

    // 트랜잭션 커밋 완료 후 마킹
    idempotencyService.markProcessed(...);
}
```

---

## 문제 2: isDuplicate()-markProcessed() TOCTOU Race Condition

**파일**: `common/kafka/ConsumerIdempotencyService.java:58-76`  
**심각도**: 높음 — 통계/랭킹 이중 집계

### 문제

`isDuplicate()`(Redis GET)와 `markProcessed()`(Redis SET NX)가 두 개의 독립된 연산이다. 두 Consumer 인스턴스가 동시에 동일 이벤트를 수신하면 둘 다 false를 받고 비즈니스 로직을 실행한다.

```
Instance A: isDuplicate() → false  (키 없음)
Instance B: isDuplicate() → false  (키 아직 없음) ← 동시 진입!
Instance A: handleOrderCreated() 실행 (DB UPSERT)
Instance B: handleOrderCreated() 실행 (중복 집계!)
Instance A: markProcessed() → SET NX 성공
Instance B: markProcessed() → SET NX 실패 (이미 있음, 하지만 이미 늦음)
```

`setIfAbsent`는 중복 **마킹**만 방지하고, 중복 **실행** 자체를 막지 못한다.

### 해결 방법

`isDuplicate()` + `markProcessed()` 분리 패턴 대신, **선점(SET NX)을 먼저 수행**하여 원자적으로 처리 권한을 획득한다.

```java
// ConsumerIdempotencyService에 메서드 추가
public boolean tryMarkProcessing(String groupId, Long orderId, String eventType) {
    String key = buildKey(groupId, orderId, eventType);
    // SET NX 성공 시 true(처리 권한 획득), 실패 시 false(이미 처리 중 or 완료)
    return redissonClient.getBucket(key).setIfAbsent("PROCESSED", TTL);
}

// Consumer에서 사용
public void consume(OrderEventPayload payload) {
    if (!idempotencyService.tryMarkProcessing(GROUP_ID, payload.orderId(), payload.eventType())) {
        return; // 이미 다른 인스턴스가 처리 중 or 완료
    }
    // 비즈니스 로직 처리
}
```

> **주의**: 이 방식은 처리 중 장애 발생 시 재시도가 불가능하다는 trade-off가 있다.  
> 처리 실패 후 재시도를 허용하려면 PROCESSING/COMPLETED 두 단계 상태 머신(Producer 멱등성과 동일 패턴)이 필요하다.

---

## 문제 3: Redis 락 ↔ DB 비관적 락 전환 구간 보호 공백

**파일**: `order/application/OrderFacade.java:85-91`  
**심각도**: 높음 — 재고 이중 차감 (Redis 장애 시 발생)

### 문제

Redis 장애 발생 순간, 이미 Redis 분산 락을 보유한 요청(일반 SELECT)과 Fallback 경로(SELECT FOR UPDATE) 요청이 동시에 재고를 차감할 수 있다. 두 경로가 서로 다른 동기화 메커니즘을 사용하므로 상호 배제가 보장되지 않는다.

```
T1  Thread-A: tryLock("product:1:stock") 성공
T2  Thread-A: createOrder() → findById(1) (일반 SELECT, stock=100)
T3  Redis 장애 발생!
T4  Thread-B: tryLock() → RedisUnavailableException → DB 비관적 락 Fallback
T5  Thread-B: findByIdWithLock(1) → SELECT FOR UPDATE (stock=100, Thread-A와 무관)
T6  Thread-A: decreaseStock(10) → stock=90
T7  Thread-B: decreaseStock(10) → stock=90 (올바른 값: 80)
```

### 해결 방법

Redis 분산 락 경로에서도 `findByIdWithLock()`(SELECT FOR UPDATE)을 사용하여 DB 락을 이중으로 건다.  
Redis 분산 락은 DB 락 경합 감소를 위한 성능 최적화이며, 최종 정합성은 DB 락이 보장하도록 구조를 변경한다.

```java
// createOrder() 내부에서 일반 findById 대신 findByIdWithLock 사용
Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
```

---

## 문제 4: RankingService.decrementSales() Read-Modify-Write 비원자적

**파일**: `ranking/application/RankingService.java`  
**심각도**: 중간 — 랭킹 점수 부정확

### 문제

`decrementSales()`가 GET → 계산 → SET 3단계로 구현되어 있다. 이 사이에 다른 스레드가 score를 변경하면 덮어쓰기가 발생한다.

```
     현재 score = 100
T1  Thread-A (취소): getScore("42") → 100
T2  Thread-B (주문): addScore("42", 5) → score=105   ← 원자적 ZINCRBY
T3  Thread-A: next = max(0, 100 - 3) = 97
T4  Thread-A: add(97, "42") → score=97               ← Thread-B의 +5가 소실!
    올바른 값: 100 + 5 - 3 = 102
```

### 해결 방법

`incrementSales()`와 동일하게 `addScore()`(ZINCRBY)에 음수 값을 전달한다. ZINCRBY는 음수도 지원하며 원자적으로 실행된다.

```java
// 수정 전
Double current = set.getScore(productId.toString());
double next = Math.max(0, current - quantity);
set.add(next, productId.toString());

// 수정 후 (원자적 ZINCRBY 음수)
double newScore = set.addScore(productId.toString(), -quantity);
if (newScore < 0) {
    set.add(0.0, productId.toString()); // 0 미만 보정 (비원자적이지만 음수 방어)
}
```

> 0 미만 엄격히 방지하려면 `ZSCORE → 계산 → ZADD`를 Lua Script로 묶어야 한다.

---

## 문제 5: OutboxMessageRelay 트랜잭션 내 동기 Kafka 블로킹

**파일**: `order/event/OutboxMessageRelay.java`  
**심각도**: 중간 — Kafka 장애 시 DB 커넥션 풀 고갈

### 문제

`transactionTemplate.execute()` 내부에서 `kafkaTemplate.send().get(5s)`를 최대 100번 호출한다.  
Kafka 브로커 장애(leader election 등) 시 100건 × 5초 타임아웃 = 최대 500초 동안 DB 커넥션 1개 점유.  
HikariCP 기본 풀 크기(10)라면 10개 스레드가 동시 점유 시 전체 API 블로킹.

추가로 Java 25 Virtual Threads 환경에서 KafkaProducer 내부의 `synchronized` 블록이 캐리어 스레드 pinning을 유발할 수 있다.

### 해결 방법

Kafka send를 트랜잭션 밖으로 분리한다.

```
트랜잭션 1: PENDING → SENDING 상태 변경 + 이벤트 조회
            커밋 → DB 커넥션 반환

Kafka send (트랜잭션 없음): send().get(5s)

트랜잭션 2: SENDING → PUBLISHED or FAILED 마킹
```

또는 배치 내 첫 번째 Kafka 실패 시 나머지 99건 조기 중단(circuit breaker 적용).

---

## 문제 6: RateLimitAspect 서버 시각 기반 토큰 리필

**파일**: `common/ratelimit/RateLimitAspect.java:113`  
**심각도**: 낮음 — Rate Limit 우회 가능성

### 문제

`Instant.now().toEpochMilli()`를 각 애플리케이션 서버에서 호출하여 Redis Lua Script에 전달한다.  
Scale-out 환경에서 서버 간 시각이 수 초 이상 차이나면 `elapsed` 계산이 왜곡된다.  
서버 시각이 미래로 튀는 경우 `elapsed`가 비정상적으로 커져 토큰이 즉시 가득 차고 Rate Limit이 우회된다.

### 해결 방법

Lua Script 내부에서 `redis.call('TIME')`으로 Redis 서버 시각을 단일 소스로 사용한다.

```lua
-- ARGV[1] 제거, Lua 내부에서 Redis TIME 사용
local timeResult = redis.call('TIME')
local now = tonumber(timeResult[1]) * 1000 + math.floor(tonumber(timeResult[2]) / 1000)
```

---

## 수정 우선순위

1. **문제 4 (decrementSales)**: 1줄 수정, 즉각 반영 가능
2. **문제 1 (markProcessed 타이밍)**: `TransactionSynchronizationManager` 또는 `TransactionTemplate` 분리
3. **문제 2 (TOCTOU)**: `tryMarkProcessing()` SET NX 선점 패턴으로 변경
4. **문제 3 (Fallback 공백)**: `findByIdWithLock()` 이중 적용
5. **문제 5 (Kafka 블로킹)**: Kafka send를 트랜잭션 밖으로 분리
6. **문제 6 (서버 시각)**: Lua Script에서 `redis.call('TIME')` 사용
