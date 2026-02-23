# 데이터베이스 면접 핵심 정리

## 1. 관계형 데이터베이스 (RDBMS) 기본 개념

### 키(Key)의 종류

| 키 | 설명 |
|----|------|
| **슈퍼키** | 유일성을 만족하는 속성의 집합 |
| **후보키** | 유일성 + 최소성을 만족하는 키 |
| **기본키 (PK)** | 후보키 중 대표로 선택한 키. NULL 불가, 중복 불가 |
| **대체키** | 후보키 중 기본키가 아닌 키 |
| **외래키 (FK)** | 다른 테이블의 기본키를 참조하는 키 |
| **복합키** | 두 개 이상의 컬럼으로 구성된 키 |

> **자연키 vs 대리키**: 자연키(주민번호, 이메일)는 비즈니스 의미를 가지지만 변경 가능성이 있다. 대리키(Auto Increment, UUID)는 비즈니스 의미 없지만 불변이다. 실무에서는 **대리키 사용을 권장**.

---

## 2. 정규화 (Normalization)

데이터 중복을 최소화하고 **이상 현상(Anomaly)**을 방지하기 위한 설계 과정.

### 이상 현상

| 이상 | 설명 |
|------|------|
| **삽입 이상** | 불필요한 데이터를 함께 삽입해야 함 |
| **갱신 이상** | 일부만 수정하면 데이터 불일치 발생 |
| **삭제 이상** | 필요한 데이터까지 함께 삭제됨 |

### 정규화 단계

| 단계 | 조건 | 핵심 |
|------|------|------|
| **1NF** | 모든 속성이 원자값 | 반복 그룹, 다중값 제거 |
| **2NF** | 1NF + 부분 함수 종속 제거 | 복합키의 일부에만 종속된 속성 분리 |
| **3NF** | 2NF + 이행 함수 종속 제거 | A→B→C에서 A→C 간접 종속 제거 |
| **BCNF** | 모든 결정자가 후보키 | 3NF보다 엄격 |

### 반정규화 (Denormalization)

- 성능 향상을 위해 **의도적으로 중복**을 허용
- 읽기 성능 향상, 조인 감소
- 쓰기 복잡도 증가, 데이터 일관성 관리 필요
- **사용 시점**: 읽기가 압도적으로 많은 경우, 조인 비용이 큰 경우

> **면접 포인트**: "정규화를 항상 해야 하나요?" → 아니다. OLTP에서는 정규화가 유리하지만, OLAP이나 읽기 위주 서비스에서는 반정규화가 성능에 유리할 수 있다. 트레이드오프를 이해하고 상황에 맞게 선택해야 한다.

---

## 3. 트랜잭션 (Transaction)

### ACID 속성

| 속성 | 설명 | 보장 메커니즘 |
|------|------|---------------|
| **Atomicity** (원자성) | 전부 성공 또는 전부 실패 | Undo Log |
| **Consistency** (일관성) | 트랜잭션 전후로 무결성 유지 | 제약 조건, 트리거 |
| **Isolation** (격리성) | 동시 트랜잭션이 서로 영향 없음 | Lock, MVCC |
| **Durability** (지속성) | 커밋된 데이터는 영구 보존 | Redo Log, WAL |

### 트랜잭션 격리 수준 (Isolation Level)

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read | 성능 |
|-----------|-----------|-------------------|-------------|------|
| **READ UNCOMMITTED** | O | O | O | 최고 |
| **READ COMMITTED** | X | O | O | 높음 |
| **REPEATABLE READ** | X | X | O (InnoDB에서는 X) | 보통 |
| **SERIALIZABLE** | X | X | X | 최저 |

### 이상 현상 설명

```
Dirty Read       : 커밋되지 않은 다른 트랜잭션의 변경 데이터를 읽음
                   → 해당 트랜잭션이 롤백하면 잘못된 데이터를 읽은 것

Non-repeatable Read : 같은 쿼리를 두 번 실행했는데 결과 값이 다름
                      → 다른 트랜잭션이 UPDATE/DELETE 후 커밋

Phantom Read     : 같은 쿼리를 두 번 실행했는데 행 수가 다름
                   → 다른 트랜잭션이 INSERT 후 커밋
```

