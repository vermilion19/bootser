# D-Day Service Rate Limiting 적용 가이드

## 개요

Admin API의 남용을 방지하기 위해 Resilience4j RateLimiter를 적용했습니다.

---

## 적용 대상

| API | 설명 | 제한 이유 |
|-----|------|----------|
| `POST /api/v1/special-days/admin/sync` | 전체 국가 공휴일 동기화 | 외부 API 호출 + DB 대량 쓰기 |
| `POST /api/v1/special-days/admin/sync/movies` | 영화 개봉일 동기화 | 외부 API 호출 + DB 쓰기 |
| `DELETE /api/v1/special-days/admin/duplicates` | 중복 데이터 삭제 | DB 대량 삭제 작업 |

---

## 구현 방법

### 1. 의존성 추가

```gradle
// apps/d-day/d-day-service/build.gradle
dependencies {
    implementation project(':libs:core-resilience')
}
```

`core-resilience` 모듈에 포함된 라이브러리:
- `spring-cloud-starter-circuitbreaker-resilience4j`
- `spring-boot-starter-aop` (어노테이션 기반 동작)
- `resilience4j-bulkhead`

### 2. RateLimiter 설정

```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      adminApi:
        limit-for-period: 5        # 기간당 허용 요청 수
        limit-refresh-period: 1m   # 제한 초기화 주기 (1분)
        timeout-duration: 0s       # 대기 시간 (0 = 즉시 거부)
```

| 설정 | 값 | 설명 |
|------|-----|------|
| `limit-for-period` | 5 | 1분당 최대 5회 요청 허용 |
| `limit-refresh-period` | 1m | 1분마다 카운터 초기화 |
| `timeout-duration` | 0s | 제한 초과 시 대기 없이 즉시 거부 |

### 3. Controller 적용

```java
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api/v1/special-days/admin")
public class SpecialDayAdminController {

    @PostMapping("/sync")
    @RateLimiter(name = "adminApi")  // 설정 이름과 매칭
    public ApiResponse<SyncResultResponse> syncAll(...) {
        // ...
    }

    @PostMapping("/sync/movies")
    @RateLimiter(name = "adminApi")
    public ApiResponse<MovieSyncResult> syncMovies(...) {
        // ...
    }

    @DeleteMapping("/duplicates")
    @RateLimiter(name = "adminApi")
    public ApiResponse<Map<String, Integer>> removeDuplicates() {
        // ...
    }
}
```

---

## 동작 방식

```
[요청 1] → 허용 (1/5)
[요청 2] → 허용 (2/5)
[요청 3] → 허용 (3/5)
[요청 4] → 허용 (4/5)
[요청 5] → 허용 (5/5)
[요청 6] → 거부 (429 Too Many Requests)
    ↓
  1분 후
    ↓
[요청 7] → 허용 (1/5) - 카운터 초기화
```

---

## 제한 초과 시 응답

```json
HTTP/1.1 429 Too Many Requests

{
  "message": "RateLimiter 'adminApi' does not permit further calls",
  "timestamp": "2025-02-05T12:00:00"
}
```

---

## 커스텀 예외 처리 (선택)

기본 429 응답 대신 커스텀 응답이 필요한 경우:

```java
@RestControllerAdvice
public class RateLimiterExceptionHandler {

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."));
    }
}
```

---

## 모니터링

Actuator 엔드포인트로 RateLimiter 상태 확인 가능:

```bash
GET /actuator/ratelimiters

# 응답 예시
{
  "rateLimiters": {
    "adminApi": {
      "availablePermissions": 3,
      "numberOfWaitingThreads": 0
    }
  }
}
```

`application.yml`에 엔드포인트 노출 추가:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, ratelimiters
```

---

## 설정 변경 가이드

### 더 엄격하게 (분당 2회)

```yaml
resilience4j:
  ratelimiter:
    instances:
      adminApi:
        limit-for-period: 2
        limit-refresh-period: 1m
```

### 더 느슨하게 (분당 20회)

```yaml
resilience4j:
  ratelimiter:
    instances:
      adminApi:
        limit-for-period: 20
        limit-refresh-period: 1m
```

### API별 다른 제한 적용

```yaml
resilience4j:
  ratelimiter:
    instances:
      syncApi:
        limit-for-period: 2
        limit-refresh-period: 1m
      deleteApi:
        limit-for-period: 10
        limit-refresh-period: 1m
```

```java
@PostMapping("/sync")
@RateLimiter(name = "syncApi")  // 분당 2회
public ApiResponse<?> syncAll(...) { }

@DeleteMapping("/duplicates")
@RateLimiter(name = "deleteApi")  // 분당 10회
public ApiResponse<?> removeDuplicates() { }
```

---

## 참고

- [Resilience4j RateLimiter 공식 문서](https://resilience4j.readme.io/docs/ratelimiter)
- [Spring Cloud CircuitBreaker](https://spring.io/projects/spring-cloud-circuitbreaker)
