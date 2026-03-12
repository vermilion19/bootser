# Shopping Mall 개발 순서 로드맵

기획서(`shopping-mall-backend-project-plan.md`) 기반으로 정리한 실제 개발 순서.
각 Phase는 이전 Phase가 완료된 후 진행하는 것을 원칙으로 한다.

---

## 의존성 현황

### 현재 추가된 의존성 (`build.gradle.kts`)

| 분류 | 라이브러리 | 용도 |
|------|-----------|------|
| Core | `kotlin-core` (내부 모듈) | Kotlin 기본, Jackson |
| Web | `spring-boot-starter-web` | REST API |
| ORM | `spring-boot-starter-data-jpa` | JPA/Hibernate |
| Validation | `spring-boot-starter-validation` | 입력값 검증 |
| Security | `spring-boot-starter-security` | 인증/인가 |
| JWT | `jjwt-api/impl/jackson 0.12.6` | JWT 발급/검증 |
| Redis | `spring-boot-starter-data-redis` | 캐싱, 중복 방지 |
| API Docs | `springdoc-openapi-starter-webmvc-ui 2.8.8` | Swagger UI |
| DB (운영) | `postgresql` | PostgreSQL |
| DB (테스트) | `h2` | 인메모리 DB |
| Test | `kotest-runner-junit5 5.9.1` | 테스트 프레임워크 |
| Test | `kotest-assertions-core 5.9.1` | 단언 |
| Test | `kotest-extensions-spring 1.3.0` | Spring 통합 테스트 |
| Test | `mockk 1.14.2` | Mocking |
| Test | `testcontainers postgresql 1.20.4` | DB 통합 테스트 |
| Test | `testcontainers junit-jupiter 1.20.4` | TC JUnit5 연동 |

### 나중에 추가할 의존성 (Advanced 단계)

| 라이브러리 | 시점 | 용도 |
|-----------|------|------|
| `spring-kafka` | Phase 4 이후 | 알림, 이벤트 기반 처리 |
| `aws-java-sdk-s3` 또는 `software.amazon.awssdk:s3` | Phase 4 이후 | 상품 이미지 업로드 |
| OpenSearch / Elasticsearch client | Advanced | 상품 검색 엔진 연동 |

---

## Phase 1 — 기반 구축

> **목표**: 다른 모든 기능의 전제가 되는 공통 구조와 인증 완성

### 1-1. 공통 응답/예외 구조

- [x] `ApiResponse<T>` 공통 응답 래퍼
- [x] `ErrorCode` enum (도메인별 에러 코드 정의)
- [x] `GlobalExceptionHandler` (`@RestControllerAdvice`)
- [x] `BaseEntity` (createdAt, updatedAt, softDelete)

### 1-2. 인증/회원 (auth / user 도메인)

- [x] `User` 엔티티 + `UserAddress` 엔티티
- [x] `UserRepository` + `UserAddressRepository`
- [x] `UserException`, `UserService`, `UserController`
- [x] `SignupRequest`, `UpdateProfileRequest`, `UserResponse`
- [x] `SecurityConfig` (PasswordEncoder Bean, FilterChain 기본 설정)
- [x] `Role` / `UserRole` — RBAC (USER, ADMIN 역할 분리)
- [x] 회원가입 `POST /api/v1/auth/signup`
- [x] 로그인 `POST /api/v1/auth/login` → Access Token + Refresh Token 발급
- [x] 토큰 재발급 `POST /api/v1/auth/refresh`
- [x] JWT 필터 구성 (JwtProvider, JwtAuthenticationFilter)
- [x] 내 프로필 조회/수정 `GET /api/v1/users/me`, `PATCH /api/v1/users/me`
- [x] 배송지 관리 `GET|POST /api/v1/users/me/addresses`

**핵심 난이도**: JWT 필터 구성, Refresh Token 저장 전략 (Redis or DB)

---

## Phase 2 — 카탈로그 + 재고

> **목표**: 주문/장바구니의 전제 조건인 상품 데이터 구조 완성

