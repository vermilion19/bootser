# Booster Infrastructure

모든 Booster 서비스가 공통으로 사용하는 인프라 컨테이너입니다.

`restart: always` 설정으로 Mac/PC 재시작 시에도 자동으로 실행됩니다.

## 구성 요소

| 서비스 | 포트 | 설명 |
|--------|------|------|
| PostgreSQL | 5432 | 공통 데이터베이스 |
| Redis | 6379 | 캐시 & 세션 |
| Zookeeper | 2181 | Kafka 의존성 |
| Kafka | 9092, 29092 | 메시지 브로커 |
| Kafka UI | 8989 | Kafka 관리 도구 |

## 생성되는 데이터베이스

- `auth_service_db`
- `restaurant_service_db`
- `waiting_service_db`
- `noti_service_db`
- `promotion_service_db`
- `dday_service_db`

## 실행 방법

### 1. 환경 변수 설정
```bash
cp .env.example .env
# 필요시 .env 파일 수정
```

### 2. 실행
```bash
docker-compose up -d
```

### 3. 상태 확인
```bash
docker-compose ps
```

### 4. 로그 확인
```bash
docker-compose logs -f postgres
docker-compose logs -f kafka
```

## 개별 서비스 실행

```bash
# DB만 실행
docker-compose up -d postgres

# DB + Redis만 실행
docker-compose up -d postgres redis

# Kafka 없이 실행
docker-compose up -d postgres redis
```

## 중지

```bash
# 중지 (데이터 유지)
docker-compose down

# 완전 삭제 (볼륨 포함)
docker-compose down -v
```

## 네트워크

`booster-network` 네트워크를 생성하여 다른 서비스들과 연결됩니다.

```bash
# 네트워크 확인
docker network ls | grep booster

# 연결된 컨테이너 확인
docker network inspect booster-network
```

## 다른 서비스에서 연결

### 로컬 개발 (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dday_service_db
    username: booster
    password: booster1234
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

### Docker 내부 (다른 컨테이너에서)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://booster-postgres:5432/dday_service_db
  data:
    redis:
      host: booster-redis
  kafka:
    bootstrap-servers: booster-kafka:29092
```

## 트러블슈팅

### 포트 충돌 시
```bash
# 사용 중인 포트 확인
lsof -i :5432
lsof -i :6379
lsof -i :9092

# 기존 프로세스 종료 후 재시작
docker-compose down && docker-compose up -d
```

### DB 초기화가 안 될 때
```bash
# 볼륨 삭제 후 재시작
docker-compose down -v
docker-compose up -d
```

### Kafka 연결 실패 시
```bash
# Kafka 헬스체크 확인
docker-compose logs kafka

# Zookeeper 먼저 확인
docker-compose logs zookeeper
```
