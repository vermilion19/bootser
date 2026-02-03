# Gateway & Discovery Docker Compose

Eureka Discovery Service와 Spring Cloud Gateway를 실행합니다.

## 구성 요소

| 서비스 | 포트 | 설명 |
|--------|------|------|
| discovery-service | 8761 | Eureka Server (서비스 디스커버리) |
| gateway-service | 6000 | Spring Cloud Gateway (API Gateway) |

## 실행 방법

```bash
docker-compose up -d
```

## 접속

- Eureka Dashboard: http://localhost:8761
- Gateway: http://localhost:6000

## 의존성

다른 마이크로서비스들이 이 게이트웨이에 등록됩니다:
- restaurant-service
- waiting-service
- promotion-service
- notification-service
- auth-service
