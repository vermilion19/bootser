# Query Burst PostgreSQL Query Practice Guide

## 목적

`query-burst`의 대용량 DB 구조를 기준으로 PostgreSQL 쿼리를 직접 실행해 보면서 다음을 연습한다.

- 복잡한 조회 쿼리 작성
- 실행 계획 해석
- 성능 이슈가 생기는 패턴 식별
- 인덱스와 쿼리 구조 최적화

API 기준이 아니라 PostgreSQL 쿼리 기준으로 정리한다.

## 스키마 요약

대용량 전제가 명시된 주요 테이블은 다음과 같다.

- `member`: 1,000만 건 목표
- `product`: 100만 건 목표
- `orders`: 3,000만 건 목표
- `order_item`: 1억 건 이상 목표
- `category`: 소수 마스터 데이터

주요 테이블 관계는 다음과 같다.

- `member` 1:N `orders`
- `orders` 1:N `order_item`
- `product` 1:N `order_item`
- `category` 1:N `product`
- `category` self join by `parent_id`
- `member` 1:N `product` via `seller_id`

## 현재 인덱스 기준

### `orders`

- `member_id`
- `status`
- `ordered_at`
- `(member_id, ordered_at)`
- `(status, ordered_at)`

### `order_item`

- `order_id`
- `product_id`
- `(product_id, created_at)`
- `(product_id, quantity, unit_price)`

### `product`

- `category_id`
- `seller_id`
- `status`
- `price`
- `(category_id, status, price)`

### `member`

- `grade`
- `region`
- `created_at`
- `(grade, created_at)`

### `category`

- `parent_id`
- `depth`

## 실행 계획 기본 원칙

모든 연습은 먼저 `EXPLAIN (ANALYZE, BUFFERS, VERBOSE)`로 시작한다.

```sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT ...
```

특히 아래를 본다.

- `Seq Scan`이 왜 발생하는지
- `actual rows`와 `estimated rows` 차이가 큰지
- `Rows Removed by Filter`가 과도한지
- `Sort Method`가 메모리인지 디스크인지
- `Hash Join`, `Merge Join`, `Nested Loop`가 데이터량에 맞는지
- `Heap Fetches`가 많은지
- `Buffers: shared hit/read` 비율이 어떤지

## 추천 연습 쿼리

### 1. 회원의 최근 주문 조회

```sql
SELECT o.id, o.member_id, o.status, o.total_amount, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
ORDER BY o.ordered_at DESC
LIMIT 50;
```

의도:

- `(member_id, ordered_at)` 인덱스 사용 확인
- `LIMIT`이 있을 때 정렬까지 인덱스로 처리되는지 확인

볼 포인트:

- `Index Scan` 또는 `Index Scan Backward` 여부
- 별도 `Sort`가 붙는지 여부

추가 연습:

```sql
CREATE INDEX CONCURRENTLY idx_orders_member_ordered_at_desc
ON orders (member_id, ordered_at DESC);
```

### 2. OFFSET 페이징 vs Keyset Pagination

```sql
SELECT o.id, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
ORDER BY o.ordered_at DESC
OFFSET 100000 LIMIT 50;
```

비교 대상:

```sql
SELECT o.id, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
  AND o.ordered_at < TIMESTAMP '2026-04-01 00:00:00'
ORDER BY o.ordered_at DESC
LIMIT 50;
```

의도:

- 큰 OFFSET이 왜 느린지 체감
- keyset pagination이 인덱스를 더 잘 활용하는지 확인

### 3. 최근 30일 상태별 주문 집계

```sql
SELECT o.status, count(*) AS cnt, sum(o.total_amount) AS amount
FROM orders o
WHERE o.ordered_at >= now() - interval '30 days'
GROUP BY o.status
ORDER BY amount DESC;
```

의도:

- `(status, ordered_at)`와 `ordered_at` 단일 인덱스 중 planner 선택 비교
- 범위 필터 + 집계에서 `Seq Scan`이 나와도 합리적인지 판단

