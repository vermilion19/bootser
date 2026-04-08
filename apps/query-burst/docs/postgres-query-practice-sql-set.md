# Query Burst PostgreSQL Practice SQL Set

## 목적

이 문서는 `query-burst` 스키마 기준으로 바로 복붙해서 실행할 수 있는 실습용 SQL 세트다.

원칙:

- 먼저 원본 쿼리를 실행한다.
- `EXPLAIN (ANALYZE, BUFFERS)` 결과를 저장한다.
- 이후 인덱스 추가 또는 쿼리 재작성 버전을 비교한다.

## 0. 준비 쿼리

### 테이블 크기와 분포 확인

```sql
SELECT count(*) FROM orders;
SELECT count(*) FROM order_item;
SELECT count(*) FROM product;
SELECT count(*) FROM member;
SELECT count(*) FROM category;

SELECT status, count(*) FROM orders GROUP BY status ORDER BY count(*) DESC;
SELECT status, count(*) FROM product GROUP BY status ORDER BY count(*) DESC;
SELECT grade, count(*) FROM member GROUP BY grade ORDER BY count(*) DESC;
SELECT region, count(*) FROM member GROUP BY region ORDER BY count(*) DESC LIMIT 20;
```

### 통계와 vacuum 상태 확인

```sql
SELECT relname,
       n_live_tup,
       n_dead_tup,
       last_vacuum,
       last_autovacuum,
       last_analyze,
       last_autoanalyze
FROM pg_stat_user_tables
WHERE relname IN ('member', 'product', 'orders', 'order_item', 'category')
ORDER BY relname;
```

### 인덱스 사용 현황 확인

```sql
SELECT schemaname,
       relname,
       indexrelname,
       idx_scan,
       idx_tup_read,
       idx_tup_fetch
FROM pg_stat_user_indexes
WHERE relname IN ('member', 'product', 'orders', 'order_item', 'category')
ORDER BY idx_scan DESC, relname, indexrelname;
```

## 1. Plan Reading 기본 세트

### 1-1. 회원의 최근 주문 조회

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.member_id, o.status, o.total_amount, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
ORDER BY o.ordered_at DESC
LIMIT 50;
```

비교:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.*
FROM orders o
WHERE o.member_id = 123456789
ORDER BY o.ordered_at DESC
LIMIT 50;
```

### 1-2. 상품 필터 + 정렬

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name, p.price, p.stock
FROM product p
WHERE p.category_id = 100
  AND p.status = 'ON_SALE'
  AND p.price BETWEEN 10000 AND 50000
ORDER BY p.price ASC
LIMIT 100;
```

### 1-3. 인덱스를 망가뜨리는 나쁜 버전

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name, p.price, p.stock
FROM product p
WHERE p.category_id = 100
  AND lower(p.status::text) = 'on_sale'
  AND p.price + 0 BETWEEN 10000 AND 50000
ORDER BY p.price ASC
LIMIT 100;
```

## 2. Pagination 세트

### 2-1. OFFSET

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
ORDER BY o.ordered_at DESC
OFFSET 100000 LIMIT 50;
```

### 2-2. Keyset

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.ordered_at
FROM orders o
WHERE o.member_id = 123456789
  AND (o.ordered_at, o.id) < (TIMESTAMP '2026-04-01 00:00:00', 999999999)
ORDER BY o.ordered_at DESC, o.id DESC
LIMIT 50;
```

### 2-3. 인덱스 후보

```sql
CREATE INDEX CONCURRENTLY idx_orders_member_ordered_at_id_desc
ON orders (member_id, ordered_at DESC, id DESC);
```

## 3. Aggregation 세트

### 3-1. 최근 30일 상태별 주문 집계

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.status, count(*) AS cnt, sum(o.total_amount) AS amount
FROM orders o
WHERE o.ordered_at >= now() - interval '30 days'
GROUP BY o.status
ORDER BY amount DESC;
```

### 3-2. 최근 30일 상품별 매출 집계

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT oi.product_id,
       sum(oi.quantity) AS qty,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
WHERE oi.created_at >= now() - interval '30 days'
GROUP BY oi.product_id
ORDER BY revenue DESC
LIMIT 100;
```

### 3-3. 인덱스 후보

```sql
CREATE INDEX CONCURRENTLY idx_order_item_created_at_product_id
ON order_item (created_at, product_id);
```

### 3-4. 특정 상품의 일별 판매 추이

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT date_trunc('day', oi.created_at) AS day,
       sum(oi.quantity) AS qty,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM order_item oi
WHERE oi.product_id = 987654321
  AND oi.created_at >= now() - interval '90 days'
GROUP BY date_trunc('day', oi.created_at)
ORDER BY day;
```

## 4. Join 세트

### 4-1. 최근 30일 카테고리별 매출

```sql
EXPLAIN (ANALYZE, BUFFERS)
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

### 4-2. 먼저 줄인 버전

