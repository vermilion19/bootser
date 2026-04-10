# query-burst Redis 사용 현황

---

## 키 구조 전체 맵

| 키 패턴 | 자료구조 | TTL | 관리 주체 | 목적 |
|---------|----------|-----|-----------|------|
| `LOCK:product:*:stock` | String (RLock) | 락 획득 시 지정 (5초) | `RedisDistributedLock` | 분산 락 |
| `FENCE:product:*:stock` | AtomicLong | 30일 | `RedisDistributedLock` | 펜싱 토큰 카운터 |
| `LOCK:outbox:relay:lock` | String (RLock) | 30초 | `OutboxMessageRelay` | 스케줄러 중복 실행 방지 |
| `IDEMPOTENCY:{key}` | String (JSON) | 5분 / 24시간 | `IdempotencyService` | 주문 중복 요청 방지 |
| `CONSUMER:{groupId}:{orderId}:{eventType}` | String | 10분 / 25시간 | `ConsumerIdempotencyService` | Kafka Consumer 멱등성 |
| `FLASH:{productId}:stock` | String (AtomicLong) | 24시간 | `FlashSaleService` | 플래시 세일 재고 |
| `RANK:hourly:{yyyyMMddHH}` | Sorted Set | 25시간 | `RankingService` | 시간대별 판매량 |
| `RANK:window:{N}h:{ts}` | Sorted Set | 5분 | `RankingService` | 슬라이딩 윈도우 집계 임시 키 |
| `RATE:{key}` | Hash | windowSeconds × 2 | `RateLimitAspect` | Rate Limit 토큰 버킷 |

---

## 1. 분산 락 + 펜싱 토큰 (RedisDistributedLock)

**사용처**: 주문 생성 시 상품 재고 동시성 제어, Outbox 스케줄러 단일 실행 보장

Redisson `RLock`(Lua 기반 SET NX + PX)으로 락을 획득하고, 락 획득 시 단조 증가 펜싱 토큰을 발급한다.

```
락 획득: LOCK:product:42:stock  SET NX PX 5000
토큰 발급: FENCE:product:42:stock  INCR  → token=7

→ DB의 Product.lastFenceToken(6)과 비교
→ token > lastFenceToken: 정상 처리
→ token ≤ lastFenceToken: 오래된 요청으로 거부 (StaleTokenException)
```

**Redis 재시작 시 주의**: 카운터가 리셋되면 이전 `lastFenceToken`보다 작은 토큰이 발급될 수 있다. 운영 환경에서는 AOF/RDB 영속화 또는 PostgreSQL 시퀀스 사용 권장.

---

## 2. 주문 멱등성 (IdempotencyService)

**사용처**: `POST /api/orders` 의 `Idempotency-Key` 헤더

클라이언트가 동일 UUID로 재요청 시 중복 주문 생성을 방지하고 캐시된 결과를 반환한다.

```
키: IDEMPOTENCY:{uuid}

상태 전이:
  없음         → SET NX PROCESSING (TTL 5분)  → 새 요청 처리
  PROCESSING   → 409 DuplicateRequestException (처리 중)
  COMPLETED    → 캐시된 OrderResult 즉시 반환 (TTL 24시간)
```

```json
// PROCESSING
{"status": "PROCESSING"}

// COMPLETED
{"status": "COMPLETED", "orderId": 123, "totalAmount": 50000}
```

---

## 3. Kafka Consumer 멱등성 (ConsumerIdempotencyService)

**사용처**: `StatisticsEventConsumer`, `RankingEventConsumer`, `FlashSaleOrderConsumer`

Scale-out 환경에서 동일 Kafka 이벤트를 여러 Consumer 인스턴스가 중복 처리하는 것을 방지한다.

```
키: CONSUMER:{groupId}:{orderId}:{eventType}

SET NX "PROCESSING" (TTL 10분)  →  성공: 처리 권한 획득
                                    실패: 이미 처리 중 or 완료 → skip

DB 커밋 후 → SET "PROCESSED" (TTL 25시간)
롤백/실패  → DELETE (재처리 허용)
```

---

## 4. 플래시 세일 재고 (FlashSaleService)

**사용처**: `POST /api/flash-sales/orders`

대량 동시 요청에서 DB 락 없이 Redis Lua 스크립트로 재고를 원자적으로 선점한다.

```
키: FLASH:{productId}:stock  (TTL 24시간)

Lua 스크립트 흐름:
  1. GET stock → 없으면 DB 초기 재고로 초기화
  2. stock < quantity → return -1 (재고 부족)
  3. DECRBY quantity → 선점 성공, 잔여 재고 반환

실패(Consumer 처리 실패) 시 → INCRBY quantity (보상 처리)
```

---

## 5. 실시간 랭킹 (RankingService)

**사용처**: `GET /api/rankings/realtime`

시간대별 Sorted Set을 슬라이딩 윈도우로 합산하여 인기 상품 TOP N을 반환한다. DB 쿼리 없이 O(log N) 응답.

```
시간대별 키: RANK:hourly:2026041014  (TTL 25시간)
  → 주문 발생 시: ZINCRBY productId +quantity
  → 취소 발생 시: ZINCRBY productId -quantity (음수 보정 포함)

조회 시 슬라이딩 윈도우 합산:
  RANK:window:6h:{timestamp}  (TTL 5분, 조회 후 즉시 삭제)
  최근 N시간의 hourly 키를 ZUNIONSTORE하여 TOP K 추출
```

---

## 6. Rate Limit 토큰 버킷 (RateLimitAspect)

**사용처**: `@DistributedRateLimit` 어노테이션 (현재 주문 생성에 적용)

Lua 스크립트로 토큰 버킷 알고리즘을 원자적으로 실행한다. 시각은 Redis `TIME` 명령으로 서버 단일 소스를 사용하여 Scale-out 환경에서도 정확성을 보장한다.

```
키: RATE:{key}  (Hash, TTL = windowSeconds × 2)
필드: tokens, lastRefill

Lua 흐름:
  1. redis.call('TIME') → Redis 서버 시각 기준
  2. elapsed = now - lastRefill
  3. newTokens = min(maxTokens, tokens + elapsed × refillRate)
  4. newTokens >= 1 → 허용, tokens-1 저장
  5. newTokens < 1  → 거부 (RateLimitExceededException, HTTP 429)
```

현재 적용 설정: `key = "order:{memberId}"`, `permits = 5`, `windowSeconds = 60`