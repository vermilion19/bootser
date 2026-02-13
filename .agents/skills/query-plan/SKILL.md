---
name: query-plan
description: DB 쿼리 실행 계획 분석 및 인덱스 설계
---

## 작업

$ARGUMENTS로 지정된 SQL 또는 JPA 메서드를 분석하여 예상 실행 계획을 추론하고, 인덱스 설계 및 쿼리 튜닝 방안을 제시합니다. PostgreSQL 환경 기준으로 분석합니다.

### 분석 대상

- `$ARGUMENTS`가 메서드명이면: 해당 JPA 메서드의 생성 쿼리 추론 후 분석
- `$ARGUMENTS`가 SQL 파일이면: 파일 내 쿼리 분석
- `$ARGUMENTS`가 스키마 파일이면: 테이블 설계 기반 인덱스 전략 제안
- `$ARGUMENTS`가 없으면: 현재 변경된 Repository 파일 대상 분석

### 분석 관점

#### 1. 쿼리 실행 계획 추론
- JPA 메서드명 → 생성될 SQL 추론
- `@Query` 어노테이션의 JPQL/Native SQL 분석
- WHERE, JOIN, ORDER BY, GROUP BY 절의 인덱스 활용 여부
- 예상 Scan 방식 (Seq Scan / Index Scan / Index Only Scan)

#### 2. 인덱스 설계
- 단일 컬럼 인덱스 vs 복합 인덱스 판단
- 복합 인덱스 컬럼 순서 최적화 (선택도 기반)
- Covering Index 적용 가능 여부
- 부분 인덱스(Partial Index) 활용 제안
- 불필요한 인덱스 제거 (쓰기 성능 저하 방지)

#### 3. 쿼리 튜닝
- Full Table Scan 유발 패턴 (LIKE '%...', 함수 적용 컬럼)
- 암묵적 타입 변환으로 인한 인덱스 무효화
- 서브쿼리 → JOIN 변환 가능 여부
- OFFSET 기반 페이징 → Keyset Pagination 전환 제안
- SELECT * 대신 필요 컬럼만 조회 (Projection)

#### 4. PostgreSQL 특화
- EXPLAIN ANALYZE 예상 결과 추론
- pg_stat_statements 활용 가이드
- VACUUM, ANALYZE 필요성 판단
- 파티셔닝 적용 제안 (대용량 테이블)

### 출력 형식

```markdown
## 쿼리 분석 결과

### 요약
- 분석 대상: [메서드/파일명]
- 관련 테이블: [테이블 목록]
- 심각도 분포: Critical N건 / Warning N건 / Info N건

---

### 추론된 SQL

```sql
SELECT w.* FROM waiting w
WHERE w.user_id = ? AND w.status = ?
ORDER BY w.created_at DESC
```

### 예상 실행 계획

```
Seq Scan on waiting  (예상 비용 높음)
  Filter: (user_id = ? AND status = ?)
  Rows Removed by Filter: ~90%
```

### Critical (즉시 개선 필요)

#### [C-1] Full Table Scan 발생
- **위치**: `WaitingRepository.java:15`
- **쿼리**: `findByUserIdAndStatus(Long userId, WaitingStatus status)`
- **문제**: user_id, status 컬럼에 인덱스 없음
- **영향**: 데이터 증가 시 선형적 성능 저하
- **해결**:
```sql
CREATE INDEX idx_waiting_user_status ON waiting (user_id, status);
```

### 인덱스 설계 제안

| 테이블 | 인덱스명 | 컬럼 | 타입 | 근거 |
|--------|----------|------|------|------|
| waiting | idx_waiting_user_status | (user_id, status) | B-Tree | 조회 빈도 높음 |
| waiting | idx_waiting_restaurant_created | (restaurant_id, created_at DESC) | B-Tree | 정렬 조회 |
```

### 사용 예시

```
/query-plan findByUserIdAndStatus   # 특정 메서드의 쿼리 효율 분석
/query-plan schema.sql              # 스키마 설계를 통한 인덱스 전략 제안
/query-plan WaitingRepository       # Repository 전체 메서드 분석
/query-plan                         # 현재 변경 파일 대상 분석
```