추가 연습:

```sql
CREATE INDEX CONCURRENTLY idx_orders_ordered_at_status
ON orders (ordered_at, status);
```

같은 조건에서 `(status, ordered_at)`와 `(ordered_at, status)`의 차이를 비교한다.

### 4. 카테고리 + 상태 + 가격 범위 상품 검색

```sql
SELECT p.id, p.name, p.price, p.stock
FROM product p
WHERE p.category_id = 100
  AND p.status = 'ON_SALE'
  AND p.price BETWEEN 10000 AND 50000
ORDER BY p.price ASC
LIMIT 100;
```

의도:

- `(category_id, status, price)` 인덱스 교과서 케이스 확인
- 정렬까지 인덱스로 처리되는지 확인

변형:

- `SELECT *`로 바꿔서 plan 비교
- `ORDER BY p.price DESC`로 바꿔서 비교

### 5. 인덱스를 일부러 못 타게 만드는 나쁜 버전

```sql
SELECT p.id, p.name, p.price, p.stock
FROM product p
WHERE p.category_id = 100
  AND lower(p.status::text) = 'on_sale'
  AND p.price + 0 BETWEEN 10000 AND 50000
ORDER BY p.price ASC
LIMIT 100;
```

의도:

- 함수 적용
- 불필요한 산술 연산
- 실무형 인덱스 무력화 패턴 확인

### 6. 최근 30일 상품별 판매량 집계

```sql
SELECT oi.product_id,
       sum(oi.quantity) AS qty,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
WHERE oi.created_at >= now() - interval '30 days'
GROUP BY oi.product_id
ORDER BY revenue DESC
LIMIT 100;
```

의도:

- 가장 큰 테이블에서 기간 조건 중심 집계 연습
- 현재 `(product_id, created_at)` 인덱스가 이 쿼리에는 애매하다는 점 확인

추가 연습:

```sql
CREATE INDEX CONCURRENTLY idx_order_item_created_at_product_id
ON order_item (created_at, product_id);
```

### 7. 특정 상품의 일별 판매 추이

```sql
SELECT date_trunc('day', oi.created_at) AS day,
       sum(oi.quantity) AS qty,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
WHERE oi.product_id = 987654321
  AND oi.created_at >= now() - interval '90 days'
GROUP BY date_trunc('day', oi.created_at)
ORDER BY day;
```

의도:

- `(product_id, created_at)` 인덱스가 잘 맞는 쿼리 확인
- `GROUP BY date_trunc(...)` 집계 비용 확인

볼 포인트:

- `Bitmap Index Scan` vs `Index Scan`
- `HashAggregate` vs `GroupAggregate`

### 8. 카테고리별 최근 30일 매출 Top N

```sql
SELECT p.category_id,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
JOIN product p ON p.id = oi.product_id
JOIN orders o ON o.id = oi.order_id
WHERE o.ordered_at >= now() - interval '30 days'
  AND o.status = 'PAID'
GROUP BY p.category_id
ORDER BY revenue DESC
LIMIT 20;
```

의도:

- 대형 테이블 3개 조인 연습
- 조인 순서와 join method 비교

추가 인덱스 후보:

```sql
CREATE INDEX CONCURRENTLY idx_order_item_order_id_product_id
ON order_item (order_id, product_id);

CREATE INDEX CONCURRENTLY idx_orders_status_ordered_at_id
ON orders (status, ordered_at, id);
```

### 9. 회원 등급별 평균 주문 금액과 P90

```sql
SELECT m.grade,
       count(*) AS orders_count,
       avg(o.total_amount) AS avg_amount,
       percentile_cont(0.9) WITHIN GROUP (ORDER BY o.total_amount) AS p90_amount
FROM orders o
JOIN member m ON m.id = o.member_id
WHERE o.ordered_at >= now() - interval '180 days'
GROUP BY m.grade
ORDER BY avg_amount DESC;
```

