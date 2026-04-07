# Query Burst - Infrastructure Guide

## 서비스 구성

### Infrastructure (Tier 1)

| 서비스 | 이미지 | 포트 | 용도 |
|--------|--------|------|------|
| PostgreSQL | postgres:17 | 127.0.0.1:5432 | 메인 데이터베이스 |
| Redis | redis:7-alpine | 127.0.0.1:6379 | 캐시, 분산 락 |
| Kafka | apache/kafka:4.0.0 | 127.0.0.1:9092 | 메시지 브로커 |

### Observability (Tier 2-3)

| 서비스 | 이미지 | 포트 | 용도 |
|--------|--------|------|------|
| Prometheus | prom/prometheus:latest | 127.0.0.1:9091 | 메트릭 수집 |
| Loki | grafana/loki:3.0.0 | 127.0.0.1:3100 | 로그 수집 |
| Tempo | grafana/tempo:2.4.0 | 127.0.0.1:3200, 9411 | 트레이싱 수집 |
| Grafana | grafana/grafana:11.0.0 | 127.0.0.1:3000 | 시각화 대시보드 |

> 모든 포트는 `127.0.0.1`에 바인딩되어 **localhost에서만 접근 가능** (외부 접근 차단)

---

## Docker 사용법

### 시작

```bash
cd /path/to/booster/dockers
docker-compose up -d
```

### 중지

```bash
docker-compose down
```

### 상태 확인

```bash
docker-compose ps
```

### 로그 확인

```bash
# 전체 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f grafana
docker-compose logs -f postgres
```

### 재시작

```bash
# 전체 재시작
docker-compose restart

# 특정 서비스 재시작
docker-compose restart postgres
```

### 완전 초기화 (데이터 삭제)

```bash
docker-compose down -v
```

---

## 접속 방법

### Grafana (대시보드)

- URL: http://localhost:3000
- 계정: admin / admin (최초 로그인 후 변경 권장)

**로그 조회 방법:**
1. 좌측 메뉴 → Explore
2. 상단 데이터소스 → Loki 선택
3. Label filters → `application = query-burst`
4. Run query 클릭

### Prometheus (메트릭)

- URL: http://localhost:9091
- 메트릭 조회: http://localhost:9091/graph

**query-burst 메트릭 예시:**
```promql
# HTTP 요청 수
http_server_requests_seconds_count{application="query-burst"}

# JVM 메모리 사용량
jvm_memory_used_bytes{application="query-burst"}
```

### PostgreSQL

```bash
# CLI 접속
psql -h localhost -p 5432 -U postgres -d query_burst

# Docker exec 접속
docker exec -it booster-postgres psql -U postgres -d query_burst
```

### Redis

```bash
# CLI 접속
redis-cli -h localhost -p 6379

# Docker exec 접속
docker exec -it booster-redis redis-cli
```

### Kafka

```bash
# 토픽 목록 조회
docker exec -it booster-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# 토픽 메시지 확인
docker exec -it booster-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic [토픽명] --from-beginning
```

---

## 환경 변수 설정

### .env 파일 생성

```bash
cp .env.example .env
```

### .env 내용

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=query_burst
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### IntelliJ 설정 (EnvFile 플러그인)

1. Run → Edit Configurations
2. QueryBurstApplication 선택
3. EnvFile 탭 → Enable EnvFile 체크
4. + 버튼 → `.env` 파일 선택 (Cmd+Shift+. 으로 숨김파일 표시)
5. Apply → OK

---

## 실행 순서

```
1. Docker 서비스 시작
   docker-compose up -d

2. IntelliJ에서 query-burst 실행
   - Profile: dev
   - EnvFile: .env 로드

3. Grafana에서 로그/메트릭 확인
   http://localhost:3000
```

---

## 자동 재시작

모든 컨테이너는 `restart: always` 설정으로 **컴퓨터 재부팅 후에도 Docker 시작 시 자동으로 실행**됩니다.

Docker Desktop이 자동 시작되도록 설정하려면:
- Docker Desktop → Settings → General → "Start Docker Desktop when you sign in" 체크

---

## 트러블슈팅

### 포트 충돌

```bash
# 5432 포트 사용 중인 프로세스 확인
lsof -i :5432

# 기존 컨테이너 정리
docker ps -a | grep postgres
docker stop [컨테이너명] && docker rm [컨테이너명]
```

### Flyway 마이그레이션 오류

```bash
# 기존 DB에 Flyway 적용 (baseline 설정)
# application-dev.yml에 아래 설정 추가:
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 2
```

### 로그가 Loki에 안 보일 때

1. `dev` 프로파일로 실행 중인지 확인
2. `core-observability` 의존성 확인
3. Loki 컨테이너 상태 확인: `docker-compose logs loki`
