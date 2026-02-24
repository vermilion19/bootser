# Booster Docker 환경

## 디렉토리 구조

```
dockers/
├── up.sh           # 실행 스크립트
├── down.sh       # 중지 스크립트
├── infrastructure/          # PostgreSQL, Redis, Kafka, Eureka, Gateway
│   ├── docker-compose.yml
│   ├── init-databases.sh    # DB 초기화 스크립트
│   └── .env.example
├── observability/           # Prometheus, Grafana, Loki, Tempo
│   └── docker-compose.yml
├── dday-backend/            # D-Day 백엔드
│   └── docker-compose.yml
└── dday-web/                # D-Day 프론트엔드
    └── docker-compose.yml
```

## 사전 준비

Docker Desktop이 실행 중이어야 한다.

모든 서비스의 Dockerfile은 **멀티스테이지 빌드**를 사용하므로 로컬에서 미리 JAR을 빌드할 필요가 없다.
Docker 내부에서 Gradle 빌드 → JRE 런타임 이미지 생성까지 자동으로 수행된다.

## 실행 / 중지 스크립트

### 전체 실행

```bash
./dockers/up.sh
```

### 특정 서비스만 실행

```bash
./dockers/up.sh infra          # infrastructure만
./dockers/up.sh dday-web       # dday-web만
./dockers/up.sh dday-back      # dday-backend만
./dockers/up.sh obs            # observability만
./dockers/up.sh infra dday-back dday-web   # 여러 개 조합
```

### 중지

```bash
./dockers/down.sh              # 전체 중지
./dockers/down.sh infra        # infrastructure만 중지
```

### 서비스 약어

| 대상 | 정식 이름 | 약어 |
|---|---|---|
| Infrastructure | `infrastructure` | `infra` |
| Observability | `observability` | `obs` |
| D-Day Backend | `dday-backend` | `dday-back` |
| D-Day Frontend | `dday-web` | - |

## 코드 수정 반영

`up.sh`은 내부적으로 `--build` 플래그를 포함하고 있어서, 코드를 수정한 뒤 다시 실행하면 이미지가 자동으로 재빌드된다.

직접 docker compose를 사용하는 경우에는 반드시 `--build`를 붙여야 한다:

```bash
docker compose -f dockers/dday-web/docker-compose.yml up -d --build
```

## 서비스 목록

### infrastructure

| 서비스 | 컨테이너 | 포트 | 이미지 |
|---|---|---|---|
| PostgreSQL | booster-postgres | 5432 | postgres:17 |
| Redis | booster-redis | 6379 | redis:7-alpine |
| Kafka (KRaft) | booster-kafka | 9092 | apache/kafka:4.0.0 |
| Eureka | booster-discovery | 8761 | 멀티스테이지 빌드 |
| Gateway | booster-gateway | 6000 | 멀티스테이지 빌드 |

PostgreSQL은 컨테이너 최초 기동 시 `init-databases.sh`로 4개 DB를 자동 생성한다:
`waiting_service_db`, `restaurant_service_db`, `noti_service_db`, `auth_service_db`

### observability

| 서비스 | 포트 | 이미지 |
|---|---|---|
| Loki | 3100 | grafana/loki:3.0.0 |
| Tempo | 3200, 9411 | grafana/tempo:2.4.0 |
| Prometheus | 9091 | prom/prometheus:latest |
| Grafana | 3000 | grafana/grafana:11.0.0 |

### dday-backend

| 서비스 | 컨테이너 | 포트 | 설명 |
|---|---|---|---|
| D-Day Service | booster-dday-service | 8080 | Spring Boot (멀티스테이지 빌드) |

Dockerfile이 프로젝트 루트를 빌드 컨텍스트로 사용하므로 별도의 JAR 빌드 없이 `docker compose up --build`만으로 빌드된다.

### dday-web

| 서비스 | 컨테이너 | 포트 | 설명 |
|---|---|---|---|
| D-Day Frontend | booster-dday-web | 3303 | Vite 빌드 + Nginx 서빙 |

## 상태 확인

```bash
# 컨테이너 상태
docker compose -f dockers/infrastructure/docker-compose.yml ps

# PostgreSQL DB 목록
docker exec booster-postgres psql -U postgres -l

# Redis 연결
docker exec booster-redis redis-cli ping

# Kafka 토픽 목록
docker exec booster-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Eureka 대시보드
# http://localhost:8761

# Grafana 대시보드
# http://localhost:3000 (admin / admin)
```

## 환경변수

`infrastructure/.env.example`을 `.env`로 복사하여 사용한다:

```bash
cp dockers/infrastructure/.env.example dockers/infrastructure/.env
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `POSTGRES_USER` | postgres | DB 사용자 |
| `POSTGRES_PASSWORD` | postgres | DB 비밀번호 |
| `JWT_SECRET` | (내장 기본값) | Gateway JWT 시크릿 |
| `TMDB_API_KEY` | dev-tmdb-key | TMDB API 키 (dday-backend) |
| `THESPORTSDB_API_KEY` | 3 | TheSportsDB API 키 (dday-backend) |

## 멀티스테이지 빌드

모든 Spring Boot / Vite 서비스의 Dockerfile은 멀티스테이지 빌드를 사용한다.

```
┌─────────────────────────────────────┐
│  Stage 1: Builder (JDK / Node)      │
│  - 소스 코드 복사                     │
│  - Gradle / npm 빌드                 │
│  - JAR 또는 정적 파일 생성             │
└──────────────┬──────────────────────┘
               │ COPY --from=builder
┌──────────────▼──────────────────────┐
│  Stage 2: Runtime (JRE / Nginx)     │
│  - 빌드 결과물만 복사                  │
│  - 최종 이미지 크기 최소화             │
└─────────────────────────────────────┘
```

멀티모듈 프로젝트이므로 빌드 컨텍스트는 프로젝트 루트(`../..`)로 설정되어 있다.
Dockerfile 내부에서 필요한 모듈(`libs/`, 해당 서비스)만 선택적으로 복사하여 빌드한다.

| Dockerfile 위치 | 빌드 대상 | 복사하는 모듈 |
|---|---|---|
| `infrastructure/discovery-service/Dockerfile` | Eureka Server | `infrastructure/discovery-service/` |
| `infrastructure/gateway-service/Dockerfile` | API Gateway | `libs/`, `infrastructure/gateway-service/` |
| `apps/d-day/d-day-service/Dockerfile` | D-Day Backend | `libs/`, `apps/d-day/` |
| `frontends/dday-web/Dockerfile` | D-Day Frontend | 자체 디렉토리 (독립 빌드) |

## 네트워크

모든 compose 파일은 `booster-network`라는 external 네트워크를 공유한다.
`up.sh` / `up.bat`이 자동으로 생성해주지만, 수동으로 만들려면:

```bash
docker network create booster-network
```
