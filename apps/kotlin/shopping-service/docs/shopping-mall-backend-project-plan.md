# Shopping Mall Backend Project Plan

## 1. 프로젝트 개요

### 1.1 프로젝트명
**Complex Shopping Mall Backend Server**

### 1.2 프로젝트 목적
사이드프로젝트로 **실무형 기능을 폭넓게 포함한 쇼핑몰 백엔드 서버**를 구축한다. 단순 CRUD 수준이 아니라, 실제 커머스 서비스에서 자주 마주치는 도메인 문제를 다루는 것을 목표로 한다.

핵심 목적은 다음과 같다.

- 상품, 주문, 결제, 쿠폰, 재고, 배송, 리뷰, 관리자 기능을 통합한 커머스 백엔드 설계 경험 확보
- 대규모 서비스로 확장 가능한 구조를 염두에 둔 아키텍처 설계 연습
- 트랜잭션, 동시성, 정합성, 장애 대응 등 백엔드 핵심 문제 해결 경험 확보
- 포트폴리오로 활용 가능한 수준의 문서화와 코드 품질 확보

### 1.3 프로젝트 성격
- 형태: 개인 사이드프로젝트
- 범위: 백엔드 API 서버 및 관리자 기능 중심
- 우선순위: 실무형 설계 경험 > 과도한 UI 구현 > 운영 자동화 일부
- 개발 방식: MVP 완성 후 확장 기능을 단계적으로 추가

---

## 2. 프로젝트 목표

### 2.1 핵심 목표
1. **회원가입부터 주문 완료까지 이어지는 전체 구매 플로우 구현**
2. **실제 서비스처럼 복잡한 비즈니스 규칙 반영**
3. **운영 관점의 관리자 기능 포함**
4. **테스트 가능한 구조와 문서화된 API 제공**
5. **트래픽 증가를 고려한 성능/확장 전략 반영**

### 2.2 결과물 목표
- REST API 서버
- 관리자용 API 또는 Admin Web과 연동 가능한 API
- Swagger/OpenAPI 문서
- ERD 및 도메인 설계 문서
- Docker 기반 실행 환경
- 테스트 코드(Unit / Integration)
- README 및 기술 의사결정 문서(ADR 수준)

---

## 3. 문제 정의

쇼핑몰 서비스는 겉보기에는 단순해 보이지만 실제로는 아래와 같은 복잡한 문제를 포함한다.

- 상품 옵션 조합에 따른 재고 관리
- 장바구니 시점과 주문 시점 간 가격/재고 불일치 문제
- 쿠폰, 할인, 적립금 적용 우선순위
- 결제 성공/실패/취소/환불 상태 전이 관리
- 주문 상태 변경에 따른 배송 처리
- 동시 주문 시 재고 차감 정합성 보장
- 관리자에 의한 상품/주문/프로모션 운영
- 검색, 정렬, 필터링, 랭킹 등 조회 최적화

본 프로젝트는 위 문제를 다루며 **“실무형 커머스 백엔드”**를 만드는 것을 목표로 한다.

---

## 4. 사용자 및 역할 정의

### 4.1 고객(User)
- 회원가입/로그인
- 상품 탐색 및 검색
- 장바구니 담기
- 주문 생성 및 결제
- 주문 조회 및 취소 요청
- 리뷰 작성
- 쿠폰/포인트 조회

### 4.2 관리자(Admin)
- 상품 등록/수정/삭제
- 카테고리 및 옵션 관리
- 재고 수정
- 주문 상태 변경
- 쿠폰 및 프로모션 발급
- 회원 관리
- 리뷰 신고/숨김 처리
- 대시보드 조회

### 4.3 시스템 운영자(Operator, 선택)
- 배치 운영
- 장애 모니터링
- 로그/메트릭 확인
- 데이터 정합성 검증

---

## 5. 범위 정의

### 5.1 MVP 범위
아래 기능은 1차 완성 목표에 포함한다.

#### 인증/회원
- 회원가입
- 로그인 / 로그아웃
- JWT 기반 인증
- 회원 프로필 조회/수정
- 주소 관리

#### 상품
- 카테고리 관리
- 상품 기본 정보 관리
- 상품 옵션(사이즈, 색상 등) 관리
- 상품 이미지 관리
- 상품 목록/상세 조회
- 상품 검색 및 필터링

#### 재고
- 옵션 단위 재고 관리
- 주문 시 재고 차감
- 재고 부족 처리
- 동시성 제어 전략 반영