> **MySQL InnoDB 기본 격리 수준**: REPEATABLE READ. Next-Key Lock으로 Phantom Read도 방지한다.
>
> **PostgreSQL 기본 격리 수준**: READ COMMITTED. MVCC 기반으로 동작한다.

---

## 4. Lock

### Lock 종류

| Lock | 설명 |
|------|------|
| **Shared Lock (S)** | 읽기 잠금. 다른 S Lock과 호환, X Lock과 비호환 |
| **Exclusive Lock (X)** | 쓰기 잠금. 모든 Lock과 비호환 |
| **Intent Lock** | 테이블 레벨에서 행 잠금 의도를 표시 (IS, IX) |
| **Record Lock** | 인덱스 레코드에 거는 잠금 |
| **Gap Lock** | 인덱스 레코드 사이 간격에 거는 잠금 (Phantom Read 방지) |
| **Next-Key Lock** | Record Lock + Gap Lock (InnoDB REPEATABLE READ) |

### Lock 호환 매트릭스

|  | S Lock | X Lock |
|--|--------|--------|
| **S Lock** | 호환 | 비호환 |
| **X Lock** | 비호환 | 비호환 |

### 낙관적 Lock vs 비관적 Lock

| 항목 | 낙관적 (Optimistic) | 비관적 (Pessimistic) |
|------|---------------------|---------------------|
| 전략 | 충돌이 드물다고 가정 | 충돌이 빈번하다고 가정 |
| 구현 | Version 컬럼, CAS | SELECT FOR UPDATE |
| 장점 | 동시성 높음 | 데이터 정합성 확실 |
| 단점 | 충돌 시 재시도 필요 | 동시성 낮음, 데드락 위험 |
| 적합한 경우 | 읽기 많고 충돌 적은 경우 | 쓰기 많고 충돌 잦은 경우 |

```sql
-- 비관적 Lock (PostgreSQL)
SELECT * FROM orders WHERE id = 1 FOR UPDATE;

-- 낙관적 Lock (JPA @Version)
UPDATE orders SET status = 'DONE', version = version + 1
WHERE id = 1 AND version = 3;
```

---

## 5. MVCC (Multi-Version Concurrency Control)

### 핵심 개념

- 데이터를 수정할 때 **이전 버전을 유지**하여, 읽기 작업이 쓰기 작업을 블로킹하지 않음
- **읽기는 Lock 없이** 수행 → 동시성 향상

### PostgreSQL의 MVCC

```
1. 각 행에 xmin(생성 트랜잭션 ID), xmax(삭제 트랜잭션 ID) 저장
2. UPDATE = DELETE(기존 행 xmax 설정) + INSERT(새 행 생성)
3. 각 트랜잭션은 자신의 Snapshot 기준으로 가시성(Visibility) 판단
4. 오래된 버전은 VACUUM이 정리
```

### MySQL InnoDB의 MVCC

```
1. Undo Log에 이전 버전 데이터 보관
2. 각 행에 숨겨진 트랜잭션 ID, 롤백 포인터 존재
3. READ COMMITTED: 매 쿼리마다 새 Snapshot
4. REPEATABLE READ: 트랜잭션 시작 시점의 Snapshot 고정
```

> **면접 포인트**: "MVCC가 있으면 Lock이 필요 없나요?" → 아니다. 읽기-쓰기 간 충돌은 MVCC로 해결하지만, 쓰기-쓰기 간 충돌은 여전히 Lock이 필요하다.

---

## 6. 인덱스 (Index)

### B-Tree 인덱스

- 가장 범용적인 인덱스 구조
- **균형 트리**: 모든 리프 노드가 같은 깊이
- 범위 검색, 정렬, 동등 비교 모두 효율적
- 시간 복잡도: O(log N)

```
                    [50]
                  /      \
           [20, 30]      [70, 80]
          /   |   \     /   |   \
       [10] [25] [35] [60] [75] [90]   ← 리프 노드 (실제 데이터 포인터)
```

### B+Tree 인덱스 (실제 RDBMS 사용)

- B-Tree의 변형. **데이터는 리프 노드에만** 저장
- 리프 노드끼리 **연결 리스트**로 연결 → 범위 검색에 유리
- 내부 노드는 키만 저장 → 더 많은 키를 담을 수 있음 → 트리 높이 감소

