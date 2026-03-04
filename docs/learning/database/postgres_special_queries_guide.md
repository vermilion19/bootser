# PostgreSQL에서 자주 쓰는 “특수 쿼리” 사용 가이드
CRUD/조인은 익숙하지만 **WITH(CTE), SELECT … FOR UPDATE, RETURNING** 같은 문법이 낯설 때, “언제/왜/어떻게” 쓰는지 **실무 시나리오 중심**으로 정리했습니다.

> 표기 규칙
> - 예시는 이해를 돕기 위한 샘플입니다(테이블/컬럼명은 상황에 맞게 바꾸세요).
> - `BEGIN … COMMIT`은 트랜잭션을 의미합니다. `FOR UPDATE`는 **트랜잭션 안에서** 의미가 큽니다.

---

## 1) WITH (CTE): Common Table Expression
`WITH`는 **쿼리 안에서 “임시로 이름 붙인 결과 집합”**을 만드는 문법입니다.  
가장 흔한 용도는 **가독성/재사용/단계적 계산**이고, 고급 용도로는 **재귀(트리 탐색)**와 **쓰기(INSERT/UPDATE/DELETE) 결과를 다음 단계로 넘기기**가 있습니다.

### 1.1 언제 쓰나 (대표 케이스)
- **쿼리 가독성 개선**: 복잡한 조인/필터/집계를 단계별로 분리하고 싶을 때
- **중간 결과 재사용**: 같은 서브쿼리를 여러 번 쓰는 경우(중복 제거)
- **데이터 정제 파이프라인**: “필터 → 조인 → 집계 → 후처리”를 순서대로 표현
- **재귀 쿼리**: 트리/계층 구조(카테고리, 조직도, 댓글 스레드 등) 탐색
- **쓰기 + 후속 처리**: INSERT/UPDATE/DELETE 결과(특히 `RETURNING`)를 다음 쿼리로 넘김

### 1.2 기본 형태
```sql
WITH cte_name AS (
  SELECT ...
)
SELECT ...
FROM cte_name;
```

#### 예시: 단계별로 쪼개서 읽기 쉽게 만들기
```sql
WITH paid_orders AS (
  SELECT id, user_id, paid_at
  FROM orders
  WHERE paid_at >= now() - interval '7 days'
),
order_totals AS (
  SELECT oi.order_id, sum(oi.price * oi.qty) AS total_amount
  FROM order_items oi
  JOIN paid_orders po ON po.id = oi.order_id
  GROUP BY oi.order_id
)
SELECT po.user_id, count(*) AS orders, sum(ot.total_amount) AS revenue
FROM paid_orders po
JOIN order_totals ot ON ot.order_id = po.id
GROUP BY po.user_id
ORDER BY revenue DESC;
```

**왜 좋나**
- 각 단계의 의미가 명확해지고, 디버깅(중간 CTE만 실행)도 쉬움.

### 1.3 성능/최적화 관점에서 주의할 점
CTE는 편하지만, 무조건 빠르지는 않습니다.
- CTE가 **“중간 결과를 한 번 만들어(물질화) 놓고”** 다음 단계에서 쓰는 형태가 되면,
  - 중간 결과가 크면 비용이 커질 수 있습니다.
- PostgreSQL 12+에서는 **일반(비재귀) CTE가 항상 최적화 장벽(optimization fence)** 은 아니고, 상황에 따라 인라인(inline)될 수 있습니다.
  - 필요하면 `MATERIALIZED` / `NOT MATERIALIZED`로 의도를 명확히 할 수 있습니다.

#### 예시: MATERIALIZED / NOT MATERIALIZED (의도 표현)
```sql
WITH recent AS MATERIALIZED (
  SELECT *
  FROM events
  WHERE created_at >= now() - interval '1 day'
)
SELECT ...
FROM recent
JOIN ...
;
```

- `MATERIALIZED`: “중간 결과를 확정해서 한 번 만들어 두고 쓰겠다” (재사용/일관된 결과가 목적일 때)
- `NOT MATERIALIZED`: “가능하면 인라인해서 최적화되도록” (중간 결과가 크고, 푸시다운 최적화가 중요할 때)

> 실무 팁: “CTE를 썼더니 느려졌다”는 케이스는 보통 **중간 결과가 예상보다 커졌거나, 필터가 뒤로 밀려**서 생깁니다. 이때 `EXPLAIN (ANALYZE, BUFFERS)`로 확인하고, `NOT MATERIALIZED` 또는 서브쿼리로 변경을 고려합니다.

