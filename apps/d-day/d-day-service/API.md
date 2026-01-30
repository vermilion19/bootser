# D-Day Special Day API 문서

## 개요
특정 국가의 오늘 특별한 날(공휴일 등)을 조회하고, 가장 가까운 다음 특별한 날까지의 D-Day를 계산하는 API입니다.

## Base URL
```
/api/v1/special-days
```

---

## 엔드포인트 목록

| Method | Path | Description |
|--------|------|-------------|
| GET | /today | 오늘의 특별한 날 조회 + 다음 D-Day |

---

## API 상세

### 오늘의 특별한 날 조회

```
GET /api/v1/special-days/today
```

#### Description
사용자의 타임존 기준 오늘 날짜에 해당하는 특별한 날 목록과, 가장 가까운 미래의 특별한 날(D-Day)을 반환합니다.

#### Request Headers
| Header | Required | Description |
|--------|----------|-------------|
| Content-Type | No | 요청 본문 없음 (GET) |

#### Query Parameters
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| countryCode | String | Yes | - | ISO 3166-1 alpha-2 국가 코드 (e.g. `KR`, `US`, `JP`) |
| timezone | String | No | `UTC` | IANA 타임존 (e.g. `Asia/Seoul`, `America/New_York`) |

#### Request Example
```
GET /api/v1/special-days/today?countryCode=KR&timezone=Asia/Seoul
```

#### Response
```json
{
  "result": "SUCCESS",
  "data": {
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
  },
  "message": null,
  "errorCode": null
}
```

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| date | String (LocalDate) | No | 조회 기준 날짜 (사용자 타임존 기준) |
| countryCode | String | No | 조회한 국가 코드 |
| hasSpecialDay | boolean | No | 오늘 특별한 날 존재 여부 |
| specialDays | Array | No | 오늘의 특별한 날 목록 (없으면 빈 배열) |
| specialDays[].name | String | No | 특별한 날 이름 |
| specialDays[].category | String | No | 카테고리 (`PUBLIC_HOLIDAY`, `MEMORIAL_DAY` 등) |
| specialDays[].description | String | Yes | 설명 |
| upcoming | Object | Yes | 가장 가까운 미래 특별한 날 (없으면 null) |
| upcoming.name | String | No | 이름 |
| upcoming.date | String (LocalDate) | No | 날짜 |
| upcoming.daysUntil | long | No | 오늘부터 남은 일수 |
| upcoming.category | String | No | 카테고리 |

#### Response - 특별한 날 없음
```json
{
  "result": "SUCCESS",
  "data": {
    "date": "2026-03-02",
    "countryCode": "KR",
    "hasSpecialDay": false,
    "specialDays": [],
    "upcoming": {
      "name": "삼일절",
      "date": "2026-03-01",
      "daysUntil": 364,
      "category": "PUBLIC_HOLIDAY"
    }
  },
  "message": null,
  "errorCode": null
}
```

---

## 에러 코드

| Code | Status | Message | Description |
|------|--------|---------|-------------|
| SD-001 | 400 | 유효하지 않은 국가 코드입니다. | 지원하지 않는 countryCode 전달 시 |
| SD-002 | 400 | 유효하지 않은 타임존입니다. | 지원하지 않는 timezone 전달 시 |
| SD-003 | 500 | 공휴일 동기화에 실패했습니다. | 외부 API 동기화 실패 시 |

#### 에러 응답 예시
```json
{
  "result": "ERROR",
  "data": null,
  "message": "유효하지 않은 국가 코드: XX",
  "errorCode": "SD-001"
}
```
