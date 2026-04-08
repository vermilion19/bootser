# Query Burst PostgreSQL Deep Dive Roadmap

## 목표

이 로드맵의 목표는 다음 수준까지 가는 것이다.

- 실행 계획을 읽고 병목 원인을 설명할 수 있다.
- 인덱스를 설계할 때 읽기/쓰기 비용을 같이 판단할 수 있다.
- 대용량 조인과 집계에서 먼저 줄여야 할 지점을 찾을 수 있다.
- SQL 튜닝으로 끝낼지, 데이터 구조를 바꿀지, 집계 테이블을 둘지 결정할 수 있다.

대상:

- 경력직 백엔드 개발자
- 애플리케이션 개발 경험은 충분하지만 PostgreSQL planner, 통계, 인덱스, 조인, 집계 쪽 deep dive가 부족한 상태

## 운영 원칙

- 매주 SQL을 직접 실행한다.
- 모든 실습은 `EXPLAIN (ANALYZE, BUFFERS)`를 같이 본다.
- Before/After를 숫자로 기록한다.
- "빠르다/느리다"가 아니라 "왜 그런가"를 문장으로 설명한다.

기록 템플릿:

| Query | Before ms | After ms | Buffers 변화 | Rows estimate 오차 | 최적화 방법 | 배운 점 |
|------|-----------:|---------:|---------------|--------------------|-------------|---------|

## Phase 1. Plan Reading Foundation

기간:

- 2주

목표:

- plan의 주요 노드를 읽는다.
- `Seq Scan`, `Index Scan`, `Bitmap Heap Scan`, `Hash Join`, `Sort`, `Aggregate`를 구분한다.
- `actual rows`와 `estimated rows` 차이의 의미를 이해한다.

### Week 1. 단일 테이블과 기본 인덱스

체크리스트:

- [ ] `orders` 최근 주문 조회 쿼리를 실행한다.
- [ ] `product` 카테고리/상태/가격 범위 검색을 실행한다.
- [ ] `SELECT *`와 projection 축소 버전을 비교한다.
- [ ] 같은 쿼리에 함수 적용 predicate를 넣어 plan이 망가지는지 본다.

실습 SQL:

- `orders` 최근 주문 조회
- `product(category_id, status, price)` 검색
- `date_trunc`, `lower`, `+ 0` 같은 안티패턴

주간 목표 질문:

- 왜 이 쿼리는 인덱스를 탔는가
- 왜 이 쿼리는 sort가 추가되는가
- 왜 projection만 줄여도 비용이 달라지는가

### Week 2. 통계와 cardinality estimation

체크리스트:

- [ ] `pg_stats`를 조회한다.
- [ ] `status`, `grade`, `region` 분포를 확인한다.
- [ ] `ANALYZE` 전후 plan 차이를 확인한다.
- [ ] row estimate가 크게 틀리는 쿼리를 하나 찾는다.

실습 SQL:

```sql
SELECT status, count(*) FROM orders GROUP BY status;
SELECT grade, count(*) FROM member GROUP BY grade;
SELECT attname, n_distinct, most_common_vals, most_common_freqs
FROM pg_stats
WHERE tablename IN ('orders', 'member');
```

주간 목표 질문:

- planner는 왜 이 row 수를 예상했는가
- estimate가 틀리면 이후 join 선택이 어떻게 망가지는가

## Phase 2. Index Design and Pagination

기간:

- 2주

목표:

- composite index와 partial index를 설계할 수 있다.
- pagination 전략을 SQL 관점에서 설명할 수 있다.

### Week 3. Composite Index 설계

체크리스트:

- [ ] `(status, ordered_at)`와 `(ordered_at, status)`를 비교한다.
- [ ] `(product_id, created_at)`와 `(created_at, product_id)`가 어떤 쿼리에 맞는지 정리한다.
- [ ] equality, range, order by 순서를 고려해 인덱스를 제안한다.
- [ ] 현재 스키마에 불필요하거나 중복 가능성이 있는 인덱스를 점검한다.

실습 포인트:

- `orders` 최근 30일 상태별 집계
- `order_item` 최근 30일 상품별 매출 집계

주간 목표 질문:

- 복합 인덱스의 컬럼 순서는 왜 중요한가
- 같은 컬럼 집합이라도 순서가 바뀌면 어떤 쿼리가 깨지는가

### Week 4. Pagination Deep Dive

체크리스트:

- [ ] `OFFSET 0`, `OFFSET 10000`, `OFFSET 100000`을 비교한다.
- [ ] keyset pagination 쿼리로 재작성한다.
- [ ] tie-breaker가 없는 경우와 있는 경우를 비교한다.
- [ ] `ORDER BY ordered_at DESC, id DESC`용 인덱스를 설계한다.

실습 포인트:

- 회원별 최근 주문 조회
- 최신순 상품 목록 조회

주간 목표 질문:

- OFFSET은 왜 뒤로 갈수록 비싸지는가
- keyset은 왜 더 안정적인가
- 정렬 컬럼이 유니크하지 않을 때 왜 문제가 생기는가

