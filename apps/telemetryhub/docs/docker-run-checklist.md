# TelemetryHub Docker Run Checklist

## 사전 확인
1. Kafka가 `host.docker.internal:9092`로 열리는지 확인한다.
2. PostgreSQL이 `host.docker.internal:5432`로 열리는지 확인한다.
3. DB `telemetryhub`가 실제로 존재하는지 확인한다.
4. DB 계정 `telemetryhub / telemetryhub`로 접속되는지 확인한다.

## 환경값 확인
1. DB 주소가 다르면 아래 파일의 값을 수정한다.
   - [stream-processor.env](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/stream-processor.env)
   - [analytics-api.env](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/analytics-api.env)
   - [batch-backfill.env](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/batch-backfill.env)
2. Kafka 주소가 다르면 아래 파일의 `SPRING_KAFKA_BOOTSTRAP_SERVERS`를 수정한다.
   - [ingestion-service.env](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/ingestion-service.env)
   - [stream-processor.env](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/stream-processor.env)

## 도커 실행
1. 기본 서비스만 올릴 때:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build
```

2. `batch-backfill`까지 포함할 때:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml --profile batch up -d --build
```

## 기동 확인
1. 각 서비스의 `/actuator/health`를 확인한다.
2. Prometheus를 쓰면 각 서비스 `/actuator/prometheus`가 scrape 가능한지 확인한다.

## 운영 모드 확인
1. simulator는 `published-message-buffer-enabled=false`인지 확인한다.
2. ingestion은 `recent-event-buffer-enabled=false`인지 확인한다.
3. ingestion이 `mode=KAFKA`, `key-strategy=DEVICE_ID`로 뜨는지 확인한다.
4. stream-processor의 `num-stream-threads`, `state-dir` 값이 의도한 운영값인지 확인한다.

## Smoke Test 순서
1. `simulator`
2. `mqtt`
3. `ingestion`
4. `kafka`
5. `stream-processor`
6. `analytics-api`