### 인덱스 종류

| 종류 | 설명 |
|------|------|
| **Clustered Index** | 테이블 데이터 자체가 인덱스 순서로 정렬 (테이블당 1개) |
| **Non-clustered Index** | 별도의 인덱스 구조. 리프에 데이터 위치 포인터 저장 |
| **Covering Index** | 쿼리에 필요한 모든 컬럼이 인덱스에 포함 (테이블 접근 불필요) |
| **Composite Index** | 여러 컬럼으로 구성된 인덱스 |
| **Unique Index** | 중복 값 불허 |
| **Hash Index** | 해시 기반. 동등 비교만 가능 (범위 검색 불가) |
| **Full-text Index** | 텍스트 검색용 |

### Clustered vs Non-clustered Index

| 항목 | Clustered | Non-clustered |
|------|-----------|---------------|
| 데이터 정렬 | 인덱스 순서 = 데이터 순서 | 별도 구조 |
| 테이블당 개수 | 1개 | 여러 개 |
| 리프 노드 | 실제 데이터 행 | 데이터 위치 포인터 |
| 범위 검색 | 매우 빠름 | 상대적 느림 |
| INSERT 성능 | 느림 (정렬 유지) | 빠름 |

### 인덱스가 동작하지 않는 경우

```sql
-- 1. 인덱스 컬럼에 함수/연산 적용
WHERE YEAR(created_at) = 2024     -- X (인덱스 무시)
WHERE created_at >= '2024-01-01'  -- O (인덱스 사용)

-- 2. 복합 인덱스에서 선두 컬럼 누락 (Leftmost Prefix 위반)
INDEX (a, b, c) → WHERE b = 1    -- X
INDEX (a, b, c) → WHERE a = 1    -- O
INDEX (a, b, c) → WHERE a = 1 AND b = 2  -- O

-- 3. LIKE 검색에서 앞에 와일드카드
WHERE name LIKE '%kim'   -- X (Full Scan)
WHERE name LIKE 'kim%'   -- O (인덱스 사용)

-- 4. 부정 조건
WHERE status != 'ACTIVE'  -- 인덱스 비효율적

-- 5. OR 조건 (각 컬럼에 개별 인덱스 필요)
WHERE a = 1 OR b = 2     -- Index Merge 또는 Full Scan

-- 6. 암묵적 타입 변환
WHERE phone = 01012345678  -- 문자열 컬럼에 숫자 비교 → 인덱스 무시
```

> **면접 포인트**: "인덱스를 많이 만들면 좋은 건가요?" → 아니다. 인덱스는 읽기를 빠르게 하지만 쓰기(INSERT, UPDATE, DELETE) 시 인덱스도 갱신해야 하므로 성능이 저하된다. 또한 저장 공간도 차지한다.

---

## 7. 실행 계획 (EXPLAIN)

### PostgreSQL EXPLAIN 주요 항목

```sql
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'test@test.com';
```

| 항목 | 설명 |
|------|------|
| **Seq Scan** | 전체 테이블 순차 스캔 |
| **Index Scan** | 인덱스로 조건 검색 후 테이블 접근 |
| **Index Only Scan** | 인덱스만으로 결과 반환 (Covering Index) |
| **Bitmap Index Scan** | 인덱스로 비트맵 생성 후 테이블 접근 |
| **Nested Loop** | 중첩 반복문 방식 조인 (소량 데이터에 유리) |
| **Hash Join** | 해시 테이블 생성 후 조인 (동등 조인에 유리) |
| **Merge Join** | 정렬된 데이터 병합 조인 (정렬된 대량 데이터에 유리) |
| **cost** | 예상 비용 (startup..total) |
| **rows** | 예상 행 수 |
| **actual time** | 실제 소요 시간 (ANALYZE 사용 시) |

> **면접 포인트**: "Seq Scan이 항상 나쁜 건가요?" → 아니다. 테이블이 작거나 대부분의 행을 읽어야 하는 경우 Seq Scan이 Index Scan보다 효율적일 수 있다. 옵티마이저가 비용 기반으로 판단한다.

---