#### 장바구니
- 상품 담기/수정/삭제
- 옵션별 수량 관리
- 장바구니 기준 예상 결제 금액 계산

#### 주문
- 주문 생성
- 주문 항목 스냅샷 저장
- 주문 상태 관리
- 주문 취소
- 주문 내역 조회

#### 결제
- 결제 요청/성공/실패 처리
- PG사 연동을 고려한 추상화 계층
- 초기에는 Mock 결제 또는 테스트 결제 사용

#### 쿠폰/할인
- 정액/정률 쿠폰
- 발급 쿠폰 조회
- 주문 시 쿠폰 적용
- 최소 주문 금액, 중복 사용 불가 조건 처리

#### 배송
- 배송지 정보 저장
- 배송 준비/배송 중/배송 완료 상태 관리
- 송장번호 저장

#### 리뷰
- 구매 완료 상품에 한해 리뷰 작성
- 평점 및 텍스트 저장
- 리뷰 목록 조회

#### 관리자
- 상품/재고/주문/쿠폰 관리 API
- 운영용 통계 기초 데이터 제공

### 5.2 확장 범위(Advanced)
MVP 이후 단계적으로 추가한다.

- 포인트/적립금 시스템
- 타임세일, 프로모션 엔진
- 상품 좋아요/찜
- 최근 본 상품
- 인기 상품 랭킹
- 알림(이메일, Slack, Webhook)
- 환불 및 부분 취소
- 교환/반품 관리
- 비동기 이벤트 처리
- 검색 엔진 연동(OpenSearch/Elasticsearch)
- 분산 락/메시지 큐 기반 재고 처리
- 멀티 셀러/멀티 벤더 구조
- 추천 시스템 연동

### 5.3 제외 범위
초기 단계에서는 아래 항목은 우선순위를 낮춘다.

- 프론트엔드 완성도 높은 사용자 화면
- 실결제 운영 배포
- 멀티 리전 운영
- 정산 시스템
- 국제 배송/다국어/다통화

---

## 6. 핵심 기능 상세

### 6.1 인증/인가
- Access Token + Refresh Token 구조
- 사용자와 관리자 권한 분리
- 주요 민감 기능에 대한 역할 기반 접근 제어(RBAC)
- 비밀번호 암호화 저장

### 6.2 상품/카탈로그
- 카테고리 계층 구조 지원 가능
- 상품은 판매 상태(판매중, 품절, 숨김 등)를 가진다
- 옵션 조합 단위 SKU 관리
- 가격 정책은 “정가, 판매가, 할인 적용가”를 분리 관리

### 6.3 장바구니
- 장바구니는 사용자별 1개 활성 상태를 기본으로 한다
- 장바구니에 담긴 가격은 참고값이며, 실제 주문 생성 시점에 가격 재검증
- 품절/가격 변경 시 사용자에게 재확인 필요

### 6.4 주문
- 주문 생성 시 주문 항목 스냅샷을 별도 저장
- 상품 원본 정보가 변경되어도 기존 주문 기록은 유지
- 주문 상태 예시
  - CREATED
  - PAYMENT_PENDING
  - PAID
  - PREPARING
  - SHIPPED
  - DELIVERED
  - CANCELED
  - REFUND_REQUESTED
  - REFUNDED

### 6.5 결제
- 결제 도메인과 주문 도메인을 분리
- 외부 PG 응답 실패/지연/중복 콜백에 대응
- 멱등성 키(idempotency key) 적용 검토
- 결제 성공 후 주문 상태 전이

### 6.6 재고
- SKU 단위 재고 관리
- 주문 생성 또는 결제 완료 시점 차감 여부를 명확히 정의
- 초기 권장 방식: **주문 생성 시 재고 예약, 결제 실패 시 복구** 또는 **결제 완료 시 차감** 중 하나를 선택
- 사이드프로젝트에서는 구현 난이도와 정합성 균형을 위해 다음 전략 권장
  - MVP: 결제 직전 재고 검증 후 결제 성공 시 차감
  - Advanced: 재고 예약 테이블 도입

### 6.7 쿠폰/프로모션
- 쿠폰 타입
  - 정액 할인
  - 정률 할인
- 조건
  - 최소 주문 금액
  - 최대 할인 금액
  - 사용자별 1회 사용
  - 유효기간
  - 특정 카테고리/상품 한정

