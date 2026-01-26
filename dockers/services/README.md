# Booster Docker Services

## Quick Start

```bash
# 1. 환경 변수 설정
cp .env.example .env

# 2. 인프라 실행 (postgres, redis, kafka)
docker-compose -f docker-compose.infra.yml up -d

# 3. 앱 서비스 실행 (discovery, gateway, apps)
docker-compose -f docker-compose.services.yml up -d

# 4. 프론트엔드 실행
docker-compose -f docker-compose.frontend.yml up -d
```

## 파일 구조

| 파일 | 설명 |
|------|------|
| docker-compose.infra.yml | 인프라 (DB, Redis, Kafka) |
| docker-compose.services.yml | 백엔드 서비스 (Discovery, Gateway, Apps) |
| docker-compose.frontend.yml | 프론트엔드 |

## Services

### Infrastructure (docker-compose.infra.yml)

| Service | Port | Description |
|---------|------|-------------|
| postgres | 5432 | PostgreSQL |
| redis | 6379 | Redis |
| zookeeper | 2181 | Zookeeper |
| kafka | 9092, 29092 | Kafka |
| kafka-ui | 8989 | Kafka UI |

### Backend Services (docker-compose.services.yml)

| Service | Port | Description |
|---------|------|-------------|
| discovery-service | 8761 | Eureka Server |
| gateway-service | 6000 | API Gateway |
| restaurant-service | 8081 (내부) | 식당 서비스 |
| waiting-service | 8082 (내부) | 웨이팅 서비스 |
| promotion-service | 8083 (내부) | 프로모션/쿠폰 서비스 |
| notification-service | 8084 (내부) | 알림 서비스 |
| auth-service | 8085 (내부) | 인증 서비스 |

> **Note**: 앱 서비스들(8081-8085)은 외부에 직접 노출되지 않습니다.
> 모든 요청은 Gateway(6000)를 통해 Eureka 기반으로 라우팅됩니다.

### Frontend (docker-compose.frontend.yml)

| Service | Port | Description |
|---------|------|-------------|
| user-web | 3300 | 사용자 웹 |

## Commands

```bash
# 인프라만 실행
docker-compose -f docker-compose.infra.yml up -d

# 인프라 + 서비스 실행
docker-compose -f docker-compose.infra.yml up -d
docker-compose -f docker-compose.services.yml up -d

# 전체 실행
docker-compose -f docker-compose.infra.yml up -d && \
docker-compose -f docker-compose.services.yml up -d && \
docker-compose -f docker-compose.frontend.yml up -d

# 특정 파일의 서비스 로그 확인
docker-compose -f docker-compose.services.yml logs -f waiting-service

# 특정 서비스만 빌드
docker-compose -f docker-compose.services.yml build waiting-service

# 특정 서비스만 재시작
docker-compose -f docker-compose.services.yml restart waiting-service

# 전체 중지
docker-compose -f docker-compose.infra.yml down
docker-compose -f docker-compose.services.yml down
docker-compose -f docker-compose.frontend.yml down

# 전체 중지 + 볼륨 삭제 (데이터 초기화)
docker-compose -f docker-compose.infra.yml down -v
```

## Scale Out

특정 서비스를 여러 인스턴스로 실행:

```bash
# waiting-service 3개로 확장
docker-compose -f docker-compose.services.yml up -d --scale waiting-service=3

# 여러 서비스 동시 확장
docker-compose -f docker-compose.services.yml up -d --scale waiting-service=3 --scale restaurant-service=2
```

Eureka Dashboard(http://localhost:8761)에서 인스턴스 확인 가능.

## Endpoints

- Eureka Dashboard: http://localhost:8761
- Gateway: http://localhost:6000
- Kafka UI: http://localhost:8989
- Frontend: http://localhost:3300
- API 예시: http://localhost:6000/waiting/...

## Troubleshooting

### 서비스가 시작되지 않을 때
```bash
# 헬스체크 상태 확인
docker-compose -f docker-compose.services.yml ps

# 특정 서비스 로그 확인
docker-compose -f docker-compose.services.yml logs waiting-service
```

### Kafka 연결 실패
Kafka가 완전히 시작될 때까지 기다려야 합니다 (약 30초).

### 메모리 부족
Docker Desktop 설정에서 메모리를 최소 8GB 이상으로 설정하세요.

### 네트워크 오류
서비스들이 다른 compose 파일에서 통신하려면 같은 네트워크를 사용해야 합니다.
infra를 먼저 실행하여 booster-network를 생성한 후 다른 서비스를 실행하세요.
