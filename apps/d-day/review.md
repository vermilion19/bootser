# 스펙 리뷰 결과

## 요약

- **전체 평가**: Needs Work
- **예상 복잡도**: Medium

스펙의 전체적인 방향은 명확하나, MVP 범위가 너무 넓고 당장 불필요한 기능이 포함되어 있음. 외부 API 동기화, Admin CRUD, Redis 캐시를 1차에 모두 넣으면 초기 개발 부담이 큼. 핵심 가치("오늘이 무슨 날?")에 집중하여 축소 필요.

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

---

## 보완 필요

### 명확화 필요

| 항목 | 질문 |
|------|------|
| **timezone 필수 여부** | 프론트에서 항상 timezone을 보내야 하나? 기본값(UTC)을 두는 게 더 실용적 |
| **D-Day 범위** | nearest upcoming을 찾을 때 몇 개월까지 탐색하나? 해당 연도 내? 무제한? |
| **source 컬럼 용도** | API_SYNC / ADMIN 구분이 MVP에서 필요한가? 1차에서는 모두 SYNC |
| **동기화 시점** | 앱 시작 시 자동? 수동 트리거? 데이터 없으면 서비스 불가? |

### 누락된 정보

| 항목 |
|------|
| 에러 응답 형식 (기존 core-web의 공통 에러 포맷 사용 여부) |
| Nager.Date API 응답 형식 → SpecialDay 엔티티 매핑 규칙 |
| 동일 국가+날짜에 중복 데이터 방지 전략 (UNIQUE 제약조건) |

---

## 정제된 MVP 스펙

### 범위

**FR-1 ~ FR-4, FR-6(단순화)** 만 구현. Admin API, Redis, 스케줄러 제외.

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
    private LocalDate date;
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
5. **웹 레이어**: Controller, Request/Response DTO
6. **테스트**: 각 단계별 단위 테스트 + 통합 테스트

## 체크리스트

- [ ] `SpecialDay` 엔티티 + UNIQUE 제약조건
- [ ] `SpecialDayCategory` enum
- [ ] `SpecialDayRepository` (findByDate, findFirstByDateAfter)
- [ ] `NagerDateClient` (RestClient, Nager.Date API 호출)
- [ ] `SpecialDaySyncService` (연도+국가별 동기화, 중복 방지)
- [ ] `SpecialDayService` (오늘 조회 + D-Day 계산)
- [ ] `SpecialDayController` + DTO
- [ ] `SpecialDayErrorCode` + 예외 처리
- [ ] 단위 테스트 (Service, Client)
- [ ] 통합 테스트 (Controller)
