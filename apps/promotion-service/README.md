# Promotion Service - 선착순 쿠폰 발급 시스템

## 개요

대용량 트래픽 환경에서 **정확히 N개만 발급**되는 선착순 쿠폰 시스템.
Redis Lua Script를 활용한 원자적 연산으로 **재고 초과 발급**과 **중복 발급**을 방지합니다.

### 핵심 요구사항

| 요구사항 | 설명 |
|----------|------|
| 재고 초과 방지 | 10,000개 한정 쿠폰은 정확히 10,000개만 발급 |
| 중복 발급 방지 | 1인 1매 원칙 (같은 사용자가 여러 번 받기 불가) |
| 고성능 | 10만 명 동시 요청에도 안정적으로 처리 |

---

## 아키텍처

### 패키지 구조 (DDD)

```
coupon/
├── domain/                     # 도메인 레이어
│   ├── CouponPolicy.java       # 쿠폰 정책 (Aggregate Root)
│   ├── CouponPolicyRepository.java
│   ├── IssuedCoupon.java       # 발급된 쿠폰
│   ├── IssuedCouponRepository.java
│   ├── DiscountType.java       # 할인 타입 (FIXED, PERCENTAGE)
│   ├── CouponStatus.java       # 쿠폰 상태 (UNUSED, USED, EXPIRED)
│   └── PolicyStatus.java       # 정책 상태 (PENDING, ACTIVE, EXHAUSTED, ENDED)
│
├── application/                # 응용 레이어
│   ├── CouponIssueService.java # 쿠폰 발급 핵심 로직 ⭐
│   └── dto/
│       └── IssueCouponCommand.java
│
├── web/                        # 표현 레이어
│   ├── controller/
│   │   └── CouponController.java
│   ├── dto/request/
│   │   └── IssueCouponRequest.java
│   ├── dto/response/
│   │   ├── CouponIssueResponse.java
│   │   └── CouponStockResponse.java
│   └── advice/
│       └── CouponExceptionHandler.java
│
└── exception/                  # 도메인 예외
    ├── CouponErrorCode.java
    ├── CouponSoldOutException.java
    ├── AlreadyIssuedCouponException.java
    ├── CouponNotFoundException.java
    └── CouponPolicyNotFoundException.java
```

### 전체 흐름

```
┌─────────┐     ┌─────────────┐     ┌─────────────────┐
│  Client │────▶│  Controller │────▶│ CouponIssueService│
└─────────┘     └─────────────┘     └────────┬────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
               ┌────▼────┐             ┌─────▼─────┐            ┌─────▼─────┐
               │  Redis  │             │    JPA    │            │  (Kafka)  │
               │ Lua Script│            │ Repository│            │  미구현   │
               │ 재고/중복 │            │ 발급이력   │            │          │
               └─────────┘             └───────────┘            └───────────┘
```

---

## Redis Lua Script 적용 부분

### 위치
`coupon/application/CouponIssueService.java:30-47`

### 코드

```java
private static final String ISSUE_COUPON_SCRIPT = """
        -- KEYS[1]: 재고 키 (coupon:stock:{policyId})
        -- KEYS[2]: 발급자 Set (coupon:issued:{policyId})
        -- ARGV[1]: 사용자 ID

        -- 1. 중복 발급 확인
        local alreadyIssued = redis.call('SISMEMBER', KEYS[2], ARGV[1])
        if alreadyIssued == 1 then
            return -2  -- 이미 발급됨
        end

        -- 2. 재고 확인
        local stock = tonumber(redis.call('GET', KEYS[1]))
        if stock == nil or stock <= 0 then
            return -1  -- 재고 없음
        end

        -- 3. 원자적으로 재고 차감 + 발급자 기록
        redis.call('DECR', KEYS[1])
        redis.call('SADD', KEYS[2], ARGV[1])

        return 1  -- 발급 성공
        """;
```

### 왜 Lua Script인가?

| 문제 | 일반 방식 | Lua Script |
|------|----------|------------|
| Race Condition | 재고 확인 → 차감 사이에 다른 요청 끼어듦 | 단일 명령으로 원자적 실행 |
| 네트워크 왕복 | 3번 (SISMEMBER → GET → DECR/SADD) | 1번 (스크립트 전체가 한 번에) |
| 동시성 보장 | 별도 분산락 필요 | Redis 싱글스레드 특성으로 자동 보장 |

### Redis 키 구조

| 키 패턴 | 타입 | 설명 |
|---------|------|------|
| `coupon:stock:{policyId}` | String | 잔여 재고 수량 |
| `coupon:issued:{policyId}` | Set | 발급받은 사용자 ID 목록 |

---

## REST API

### 쿠폰 발급

```http
POST /api/v1/coupons/policies/{policyId}/issue
Content-Type: application/json

{
    "userId": 12345
}
```

**Response (성공)**
```json
{
    "couponId": 1234567890123456789,
    "couponPolicyId": 100,
    "userId": 12345,
    "status": "UNUSED",
    "issuedAt": "2025-01-14T10:30:00",
    "expireAt": "2025-02-14T23:59:59"
}
```

**Response (실패 - 소진)**
```json
{
    "result": "ERROR",
    "errorCode": "CP003",
    "message": "쿠폰이 모두 소진되었습니다."
}
```

### 잔여 재고 조회

```http
GET /api/v1/coupons/policies/{policyId}/stock
```

**Response**
```json
{
    "couponPolicyId": 100,
    "remainingStock": 5000,
    "available": true
}
```

