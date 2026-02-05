# D-Day 서비스 아키텍처 설계

## 1. 전체 시스템 구성도

```
                            [Client]
                               |
                               v
                        [API Gateway]
                               |
                               v
                     [D-Day Service (MVP)]
                        /           \
                       v             v
                [PostgreSQL]    [Nager.Date API]
                                (외부 공휴일)

--- 2차 확장 시 ---

                            [Client]
                               |
                               v
                        [API Gateway]
                               |
                               v
                     [D-Day Service]
                    /    |     \      \
                   v     v      v      v
            [PostgreSQL] [Redis] [Kafka] [Nager.Date]
                                   |
                                   v
                          [Notification Service]
```

## 2. 요청 흐름 시퀀스

```
Client                Controller           Service            Repository       NagerDateClient
  |--- GET /today ------->|                    |                    |                |
  |                        |-- getTodayInfo -->|                    |                |
  |                        |                   |-- findByDate ----->|                |
  |                        |                   |<-- List<SpecialDay>|                |
  |                        |                   |-- findUpcoming --->|                |
  |                        |                   |<-- Optional -------|                |
  |                        |<- TodayResult ----|                    |                |
  |<-- TodayResponse ------|                   |                    |                |

=== 앱 시작 시 (CommandLineRunner) ===

CommandLineRunner        SyncService          NagerDateClient      Repository
  |-- syncIfEmpty -------->|                       |                   |
  |                        |-- GET /holidays ----->|                   |
  |                        |<-- List<HolidayDto> --|                   |
  |                        |-- saveAll (중복제거) ----------------->|   |
```

## 3. 패키지 구조

```
apps/d-day/d-day-service/
└── src/main/java/com/booster/ddayservice/
    ├── DdayServiceApplication.java
    ├── specialday/
    │   ├── domain/
    │   │   ├── SpecialDay.java
    │   │   ├── SpecialDayCategory.java
    │   │   └── SpecialDayRepository.java
    │   ├── application/
    │   │   ├── SpecialDayService.java
    │   │   ├── SpecialDaySyncService.java
    │   │   ├── TimezoneConverter.java
    │   │   └── dto/
    │   │       ├── TodayResult.java
    │   │       └── UpcomingInfo.java
    │   ├── web/
    │   │   ├── controller/
    │   │   │   └── SpecialDayController.java
    │   │   └── dto/
    │   │       ├── TodayRequest.java
    │   │       └── TodayResponse.java
    │   ├── infrastructure/
    │   │   └── NagerDateClient.java
    │   └── exception/
    │       ├── SpecialDayErrorCode.java
    │       └── UnsupportedCountryException.java
    └── config/
        └── SyncInitializer.java              # CommandLineRunner
```

## 4. 레이어별 인터페이스 설계

### Domain

| 클래스 | 메서드 시그니처 |
|--------|----------------|
| `SpecialDay` | `static create(name, category, date, eventTime, eventTimezone, countryCode, description)` |
| | `boolean isToday(LocalDate date)` |
| | `long daysUntilFrom(LocalDate baseDate)` |
| `SpecialDayRepository` | `findByDateAndCountryCodeIn(LocalDate, List<String>)` |
| | `findFirstByCountryCodeInAndDateAfterOrderByDateAsc(List<String>, LocalDate)` |
| | `existsByCountryCodeAndDateAndName(String, LocalDate, String)` |

### Application

| 클래스 | 메서드 시그니처 |
|--------|----------------|
| `SpecialDayService` | `TodayResult getTodayInfo(String countryCode, ZoneId userTimezone)` |
| `SpecialDaySyncService` | `int syncByYear(int year, String countryCode)` |
| | `void syncIfEmpty(String countryCode)` |
| `TimezoneConverter` | `LocalDate convertDate(LocalDate, LocalTime, ZoneId eventZone, ZoneId userZone)` |

### Web

| 클래스 | 엔드포인트 |
|--------|-----------|
| `SpecialDayController` | `GET /api/v1/special-days/today?timezone=&countryCode=` |
| `TodayRequest` | record: `countryCode(필수)`, `timezone(기본 UTC)` |
| `TodayResponse` | record: `of(TodayResult)` 정적 팩토리 |

### Infrastructure

| 클래스 | 메서드 시그니처 |
|--------|----------------|
| `NagerDateClient` | `List<NagerHolidayDto> getPublicHolidays(int year, String countryCode)` |

## 5. 테이블 DDL

```sql
CREATE TABLE special_day (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50)  NOT NULL,
    date            DATE         NOT NULL,
    event_time      TIME,
    event_timezone  VARCHAR(50),
    country_code    VARCHAR(10)  NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,

    CONSTRAINT uk_special_day_country_date_name
        UNIQUE (country_code, date, name)
);

CREATE INDEX idx_special_day_country_date ON special_day (country_code, date);
CREATE INDEX idx_special_day_date ON special_day (date);
```

## 6. 확장 포인트 (2차)

| 확장 항목 | MVP 준비 | 2차 구현 |
|-----------|---------|----------|
| Redis 캐시 | 없음 | `SpecialDayRedisRepository`, Cache-Aside |
| 스케줄러 | CommandLineRunner | `@Scheduled` + ShedLock |
| Kafka 이벤트 | 없음 | 동기화 완료 이벤트 → Notification 연동 |
| 다중 외부 API | NagerDateClient만 | `HolidayApiClient` 인터페이스 + Strategy 패턴 |
| Admin CRUD | 없음 | `SpecialDayAdminController` |
| 시간대 변환 | 필드 확보 + TimezoneConverter 껍데기 | 커스텀 이벤트 추가 시 본격 가동 |

### 2차 확장 시 패키지 추가 예상

```
specialday/
├── infrastructure/
│   ├── NagerDateClient.java            # 기존
│   ├── CalendarificClient.java         # 추가 (Strategy)
│   ├── HolidayApiClient.java           # 인터페이스 (추가)
│   └── SpecialDayRedisRepository.java  # 추가
├── event/
│   ├── SyncCompletedEventListener.java # 추가
│   └── OutboxMessageRelay.java         # 추가
└── domain/
    └── outbox/
        ├── OutboxEvent.java            # 추가
        └── OutboxRepository.java       # 추가
```

## 7. 트레이드오프

| 결정 | 선택 | 이유 |
|------|------|------|
| 캐시 미적용 | DB 직접 조회 | `(country_code, date)` 인덱스 조회는 sub-ms |
| CommandLineRunner | 앱 시작 시 1회 | 공휴일은 연간 고정, 스케줄러 불필요 |
| RestClient | Spring RestClient 직접 | 외부 API 1개, Feign 의존성 불필요 |
| source 컬럼 | MVP에서 제외 | 모든 데이터가 SYNC, 2차에서 Admin 추가 시 컬럼 추가 |