### 2-1. 카테고리 (catalog 도메인)

- [ ] `Category` 엔티티 (parent_id 기반 계층 구조)
- [ ] 카테고리 목록 조회 `GET /api/v1/categories`
- [ ] 관리자 카테고리 등록/수정 `POST|PATCH /admin/v1/categories`

### 2-2. 상품 / 옵션 / Variant (catalog 도메인)

- [ ] `Product` 엔티티 (판매 상태: ON_SALE, SOLD_OUT, HIDDEN)
- [ ] `ProductOptionGroup` + `ProductOptionValue` 엔티티
- [ ] `ProductVariant` 엔티티 (SKU 단위)
- [ ] `ProductVariantOptionValue` (Variant ↔ OptionValue 매핑)
- [ ] `ProductImage` 엔티티
- [ ] 상품 목록 조회 `GET /api/v1/products` (필터링, 페이지네이션)
- [ ] 상품 상세 조회 `GET /api/v1/products/{productId}`
- [ ] 관리자 상품 등록/수정 `POST|PATCH /admin/v1/products`
- [ ] 관리자 옵션 관리 `POST /admin/v1/products/{productId}/options`

### 2-3. 재고 (inventory 도메인)

- [ ] `Inventory` 엔티티 (variant 단위, `version` 컬럼으로 낙관적 락 준비)
- [ ] `InventoryHistory` 엔티티 (변경 이력)
- [ ] 관리자 재고 수정 `PATCH /admin/v1/inventories/{inventoryId}`
- [ ] 재고 조회 API

### 2-4. 상품 목록 캐싱

- [ ] 상품 목록 / 카테고리 Redis 캐싱 적용 (`@Cacheable`)

**핵심 난이도**: Variant/SKU 구조 (Product → Variant → Inventory 1:1 관계 설계)

---

## Phase 3 — 구매 핵심 플로우

> **목표**: 장바구니 → 주문 → 결제 → 재고 차감 전체 플로우 완성
> 이 Phase가 프로젝트의 핵심이며 가장 어려운 구간이다.

### 3-1. 장바구니 (cart 도메인)

- [ ] `Cart` + `CartItem` 엔티티
- [ ] 장바구니 조회 `GET /api/v1/cart`
- [ ] 상품 담기 `POST /api/v1/cart/items`
- [ ] 수량 수정 `PATCH /api/v1/cart/items/{cartItemId}`
- [ ] 삭제 `DELETE /api/v1/cart/items/{cartItemId}`
- [ ] 장바구니 예상 결제 금액 계산 (가격 스냅샷 포함)

### 3-2. 주문 (order 도메인)

- [ ] `Order` + `OrderItem` 엔티티 (스냅샷 컬럼 포함)
- [ ] `OrderStatusHistory` 엔티티
- [ ] 주문 생성 `POST /api/v1/orders`
  - 재고 검증
  - 가격 재검증 (장바구니 가격 신뢰 금지)
  - OrderItem 스냅샷 저장
  - 재고 `reserved_quantity` 증가 또는 차감 정책 결정
- [ ] 주문 상태 전이 정의

```
CREATED → PAYMENT_PENDING → PAID → PREPARING → SHIPPED → DELIVERED
                          ↘ PAYMENT_FAILED
CREATED|PAYMENT_PENDING|PAID → CANCELED
DELIVERED → REFUND_REQUESTED → REFUNDED
```

- [ ] 주문 목록 조회 `GET /api/v1/orders`
- [ ] 주문 상세 조회 `GET /api/v1/orders/{orderId}`
- [ ] 주문 취소 `POST /api/v1/orders/{orderId}/cancel`

### 3-3. 결제 (payment 도메인)

- [ ] `Payment` + `PaymentEvent` 엔티티
- [ ] Mock 결제 구현 (실제 PG 연동 없이 성공/실패 시뮬레이션)
- [ ] 결제 요청/확인 `POST /api/v1/payments/confirm`
- [ ] Webhook 수신 `POST /api/v1/payments/webhook`
- [ ] `idempotency_key` 적용 (중복 결제 방지)
- [ ] 결제 성공 → 재고 차감 + 주문 상태 PAID 전이
- [ ] 결제 실패 → 주문 상태 PAYMENT_FAILED 전이 + 재고 복구

