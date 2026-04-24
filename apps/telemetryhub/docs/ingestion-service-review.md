# TelemetryHub Ingestion Service Review

## 업데이트 2026-04-24

현재 구현은 초기 MVP 골격보다 다음 항목들이 더 엄격해졌다.

- MQTT subscriber scale-out을 위해 고유 `clientId` 해석과 shared subscription을 지원한다.
- MQTT inbound 처리는 더 이상 Paho callback thread에서 전부 처리하지 않는다.
- Kafka publish metrics는 `send()` 직후가 아니라 broker ack 기준으로 반영된다.
- Kafka producer 신뢰성 설정이 명시적으로 들어갔다.
- recent-event 디버그 버퍼는 여전히 사용할 수 있지만, 이제 무제한 trim 방식이 아니라 bounded 구조를 사용한다.

### 현재 MQTT 런타임 모델

`MqttInboundSubscriberLifecycle`는 이제 실제 런타임 `clientId`를 다음 순서로 결정한다.

1. `telemetryhub.ingestion.mqtt.client-id`
2. 선택값인 `telemetryhub.ingestion.mqtt.client-id-suffix`
3. suffix가 없으면 인스턴스용 fallback suffix

추가로 아래 설정도 지원한다.

- `telemetryhub.ingestion.mqtt.shared-subscription-enabled`
- `telemetryhub.ingestion.mqtt.shared-subscription-group`

즉 지금은 다음 두 방식으로 실행할 수 있다.

- 일반 topic subscription을 쓰는 단일 subscriber 모드
- shared subscription + 고유 client id를 쓰는 scale-out 모드

### 현재 MQTT inbound 스레딩 모델

이제 `messageArrived()`는 아래 작업만 한다.

- 수신 메타데이터 기록
- 카운터 증가
- 내부 bounded queue에 메시지 적재

실제 애플리케이션 처리는 아래 설정으로 제어되는 worker thread가 담당한다.

- `telemetryhub.ingestion.mqtt.inbound-queue-capacity`
- `telemetryhub.ingestion.mqtt.inbound-worker-threads`

`GET /ingestion/v1/mqtt/subscriber`에는 이제 아래 값도 포함된다.

- 총 수신 메시지 수
- 현재 queue 적재 수
- drop된 메시지 수

### 현재 Kafka publish 동작

`KafkaIngestionPublisher`는 이제:

- publish snapshot을 atomic update 함수로 갱신하고
- Kafka async callback이 성공적으로 끝났을 때만 success를 기록하며
- async publish failure도 같은 snapshot 모델에 반영한다

현재 producer 기본값은 `application.yml`에 명시돼 있다.

- `acks=all`
- `retries=3`
- `enable.idempotence=true`
- `max.in.flight.requests.per.connection=5`
- `linger.ms=5`
- `compression.type=lz4`

### 현재 in-memory 디버그 버퍼 동작

`InMemoryNormalizedRawEventStore`는 이제 bounded deque 방식을 사용한다.

즉:

- append가 capacity를 인지하고
- burst 트래픽에서 디버그 모드가 더 안전하며
- 운영 모드에서는 여전히 `telemetryhub.ingestion.runtime.recent-event-buffer-enabled=false`를 유지하는 것이 맞다

## 목적
`ingestion-service`는 시뮬레이터나 실제 디바이스가 보낸 메시지를 수신 경계에서 받아, 내부 파이프라인이 처리하기 쉬운 raw event 형태로 정규화하는 모듈이다.

현재 구현 목표는 세 가지다.

1. MQTT broker가 없어도 수집 로직을 먼저 개발할 수 있어야 한다.
2. broker가 생기면 실제 MQTT subscribe 경계로 자연스럽게 전환할 수 있어야 한다.
3. 최종적으로는 정규화된 raw event를 Kafka raw topic으로 내보낼 수 있어야 한다.

즉 지금의 `ingestion-service`는 단순한 API 서버가 아니라 아래 역할을 동시에 가진다.

- MQTT inbound adapter
- raw event normalizer
- publish router
- Kafka raw topic 진입점

## 전체 구조
```text
IngestionController
  -> IngestionService
     -> RawEventNormalizer
        -> JsonRawEventNormalizer
           -> IngestionTopicResolver
     -> IngestionPublisher
        -> RoutingIngestionPublisher
           -> LoggingIngestionPublisher
           -> InMemoryIngestionPublisher
           -> KafkaIngestionPublisher

MqttInboundSubscriberLifecycle
  -> MqttInboundAdapter
     -> IngestionService
```

## 핵심 흐름

### 1. 브로커 없는 개발 경로
```text
Simulator BRIDGE or 직접 HTTP 호출
  -> POST /ingestion/v1/mqtt/messages or /mqtt/messages/batch
  -> MqttInboundAdapter
  -> IngestionService
  -> JsonRawEventNormalizer
  -> RoutingIngestionPublisher
  -> MEMORY or KAFKA
```

