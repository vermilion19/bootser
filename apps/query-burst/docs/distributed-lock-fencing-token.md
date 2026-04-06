# 분산 락 + 펜싱 토큰 구현 가이드

## 1. 배경 및 문제 정의

### 왜 분산 락이 필요한가

다중 서버 환경에서 동일 상품에 대한 주문이 동시에 들어오면 재고 차감 로직에 **Race Condition**이 발생한다.

```
서버 A: stock=1 읽음 → 차감 계산 → stock=0 저장
서버 B: stock=1 읽음 → 차감 계산 → stock=0 저장  ← 중복 차감!
```

단순 분산 락만으로는 부족하다. **GC pause** 또는 **네트워크 지연**으로 락이 만료된 후에도 오래된 요청이 DB에 도달할 수 있기 때문이다.

### 펜싱 토큰이 해결하는 문제

```
서버 A: 락 획득(token=42) → GC pause → 락 만료
서버 B: 락 획득(token=43) → 재고 차감 완료, DB에 last_fence_token=43 저장
서버 A: GC 복귀 → token=42로 재고 차감 시도 → 42 ≤ 43 → 거부!
```

DB에 저장된 `last_fence_token`과 비교하여 **오래된 요청을 DB 레벨에서 차단**한다.

---

## 2. 아키텍처

### 전체 흐름

```
POST /api/orders
  │
  ├─ [멱등성 검사] Idempotency-Key 헤더 확인
  │    ├─ COMPLETED → 캐시 결과 즉시 반환
  │    └─ PROCESSING → 409 Conflict
  │
  ├─ [분산 락] Redis RLock 획득 (productId 오름차순)
  │    └─ Redis 장애 → DB 비관적 락(SELECT FOR UPDATE) Fallback
  │
  ├─ [펜싱 토큰] RAtomicLong INCR → 단조 증가 토큰 발급
  │
  ├─ [트랜잭션] 펜싱 토큰 검증 → 재고 차감 → 주문 저장
  │
  └─ [멱등성 완료] COMPLETED 마킹
```

### 패키지 구조

```
apps/query-burst/src/main/java/com/booster/queryburst/
│
├── lock/
│   ├── DistributedLock.java           # 분산 락 인터페이스
│   ├── FencingToken.java              # 펜싱 토큰 VO
│   ├── RedisDistributedLock.java      # Redisson 구현체 (메트릭 포함)
│   ├── LockAcquisitionException.java  # 락 획득 실패
│   └── RedisUnavailableException.java # Redis 연결 장애
│
├── order/
│   ├── application/
│   │   ├── OrderFacade.java           # 락 경계 + 멱등성 + fallback 조율
│   │   ├── OrderService.java          # 트랜잭션: 펜싱 검증 + 재고 차감
│   │   ├── IdempotencyService.java    # 멱등성 키 관리 (Redis)
│   │   ├── IdempotencyCheck.java      # sealed interface (Proceed/AlreadyCompleted/AlreadyProcessing)
│   │   └── dto/
│   │       ├── OrderCreateCommand.java   # fencingTokens: Map<Long, Long>
│   │       ├── OrderItemCommand.java
│   │       └── OrderResult.java
│   ├── exception/
│   │   └── DuplicateRequestException.java
│   └── web/
│       ├── OrderController.java
│       ├── advice/
│       │   └── OrderExceptionHandler.java
│       └── dto/...
│
└── product/
    └── domain/
        ├── Product.java               # last_fence_token 필드, decreaseStock(int, long)
        ├── ProductRepository.java     # findByIdWithLock (PESSIMISTIC_WRITE)
        └── StaleTokenException.java
```

---

## 3. 핵심 컴포넌트 상세

### 3.1 FencingToken

```java
public record FencingToken(long value) {
    public boolean isNewerThan(long lastAppliedToken) {
        return this.value > lastAppliedToken;
    }
}
```

### 3.2 DistributedLock 인터페이스

```java
public interface DistributedLock {
    FencingToken tryLock(String key, Duration ttl);  // 락 획득 + 토큰 발급
    void unlock(String key, FencingToken token);
}
```

### 3.3 Redis 키 구조

| 키 | 용도 | TTL |
|----|------|-----|
| `LOCK:product:{id}:stock` | 분산 락 보유 여부 | leaseTime (5초) |
| `FENCE:product:{id}:stock` | 단조 증가 펜싱 카운터 | 30일 |
| `IDEMPOTENCY:{key}` | 멱등성 상태 저장 | 처리 중 5분 / 완료 24시간 |

> **카운터와 락을 분리하는 이유**: 락이 만료되어도 카운터 값은 유지되어야 다음 락 획득 시 단조 증가가 보장된다.

### 3.4 Product 도메인 변경

```java
// product 테이블에 추가된 컬럼
@Column(nullable = false)
private long lastFenceToken = 0L;

// 분산 락 경로: 펜싱 토큰 검증 후 재고 차감
public void decreaseStock(int quantity, long fenceToken) {
    if (fenceToken <= this.lastFenceToken) {
        throw new StaleTokenException(...);
    }
    // 재고 차감 + lastFenceToken 갱신
}

// DB 비관적 락 Fallback 경로: 토큰 검증 생략
public void decreaseStockFallback(int quantity) {
    // SELECT FOR UPDATE가 동시성 보장하므로 토큰 불필요
}
```