의도:

- ordered-set aggregate 비용 확인
- `join -> group by -> percentile` 패턴 분석

### 10. 지역 + 등급 + 가입기간 필터

```sql
SELECT m.region, m.grade, count(*) AS cnt
FROM member m
WHERE m.grade = 'VIP'
  AND m.region = 'SEOUL'
  AND m.created_at >= now() - interval '1 year'
GROUP BY m.region, m.grade;
```

의도:

- 단일 인덱스만 있는 컬럼 조합에서 planner 선택 확인
- bitmap and 또는 seq scan 여부 비교

추가 인덱스 후보:

```sql
CREATE INDEX CONCURRENTLY idx_member_region_grade_created_at
ON member (region, grade, created_at);
```

### 11. 재귀 카테고리 트리 + 상품 수

```sql
WITH RECURSIVE cte AS (
    SELECT c.id, c.parent_id, c.name, c.depth
    FROM category c
    WHERE c.id = 1

    UNION ALL

    SELECT c.id, c.parent_id, c.name, c.depth
    FROM category c
    JOIN cte ON c.parent_id = cte.id
)
SELECT cte.id,
       cte.name,
       count(p.id) AS product_count
FROM cte
LEFT JOIN product p ON p.category_id = cte.id
GROUP BY cte.id, cte.name
ORDER BY product_count DESC;
```

의도:

- 재귀 CTE와 대형 테이블 join 연습
- 작은 마스터 테이블이 큰 테이블과 만날 때 비용 확인

### 12. fencing token 점검용 쿼리

```sql
SELECT p.id, p.stock, p.last_fence_token, p.updated_at
FROM product p
WHERE p.last_fence_token > 0
ORDER BY p.last_fence_token DESC
LIMIT 100;
```

의도:

- 신규 컬럼 `last_fence_token` 점검 쿼리 분석
- 운영성 조회가 늘면 왜 별도 인덱스가 필요한지 확인

추가 인덱스 후보:

```sql
CREATE INDEX CONCURRENTLY idx_product_last_fence_token
ON product (last_fence_token DESC)
WHERE last_fence_token > 0;
```

### 13. 주문별 최고가 상품 찾기

```sql
SELECT *
FROM (
    SELECT oi.order_id,
           oi.product_id,
           oi.unit_price,
           row_number() OVER (
               PARTITION BY oi.order_id
               ORDER BY oi.unit_price DESC
           ) AS rn
    FROM order_item oi
    WHERE oi.created_at >= now() - interval '30 days'
) t
WHERE t.rn = 1;
```

의도:

- window function + 대량 정렬 비용 확인
- `work_mem`과 sort spill 영향 체감

### 14. EXISTS vs JOIN DISTINCT 비교

```sql
SELECT p.id, p.name
FROM product p
WHERE EXISTS (
    SELECT 1
    FROM order_item oi
    WHERE oi.product_id = p.id
      AND oi.created_at >= now() - interval '7 days'
);
```

비교 대상:

```sql
SELECT DISTINCT p.id, p.name
FROM product p
JOIN order_item oi ON oi.product_id = p.id
WHERE oi.created_at >= now() - interval '7 days';
```

의도:

- `EXISTS`, `JOIN`, `DISTINCT`의 비용 차이 확인
- 중복 제거 비용이 얼마나 큰지 비교

## 일부러 느리게 만들어 보는 패턴

### 컬럼에 함수 적용

```sql
SELECT *
FROM orders o
WHERE date_trunc('day', o.ordered_at) = current_date;
```

개선:

```sql
SELECT *
FROM orders o
WHERE o.ordered_at >= current_date
  AND o.ordered_at < current_date + interval '1 day';
```

### 앞에 와일드카드가 붙은 LIKE

```sql
SELECT *
FROM member m
WHERE m.email LIKE '%gmail.com';
```