## 8. 조인 (Join)

### 조인 종류

```sql
-- INNER JOIN: 양쪽 모두 일치하는 행
SELECT * FROM A INNER JOIN B ON A.id = B.a_id;

-- LEFT (OUTER) JOIN: 왼쪽 테이블 전체 + 오른쪽 일치하는 행
SELECT * FROM A LEFT JOIN B ON A.id = B.a_id;

-- RIGHT (OUTER) JOIN: 오른쪽 테이블 전체 + 왼쪽 일치하는 행
SELECT * FROM A RIGHT JOIN B ON A.id = B.a_id;

-- FULL (OUTER) JOIN: 양쪽 모두 전체
SELECT * FROM A FULL JOIN B ON A.id = B.a_id;

-- CROSS JOIN: 카테시안 곱 (M x N)
SELECT * FROM A CROSS JOIN B;

-- SELF JOIN: 자기 자신과 조인
SELECT e.name, m.name AS manager
FROM employees e JOIN employees m ON e.manager_id = m.id;
```

### 조인 알고리즘

| 알고리즘 | 방식 | 적합한 경우 |
|----------|------|-------------|
| **Nested Loop** | 외부 테이블 행마다 내부 테이블 스캔 | 소량 + 인덱스 존재 |
| **Hash Join** | 작은 테이블로 해시 생성, 큰 테이블 순회 | 동등 조인, 인덱스 없는 경우 |
| **Sort Merge Join** | 양쪽 정렬 후 병합 | 이미 정렬된 대량 데이터 |

---

## 9. N+1 문제

### 정의

연관 관계의 데이터를 조회할 때, 부모 1번 + 자식 N번 = **총 N+1번의 쿼리**가 발생하는 문제.

### 예시

```java
// N+1 발생 (Lazy Loading)
List<Restaurant> restaurants = restaurantRepository.findAll(); // 1번
for (Restaurant r : restaurants) {
    r.getMenus().size(); // 식당마다 메뉴 쿼리 → N번
}
```

### 해결 방법

| 방법 | 설명 |
|------|------|
| **Fetch Join** | `JOIN FETCH`로 한 번에 조회 |
| **@EntityGraph** | JPA에서 조인 전략 지정 |
| **@BatchSize** | IN 절로 묶어서 조회 (100개씩 등) |
| **@Fetch(SUBSELECT)** | 서브쿼리로 한 번에 조회 |
| **Projection** | 필요한 컬럼만 DTO로 직접 조회 |

```java
// Fetch Join으로 해결
@Query("SELECT r FROM Restaurant r JOIN FETCH r.menus")
List<Restaurant> findAllWithMenus();
```

> **면접 포인트**: "Fetch Join의 단점은?" → 페이징이 불가능하다 (메모리에서 페이징). 1:N 관계에서 데이터 뻥튀기(카테시안 곱)가 발생할 수 있어 `DISTINCT`가 필요하다.

---

## 10. SQL 튜닝 기본

### 느린 쿼리 개선 체크리스트

```
1. WHERE 절 컬럼에 적절한 인덱스가 있는가?
2. SELECT * 대신 필요한 컬럼만 조회하는가?
3. 서브쿼리를 JOIN으로 변환할 수 있는가?
4. 인덱스가 실제로 사용되고 있는가? (EXPLAIN 확인)
5. 불필요한 정렬(ORDER BY)이 있는가?
6. LIMIT 없이 대량 데이터를 조회하지 않는가?
7. 적절한 파티셔닝이 필요한 수준의 데이터인가?
```

### 서브쿼리 vs JOIN

```sql
-- 서브쿼리 (비효율적일 수 있음)
SELECT * FROM orders
WHERE user_id IN (SELECT id FROM users WHERE status = 'ACTIVE');

-- JOIN으로 변환 (일반적으로 더 효율적)
SELECT o.* FROM orders o
INNER JOIN users u ON o.user_id = u.id
WHERE u.status = 'ACTIVE';
```

### 페이지네이션 최적화

```sql
-- OFFSET 방식 (느림 - 대량 데이터에서)
SELECT * FROM posts ORDER BY id DESC LIMIT 20 OFFSET 10000;
-- 앞의 10000건을 읽고 버림

-- 커서 방식 (빠름 - No Offset)
SELECT * FROM posts WHERE id < 9000 ORDER BY id DESC LIMIT 20;
-- 인덱스로 바로 접근
```

