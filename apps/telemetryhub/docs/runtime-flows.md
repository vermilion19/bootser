# TelemetryHub Runtime Flows

## 목적
이 문서는 TelemetryHub를 실제로 실행할 때 어떤 서비스가 어떤 순서로 연결되는지 빠르게 파악하기 위한 런타임 흐름 문서다.

현재 기준으로 중요한 실행 흐름은 다음 다섯 가지다.

1. `simulator -> memory`
2. `simulator -> bridge -> ingestion`
3. `simulator -> broker -> ingestion`
4. `ingestion -> kafka -> stream-processor`
5. `analytics-api -> read model`

## 전체 흐름 요약
```text
device-simulator
  -> BRIDGE 또는 MQTT
  -> ingestion-service
  -> Kafka raw topic
  -> stream-processor
  -> read model tables
  -> analytics-api
```

## 1. Simulator 단독 확인 흐름
브로커도 ingestion도 없이 시뮬레이터 자체만 검증할 때 쓰는 흐름이다.

### 언제 쓰는가
- 시뮬레이터 이벤트 생성 규칙만 먼저 확인할 때
- 시나리오 변경이 payload에 어떻게 반영되는지 볼 때
- 브로커 준비 전 device-simulator만 점검할 때

### 동작 구조
```text
device-simulator
  -> event generator
  -> publisher mode = MEMORY
  -> in-memory published message store
```

### 주요 API
- `POST /sim/v1/start`
- `POST /sim/v1/stop`
- `POST /sim/v1/scale`
- `POST /sim/v1/scenario/{scenario}`
- `GET /sim/v1/status`
- `GET /sim/v1/metrics`
- `GET /sim/v1/preview`
- `GET /sim/v1/published-messages`
- `POST /sim/v1/published-messages/clear`

### 확인 포인트
- 루프가 실제로 시작되는지
- device 수와 publish interval이 반영되는지
- `preview`에서 event payload가 의도대로 생성되는지
- `published-messages`에 topic, qos, payload가 쌓이는지

### 기대 결과
- 브로커 없이도 시뮬레이터 내부 로직과 시나리오를 검증할 수 있다.

## 2. Simulator -> Bridge -> Ingestion
MQTT broker가 아직 없을 때 simulator와 ingestion을 연결하는 개발용 흐름이다.

### 언제 쓰는가
- broker 없이 ingestion 경계를 먼저 검증할 때
- simulator가 만든 topic/payload를 ingestion이 제대로 정규화하는지 볼 때
- 가장 빠른 end-to-end 초안 확인이 필요할 때

### 동작 구조
```text
device-simulator (publisher mode = BRIDGE)
  -> HTTP POST /ingestion/v1/mqtt/messages/batch
  -> ingestion-service MqttInboundAdapter
  -> IngestionService
  -> normalize / enrich
  -> MEMORY 또는 KAFKA publish
```

### simulator 핵심 설정
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

### ingestion 핵심 설정
개발 초반에는 보통 `MEMORY` 모드로 둔다.

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: MEMORY
```

### 실제 실행 순서
1. ingestion-service를 먼저 띄운다.
2. device-simulator를 띄운다.
3. simulator에 `POST /sim/v1/start`를 호출한다.
4. simulator가 배치를 만들고 bridge publisher가 ingestion으로 HTTP 요청을 보낸다.
5. ingestion이 이를 MQTT inbound처럼 받아 정규화한다.

### 점검 API
#### simulator
- `GET /sim/v1/metrics`

#### ingestion
- `GET /ingestion/v1/mqtt/metrics`
- `GET /ingestion/v1/metrics`
- `GET /ingestion/v1/events`

### 기대 결과
- simulator metrics의 생성량이 증가한다.
- ingestion의 MQTT metrics가 증가한다.
- ingestion recent events에 정규화된 raw event가 보인다.

## 3. Simulator -> Broker -> Ingestion
실제 MQTT broker를 가운데 두고 simulator와 ingestion을 연결하는 흐름이다.

### 언제 쓰는가
- 실제 MQTT subscribe 경계를 검증할 때
- broker를 포함한 런타임 smoke test를 할 때
- ingestion scale-out과 shared subscription을 점검할 때

### 동작 구조
```text
device-simulator (publisher mode = MQTT)
  -> MQTT Broker
  -> ingestion-service MqttInboundSubscriberLifecycle
  -> inbound queue
  -> worker threads
  -> MqttInboundAdapter
  -> IngestionService
  -> normalize / enrich
  -> MEMORY 또는 KAFKA publish
```

### simulator 핵심 설정
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

### ingestion 핵심 설정
```yaml
telemetryhub:
  ingestion:
    mqtt:
      enabled: true
      real-client-enabled: true
      broker-uri: tcp://localhost:1883
      client-id: telemetryhub-ingestion-service
      shared-subscription-enabled: true
      shared-subscription-group: telemetryhub-ingestion
