# query-burst 동시성 문제 분석

분석 일자: 2026-04-09  
업데이트: 2026-04-10 (전 항목 코드 반영 확인)

---

## 요약 (심각도 순)

| 순위 | 문제 | 위치 | 심각도 | 영향 | 상태 |
|------|------|------|--------|------|------|
| 1 | markProcessed()가 @Transactional 커밋 전에 실행 | StatisticsEventConsumer | 높음 | DB 롤백 시 이벤트 영구 유실 | ✅ 해결 |
| 2 | isDuplicate()-markProcessed() TOCTOU gap | ConsumerIdempotencyService | 높음 | 통계/랭킹 이중 집계 | ✅ 해결 |
| 3 | Redis 락 ↔ DB 비관적 락 전환 구간 보호 공백 | OrderFacade / OrderService | 높음 | 재고 이중 차감 (낮은 확률) | ✅ 해결 |
| 4 | decrementSales() Read-Modify-Write 비원자적 | RankingService | 중간 | 랭킹 부정확 | ✅ 해결 |
| 5 | 트랜잭션 내 동기 Kafka 블로킹 | OutboxMessageRelay | 중간 | DB 커넥션 풀 고갈 | ✅ 해결 |
| 6 | 서버 시각 기반 토큰 리필 | RateLimitAspect | 낮음 | Rate Limit 우회 가능성 | ✅ 해결 |

---

## 문제 1: markProcessed()가 @Transactional 커밋 전에 실행

**파일**: `statistics/event/StatisticsEventConsumer.java`  
**심각도**: 높음 — 이벤트 영구 유실  
**상태**: ✅ 해결됨

### 문제

`@Transactional` 메서드 내부에서 `markProcessed()`를 호출하면 DB 커밋 **전**에 Redis에 PROCESSED 마킹이 된다.

```
T1  consume() 진입 → 트랜잭션 시작
T2  handleOrderCreated() → DB UPDATE (미커밋 상태)
T3  markProcessed() → Redis에 PROCESSED SET  ← 커밋 전 마킹!
T4  트랜잭션 커밋 실패 → DB 롤백
T5  Kafka가 동일 메시지 재전달 → isDuplicate()=true → 처리 건너뜀 → 영구 유실
```

### 해결 (적용됨)

`TransactionSynchronizationManager`로 afterCommit 훅을 등록하여 커밋 성공 후에만 PROCESSED로 승격.  
롤백 시 `afterCompletion()`에서 `clearProcessing()`을 호출해 재처리 가능 상태로 복원.

```java
// StatisticsEventConsumer.registerSynchronization()
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        idempotencyService.markProcessed(GROUP_ID, payload.orderId(), payload.eventType());
    }

    @Override
    public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
            idempotencyService.clearProcessing(GROUP_ID, payload.orderId(), payload.eventType());
        }
    }
});
```

---

## 문제 2: isDuplicate()-markProcessed() TOCTOU Race Condition

**파일**: `common/kafka/ConsumerIdempotencyService.java`  
**심각도**: 높음 — 통계/랭킹 이중 집계  
**상태**: ✅ 해결됨

### 문제

`isDuplicate()`(Redis GET)와 `markProcessed()`(Redis SET NX)가 두 개의 독립된 연산이면, 두 Consumer 인스턴스가 동시에 동일 이벤트를 수신할 때 둘 다 false를 받고 비즈니스 로직을 중복 실행한다.

```
Instance A: isDuplicate() → false  (키 없음)
Instance B: isDuplicate() → false  (키 아직 없음) ← 동시 진입!
Instance A: handleOrderCreated() 실행 (DB UPSERT)
Instance B: handleOrderCreated() 실행 (중복 집계!)
```

### 해결 (적용됨)

`isDuplicate()` + `markProcessed()` 분리 패턴을 제거하고 **SET NX 선점**으로 처리 권한을 원자적으로 획득.  
PROCESSING/PROCESSED 2단계 상태 머신으로 처리 중 장애 시 재시도 가능.

```
tryStartProcessing()  → SET NX "PROCESSING" (TTL 10분)
                         성공: 처리 권한 획득
                         실패: 이미 처리 중 or 완료 → skip

afterCommit()         → markProcessed()  SET "PROCESSED" (TTL 25시간)
afterCompletion(FAIL) → clearProcessing()  키 삭제 (재처리 허용)
```

---

## 문제 3: Redis 락 ↔ DB 비관적 락 전환 구간 보호 공백

**파일**: `order/application/OrderFacade.java`, `order/application/OrderService.java`  
**심각도**: 높음 — 재고 이중 차감 (Redis 장애 시 발생)  
**상태**: ✅ 해결됨

### 문제

Redis 장애 발생 순간, Redis 분산 락을 보유한 요청(일반 SELECT)과 Fallback 경로(SELECT FOR UPDATE) 요청이 동시에 재고를 차감하면, 두 경로가 서로 다른 동기화 메커니즘을 사용하므로 상호 배제가 보장되지 않는다.