의도:

- 일반 B-Tree 인덱스가 왜 잘 안 쓰이는지 확인

### 정렬 인덱스가 없는 Top N

```sql
SELECT o.id, o.total_amount
FROM orders o
ORDER BY o.total_amount DESC
LIMIT 100;
```

의도:

- 대용량 전체 정렬 비용 확인

### OR 남발

```sql
SELECT *
FROM product p
WHERE p.status = 'ON_SALE'
   OR p.price < 1000
   OR p.stock = 0;
```

의도:

- planner가 단순하고 예쁜 인덱스 경로를 못 잡는 케이스 확인

## 어떤 식으로 최적화할지 연습할 것인가

각 쿼리마다 아래 순서로 반복하면 된다.

1. 원본 쿼리를 `EXPLAIN (ANALYZE, BUFFERS)`로 본다.
2. 실제로 느린 이유를 한 줄로 적는다.
3. 인덱스로 해결할지, 쿼리 구조를 바꿀지 먼저 결정한다.
4. 인덱스를 추가하거나 쿼리를 재작성한다.
5. plan이 바뀌었는지, 시간이 줄었는지 비교한다.
6. 쓰기 비용 증가나 불필요한 인덱스 생성 여부까지 같이 평가한다.

## 자주 보는 최적화 판단 기준

### 인덱스로 해결하는 편이 맞는 경우

- 선택도가 높은 조건으로 빠르게 줄일 수 있을 때
- `WHERE + ORDER BY + LIMIT` 조합이 자주 반복될 때
- 운영성 조회가 반복되고 전체 스캔이 낭비일 때

### 쿼리 구조를 바꾸는 편이 맞는 경우

- 큰 OFFSET이 있는 경우
- 함수 적용이나 불필요한 형변환 때문에 인덱스가 무력화된 경우
- `DISTINCT`, `SELECT *`, 과한 JOIN으로 중간 결과가 불필요하게 커진 경우
- 먼저 줄인 뒤 join 해야 하는데 큰 테이블끼리 먼저 붙는 경우

### 인덱스를 만들기 전에 확인할 것

- 읽기 이득 대비 쓰기 비용이 감당 가능한지
- 비슷한 인덱스가 이미 있는지
- 복합 인덱스 컬럼 순서가 실제 필터/정렬 순서와 맞는지
- partial index가 더 적절한지
- covering index가 테이블 접근을 줄일 가치가 있는지

## 실습 체크리스트

- `Seq Scan`이 나왔다고 바로 나쁜 것이라고 판단하지 않는다.
- `estimated rows`와 `actual rows` 차이가 크면 통계 문제를 의심한다.
- 인덱스를 탔어도 정렬이나 heap fetch 때문에 느릴 수 있다.
- 큰 집계 쿼리는 인덱스보다 먼저 데이터량을 줄이는 게 더 중요하다.
- 조인 쿼리는 각 단계에서 rows가 어떻게 증폭되는지 본다.
- `ANALYZE` 이후 실행 계획이 달라지는지도 확인한다.

## 다음 단계

### 1. 쿼리별 벤치마크 시트 만들기

아래 포맷으로 직접 기록한다.

| Query | Before ms | After ms | Plan 변화 | 적용한 최적화 | 비고 |
|------|-----------:|---------:|-----------|---------------|------|
| 최근 주문 조회 |  |  |  |  |  |
| 최근 30일 매출 집계 |  |  |  |  |  |
| 카테고리 매출 Top N |  |  |  |  |  |

### 2. 실제 데이터 분포 확인 쿼리부터 먼저 실행

```sql
SELECT count(*) FROM orders;
SELECT count(*) FROM order_item;
SELECT count(*) FROM product;
SELECT count(*) FROM member;

SELECT status, count(*) FROM orders GROUP BY status;
SELECT status, count(*) FROM product GROUP BY status;
SELECT grade, count(*) FROM member GROUP BY grade;
```