### 6.8 배송
- 주문별 배송지 스냅샷 저장
- 부분 배송은 초기 범위에서 제외 가능
- 배송 상태와 주문 상태의 관계를 명확히 관리

### 6.9 리뷰
- 구매 확정 또는 배송 완료 이후 작성 가능
- 동일 주문 항목 기준 리뷰 중복 작성 제한
- 관리자 신고/숨김 처리 기능 고려

---

## 7. 비즈니스 규칙 초안

1. 회원만 장바구니와 주문 기능을 사용할 수 있다.
2. 품절 상품은 장바구니에는 남아 있을 수 있으나 주문은 불가능하다.
3. 주문 시 상품명, 가격, 옵션명 등은 스냅샷으로 저장한다.
4. 주문 생성 시점에 쿠폰 사용 가능 여부를 검증한다.
5. 결제 성공 전까지 주문은 확정 상태가 아니다.
6. 결제 실패 시 주문은 실패 상태로 전이되거나 재시도 가능 상태를 가진다.
7. 배송 시작 이후에는 단순 주문 취소가 불가능하고 별도 반품/환불 프로세스로 처리한다.
8. 리뷰는 실제 구매한 상품에만 작성 가능하다.
9. 재고는 음수가 되면 안 된다.
10. 관리자 기능은 일반 사용자 권한과 완전히 분리한다.

---

## 8. 도메인 모델 초안

### 8.1 핵심 엔티티
- User
- UserAddress
- Admin
- Category
- Product
- ProductOption
- ProductImage
- Inventory
- Cart
- CartItem
- Order
- OrderItem
- Payment
- Coupon
- UserCoupon
- Shipment
- Review
- PointLedger (확장)
- Promotion (확장)

### 8.2 관계 예시
- User 1:N UserAddress
- Category 1:N Product
- Product 1:N ProductOption
- ProductOption 1:1 Inventory
- User 1:1 Cart
- Cart 1:N CartItem
- User 1:N Order
- Order 1:N OrderItem
- Order 1:1 Payment
- Order 1:1 Shipment
- User 1:N UserCoupon
- Product / OrderItem 1:N Review

### 8.3 설계 포인트
- `OrderItem`은 주문 시점의 가격, 할인 금액, 상품명, 옵션명 스냅샷을 가진다.
- `Payment`는 외부 거래 ID와 상태 이력을 저장한다.
- `Inventory`는 가용 수량, 안전 재고, 예약 수량(확장)을 가질 수 있다.
- `Coupon`과 `UserCoupon`을 분리하여 발급 이력을 관리한다.

---

## 9. API 설계 방향

### 9.1 사용자 API 예시
#### 인증
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

