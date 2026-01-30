# D-Day 서비스 - Application / Web / Exception 레이어 구현 및 Jackson 버그 수정

**날짜**: 2026-01-30
**모듈**: `apps/d-day/d-day-service`, `libs/core-web`, `libs/common`

---

## 1. Application 레이어 구현

### 신규 파일

| 파일 | 설명 |
|------|------|
| `application/SpecialDaySyncService.java` | Nager.Date API → DB 동기화. `syncByYear(year, countryCode)` 메서드 제공. 중복 방지(`existsByCountryCodeAndDateAndName`) |
| `application/SpecialDayService.java` | 오늘 특별한 날 조회 + 다음 D-Day 계산. 사용자 timezone 기준 오늘 날짜 산출 |
| `application/dto/TodayResult.java` | 조회 결과 record DTO. `SpecialDayItem`, `UpcomingItem` 내부 record 포함 |

### Repository 변경

- `SpecialDayRepository`에 `existsByCountryCodeAndDateAndName` 메서드 추가 (동기화 중복 방지)

---

## 2. Web 레이어 구현

### 신규 파일

| 파일 | 설명 |
|------|------|
| `web/controller/SpecialDayController.java` | `GET /api/v1/special-days/today?countryCode=KR&timezone=UTC` |
| `web/dto/TodayResponse.java` | API 응답 record DTO. `TodayResult` → `TodayResponse` 변환 |

---

## 3. Exception 레이어 구현

### 신규 파일

| 파일 | 설명 |
|------|------|
| `exception/SpecialDayErrorCode.java` | `SD-001` 잘못된 국가코드, `SD-002` 잘못된 타임존, `SD-003` 동기화 실패 |
| `exception/SpecialDayException.java` | `CoreException` 상속 |
| `exception/SpecialDayExceptionHandler.java` | `@RestControllerAdvice` 전역 예외 처리 |

---

## 4. 초기 데이터 동기화

### 신규 파일

| 파일 | 설명 |
|------|------|
| `config/SpecialDayDataInitializer.java` | `ApplicationRunner` 구현. 앱 시작 시 현재 연도 KR 공휴일 자동 동기화. 실패해도 앱 기동 정상 진행 |

---

## 5. 버그 수정: Lombok @Builder + record 호환성

### 증상
- `NoSuchFieldError` 발생 (초기 추정)
- record에 Lombok `@Builder`를 사용하면 Java 25 + Lombok 조합에서 문제 발생

### 수정
- `TodayResult`, `TodayResponse`에서 `@Builder` 제거
- `TodayResult.builder()...build()` → `new TodayResult(...)` 생성자 호출로 변경
- record는 canonical constructor가 이미 있으므로 `@Builder` 불필요

---

## 6. 버그 수정: Jackson 버전 불일치 (NoSuchFieldError)

### 증상
```
NoSuchFieldError: Class tools.jackson.databind.SerializationFeature does not have member field
'tools.jackson.databind.SerializationFeature WRITE_DATES_AS_TIMESTAMPS'
```

### 근본 원인
- `jackson-datatype-jsr310:3.0.0-rc2`가 `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`를 참조
- `jackson-databind:3.0.3`에서 해당 필드가 `DateTimeFeature`로 이동됨
- jsr310 3.0.3 릴리스가 아직 존재하지 않아 버전 매칭 불가

### 기존 상태
- `libs/common/JsonUtils.java`에 workaround가 있었으나, 이는 커스텀 `JsonMapper`에만 적용
- Spring MVC의 `HttpMessageConverter`는 자체 ObjectMapper를 사용하므로 Controller 응답 직렬화 시 여전히 에러 발생

### 수정

**`libs/core-web` - `JacksonConfig.java` 신규 생성**

```java
@Configuration
public class JacksonConfig implements WebMvcConfigurer {
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // LocalDate, LocalDateTime, LocalTime에 명시적 포맷터 지정
        // → jsr310 rc2의 버그 있는 serializer 대신 databind 내장 serializer 사용
        converters.addFirst(new JacksonJsonHttpMessageConverter(mapper));
    }
}
```

- `configureMessageConverters`로 커스텀 `JsonMapper`를 최우선 MessageConverter로 등록
- `LocalDate/LocalDateTime/LocalTime` serializer를 `tools.jackson.databind.ext.javatime.ser.*`에서 가져와 명시적 포맷터 지정
- jsr310 rc2의 `JSR310FormattedSerializerBase` → `SerializationFeature` 참조 경로를 완전 우회

### 향후
- `jackson-datatype-jsr310:3.0.3` 릴리스 시 `libs/common/build.gradle` 버전 업데이트 후 `JacksonConfig` workaround 제거 가능
