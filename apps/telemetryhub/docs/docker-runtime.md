# TelemetryHub Docker Runtime

## 목적
이 문서는 `MQTT broker + TelemetryHub 마이크로서비스`만 Docker Compose로 올리고, Kafka / DB / Prometheus / Grafana는 기존 infrastructure를 재사용하는 실행 기준 문서다.

## 구성 범위
Compose에 포함되는 것:
- MQTT broker
- `device-simulator`
- `ingestion-service`
- `stream-processor`
- `analytics-api`
- `batch-backfill` (optional profile)

Compose에 포함하지 않는 것:
- Kafka
- DB
- Prometheus
- Grafana

즉 Kafka와 DB는 기존 infrastructure 주소를 env로 주입하고, Prometheus는 각 서비스의 `/actuator/prometheus`만 scrape 하면 된다.

## 파일 위치
- compose: [docker-compose.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/docker-compose.yml)
- 공용 Dockerfile: [Dockerfile](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/Dockerfile)
- broker 설정: [mosquitto.conf](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/mosquitto/mosquitto.conf)
- 공통 포트 템플릿: [.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/.env.example)
- 서비스별 env 템플릿:
  - [device-simulator.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/device-simulator.env.example)
  - [ingestion-service.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/ingestion-service.env.example)
  - [stream-processor.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/stream-processor.env.example)
  - [analytics-api.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/analytics-api.env.example)
  - [batch-backfill.env.example](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docker/env/batch-backfill.env.example)

## 실행 준비
1. `apps/telemetryhub/docker/.env.example`를 `.env`로 복사해서 포트를 필요하면 조정한다.
2. `apps/telemetryhub/docker/env/*.env.example`를 각각 `.env` 파일로 복사한다.
3. 서비스별 env 파일에서 Kafka / DB 주소를 기존 infrastructure 주소로 바꾼다.

기본 예시는 Docker 컨테이너에서 호스트 인프라를 바라보는 기준이라 `host.docker.internal`을 사용한다.

현재 기본 가정:
- Kafka: `host.docker.internal:9092`
- PostgreSQL: `host.docker.internal:5432`
- DB name: `telemetryhub`
- DB username: `telemetryhub`
- DB password: `telemetryhub`

즉 지금 템플릿은 “외부 인프라가 같은 머신 또는 접근 가능한 호스트에 이미 떠 있고, TelemetryHub 서비스만 컨테이너로 띄우는” 상황에 맞춰져 있다.

## 실행 명령
기본 서비스:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build
```

배치 포함:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml --profile batch up -d --build
```

## 운영 모드 기준
스케일아웃을 고려한 기본 운영 기준은 다음과 같다.

- simulator
  - `TELEMETRYHUB_SIMULATOR_RUNTIME_PUBLISHED_MESSAGE_BUFFER_ENABLED=false`
- ingestion
  - `TELEMETRYHUB_INGESTION_RUNTIME_RECENT_EVENT_BUFFER_ENABLED=false`
  - `TELEMETRYHUB_INGESTION_PUBLISHER_MODE=KAFKA`
  - `TELEMETRYHUB_INGESTION_PUBLISHER_KAFKA_KEY_STRATEGY=DEVICE_ID`
- stream-processor
  - `TELEMETRYHUB_STREAM_NUM_STREAM_THREADS`
  - `TELEMETRYHUB_STREAM_STATE_DIR`
  - `TELEMETRYHUB_STREAM_NUM_STANDBY_REPLICAS`

## Prometheus scrape 기준
각 서비스는 actuator prometheus endpoint를 노출한다.

- `device-simulator`: `http://localhost:8091/actuator/prometheus`
- `ingestion-service`: `http://localhost:8092/actuator/prometheus`
- `stream-processor`: `http://localhost:8093/actuator/prometheus`
- `analytics-api`: `http://localhost:8094/actuator/prometheus`
- `batch-backfill`: `http://localhost:8095/actuator/prometheus`

## 참고
- Kafka topic / partition 전략과 scale 기준은 [scale-readiness.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/scale-readiness.md)에 정리돼 있다.
