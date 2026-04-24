# TelemetryHub Docker Runtime

## 업데이트 2026-04-24

Docker runtime 전제도 최신 구현 변경에 맞춰 정렬됐다.

### ingestion-service 런타임 기본값

현재 Docker env의 `ingestion-service`는 아래를 전제로 한다.

- 컨테이너별 고유 MQTT client id suffix
- scale-out을 위한 shared subscription 활성화
- bounded MQTT inbound queue
- 명시적인 Kafka producer 신뢰성 설정

관련 env key:

- `TELEMETRYHUB_INGESTION_MQTT_CLIENT_ID_SUFFIX`
- `TELEMETRYHUB_INGESTION_MQTT_SHARED_SUBSCRIPTION_ENABLED`
- `TELEMETRYHUB_INGESTION_MQTT_SHARED_SUBSCRIPTION_GROUP`
- `TELEMETRYHUB_INGESTION_MQTT_INBOUND_QUEUE_CAPACITY`
- `TELEMETRYHUB_INGESTION_MQTT_INBOUND_WORKER_THREADS`
- `SPRING_KAFKA_PRODUCER_ACKS`
- `SPRING_KAFKA_PRODUCER_RETRIES`
- `SPRING_KAFKA_PRODUCER_PROPERTIES_ENABLE_IDEMPOTENCE`
- `SPRING_KAFKA_PRODUCER_PROPERTIES_MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION`
- `SPRING_KAFKA_PRODUCER_PROPERTIES_LINGER_MS`
- `SPRING_KAFKA_PRODUCER_PROPERTIES_COMPRESSION_TYPE`

### stream-processor 런타임 기본값

현재 Docker runtime은 Kafka Streams local state를 tmp 데이터가 아니라 영속 컨테이너 데이터로 취급한다.

즉:

- Compose가 `/var/lib/telemetryhub-streams`를 마운트하고
- `TELEMETRYHUB_STREAM_STATE_DIR=/var/lib/telemetryhub-streams`
- `TELEMETRYHUB_STREAM_NUM_STANDBY_REPLICAS=1`

이 구성을 앞으로의 기본 런타임 형태로 보면 된다.

## 목적
이 문서는 `MQTT broker + TelemetryHub 마이크로서비스`만 Docker Compose로 올리고, Kafka / DB / Prometheus / Grafana는 기존 infrastructure를 재사용하는 실행 기준 문서다.

현재 compose는 다음 방향을 기본 전제로 한다.

- 마이크로서비스 컨테이너는 내부 Docker network에서만 직접 통신한다.
- 외부에서 접근할 포트는 `edge` nginx가 대신 publish 한다.
- `container_name`을 제거해서 `docker compose up --scale ...`가 가능하다.
- `stream-processor`는 replica 간 state 충돌을 피하기 위해 공유 named volume 대신 replica별 local volume을 사용한다.

## 구성 범위
Compose에 포함되는 것:
- MQTT broker
- edge proxy
- `device-simulator`
- `ingestion-service`
- `stream-processor`
- `analytics-api`
- `batch-backfill` (optional profile)
- `batch-edge` (optional profile)

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
1. `apps/telemetryhub/docker/.env.example`를 `.env`로 복사해서 edge publish 포트를 필요하면 조정한다.
2. `apps/telemetryhub/docker/env/*.env.example`를 각각 `.env` 파일로 복사한다.
3. 서비스별 env 파일에서 Kafka / DB 주소를 기존 infrastructure 주소로 바꾼다.

기본 예시는 Docker 컨테이너에서 호스트 인프라를 바라보는 기준이라 `host.docker.internal`을 사용한다.

현재 기본 가정:
- Kafka: `kafka:9092` (`booster-network` 내부 DNS)
- PostgreSQL: `host.docker.internal:5432`
- DB name: `telemetryhub`
- DB username: `wer2wer`
- DB password: `gozldwhwRk`

즉 지금 템플릿은 “Kafka는 `booster-network`를 통해 내부 DNS로 접근하고, PostgreSQL은 호스트 인프라를 바라보는” 상황에 맞춰져 있다.

## 실행 명령
기본 서비스:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build
```

배치 포함:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml --profile batch up -d --build
```

예시 scale-out:

```bash
docker compose -f apps/telemetryhub/docker/docker-compose.yml up -d --build \
  --scale ingestion-service=3 \
  --scale analytics-api=2
```

`stream-processor`도 replica를 늘릴 수는 있지만, Kafka partition 수와 read model write contention까지 같이 봐야 한다.

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
  - compose mounts `/var/lib/telemetryhub-streams` to a replica-local Docker volume for state store persistence

## Prometheus scrape 기준
각 서비스는 actuator prometheus endpoint를 노출한다.

외부에서는 edge proxy를 통해 기존 포트로 접근한다.

- `device-simulator`: `http://localhost:8091/actuator/prometheus`
- `ingestion-service`: `http://localhost:8092/actuator/prometheus`
- `stream-processor`: `http://localhost:8093/actuator/prometheus`
- `analytics-api`: `http://localhost:8094/actuator/prometheus`
- `batch-backfill`: `http://localhost:8095/actuator/prometheus` (`--profile batch`일 때)

## Stream State Persistence
`stream-processor` now uses:

- `TELEMETRYHUB_STREAM_STATE_DIR=/var/lib/telemetryhub-streams`
- `TELEMETRYHUB_STREAM_NUM_STANDBY_REPLICAS=1`

and Compose mounts a replica-local Docker volume at `/var/lib/telemetryhub-streams`.

This keeps Kafka Streams local state out of container tmp storage without forcing every replica to share the same filesystem path.

## Scale-Out 메모

- `device-simulator`
  - replica를 늘릴 수는 있지만 control API가 인스턴스별로 분산되므로 운영형 scale target으로 보긴 어렵다.
- `ingestion-service`
  - shared subscription과 고유 client id suffix를 사용하므로 compose scale-out 대상에 가장 적합하다.
- `analytics-api`
  - stateless read API라 edge proxy 뒤에서 수평 확장하기 쉽다.
- `stream-processor`
  - compose 레벨 scale-out은 가능하지만 실제 처리량은 Kafka partition 수와 DB write contention에 묶인다.
- `batch-backfill`
  - 대체로 singleton 또는 작업 단위 분리 실행이 더 안전하다.

## 참고
- Kafka topic / partition 전략과 scale 기준은 [scale-readiness.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/scale-readiness.md)에 정리돼 있다.
