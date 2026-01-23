---
name: api
description: Controller의 REST API 문서를 마크다운으로 생성
user-invocable: true
---

## 작업

$ARGUMENTS로 지정된 Controller 클래스의 API 문서를 생성합니다.

### 문서 포함 내용

1. API 개요
2. Base URL
3. 각 엔드포인트 상세
4. Request/Response 예시
5. 에러 코드

### 문서 형식

```markdown
# [서비스명] API 문서

## 개요
서비스에 대한 간단한 설명

## Base URL
\`\`\`
/api/v1/[resource]
\`\`\`

---

## 엔드포인트 목록

| Method | Path | Description |
|--------|------|-------------|
| POST | /waitings | 웨이팅 등록 |
| GET | /waitings/{id} | 웨이팅 조회 |

---

## API 상세

### 웨이팅 등록

\`\`\`
POST /api/v1/waitings
\`\`\`

#### Description
새로운 웨이팅을 등록합니다.

#### Request Headers
| Header | Required | Description |
|--------|----------|-------------|
| Content-Type | Yes | application/json |

#### Request Body
\`\`\`json
{
  "restaurantId": 1,
  "partySize": 4,
  "phoneNumber": "010-1234-5678"
}
\`\`\`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| restaurantId | Long | Yes | 식당 ID |
| partySize | Integer | Yes | 인원 수 |
| phoneNumber | String | Yes | 연락처 |

#### Response
\`\`\`json
{
  "code": "SUCCESS",
  "data": {
    "waitingId": 123,
    "waitingNumber": 15,
    "estimatedWaitTime": 30
  }
}
\`\`\`

#### Error Codes
| Code | Message | Description |
|------|---------|-------------|
| DUPLICATE_WAITING | 이미 등록된 웨이팅 | 동일 식당에 중복 등록 |
| RESTAURANT_NOT_FOUND | 식당을 찾을 수 없음 | 존재하지 않는 식당 |
```

### 분석 대상

- `@RestController`, `@Controller` 클래스
- `@RequestMapping`, `@GetMapping`, `@PostMapping` 등
- Request/Response DTO
- `@Valid`, `@NotNull` 등 validation 어노테이션
- 예외 처리 (`@ExceptionHandler`)

### 사용 예시

```
/api WaitingController            # WaitingController API 문서 생성
/api src/main/.../controller/     # 폴더 내 모든 Controller 문서화
/api                              # 현재 열린 Controller 문서화
```