---

## 11. 파티셔닝 (Partitioning)

### 수평 파티셔닝 (Horizontal)

- 행(Row) 기준으로 분할
- 같은 스키마, 데이터만 나눔

| 방식 | 설명 | 예시 |
|------|------|------|
| **Range** | 값 범위 기준 | 날짜별 (2024-01, 2024-02...) |
| **List** | 특정 값 목록 기준 | 지역별 (서울, 부산, 대구) |
| **Hash** | 해시 함수 결과 기준 | user_id % 4 |

### 수직 파티셔닝 (Vertical)

- 열(Column) 기준으로 분할
- 자주 접근하는 컬럼과 아닌 컬럼 분리
- 예: 사용자 테이블에서 프로필 이미지 LOB를 별도 테이블로 분리

### 샤딩 (Sharding)

- 수평 파티셔닝을 **여러 서버**로 분산
- 각 샤드가 독립적인 데이터베이스 인스턴스
- 샤딩 키 선택이 매우 중요 (데이터 균등 분배)

> **면접 포인트**: "파티셔닝과 샤딩의 차이?" → 파티셔닝은 단일 DB 내에서 테이블을 분할하는 것, 샤딩은 여러 DB 서버에 데이터를 분산하는 것이다. 샤딩은 수평 확장이 가능하지만 크로스 샤드 조인, 분산 트랜잭션 등의 복잡성이 추가된다.

---

## 12. 레플리케이션 (Replication)

### Master-Slave (Primary-Replica)

```
    [Client]
       │
  ┌────┴────┐
  │ Write   │ Read
  ▼         ▼
[Master] → [Slave 1]
         → [Slave 2]
         → [Slave 3]
   복제 (Replication)
```

- **Master**: 쓰기(INSERT, UPDATE, DELETE) 담당
- **Slave**: 읽기(SELECT) 담당
- **읽기 부하 분산** 가능

### 동기 vs 비동기 복제

| 항목 | 동기 (Synchronous) | 비동기 (Asynchronous) |
|------|--------------------|-----------------------|
| 일관성 | 강한 일관성 | 최종 일관성 (지연 있음) |
| 성능 | 느림 (Slave 응답 대기) | 빠름 |
| 가용성 | Slave 장애 시 쓰기 블로킹 | Slave 장애에 영향 없음 |

> **면접 포인트**: "복제 지연(Replication Lag) 문제는 어떻게 해결하나요?" → 쓰기 직후 읽기는 Master에서 수행, 읽기 전용 트랜잭션은 Slave에서 수행. 또는 반동기(Semi-sync) 복제를 사용하여 최소 하나의 Slave에 전달됨을 보장.

---

## 13. CAP 이론

분산 시스템에서 다음 3가지를 **동시에 모두 만족할 수 없다**.

| 속성 | 설명 |
|------|------|
| **Consistency** | 모든 노드가 같은 시점에 같은 데이터를 봄 |
| **Availability** | 모든 요청이 (성공/실패와 상관없이) 응답을 받음 |
| **Partition Tolerance** | 네트워크 분할이 발생해도 시스템이 동작 |

### 조합

| 유형 | 설명 | 예시 |
|------|------|------|
| **CP** | 일관성 + 분할 내성 | MongoDB, HBase, Redis Cluster |
| **AP** | 가용성 + 분할 내성 | Cassandra, DynamoDB, CouchDB |
| **CA** | 일관성 + 가용성 (분할 없는 경우) | 단일 노드 RDBMS |

> 실제 분산 환경에서 네트워크 분할은 불가피하므로 **P는 기본**, CP 또는 AP 중 선택하게 된다.

### BASE vs ACID

| ACID (RDBMS) | BASE (NoSQL) |
|-------------|-------------|
| Atomicity | **B**asically **A**vailable |
| Consistency | **S**oft state |
| Isolation | **E**ventual consistency |
| Durability | |

---

## 14. Redis

### 데이터 타입