```
T1  Thread-A: tryLock("product:1:stock") 성공
T2  Thread-A: createOrder() → findById(1) (일반 SELECT, stock=100)
T3  Redis 장애!
T4  Thread-B: tryLock() → RedisUnavailableException → DB 비관적 락 Fallback
T5  Thread-B: findByIdWithLock(1) → SELECT FOR UPDATE (stock=100, Thread-A와 무관)
T6  Thread-A: decreaseStock(10) → stock=90
T7  Thread-B: decreaseStock(10) → stock=90  (올바른 값: 80)
```

### 해결 (적용됨)

Redis 분산 락 경로(`createOrder()`)에서도 `findByIdWithLock()`(SELECT FOR UPDATE)을 사용.  
Redis 분산 락은 DB 락 경합 감소를 위한 성능 최적화이며, 최종 정합성은 DB 락이 보장.

```java
// OrderService.createOrder() — Redis 락 경로
item -> productRepository.findByIdWithLock(item.productId())  // SELECT FOR UPDATE
        .orElseThrow(...)

// OrderService.createOrderWithPessimisticLock() — Fallback 경로
item -> productRepository.findByIdWithLock(item.productId())  // 동일하게 SELECT FOR UPDATE
        .orElseThrow(...)
```

두 경로 모두 `findByIdWithLock()`을 사용하므로, Redis 장애 전환 구간에도 DB 락이 상호 배제를 보장한다.

---

## 문제 4: RankingService.decrementSales() Read-Modify-Write 비원자적

**파일**: `ranking/application/RankingService.java`  
**심각도**: 중간 — 랭킹 점수 부정확  
**상태**: ✅ 해결됨

### 문제

GET → 계산 → SET 3단계 사이에 다른 스레드가 score를 변경하면 덮어쓰기가 발생한다.

```
     현재 score = 100
T1  Thread-A (취소): getScore("42") → 100
T2  Thread-B (주문): addScore("42", 5) → score=105   ← 원자적 ZINCRBY
T3  Thread-A: next = max(0, 100 - 3) = 97
T4  Thread-A: add(97, "42") → score=97               ← Thread-B의 +5가 소실!
    올바른 값: 100 + 5 - 3 = 102
```

### 해결 (적용됨)

`addScore()`(ZINCRBY)에 음수 값을 전달하여 원자적으로 처리.  
0 미만 방어는 비원자적이지만, score가 음수가 되는 상황 자체가 비정상이므로 허용 가능한 trade-off.

```java
double nextScore = set.addScore(String.valueOf(productId), -quantity);
if (nextScore < 0) {
    set.add(0.0, String.valueOf(productId)); // 0 미만 보정
}
```

> 0 미만을 엄격히 방지하려면 `ZSCORE → 계산 → ZADD`를 Lua Script로 묶어야 한다.

---

## 문제 5: OutboxMessageRelay 트랜잭션 내 동기 Kafka 블로킹

**파일**: `order/event/OutboxMessageRelay.java`  
**심각도**: 중간 — Kafka 장애 시 DB 커넥션 풀 고갈  
**상태**: ✅ 해결됨

### 문제

`transactionTemplate.execute()` 내부에서 `kafkaTemplate.send().get(5s)`를 최대 100번 호출하면,  
Kafka 장애 시 100건 × 5초 = 최대 500초 동안 DB 커넥션 1개 점유.  
HikariCP 기본 풀 크기(10)라면 10개 스레드가 동시 점유 시 전체 API 블로킹.

### 해결 (적용됨)

Kafka send를 트랜잭션 밖으로 분리하여 DB 커넥션을 즉시 반환.

```
트랜잭션 1 (claimPublishCandidates):
  PENDING/STALE SENDING → SENDING 상태 변경 + 이벤트 조회
  커밋 → DB 커넥션 반환

Kafka send (트랜잭션 없음):
  kafkaTemplate.send().get(5s)

트랜잭션 2 (markPublished / markPublishFailed):
  SENDING → PUBLISHED or FAILED 마킹
```

---

## 문제 6: RateLimitAspect 서버 시각 기반 토큰 리필

**파일**: `common/ratelimit/RateLimitAspect.java`  
**심각도**: 낮음 — Rate Limit 우회 가능성  
**상태**: ✅ 해결됨

### 문제

`Instant.now().toEpochMilli()`를 각 애플리케이션 서버에서 호출하여 Redis Lua Script에 전달하면,  
Scale-out 환경에서 서버 간 시각 차이로 `elapsed` 계산이 왜곡된다.  
서버 시각이 미래로 튀면 `elapsed`가 비정상적으로 커져 토큰이 즉시 가득 차고 Rate Limit이 우회된다.

### 해결 (적용됨)

Lua Script 내부에서 `redis.call('TIME')`으로 Redis 서버 시각을 단일 소스로 사용.  
`Instant.now()`는 abuse-detection Kafka 이벤트의 로그용 타임스탬프에만 사용되며 Rate Limit 로직과 무관.

```lua
local timeResult = redis.call('TIME')
local now = tonumber(timeResult[1]) * 1000 + math.floor(tonumber(timeResult[2]) / 1000)
```