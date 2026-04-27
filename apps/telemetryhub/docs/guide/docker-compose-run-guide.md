# TelemetryHub Docker Compose Run Guide

## 목적
이 문서는 현재 `apps/telemetryhub/docker/docker-compose.yml`을 어떻게 실행해야 하는지 빠르게 정리한 가이드다.

## 실행 전 준비
1. `apps/telemetryhub/docker/.env.example`를 `apps/telemetryhub/docker/.env`로 복사한다.
2. `apps/telemetryhub/docker/env/*.env.example`를 대응되는 `.env` 파일로 복사한다.
3. Kafka와 PostgreSQL 주소를 실제 인프라에 맞게 수정한다.

기본 템플릿은 아래를 가정한다.
- Kafka: `host.docker.internal:9092`
- PostgreSQL: `host.docker.internal:5432`

## 기본 실행
프로젝트 루트에서 실행한다.

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build
```

외부 접근 포트:
- MQTT broker: `1883`
- device-simulator: `8091`
- ingestion-service: `8092`
- stream-processor: `8093`
- analytics-api: `8094`

실제 서비스 컨테이너는 내부 network에서만 열리고, 외부 포트는 `edge` nginx가 publish 한다.

## batch-backfill 포함 실행
```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml --profile batch up -d --build
```

이 경우 `batch-edge`가 `8095`를 publish 한다.

## scale-out 실행 예시

### ingestion-service + analytics-api scale-out
```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build \
  --scale ingestion-service=3 \
  --scale analytics-api=2
```

### stream-processor까지 scale-out
```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build \
  --scale ingestion-service=3 \
  --scale analytics-api=2 \
  --scale stream-processor=2
```

`stream-processor`는 partition 수와 DB write contention을 같이 봐야 하므로 보수적으로 늘린다.

## 상태 확인
```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml ps
docker compose -f apps/telemetryhub/docker/docker-compose.yml logs -f edge
docker compose -f apps/telemetryhub/docker/docker-compose.yml logs -f stream-processor
```

health check 예시:

```bash
curl -fsS http://localhost:8092/actuator/health
curl -fsS http://localhost:8093/actuator/health
curl -fsS http://localhost:8094/actuator/health
```

## 중지
```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml down
```

batch profile까지 같이 내릴 때:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml --profile batch down
```
