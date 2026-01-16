# Booster API Endpoints

Gateway를 통한 API 호출 가이드입니다.

**Base URL:** `http://localhost:6000`

---

## Auth Service

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/auth/v1/signup` | 회원가입 |
| POST | `/auth/v1/login` | 로그인 |

### 예시

```bash
# 회원가입
curl -X POST http://localhost:6000/auth/v1/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "1234", "role": "CUSTOMER"}'

# 로그인
curl -X POST http://localhost:6000/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "1234"}'
```

---

## Restaurant Service

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/restaurants/v1` | 식당 등록 |
| GET | `/restaurants/v1` | 전체 식당 조회 |
| GET | `/restaurants/v1/{restaurantId}` | 식당 상세 조회 |
| PATCH | `/restaurants/v1/{restaurantId}` | 식당 정보 수정 |
| POST | `/restaurants/v1/{restaurantId}/open` | 영업 시작 |
| POST | `/restaurants/v1/{restaurantId}/close` | 영업 종료 |
| POST | `/restaurants/v1/{restaurantId}/entry?partySize={n}` | 입장 처리 |
| POST | `/restaurants/v1/{restaurantId}/exit?partySize={n}` | 퇴장 처리 |

### 예시

```bash
# 식당 등록
curl -X POST http://localhost:6000/restaurants/v1 \
  -H "Content-Type: application/json" \
  -d '{"name": "맛있는 식당", "address": "서울시 강남구", "capacity": 50}'

# 전체 식당 조회
curl -X GET http://localhost:6000/restaurants/v1

# 식당 상세 조회
curl -X GET http://localhost:6000/restaurants/v1/1

# 식당 정보 수정
curl -X PATCH http://localhost:6000/restaurants/v1/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "더 맛있는 식당"}'

# 영업 시작
curl -X POST http://localhost:6000/restaurants/v1/1/open

# 영업 종료
curl -X POST http://localhost:6000/restaurants/v1/1/close

# 입장 처리 (4명)
curl -X POST "http://localhost:6000/restaurants/v1/1/entry?partySize=4"

# 퇴장 처리 (4명)
curl -X POST "http://localhost:6000/restaurants/v1/1/exit?partySize=4"
```

---

## Waiting Service

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/waitings/v1` | 대기 등록 |
| GET | `/waitings/v1/{waitingId}` | 내 대기 상태 조회 |
| PATCH | `/waitings/v1/{waitingId}/postpone` | 순서 미루기 |
| PATCH | `/waitings/v1/{waitingId}/cancel` | 대기 취소 |
| PATCH | `/waitings/v1/{waitingId}/enter` | 입장 처리 |
| PATCH | `/waitings/v1/{waitingId}/call` | 대기 호출 (사장님용) |
| GET | `/waitings/v1/restaurants/{restaurantId}?cursor={n}&size={n}` | 대기 목록 조회 |

### 예시

```bash
# 대기 등록
curl -X POST http://localhost:6000/waitings/v1 \
  -H "Content-Type: application/json" \
  -d '{"restaurantId": 1, "userId": 1, "partySize": 4, "phoneNumber": "010-1234-5678"}'

# 내 대기 상태 조회
curl -X GET http://localhost:6000/waitings/v1/1

# 순서 미루기
curl -X PATCH http://localhost:6000/waitings/v1/1/postpone \
  -H "Content-Type: application/json" \
  -d '{"restaurantId": 1}'

# 대기 취소
curl -X PATCH http://localhost:6000/waitings/v1/1/cancel

# 입장 처리
curl -X PATCH http://localhost:6000/waitings/v1/1/enter

# 대기 호출 (사장님)
curl -X PATCH http://localhost:6000/waitings/v1/1/call

# 대기 목록 조회 (커서 기반 페이지네이션)
curl -X GET "http://localhost:6000/waitings/v1/restaurants/1?size=20"
curl -X GET "http://localhost:6000/waitings/v1/restaurants/1?cursor=10&size=20"
```

---

## Promotion Service (Coupon)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/coupons/v1` | 쿠폰 생성 |
| POST | `/coupons/v1/{couponId}/issue` | 쿠폰 발급 |

### 예시

```bash
# 쿠폰 생성
curl -X POST http://localhost:6000/coupons/v1 \
  -H "Content-Type: application/json" \
  -d '{"name": "신규 가입 쿠폰", "discountAmount": 5000, "totalQuantity": 100}'

# 쿠폰 발급
curl -X POST http://localhost:6000/coupons/v1/1/issue \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

---

## Gateway Routing 요약

| Path Pattern | Target Service |
|--------------|----------------|
| `/auth/**` | auth-service |
| `/restaurants/**` | restaurant-service |
| `/waitings/**` | waiting-service |
| `/coupons/**` | promotion-service |

---

## 기타 인프라 URL

| 서비스 | URL | 설명 |
|--------|-----|------|
| Gateway | http://localhost:6000 | API Gateway |
| Eureka Dashboard | http://localhost:8761 | 서비스 디스커버리 |
| Kafka UI | http://localhost:8989 | Kafka 모니터링 |
| Jenkins | http://localhost:9090 | CI/CD |