#### 회원
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`
- `GET /api/v1/users/me/addresses`
- `POST /api/v1/users/me/addresses`

#### 상품
- `GET /api/v1/products`
- `GET /api/v1/products/{productId}`
- `GET /api/v1/categories`

#### 장바구니
- `GET /api/v1/cart`
- `POST /api/v1/cart/items`
- `PATCH /api/v1/cart/items/{cartItemId}`
- `DELETE /api/v1/cart/items/{cartItemId}`

#### 주문
- `POST /api/v1/orders`
- `GET /api/v1/orders`
- `GET /api/v1/orders/{orderId}`
- `POST /api/v1/orders/{orderId}/cancel`

#### 결제
- `POST /api/v1/payments/confirm`
- `POST /api/v1/payments/webhook`

#### 쿠폰
- `GET /api/v1/coupons/me`
- `POST /api/v1/coupons/{couponId}/issue`

#### 리뷰
- `POST /api/v1/reviews`
- `GET /api/v1/products/{productId}/reviews`

### 9.2 관리자 API 예시
- `POST /admin/v1/products`
- `PATCH /admin/v1/products/{productId}`
- `POST /admin/v1/products/{productId}/options`
- `PATCH /admin/v1/inventories/{inventoryId}`
- `GET /admin/v1/orders`
- `PATCH /admin/v1/orders/{orderId}/status`
- `POST /admin/v1/coupons`
- `GET /admin/v1/dashboard/summary`

### 9.3 API 설계 원칙
- REST 스타일 우선
- 상태 변경은 명확한 액션 엔드포인트 또는 커맨드성 API 사용
- 예외 응답 포맷 통일
- 페이지네이션 기본 지원
- 관리자/사용자 API 분리
- Swagger/OpenAPI 문서 자동화

---

## 10. 아키텍처 제안

### 10.1 권장 구조
사이드프로젝트 1차 구현은 **모듈형 모놀리식(Modular Monolith)** 구조를 권장한다.

이유는 다음과 같다.

- 혼자 개발할 때 복잡도를 통제하기 좋다.
- MSA보다 빠르게 개발 가능하다.
- 도메인 경계를 명확히 나누면 이후 서비스 분리도 가능하다.
- 테스트와 배포 단순성이 높다.

### 10.2 모듈 예시
- auth
- user
- catalog
- inventory
- cart
- order
- payment
- coupon
- shipment
- review
- admin
- common

### 10.3 계층 구조 예시
- Presentation Layer: Controller, Request/Response DTO
- Application Layer: UseCase, Service, Facade
- Domain Layer: Entity, Domain Service, Policy
- Infrastructure Layer: Repository, External API Client, Redis, MQ

### 10.4 이벤트 활용 포인트
초기에는 동기 처리 위주로 구현하되, 아래는 이벤트화 여지가 있다.

- 주문 완료 후 알림 발송
- 결제 성공 후 포인트 적립
- 배송 완료 후 리뷰 작성 가능 처리
- 인기 상품 통계 집계

---

## 11. 기술 스택 제안

### 11.1 백엔드
- Language: Kotlin
- Framework: Spring Boot
- Build: Gradle
- ORM: Spring Data JPA + JOOQ(현재 숙련도 낮음)

### 11.2 데이터
- RDBMS: PostgreSQL
- Cache: Redis
- Search(선택): OpenSearch 또는 Elasticsearch

### 11.3 인프라/운영
- Docker / Docker Compose
- GitHub Actions
- Nginx (선택)
- AWS EC2 / ECS / Lightsail 중 선택
- S3 또는 호환 스토리지(상품 이미지)

### 11.4 관측성/문서화
- Swagger / springdoc-openapi
- Prometheus + Grafana (확장)
- ELK / Loki (확장)

### 11.5 테스트
- kotest
- MockK 또는 Mockito
- Testcontainers
- RestAssured 또는 MockMvc

---

## 12. 데이터베이스 설계 핵심 포인트

### 12.1 테이블 분리 원칙
- 조회 최적화보다 우선 정규화 기반으로 시작
- 이후 병목 구간에 한해 읽기 최적화 적용
- 주문/결제/재고는 이력 관리와 정합성 우선

### 12.2 주요 컬럼 예시
#### Product
- id
- category_id
- name
- description
- status
- list_price
- sale_price
- created_at
- updated_at

#### ProductOption
- id
- product_id
- option_name
- option_value
- sku
- additional_price
- status

#### Inventory
- id
- product_option_id
- available_quantity
- reserved_quantity
- version

#### Order
- id
- user_id
- order_number
- status
- total_amount
- discount_amount
- payment_amount
- ordered_at

#### Payment
- id
- order_id
- method
- status
- transaction_key
- approved_at
- failed_reason

---

## 13. 성능 및 확장 고려사항

### 13.1 성능 포인트
- 상품 목록 조회 캐싱
- 카테고리/베스트 상품 캐싱
- 장바구니/주문 생성은 트랜잭션 범위 최소화
- 복합 인덱스 설계
- N+1 방지

### 13.2 확장 포인트
- 검색 서비스 분리 가능성 고려
- 주문/결제/알림 이벤트 분리 가능성 고려
- 배치 작업 독립 실행 고려
- 이미지 저장소 외부화

### 13.3 동시성 포인트
- 재고 차감 시 낙관적 락 또는 비관적 락 검토
- 중복 결제 요청 방지
- 동일 쿠폰 중복 사용 방지

---

## 14. 보안 요구사항

- 비밀번호 해시 저장
- JWT 서명키 관리
- 민감 정보 로깅 금지
- 관리자 API 별도 인증/인가 체계
- 입력값 검증 강화
- SQL Injection / XSS / CSRF 기본 대응 고려
- 웹훅 검증 로직 포함
- Rate Limiting(확장)

---

## 15. 비기능 요구사항

### 15.1 안정성
- 예외 응답 표준화
- 주요 도메인 로직 테스트 보강
- 장애 시 재처리 가능한 구조 지향

### 15.2 유지보수성
- 도메인 중심 패키지 구조
- 서비스 간 책임 분리
- 공통 코드 과도한 추상화 지양
- ADR 또는 설계 메모 기록

### 15.3 가시성
- 요청/응답 로깅 정책 정의
- 주문/결제/배송 상태 추적 로그 제공
- 운영 지표 수집 가능 구조 확보

---

## 16. 예외 시나리오 및 테스트 시나리오

### 16.1 핵심 예외 시나리오
- 결제 직전 재고 부족 발생
- 동일 상품 동시 주문
- 결제 승인 후 주문 상태 반영 실패
- 중복 결제 콜백 수신
- 만료 쿠폰 사용 시도
- 배송 시작 이후 취소 요청
- 리뷰 중복 작성 시도

### 16.2 필수 테스트 영역
- 주문 생성 성공/실패
- 쿠폰 적용 계산 검증
- 재고 차감 동시성 테스트
- 결제 상태 전이 테스트
- 관리자 권한 접근 제어 테스트
- API 통합 테스트

---

## 17. 개발 단계 제안

### 1단계: 기반 구축
- 프로젝트 세팅
- 공통 응답/예외 구조
- 인증/인가 기본 구조
- DB/Redis/Docker 환경 구성

### 2단계: 카탈로그
- 카테고리/상품/옵션/재고
- 상품 목록/상세 조회
- 관리자 상품 관리

### 3단계: 구매 흐름
- 장바구니
- 주문 생성
- 결제 연동(Mock)
- 주문 조회/취소

### 4단계: 운영 기능
- 쿠폰
- 배송
- 리뷰
- 관리자 주문 관리

### 5단계: 품질 강화
- 테스트 보강
- 캐싱 적용
- 로그/모니터링
- 성능 개선

---

## 18. 6주 일정 예시

### Week 1
- 요구사항 정리
- ERD 초안 작성
- 프로젝트 초기 세팅
- 인증/회원 기능 구현

### Week 2
- 상품/카테고리/옵션/재고 구현
- 관리자 상품 관리 API 구현
- 상품 조회 API 구현

### Week 3
- 장바구니 구현
- 주문 도메인 설계
- 주문 생성 API 구현

### Week 4
- 결제 Mock 연동
- 주문 상태 전이 구현
- 쿠폰 기능 구현

### Week 5
- 배송/리뷰/관리자 주문 관리 구현
- 예외 케이스 처리
- 테스트 코드 보강

### Week 6
- 성능 개선
- Swagger 및 README 정리
- Docker 배포 구성
- 포트폴리오 문서 정리

---

## 19. 산출물 목록

- 프로젝트 기획서
- 기능 명세서
- ERD
- API 명세서
- 시퀀스 다이어그램
- 기술 스택 선정 이유 문서
- 실행 가이드 README
- 테스트 시나리오 문서

---

## 20. 리스크 및 대응 전략

### 리스크 1. 기능 범위 과다
대응:
- MVP와 확장 기능을 명확히 분리
- 핵심 구매 흐름 우선 구현

### 리스크 2. 결제/재고 정합성 구현 난이도
대응:
- 초기에는 단순하면서 설명 가능한 정책 채택
- 주문/결제/재고 상태 전이를 문서화

### 리스크 3. 운영 기능 부족
대응:
- 관리자 API를 최소한으로라도 포함
- 주문/재고/쿠폰 관리 기능 우선 확보

### 리스크 4. 테스트 부족
대응:
- 주문/결제/재고는 반드시 통합 테스트 작성
- 동시성 테스트는 별도 시나리오로 관리

---

## 21. 최종 방향 제안

이 프로젝트는 단순 쇼핑몰이 아니라 **“실무형 커머스 백엔드 아키텍처 연습 프로젝트”**로 정의하는 것이 좋다.

따라서 다음 방향을 추천한다.

1. 1차는 **모듈형 모놀리식 + PostgreSQL + Redis + Mock Payment**로 구현한다.
2. 핵심은 **상품 → 장바구니 → 주문 → 결제 → 배송** 흐름 완성이다.
3. 이후 확장으로 **쿠폰, 리뷰, 포인트, 이벤트 기반 처리, 검색 최적화**를 붙인다.
4. 코드만큼 문서화(ERD, API, 시퀀스 다이어그램)를 중요하게 가져간다.

---

## 22. 다음 작업 우선순위

기획서 다음 단계는 아래 순서가 가장 효율적이다.

1. **도메인/ERD 설계**
2. **주문-결제-재고 상태 전이 정의**
3. **API 명세서 작성**
4. **프로젝트 패키지 구조 설계**
5. **MVP 구현 시작**

