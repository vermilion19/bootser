# TimezoneConverter 사용법

이벤트의 시간대를 사용자 시간대로 변환하는 유틸리티 클래스입니다. 정적 메서드 하나만 제공합니다.

## API

```java
TimezoneConverter.convert(eventDate, eventTime, eventTimezone, userTimezone)
```

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `eventDate` | `LocalDate` | 이벤트 날짜 |
| `eventTime` | `LocalTime` | 이벤트 시각 (null 가능) |
| `eventTimezone` | `Timezone` | 이벤트의 타임존 |
| `userTimezone` | `Timezone` | 사용자의 타임존 |

### 반환 타입: `ConvertedDateTime`

| 필드 | 타입 | 설명 |
|---|---|---|
| `date()` | `LocalDate` | 변환된 날짜 |
| `time()` | `LocalTime` | 변환된 시각 (eventTime이 null이면 null) |
| `dateShifted()` | `boolean` | 변환 결과 날짜가 바뀌었는지 여부 |

## 사용 예시

### 1. 시간이 있는 이벤트 변환 (KST → UTC)

```java
ConvertedDateTime result = TimezoneConverter.convert(
    LocalDate.of(2026, 3, 1),
    LocalTime.of(15, 0),
    Timezone.ASIA_SEOUL,
    Timezone.UTC
);
// result.date() → 2026-03-01, result.time() → 06:00, dateShifted → false
```

### 2. 자정을 넘기는 변환 (KST 02:00 → UTC)

```java
ConvertedDateTime result = TimezoneConverter.convert(
    LocalDate.of(2026, 3, 1),
    LocalTime.of(2, 0),
    Timezone.ASIA_SEOUL,
    Timezone.UTC
);
// result.date() → 2026-02-28, result.time() → 17:00, dateShifted → true
```

### 3. 공휴일 등 시각이 없는 이벤트

```java
ConvertedDateTime result = TimezoneConverter.convert(
    LocalDate.of(2026, 1, 1),
    null,
    Timezone.ASIA_SEOUL,
    Timezone.UTC
);
// 날짜 그대로 반환, time → null, dateShifted → false
```

## 참고

- `dateShifted` 플래그를 활용하면 UI에서 "날짜가 변경되었습니다" 같은 안내를 표시할 수 있다.
- `eventTime`이 null인 경우(공휴일 등) 타임존 변환 없이 날짜를 그대로 반환한다.
- 동일 타임존 간 변환 시 입력값이 그대로 반환된다.