```

### 실제 실행 순서
1. MQTT broker를 띄운다.
2. ingestion-service를 띄운다.
3. `GET /ingestion/v1/mqtt/subscriber`로 subscriber 상태를 확인한다.
4. device-simulator를 띄우고 `POST /sim/v1/start`를 호출한다.
5. simulator가 broker로 publish한다.
6. ingestion subscriber가 메시지를 받아 queue에 넣고 worker가 처리한다.

### 점검 API
#### ingestion
- `GET /ingestion/v1/mqtt/subscriber`
- `GET /ingestion/v1/mqtt/metrics`
- `GET /ingestion/v1/metrics`
- `GET /ingestion/v1/events`

### 확인 포인트
- subscriber `state`가 `READY`인지
- `subscriptions`가 의도한 topic인지
- `queuedMessages`가 계속 쌓이기만 하지 않는지
- `droppedMessages`가 증가하지 않는지

### 기대 결과
- MQTT broker를 통해 들어온 이벤트가 ingestion에서 정상 정규화된다.
- scale-out 시에는 shared subscription 기준으로 인스턴스 간 분산 수신 준비가 된다.

## 4. Ingestion -> Kafka -> Stream Processor
정규화된 raw event를 Kafka raw topic으로 보내고, stream-processor가 이를 읽어 집계하는 흐름이다.

### 언제 쓰는가
- 파이프라인의 핵심 실시간 집계를 검증할 때
- Kafka partition key 전략이 실제로 동작하는지 볼 때
- stream-processor write 메트릭을 점검할 때

### 동작 구조
```text
ingestion-service
  -> RoutingIngestionPublisher
  -> KafkaIngestionPublisher
  -> telemetryhub.raw-events
  -> stream-processor
  -> dedup / late-event filtering
  -> aggregate
  -> state store
  -> read model upsert
```

### ingestion 핵심 설정
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
        key-strategy: DEVICE_ID
```

### stream-processor 핵심 설정
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

telemetryhub:
  stream:
    source-topic: telemetryhub.raw-events
    late-event-grace: 2m
```

### 실제 실행 순서
1. Kafka를 준비한다.
2. stream-processor를 먼저 띄운다.
3. ingestion-service를 `mode=KAFKA`로 띄운다.
4. simulator 또는 broker 경로로 ingestion에 이벤트를 넣는다.
5. ingestion이 Kafka raw topic으로 publish한다.
6. stream-processor가 이를 읽어 집계와 DB upsert를 수행한다.

### 점검 API
#### ingestion
- `GET /ingestion/v1/metrics`

#### stream-processor
- `GET /stream/v1/metrics`

### ingestion에서 확인할 값
- `totalPublished`
- `lastFailureStage`
- `lastFailureReason`
- `kafkaPublish.totalPublished`
- `kafkaPublish.totalFailed`

### stream-processor에서 확인할 값
- `totalProjectionWrites`
- `totalProjectionWriteFailures`
- `deviceLastSeen.writes`
- `eventsPerMinute.writes`
- `drivingEventCounter.writes`
- `regionHeatmap.writes`

### 기대 결과
- ingestion의 Kafka publish 성공 수가 증가한다.
- stream-processor projection write 수가 증가한다.
- read model 테이블이 채워진다.

## 5. Analytics API 조회 흐름
stream-processor가 만든 read model을 analytics-api가 조회하는 흐름이다.

### 언제 쓰는가
- 최종 사용자나 운영자가 집계 결과를 API로 조회할 때
- read model이 실제 serving에 사용 가능한지 확인할 때

### 동작 구조
```text
analytics-api
  -> AnalyticsQueryService
  -> native SQL
  -> telemetryhub_* read model tables
```

### 주요 조회 API
- `GET /analytics/v1/devices/latest`
- `GET /analytics/v1/devices/{deviceId}/latest`
- `GET /analytics/v1/events-per-minute`
- `GET /analytics/v1/driving-events/counters`
- `GET /analytics/v1/heatmap/regions`

### 실제 실행 순서
1. ingestion과 stream-processor가 먼저 데이터를 쌓는다.
2. analytics-api를 띄운다.
3. 각 조회 API로 read model 내용을 확인한다.

### 기대 결과
- 최신 디바이스 상태
- 분당 이벤트 수
- driving event 카운트
- 지역 heatmap 집계
를 API로 조회할 수 있다.

## 추천 실행 순서
### 1단계
- `simulator -> memory`
- 시뮬레이터 자체가 정상인지 먼저 본다.

### 2단계
- `simulator -> bridge -> ingestion`
- broker 없이 ingestion 정규화 경계를 확인한다.

### 3단계
- `simulator -> broker -> ingestion`
- 실제 MQTT 경계를 확인한다.

### 4단계
- `ingestion -> kafka -> stream-processor`
- 실시간 집계와 read model 반영을 확인한다.

### 5단계
- `analytics-api -> read model`
- 최종 조회 경로를 확인한다.

## 기본 모드 정리
### device-simulator
- 기본 publisher mode는 `MEMORY`

### ingestion-service
- 기본 publisher mode는 `MEMORY`
- 기본 MQTT subscriber는 비활성

### stream-processor
- source topic을 읽는 실시간 집계 서비스
- 외부 Kafka와 DB가 준비되어야 의미 있는 데이터가 쌓인다

### analytics-api
- read model이 이미 채워져 있어야 결과가 나온다

## 실전 smoke test 흐름
가장 현실적인 smoke test는 아래 순서다.

1. MQTT broker 실행
2. ingestion-service 실행
3. stream-processor 실행
4. analytics-api 실행
5. device-simulator 실행
6. simulator start 호출
7. ingestion metrics 확인
8. stream metrics 확인
9. analytics 조회 확인

## 관련 문서
- [device-simulator-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/device-simulator-review.md)
- [ingestion-service-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/ingestion-service-review.md)
- [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md)
- [analytics-api-review.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/analytics-api-review.md)
- [docker-runtime.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/docker-runtime.md)
- [docker-run-checklist.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/docker-run-checklist.md)