### 1.4 데이터 변경(쓰기) CTE: INSERT/UPDATE/DELETE를 “파이프라인”처럼 연결
CTE 안에는 SELECT뿐 아니라 **INSERT/UPDATE/DELETE**도 넣을 수 있고, 특히 `RETURNING`으로 결과를 다음 단계로 전달할 수 있습니다.

#### 예시: “주문 생성 → 주문 아이템 생성”을 한 번에
```sql
WITH new_order AS (
  INSERT INTO orders (user_id, status)
  VALUES ($1, 'CREATED')
  RETURNING id
)
INSERT INTO order_items (order_id, product_id, qty, price)
SELECT new_order.id, x.product_id, x.qty, x.price
FROM new_order
JOIN (VALUES
  (101, 2, 12000),
  (202, 1, 35000)
) AS x(product_id, qty, price) ON true
RETURNING order_id;
```

**왜 좋나**
- 애플리케이션에서 “INSERT → SELECT id → INSERT …” 왕복을 줄이고
- 트랜잭션 안에서 원자적으로 묶기 쉬움

#### 예시: “삭제 + 아카이브” (DELETE RETURNING을 활용)
```sql
WITH moved AS (
  DELETE FROM sessions
  WHERE expires_at < now()
  RETURNING *
)
INSERT INTO sessions_archive
SELECT * FROM moved;
```

### 1.5 WITH RECURSIVE: 계층/트리 탐색
#### 예시: 카테고리 트리에서 “특정 노드의 모든 조상” 가져오기
```sql
WITH RECURSIVE ancestors AS (
  SELECT id, parent_id, name, 0 AS depth
  FROM categories
  WHERE id = $1

  UNION ALL

  SELECT c.id, c.parent_id, c.name, a.depth + 1
  FROM categories c
  JOIN ancestors a ON a.parent_id = c.id
)
SELECT *
FROM ancestors
ORDER BY depth;
```

**언제 쓰나**
- 조직도(상위 부서), 카테고리(상위 카테고리), 댓글(부모 체인), 메뉴 트리 등

**주의**
- 무한 루프 방지(사이클), depth 제한, 인덱스(parent_id) 중요

---

## 2) SELECT … FOR UPDATE: “읽고 나서 업데이트할 행”을 잠그기
`SELECT … FOR UPDATE`는 **조회한 행에 row-level lock(행 잠금)**을 걸어,
다른 트랜잭션이 그 행을 **수정/삭제**하려고 하면 **기다리게(또는 실패하게)** 만드는 기능입니다.

> 핵심: `FOR UPDATE`는 “조회”처럼 보이지만, 사실상 **동시성 제어 도구**입니다.

### 2.1 언제 쓰나 (대표 케이스)
- “읽고 → 계산하고 → 업데이트”에서 **Lost Update(덮어쓰기)**를 막고 싶을 때
- 재고/잔액/좌석 예약 등 **동시 업데이트**가 빈번하고 정확성이 중요한 도메인
- 작업 큐에서 여러 워커가 **같은 작업을 중복으로 집어가지 않게** 할 때 (`SKIP LOCKED`)

### 2.2 기본 사용 패턴
```sql
BEGIN;

SELECT *
FROM accounts
WHERE id = $1
FOR UPDATE;

UPDATE accounts
SET balance = balance - 100
WHERE id = $1;

COMMIT;
```

**무슨 일이 벌어지나**
- 같은 `id`에 대해 다른 트랜잭션이 UPDATE/DELETE/SELECT FOR UPDATE를 시도하면 **락이 풀릴 때까지 대기**합니다.
- 일반 `SELECT`는 보통 대기하지 않고(스냅샷으로) 읽습니다.

### 2.3 NOWAIT / SKIP LOCKED: “기다릴지/건너뛸지”
#### (1) NOWAIT: 락이 걸려있으면 즉시 에러
```sql
SELECT *
FROM accounts
WHERE id = $1
FOR UPDATE NOWAIT;
```

- “기다리면 안 되는” API에서 유용  
- 앱에서 에러(락 경합)를 잡아서 “잠시 후 재시도” UX를 만들 때

#### (2) SKIP LOCKED: 락 걸린 행은 건너뛰고 다음 행
**작업 큐/배치 워커**에서 매우 자주 씁니다.

```sql
BEGIN;

WITH job AS (
  SELECT id
  FROM jobs
  WHERE status = 'READY'
  ORDER BY created_at
  LIMIT 1
  FOR UPDATE SKIP LOCKED
)
UPDATE jobs j
SET status = 'RUNNING', started_at = now()
FROM job
WHERE j.id = job.id
RETURNING j.*;

COMMIT;
```

