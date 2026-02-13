---
name: resilience
description: 고가용성 및 장애 복구 전략 점검
---

## 작업

$ARGUMENTS로 지정된 코드를 대상으로 서비스 안정성 및 장애 복구 전략을 점검합니다.

### 점검 대상

- `$ARGUMENTS`가 클래스명이면: 해당 클래스의 장애 대응 패턴 분석
- `$ARGUMENTS`가 디렉토리면: 해당 경로 내 전체 파일 분석
- `$ARGUMENTS`가 없으면: 현재 변경된 파일 대상 분석

### 점검 관점

#### 1. Circuit Breaker
- Resilience4j `@CircuitBreaker` 적용 여부
- fallback 메서드 존재 및 적절성
- 임계값 설정 (failureRateThreshold, slowCallRateThreshold)
- 상태 전이 설정 (CLOSED → OPEN → HALF_OPEN)
- 외부 서비스 호출(Feign, RestTemplate) 에 Circuit Breaker 미적용 여부

#### 2. 재시도 (Retry)
- `@Retry` 적용 여부 및 재시도 횟수 적정성
- 멱등성이 보장되지 않는 연산에 Retry 적용 위험
- Exponential Backoff 적용 여부
- 재시도 대상 예외 필터링 (`retryExceptions`, `ignoreExceptions`)

#### 3. Bulkhead / Rate Limiter
- 스레드 격리(`@Bulkhead`) 적용 여부
- 핵심 API와 비핵심 API 간 리소스 분리
- Rate Limiter 설정 적정성

#### 4. 예외 처리
- `@RestControllerAdvice` 전역 예외 핸들러 누락 여부
- 예외 삼킴(catch 후 무시) 패턴
- 적절한 HTTP 상태 코드 반환 여부
- 도메인 예외와 시스템 예외 구분 여부

#### 5. Kafka / 메시징
- Dead Letter Queue(DLQ) 처리 로직 존재 여부
- Consumer 실패 시 재처리 전략
- Offset 커밋 전략 (at-least-once 보장)
- Outbox 패턴의 재발행 로직 점검
- 메시지 역직렬화 실패 처리

#### 6. 타임아웃 / 연결 관리
- 외부 호출 타임아웃 설정 여부 (connectTimeout, readTimeout)
- DB 커넥션 풀 설정 적정성
- Redis 연결 실패 시 fallback 존재 여부

### 출력 형식

```markdown
## 장애 복구 전략 점검 결과

### 요약
- 분석 대상: [클래스/패키지명]
- 심각도 분포: Critical N건 / Warning N건 / Info N건
- 전체 평가: (Resilient / Partially Resilient / Fragile)

---

### Critical (즉시 개선 필요)

#### [C-1] 외부 호출에 Circuit Breaker 미적용
- **위치**: `ClassName.java:45`
- **문제**: Feign Client 호출 시 Circuit Breaker 없이 직접 호출
- **영향**: 외부 서비스 장애 시 연쇄 장애(Cascading Failure) 발생
- **해결**:
```java
// Before
@FeignClient(name = "restaurant-service")
public interface RestaurantClient {
    @GetMapping("/api/v1/restaurants/{id}")
    RestaurantResponse getRestaurant(@PathVariable Long id);
}

// After
@CircuitBreaker(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
public RestaurantResponse getRestaurant(Long id) {
    return restaurantClient.getRestaurant(id);
}
```

### Warning (개선 권장)

#### [W-1] 제목
- **위치**: `ClassName.java:78`
- **문제**: 설명
- **영향**: 예상 장애 시나리오
- **해결**: 개선 코드 또는 설정

### Info (참고)

#### [I-1] 제목
- **위치**: `ClassName.java:100`
- **내용**: 설명 및 제안
```

### 사용 예시

```
/resilience WaitingFacade            # Facade의 외부 호출 장애 대응 점검
/resilience event/                   # Kafka Consumer/Producer 안정성 점검
/resilience infrastructure/          # Feign Client 장애 전파 방지 점검
/resilience                          # 현재 변경 파일 대상 분석
```