### 2. 브로커 있는 실제 수집 경로
```text
Simulator MQTT publish
  -> MQTT Broker
  -> MqttInboundSubscriberLifecycle
  -> MqttInboundAdapter
  -> IngestionService
  -> JsonRawEventNormalizer
  -> RoutingIngestionPublisher
  -> MEMORY or KAFKA
```

### 3. 내부 처리 순서
1. 메시지가 topic, qos, payload 형태로 들어온다.
2. `IngestionService`가 수신 카운터를 올린다.
3. `JsonRawEventNormalizer`가 topic을 보고 이벤트 타입을 결정한다.
4. payload를 `contracts` 타입으로 파싱한다.
5. `eventId`, `deviceId`, `eventType`, `eventTime`을 검증한다.
6. `ingestTime`이 없으면 수신 시각으로 보정한다.
7. `NormalizedRawEvent`로 변환한다.
8. `RoutingIngestionPublisher`가 현재 모드에 따라 `LOGGING`, `MEMORY`, `KAFKA` 중 하나로 publish한다.
9. publish 성공/실패 결과가 metrics에 반영된다.

## 패키지별 역할

### 1. bootstrap
- `IngestionServiceApplication`
  - Spring Boot 시작점
  - `@ConfigurationPropertiesScan`으로 publisher / MQTT subscriber 설정을 바인딩한다.

### 2. application
- `IngestionMessage`
  - 수신 경계에서 들어오는 원본 입력 모델
  - `topic`, `qos`, `payload`, `receivedAt`을 가진다.
- `NormalizedRawEvent`
  - 정규화된 raw event 모델
  - 내부 파이프라인이 공통적으로 소비할 형태다.
  - `eventType`, `eventId`, `deviceId`, `eventTime`, `ingestTime`, `sourceTopic`, `kafkaKey`, `payload`를 가진다.
- `RawEventNormalizer`
  - 원본 입력을 `NormalizedRawEvent`로 바꾸는 인터페이스
- `IngestionPublisher`
  - 정규화된 이벤트를 어디로 보낼지 추상화한 인터페이스
- `IngestionPublishResult`
  - publish 결과 모델
  - 성공 여부, 대상, 실패 이유를 포함한다.
- `IngestionFailureStage`
  - 실패 단계 구분
  - 현재는 `NORMALIZE`, `PUBLISH`
- `IngestionMetricsSnapshot`
  - 수신/발행/실패 누적량과 마지막 실패 정보를 담는 메트릭 모델
- `IngestionService`
  - ingestion 핵심 서비스
  - 수신, normalize, publish, metrics 갱신을 담당한다.
- `MqttInboundAdapter`
  - MQTT에서 메시지가 들어온 것처럼 처리하는 adapter
  - HTTP bridge 경로와 실제 subscriber 경로가 둘 다 이 adapter를 탄다.
- `MqttInboundMetricsSnapshot`
  - MQTT inbound adapter 전용 메트릭 모델

### 3. config
- `IngestionPublisherProperties`
  - publisher 모드와 Kafka 설정을 바인딩한다.
  - `LOGGING`, `MEMORY`, `KAFKA`
- `IngestionMqttSubscriberProperties`
  - 실제 MQTT subscriber 설정을 바인딩한다.
  - broker URI, clientId, subscribe topic 목록 등을 포함한다.

### 4. infrastructure
- `IngestionTopicResolver`
  - topic suffix를 보고 이벤트 타입을 결정한다.
  - `/telemetry`, `/device-health`, `/driving-event`
- `JsonRawEventNormalizer`
  - payload JSON을 `contracts` 타입으로 파싱하고 검증한다.
  - topic과 payload의 eventType이 불일치하면 실패시킨다.
- `RoutingIngestionPublisher`
  - 현재 설정에 따라 logging/memory/kafka 중 하나로 라우팅한다.
- `LoggingIngestionPublisher`
  - 이벤트를 로그에만 남긴다.
- `InMemoryIngestionPublisher`
  - 정규화된 raw event를 메모리 저장소에 넣는다.
- `InMemoryNormalizedRawEventStore`
  - 최근 normalized raw event를 최대 500개까지 보관한다.
- `KafkaIngestionPublisher`
  - 정규화된 raw event를 Kafka raw topic으로 publish한다.
  - publish 관측 상태를 `KafkaPublishSnapshot`으로 유지한다.
- `KafkaPublishSnapshot`
  - Kafka publish 상태 모델
  - enable 여부, topic, 성공/실패 수, 마지막 publish 시각, 마지막 실패 이유를 담는다.
- `MqttInboundSubscriberLifecycle`
  - 실제 MQTT subscriber lifecycle
  - broker가 있을 때 Paho client로 subscribe하고 수신 메시지를 `MqttInboundAdapter`로 넘긴다.
- `MqttSubscriberState`
  - MQTT subscriber 상태 enum
- `MqttSubscriberSnapshot`
  - MQTT subscriber 상태 응답 모델

