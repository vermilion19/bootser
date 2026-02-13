---
name: anti-pattern
description: 시니어 관점에서의 '나쁜 코드' 탐지 및 개선
---

## 작업

$ARGUMENTS로 지정된 코드에서 대규모 환경에서 문제가 될 수 있는 안티패턴을 탐지하고 시니어 수준의 대안을 제시합니다.

### 탐지 대상

- `$ARGUMENTS`가 클래스명/메서드명이면: 해당 코드 분석
- `$ARGUMENTS`가 디렉토리면: 해당 경로 내 전체 파일 분석
- `$ARGUMENTS`가 없으면: 현재 변경된 파일 대상 분석

### 탐지 관점

#### 1. DB / 데이터 접근
- 루프 내 DB 호출 (Loop-Query)
- N+1 쿼리 미인지 상태
- 트랜잭션 범위 과다 (Long Transaction)
- 불필요한 전체 조회 후 메모리 필터링
- 엔티티를 API 응답에 직접 노출

#### 2. 예외 처리
- 예외 삼키기 (catch 후 무시 또는 로그만 출력)
- 과도한 상위 예외 catch (`catch (Exception e)`)
- 비즈니스 로직에 예외를 흐름 제어로 사용
- 의미 없는 예외 래핑 (wrap 후 정보 손실)

#### 3. 동시성 / 공유 자원
- 공유 가변 상태 무방비 접근
- `synchronized` 남용 (성능 병목)
- 분산 환경에서 로컬 캐시/상태 의존
- ThreadLocal 누수 (반환 누락)

#### 4. 설계 / 구조
- God Class (과도한 책임 집중)
- Feature Envy (다른 객체 데이터에 지나친 의존)
- Primitive Obsession (원시 타입 남용, VO 미사용)
- 순환 의존 (Circular Dependency)
- Service 레이어에 도메인 로직 유출 (Anemic Domain Model)

#### 5. 성능
- 불필요한 객체 생성 반복
- String 연결에 `+` 연산 루프 사용
- 스트림 내 블로킹 I/O 호출
- 캐시 가능한 데이터의 반복 조회

#### 6. 보안
- 사용자 입력 미검증
- SQL/JPQL에 문자열 연결로 파라미터 삽입
- 민감 정보 로그 출력
- 하드코딩된 시크릿/패스워드

### 출력 형식

```markdown
## 안티패턴 탐지 결과

### 요약
- 분석 대상: [클래스/패키지명]
- 심각도 분포: Critical N건 / Warning N건 / Info N건

---

### Critical (즉시 제거 필요)

#### [C-1] Loop-Query: 루프 내 DB 호출
- **위치**: `OrderService.java:67`
- **패턴**: 주문 목록을 순회하며 건별로 상품 조회
- **영향**: 주문 100건 → 101회 쿼리, 트래픽 증가 시 DB 커넥션 고갈
- **개선**:
```java
// Before (Anti-pattern)
for (Order order : orders) {
    Product product = productRepository.findById(order.getProductId());
}

// After
List<Long> productIds = orders.stream().map(Order::getProductId).toList();
Map<Long, Product> productMap = productRepository.findAllById(productIds)
    .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
```

### Warning (개선 권장)

#### [W-1] 제목
- **위치**: `ClassName.java:라인`
- **패턴**: 안티패턴명
- **영향**: 예상 문제
- **개선**: 대안 코드

### Info (참고)

#### [I-1] 제목
- **내용**: 설명 및 제안

---

### 안티패턴 요약표

| # | 패턴명 | 위치 | 심각도 | 카테고리 |
|---|--------|------|--------|----------|
| C-1 | Loop-Query | Service:67 | Critical | DB |
| W-1 | Exception Swallowing | Handler:23 | Warning | 예외 |
```

### 사용 예시

```
/anti-pattern src/main/java/service/    # 서비스 레이어 전체 안티패턴 탐지
/anti-pattern async-processing-logic    # 비동기 처리 로직 점검
/anti-pattern WaitingFacade             # 특정 클래스 안티패턴 분석
/anti-pattern                           # 현재 변경 파일 대상 분석
```
