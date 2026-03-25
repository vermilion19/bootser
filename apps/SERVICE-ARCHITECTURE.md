# Booster 서비스 아키텍처 및 데이터 흐름

## 1. 서비스 개요

| 서비스 | 포트 | 핵심 역할 |
|--------|------|---------|
| **Waiting Service** | 8080 | 웨이팅 등록/조회/상태 변경, Redis 실시간 순위 관리 |
| **Restaurant Service** | 6000 | 식당 정보 관리, 입장/퇴장 처리 |
| **Notification Service** | - | Kafka 이벤트 기반 Slack 알림 발송 및 로그 저장 |
| **Promotion Service** | - | 쿠폰 생성 및 발급 관리 |
| **Auth Service** | - | OAuth2 기반 회원 관리, JWT 발급 |

---

## 2. 서비스 연결 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                      Kafka Message Broker                       │
├─────────────────────────────────────────────────────────────────┤
│  waiting-events            (REGISTER, CALLED, ENTER, CANCEL)   │
│  booster.restaurant.events (CREATED, UPDATED, DELETED)         │
│  coupon.issue.request      (userId:couponId)                   │
│  member-events             (SIGNIN, SIGNUP, UPDATE, DELETE)    │
│  waiting-events.DLT        (Dead Letter Queue)                 │
└───────────┬─────────────────────────────────────────────────────┘
            │
    ┌───────▼────────┐                  ┌──────────────────┐
    │ Waiting Service│                  │  Restaurant      │
    │                │  ◄─ Kafka ──────  │  Service         │
    │ restaurant_    │  (CREATED/       │                  │
    │ snapshot (DB)  │   UPDATED/       │  Outbox → Kafka  │
    │ Redis Cache    │   DELETED)       │                  │
    └───────┬────────┘                  └────────┬─────────┘
            │                                    │
            │ waiting-events (pub)   restaurant.events (pub, Outbox)
            │                                    │
    ┌───────▼────────────────────────────────────▼─────────┐
    │                    Kafka Topics                       │
    └───────┬──────────────────────────┬───────────────────┘
            │                          │
    ┌───────▼──────────┐      ┌────────▼──────────┐
    │ Notification Svc │      │  Promotion Service │
    │  (sub: CALLED)   │      │  (sub: coupon.     │
    │                  │      │   issue.request)   │
    │  → Slack API     │      │                    │
    └──────────────────┘      └────────────────────┘

    ┌──────────────────┐
    │   Auth Service   │
    │  → member-events │
    │    (Outbox, pub) │
    └──────────────────┘

    [Bootstrap - 기동 시 1회]
    Waiting Service ──GET /restaurants/v1──► Restaurant Service
    (ApplicationReadyEvent, restaurant_snapshot 초기 적재 후 Feign 미사용)
```

---

## 3. 각 서비스 상세

### 3.1 Waiting Service

**주요 도메인**: `Waiting`
- 상태 값: `WAITING → CALLED → ENTERED`, `WAITING → CANCELED`

**REST API** (`/waitings/v1`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/waitings/v1` | 웨이팅 등록 |
| GET | `/waitings/v1/{waitingId}` | 웨이팅 상태 조회 |
| GET | `/waitings/v1/restaurants/{restaurantId}` | 대기열 목록 (커서 기반) |
| PATCH | `/waitings/v1/{waitingId}/call` | 호출 |
| PATCH | `/waitings/v1/{waitingId}/enter` | 입장 처리 |
| PATCH | `/waitings/v1/{waitingId}/cancel` | 취소 |
| PATCH | `/waitings/v1/{waitingId}/postpone` | 순서 미루기 |

**외부 의존성**

| 방향 | 대상 | 방식 | 목적 |
|------|------|------|------|
| 발행 | `waiting-events` | Kafka + ApplicationEvent | 웨이팅 생명주기 이벤트 |
| 구독 | `booster.restaurant.events` | Kafka | 식당 정보 변경 → DB + Redis 갱신 |
| 호출 | Restaurant Service | Feign (기동 시 1회만) | `restaurant_snapshot` 초기 적재 (Bootstrap) |