```sql
EXPLAIN (ANALYZE, BUFFERS)
WITH recent_orders AS (
    SELECT id
    FROM orders
    WHERE ordered_at >= now() - interval '30 days'
      AND status = 'PAID'
)
SELECT p.category_id,
       sum(oi.quantity * oi.unit_price) AS revenue
FROM recent_orders ro
JOIN order_item oi ON oi.order_id = ro.id
JOIN product p ON p.id = oi.product_id
GROUP BY p.category_id
ORDER BY revenue DESC
LIMIT 20;
```

### 4-3. 인덱스 후보

```sql
CREATE INDEX CONCURRENTLY idx_order_item_order_id_product_id
ON order_item (order_id, product_id);

CREATE INDEX CONCURRENTLY idx_orders_status_ordered_at_id
ON orders (status, ordered_at, id);
```

## 5. 통계 집계 세트

### 5-1. 회원 등급별 평균 주문 금액과 P90

```sql
EXPLAIN (ANALYZE, BUFFERS)
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

### 5-2. 지역 + 등급 + 가입기간 필터

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT m.region, m.grade, count(*) AS cnt
FROM member m
WHERE m.grade = 'VIP'
  AND m.region = 'SEOUL'
  AND m.created_at >= now() - interval '1 year'
GROUP BY m.region, m.grade;
```

### 5-3. 인덱스 후보

```sql
CREATE INDEX CONCURRENTLY idx_member_region_grade_created_at
ON member (region, grade, created_at);
```

## 6. Recursive CTE 세트

### 6-1. 카테고리 트리 + 상품 수

```sql
EXPLAIN (ANALYZE, BUFFERS)
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

## 7. Fencing Token 운영성 쿼리 세트

### 7-1. 최근 token 사용 상품 조회

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.stock, p.last_fence_token, p.updated_at
FROM product p
WHERE p.last_fence_token > 0
ORDER BY p.last_fence_token DESC
LIMIT 100;
```

### 7-2. 이상치 조회

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id,
       p.stock,
       p.last_fence_token,
       p.updated_at
FROM product p
WHERE p.last_fence_token > 100000
ORDER BY p.last_fence_token DESC;
```

### 7-3. partial index 후보

```sql
CREATE INDEX CONCURRENTLY idx_product_last_fence_token
ON product (last_fence_token DESC)
WHERE last_fence_token > 0;
```

## 8. Window Function 세트

### 8-1. 주문별 최고가 상품 찾기

```sql
EXPLAIN (ANALYZE, BUFFERS)
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

### 8-2. DISTINCT ON 비교

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT DISTINCT ON (oi.order_id)
       oi.order_id,
       oi.product_id,
       oi.unit_price
FROM order_item oi
WHERE oi.created_at >= now() - interval '30 days'
ORDER BY oi.order_id, oi.unit_price DESC;
```

## 9. EXISTS vs JOIN DISTINCT 세트

### 9-1. EXISTS

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name
FROM product p
WHERE EXISTS (
    SELECT 1
    FROM order_item oi
    WHERE oi.product_id = p.id
      AND oi.created_at >= now() - interval '7 days'
);
```

### 9-2. JOIN DISTINCT

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT DISTINCT p.id, p.name
FROM product p
JOIN order_item oi ON oi.product_id = p.id
WHERE oi.created_at >= now() - interval '7 days';
```

## 10. 안티패턴 세트

### 10-1. 함수 적용 predicate

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders o
WHERE date_trunc('day', o.ordered_at) = current_date;
```

개선:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders o
WHERE o.ordered_at >= current_date
  AND o.ordered_at < current_date + interval '1 day';
```

### 10-2. 앞 와일드카드 LIKE

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM member m
WHERE m.email LIKE '%gmail.com';
```

### 10-3. 정렬 인덱스 없는 Top N

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.id, o.total_amount
FROM orders o
ORDER BY o.total_amount DESC
LIMIT 100;
```

### 10-4. OR 남발

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM product p
WHERE p.status = 'ON_SALE'
   OR p.price < 1000
   OR p.stock = 0;
```

## 11. 운영 진단 세트

### 11-1. 상위 슬로우 쿼리

```sql
SELECT query, calls, total_exec_time, mean_exec_time, rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

### 11-2. 활성 세션과 wait event

```sql
SELECT pid, usename, state, wait_event_type, wait_event, query
FROM pg_stat_activity
WHERE state <> 'idle'
ORDER BY query_start;
```

## 12. 실습 후 반드시 적을 것

- 왜 이 plan이 나왔는가
- 병목은 scan, join, sort, aggregate, lock 중 무엇인가
- 인덱스로 푸는 게 맞는가, SQL 재작성으로 푸는 게 맞는가
- 지금 빨라져도 데이터 10배에서 유지될 구조인가
- 읽기 성능 향상 대비 쓰기 비용 증가는 감당 가능한가

## 관련 문서

- [postgres-query-practice-guide.md](C:\Users\NCand\Documents\bootser\apps\query-burst\docs\postgres-query-practice-guide.md)
- [postgres-query-roadmap.md](C:\Users\NCand\Documents\bootser\apps\query-burst\docs\postgres-query-roadmap.md)