**왜 좋나**
- 워커가 여러 대여도, 서로 락 때문에 기다리기보단
- “잠금 안 잡힌 작업”을 가져가서 **중복 처리 없이 병렬 처리** 가능

### 2.4 조인에서 FOR UPDATE: “어느 테이블을 잠글지” 명확히
조인을 걸고 `FOR UPDATE`를 쓰면, 의도치 않게 여러 테이블 행이 잠길 수 있습니다.  
필요하면 `OF`로 대상 테이블을 지정합니다.

```sql
SELECT o.*
FROM orders o
JOIN users u ON u.id = o.user_id
WHERE o.id = $1
FOR UPDATE OF o;  -- orders만 잠금
```

### 2.5 잠금 모드들(간단 정리)
- `FOR UPDATE`: 가장 강함(수정/삭제 방지에 가까움). “이 행을 곧 수정할 거다”에 대응.
- `FOR NO KEY UPDATE`: 키(기본키/유니크키) 변경은 아니고, 일부 컬럼만 변경할 때 더 느슨한 잠금(복잡한 FK 환경에서 도움이 될 수 있음).
- `FOR SHARE` / `FOR KEY SHARE`: 읽기 안정성/외래키 관련 동시성에서 쓰는 경우가 있음.

> 실무에선 대부분 `FOR UPDATE`만으로도 충분합니다.  
> 잠금 경합이 심하고 FK가 복잡할 때만 다른 모드를 고려해도 늦지 않습니다.

### 2.6 주의사항(면접/실무 단골)
- **트랜잭션을 짧게**: 락을 잡아놓고 네트워크 호출/긴 계산을 하면 장애가 나기 쉽습니다.
- **데드락(교착)**: A가 row1→row2 순서로 잠그고, B가 row2→row1 순서로 잠그면 데드락 가능  
  → 항상 **일관된 순서(ORDER BY id)**로 잠그는 습관이 중요.
- `lock_timeout`, `statement_timeout` 같은 타임아웃 정책이 운영에 도움이 됩니다(무한 대기 방지).

---

## 3) RETURNING: INSERT/UPDATE/DELETE 결과를 즉시 돌려받기
`RETURNING`은 DML(INSERT/UPDATE/DELETE) 뒤에 붙여서 **변경된 행(또는 필요한 컬럼)**을 바로 반환합니다.

### 3.1 언제 쓰나 (대표 케이스)
- INSERT 후 생성된 PK/ID를 다시 SELECT하지 않고 바로 받고 싶을 때
- UPDATE 후 변경 결과(최종 값)를 바로 응답해야 할 때
- DELETE 후 삭제된 행을 로깅/아카이브/응답으로 쓰고 싶을 때
- “한 번의 왕복으로” 결과를 가져와 성능/일관성을 높이고 싶을 때

### 3.2 INSERT … RETURNING
```sql
INSERT INTO users (email, name)
VALUES ($1, $2)
RETURNING id, created_at;
```

- `serial/identity`, default 값, 트리거로 세팅된 컬럼 등도 함께 반환 가능

### 3.3 UPDATE … RETURNING
```sql
UPDATE accounts
SET balance = balance - 100
WHERE id = $1
RETURNING id, balance;
```

- 업데이트된 “최종 값”을 반환하므로, API 응답에 바로 쓰기 좋음

#### “업데이트 전 값(old)”도 필요하다면?
`RETURNING`은 기본적으로 “바뀐 후 값”을 돌려줍니다.  
old/new를 같이 원하면 보통 CTE나 FROM-subquery를 씁니다.

```sql
WITH before AS (
  SELECT id, balance AS old_balance
  FROM accounts
  WHERE id = $1
  FOR UPDATE
)
UPDATE accounts a
SET balance = a.balance - 100
FROM before b
WHERE a.id = b.id
RETURNING a.id, b.old_balance, a.balance AS new_balance;
```

### 3.4 DELETE … RETURNING
```sql
DELETE FROM sessions
WHERE id = $1
RETURNING id, user_id, expires_at;
```

### 3.5 UPSERT와 RETURNING: INSERT … ON CONFLICT … DO UPDATE … RETURNING
```sql
INSERT INTO user_settings (user_id, theme)
VALUES ($1, $2)
ON CONFLICT (user_id)
DO UPDATE SET theme = EXCLUDED.theme
RETURNING user_id, theme;
```

- “있으면 업데이트, 없으면 삽입”을 한 번에 처리하면서 최종 결과를 얻음

### 3.6 주의사항
- `RETURNING *`는 편하지만, 행이 크면 네트워크/CPU 비용이 큽니다 → 필요한 컬럼만 반환
- 대량 변경 쿼리에서 `RETURNING`은 결과가 커질 수 있으므로, 사용 목적을 분명히

