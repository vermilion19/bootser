# D-Day Service 캐시 설계

## 개요

공개 데이터는 Redis 캐시를 사용하고, 비공개 데이터는 DB에서 직접 조회하는 하이브리드 캐시 전략을 적용했습니다.

---

## 캐시 전략

### 설계 원칙

1. **공개 데이터 (isPublic=true)** → Redis 캐시
   - 여러 사용자가 공유 가능
   - 카테고리별 다른 TTL 적용

2. **비공개 데이터 (isPublic=false)** → DB 직접 조회
   - 본인만 접근 가능
   - 캐시 시 Redis 메모리 부담 → 캐시 안 함

---

## 캐시 구조

### 카테고리 그룹

| 캐시 이름 | 카테고리 | TTL | 이유 |
|-----------|----------|-----|------|
| `public-holidays` | PUBLIC_HOLIDAY, MEMORIAL_DAY | 6개월 | 공휴일은 거의 변경되지 않음 |
| `public-entertainment` | MOVIE, SPORTS, ENTERTAINMENT | 1주일 | 개봉일 변경, 경기 취소 가능성 |
| `public-others` | CUSTOM | 1일 | 기타 공개 데이터 |

### 캐시 키 형식

```
public-holidays::date:2025-02-05:country:12345
public-holidays::upcoming:country:12345:after:2025-02-05
public-holidays::past:country:12345:before:2025-02-05
```

- `12345`는 `countryCodes.hashCode()` 값

---

## 데이터 흐름

### 조회 API 호출 시

```
┌─────────────────────────────────────────────────────────┐
│                    getToday() 호출                       │
└─────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│   공개 데이터 조회    │         │   비공개 데이터 조회  │
│ (SpecialDayCacheService)       │ (SpecialDayRepository)│
└─────────────────────┘         └─────────────────────┘
          │                               │
          │  ┌─────────────────┐          │
          ├─▶│ public-holidays │          │
          │  │  (캐시 조회)     │          │
          │  └─────────────────┘          │
          │  ┌─────────────────┐          │
          ├─▶│public-entertainment│        │
          │  │  (캐시 조회)     │          │
          │  └─────────────────┘          │
          │  ┌─────────────────┐          │
          └─▶│ public-others   │          │
             │  (캐시 조회)     │          │
             └─────────────────┘          │
                      │                   │
                      └─────────┬─────────┘
                                ▼
                    ┌─────────────────────┐
                    │     결과 병합        │
                    └─────────────────────┘
                                │
                                ▼
                    ┌─────────────────────┐
                    │    TodayResult 반환  │
                    └─────────────────────┘
```

### 캐시 Hit/Miss 로직

```
캐시 조회 요청
     │
     ▼
┌─────────────┐
│ 캐시에 있음? │
└─────────────┘
     │
     ├── Yes → 캐시에서 반환 (빠름)
     │
     └── No  → DB 조회 → 캐시 저장 → 반환
              [CACHE MISS] 로그 출력
```

---

## 구현 파일

### CacheConfig.java

```java
Map<String, RedisCacheConfiguration> customConfigs = new HashMap<>();
customConfigs.put("public-holidays", defaultConfig.entryTtl(Duration.ofDays(180)));
customConfigs.put("public-entertainment", defaultConfig.entryTtl(Duration.ofDays(7)));
customConfigs.put("public-others", defaultConfig.entryTtl(Duration.ofDays(1)));
```

### SpecialDayCategory.java

```java
// 캐시 그룹 정의
public static final List<SpecialDayCategory> HOLIDAY_GROUP = List.of(PUBLIC_HOLIDAY, MEMORIAL_DAY);
public static final List<SpecialDayCategory> ENTERTAINMENT_GROUP = List.of(MOVIE, SPORTS, ENTERTAINMENT);
public static final List<SpecialDayCategory> CUSTOM_GROUP = List.of(CUSTOM);

public String getCacheGroup() {
    if (HOLIDAY_GROUP.contains(this)) return "public-holidays";
    else if (ENTERTAINMENT_GROUP.contains(this)) return "public-entertainment";
    else return "public-others";
}
```

### SpecialDayCacheService.java

```java
@Cacheable(value = "public-holidays", key = "'date:' + #date + ':country:' + #countryCodes.hashCode()")
public List<SpecialDay> findPublicHolidays(LocalDate date, List<CountryCode> countryCodes) {
    return specialDayRepository.findPublicByDateAndCountryCodeAndCategories(
            date, countryCodes, SpecialDayCategory.HOLIDAY_GROUP);
}
```

### SpecialDayService.java

```java
private List<SpecialDay> findVisibleSpecialDays(LocalDate date, List<CountryCode> countryCodes,
                                                 List<SpecialDayCategory> categories, Long memberId) {
    // 공개 데이터 (캐시)
    List<SpecialDay> publicData = cacheService.findAllPublicByDate(date, countryCodes);

    // 비공개 데이터 (DB)
    List<SpecialDay> privateData = memberId != null
            ? specialDayRepository.findPrivateByDateAndMemberId(date, countryCodes, memberId)
            : List.of();

    // 병합
    List<SpecialDay> result = new ArrayList<>(publicData);
    result.addAll(privateData);
    return result;
}
```

---

## 캐시 무효화

### 데이터 변경 시

공개 데이터가 변경되면 관련 캐시를 무효화해야 합니다.

```java
@CacheEvict(value = "public-holidays", allEntries = true)
public void syncHolidays(...) { }

@CacheEvict(value = "public-entertainment", allEntries = true)
public void syncMovies(...) { }
```

### 현재 적용된 무효화

| 작업 | 무효화 캐시 |
|------|------------|
| `syncByYear()` | `external-holidays` (특정 키) |
| 공휴일 Sync | `public-holidays` 무효화 필요 (TODO) |
| 영화 Sync | `public-entertainment` 무효화 필요 (TODO) |

---

## Redis 메모리 관리

### 권장 설정

```conf
# redis.conf
maxmemory 512mb
maxmemory-policy allkeys-lru
```

- 메모리 초과 시 LRU(Least Recently Used) 정책으로 오래된 캐시 자동 제거

### 예상 메모리 사용량

| 항목 | 예상 크기 |
|------|----------|
| 공휴일 (1년, 10개국) | ~200개 × 1KB = ~200KB |
| 영화 (1년) | ~500개 × 1KB = ~500KB |
| 기타 공개 데이터 | 변동 |

→ 공개 데이터만 캐싱하므로 메모리 부담 적음

---

## 모니터링

### 캐시 상태 확인

```bash
# Redis CLI
redis-cli keys "public-holidays*"
redis-cli keys "public-entertainment*"

# TTL 확인
redis-cli ttl "public-holidays::date:2025-02-05:country:12345"
```

### 로그 확인

캐시 미스 발생 시 로그 출력:

```
DEBUG [CACHE MISS] public-holidays - date=2025-02-05, countries=[KR]
```

---

## 향후 개선 사항

1. **캐시 무효화 자동화**: Sync 작업 시 관련 캐시 자동 무효화
2. **캐시 워밍**: 서버 시작 시 자주 조회되는 데이터 미리 캐싱
3. **분산 캐시 무효화**: 멀티 인스턴스 환경에서 캐시 일관성 유지