이걸 먼저 봐야 planner 선택이 왜 그런지 해석할 수 있다.

### 3. 통계와 vacuum 상태 확인

```sql
SELECT relname, n_live_tup, n_dead_tup, last_vacuum, last_autovacuum, last_analyze, last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('member', 'product', 'orders', 'order_item', 'category');
```

### 4. 인덱스 사용 현황 확인

```sql
SELECT schemaname, relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE relname IN ('member', 'product', 'orders', 'order_item', 'category')
ORDER BY idx_scan DESC;
```

### 5. 슬로우 쿼리 후보를 운영 관점으로 확장

가능하면 `pg_stat_statements`도 같이 본다.

```sql
SELECT query, calls, total_exec_time, mean_exec_time, rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

### 6. 다음 연습 주제

- partial index 연습: `last_fence_token > 0`
- covering index 연습: `product_id, quantity, unit_price`
- pagination 연습: `OFFSET` vs keyset
- 집계 연습: `GROUP BY + date_trunc + percentile_cont`
- 조인 연습: `orders -> order_item -> product`
- 재귀 연습: `category` tree

## 권장 진행 순서

1. 쉬운 단건/목록 조회부터 plan 읽는 습관을 만든다.
2. `OFFSET` 문제와 keyset pagination을 비교한다.
3. `order_item` 집계로 큰 테이블 스캔 문제를 본다.
4. 3개 테이블 조인으로 join order를 해석한다.
5. partial/composite/covering index를 직접 추가해 본다.
6. 일부러 나쁜 쿼리로 망가뜨린 뒤 다시 고쳐 본다.

## 마무리

이 문서는 `query-burst`의 현재 엔티티 및 인덱스 구조를 기준으로 PostgreSQL 성능 연습을 하기 위한 가이드다. 핵심은 "인덱스를 외우는 것"이 아니라 "왜 planner가 그렇게 판단했는지 읽는 것"에 있다.

## Deep Dive 확장 주제

경력직 백엔드 개발자가 시니어급 쿼리 역량으로 올라가려면, 단순히 "느린 쿼리를 빠르게 만드는 법"을 넘어서 아래 질문에 답할 수 있어야 한다.

- 이 쿼리가 왜 이 실행 계획을 택했는가
- 이 인덱스가 읽기에는 유리하지만 쓰기에는 어떤 비용을 만드는가
- 지금의 병목이 SQL 자체인지, 통계인지, 스토리지인지, 메모리인지
- 현재 한 번 빠른 쿼리가 아니라 데이터가 10배 늘어도 버틸 구조인가

아래 주제들은 그 수준까지 가기 위한 연습 영역이다.

### 1. Cardinality Estimation과 통계 해석

목표:

- planner가 왜 틀린 예측을 했는지 읽을 수 있어야 한다.
- `actual rows`와 `estimated rows` 차이를 보고 통계 문제를 의심할 수 있어야 한다.

연습 포인트:

- 특정 상태값이나 지역값이 한쪽으로 치우친 경우 plan이 어떻게 달라지는지 본다.
- `ANALYZE` 전후 실행 계획이 바뀌는지 비교한다.
- `n_distinct`, MCV, histogram이 planner 판단에 어떤 영향을 주는지 확인한다.

확인 쿼리:

```sql
SELECT attname, n_distinct, most_common_vals, most_common_freqs, histogram_bounds
FROM pg_stats
WHERE tablename = 'orders'
  AND attname IN ('status', 'ordered_at');
