# 오늘의 특별한 날 알림 서비스 (Today's Special Day) 스펙

## 개요

사용자의 시간대와 국가에 맞춰 오늘이 어떤 특별한 날인지 알려주고, 특별한 날이 아닌 경우 가장 가까운 특별한 날까지의 D-Day를 보여주는 서비스.

## 배경 (Why)

- 공휴일, 기념일, 이벤트 등 "오늘이 무슨 날인지" 한눈에 확인할 수 있는 통합 서비스 부재
- 전세계 사용자 대상이므로 시간대/국가별 차이를 정확히 반영해야 함
- 공휴일뿐 아니라 스포츠, 공연, 영화 개봉 등 다양한 카테고리의 이벤트를 통합 제공

## 요구사항

### 기능 요구사항 (Functional)

- [ ] **FR-1**: 사용자의 타임존 기반으로 "오늘"을 정확히 결정한다
  - 요청 시 `timezone` 파라미터 (e.g. `Asia/Seoul`, `America/New_York`) 필수
  - 서버는 해당 타임존 기준 날짜를 계산하여 조회
- [ ] **FR-2**: 국가별 공휴일/기념일 데이터를 조회한다
  - 사용자 기본 국가(`countryCode`) 설정 지원 (ISO 3166-1 alpha-2)
  - 다른 국가의 특별한 날도 `countryCode` 파라미터로 조회 가능
- [ ] **FR-3**: 오늘이 특별한 날이면 해당 정보를 반환한다
  - 이름, 카테고리(공휴일/기념일/스포츠/공연/영화 등), 설명
  - 하루에 복수의 특별한 날이 있을 수 있음 (리스트 반환)
- [ ] **FR-4**: 오늘이 특별한 날이 아니면 D-Day 정보를 반환한다
  - 가장 가까운 **과거** 특별한 날: "N일 전 - OO이었습니다"
  - 가장 가까운 **미래** 특별한 날: "N일 후 - OO입니다"
- [ ] **FR-5**: 커스텀 이벤트(스포츠, 공연, 영화 개봉일 등)를 관리할 수 있다
  - 관리자가 이벤트를 CRUD 할 수 있는 Admin API 제공
  - 카테고리: `PUBLIC_HOLIDAY`, `MEMORIAL_DAY`, `SPORTS`, `ENTERTAINMENT`, `MOVIE`, `CUSTOM`
- [ ] **FR-6**: 외부 공휴일 API 데이터를 주기적으로 동기화한다
  - 외부 API에서 데이터를 가져와 내부 DB에 저장 (캐싱)
  - 동기화 주기: 일 1회 (스케줄러)
- [ ] **FR-7**: 타국 이벤트의 시간대 변환 및 D-Day 계산을 지원한다
  - 이벤트는 **원본 시간대**(이벤트가 열리는 국가의 timezone)를 가진다
  - 사용자가 다른 국가의 이벤트를 조회할 때, 해당 이벤트의 원본 시간(날짜+시각)을 사용자의 timezone으로 변환하여 보여준다
  - 예: 한국 공연이 `2026-03-02 19:00 Asia/Seoul`이면, 미국 동부 사용자에게는 `2026-03-02 05:00 America/New_York`으로 표시
  - D-Day 계산도 사용자 timezone 기준으로 수행 (변환된 날짜 기준 남은 일수)
  - 날짜가 시차로 인해 달라질 수 있음을 명시 표시 (e.g. 한국 3/2 → 미국 3/1)

### 비기능 요구사항 (Non-Functional)

- [ ] **NFR-1**: 응답 시간 200ms 이내 (캐시 히트 시)
- [ ] **NFR-2**: Redis 캐시 적용 - 국가+날짜 기준 캐싱 (TTL: 24h)
- [ ] **NFR-3**: 외부 API 장애 시에도 내부 DB 데이터로 서비스 가능 (Resilience)
- [ ] **NFR-4**: 200+ 국가 코드 지원 (ISO 3166-1)

## 영향 범위

### 신규 서비스 모듈

이 기능은 기존 booster 프로젝트와 독립적인 **새로운 서비스 모듈**로 생성합니다.

