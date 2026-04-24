# TelemetryHub Runtime Flows

## 목적
이 문서는 현재 구현된 런타임 경로를 빠르게 실행하고 확인하기 위한 운영용 메모다.

현재 기준으로 주요 실행 경로는 세 가지다.

1. `simulator -> bridge -> ingestion`
2. `simulator -> broker -> ingestion`
3. `ingestion -> kafka`

---

## 1. Simulator -> Bridge -> Ingestion

### 언제 쓰나
- MQTT broker가 아직 없을 때
- simulator 와 ingestion 연결을 먼저 검증하고 싶을 때

### 동작 구조
```text
device-simulator (BRIDGE mode)
  -> HTTP POST /ingestion/v1/mqtt/messages/batch
  -> ingestion-service MqttInboundAdapter
  -> normalize / enrich
  -> MEMORY or KAFKA publish
```

### simulator 설정
[apps/telemetryhub/device-simulator/src/main/resources/application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/resources/application.yml)

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: BRIDGE
      bridge:
        enabled: true
        ingestion-base-url: http://localhost:8092
        mqtt-batch-path: /ingestion/v1/mqtt/messages/batch
```

### ingestion 설정
[apps/telemetryhub/ingestion-service/src/main/resources/application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/resources/application.yml)

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: MEMORY
```

### 실행 후 확인 포인트
- simulator
  - `GET /sim/v1/metrics`
- ingestion
  - `GET /ingestion/v1/mqtt/metrics`
  - `GET /ingestion/v1/events`
  - `GET /ingestion/v1/metrics`

### 기대 결과
- simulator가 배치를 만들면 ingestion의 MQTT metrics가 증가한다.
- ingestion events 목록에 normalized raw event가 쌓인다.

---

## 2. Simulator -> Broker -> Ingestion

### 언제 쓰나
- MQTT broker를 Docker로 띄운 뒤 실제 subscribe 경계를 확인하고 싶을 때

### 동작 구조
```text
device-simulator (MQTT mode)
  -> MQTT Broker
  -> ingestion-service MQTT subscriber
  -> MqttInboundAdapter
  -> normalize / enrich
  -> MEMORY or KAFKA publish
```

### simulator 설정
```yaml
telemetryhub:
  simulator:
    publisher:
      mode: MQTT
      mqtt:
        enabled: true
        real-client-enabled: true
        broker-uri: tcp://localhost:1883
        client-id: telemetryhub-device-simulator
```

### ingestion 설정
```yaml
telemetryhub:
  ingestion:
    mqtt:
      enabled: true
      real-client-enabled: true
      broker-uri: tcp://localhost:1883
      client-id: telemetryhub-ingestion-service
```

### 실행 후 확인 포인트
- ingestion
  - `GET /ingestion/v1/mqtt/subscriber`
  - `GET /ingestion/v1/mqtt/metrics`
  - `GET /ingestion/v1/events`

### 기대 결과
- subscriber 상태가 `READY`
- MQTT metrics 수치 증가
- event 목록 증가

---

## 3. Ingestion -> Kafka

### 언제 쓰나
- normalized raw event를 Kafka raw topic으로 실제 publish 하고 싶을 때

### 동작 구조
```text
ingestion-service
  -> RoutingIngestionPublisher
  -> KafkaIngestionPublisher
  -> Kafka raw topic
```

### ingestion 설정
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

telemetryhub:
  ingestion:
    publisher:
      mode: KAFKA
      kafka:
        enabled: true
        raw-topic: telemetryhub.raw-events
```

### 실행 후 확인 포인트
- `GET /ingestion/v1/metrics`

여기서 확인할 수 있는 것:
- `totalPublished`
- `lastFailureStage`
- `lastFailureReason`
- `kafkaPublish.enabled`
- `kafkaPublish.totalPublished`
- `kafkaPublish.totalFailed`
- `kafkaPublish.lastPublishedAt`

### 기대 결과
- Kafka 연결이 정상이면 `kafkaPublish.totalPublished` 증가
- Kafka 미연결/비활성이면 `lastFailureStage=PUBLISH`와 실패 이유 확인 가능

---

## 추천 실행 순서

### 1단계
- `simulator -> bridge -> ingestion`
- broker 없이도 가장 빨리 전체 흐름을 확인할 수 있다.

### 2단계
- `simulator -> broker -> ingestion`
- broker subscribe 경계를 확인한다.

### 3단계
- `ingestion -> kafka`
- raw topic publish를 붙인다.

### 4단계
- `simulator -> broker -> ingestion -> kafka`
- 첫 번째 end-to-end smoke test

---

## 현재 기본값

### simulator
- 기본 publisher mode: `MEMORY`

### ingestion
- 기본 publisher mode: `MEMORY`
- 기본 mqtt subscriber: disabled

즉 지금 기본 상태로는 둘 다 안전한 로컬 검증 모드다.

---

## 관련 문서
- [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- [status.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/status.md)