```

실전 감각:

- 시니어는 plan만 보는 것이 아니라, 잘못된 row estimate가 downstream join 선택을 망치고 있는지까지 본다.

### 2. Join Order와 Join Method 통제 감각

목표:

- `Nested Loop`, `Hash Join`, `Merge Join` 중 왜 이 방식이 선택됐는지 읽는다.
- 어떤 테이블을 먼저 줄여야 하는지 판단한다.

연습 포인트:

- `orders -> order_item -> product`와 `product -> order_item -> orders`의 중간 rows 차이를 비교한다.
- 기간 조건을 먼저 적용했을 때와 나중에 적용했을 때 비용 차이를 본다.
- 조인 키 인덱스가 있어도 join order가 잘못되면 왜 느린지 확인한다.

비교 실험:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.category_id, sum(oi.quantity * oi.unit_price)
FROM orders o
JOIN order_item oi ON oi.order_id = o.id
JOIN product p ON p.id = oi.product_id
WHERE o.ordered_at >= now() - interval '30 days'
  AND o.status = 'PAID'
GROUP BY p.category_id;
```

```sql
EXPLAIN (ANALYZE, BUFFERS)
WITH recent_orders AS (
    SELECT id
    FROM orders
    WHERE ordered_at >= now() - interval '30 days'
      AND status = 'PAID'
)
SELECT p.category_id, sum(oi.quantity * oi.unit_price)
FROM recent_orders ro
JOIN order_item oi ON oi.order_id = ro.id
JOIN product p ON p.id = oi.product_id
GROUP BY p.category_id;
```

실전 감각:

- 시니어는 "조인한다"가 아니라 "어느 단계에서 얼마나 줄이고, 어디서 폭증하는가"를 본다.

### 3. Composite Index 설계 감각

목표:

- 복합 인덱스를 "있으면 좋다" 수준이 아니라, 필터/정렬/조인 순서까지 고려해서 설계한다.

핵심 질문:

- equality 조건이 먼저인가
- range 조건이 어디에 오는가
- `ORDER BY`까지 커버해야 하는가
- 같은 컬럼 집합이라도 순서가 바뀌면 왜 완전히 다른가

연습 포인트:

- `(status, ordered_at)` vs `(ordered_at, status)`
- `(member_id, ordered_at)` vs `(ordered_at, member_id)`
- `(product_id, created_at)` vs `(created_at, product_id)`

실전 감각:

- 시니어는 인덱스를 추가하기 전에 "이 인덱스가 어떤 쿼리 패턴 집합을 먹여 살리는가"를 먼저 본다.

### 4. Covering Index와 Heap Fetch 최적화

목표:

- index scan을 탔다고 끝이 아니라 heap access가 얼마나 남는지 본다.
- `Index Only Scan`이 가능한 구조를 의도적으로 설계한다.

연습 포인트:

- `order_item(product_id, quantity, unit_price)` covering index가 실제로 heap fetch를 얼마나 줄이는지 확인한다.
- `SELECT *`일 때와 projection을 좁혔을 때 차이를 비교한다.
- visibility map 상태에 따라 index only scan이 완전하지 않을 수 있음을 확인한다.

확인 쿼리:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT oi.product_id, oi.quantity, oi.unit_price
FROM order_item oi
WHERE oi.product_id = 987654321;
```

실전 감각:

- 시니어는 "인덱스를 탔다"가 아니라 "테이블을 얼마나 덜 읽었는가"까지 확인한다.

### 5. Partial Index 설계와 운영성 쿼리 분리

목표:

- 전체 데이터를 위한 범용 인덱스와, 운영/점검성 조회를 위한 선택적 인덱스를 구분한다.

연습 포인트:

- `last_fence_token > 0` 같은 조건이 일부 데이터에만 적용될 때 partial index를 만든다.
- `status = 'PAID'` 같이 매우 자주 보는 hot subset 전용 인덱스를 검토한다.

비교 실험:

```sql
CREATE INDEX CONCURRENTLY idx_product_last_fence_token
ON product (last_fence_token DESC)
WHERE last_fence_token > 0;
```

실전 감각:

- 시니어는 "모든 검색을 위해 큰 인덱스 하나"보다 "정말 자주 보는 subset용 작은 인덱스"가 더 가치 있을 때를 안다.

### 6. Window Function과 대량 정렬 제어

목표:

- window function이 멋진 SQL 문법이지만, 실제로는 큰 sort와 memory pressure를 만든다는 걸 이해한다.

연습 포인트:

- `row_number`, `rank`, `sum() over (...)`를 `order_item`에 적용해 본다.
- `work_mem`을 조절했을 때 external sort가 사라지는지 확인한다.
- 동일 결과를 `DISTINCT ON` 또는 pre-aggregation으로 바꿨을 때 비교한다.

비교 실험:

```sql
SELECT DISTINCT ON (oi.order_id)
       oi.order_id, oi.product_id, oi.unit_price
