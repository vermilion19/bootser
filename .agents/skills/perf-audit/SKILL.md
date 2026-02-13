---
name: perf-audit
description: 성능 병목 지점 분석 및 최적화 제안
---

## 작업

$ARGUMENTS로 지정된 코드를 대규모 트래픽 환경 관점에서 분석하여 성능 병목 지점을 찾고 최적화 방안을 제안합니다.

### 점검 대상

- `$ARGUMENTS`가 클래스명이면: 해당 클래스 성능 분석
- `$ARGUMENTS`가 디렉토리면: 해당 경로 내 전체 파일 분석
- `$ARGUMENTS`가 없으면: 현재 변경된 파일 대상 분석

### 점검 관점

#### 1. JPA / DB 접근
- **N+1 쿼리**: `@OneToMany`, `@ManyToOne` 관계에서 Lazy Loading으로 인한 N+1 발생 여부
- **불필요한 조회**: 사용하지 않는 컬럼까지 SELECT 하는 경우 (Projection 미사용)
- **인덱스 미활용**: WHERE, ORDER BY, JOIN 조건에 인덱스가 없거나 타지 않는 쿼리
- **벌크 연산 누락**: 반복문 내 단건 INSERT/UPDATE 대신 Batch 처리 필요 여부
- **트랜잭션 범위**: 트랜잭션이 불필요하게 넓거나 긴 경우

#### 2. 객체 생성 / 메모리
- **불필요한 객체 생성**: 루프 내 반복 생성, 매번 new하는 포맷터/패턴 등
- **스트림 남용**: 대용량 컬렉션에서 불필요한 중간 연산
- **컬렉션 초기 용량**: 크기를 알 수 있는 경우 초기 capacity 미지정
- **String 연결**: 루프 내 `+` 연산 대신 `StringBuilder` 필요 여부

#### 3. 알고리즘 / 루프
- **O(n²) 이상의 중첩 루프**: 대체 가능한 자료구조(Map, Set) 제안
- **중복 연산**: 캐싱 가능한 반복 계산
- **불필요한 정렬/변환**: 이미 정렬된 데이터 재정렬 등

#### 4. 동시성 / 캐시
- **캐시 미적용**: 반복 조회되는 데이터에 Redis Cache-Aside 미적용
- **분산 락 범위**: 락 범위가 넓어 처리량 저하
- **스레드 블로킹**: 동기 호출로 인한 스레드 점유

#### 5. 외부 호출
- **동기 외부 호출**: 비동기/이벤트로 전환 가능한 외부 서비스 호출
- **타임아웃 미설정**: Feign, RestTemplate 등 타임아웃 없는 호출
- **CircuitBreaker 미적용**: 장애 전파 가능성

### 출력 형식

```markdown
## 성능 분석 결과

### 요약
- 분석 대상: [클래스/패키지명]
- 심각도 분포: Critical N건 / Warning N건 / Info N건

---

### Critical (즉시 개선 필요)

#### [C-1] N+1 쿼리 발생
- **위치**: `ClassName.java:123`
- **문제**: 설명
- **영향**: 대기열 100건 조회 시 101회 쿼리 발생
- **해결**:
```java
// Before
repository.findAll();

// After
@Query("SELECT w FROM Waiting w JOIN FETCH w.restaurant")
List<Waiting> findAllWithRestaurant();
```

### Warning (개선 권장)

#### [W-1] 제목
- **위치**: `ClassName.java:45`
- **문제**: 설명
- **영향**: 예상 성능 영향
- **해결**: 개선 코드

### Info (참고)

#### [I-1] 제목
- **위치**: `ClassName.java:78`
- **내용**: 설명 및 제안
```

### 사용 예시

```
/perf-audit WaitingService          # 서비스 로직 성능 점검
/perf-audit repository/             # 쿼리 및 DB 접근 로직 최적화
/perf-audit WaitingFacade deep      # 외부 호출 포함 심층 분석
/perf-audit                         # 현재 변경 파일 대상 분석
```
