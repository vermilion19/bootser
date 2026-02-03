# D-Day Docker Compose

D-Day 서비스(Backend + Frontend)를 Docker로 실행합니다.

**주의**: 이 구성은 `dockers/infrastructure`가 먼저 실행되어 있어야 합니다.

## 파일 구조

```
dockers/d-day/
├── docker-compose.yml      # 프로덕션 배포용
├── docker-compose.dev.yml  # 개발용 (핫 리로드 지원)
├── deploy.bat              # Windows 배포 스크립트
├── deploy.sh               # Linux/Mac 배포 스크립트
└── README.md
```

---

## 사전 요구사항

### 1. 네트워크 생성
```bash
docker network create booster-network
```

### 2. Infrastructure 실행
```bash
cd ../infrastructure
cp .env.example .env
docker-compose up -d
```

### 3. 환경 변수 설정
```bash
cp .env.example .env
# .env 파일에서 TMDB_API_KEY, Google OAuth 설정
```

---

## 프로덕션 배포

### 배포 스크립트 사용

```bash
cd dockers/d-day

# 전체 재빌드 + 재시작
deploy.bat

# 백엔드만 재빌드
deploy.bat backend

# 프론트엔드만 재빌드
deploy.bat frontend

# 캐시 무시하고 완전히 새로 빌드
deploy.bat --no-cache
```

### 명령어 설명

| 명령어 | 설명 |
|--------|------|
| `deploy.bat` | 변경된 서비스 감지해서 재빌드 |
| `deploy.bat backend` | Java 코드 수정 후 백엔드만 재배포 |
| `deploy.bat frontend` | React 코드 수정 후 프론트만 재배포 |
| `deploy.bat --no-cache` | 의존성 변경 시 완전 새로 빌드 |

### 수동 명령어

```bash
# 변경된 서비스 재빌드 + 재시작
docker-compose up --build -d

# 특정 서비스만 재빌드
docker-compose up --build -d dday-service

# 캐시 무시하고 완전히 새로 빌드
docker-compose build --no-cache dday-service && docker-compose up -d dday-service
```

> Docker는 레이어 캐시를 사용하므로, 코드만 변경된 경우 빌드가 빠릅니다.

---

## 개발 모드 (핫 리로드)

### 실행 방법

```bash
# 개발 모드로 실행
cd dockers/d-day
docker-compose -f docker-compose.dev.yml up
```

### 핫 리로드 방식

| 서비스 | 핫 리로드 방식 |
|--------|---------------|
| **d-day-service** | Gradle `--continuous` + Spring DevTools |
| **dday-web** | Vite Dev Server + HMR |

### 코드 변경 시 동작

- **Java 코드 수정** → Gradle이 감지 → 자동 재컴파일 → Spring DevTools가 재시작
- **React 코드 수정** → Vite HMR → 브라우저 자동 갱신 (새로고침 불필요)

---

## 포트 구성

| 서비스 | 포트 | 설명 |
|--------|------|------|
| dday-service | 8081 | Spring Boot 백엔드 |
| dday-web | 3302 | React 프론트엔드 (Nginx / Vite) |
| discovery-service | 8761 | Eureka Server |
| gateway-service | 6000 | Spring Cloud Gateway |

---

## 접속 URL

| 서비스 | URL |
|--------|-----|
| D-Day Frontend | http://localhost:3302 |
| D-Day API | http://localhost:8081/api/v1/special-days/today |
| D-Day API (via Gateway) | http://localhost:6000/api/v1/special-days/today |
| Eureka Dashboard | http://localhost:8761 |

---

## 의존성

이 서비스는 `booster-network`를 통해 다음 인프라에 연결됩니다:

| 서비스 | 호스트명 | 용도 |
|--------|---------|------|
| PostgreSQL | booster-postgres:5432 | 데이터베이스 (dday_service_db) |
| Redis | booster-redis:6379 | 캐시 |

---

## 환경 변수

`.env` 파일로 관리:

```bash
POSTGRES_USER=booster
POSTGRES_PASSWORD=booster1234
JWT_SECRET=your-secret-key
TMDB_API_KEY=your-tmdb-key
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

---

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

### 로그 확인
```bash
docker-compose logs -f dday-service
docker-compose logs -f dday-web
```