---

## 4. 멱등성 처리

### 동작 방식

```
클라이언트 → POST /api/orders
             Idempotency-Key: {UUID}
```

| 상태 | 동작 | HTTP |
|------|------|------|
| 키 없음 | `PROCESSING` 마킹 후 정상 처리 | 201 |
| `COMPLETED` | 캐시된 결과 즉시 반환 | 201 |
| `PROCESSING` | DuplicateRequestException | 409 |
| 헤더 미전송 | 멱등성 검사 생략 | 201 |

### Redis 저장 포맷

```json
// 처리 중 (TTL: 5분)
{"status": "PROCESSING"}

// 완료 (TTL: 24시간)
{"status": "COMPLETED", "orderId": 123456789, "totalAmount": 50000}
```

> `Idempotency-Key` 헤더는 선택 사항이다. 미전송 시 멱등성 검사 없이 동작한다.

---

## 5. Redis 장애 Fallback

### 전환 조건

`RedisDistributedLock.tryLock()`에서 Redis 연결 관련 예외 발생 시 `RedisUnavailableException`으로 래핑하여 전파한다.

### Fallback 경로

```
OrderFacade.placeOrder()
  └─ RedisUnavailableException 감지
       └─ OrderService.createOrderWithPessimisticLock()
            └─ ProductRepository.findByIdWithLock()  ← SELECT ... FOR UPDATE
                 └─ Product.decreaseStockFallback()   ← 토큰 검증 없음
```

### 동시성 보장 비교

| 방식 | 동시성 보장 수단 | fencing token |
|------|----------------|---------------|
| 분산 락 경로 | Redis RLock + DB 토큰 검증 | 필요 |
| DB Fallback 경로 | SELECT FOR UPDATE (행 레벨 잠금) | 불필요 |

> `lastFenceToken`은 Fallback 시 갱신하지 않는다. Redis 복구 후 Redis 경로로 돌아와도 카운터가 단조 증가하므로 기존 토큰값보다 항상 크다.

---

## 6. 메트릭

Prometheus에서 수집 가능한 커스텀 카운터 목록.

| 메트릭 | 태그 | 설명 |
|--------|------|------|
| `distributed_lock_acquired_total` | `key=product:*:stock` | 락 획득 성공 |
| `distributed_lock_failed_total` | `key=product:*:stock` | 락 획득 실패 (경합, 타임아웃) |
| `distributed_lock_redis_error_total` | `key=product:*:stock` | Redis 연결 오류 |
| `order_redis_fallback_total` | — | DB Fallback 전환 횟수 |

> 태그의 숫자 ID는 `product:*:stock`으로 정규화하여 **카디널리티 폭발**을 방지한다.

---

## 7. 예외 처리 (HTTP 상태 코드)

| 예외 | 상태 코드 | 원인 |
|------|-----------|------|
| `LockAcquisitionException` | 429 Too Many Requests | 락 경합 — 잠시 후 재시도 |
| `StaleTokenException` | 409 Conflict | 펜싱 토큰 실패 — 처음부터 재요청 |
| `DuplicateRequestException` | 409 Conflict | 동일 Idempotency-Key 처리 중 |
| `IllegalStateException` | 409 Conflict | 재고 부족 등 비즈니스 규칙 위반 |
| `IllegalArgumentException` | 404 Not Found | 존재하지 않는 회원/상품 |

---

## 8. DB 마이그레이션

```sql
-- V2: product 테이블에 펜싱 토큰 컬럼 추가
ALTER TABLE product
    ADD COLUMN last_fence_token BIGINT NOT NULL DEFAULT 0;
```

---

## 9. 다중 상품 주문 시 데드락 방지

여러 상품에 대해 락을 순차 획득할 때, 서버마다 획득 순서가 다르면 데드락이 발생한다.

```
서버 A: product:1 락 대기 → product:2 락 대기
서버 B: product:2 락 대기 → product:1 락 대기  ← 교착
```

**해결**: 모든 서버가 `productId 오름차순`으로 락을 획득한다.

```java
List<Long> productIds = request.items().stream()
        .map(OrderItemRequest::productId)
        .sorted()       // 오름차순 정렬
        .distinct()
        .toList();
```

락 해제는 역순(내림차순)으로 수행한다.

---

## 10. 운영 고려사항

### Redis 재시작 시 카운터 리셋

Redis가 재시작되면 `FENCE:*` 카운터가 0으로 초기화된다. DB의 `last_fence_token`이 이미 양수이면 새로 발급되는 token=1이 거부될 수 있다.

**운영 대응**:
- Redis AOF/RDB 영속화 활성화
- 또는 DB 시퀀스(`PostgreSQL SEQUENCE`)로 카운터를 교체

### 락 TTL vs 트랜잭션 시간

**락 TTL ≥ 트랜잭션 처리 시간** 을 반드시 보장해야 한다. TTL이 짧으면 트랜잭션 진행 중 락이 만료되고 다른 서버가 동일 락을 획득할 수 있다. 이때 펜싱 토큰이 마지막 방어선 역할을 한다.