**핵심 난이도**: 재고 동시성 (낙관적 락 `@Version`), 결제 멱등성, 상태 전이 정합성

---

## Phase 4 — 운영 기능

> **목표**: 실제 서비스 운영에 필요한 기능들 완성

### 4-1. 쿠폰 (coupon 도메인)

- [ ] `Coupon` + `UserCoupon` + `OrderCouponUsage` 엔티티
- [ ] 쿠폰 타겟 매핑 (`CouponProductTarget`, `CouponCategoryTarget`)
- [ ] 쿠폰 발급 `POST /api/v1/coupons/{couponId}/issue`
- [ ] 내 쿠폰 목록 `GET /api/v1/coupons/me`
- [ ] 주문 시 쿠폰 적용 (정액/정률 할인 계산)
- [ ] 중복 사용 방지 (Redis SETNX 또는 DB UNIQUE 제약)
- [ ] 관리자 쿠폰 생성/발급 `POST /admin/v1/coupons`

### 4-2. 배송 (shipment 도메인)

- [ ] `Shipment` + `ShipmentItem` 엔티티
- [ ] 배송지 스냅샷 저장 (주문 시점 주소 복사)
- [ ] 배송 상태 관리 (READY → SHIPPED → DELIVERED)
- [ ] 송장번호 저장
- [ ] 관리자 배송 상태 변경 API

### 4-3. 리뷰 (review 도메인)

- [ ] `Review` 엔티티 (`order_item_id` 연결로 구매 검증)
- [ ] 리뷰 작성 `POST /api/v1/reviews` (배송 완료 후에만 허용)
- [ ] 리뷰 목록 조회 `GET /api/v1/products/{productId}/reviews`
- [ ] 동일 `order_item_id` 기준 중복 작성 방지 (UNIQUE 제약)
- [ ] 관리자 리뷰 숨김 처리

### 4-4. 관리자 통합 API

- [ ] 주문 목록/상태 변경 `GET|PATCH /admin/v1/orders`
- [ ] 대시보드 요약 `GET /admin/v1/dashboard/summary`

---

## Phase 5 — 품질 강화

> **목표**: 테스트 커버리지 확보 + 운영 준비

### 5-1. 핵심 테스트 (Kotest + Testcontainers)

- [ ] 재고 차감 동시성 테스트 (스레드 다수로 동시 주문 시뮬레이션)
- [ ] 결제 상태 전이 테스트
- [ ] 쿠폰 중복 사용 방지 테스트
- [ ] 주문 생성 성공/실패 통합 테스트
- [ ] 관리자 권한 접근 제어 테스트

### 5-2. 성능 개선

- [ ] 상품 목록 N+1 쿼리 점검 (fetch join or BatchSize)
- [ ] 주요 조회 쿼리 인덱스 점검
- [ ] Redis 캐시 TTL 정책 정비

### 5-3. 문서화 및 배포

- [ ] Swagger 문서 정리 (태그, 설명, 예시 추가)
- [ ] Docker Compose 작성 (PostgreSQL + Redis)
- [ ] `README.md` 실행 가이드 작성
- [ ] 시퀀스 다이어그램 (주문-결제-재고 플로우)

---

## 진행 상황 요약

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 기반 구축 (공통 구조 + 인증/회원) | ✅ 완료 |
| Phase 2 | 카탈로그 + 재고 | 🔲 대기 |
| Phase 3 | 구매 핵심 플로우 (장바구니 → 주문 → 결제) | 🔲 대기 |
| Phase 4 | 운영 기능 (쿠폰 / 배송 / 리뷰) | 🔲 대기 |
| Phase 5 | 품질 강화 (테스트 / 성능 / 배포) | 🔲 대기 |