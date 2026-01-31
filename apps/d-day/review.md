# 스펙 리뷰 결과

## 요약

- **전체 평가**: Needs Work
- **예상 복잡도**: Medium-High

스펙의 전체적인 방향은 명확하나, MVP 범위가 너무 넓고 당장 불필요한 기능이 포함되어 있음. FR-7(시간대 변환)이 추가되면서 복잡도가 상승. 핵심 가치("오늘이 무슨 날?")에 집중하되, 시간대 변환은 핵심 차별점이므로 MVP에 포함하되 범위를 한정할 필요 있음.

---

## 간소화 제안

### 제거 권장

| 항목 | 이유 |
|------|------|
| **FR-5 Admin CRUD API 전체** | MVP에서 데이터는 외부 API 동기화 + SQL INSERT로 충분. 관리 화면 없이 시작 |
| **NFR-4 "200+ 국가 지원"** | Nager.Date가 지원하는 국가만 수용하면 됨. 숫자 목표 불필요 |
| **API 2번 (기간 조회)** | MVP 핵심은 "오늘" 조회. 기간 조회는 2차에 추가 |
| **CountryCode VO** | `String countryCode`로 충분. ISO 검증은 외부 API 응답 기준으로 자연스럽게 제한됨 |

### 단순화 권장

| 항목 | 대안 |
|------|------|
| **FR-6 일 1회 스케줄러 동기화** | MVP에서는 연 1회 수동 동기화(CommandLineRunner 또는 API 호출)로 시작. 공휴일 데이터는 연간 고정이므로 매일 동기화 불필요 |
| **NFR-2 Redis 캐시** | DB 조회 자체가 단순 인덱스 조회(country_code + date)라 충분히 빠름. 캐시는 트래픽 증가 시 추가 |
| **SpecialDayRedisRepository** | 1차에서 제거. JPA Repository만으로 시작 |
| **SpecialDaySyncService 스케줄러** | 단순 동기화 커맨드로 대체. `@Scheduled` + ShedLock은 2차 |
| **CircuitBreaker (NFR-3)** | 동기화가 실시간이 아닌 배치이므로 CircuitBreaker 불필요. 동기화 실패 시 로그 + 재시도면 충분 |

### 후순위 권장

| 항목 | 이유 |
|------|------|
| **스포츠/공연/영화 카테고리** | MVP는 `PUBLIC_HOLIDAY`만. 나머지 카테고리는 데이터 소스 확보 후 추가 |
| **GLOBAL 이벤트** | 국가별 공휴일만으로 시작. "전세계 공통" 개념은 2차 |
| **과거 D-Day ("N일 전")** | 미래 D-Day만 보여줘도 핵심 가치 전달 가능. 과거는 2차 |

### FR-7 (시간대 변환) 리뷰

**MVP 포함 권장** - 타국 이벤트의 시간대 변환은 이 서비스의 핵심 차별점.

| 관점 | 의견 |
|------|------|
| **MVP 범위 한정** | 공휴일은 `event_time`이 null → 시간 변환 없이 날짜 그대로 반환. 시간대 변환은 `event_time`이 있는 커스텀 이벤트에만 적용. 따라서 MVP(공휴일만)에서는 변환 로직이 작동하지 않되, **도메인 모델에 필드를 미리 확보**하는 수준 |
| **구현 복잡도** | `java.time.ZonedDateTime` 변환은 단순함. 핵심은 "event_time이 null일 때의 분기 처리"만 명확하면 됨 |
| **dateShifted 플래그** | 유용하지만 MVP에서는 후순위. 프론트가 `originalDate`와 `convertedDate`를 비교하면 되므로, 서버에서 계산할 필요 없음 |
| **API 응답 구조 비대** | `originalDate/Time/Timezone` + `convertedDate/Time/Timezone` 6개 필드는 과도함. MVP에서는 `eventDate`, `eventTime`, `eventTimezone`, `userDate`, `userTime` 정도로 단순화 |

---

## 보완 필요

### 명확화 필요

| 항목 | 질문 |
|------|------|
| **timezone 필수 여부** | 프론트에서 항상 timezone을 보내야 하나? 기본값(UTC)을 두는 게 더 실용적 |
| **D-Day 범위** | nearest upcoming을 찾을 때 몇 개월까지 탐색하나? 해당 연도 내? 무제한? |
| **source 컬럼 용도** | API_SYNC / ADMIN 구분이 MVP에서 필요한가? 1차에서는 모두 SYNC |
| **동기화 시점** | 앱 시작 시 자동? 수동 트리거? 데이터 없으면 서비스 불가? |
| **event_time null 시 변환 정책** | 공휴일처럼 시각이 없는 이벤트의 타국 조회 시, 날짜를 그대로 보여줄지 아니면 00:00 기준으로 변환할지 결정 필요. **제안: 날짜 그대로 표시 (변환하지 않음)** |
| **countryCode vs timezone 관계** | 미국 사용자가 `countryCode=KR`로 요청 시, 사용자 timezone은 파라미터로 받되 `countryCode`는 "조회할 국가"의 의미임을 명확히. 혼동 소지 있음 |

### 누락된 정보

| 항목 |
|------|
| 에러 응답 형식 (기존 core-web의 공통 에러 포맷 사용 여부) |
| Nager.Date API 응답 형식 → SpecialDay 엔티티 매핑 규칙 |
| 동일 국가+날짜에 중복 데이터 방지 전략 (UNIQUE 제약조건) |
| Nager.Date 공휴일의 `event_timezone` 기본값 (국가 코드 → 대표 timezone 매핑 필요) |

---

