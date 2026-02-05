# d-day-service 코드 리뷰 보고서

> **분석 일시:** 2026-02-05
> **대상 모듈:** `apps/d-day/d-day-service`
> **분석 관점:** 논리적 오류, 보안 취약점, 코드 구조 및 확장성

---

## 목차

1. [프로젝트 구조 분석](#1-프로젝트-구조-분석)
2. [논리적 오류](#2-논리적-오류)
3. [보안 취약점](#3-보안-취약점)
4. [코드 품질 및 확장성](#4-코드-품질-및-확장성)
5. [개선 권장사항](#5-개선-권장사항)

---

## 1. 프로젝트 구조 분석

### 1.1 패키지 구조

```
d-day-service/
├── auth/
│   └── web/                          # 인증/인가
│       ├── CurrentMemberId.java      # 커스텀 annotation
│       └── CurrentMemberIdResolver.java
│
├── specialday/
│   ├── domain/                       # 도메인 레이어
│   │   ├── SpecialDay.java           # Entity (Rich Domain Model)
│   │   ├── SpecialDayRepository.java # Repository interface
│   │   ├── SpecialDayCategory.java   # Enum (6가지)
│   │   ├── CountryCode.java          # Enum (127개 국가)
│   │   └── Timezone.java             # Enum (시간대)
│   │
│   ├── application/                  # 응용 레이어
│   │   ├── SpecialDayService.java    # 핵심 비즈니스 로직
│   │   ├── SpecialDaySyncService.java # 공휴일 동기화
│   │   ├── MovieSyncService.java     # 영화 개봉일 동기화
│   │   └── TimezoneConverter.java    # 타임존 변환
│   │
│   ├── web/                          # 표현 레이어
│   │   ├── controller/
│   │   │   ├── SpecialDayController.java
│   │   │   └── SpecialDayAdminController.java
│   │   └── dto/
│   │
│   ├── infrastructure/               # 외부 API 클라이언트
│   │   ├── NagerDateClient.java      # 공휴일 API
│   │   └── TmdbClient.java           # 영화 API
│   │
│   └── exception/                    # 도메인 예외
│
└── config/                           # 설정 클래스
```

### 1.2 DDD 패턴 적용 상태

| 항목 | 상태 | 비고 |
|------|------|------|
| 레이어 분리 | ✅ 양호 | domain/application/web/infrastructure 명확 |
| Rich Domain Model | ✅ 양호 | `toggleVisibility()`, `isOwnedBy()` 등 |
| 정적 팩토리 메서드 | ✅ 양호 | `SpecialDay.of()`, `createByMember()` |
| Repository 추상화 | ✅ 양호 | interface 정의, JPA 구현 |
| Event 레이어 | ❌ 미적용 | 이벤트 기반 아키텍처 없음 |
| Outbox 패턴 | ❌ 미적용 | 동기화 트랜잭션 보장 없음 |

---

## 2. 논리적 오류

---

---

### 2.3 [HIGH] SpecialDaySyncService.removeDuplicates() - 반환값 오류

**파일:** `SpecialDaySyncService.java:96-118`

```java
public int removeDuplicates(CountryCode countryCode) {
    List<String> duplicateKeys = specialDayRepository.findDuplicateDateNameKeysByCountryCode(countryCode);
    int totalDeleted = 0;

    for (String key : duplicateKeys) {
        // ... 삭제 로직 ...
        totalDeleted++;  // ← 항상 1씩 증가 (실제 삭제 수 아님!)
    }

    return totalDeleted;  // ← duplicateKeys.size() 반환
}
```

**문제점:**
- 실제 삭제된 레코드 수가 아닌 키 개수 반환
- 메서드 계약 위반

**수정 방안:**
```java
int deleted = specialDayRepository.deleteDuplicatesByCountryCodeAndDateAndName(...);
totalDeleted += deleted;  // 실제 삭제 수 누적
```

---

### 2.4 [MEDIUM] syncByYear vs syncByYear2 - Dead Code

**파일:** `SpecialDaySyncService.java`

| 메서드 | 캐시 evict | 호출처 |
|--------|-----------|--------|
| `syncByYear()` | ❌ 없음 | Controller |
| `syncByYear2()` | ✅ 있음 | 미사용 |

**문제점:**
- 개선된 버전(`syncByYear2`)이 사용되지 않음
- 캐시 무효화 누락으로 stale data 가능성

---

### 2.5 [MEDIUM] MovieSyncService - 암묵적 기본값

```java
try {
    countryCode = CountryCode.valueOf(region.toUpperCase());
} catch (IllegalArgumentException e) {
    countryCode = CountryCode.KR;  // ← 조용히 KR로 변환
}
```

**문제점:** 잘못된 입력에 에러 대신 기본값 반환 (예상치 못한 동작)

---

## 3. 보안 취약점

### 3.1 [HIGH] API Key 기본값 노출

**파일:** `application.yml:28-30`

```yaml
app:
  tmdb:
    api-key: ${TMDB_API_KEY:dev-tmdb-key}
```

**위험:** 환경 변수 미설정 시 개발용 키로 동작

**권장사항:**
```yaml
api-key: ${TMDB_API_KEY}  # 기본값 제거, 필수화
```

---

### 3.2 [HIGH] 인증 헤더 신뢰 문제

**파일:** `CurrentMemberIdResolver.java:24-54`

```java
String userIdHeader = webRequest.getHeader("X-User-Id");  // ← 직접 신뢰
long memberId = Long.parseLong(userIdHeader);
```

**위험:**
- 클라이언트가 `X-User-Id` 헤더를 임의로 설정 가능
- Gateway 우회 시 타인 계정으로 접근 가능

**권장사항:**
- JWT 토큰 검증 추가
- 또는 내부 네트워크 격리 (서비스 간 직접 통신 차단)

---

### 3.3 [MEDIUM] 입력 검증 부족

**파일:** `CreateSpecialDayRequest.java`

| 필드 | 현재 검증 | 필요한 검증 |
|------|----------|------------|
| `name` | `@NotBlank` | `@Size(max=255)` 추가 |
| `description` | 없음 | `@Size(max=1000)` 추가 |
| `category` | `@NotNull` | enum 형식 검증 |
| `timezone` | 없음 | 형식 검증 또는 nullable 명시 |

---

### 3.4 [MEDIUM] Admin API Rate Limiting 부재

**파일:** `SpecialDayAdminController.java`

```java
@PostMapping("/sync")
public ApiResponse<SyncResultResponse> syncAll(...) { ... }

@PostMapping("/sync/movies")
public ApiResponse<MovieSyncResult> syncMovies(...) { ... }
```

**위험:**
- 대량 요청으로 DB/외부 API 과부하 가능
- Gateway에서 admin 경로 차단했지만, 내부 호출 시 제한 없음

**권장사항:**
- `@RateLimiter` (Resilience4j) 적용
- 스케줄러 기반 동기화로 변경

---

### 3.5 [LOW] IDOR (Insecure Direct Object Reference)

**현황:** `isOwnedBy(memberId)` 로 소유권 검증 (양호)

**주의점:** delete/toggleVisibility 메서드의 로직 오류 수정 시 검증 로직 유지 필요

---

## 4. 코드 품질 및 확장성

### 4.1 SOLID 원칙 준수

| 원칙 | 평가 | 비고 |
|------|------|------|
| Single Responsibility | ✅ | Service/Controller/Repository 명확 분리 |
| Open/Closed | ⚠️ | 새 동기화 소스 추가 시 코드 수정 필요 |
| Liskov Substitution | ✅ | 상속 미사용 |
| Interface Segregation | ✅ | Repository 적절히 분리 |
| Dependency Inversion | ✅ | DI, interface 의존 |

---

### 4.2 중복 코드

**SpecialDayService의 오버로드 메서드:**

```java
// memberId 유무만 다른 중복 메서드
TodayResult getToday(..., Long memberId)
TodayResult getToday(...)  // ← memberId=null 호출

PastResult getPast(..., Long memberId)
PastResult getPast(...)    // ← memberId=null 호출
```

**개선:** Optional<Long> memberId 로 통합

---

### 4.3 하드코딩된 값

| 위치 | 값 | 개선 방안 |
|------|-----|----------|
| `NagerDateClient` | `https://date.nager.at/api/v3` | `application.yml`로 이동 |
| `TmdbClient` | `MAX_PAGES = 5` | 설정 파일로 이동 |
| `CurrentMemberIdResolver` | `X-User-Id`, `-1` | 상수 클래스 정의 |
| `CacheConfig` | TTL 30일, 30분 | 설정 파일로 이동 |

---

### 4.4 테스트 현황

| 영역 | 상태 | 비고 |
|------|------|------|
| SpecialDayServiceTest | ✅ 완전 | Mockito 기반 단위 테스트 |
| TimezoneConverterTest | ✅ 완전 | 경계값 테스트 포함 |
| Controller 테스트 | ⚠️ 부분 | MockMvc 테스트 미흡 |
| 통합 테스트 | ❌ 없음 | @SpringBootTest 필요 |
| 외부 API 테스트 | ⚠️ 부분 | WireMock 활용 권장 |

---

### 4.5 확장성 고려사항

#### 새 카테고리 추가 시

```java
// 현재: Enum 수정 필요
public enum SpecialDayCategory {
    PUBLIC_HOLIDAY, MEMORIAL_DAY, SPORTS, ENTERTAINMENT, MOVIE, CUSTOM
    // NEW_CATEGORY 추가 → 코드 배포 필요
}
```

**개선:** DB 테이블로 관리 또는 동적 로딩

#### 새 동기화 소스 추가 시

현재 구조:
```
NagerDateClient + SpecialDaySyncService (공휴일)
TmdbClient + MovieSyncService (영화)
NewClient + NewSyncService (새 소스) ← 매번 새 클래스 필요
```

**개선:** Strategy 패턴 적용
```java
interface SyncStrategy {
    int sync(int year, CountryCode country);
}

@Component class HolidaySyncStrategy implements SyncStrategy { ... }
@Component class MovieSyncStrategy implements SyncStrategy { ... }
```

---

## 5. 개선 권장사항

### 5.1 긴급 수정 (P0)

| 항목 | 파일 | 조치 |
|------|------|------|
| delete() 제어 흐름 | `SpecialDayService.java` | if 조건 반전, early return |
| toggleVisibility() 제어 흐름 | `SpecialDayService.java` | 동일하게 수정 |
| removeDuplicates() 반환값 | `SpecialDaySyncService.java` | 실제 삭제 수 누적 |

### 5.2 보안 강화 (P1)

| 항목 | 조치 |
|------|------|
| API Key | 환경 변수 필수화, 기본값 제거 |
| 인증 | JWT 검증 추가 또는 내부 네트워크 격리 |
| 입력 검증 | DTO에 `@Size`, `@Pattern` 추가 |
| Rate Limiting | Admin API에 Resilience4j 적용 |

### 5.3 코드 품질 (P2)

| 항목 | 조치 |
|------|------|
| Dead Code | `syncByYear2` 정리 또는 활용 |
| 중복 메서드 | Optional 파라미터로 통합 |
| 하드코딩 | 설정 파일 또는 상수 클래스로 이동 |
| 테스트 | 통합 테스트, Controller 테스트 추가 |

### 5.4 아키텍처 개선 (P3)

| 항목 | 조치 |
|------|------|
| Event 레이어 | ApplicationEvent 발행 구조 추가 |
| Outbox 패턴 | 동기화 이벤트 발행 보장 |
| 동기화 전략 | Strategy 패턴으로 리팩토링 |
| 캐시 전략 | 동기화 시 명시적 evict |

---

## 요약

### 잘된 부분
- DDD 레이어 구조 명확
- 도메인 엔티티의 비즈니스 메서드
- 예외 처리 체계 (ErrorCode + Handler)
- 기본적인 단위 테스트 커버리지

### 긴급 조치 필요
- `delete()`, `toggleVisibility()` 로직 버그 → **서비스 장애 가능**
- API Key 노출 위험
- 인증 헤더 스푸핑 가능성

### 중장기 개선
- 통합 테스트 추가
- Event 기반 아키텍처 도입
- Strategy 패턴으로 동기화 확장성 확보

---

*이 보고서는 코드 정적 분석 기반으로 작성되었습니다. 실제 런타임 동작은 테스트를 통해 검증이 필요합니다.*
