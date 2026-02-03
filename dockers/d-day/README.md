# D-Day Docker Compose

D-Day 서비스(Backend + Frontend)를 Docker로 실행합니다.

**주의**: 이 구성은 `dockers/infrastructure`가 먼저 실행되어 있어야 합니다.

## 사전 요구사항

### 1. Infrastructure 실행
```bash
cd ../infrastructure
cp .env.example .env
docker-compose up -d
```

### 2. 환경 변수 설정
```bash
cp .env.example .env
# .env 파일에서 TMDB_API_KEY, Google OAuth 설정
```

## 실행 방법

### 전체 실행 (Backend + Frontend)
```bash
docker-compose up -d
```

### 백엔드만 실행
```bash
docker-compose up -d dday-service
```

### 빌드 후 실행
```bash
docker-compose up -d --build
```

### 로그 확인
```bash
docker-compose logs -f dday-service
```

## 포트 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| dday-service | 8081 | Spring Boot 백엔드 |
| dday-web | 3302 | React 프론트엔드 (Nginx) |

## 접속

- Backend API: http://localhost:8081/api/v1/special-days/today
- Frontend: http://localhost:3302

## 의존성

이 서비스는 `booster-network`를 통해 다음 인프라에 연결됩니다:

| 서비스 | 호스트명 | 용도 |
|--------|---------|------|
| PostgreSQL | booster-postgres:5432 | 데이터베이스 (dday_service_db) |
| Redis | booster-redis:6379 | 캐시 |

## 트러블슈팅

### 네트워크 연결 실패
```bash
# infrastructure가 실행 중인지 확인
docker ps | grep booster-postgres

# 네트워크 확인
docker network inspect booster-network
```

### 빌드 실패
```bash
# 캐시 없이 재빌드
docker-compose build --no-cache
```