## 정제된 MVP 스펙

### 범위

**FR-1 ~ FR-4, FR-6(단순화), FR-7(도메인 모델만)** 구현. Admin API, Redis, 스케줄러 제외.
FR-7은 도메인에 `eventTime`, `eventTimezone` 필드를 확보하되, 실제 변환 로직은 커스텀 이벤트 카테고리 추가 시(2차) 본격 가동.

### 도메인 모델

```java
@Entity
@Table(name = "special_day", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"country_code", "date", "name"})
})
public class SpecialDay extends BaseEntity {
    private String name;                    // 특별한 날 이름
    @Enumerated(EnumType.STRING)
    private SpecialDayCategory category;    // PUBLIC_HOLIDAY (MVP)
    private LocalDate date;                 // 이벤트 현지 날짜
    private LocalTime eventTime;            // 이벤트 시각 (nullable, 공휴일은 null)
    private String eventTimezone;           // 이벤트 현지 timezone (e.g. "Asia/Seoul")
    private String countryCode;             // ISO 3166-1 alpha-2
    private String description;             // nullable
}
```

```java
public enum SpecialDayCategory {
    PUBLIC_HOLIDAY,
    MEMORIAL_DAY,
    SPORTS,
    ENTERTAINMENT,
    MOVIE,
    CUSTOM
}
```

### Repository

```java
public interface SpecialDayRepository extends JpaRepository<SpecialDay, Long> {

    // 오늘 특별한 날 조회 (해당 국가 + GLOBAL)
    List<SpecialDay> findByDateAndCountryCodeIn(LocalDate date, List<String> countryCodes);

    // 가장 가까운 미래 특별한 날
    Optional<SpecialDay> findFirstByCountryCodeInAndDateAfterOrderByDateAsc(
        List<String> countryCodes, LocalDate date);

    // 가장 가까운 과거 특별한 날 (2차)
    Optional<SpecialDay> findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(
        List<String> countryCodes, LocalDate date);
}
```

### API (MVP)

```
GET /api/v1/special-days/today?timezone=Asia/Seoul&countryCode=KR
```

- `timezone`: 기본값 `UTC`
- `countryCode`: 필수 (ISO 3166-1 alpha-2)

### Response

```json
{
  "date": "2026-12-25",
  "countryCode": "KR",
  "hasSpecialDay": true,
  "specialDays": [
    {
      "name": "크리스마스",
      "category": "PUBLIC_HOLIDAY",
      "description": "Christmas Day"
    }
  ],
  "upcoming": {
    "name": "신정",
    "date": "2027-01-01",
    "daysUntil": 7,
    "category": "PUBLIC_HOLIDAY"
  }
}
```

### 데이터 동기화 (MVP)

- `SpecialDaySyncService.syncByYear(int year, String countryCode)` 메서드 제공
- 앱 시작 시 데이터 없으면 현재 연도 동기화 (CommandLineRunner)
- Nager.Date API: `GET https://date.nager.at/api/v3/publicholidays/{year}/{countryCode}`

### 패키지 구조 (MVP)

```
apps/d-day/d-day-service/
└── src/main/java/com/booster/ddayservice/
    └── specialday/
        ├── domain/
        │   ├── SpecialDay.java
        │   ├── SpecialDayCategory.java
        │   └── SpecialDayRepository.java
        ├── application/
        │   ├── SpecialDayService.java        # 오늘 조회 + D-Day 계산
        │   ├── SpecialDaySyncService.java    # Nager.Date → DB 동기화
        │   └── dto/
        │       └── TodayResult.java
        ├── web/
        │   ├── controller/
        │   │   └── SpecialDayController.java
        │   └── dto/
        │       ├── TodayRequest.java
        │       └── TodayResponse.java
        ├── infrastructure/
        │   └── NagerDateClient.java          # RestClient로 Nager.Date 호출
        └── exception/
            └── SpecialDayErrorCode.java
```

---

## 구현 순서 제안

1. **도메인 레이어**: `SpecialDay` 엔티티, `SpecialDayCategory` enum, `SpecialDayRepository`
2. **인프라 레이어**: `NagerDateClient` (Nager.Date API 호출 + 응답 매핑)
3. **동기화**: `SpecialDaySyncService` (API → DB 저장, 중복 방지)
4. **조회 서비스**: `SpecialDayService` (오늘 조회 + upcoming D-Day)
5. **시간대 변환**: `TimezoneConverter` 유틸 (eventTime 존재 시 사용자 timezone으로 변환)
6. **웹 레이어**: Controller, Request/Response DTO
7. **테스트**: 각 단계별 단위 테스트 + 통합 테스트 (시간대 변환 경계값 포함)

## 체크리스트

- [x] `SpecialDay` 엔티티 + UNIQUE 제약조건
- [x] `SpecialDayCategory` enum
- [x] `SpecialDayRepository` (findByDate, findFirstByDateAfter)
- [x] `NagerDateClient` (RestClient, Nager.Date API 호출)
- [x] `SpecialDaySyncService` (연도+국가별 동기화, 중복 방지)
- [x] `SpecialDayService` (오늘 조회 + D-Day 계산)
- [x] `TimezoneConverter` (eventTime 존재 시 사용자 timezone 변환, null이면 패스스루)
- [x] `SpecialDayController` + DTO
- [x] `SpecialDayErrorCode` + 예외 처리
- [x] 단위 테스트 (Service, SyncService, Client, TimezoneConverter)
- [x] 시간대 변환 테스트 (같은 날 유지, 날짜 역전, eventTime null, 동일 timezone)
- [x] 통합 테스트 (Controller - @WebMvcTest)