**핵심 패턴**
- Redis SortedSet으로 실시간 대기 순위 관리
- Self-Healing: Redis 유실 시 DB COUNT 쿼리로 복구
- Outbox Pattern: 이벤트 발행 보장
- ShedLock (Redis): Outbox 스케줄러 중복 실행 방지
- Event-Carried State Transfer: 식당 정보를 `restaurant_snapshot` 테이블에 로컬 보관, 런타임 Feign 의존 없음

---

### 3.2 Restaurant Service

**주요 도메인**: `Restaurant`
- 상태 값: `OPEN`, `CLOSED`

**REST API** (`/restaurants/v1`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/restaurants/v1` | 식당 등록 |
| GET | `/restaurants/v1/{restaurantId}` | 식당 조회 |
| GET | `/restaurants/v1` | 전체 식당 조회 |
| PATCH | `/restaurants/v1/{restaurantId}` | 정보 수정 |
| POST | `/restaurants/v1/{restaurantId}/open` | 영업 시작 |
| POST | `/restaurants/v1/{restaurantId}/close` | 영업 종료 |
| POST | `/restaurants/v1/{restaurantId}/entry` | 입장 처리 |
| POST | `/restaurants/v1/{restaurantId}/exit` | 퇴장 처리 |

**외부 의존성**

| 방향 | 대상 | 방식 | 목적 |
|------|------|------|------|
| 발행 | `booster.restaurant.events` | Kafka (Outbox) | 식당 CRUD 이벤트 발행 |
| 구독 | `waiting-events` | Kafka | 입장/취소 이벤트로 occupancy 조정 |

**이벤트 페이로드 (통합 포맷)**
```json
{ "eventType": "RESTAURANT_CREATED|UPDATED|DELETED", "id": 1, "name": "맛집" }
```

**핵심 패턴**
- Transactional Outbox: DB에 OutboxEvent 저장 후 3초마다 Kafka 발행
- 등록/수정/삭제 모두 이벤트 발행하여 Waiting Service가 식당 정보 변경을 실시간 반영

---

### 3.3 Notification Service

**주요 도메인**: `Notification` (알림 로그)
- 상태 값: `PENDING → SENT`, `PENDING → FAILED`

**Kafka 구독**

| 토픽 | 이벤트 타입 | 처리 내용 |
|------|-----------|---------|
| `waiting-events` | CALLED | Slack 알림 발송, 알림 로그 DB 저장 |
| `waiting-events.DLT` | (최종 실패) | Notification.markAsFailed() 저장 |

**외부 의존성**

| 방향 | 대상 | 방식 | 목적 |
|------|------|------|------|
| 호출 | Slack API | HTTP (비동기, Virtual Thread) | 고객 알림 메시지 전송 |

**핵심 패턴**
- `@Async` + Virtual Thread로 Slack 비동기 전송
- DLT(Dead Letter Topic) 핸들러로 최종 실패 처리

---

### 3.4 Promotion Service

**주요 도메인**: `Coupon`, `CouponIssue`

**REST API** (`/coupons/v1`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/coupons/v1` | 쿠폰 생성 |
| POST | `/coupons/v1/{couponId}/issue` | 쿠폰 발급 요청 |

**Kafka 구독**

| 토픽 | 메시지 포맷 | 처리 내용 |
|------|-----------|---------|
| `coupon.issue.request` | `userId:couponId` | 쿠폰 발급 처리, 중복 발급 방지 |

**핵심 패턴**
- Redis 기반 Outbox: Kafka 발행 실패 시 Redis 해시에 저장
- CouponOutboxWorker: 60초마다 재발행 시도

---

### 3.5 Auth Service

**주요 도메인**: `User`
- OAuth Provider: GOOGLE 등

**REST API** (`/auth/v1`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/auth/v1/login/{provider}` | OAuth2 로그인 URL 반환 |
| POST | `/auth/v1/logout` | 로그아웃 |

**Kafka 발행**

| 토픽 | 이벤트 타입 | 트리거 |
|------|-----------|-------|
| `member-events` | SIGNIN, SIGNUP, UPDATE, DELETE | 회원 생명주기 이벤트 |

**핵심 패턴**
- Transactional Outbox: MemberOutboxEvent 저장 후 3초마다 발행

---

## 4. Kafka 이벤트 토픽 전체 정리

| 토픽 | Producer | Consumer | 이벤트 타입 |
|------|----------|----------|-----------|
| `waiting-events` | Waiting Service | Restaurant Service, Notification Service | REGISTER, CALLED, ENTER, CANCEL |
| `booster.restaurant.events` | Restaurant Service | Waiting Service | RESTAURANT_CREATED, RESTAURANT_UPDATED, RESTAURANT_DELETED |
| `coupon.issue.request` | 외부 or Promotion API | Promotion Service | 쿠폰 발급 요청 |
| `member-events` | Auth Service | (다른 시스템) | SIGNIN, SIGNUP, UPDATE, DELETE |
| `waiting-events.DLT` | Kafka (자동) | Notification Service | 재시도 최종 실패 메시지 |

### 공유 이벤트 DTO (libs/core-web)

```java
// WaitingEvent
record WaitingEvent(
    Long restaurantId,
    String restaurantName,
    Long waitingId,
    String guestPhone,
    int waitingNumber,
    Long rank,
    int partySize,
    EventType type   // REGISTER, ENTER, CANCEL, CALLED
)

// MemberEvent
record MemberEvent(
    String eventId,
    Long memberId,
    String nickName,
    LocalDateTime timestamp,
    EventType eventType  // SIGNIN, SIGNUP, UPDATE, DELETE
)
```

---

## 5. 이벤트 발행 패턴 비교

| 패턴 | 사용 서비스 | 방식 | 보장 수준 |
|------|-----------|------|---------|
| **즉시 발행** | Waiting Service | `@TransactionalEventListener(AFTER_COMMIT)` → Kafka | 트랜잭션 커밋 후 발행, 실패 시 손실 가능 |
| **DB Outbox** | Restaurant, Auth Service | DB 저장 → 스케줄러(3s) → Kafka | At-least-once 보장 |
| **Redis Outbox** | Promotion Service | Redis 저장 → 스케줄러(60s) → Kafka | 빠른 재시도, Redis 유실 위험 |

---

## 6. 식당 정보 조회 전략 (Event-Carried State Transfer)

런타임 Feign 동기 호출을 제거하고, 이벤트 기반으로 식당 정보를 로컬에서 관리합니다.

**조회 우선순위 (RestaurantCacheService)**
```
1. Redis Cache  (L1, TTL 24h)
       ↓ Miss
2. restaurant_snapshot 테이블  (L2, Waiting Service 로컬 DB)
       ↓ 없음
3. Fallback: "식당 #{id}"  (Bootstrap/이벤트로 곧 채워짐)
```

**갱신 경로**
```
Restaurant Service
  등록/수정/삭제
     │
     └─ Outbox → Kafka(booster.restaurant.events)
                      │
                      └─ RestaurantEventConsumer
                            ├─ restaurant_snapshot upsert/delete (DB)
                            └─ Redis updateCache/evictCache
```

**Bootstrap (기동 시 1회)**
```
ApplicationReadyEvent
  └─ RestaurantBootstrapService
       └─ GET /restaurants/v1  (Feign, 1회성)
            └─ restaurant_snapshot 전체 적재 + Redis 워밍
```

**효과**
- Restaurant Service 장애 시 Waiting Service 정상 동작 (완전한 장애 격리)
- Redis 유실 시 로컬 DB에서 복구
- 식당명 반영 지연: Outbox polling 주기(3초) 이내

---

## 7. Spring 내부 이벤트 흐름 (Waiting Service)

```
WaitingService.register()
  └─ ApplicationEventPublisher.publishEvent(WaitingEvent)
       └─ WaitingEventListener (AFTER_COMMIT)
            └─ WaitingEventProducer.send()
                 └─ Kafka: waiting-events
```

트랜잭션 커밋 이후에 Kafka 발행하여 DB와 메시지 사이의 불일치 최소화

---

## 8. 핵심 시나리오 - 웨이팅 등록부터 입장까지

```
1. [Client] POST /waitings/v1  →  Waiting Service
   ├─ RestaurantCacheService.getRestaurantName()
   │    └─ Redis → restaurant_snapshot DB → Fallback  (Feign 없음)
   ├─ Waiting.create() + DB 저장
   ├─ Redis SortedSet에 순위 등록
   └─ ApplicationEvent 발행 (REGISTER)

2. [Waiting Service] AFTER_COMMIT
   └─ Kafka waiting-events (REGISTER) 발행

3. [점주] PATCH /waitings/v1/{id}/call  →  Waiting Service
   ├─ waiting.call() (WAITING → CALLED)
   └─ Kafka waiting-events (CALLED) 발행

4. [Notification Service] CALLED 이벤트 수신
   ├─ Notification 로그 저장 (PENDING)
   ├─ SlackClient.sendMessage() 비동기 전송  (@Async)
   └─ Notification.markAsSent() 또는 markAsFailed()

5. [점주] POST /restaurants/v1/{id}/entry  →  Restaurant Service
   ├─ Restaurant.currentOccupancy 증가
   └─ Kafka booster.restaurant.events (RESTAURANT_UPDATED) 발행 (Outbox)

6. [Waiting Service] ENTER 이벤트 처리
   ├─ waiting.enter() (CALLED → ENTERED)
   └─ Outbox 저장 → 3초 후 Kafka 재발행
```

---

## 9. 스케줄러 목록

| 서비스 | 컴포넌트 | 주기 | 목적 | 분산 락 |
|--------|---------|------|------|--------|
| Waiting Service | OutboxMessageRelay | 3s | Outbox 이벤트 Kafka 발행 | ShedLock (Redis) |
| Restaurant Service | OutboxMessageRelay | 3s | Outbox 이벤트 Kafka 발행 | - |
| Promotion Service | CouponOutboxWorker | 60s | Redis Outbox 재발행 | - |
| Auth Service | MemberOutboxRelay | 3s | 회원 이벤트 Kafka 발행 | - |

---

## 10. 데이터베이스 테이블 요약

### waiting-service
| 테이블 | 주요 컬럼 |
|--------|---------|
| `waiting` | id, restaurant_id, guest_phone, waiting_number, status, created_at |
| `outbox_events` | id, aggregate_type, aggregate_id, event_type, payload, published |
| `restaurant_snapshot` | id (=restaurantId), name, created_at, updated_at |

### restaurant-service
| 테이블 | 주요 컬럼 |
|--------|---------|
| `restaurant` | id, name, capacity, current_occupancy, max_waiting_limit, status |
| `outbox_events` | id, aggregate_type, aggregate_id, event_type, payload, published |

### notification-service
| 테이블 | 주요 컬럼 |
|--------|---------|
| `notification_logs` | id, restaurant_id, waiting_id, target, message, status, sent_at |

### promotion-service
| 테이블 | 주요 컬럼 |
|--------|---------|
| `coupon` | id, title, total_quantity, issued_quantity, discount_amount, start_at, end_at |
| `coupon_issue` | id, coupon_id, user_id (UK) |

### auth-service
| 테이블 | 주요 컬럼 |
|--------|---------|
| `users` | id, email, name, oauth_provider, oauth_id (UK), role |
| `member_outbox_event` | id, aggregate_id, event_type, payload, published |

---

## 11. 주의사항 및 트러블슈팅

### Self-Invocation 주의
AOP 어노테이션(`@Cacheable`, `@Transactional`, `@Async`)이 붙은 메서드를 같은 클래스 내에서 호출하면 프록시가 동작하지 않아 AOP가 무시됨. 반드시 다른 Bean에서 호출해야 함.

### Redis 유실 대비 (Waiting Service)
- **대기 순위**: Redis 없으면 DB COUNT 쿼리로 재계산 후 Redis에 복구 (Self-Healing)
- **식당 정보**: Redis 없으면 `restaurant_snapshot` 테이블에서 조회 후 Redis 재적재

### DLQ 처리 (Notification Service)
Kafka 재시도 후 최종 실패한 메시지는 `waiting-events.DLT` 토픽으로 자동 라우팅되며, `DltEventListener`가 `Notification.markAsFailed()`로 상태를 기록함.

### 쿠폰 중복 발급 방지 (Promotion Service)
`coupon_issue` 테이블의 `(coupon_id, user_id)` 유니크 제약으로 동일 사용자의 동일 쿠폰 중복 발급 방지.