```
apps/d-day/d-day-service/
└── src/main/java/com/booster/ddayservice/
    ├── specialday/
    │   ├── domain/
    │   │   ├── SpecialDay.java            # 특별한 날 엔티티
    │   │   ├── SpecialDayRepository.java
    │   │   ├── SpecialDayCategory.java    # 카테고리 Enum
    │   │   └── CountryCode.java           # 국가 코드 VO
    │   │
    │   ├── application/
    │   │   ├── SpecialDayService.java     # 조회 로직
    │   │   ├── SpecialDaySyncService.java # 외부 API 동기화
    │   │   └── dto/
    │   │       ├── TodayResult.java       # 오늘 조회 결과
    │   │       └── DdayInfo.java          # D-Day 정보
    │   │
    │   ├── web/
    │   │   ├── controller/
    │   │   │   ├── SpecialDayController.java      # 사용자 API
    │   │   │   └── SpecialDayAdminController.java  # 관리자 API
    │   │   ├── dto/request/
    │   │   │   └── TodayRequest.java
    │   │   └── dto/response/
    │   │       └── TodayResponse.java
    │   │
    │   ├── infrastructure/
    │   │   ├── HolidayApiClient.java      # 외부 공휴일 API 호출
    │   │   └── SpecialDayRedisRepository.java
    │   │
    │   └── exception/
    │       ├── SpecialDayErrorCode.java
    │       └── UnsupportedCountryException.java
    │
    └── config/
        └── SpecialDayScheduleConfig.java  # 동기화 스케줄러
```

### 의존성

| 구분 | 내용 |
|------|------|
| **libs** | `common`, `core-web`, `storage-db`, `storage-redis`, `core-resilience` |
| **외부 API** | Nager.Date (무료, 키 불필요) 또는 Calendarific (무료 티어) |
| **신규 라이브러리** | 없음 (기존 스택으로 충분) |

## API 설계

### 1. 오늘의 특별한 날 조회

```
GET /api/v1/special-days/today?timezone=Asia/Seoul&countryCode=KR
```

**Response (특별한 날이 있는 경우)**

```json
{
  "date": "2026-12-25",
  "country": "KR",
  "hasSpecialDay": true,
  "specialDays": [
    {
      "id": 1,
      "name": "크리스마스",
      "category": "PUBLIC_HOLIDAY",
      "description": "예수 그리스도의 탄생을 기념하는 날"
    }
  ],
  "nearest": null
}
```

**Response (특별한 날이 없는 경우)**

```json
{
  "date": "2026-03-10",
  "country": "KR",
  "hasSpecialDay": false,
  "specialDays": [],
  "nearest": {
    "previous": {
      "name": "삼일절",
      "date": "2026-03-01",
      "daysAgo": 9,
      "category": "PUBLIC_HOLIDAY"
    },
    "upcoming": {
      "name": "어린이날",
      "date": "2026-05-05",
      "daysUntil": 56,
      "category": "PUBLIC_HOLIDAY"
    }
  }
}
```

### 2. 타국 이벤트 조회 (시간대 변환)

```
GET /api/v1/special-days/today?timezone=America/New_York&countryCode=KR
```

미국 동부 사용자가 한국 이벤트를 조회하는 경우:

```json
{
  "date": "2026-03-01",
  "countryCode": "KR",
  "userTimezone": "America/New_York",
  "hasSpecialDay": true,
  "specialDays": [
    {
      "name": "K-POP 콘서트",
      "category": "ENTERTAINMENT",
      "description": "서울 올림픽공원",
      "originalDate": "2026-03-02",
      "originalTime": "19:00",
      "originalTimezone": "Asia/Seoul",
      "convertedDate": "2026-03-02",
      "convertedTime": "05:00",
      "convertedTimezone": "America/New_York",
      "dateShifted": false
    }
  ],
  "upcoming": {
    "name": "삼일절",
    "date": "2026-03-01",
    "daysUntil": 0,
    "category": "PUBLIC_HOLIDAY",
    "convertedDate": "2026-02-28",
    "dateShifted": true
  }
}
```

> `dateShifted: true`는 시차로 인해 사용자 기준 날짜가 원본과 달라졌음을 의미한다.
> D-Day 계산은 `convertedDate` 기준으로 수행한다.

### 3. 특정 기간 특별한 날 조회

```
GET /api/v1/special-days?countryCode=KR&from=2026-01-01&to=2026-12-31
```

### 3. 관리자 - 커스텀 이벤트 등록

```
POST /api/v1/admin/special-days
```