## Phase 3. Join and Aggregation

기간:

- 3주

목표:

- 대형 테이블 조인에서 join order를 읽는다.
- 집계 쿼리의 병목을 찾아낸다.
- window function과 DISTINCT ON의 차이를 이해한다.

### Week 5. Join Order와 Join Method

체크리스트:

- [ ] `orders -> order_item -> product` 3개 조인을 분석한다.
- [ ] 최근 30일 조건을 먼저 줄인 버전과 비교한다.
- [ ] `Hash Join`, `Nested Loop`, `Merge Join` 케이스를 각각 경험한다.
- [ ] 중간 결과 rows가 어디서 급증하는지 기록한다.

주간 목표 질문:

- 어떤 단계에서 먼저 줄여야 하는가
- 왜 join key 인덱스가 있어도 느릴 수 있는가

### Week 6. Aggregation과 Summary Table 판단

체크리스트:

- [ ] 최근 7일, 30일, 180일 집계를 비교한다.
- [ ] `GROUP BY + date_trunc` 집계 비용을 본다.
- [ ] `percentile_cont`를 포함한 통계 집계를 실행한다.
- [ ] 원본 집계 대신 summary table이 필요한 기준을 정리한다.

주간 목표 질문:

- 이 집계를 요청 시점에 실시간 계산하는 게 맞는가
- 어느 시점부터 materialized view나 summary table이 유리한가

### Week 7. Window Function과 정렬 비용

체크리스트:

- [ ] `row_number()` 쿼리를 실행한다.
- [ ] `DISTINCT ON`으로 동일 목적 쿼리를 작성한다.
- [ ] `work_mem` 변화에 따른 sort spill 여부를 관찰한다.
- [ ] temp read/write가 생기는 plan을 하나 찾는다.

주간 목표 질문:

- window function이 편한데 왜 비쌀 수 있는가
- sort가 병목일 때 SQL 구조 변경으로 풀 수 있는가

## Phase 4. Operational SQL Thinking

기간:

- 2주

목표:

- 운영성 조회, 통계, lock, wait를 같이 본다.
- "느린 쿼리"와 "막힌 쿼리"를 구분한다.
- 전체 워크로드 관점에서 우선순위를 정한다.

### Week 8. Partial Index와 운영성 조회

체크리스트:

- [ ] `last_fence_token > 0` 점검 쿼리를 실행한다.
- [ ] partial index 전후를 비교한다.
- [ ] 운영용 쿼리와 비즈니스 쿼리의 인덱스 요구가 다름을 정리한다.

주간 목표 질문:

- 전체 범용 인덱스보다 작은 partial index가 더 좋은 경우는 언제인가

### Week 9. pg_stat_*와 wait analysis

체크리스트:

- [ ] `pg_stat_statements` 상위 쿼리를 확인한다.
- [ ] `pg_stat_user_tables`, `pg_stat_user_indexes`를 확인한다.
- [ ] `pg_stat_activity`로 wait event를 본다.
- [ ] lock 대기 상황과 단순 느린 스캔 상황을 구분한다.

주간 목표 질문:

- 가장 느린 쿼리와 가장 비싼 쿼리는 같은가
- 실행 시간이 아니라 wait 시간이 본질인 쿼리는 무엇인가

## Phase 5. Senior-Level Synthesis

기간:

- 1주

목표:

- 개별 튜닝이 아니라 구조적 판단을 내린다.
- "인덱스 추가", "SQL 재작성", "집계 구조 변경", "캐시/비동기화" 중 무엇이 맞는지 결정한다.

### Week 10. 최종 과제

체크리스트:

- [ ] 주문 목록 조회를 keyset 기반으로 재설계한다.
- [ ] 최근 30일 매출 대시보드를 최적화한다.
- [ ] fencing token 운영 점검 쿼리를 설계한다.
- [ ] 최악의 쿼리 3개를 골라 근본 원인과 해결책을 정리한다.

최종 산출물:

- 쿼리 10개 이상의 before/after 분석
- 인덱스 설계안
- 삭제 후보 인덱스 검토안
- summary table 또는 materialized view 도입 기준
- 운영 관점 모니터링 포인트

## 시니어급 체크포인트

아래를 말로 설명할 수 있으면 상당히 올라온 상태다.

- 왜 이 실행 계획이 나왔는가
- 이 인덱스는 어떤 쿼리 패턴을 위한 것인가
- 이 인덱스는 왜 쓰기 비용 대비 가치가 없는가
- 이 문제는 SQL 튜닝의 영역인지, 모델링/집계 구조의 영역인지
- 이 쿼리는 지금 빠르지만 데이터가 10배 늘면 왜 깨질 것 같은지

## 병행하면 좋은 주제

- PostgreSQL MVCC
- VACUUM / ANALYZE / autovacuum
- lock / wait event / deadlock
- materialized view
- partitioning
- pg_stat_statements
- connection pool과 트랜잭션 길이

## 관련 문서

- [postgres-query-practice-guide.md](C:\Users\NCand\Documents\bootser\apps\query-burst\docs\postgres-query-practice-guide.md)
