# query-burst 아키텍처 문서

> 대용량 트래픽 대응을 목표로 설계된 커머스 플랫폼.  
> 분산 락, 이벤트 기반 아키텍처, CQRS, 실시간 랭킹 등 고가용성 패턴을 실습한다.

---

## 목차

1. [기술 스택](#기술-스택)
2. [전체 패키지 구조](#전체-패키지-구조)
3. [도메인 개요](#도메인-개요)
4. [데이터 모델 & 인덱스 전략](#데이터-모델--인덱스-전략)
5. [API 엔드포인트](#api-엔드포인트)
6. [핵심 아키텍처 패턴](#핵심-아키텍처-패턴)
   - [분산 락 + 펜싱 토큰](#1-분산-락--펜싱-토큰)
   - [멱등성 처리](#2-멱등성-처리)
   - [Outbox Pattern](#3-outbox-pattern)
   - [실시간 랭킹 (Redis Sorted Set)](#4-실시간-랭킹-redis-sorted-set)
   - [CQRS 통계](#5-cqrs-통계)
7. [이벤트 파이프라인 전체 흐름](#이벤트-파이프라인-전체-흐름)
8. [페이지네이션 전략 비교](#페이지네이션-전략-비교)
9. [면접 스토리라인](#면접-스토리라인)

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.1 |
| Database | PostgreSQL (운영), H2 (테스트) |
| Cache / Lock | Redis (Redisson) |
| Message Broker | Apache Kafka |
| ORM | Spring Data JPA + QueryDSL 5.0 |
| Observability | Prometheus, Grafana, Loki, Tempo |

---

## 전체 패키지 구조

```
com.booster.queryburst/
│
├── member/                      # 회원 도메인
│   ├── domain/                  # Member, MemberGrade, MemberRepository, MemberQueryRepository
│   ├── application/             # MemberService, dto/
│   └── web/                     # MemberController, dto/request, dto/response
│
├── product/                     # 상품/카테고리 도메인
│   ├── domain/                  # Product, Category, Repository, QueryRepository
│   ├── application/             # ProductService, CategoryService, dto/
│   └── web/                     # ProductController, CategoryController, dto/
│
├── order/                       # 주문 도메인 (핵심)
│   ├── domain/
│   │   ├── Orders, OrderItem, OrderStatus
│   │   ├── OrderRepository, OrderItemRepository
│   │   ├── OrderQueryRepository, OrderItemQueryRepository
│   │   └── outbox/              # OutboxEvent, OutboxEventRepository, OutboxStatus
│   ├── application/
│   │   ├── OrderService         # 비즈니스 로직 + Outbox 저장
│   │   ├── OrderFacade          # 분산락 + 멱등성 + Fallback 조율
│   │   ├── IdempotencyService   # Redis 기반 멱등성
│   │   └── dto/
│   ├── event/
│   │   ├── OrderEventPayload    # Kafka 메시지 포맷
│   │   └── OutboxMessageRelay   # 폴링 발행자 (3초 스케줄)
│   ├── web/                     # OrderController, dto/
│   └── exception/               # DuplicateRequestException
│
├── ranking/                     # 실시간 랭킹 도메인 (신규)
│   ├── application/             # RankingService (Redis Sorted Set)
│   ├── event/                   # RankingEventConsumer (Kafka)
│   └── web/                     # RankingController
│
├── statistics/                  # CQRS 통계 도메인 (신규)
│   ├── domain/                  # DailySalesSummary, ProductDailySales, Repository
│   ├── event/                   # StatisticsEventConsumer (Kafka)
│   ├── application/             # StatisticsQueryService
│   └── web/                     # StatisticsController
│
├── lock/                        # 분산 락 인프라
│   ├── DistributedLock          # 인터페이스
│   ├── RedisDistributedLock     # Redisson 구현체
│   ├── FencingToken             # 단조 증가 토큰
│   ├── LockAcquisitionException
│   └── RedisUnavailableException
│
└── config/
    ├── SchedulingConfig         # @EnableScheduling
    ├── DataInitializer          # 대량 더미 데이터 적재
    └── DataInitController       # POST /api/data-init
```

---

## 도메인 개요

### Member (회원)
- 목표 데이터: **1,000만 건**
- 등급: `BRONZE`, `SILVER`, `GOLD`, `PLATINUM`, `DIAMOND`
- 역할: 상품 판매자(seller) 및 주문자(buyer) 겸용

### Category (카테고리)
- 목표 데이터: **~1,000건** (마스터 데이터)
- 계층 구조: 최대 **3단계** (대분류 → 중분류 → 소분류)
- 셀프 조인: `parent_id` 컬럼으로 계층 표현

### Product (상품)
- 목표 데이터: **100만 건**
- 상태: `ACTIVE`(70%), `INACTIVE`(20%), `SOLD_OUT`(10%)
- 재고 차감 시 **펜싱 토큰** 검증으로 오래된 락 보유자 차단

### Orders / OrderItem (주문)
- 목표 데이터: **3,000만 건** (주문) / **1억 건+** (주문 항목)
- `unitPrice` 스냅샷: 주문 시점 가격 보존 (이후 가격 변동 무관)
- 상태 머신: `PENDING → PAID → SHIPPED → DELIVERED` / `→ CANCELED`

---

## 데이터 모델 & 인덱스 전략

### orders 테이블

| 인덱스 | 컬럼 | 용도 |
|--------|------|------|
| `idx_orders_member_id` | `member_id` | 회원별 주문 조회 (FK) |
| `idx_orders_status` | `status` | 상태 필터 |
| `idx_orders_ordered_at` | `ordered_at` | 기간 조회 |
| `idx_orders_member_ordered_at` | `member_id, ordered_at` | **핵심 복합 인덱스**: 회원별 최근 주문 |
| `idx_orders_status_ordered_at` | `status, ordered_at` | 관리자 대시보드용 |

### order_item 테이블

| 인덱스 | 컬럼 | 용도 |
|--------|------|------|
| `idx_order_item_order_id` | `order_id` | 주문별 항목 조회 (FK) |
| `idx_order_item_product_id` | `product_id` | 상품별 판매 내역 |
| `idx_order_item_covering` | `product_id, quantity, unit_price` | **커버링 인덱스**: 집계 시 테이블 접근 없음 |

### product 테이블

| 인덱스 | 컬럼 | 용도 |
|--------|------|------|
| `idx_product_category_status_price` | `category_id, status, price` | **핵심 복합 인덱스**: 카테고리 내 필터 검색 |
| `idx_product_seller_id` | `seller_id` | 판매자별 조회 |
| `idx_product_status` | `status` | 상태 필터 (카디널리티 낮음 → 부분 인덱스 권장) |

### outbox_event 테이블 (신규)

| 인덱스 | 컬럼 | 용도 |
|--------|------|------|
| `idx_outbox_status_created` | `status, created_at` | PENDING 이벤트 폴링 조회 |

### daily_sales_summary 테이블 (신규, CQRS)

| 컬럼 | 설명 |
|------|------|
| `date` | 집계 날짜 |
| `category_id` | 카테고리 ID |
| `total_amount` | 해당 날짜 카테고리 누적 매출 |
| `order_count` | 해당 날짜 카테고리 주문 건수 |

> UNIQUE 인덱스: `(date, category_id)` — UPSERT 기준

### product_daily_sales 테이블 (신규, CQRS)

| 컬럼 | 설명 |
|------|------|
| `date` | 집계 날짜 |
| `product_id` | 상품 ID |
| `sold_count` | 해당 날짜 누적 판매 수량 |
| `revenue` | 해당 날짜 누적 매출 |

> UNIQUE 인덱스: `(date, product_id)` — UPSERT 기준  
> 복합 인덱스: `(date, sold_count)` — 날짜별 판매량 순위 조회

---

## API 엔드포인트

### Member — `/api/members`

| Method | Path | 설명 | 특이사항 |
|--------|------|------|----------|
| `GET` | `/api/members` | 회원 목록 (페이지) | COUNT 쿼리 포함 |
| `GET` | `/api/members/v2` | 회원 목록 (Slice) | COUNT 없음, OFFSET 방식 |
| `GET` | `/api/members/v3` | 회원 목록 (커서) | OFFSET 없음, 커서 기반 |
| `POST` | `/api/members` | 회원 생성 | |
| `PATCH` | `/api/members/{id}` | 회원 수정 | |
| `DELETE` | `/api/members/{id}` | 회원 삭제 | |

### Category — `/api/categories`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/categories` | 카테고리 목록 (페이지) |
| `GET` | `/api/categories/v2` | 카테고리 목록 (커서) |
| `POST` | `/api/categories` | 카테고리 생성 (`parentId` null = 대분류) |
| `DELETE` | `/api/categories/{id}` | 카테고리 삭제 |

### Product — `/api/products`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/products` | 상품 목록 (커서 + 필터) |
| `POST` | `/api/products` | 상품 생성 |
| `PATCH` | `/api/products/{id}/price` | 가격 수정 |
| `PATCH` | `/api/products/{id}/status` | 상태 변경 |
| `DELETE` | `/api/products/{id}` | 상품 삭제 |

> `GET /api/products` 필터 파라미터: `cursor`, `categoryId`, `status`, `minPrice`, `maxPrice`, `size`

### Order — `/api/orders`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/orders` | 주문 목록 (커서 + 필터) |
| `GET` | `/api/orders/{id}` | 주문 상세 (항목 포함) |
| `GET` | `/api/orders/stats/monthly-sales` | 월별 매출 집계 |
| `GET` | `/api/orders/stats/top-products` | 상품별 판매 TOP N |
| `POST` | `/api/orders` | 주문 생성 (분산락 + 멱등성) |
| `PATCH` | `/api/orders/{id}/pay` | 결제 처리 |
| `PATCH` | `/api/orders/{id}/ship` | 배송 시작 |
| `PATCH` | `/api/orders/{id}/deliver` | 배송 완료 |
| `DELETE` | `/api/orders/{id}` | 주문 취소 |

> `POST /api/orders` 헤더: `Idempotency-Key: {UUID}` (선택, 중복 요청 방지)

### Ranking — `/api/rankings`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/rankings/realtime` | 슬라이딩 윈도우 인기 상품 TOP N |

> 파라미터: `windowHours` (1~24, 기본 1), `size` (기본 10)  
> DB 쿼리 없음 — Redis Sorted Set O(log N) 응답

### Statistics — `/api/statistics`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/statistics/daily-sales` | 기간별 카테고리 매출 집계 |
| `GET` | `/api/statistics/top-products` | 날짜별 상품 판매 TOP 10 |
| `GET` | `/api/statistics/products/{id}/trend` | 상품별 기간 판매 추이 |

> 비정규화 테이블 단순 조회 — 조인 없음, 5ms 이내 응답

---

## 핵심 아키텍처 패턴

### 1. 분산 락 + 펜싱 토큰

**문제**: 동시에 여러 요청이 같은 상품의 재고를 차감할 때 Race Condition 발생.

**해결**:
1. Redisson 기반 `DistributedLock`으로 상품별 락 획득
2. 락 획득 시 단조 증가하는 `FencingToken` 발급
3. 재고 차감 시 `fenceToken > lastFenceToken` 검증 → 오래된 락 보유자 차단

```
Client A (락 획득, token=5) ─────────────────────▶ 재고 차감 성공
Client B (락 획득 실패로 지연, token=3) ─▶ DB에서 token=3 < lastFenceToken=5 → 거부
```

**코드 흐름**:
```
OrderFacade.placeOrder()
  ├─ productId 오름차순 정렬 (다중 상품 데드락 방지)
  ├─ 각 상품별 tryLock() → FencingToken 획득
  ├─ OrderService.createOrder(command + fencingTokens)
  │   └─ product.decreaseStock(quantity, fenceToken)  ← 엔티티 내부 검증
  └─ finally: unlock() 역순 해제
```

**Redis 장애 Fallback**: `RedisUnavailableException` 발생 시 자동으로 `SELECT FOR UPDATE` (DB 비관적 락) 경로로 전환.

---

### 2. 멱등성 처리

**문제**: 네트워크 이슈로 클라이언트가 동일 주문 요청을 재전송하면 중복 주문 생성.

**해결**: 클라이언트가 UUID 기반 `Idempotency-Key` 헤더를 발급. Redis에 처리 상태 + 결과를 24시간 캐싱.

```
상태 머신 (Redis 키: "idempotency:{key}")
  PROCESSING → 동시 중복 요청 차단 (DuplicateRequestException 429)
  COMPLETED  → 캐시된 결과 즉시 반환 (DB/Lock 미접근)
  없음       → 신규 요청으로 처리 시작
```

---

### 3. Outbox Pattern

**문제**: 주문 저장(DB 커밋)과 Kafka 이벤트 발행 사이의 원자성 보장 불가. DB 커밋 후 Kafka 발행 실패 시 이벤트 유실.

**해결**: 이벤트를 Kafka가 아닌 같은 DB 트랜잭션에 저장. 별도 스케줄러(Relay)가 Kafka로 발행.

```
[OrderService] — @Transactional
  ├─ Orders INSERT
  ├─ OrderItem INSERT  
  ├─ Product stock UPDATE
  └─ OutboxEvent INSERT  ← 같은 트랜잭션 (All or Nothing)

[OutboxMessageRelay] — @Scheduled(fixedDelay=3000)
  ├─ Redis 분산 락 획득 (Scale-out 환경에서 단일 인스턴스 보장)
  ├─ SELECT * FROM outbox_event WHERE status='PENDING' ORDER BY created_at LIMIT 100
  ├─ kafkaTemplate.send().get(5s)  ← 동기 ACK 대기
  ├─ 성공: status = 'PUBLISHED'
  └─ 실패: retryCount++, 3회 초과 시 status = 'FAILED'
```

**전달 보장**: At-Least-Once (Kafka ACK 후 PUBLISHED 마킹 → Consumer 멱등성 필요)

**AOP Self-invocation 방지**:  
`@Scheduled` 메서드에 `@Transactional`을 직접 붙이지 않고 `TransactionTemplate`으로 프로그래밍 방식 트랜잭션 관리.

---

### 4. 실시간 랭킹 (Redis Sorted Set)

**문제**: "최근 1시간 인기 상품 TOP 10"을 ORDER BY COUNT로 DB에서 집계하면 3,000만 건 테이블 풀 스캔.

**해결**: Redis Sorted Set으로 시간대별 판매량 누적. 슬라이딩 윈도우 집계.

**자료구조**:
```
Key:    "RANK:hourly:{yyyyMMddHH}"   예) "RANK:hourly:2026040914"
Member: productId (String)
Score:  해당 시간대 누적 판매 수량
TTL:    25시간 (자동 만료)
```

**슬라이딩 윈도우 집계** (`windowHours=6`):
```
keys = ["RANK:hourly:2026040909", ..., "RANK:hourly:2026040914"]
각 키의 Entry를 합산 → 임시 DEST 키 생성
ZREVRANGE DEST 0 9 WITHSCORES → Top 10 반환
DEST 키 즉시 삭제
```

**이벤트 흐름**:
```
ORDER_CREATED
  → RankingEventConsumer (ranking-consumer-group)
  → RankingService.incrementSales(productId, quantity)
  → ZINCRBY "RANK:hourly:{HH}" quantity "{productId}"   O(log N), Lock-free
```

---

### 5. CQRS 통계

**문제**: 집계 쿼리(`SELECT SUM ... GROUP BY category_id`)가 3,000만 건 테이블을 매번 스캔 → 수 초 응답.

**해결**: 주문 이벤트 발생 시 비정규화 통계 테이블을 실시간 갱신 (CQRS Write Side). 조회 시 단순 SELECT (CQRS Read Side).

```
Write Side (이벤트 기반 갱신):
  ORDER_CREATED
    → StatisticsEventConsumer (statistics-consumer-group)
    → daily_sales_summary UPSERT  (date, category_id → totalAmount, orderCount 증가)
    → product_daily_sales UPSERT  (date, product_id  → soldCount, revenue 증가)
  
  ORDER_CANCELED
    → 위 두 테이블 역산 (감소)

Read Side (단순 조회):
  GET /api/statistics/daily-sales
    → SELECT * FROM daily_sales_summary WHERE date BETWEEN ? AND ?
    → 조인 없음, 인덱스 직접 사용 → 5ms 이내
```

**성능 개선**:
| 방식 | 쿼리 | 응답시간 |
|------|------|----------|
| 기존 (집계) | `GROUP BY` on 3,000만 건 | ~3초 |
| CQRS (비정규화) | 단순 `SELECT` | ~5ms |

---

## 이벤트 파이프라인 전체 흐름

```
[Client]
    │
    ▼
[OrderController] POST /api/orders
    │
    ▼
[OrderFacade]
    ├─ 멱등성 검사 (Redis)
    ├─ 분산 락 획득 (Redisson + FencingToken)
    └─ OrderService.createOrder()
            │
            └─ @Transactional
                ├─ Orders INSERT
                ├─ OrderItem INSERT
                ├─ Product stock DECR (펜싱 토큰 검증)
                └─ OutboxEvent INSERT (status=PENDING)

                        │ (최대 3초 지연)
                        ▼

[OutboxMessageRelay] @Scheduled(fixedDelay=3000)
    ├─ Redis 분산 락 (단일 인스턴스 보장)
    └─ Kafka "order-events" 발행

                        │
            ┌───────────┼────────────┐
            │                        │
            ▼                        ▼
[ranking-consumer-group]    [statistics-consumer-group]
  RankingEventConsumer        StatisticsEventConsumer
            │                        │
            ▼                        ▼
  Redis Sorted Set            DB UPSERT
  "RANK:hourly:{HH}"          daily_sales_summary
  (시간대별 판매량)             product_daily_sales
            │                        │
            ▼                        ▼
  GET /api/rankings/realtime  GET /api/statistics/daily-sales
  (DB 쿼리 없음, O(log N))    (단순 SELECT, 5ms 이내)
```

---

## 페이지네이션 전략 비교

| 버전 | 방식 | COUNT | OFFSET | 특징 |
|------|------|-------|--------|------|
| v1 | `Page<T>` | O | O | 전체 건수 포함, 대용량 비효율 |
| v2 | `Slice<T>` | X | O | COUNT 제거, 하지만 OFFSET 문제 잔존 |
| v3 | Cursor | X | X | **권장**: 인덱스 직접 탐색, 일관된 성능 |

**커서 기반 동작 원리**:
```sql
-- 첫 페이지
SELECT * FROM member ORDER BY id DESC LIMIT 21

-- 두 번째 페이지 (cursor = 마지막 id)
SELECT * FROM member WHERE id < :cursor ORDER BY id DESC LIMIT 21
```

---

## 면접 스토리라인

이 프로젝트의 기술 선택들은 하나의 일관된 스토리로 연결된다:

> "3,000만 건 주문, 100만 건 상품이 존재하는 시스템에서 Flash-like 트래픽을 어떻게 처리했는가?"

1. **DB 동시성** → 분산 락 + 펜싱 토큰으로 Race Condition 차단
2. **중복 요청** → Redis 기반 멱등성으로 중복 주문 방지
3. **Redis 장애** → DB 비관적 락으로 자동 Fallback (무중단)
4. **이벤트 유실** → Outbox 패턴으로 At-Least-Once 발행 보장
5. **읽기 집계 부하** → CQRS 통계 테이블로 집계 쿼리 제거 (3초 → 5ms)
6. **실시간 랭킹** → Redis Sorted Set으로 DB 쿼리 없이 O(log N) 응답
7. **페이지네이션** → OFFSET 제거, 커서 기반으로 대용량에서도 일관된 성능

각 패턴은 **왜(Why)** → **무엇을(What)** → **어떻게(How)** → **트레이드오프(Trade-off)** 의 구조로 설명 가능하다.
