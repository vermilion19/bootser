# Booster Docker Services

## Quick Start

```bash
# 1. 환경 변수 설정
cp .env.example .env

# 2. 전체 서비스 실행
docker-compose -f docker-compose.infra.yml up -d
docker-compose -f docker-compose.services.yml up -d
docker-compose -f docker-compose.frontend.yml up -d
# 3. 로그 확인
docker-compose logs -f
```

## Services

### Infrastructure

| Service | Port | Description |
|---------|------|-------------|
| discovery-service | 8761 | Eureka Server |
| gateway-service | 6000 | API Gateway |
| postgres | 5432 | PostgreSQL |
| redis | 6379 | Redis |
| kafka | 9092 | Kafka |

### Application Services (내부 전용)

| Service | 외부 접근 | 내부 포트 | Description |
|---------|----------|----------|-------------|
| waiting-service | Gateway 경유 | 8080 | 웨이팅 서비스 |
| restaurant-service | Gateway 경유 | 8080 | 식당 서비스 |
| notification-service | Gateway 경유 | 8080 | 알림 서비스 |
| auth-service | Gateway 경유 | 8080 | 인증 서비스 |
| promotion-service | Gateway 경유 | 8080 | 프로모션/쿠폰 서비스 |

> **Note**: 앱 서비스들은 외부에 직접 노출되지 않습니다.
> 모든 요청은 Gateway(6000)를 통해 Eureka 기반으로 라우팅됩니다.

## Commands

```bash
# 특정 서비스만 빌드
docker-compose build waiting-service

# 특정 서비스만 실행
docker-compose up -d waiting-service

# 서비스 재시작
docker-compose restart waiting-service

# 로그 확인 (특정 서비스)
docker-compose logs -f waiting-service

# 전체 중지
docker-compose down

# 전체 중지 + 볼륨 삭제 (데이터 초기화)
docker-compose down -v

# 이미지 재빌드 후 실행
docker-compose up -d --build
```

## Scale Out

특정 서비스를 여러 인스턴스로 실행:

```bash
# waiting-service 3개로 확장
docker-compose up -d --scale waiting-service=3

# 여러 서비스 동시 확장
docker-compose up -d --scale waiting-service=3 --scale restaurant-service=2
```

Eureka Dashboard(http://localhost:8761)에서 인스턴스 확인 가능.

## Infrastructure Only

앱 서비스 없이 인프라(DB, Redis, Kafka)만 실행:

```bash
docker-compose up -d postgres redis zookeeper kafka
```

## Endpoints

- Eureka Dashboard: http://localhost:8761
- Gateway: http://localhost:6000
- API 예시: http://localhost:6000/waiting/...

## Troubleshooting

### 서비스가 시작되지 않을 때
```bash
# 헬스체크 상태 확인
docker-compose ps

# 특정 서비스 로그 확인
docker-compose logs waiting-service
```

### Kafka 연결 실패
Kafka가 완전히 시작될 때까지 기다려야 합니다 (약 30초).

### 메모리 부족
Docker Desktop 설정에서 메모리를 최소 8GB 이상으로 설정하세요.