```json
{
  "name": "2026 FIFA 월드컵 결승",
  "category": "SPORTS",
  "date": "2026-07-19",
  "countryCode": "GLOBAL",
  "description": "미국, 멕시코, 캐나다 공동 개최 월드컵 결승전"
}
```

## 데이터 모델

### special_day 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK) | Snowflake ID |
| name | VARCHAR(200) | 특별한 날 이름 |
| category | VARCHAR(50) | 카테고리 (ENUM) |
| date | DATE | 날짜 (이벤트 현지 기준) |
| event_time | TIME | 이벤트 시각 (nullable, 공휴일은 null) |
| event_timezone | VARCHAR(50) | 이벤트 현지 timezone (e.g. `Asia/Seoul`) |
| country_code | VARCHAR(10) | ISO 3166-1 또는 "GLOBAL" |
| description | TEXT | 설명 (nullable) |
| source | VARCHAR(50) | 데이터 출처 (API_SYNC / ADMIN) |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

**인덱스**

- `idx_special_day_country_date`: (`country_code`, `date`) - 핵심 조회 조건
- `idx_special_day_date`: (`date`) - D-Day 계산용 범위 조회

## 테스트 시나리오

### 정상 케이스

- 공휴일 당일 조회 → 해당 공휴일 정보 반환
- 하루에 복수 이벤트 존재 시 → 리스트로 모두 반환
- 특별한 날이 아닌 날 조회 → nearest previous/upcoming 반환
- 다른 국가 코드로 조회 → 해당 국가 데이터 반환
- GLOBAL 이벤트는 모든 국가에서 조회됨

### 예외 케이스

- 지원하지 않는 `countryCode` → `UnsupportedCountryException`
- 유효하지 않은 `timezone` → 400 Bad Request
- 외부 API 장애 시 → 내부 DB 데이터로 fallback (CircuitBreaker)

### 시간대 변환 케이스

- 한국 3/2 19:00 이벤트 → 미국 동부 3/2 05:00 (같은 날)
- 한국 3/1 02:00 이벤트 → 미국 동부 2/28 12:00 (날짜 역전, `dateShifted: true`)
- 시각 정보 없는 공휴일 → 날짜만 표시, 시간 변환 없이 날짜 그대로 사용
- D-Day: 한국 3/2 이벤트를 미국 동부에서 조회 시, convertedDate 기준으로 남은 일수 계산

### 경계값 케이스

- 날짜 변경선 부근 (UTC 자정 전후) → 타임존별 정확한 날짜 계산 검증
- 연말/연초 (12/31 ~ 1/1) → D-Day 계산 시 연도 전환
- 데이터가 아예 없는 국가 → 빈 리스트 + nearest null
- 시차로 인한 날짜 역전 시 D-Day가 -1일 될 수 있음 검증

## 미결정 사항 (TBD)

1. **외부 API 선택**: Nager.Date (무료/오픈소스) vs Calendarific (더 많은 국가) - MVP는 Nager.Date 권장
2. **스포츠/엔터테인먼트 데이터 소스**: 수동 입력(Admin)으로 시작할지, 별도 API 연동할지
3. **다국어 지원**: 특별한 날 이름의 다국어 처리 (MVP에서는 단일 언어)
4. **사용자 개인 기념일**: 사용자가 직접 등록하는 기능 (2차 범위)
5. **알림(Push/SMS)**: 특별한 날 전날 알림 기능 (notification-service 연동, 2차 범위)

## 외부 공휴일 데이터 소스

| API | 무료 여부 | 국가 수 | API 키 | 비고 |
|-----|----------|---------|--------|------|
| [Nager.Date](https://date.nager.at/Api) | 완전 무료 | 100+ | 불필요 | 오픈소스, MVP 추천 |
| [OpenHolidays API](https://www.openholidaysapi.org/en/) | 완전 무료 | 제한적 | 불필요 | 유럽 중심 |
| [Calendarific](https://calendarific.com) | 무료 티어 | 230+ | 필요 | 가장 넓은 커버리지 |
| [API Ninjas](https://api-ninjas.com/api/holidays) | 무료 티어 | 230+ | 필요 | 2010-2030 지원 |

**MVP 추천**: Nager.Date (키 불필요, 100+ 국가, 안정적)
**확장 시**: Calendarific 추가 연동 (230+ 국가)