FROM order_item oi
WHERE oi.created_at >= now() - interval '30 days'
ORDER BY oi.order_id, oi.unit_price DESC;
```

실전 감각:

- 시니어는 window function을 쓸 수 있는지보다, 그 비용이 허용 가능한지 먼저 판단한다.

### 7. Aggregation 전략과 Pre-Aggregation 판단

목표:

- `GROUP BY`를 빨리 돌리는 것뿐 아니라, 언제 원본 테이블 직접 집계를 포기해야 하는지 판단한다.

연습 포인트:

- 최근 7일, 30일, 180일 집계 비용 차이를 비교한다.
- `date_trunc` 기반 집계가 얼마나 비싼지 본다.
- 특정 지표는 materialized view나 summary table이 맞는지 판단한다.

질문:

- 이 집계는 요청 시점에 실시간이어야 하는가
- 분 단위 정확도가 필요한가
- 배치/스트리밍 집계로 분리하는 편이 맞는가

실전 감각:

- 시니어는 느린 집계를 튜닝만 하지 않고, "이걸 원본에서 매번 집계하는 게 맞는가"를 먼저 묻는다.

### 8. Pagination 전략의 구조적 선택

목표:

- `LIMIT/OFFSET` 문제를 이해하고, keyset pagination으로 넘어갈 기준을 세운다.

연습 포인트:

- 동일한 목록 쿼리에 대해 page 1, page 100, page 10000을 비교한다.
- 정렬 컬럼이 유니크하지 않을 때 tie-breaker 컬럼이 왜 필요한지 확인한다.

예시:

```sql
SELECT o.id, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
  AND (o.ordered_at, o.id) < (TIMESTAMP '2026-04-01 00:00:00', 999999999)
