# query-burst Kafka 사용 현황

---

## 토픽 구조

| 토픽 | Producer | Consumer | 목적 |
|------|----------|----------|------|
| `order-events` | `OutboxMessageRelay` | `StatisticsEventConsumer`, `RankingEventConsumer` | 주문 이벤트 브로드캐스트 |
| `flash-sale-orders` | `FlashSaleService` | `FlashSaleOrderConsumer` | 플래시 세일 주문 비동기 처리 |
| `abuse-detection` | `RateLimitAspect` | (없음) | Rate Limit 초과 감지 알림 |

---

## 1. order-events

### Producer — OutboxMessageRelay

Outbox 패턴 기반. DB 트랜잭션과 Kafka 발행을 분리하여 이벤트 유실을 방지한다.

```
주문 생성/취소/상태변경
  → DB OutboxEvent 저장 (트랜잭션 내)
  → OutboxMessageRelay 스케줄러 (3초마다)
      → PENDING → SENDING → PUBLISHED / FAILED
  → order-events 토픽 발행
```

발행 이벤트 타입:
- `ORDER_CREATED`
- `ORDER_CANCELED`
- `ORDER_STATUS_CHANGED`

### Consumer 1 — StatisticsEventConsumer (statistics-consumer-group)

주문 이벤트를 받아 **CQRS 통계 테이블**을 비동기로 갱신한다.

| 이벤트 | 처리 내용 |
|--------|-----------|
| `ORDER_CREATED` | `daily_sales_summary` (카테고리별 매출) + `product_daily_sales` (상품별 매출) UPSERT |
| `ORDER_CANCELED` | 위 두 테이블에서 해당 금액/수량 차감 |

트랜잭션 커밋 후 `markProcessed()` 호출 (`TransactionSynchronizationManager.afterCommit()` 훅).

### Consumer 2 — RankingEventConsumer (ranking-consumer-group)

주문 이벤트를 받아 **Redis 실시간 랭킹** 점수를 갱신한다.

| 이벤트 | 처리 내용 |
|--------|-----------|
| `ORDER_CREATED` | Redis Sorted Set에 상품별 판매량 증가 (`ZINCRBY +quantity`) |
| `ORDER_CANCELED` | Redis Sorted Set에 상품별 판매량 감소 (`ZINCRBY -quantity`) |

---

## 2. flash-sale-orders

### Producer — FlashSaleService

플래시 세일 요청을 **Redis에서 선점 후 즉시 응답**, 실제 DB 주문 생성은 Kafka를 통해 비동기 처리.

```
POST /api/flash-sales/orders
  → Redis Lua 스크립트로 재고 원자적 선점
  → flash-sale-orders 토픽 발행 (즉시 202 Accepted 반환)
```

### Consumer — FlashSaleOrderConsumer (flash-sale-consumer-group)

```
flash-sale-orders 소비
  → FlashSaleService.processOrder() → DB 주문 생성
  → 실패 시 compensateStock() → Redis 재고 원상복구
```

---

## 3. abuse-detection

### Producer — RateLimitAspect

Rate Limit 초과 시 `abuse-detection` 토픽으로 이벤트 발행. 현재 query-burst 내 Consumer 없음 (외부 모니터링 시스템 연동 목적).

```json
{
  "key": "order:1",
  "permits": 5,
  "windowSeconds": 60,
  "timestamp": 1712000000000
}
```

---

## 멱등성 처리 (모든 Consumer 공통)

`ConsumerIdempotencyService`를 통해 중복 처리를 방지한다.

```
PROCESSING/PROCESSED 2단계 상태 머신 (Redis SET NX 선점)

tryStartProcessing()    → SET NX "PROCESSING" (TTL 10분)
                          성공: 처리 권한 획득
                          실패: skip (이미 처리 중 or 완료)

처리 완료              → markProcessed()   SET "PROCESSED" (TTL 25시간)
처리 실패/롤백         → clearProcessing() 키 삭제 (재처리 허용)
```