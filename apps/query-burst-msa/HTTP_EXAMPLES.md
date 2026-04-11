# query-burst-msa HTTP examples

## 1. catalog-service 데이터 준비

```http
POST http://localhost:18113/api/categories
Content-Type: application/json

{
  "name": "flash-sale",
  "parentId": null
}
```

```http
POST http://localhost:18113/api/products
Content-Type: application/json

{
  "name": "SSD 2TB",
  "price": 159000,
  "stock": 50,
  "status": "ACTIVE",
  "categoryId": 301,
  "sellerId": 1001
}
```

## 2. member-service 데이터 준비

```http
POST http://localhost:18114/api/members
Content-Type: application/json

{
  "email": "msa-user@booster.dev",
  "name": "msa user",
  "grade": "VIP",
  "region": "SEOUL"
}
```

## 3. order-service 주문 생성

```http
POST http://localhost:18115/api/orders
Content-Type: application/json
Idempotency-Key: demo-order-1

{
  "memberId": 1001,
  "items": [
    {
      "productId": 2001,
      "quantity": 2,
      "unitPrice": 159000
    }
  ]
}
```

## 4. Kafka 기반 read model 확인

`order-service`가 `order-events` 토픽으로 이벤트를 발행하면:

- `analytics-service`는 `GET http://localhost:18111/api/statistics/top-products?date=2026-04-11`
- `ranking-service`는 `GET http://localhost:18112/api/rankings/realtime?windowHours=1&size=10`

으로 반영 결과를 조회한다.