ORDER BY o.ordered_at DESC, o.id DESC
LIMIT 50;
```

실전 감각:

- 시니어는 페이지네이션을 UI 기능이 아니라, 데이터 접근 전략으로 본다.

### 9. Concurrency와 Locking 읽기

목표:

- 빠른 조회만이 아니라, 동시성 상황에서 어떤 대기가 생기고 어떤 update 패턴이 위험한지 읽는다.

연습 포인트:

- `SELECT ... FOR UPDATE`가 어떤 범위를 잠그는지 본다.
- 재고 차감 시 update 경쟁이 생기면 어떤 대기가 발생하는지 본다.
- fencing token 컬럼이 update 경합과 어떤 관계가 있는지 생각해 본다.

확인 쿼리:

```sql
SELECT pid, wait_event_type, wait_event, query
FROM pg_stat_activity
WHERE state <> 'idle';
```

실전 감각:

- 시니어는 느린 쿼리와 막힌 쿼리를 구분한다.
- execution time이 아니라 wait time이 본질인 상황을 읽어야 한다.

### 10. 운영에서 보는 시각: Top Query, Top Table, Top Index

목표:

- 개별 SQL 튜닝을 넘어서 시스템 레벨에서 어디가 병목인지 볼 수 있어야 한다.

연습 포인트:

- `pg_stat_statements`로 total time 상위 쿼리 확인
- `pg_stat_user_tables`로 dead tuple과 analyze 상태 확인
- `pg_stat_user_indexes`로 안 쓰는 인덱스와 자주 쓰는 인덱스 확인

실전 감각:

- 시니어는 느린 쿼리 한 개를 고치는 사람보다, 전체 워크로드의 병목 구조를 읽는 사람에 가깝다.

## 시니어급으로 가기 위한 권장 학습 방식

### 1. 한 번에 많이 보지 말고, 같은 쿼리를 여러 각도에서 본다

한 쿼리로 아래를 모두 연습한다.

- 원본 plan
- projection 축소
- predicate 변경
- composite index 추가
- partial index 추가
- keyset pagination 전환
- CTE 또는 서브쿼리로 먼저 줄이기

### 2. 쿼리 자체와 데이터 분포를 같이 본다

SQL만 보고 판단하면 한계가 있다. 항상 같이 봐야 한다.

- row count
- 상태값 분포
- skew 여부
- 최근 데이터 쏠림 여부

### 3. Before/After를 숫자로 남긴다

문장으로 "빨라졌다"가 아니라, 아래를 기록해야 실력이 쌓인다.

- planning time
- execution time
- shared read/hit
- temp read/write
- rows estimate 오차

### 4. 인덱스 추가보다 삭제 판단도 연습한다

시니어급 역량은 인덱스를 많이 만드는 능력이 아니다.

- 중복 인덱스인가
- 거의 안 쓰는데 쓰기 비용만 키우는가
- 특정 기능 종료 후 남아 있는 인덱스인가

### 5. 결국은 "왜"를 설명할 수 있어야 한다

최종 목표는 아래를 말로 설명할 수 있는 상태다.

- 왜 이 plan이 나왔는가
- 왜 이 인덱스가 필요한가
- 왜 이 인덱스는 오히려 해로운가
- 왜 이 쿼리는 SQL 수정이 답이고 인덱스가 답이 아닌가
- 왜 지금은 쿼리 튜닝이 아니라 집계 구조 변경이 필요한가

## 추천 실전 과제

### 과제 1. 주문 목록 API를 SQL 레벨에서 재설계

조건:

- 회원 기준 최근 주문 조회
- 상태 필터 optional
- 최신순 정렬
- 무한 스크롤

목표:

- offset 방식 초안 작성
- keyset 방식으로 재작성
- 필요한 composite index 설계
- tie-breaker 포함

### 과제 2. 최근 30일 매출 대시보드 최적화

조건:

- 카테고리별 매출
- 지역별 매출
- 등급별 평균 주문 금액

목표:

- 원본 쿼리 작성
- plan 분석
- index와 쿼리 재작성
- 최종적으로 summary table이 필요한지 판단

### 과제 3. fencing token 운영 점검 쿼리 설계

조건:

- 최근에 token이 갱신된 상품 목록
- 비정상적으로 token 증가가 큰 상품
- stock과 token 관계 이상치 조회

목표:

- partial index 설계
- 운영성 조회 쿼리와 비즈니스 조회 쿼리의 인덱스 분리

### 과제 4. 최악의 쿼리 고치기

아래 안티패턴을 일부러 만들고 plan을 비교한다.

- `SELECT *`
- `%LIKE%`
- 함수 적용 predicate
- 큰 OFFSET
- `DISTINCT` 남발
- join 전에 filtering 안 함

## 최종 목표

이 문서의 연습을 끝내면 단순히 "쿼리를 잘 짠다" 수준이 아니라 아래 역량을 목표로 한다.

- 실행 계획을 읽고 병목 원인을 설명할 수 있다.
- 인덱스를 설계할 때 읽기/쓰기 비용을 같이 판단할 수 있다.
- 대용량 조인과 집계에서 먼저 줄여야 할 지점을 찾을 수 있다.
- pagination, aggregation, 운영성 조회를 각각 다른 문제로 분리해서 다룰 수 있다.
- SQL 튜닝으로 끝낼지, 데이터 구조를 바꿀지, 집계 테이블을 둘지 결정할 수 있다.
