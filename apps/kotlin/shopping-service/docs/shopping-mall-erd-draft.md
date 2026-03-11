# Shopping Mall ERD Draft

복잡한 기능을 포함한 쇼핑몰 백엔드 서버를 위한 **1차 ERD 초안**이다.  
이번 초안은 **MVP + 실무 확장 가능성**을 모두 고려해서, 지나치게 단순한 CRUD 구조보다는  
`Variant/SKU`, `Payment Retry`, `Shipment Split`, `Coupon Issuance`, `Order Snapshot` 을 반영하는 방향으로 설계했다.

> 표기 규칙: 실제 DB 테이블명은 `snake_case` 소문자를 권장하고, 아래 Mermaid 블록은 가독성을 위해 대문자 엔티티명으로 표기했다.

## ERD (Mermaid)

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar email
        varchar password_hash
        varchar name
        varchar phone
        varchar status
        timestamptz last_login_at
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at
    }

    ROLES {
        bigint id PK
        varchar code
        varchar name
        timestamptz created_at
    }

    USER_ROLES {
        bigint user_id PK FK
        bigint role_id PK FK
        timestamptz created_at
    }

    USER_ADDRESSES {
        bigint id PK
        bigint user_id FK
        varchar label
        varchar recipient_name
        varchar recipient_phone
        varchar zip_code
        varchar address1
        varchar address2
        boolean is_default
        timestamptz created_at
    }

    CATEGORIES {
        bigint id PK
        bigint parent_id FK
        varchar name
        varchar slug
        int depth
        int sort_order
        boolean is_active
        timestamptz created_at
    }

    PRODUCTS {
        bigint id PK
        bigint category_id FK
        varchar name
        text description
        varchar status
        numeric list_price
        numeric sale_price
        varchar currency
        timestamptz sales_start_at
        timestamptz sales_end_at
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_IMAGES {
        bigint id PK
        bigint product_id FK
        varchar image_url
        varchar image_type
        int sort_order
        boolean is_main
    }

    PRODUCT_OPTION_GROUPS {
        bigint id PK
        bigint product_id FK
        varchar name
        int display_order
        boolean is_required
    }

    PRODUCT_OPTION_VALUES {
        bigint id PK
        bigint option_group_id FK
        varchar value
        int display_order
    }

    PRODUCT_VARIANTS {
        bigint id PK
        bigint product_id FK
        varchar sku
        varchar status
        numeric additional_price
        int display_order
        timestamptz created_at
        timestamptz updated_at
    }

    PRODUCT_VARIANT_OPTION_VALUES {
        bigint variant_id PK FK
        bigint option_value_id PK FK
    }

    INVENTORIES {
        bigint id PK
        bigint variant_id FK
        int available_quantity
        int reserved_quantity
        int safety_stock
        bigint version
        timestamptz updated_at
    }

    INVENTORY_HISTORIES {
        bigint id PK
        bigint inventory_id FK
        varchar change_type
        int delta_quantity
        int before_quantity
        int after_quantity
        varchar reference_type
        bigint reference_id
        timestamptz created_at
    }

    CARTS {
        bigint id PK
        bigint user_id FK
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    CART_ITEMS {
        bigint id PK
        bigint cart_id FK
        bigint product_id FK
        bigint variant_id FK
        int quantity
        numeric unit_price_snapshot
        timestamptz created_at
        timestamptz updated_at
    }

    ORDERS {
        bigint id PK
        bigint user_id FK
        varchar order_number
        varchar status
        numeric total_product_amount
        numeric discount_amount
        numeric delivery_fee
        numeric payment_amount
        varchar currency
        timestamptz ordered_at
        timestamptz created_at
    }

    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        bigint variant_id FK
        varchar product_name_snapshot
        varchar sku_snapshot
        varchar option_summary_snapshot
        int quantity
        numeric unit_sale_price_snapshot
        numeric unit_discount_amount
        numeric line_total_amount
        varchar status
    }

    ORDER_STATUS_HISTORIES {
        bigint id PK
        bigint order_id FK
        varchar from_status
        varchar to_status
        varchar reason
        varchar actor_type
        bigint actor_id
        timestamptz created_at
    }

    PAYMENTS {
        bigint id PK
        bigint order_id FK
        varchar provider
        varchar method
        varchar status
        varchar payment_key
        varchar idempotency_key
        numeric requested_amount
        numeric approved_amount
        timestamptz approved_at
        timestamptz failed_at
    }

    PAYMENT_EVENTS {
        bigint id PK
        bigint payment_id FK
        varchar event_type
        varchar provider_event_id
        jsonb payload_json
        timestamptz created_at
    }

    COUPONS {
        bigint id PK
        varchar code
        varchar name
        varchar coupon_type
        numeric discount_value
        numeric max_discount_amount
        numeric min_order_amount
        varchar status
        timestamptz start_at
        timestamptz end_at
    }

    COUPON_PRODUCT_TARGETS {
        bigint coupon_id PK FK
        bigint product_id PK FK
    }

    COUPON_CATEGORY_TARGETS {
        bigint coupon_id PK FK
        bigint category_id PK FK
    }

    USER_COUPONS {
        bigint id PK
        bigint coupon_id FK
        bigint user_id FK
        varchar status
        timestamptz issued_at
        timestamptz used_at
        bigint used_order_id FK
    }

    ORDER_COUPON_USAGES {
        bigint id PK
        bigint order_id FK
        bigint user_coupon_id FK
        varchar coupon_code_snapshot
        varchar coupon_name_snapshot
        numeric discount_amount
        timestamptz created_at
    }

    SHIPMENTS {
        bigint id PK
        bigint order_id FK
        varchar status
        varchar carrier
        varchar tracking_number
        varchar recipient_name
        varchar recipient_phone
        varchar zip_code
        varchar address1
        varchar address2
        timestamptz shipped_at
        timestamptz delivered_at
    }

    SHIPMENT_ITEMS {
        bigint id PK
        bigint shipment_id FK
        bigint order_item_id FK
        int quantity
    }

    REVIEWS {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        bigint order_item_id FK
        int rating
        varchar status
        text content
        timestamptz created_at
        timestamptz updated_at
    }

    USERS ||--o{ USER_ADDRESSES : has
    USERS ||--o{ USER_ROLES : assigned
    ROLES ||--o{ USER_ROLES : maps
    CATEGORIES ||--o{ CATEGORIES : parent_of
    CATEGORIES ||--o{ PRODUCTS : contains
    PRODUCTS ||--o{ PRODUCT_IMAGES : shows
    PRODUCTS ||--o{ PRODUCT_OPTION_GROUPS : defines
    PRODUCT_OPTION_GROUPS ||--o{ PRODUCT_OPTION_VALUES : has
    PRODUCTS ||--o{ PRODUCT_VARIANTS : sells_as
    PRODUCT_VARIANTS ||--o{ PRODUCT_VARIANT_OPTION_VALUES : selects
    PRODUCT_OPTION_VALUES ||--o{ PRODUCT_VARIANT_OPTION_VALUES : selected_in
    PRODUCT_VARIANTS ||--|| INVENTORIES : tracked_by
    INVENTORIES ||--o{ INVENTORY_HISTORIES : logs
    USERS ||--o{ CARTS : owns
    CARTS ||--o{ CART_ITEMS : contains
    PRODUCTS ||--o{ CART_ITEMS : references
    PRODUCT_VARIANTS ||--o{ CART_ITEMS : selected_as
    USERS ||--o{ ORDERS : places
    ORDERS ||--o{ ORDER_ITEMS : contains
    PRODUCTS ||--o{ ORDER_ITEMS : originates
    PRODUCT_VARIANTS ||--o{ ORDER_ITEMS : ordered_as
    ORDERS ||--o{ ORDER_STATUS_HISTORIES : records
    ORDERS ||--o{ PAYMENTS : attempts
    PAYMENTS ||--o{ PAYMENT_EVENTS : receives
    COUPONS ||--o{ COUPON_PRODUCT_TARGETS : targets
    PRODUCTS ||--o{ COUPON_PRODUCT_TARGETS : limited_by
    COUPONS ||--o{ COUPON_CATEGORY_TARGETS : targets
    CATEGORIES ||--o{ COUPON_CATEGORY_TARGETS : limited_by
    COUPONS ||--o{ USER_COUPONS : issued_as
    USERS ||--o{ USER_COUPONS : owns
    ORDERS ||--o{ ORDER_COUPON_USAGES : applies
    USER_COUPONS ||--o{ ORDER_COUPON_USAGES : consumed_as
    ORDERS ||--o{ SHIPMENTS : ships
    SHIPMENTS ||--o{ SHIPMENT_ITEMS : includes
    ORDER_ITEMS ||--o{ SHIPMENT_ITEMS : allocated_to
    USERS ||--o{ REVIEWS : writes
    PRODUCTS ||--o{ REVIEWS : receives
    ORDER_ITEMS ||--o| REVIEWS : can_have
```

## 설계 전제

- 관리자 계정은 별도 `admin_users` 테이블보다 `users + roles + user_roles` 기반 RBAC로 통합했다.
- 실제 판매 단위는 `product_variants` 이며, 옵션이 없는 상품도 기본 Variant 1개를 갖는 방식으로 설계했다.
- 결제는 주문당 1건으로 고정하지 않고, 재시도/실패/취소/환불 확장을 고려해 `orders : payments = 1:N` 으로 잡았다.
- 배송은 MVP에서는 보통 주문당 1건이지만, 부분 배송 확장을 위해 `orders : shipments = 1:N` 으로 잡았다.
- 리뷰는 실제 구매 검증을 위해 `order_items` 에 직접 연결했다. 따라서 동일 주문 항목에 리뷰 중복 작성 제한을 걸기 쉽다.
- 주문 항목(`order_items`)은 상품명, SKU, 옵션명, 가격 스냅샷을 별도 컬럼으로 저장한다.
- 쿠폰은 발급 마스터(`coupons`)와 사용자 지급 이력(`user_coupons`)을 분리했고, 상품/카테고리 한정 쿠폰을 위해 타겟 매핑 테이블을 별도로 뒀다.

## 핵심 제약/인덱스 추천

- `users.email` UNIQUE
- `roles.code` UNIQUE
- `products` 는 `(category_id, status)` 인덱스 추천
- `product_variants.sku` UNIQUE
- `inventories.variant_id` UNIQUE
- `carts` 는 **활성 장바구니만** `user_id` 기준 partial unique index 추천 (`status = 'ACTIVE'`)
- `orders.order_number` UNIQUE
- `payments.payment_key` UNIQUE, `payments.idempotency_key` UNIQUE
- `reviews.order_item_id` UNIQUE
- `inventory_histories (inventory_id, created_at)` 복합 인덱스 추천
- `order_items (order_id)`, `shipments (order_id)`, `user_coupons (user_id, status)` 복합 인덱스 추천

## 모듈별 해석 포인트

### 1) 회원/권한
- 권한은 `roles`, `user_roles` 로 관리해서 일반 사용자/관리자/운영자를 확장 가능하게 둔다.
- 배송지 정보는 `user_addresses` 에 저장하지만, 실제 주문 배송지는 `shipments` 에 스냅샷으로 남긴다.

### 2) 상품/카탈로그
- `products` 는 상품 마스터, `product_variants` 는 실제 재고/주문/결제 단위 SKU 이다.
- 옵션 조합은 `product_option_groups -> product_option_values -> product_variant_option_values` 구조로 표현한다.
- 예: 티셔츠 상품 1개에 색상/사이즈 옵션 그룹이 있고, `Black + L` 조합이 하나의 Variant 가 된다.

### 3) 재고
- 재고 정합성은 `inventories.version` 을 사용한 optimistic lock 또는 DB 락 전략으로 처리하기 좋게 설계했다.
- 수량 변경 사유 추적을 위해 `inventory_histories` 를 별도로 둔다.

### 4) 장바구니
- 장바구니 항목은 Variant 기준으로 담고, 표시용/비교용 가격 스냅샷을 같이 보관한다.
- 실제 주문 생성 시에는 장바구니 가격을 신뢰하지 않고 재검증하는 흐름이 맞다.

### 5) 주문/결제
- 주문은 `orders`, 주문 상세는 `order_items`, 상태 이력은 `order_status_histories` 로 분리했다.
- 결제는 재시도와 웹훅 이벤트 추적을 위해 `payments`, `payment_events` 로 나눴다.
- 주문과 결제를 1:1로 두지 않은 점이 실무적으로 중요하다.

### 6) 쿠폰
- `coupons` 는 정책 마스터, `user_coupons` 는 실제 사용자 보유 쿠폰이다.
- `order_coupon_usages` 는 주문 시점의 할인액과 쿠폰 스냅샷을 남긴다.
- MVP에서는 주문당 쿠폰 1장만 허용해도 되고, 스키마는 다중 쿠폰 확장도 수용 가능하다.

### 7) 배송/리뷰
- `shipment_items` 를 두면 이후 부분 배송/분리 출고로 확장하기 쉽다.
- 리뷰는 `order_item_id` 로 구매 검증을 하므로 “구매한 상품만 리뷰 가능” 규칙을 강하게 보장할 수 있다.

## 구현 우선순위 추천

1. `users`, `user_addresses`, `roles`, `user_roles`
2. `categories`, `products`, `product_option_groups`, `product_option_values`, `product_variants`, `inventories`
3. `carts`, `cart_items`
4. `orders`, `order_items`, `order_status_histories`
5. `payments`, `payment_events`
6. `coupons`, `user_coupons`, `order_coupon_usages`
7. `shipments`, `shipment_items`, `reviews`

## 확장 테이블(이번 초안에서는 상세 생략)

- `point_ledgers` : 포인트/적립금
- `promotions`, `promotion_targets` : 타임세일/프로모션 엔진
- `wishlists` : 찜 기능
- `return_requests`, `exchange_requests`, `refunds` : 반품/교환/환불
- `notifications` : 이메일/Slack/Webhook 알림
- `product_rankings`, `recently_viewed_products` : 인기/최근 본 상품

## 다음 단계 추천

이 ERD 기준으로 다음 문서를 이어가면 흐름이 좋다.

1. PostgreSQL DDL 초안 (진행 중)
2. 주문 생성 / 결제 승인 / 재고 차감 시퀀스 다이어그램
3. 주문/결제/재고/쿠폰 API 명세 초안