---

## 4) 자주 쓰는 조합 레시피 (실무형)

### 레시피 A) “재고 차감 + 주문 생성” (락 + returning + 트랜잭션)
**상황**: 동시 주문이 몰려도 재고가 음수가 되면 안 됨.

```sql
BEGIN;

-- 1) 재고 행 잠금
SELECT stock
FROM products
WHERE id = $1
FOR UPDATE;

-- 2) 재고가 충분하면 차감
UPDATE products
SET stock = stock - $2
WHERE id = $1 AND stock >= $2
RETURNING id, stock;

-- 3) (UPDATE가 0행이면 재고 부족) → 앱에서 롤백/에러 처리

-- 4) 주문 생성
INSERT INTO orders (user_id, product_id, qty)
VALUES ($3, $1, $2)
RETURNING id;

COMMIT;
```

**왜 이렇게 하냐**
- `FOR UPDATE`로 재고 행을 잠궈서 동시성 안전
- `UPDATE … WHERE stock >= qty RETURNING`으로 “성공 여부 + 최종 재고”를 한 번에

### 레시피 B) “멀티 워커 작업 큐” (SKIP LOCKED)
**상황**: 워커 여러 대가 READY 작업을 나눠서 처리해야 함(중복 금지).

```sql
BEGIN;

WITH picked AS (
  SELECT id
  FROM jobs
  WHERE status = 'READY'
  ORDER BY id
  LIMIT 1
  FOR UPDATE SKIP LOCKED
)
UPDATE jobs j
SET status = 'RUNNING', started_at = now()
FROM picked p
WHERE j.id = p.id
RETURNING j.*;

COMMIT;
```

**포인트**
- 기다리지 않고(`SKIP LOCKED`) “안 잠긴 작업”을 집어가므로 스케일하기 좋음

### 레시피 C) “쓰기 파이프라인” (WITH + RETURNING)
**상황**: insert 결과를 바로 다음 insert에 사용하고 싶다(애플리케이션 왕복 최소화).

```sql
WITH u AS (
  INSERT INTO users(email) VALUES ($1)
  RETURNING id
)
INSERT INTO profiles(user_id, nickname)
SELECT u.id, $2
FROM u
RETURNING user_id;
```

---

## 5) 빠른 선택 가이드 (언제 뭘 쓰지?)
- **복잡한 쿼리를 읽기 쉽게** 만들고 싶다 → `WITH` (CTE)
- **트리/계층 구조**를 SQL로 풀고 싶다 → `WITH RECURSIVE`
- **읽고 나서 수정할 행의 동시성**을 확실히 잡고 싶다 → `SELECT … FOR UPDATE`
- **락 기다리지 않고 실패/회피**하고 싶다 → `NOWAIT` / `SKIP LOCKED`
- **INSERT/UPDATE/DELETE 결과를 바로 받고 싶다** → `RETURNING`
- **여러 단계의 쓰기를 한 쿼리로** 연결하고 싶다 → `WITH (data-modifying CTE) + RETURNING`

---

## 6) 면접에서 자주 나오는 “한 줄 답변” 템플릿
- **CTE를 왜 쓰나요?**  
  “복잡한 쿼리를 단계적으로 분해해 가독성을 높이고, 필요하면 중간 결과 재사용/재귀/쓰기 파이프라인까지 구성할 수 있어서요.”
- **FOR UPDATE는 왜 쓰나요?**  
  “동시성에서 ‘읽고-수정’ 사이에 다른 트랜잭션이 끼어들어 생기는 lost update를 막기 위해 행 잠금을 겁니다.”
- **SKIP LOCKED는 언제 쓰나요?**  
  “여러 워커가 동시에 작업을 집어가되, 락 때문에 대기하지 않고 분산 처리하려고 작업 큐 패턴에 씁니다.”
- **RETURNING은 왜 좋나요?**  
  “DML 결과를 다시 SELECT하지 않고 즉시 받아서 네트워크 왕복을 줄이고 일관된 결과를 반환할 수 있어요.”

---

## 다음 단계 추천(원하면 더 정리해줄 수 있는 주제)
- 트랜잭션 격리 수준(READ COMMITTED/REPEATABLE READ/SERIALIZABLE)과 “왜 FOR UPDATE가 필요한가”의 연결
- `EXPLAIN (ANALYZE)`로 CTE/조인의 플랜 비교하는 법
- `INSERT … ON CONFLICT`(upsert) 실무 패턴 10선
- 페이징: OFFSET vs Keyset pagination(커서 기반)

