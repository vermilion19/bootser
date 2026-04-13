# query-burst-msa

대용량 주문 트래픽을 MSA 구조로 처리하는 멀티모듈 프로젝트.  
`query-burst` 모놀리스와 동일한 도메인을 서비스 경계로 분리하여 각 서비스의 독립적인 확장과 배포를 목표로 한다.

## 서비스 구성

```
order-service ──(HTTP)──→ catalog-service
     │
     └──(Kafka: order-events)──→ analytics-service
                              └──→ ranking-service

member-service  (독립)
```

| 서비스 | 포트 | 저장소 | 역할 |
|--------|------|--------|------|
| `order-service` | 18115 | PostgreSQL, Kafka | 주문 생성·상태 전이, Outbox → Kafka 발행 |
| `catalog-service` | 18113 | PostgreSQL | 상품/카테고리 관리, 재고 예약 처리 |
| `analytics-service` | 18111 | PostgreSQL, Kafka | 주문 이벤트 기반 일별 매출 집계 Read Model |
| `ranking-service` | 18112 | Redis, Kafka | 주문 이벤트 기반 실시간 상품 랭킹 Read Model |
| `member-service` | 18114 | PostgreSQL | 회원 마스터 관리 |

## 모듈 구조

```
query-burst-msa/
├── contracts/           # 서비스 간 공유 계약 (이벤트 DTO, 내부 API 타입)
├── order-service/
├── catalog-service/
├── analytics-service/
├── ranking-service/
└── member-service/
```

### contracts

서비스 간 결합을 최소화하기 위한 공유 타입 모듈.

- **이벤트**: `OrderEventPayload`, `OrderEventItem`, `OrderEventType`
  - `ORDER_CREATED` / `ORDER_STATUS_CHANGED` / `ORDER_CANCELED`
- **재고 예약**: `InventoryReservationRequest/Response/Item/Status`

## 주요 아키텍처 패턴

### Outbox Pattern (order-service)

DB 트랜잭션과 Kafka 발행을 원자적으로 처리.

```
주문 생성 트랜잭션
  ├── orders 테이블 저장
  └── outbox_events 테이블 저장 (status=PENDING)
          ↓
  OrderOutboxRelay (3초 주기 폴링)
          ↓
  Kafka: order-events 토픽 발행 → status=PUBLISHED
```

### 재고 예약 2-Phase (order-service ↔ catalog-service)

```
createOrder()
  1. reserve()  → 재고 차감 + reservationId 반환
  2. 주문 저장 (STOCK_RESERVED or REJECTED)

pay()
  └── commit(reservationId)  → 예약 확정

cancel()
  └── release(reservationId) → 재고 복구
```

`Idempotency-Key` 헤더로 중복 주문 생성 방지.

### Kafka Consumer 멱등성

| 서비스 | 멱등성 처리 방식 |
|--------|----------------|
| `analytics-service` | `ProcessedOrderEvent` 테이블 (DB unique key) |
| `ranking-service` | Redis `setIfAbsent` (TTL 7일) |

### 실시간 랭킹 (ranking-service)

Redis Sorted Set을 시간 버킷(1시간 단위)으로 관리.

```
주문 이벤트 수신
  └── RANK:hourly:2025041318 에 productId별 수량 addScore
          ↓
조회 시 windowHours 범위의 버킷을 머지하여 Top-N 반환
```

- `ORDER_CREATED`: +quantity
- `ORDER_CANCELED`: -quantity
- 버킷 TTL: 25시간 자동 만료

### 일별 매출 집계 (analytics-service)

Kafka 이벤트를 카테고리·상품별로 누적하여 PostgreSQL Read Model 구축.

- `DailySalesSummary`: 날짜 × 카테고리별 총 매출/주문 수
- `ProductDailySales`: 날짜 × 상품별 판매 수량/매출

## API 요약

### order-service `:18115`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/orders` | 주문 목록 (커서 페이지네이션) |
| `GET` | `/api/orders/{orderId}` | 주문 상세 |
| `POST` | `/api/orders` | 주문 생성 (`Idempotency-Key` 헤더 지원) |
| `PATCH` | `/api/orders/{orderId}/pay` | 결제 처리 |
| `PATCH` | `/api/orders/{orderId}/ship` | 배송 시작 |
| `PATCH` | `/api/orders/{orderId}/deliver` | 배송 완료 |
| `DELETE` | `/api/orders/{orderId}` | 주문 취소 |

### catalog-service `:18113`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/products` | 상품 목록 (커서·카테고리·상태 필터) |
| `POST` | `/api/products` | 상품 등록 |
| `PATCH` | `/api/products/{id}/price` | 가격 변경 |
| `PATCH` | `/api/products/{id}/status` | 상태 변경 |
| `DELETE` | `/api/products/{id}` | 상품 삭제 |
| `GET` | `/api/categories` | 카테고리 목록 |
| `POST` | `/api/categories` | 카테고리 등록 |
| `POST` | `/internal/inventory/reservations` | 재고 예약 (order-service 전용) |
| `POST` | `/internal/inventory/reservations/{id}/commit` | 예약 확정 |
| `POST` | `/internal/inventory/reservations/{id}/release` | 예약 해제 |

### analytics-service `:18111`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/analytics/daily-sales` | 기간별 일별 매출 (`from`, `to`) |
| `GET` | `/api/analytics/top-products` | 특정 날짜 상위 상품 10개 |
| `GET` | `/api/analytics/product-trend/{id}` | 상품별 기간 판매 추이 |

### ranking-service `:18112`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/rankings/products` | 시간 윈도우 기반 상품 Top-N (`windowHours`, `size`) |

### member-service `:18114`

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/members/{memberId}` | 회원 조회 |
| `POST` | `/api/members` | 회원 등록 |
| `PATCH` | `/api/members/{memberId}` | 회원 정보 수정 |

## 인프라 의존성

```
booster-postgres  ← analytics, catalog, member, order (DB 분리)
booster-redis     ← ranking
booster-kafka     ← order (producer), analytics (consumer), ranking (consumer)
```

Kafka 토픽: `order-events`

## Scale-out

Nginx 로드밸런서를 통해 각 서비스를 독립적으로 수평 확장할 수 있다.  
상세 방법은 [RUNBOOK.md](./RUNBOOK.md)를 참고한다.

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml \
  up --scale catalog-service=3 --scale order-service=2 -d
```

## Observability

모든 서비스는 아래 엔드포인트를 노출한다.

| 항목 | 경로 |
|------|------|
| 메트릭 | `GET /actuator/prometheus` |
| 헬스 | `GET /actuator/health` |
| 트레이싱 | Tempo (Zipkin 프로토콜) |
| 로그 | Loki (Logback Appender) |

Prometheus는 `dns_sd_configs`로 스케일된 인스턴스를 자동 감지하여 인스턴스별 메트릭을 수집한다.