| 타입 | 설명 | 사용 예시 |
|------|------|-----------|
| **String** | 단순 키-값 | 캐시, 세션, 카운터 |
| **Hash** | 필드-값 쌍의 집합 | 사용자 프로필 |
| **List** | 순서 있는 문자열 목록 | 메시지 큐, 최근 활동 |
| **Set** | 중복 없는 문자열 집합 | 태그, 고유 방문자 |
| **Sorted Set** | 점수 기반 정렬 Set | 랭킹, 대기열 순번 |
| **Stream** | 로그 구조 데이터 | 이벤트 스트리밍 |

### 캐시 전략

| 전략 | 읽기/쓰기 | 동작 |
|------|-----------|------|
| **Cache-Aside** | 읽기 | 캐시 미스 → DB 조회 → 캐시 저장 |
| **Read-Through** | 읽기 | 캐시가 자동으로 DB 조회 |
| **Write-Through** | 쓰기 | 캐시와 DB 동시 쓰기 |
| **Write-Behind** | 쓰기 | 캐시에 먼저 쓰고, 나중에 DB 반영 |
| **Write-Around** | 쓰기 | DB에만 쓰고, 캐시는 읽기 시 갱신 |

### 캐시 문제

| 문제 | 설명 | 해결 |
|------|------|------|
| **Cache Stampede** | 캐시 만료 시 동시 DB 요청 폭주 | 분산 락, TTL 랜덤화 |
| **Cache Penetration** | 존재하지 않는 데이터 반복 요청 | Bloom Filter, null 캐싱 |
| **Cache Avalanche** | 대량의 캐시가 동시에 만료 | TTL 분산, 다단계 캐시 |
| **Cache Inconsistency** | DB와 캐시 데이터 불일치 | TTL 설정, 이벤트 기반 갱신 |

### Redis 영속성

| 방식 | 설명 | 특징 |
|------|------|------|
| **RDB** | 특정 시점 스냅샷 저장 | 빠른 복구, 데이터 유실 가능 |
| **AOF** | 모든 쓰기 명령을 로그로 저장 | 데이터 안전, 파일 크기 큼 |
| **RDB + AOF** | 둘 다 사용 | 권장 방식 |

---

## 15. NoSQL

### RDBMS vs NoSQL

| 항목 | RDBMS | NoSQL |
|------|-------|-------|
| 스키마 | 엄격한 스키마 | 유연한 스키마 (Schema-less) |
| 확장 | 수직 확장 (Scale-up) | 수평 확장 (Scale-out) |
| 트랜잭션 | ACID 보장 | BASE (최종 일관성) |
| 조인 | 지원 | 제한적 또는 미지원 |
| 적합한 경우 | 정형 데이터, 복잡한 관계 | 대량 데이터, 빠른 변경, 유연한 구조 |

### NoSQL 유형

| 유형 | 설명 | 예시 |
|------|------|------|
| **Key-Value** | 단순 키-값 저장 | Redis, DynamoDB |
| **Document** | JSON/BSON 문서 저장 | MongoDB, CouchDB |
| **Column-Family** | 컬럼 기반 저장 | Cassandra, HBase |
| **Graph** | 노드와 관계 저장 | Neo4j, Amazon Neptune |

---

## 16. 데이터베이스 커넥션 풀 (Connection Pool)

### 왜 필요한가?

- DB 연결 생성은 **비용이 큼** (TCP 3-way handshake, 인증 등)
- 매 요청마다 연결 생성/해제하면 성능 저하
- 미리 일정 수의 커넥션을 만들어 놓고 **재사용**

### HikariCP (Spring Boot 기본)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # 최대 커넥션 수
      minimum-idle: 5           # 최소 유휴 커넥션
      connection-timeout: 30000 # 커넥션 획득 대기 시간 (ms)
      idle-timeout: 600000      # 유휴 커넥션 유지 시간
      max-lifetime: 1800000     # 커넥션 최대 수명