### 5. web
- `IngestionController`
  - 외부 진입 API
  - 일반 메시지 입력, MQTT inbound 입력, metrics, recent events, subscriber 상태 조회를 제공한다.

### 6. web.dto
- `IngestMessageRequest`
  - 일반 raw input 요청
- `MqttInboundMessageRequest`
  - MQTT 단건 메시지 요청
- `MqttInboundBatchRequest`
  - MQTT 배치 메시지 요청
- `NormalizedRawEventResponse`
  - 정규화 결과 응답
- `IngestionMetricsResponse`
  - ingestion 메트릭 응답
  - Kafka publish 상태도 포함한다.
- `MqttInboundMetricsResponse`
  - MQTT inbound adapter 메트릭 응답
- `MqttSubscriberResponse`
  - 실제 MQTT subscriber 상태 응답

## Publisher 모드 설명

### 1. `LOGGING`
- 정규화된 이벤트를 로그에만 남긴다.
- 가장 단순한 확인용 모드다.

### 2. `MEMORY`
- 정규화된 이벤트를 메모리 저장소에 적재한다.
- 현재 브로커/Kafka 없는 단계에서 가장 실용적인 기본 모드다.
- `GET /ingestion/v1/events`로 최근 normalized raw event를 확인할 수 있다.

### 3. `KAFKA`
- 정규화된 이벤트를 Kafka raw topic으로 publish한다.
- broker와 Kafka가 준비되면 실제 파이프라인 진입점이 된다.

현재 기본 설정은 [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/resources/application.yml) 기준 `MEMORY`다.

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: MEMORY
```

Kafka로 바꾸려면:

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: KAFKA
      kafka:
        enabled: true
        raw-topic: telemetryhub.raw-events
```

## MQTT inbound 경로 설명

### 1. HTTP bridge용 MQTT adapter
브로커가 없을 때는 simulator가 HTTP로 `/ingestion/v1/mqtt/messages/batch`를 호출한다.

이 경로의 장점:
- MQTT broker 없이도 MQTT inbound 경계를 검증할 수 있다.
- 나중에 broker가 생겨도 내부 로직은 그대로 재사용된다.

### 2. 실제 MQTT subscriber
broker가 있을 때는 `MqttInboundSubscriberLifecycle`이 직접 broker를 subscribe한다.

활성화 조건:

```yaml
telemetryhub:
  ingestion:
    mqtt:
      enabled: true
      real-client-enabled: true
```

이 경로의 장점:
- 실제 MQTT topic subscribe 검증 가능
- broker -> ingestion 실제 경로 smoke test 가능

## 실패 처리 정책
현재 실패는 두 단계로 나뉜다.

### 1. `NORMALIZE`
- topic이 비어 있음
- topic이 지원되지 않음
- payload가 비어 있음
- JSON 파싱 실패
- eventType 과 topic 불일치
- eventId / deviceId 누락

### 2. `PUBLISH`
- publisher가 실패 결과를 반환
- 예: `mode=KAFKA`인데 `kafka.enabled=false`
- 예: KafkaTemplate 전송 중 예외

이 정보는 `GET /ingestion/v1/metrics`에서 확인 가능하다.

## 현재 API 역할
- `POST /ingestion/v1/messages`
  - 일반 raw input 처리
- `POST /ingestion/v1/mqtt/messages`
  - MQTT 단건 메시지 경계 처리
- `POST /ingestion/v1/mqtt/messages/batch`
  - MQTT 배치 메시지 경계 처리
- `GET /ingestion/v1/metrics`
  - 전체 ingestion 메트릭 + Kafka publish 상태
- `GET /ingestion/v1/mqtt/metrics`
  - MQTT inbound adapter 메트릭
- `GET /ingestion/v1/mqtt/subscriber`
  - 실제 MQTT subscriber 상태
- `GET /ingestion/v1/events`
  - 메모리 저장된 normalized raw event 조회
- `POST /ingestion/v1/events/clear`
  - 메모리 저장소 초기화

## 현재 시점 평가
현재 `ingestion-service`는 MVP 1차 완료 상태로 볼 수 있다.

이미 가능한 것:
- 브로커 없는 bridge 경로 검증
- 실제 MQTT subscriber 경계 보유
- raw event normalize / enrich
- publisher mode 전환
- Kafka 연동 모드 준비
- 실패 단계 분류
- Kafka publish 상태 관측

아직 남은 것:
- 실제 broker + Kafka end-to-end smoke test
- DLQ 방향 정리
- observability 메트릭 세분화
- raw archive 경로 설계

## 권장 다음 단계
문서 정리 후에는 `stream-processor` 설계로 넘어가는 것이 맞다.

이유:
- simulator 와 ingestion 은 MVP 1차 기준으로 충분히 연결 가능한 상태다.
- 프로젝트의 다음 핵심 난도는 out-of-order / late event 를 반영하는 stream 처리 계층이다.