### 발급 여부 확인

```http
GET /api/v1/coupons/policies/{policyId}/issued?userId=12345
```

**Response**
```json
true
```

---

## Entity 설계

### CouponPolicy (쿠폰 정책)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | Snowflake ID |
| name | String | 쿠폰명 |
| description | String | 설명 |
| discountType | Enum | FIXED(정액), PERCENTAGE(정률) |
| discountValue | Integer | 할인 금액 또는 비율 |
| totalQuantity | Integer | 총 발급 가능 수량 |
| issuedQuantity | Integer | 현재까지 발급된 수량 |
| startAt | LocalDateTime | 발급 시작 시간 |
| endAt | LocalDateTime | 발급 종료 시간 |
| expireAt | LocalDateTime | 쿠폰 만료 시간 |
| status | Enum | PENDING, ACTIVE, EXHAUSTED, ENDED |

### IssuedCoupon (발급된 쿠폰)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | Snowflake ID |
| couponPolicyId | Long | 쿠폰 정책 ID (FK) |
| userId | Long | 발급받은 사용자 ID |
| status | Enum | UNUSED, USED, EXPIRED |
| issuedAt | LocalDateTime | 발급 시각 |
| usedAt | LocalDateTime | 사용 시각 (nullable) |
| expireAt | LocalDateTime | 만료 시각 |

**인덱스**
- `idx_issued_coupon_user_policy`: (userId, couponPolicyId) - 중복 발급 체크
- `idx_issued_coupon_policy`: (couponPolicyId) - 정책별 조회
- `uk_user_policy`: UNIQUE (userId, couponPolicyId) - DB 레벨 중복 방지

---

## 추후 확장 포인트

### 1. Kafka 비동기 처리 (우선순위: 높음)

현재는 Redis 발급 성공 후 **동기적으로 DB 저장**합니다.
Kafka를 도입하면 DB 병목을 해소할 수 있습니다.

```
현재: Redis 발급 → DB 저장 (동기)
개선: Redis 발급 → Kafka 발행 → Consumer가 DB 저장 (비동기)
```

**구현 위치**
- `coupon/event/CouponIssuedEventProducer.java` (신규)
- `coupon/event/CouponIssuedEventConsumer.java` (신규)

### 2. 쿠폰 정책 관리 API (우선순위: 높음)

관리자가 쿠폰 정책을 생성/수정/활성화할 수 있는 API 필요.

```http
POST   /api/v1/admin/coupon-policies          # 정책 생성
GET    /api/v1/admin/coupon-policies/{id}     # 정책 조회
PUT    /api/v1/admin/coupon-policies/{id}     # 정책 수정
POST   /api/v1/admin/coupon-policies/{id}/activate  # 정책 활성화
```

**구현 위치**
- `coupon/application/CouponPolicyService.java` (신규)
- `coupon/web/controller/CouponPolicyAdminController.java` (신규)

### 3. 쿠폰 사용 기능 (우선순위: 중간)

주문 서비스에서 쿠폰을 사용할 때 호출하는 API.

```http
POST /api/v1/coupons/{couponId}/use
```

**구현 위치**
- `coupon/application/CouponUseService.java` (신규)
- `IssuedCoupon.use()` 메서드 (이미 구현됨)

### 4. Redis 장애 대응 (우선순위: 중간)

Redis가 죽었을 때 DB Fallback 전략.

```java
public IssuedCoupon issueWithFallback(IssueCouponCommand command) {
    try {
        return issueWithRedis(command);  // 기본: Redis
    } catch (RedisConnectionFailureException e) {
        return issueWithDbLock(command); // Fallback: DB 비관적 락
    }
}
```

**구현 위치**
- `coupon/application/CouponIssueService.java` 내 Fallback 로직 추가

### 5. 동시성 테스트 (우선순위: 높음)

10,000명이 동시에 100개 쿠폰을 요청하는 테스트.

```java
@Test
void 동시에_10000명이_100개_쿠폰_요청시_정확히_100개만_발급된다() {
    // given
    int couponCount = 100;
    int threadCount = 10000;

    // when
    ExecutorService executor = Executors.newFixedThreadPool(100);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                couponIssueService.issue(command);
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    // then
    assertThat(issuedCouponRepository.count()).isEqualTo(100);
}
```

**구현 위치**
- `src/test/java/.../CouponIssueConcurrencyTest.java` (신규)

### 6. 스케줄러 기능 (우선순위: 낮음)

- 정책 자동 활성화: `startAt` 도달 시 PENDING → ACTIVE
- 정책 자동 종료: `endAt` 도달 시 ACTIVE → ENDED
- 쿠폰 만료 처리: `expireAt` 도달 시 UNUSED → EXPIRED

**구현 위치**
- `coupon/application/CouponScheduler.java` (신규)

---

## 실행 방법

### 사전 조건
- Redis 실행 중 (`localhost:6379`)
- H2 DB (기본 내장)

### 서비스 실행

```bash
./gradlew :apps:promotion-service:bootRun
```

### 쿠폰 재고 초기화 (Redis)

서비스 시작 후 쿠폰 정책의 재고를 Redis에 설정해야 합니다.

```java
// CouponIssueService.initializeCouponStock(policyId, quantity)
couponIssueService.initializeCouponStock(100L, 10000);
```

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Framework | Spring Boot 4.0.1 |
| Database | H2 (개발), PostgreSQL (운영) |
| Cache | Redis (Redisson) |
| ORM | Spring Data JPA |
| ID 생성 | Snowflake Algorithm |