```

### 적정 풀 크기

```
최적 풀 크기 = (코어 수 * 2) + 유효 디스크 수
```

> **면접 포인트**: "커넥션 풀 크기를 너무 크게 하면?" → 커넥션마다 메모리를 사용하고, DB 서버의 max_connections 제한에 걸릴 수 있다. 또한 컨텍스트 스위칭 오버헤드가 증가한다. 너무 작으면 커넥션 대기(timeout)가 발생한다.

---

## 17. 데드락 (Database Deadlock)

### 발생 예시

```
Transaction A                Transaction B
-----------                  -----------
UPDATE accounts              UPDATE accounts
  SET balance = 100            SET balance = 200
  WHERE id = 1; (Lock)        WHERE id = 2; (Lock)

UPDATE accounts              UPDATE accounts
  SET balance = 200            SET balance = 100
  WHERE id = 2; (대기...)      WHERE id = 1; (대기...)

→ 서로 상대방의 Lock을 기다리며 영원히 블로킹
```

### 예방 방법

1. **일관된 순서로 Lock 획득**: 항상 id 오름차순으로 접근
2. **트랜잭션 시간 최소화**: 불필요한 작업을 트랜잭션 밖으로
3. **적절한 격리 수준 선택**: 필요 이상으로 높은 격리 수준 사용하지 않기
4. **타임아웃 설정**: `innodb_lock_wait_timeout`
5. **낙관적 Lock 사용**: 충돌이 드문 경우 Version 기반 제어

---

## 18. 저장 프로시저 vs ORM

| 항목 | 저장 프로시저 | ORM (JPA 등) |
|------|-------------|-------------|
| 실행 위치 | DB 서버 | 애플리케이션 서버 |
| 성능 | 네트워크 비용 절약, 실행 계획 캐싱 | 추가 추상화 오버헤드 |
| 유지보수 | DB 변경 시 배포 별도 | 코드와 함께 버전 관리 |
| 이식성 | DB 벤더 종속 | DB 독립적 |
| 디버깅 | 어려움 | 상대적 쉬움 |
| 적합한 경우 | 복잡한 배치, 대량 데이터 처리 | 일반 CRUD, 도메인 중심 설계 |

---

## 19. WAL (Write-Ahead Logging)

### 핵심 원리

- 데이터를 변경하기 **전에** 로그를 먼저 디스크에 기록
- 장애 발생 시 로그를 기반으로 **복구(Recovery)** 가능
- PostgreSQL, MySQL InnoDB 모두 WAL 사용

### 복구 과정

```
1. 시스템 장애 발생
2. 재시작 시 WAL(Redo Log) 읽기
3. 커밋된 트랜잭션 → Redo (재실행)
4. 커밋되지 않은 트랜잭션 → Undo (롤백)
5. 데이터 일관성 복구 완료
```

### Checkpoint

- WAL 로그가 계속 쌓이면 복구 시간이 길어짐
- 주기적으로 메모리의 더티 페이지를 디스크에 반영하는 것이 **Checkpoint**
- Checkpoint 이전의 WAL은 안전하게 삭제 가능

---

## 20. 자주 나오는 면접 질문 모음

1. **ACID란 무엇인가요?**
2. **트랜잭션 격리 수준 4가지를 설명하세요**
3. **인덱스의 원리와 B+Tree를 설명하세요**
4. **인덱스가 동작하지 않는 경우는?**
5. **Clustered Index와 Non-clustered Index의 차이는?**
6. **정규화란 무엇이고, 언제 반정규화하나요?**
7. **N+1 문제란 무엇이고 어떻게 해결하나요?**
8. **낙관적 Lock과 비관적 Lock의 차이는?**
9. **MVCC란 무엇인가요?**
10. **DB 데드락이 발생하는 원인과 해결 방법은?**
11. **Redis를 캐시로 사용할 때 주의할 점은?**
12. **Cache Stampede란 무엇이고 어떻게 방지하나요?**
13. **샤딩이란 무엇이고, 샤딩 키를 어떻게 선택하나요?**
14. **레플리케이션과 복제 지연 문제 해결법은?**
15. **CAP 이론을 설명하세요**
16. **커넥션 풀은 왜 필요하고, 적정 크기는?**
17. **RDBMS와 NoSQL의 차이점은?**
18. **실행 계획(EXPLAIN)에서 어떤 것을 확인하나요?**
19. **커서 기반 페이지네이션이 OFFSET보다 나은 이유는?**
20. **WAL(Write-Ahead Logging)이란 무엇인가요?**
