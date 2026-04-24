# TelemetryHub Ingestion Service Review

## 목적
`ingestion-service`는 시뮬레이터나 실제 디바이스가 보낸 메시지를 수신 경계에서 받아, 파이프라인이 공통으로 처리할 수 있는 정규화된 raw event로 변환하는 서비스다.

현재 이 서비스는 다음 역할을 맡는다.

- MQTT 스타일 메시지 수신
- topic 기반 이벤트 타입 판별
- payload JSON 파싱 및 공통 메타데이터 추출
- `ingestTime` 보정
- Kafka 또는 메모리로 publish
- 수신/정규화/발행 상태 관측

## 전체 구조
```text
IngestionController
  -> IngestionService
     -> RawEventNormalizer
        -> JsonRawEventNormalizer
     -> IngestionPublisher
        -> RoutingIngestionPublisher
           -> LoggingIngestionPublisher
           -> InMemoryIngestionPublisher
           -> KafkaIngestionPublisher

MqttInboundSubscriberLifecycle
  -> inbound queue
  -> worker threads
  -> MqttInboundAdapter
     -> IngestionService
```

## 처리 흐름
### 1. 일반 HTTP 수집 경로
```text
POST /ingestion/v1/messages
  -> IngestionController.ingest()
  -> IngestionService.ingest()
  -> JsonRawEventNormalizer.normalize()
  -> RoutingIngestionPublisher.publish()
```

### 2. MQTT 경계 흉내 경로
```text
POST /ingestion/v1/mqtt/messages
POST /ingestion/v1/mqtt/messages/batch
  -> IngestionController
  -> MqttInboundAdapter
  -> IngestionService
```

### 3. 실제 MQTT subscriber 경로
```text
MQTT Broker
  -> MqttInboundSubscriberLifecycle.messageArrived()
  -> inbound queue 적재
  -> worker thread
  -> MqttInboundAdapter.receive()
  -> IngestionService.ingest()
```

## 핵심 클래스 역할
### `IngestionController`
- 외부 HTTP API 진입점이다.
- 일반 메시지 수집, MQTT 흉내 수집, 메트릭 조회, 최근 이벤트 조회를 담당한다.

### `IngestionService`
- 수집 서비스의 핵심 orchestration 레이어다.
- 수신 카운트 증가, 정규화 호출, publish 호출, 실패 단계 기록을 맡는다.

### `JsonRawEventNormalizer`
- topic suffix를 보고 `TELEMETRY`, `DEVICE_HEALTH`, `DRIVING_EVENT` 중 무엇인지 판별한다.
- payload를 `contracts`의 이벤트 타입으로 파싱한다.
- `eventId`, `deviceId`, `eventTime`, `eventType`, `ingestTime`을 정규화한다.
- Kafka partition key로 쓸 `kafkaKey`도 여기서 결정된다.

### `RoutingIngestionPublisher`
- 현재 설정된 publisher mode에 따라 `LOGGING`, `MEMORY`, `KAFKA` 중 하나로 라우팅한다.

### `KafkaIngestionPublisher`
- 정규화된 raw event를 Kafka raw topic으로 보낸다.
- ack 기반 성공/실패 카운트를 관리한다.

### `MqttInboundAdapter`
- MQTT에서 들어온 것처럼 처리되는 입력을 `IngestionService`로 넘기는 adapter다.
- HTTP bridge 경로와 실제 subscriber 경로가 이 adapter를 공유한다.

### `MqttInboundSubscriberLifecycle`
- Paho MQTT client lifecycle을 관리한다.
- 고유 `clientId`를 계산한다.
- shared subscription을 사용할 수 있다.
- callback thread에서는 큐 적재만 수행하고 실제 처리 로직은 worker thread로 넘긴다.

## Publisher 모드
### `LOGGING`
- 정규화된 이벤트를 로그만 남긴다.
- 구조 확인용이다.

### `MEMORY`
- 정규화된 이벤트를 메모리 저장소에 적재한다.
- 브로커와 Kafka가 아직 없을 때 개발용으로 가장 안전하다.
- `GET /ingestion/v1/events`로 최근 이벤트를 확인할 수 있다.

### `KAFKA`
- 정규화된 이벤트를 Kafka raw topic으로 publish한다.
- 운영이나 end-to-end smoke test에서 사용하는 모드다.

## MQTT subscriber 모드
### 핵심 설정
- `telemetryhub.ingestion.mqtt.enabled`
- `telemetryhub.ingestion.mqtt.real-client-enabled`
- `telemetryhub.ingestion.mqtt.client-id`
- `telemetryhub.ingestion.mqtt.client-id-suffix`
- `telemetryhub.ingestion.mqtt.shared-subscription-enabled`
- `telemetryhub.ingestion.mqtt.shared-subscription-group`
- `telemetryhub.ingestion.mqtt.inbound-queue-capacity`
- `telemetryhub.ingestion.mqtt.inbound-worker-threads`

### 현재 동작 방식
- 인스턴스마다 고유 `clientId`를 만든다.
- shared subscription을 켜면 여러 ingestion 인스턴스가 같은 topic workload를 나눠 받을 수 있다.
- callback thread는 큐 적재까지만 한다.
- 큐가 가득 차면 메시지를 drop하고 `droppedMessages`를 증가시킨다.

## 실패 처리 정책
### `NORMALIZE`
- topic이 비어 있음
- topic이 지원되지 않음
- payload가 비어 있음
- JSON 파싱 실패
- topic과 payload의 이벤트 타입 불일치
- 필수 메타데이터 누락

### `PUBLISH`
- publisher가 실패 결과를 반환
- Kafka publisher 비활성 상태에서 `mode=KAFKA`
- Kafka publish callback 실패

## API 상세 설명
## `POST /ingestion/v1/messages`
일반 HTTP 메시지를 직접 수집할 때 사용하는 API다.

### 요청 바디
```json
{
  "topic": "telemetryhub/devices/device-001/telemetry",
  "qos": 1,
  "payload": "{\"metadata\":{...},\"latitude\":37.5,...}"
}
```

### 요청 필드
- `topic`
  - 필수
  - 공백 불가
  - 이벤트 타입 판별에 사용된다.
- `qos`
  - 필수
  - `0` 또는 `1`
  - MQTT처럼 메시지 전달 품질을 표현하지만, 현재는 메타데이터 성격이 더 강하다.
- `payload`
  - 필수
  - 공백 불가
  - JSON 문자열이어야 한다.

### 응답 필드
- `eventType`
  - 정규화된 이벤트 타입
- `eventId`
  - 이벤트 고유 식별자
- `deviceId`
  - 디바이스 식별자
- `eventTime`
  - 디바이스가 발생시킨 시각
- `ingestTime`
  - ingestion이 수신한 시각 또는 payload에 있던 ingest 시각
- `sourceTopic`
  - 원본 topic
- `kafkaKey`
  - Kafka partition key
- `payload`
  - 정규화 후 다시 저장되는 JSON payload

### 실제 동작 로직
1. `IngestionController.ingest()`가 요청을 받는다.
2. `Instant.now()`를 `receivedAt`으로 넣어 `IngestionMessage`를 만든다.
3. `IngestionService.ingest()`가 `totalReceived`를 증가시킨다.
4. `JsonRawEventNormalizer.normalize()`가 topic과 payload를 파싱한다.
5. `RoutingIngestionPublisher.publish()`가 현재 mode에 맞는 publisher를 고른다.
6. publish 성공이면 `totalPublished`와 `lastPublishedAt`이 갱신된다.
7. 실패면 `lastFailureStage`, `lastFailureReason`이 기록된다.

## `POST /ingestion/v1/mqtt/messages`
MQTT에서 들어온 단건 메시지를 HTTP로 흉내 낼 때 사용하는 API다.

### 요청 바디
구조는 `POST /ingestion/v1/messages`와 동일하다.

### 실제 동작 로직
1. `IngestionController.ingestFromMqtt()`가 요청을 받는다.
2. `MqttInboundAdapter.receive()`가 MQTT 입력 메트릭을 갱신한다.
3. 내부적으로 `IngestionService.ingest()`를 호출한다.

### 일반 수집 API와의 차이
- 정규화와 publish 결과는 동일하다.
- 차이는 MQTT inbound 메트릭을 별도로 쌓는다는 점이다.

## `POST /ingestion/v1/mqtt/messages/batch`
MQTT batch 유입을 한 번에 흉내 내는 API다.

### 요청 바디
```json
{
  "messages": [
    {
      "topic": "telemetryhub/devices/device-001/telemetry",
      "qos": 1,
      "payload": "{...}"
    },
    {
      "topic": "telemetryhub/devices/device-001/driving-event",
      "qos": 1,
      "payload": "{...}"
    }
  ]
}
```

### 요청 필드
- `messages`
  - 필수
  - 비어 있으면 안 된다.
  - 각 요소는 `MqttInboundMessageRequest` 검증을 그대로 받는다.

### 응답
- 정규화 결과 배열을 반환한다.
- 각 원소는 `NormalizedRawEventResponse` 형식이다.

### 실제 동작 로직
1. `IngestionController.ingestBatchFromMqtt()`가 요청을 받는다.
2. 각 메시지를 `IngestionMessage`로 변환한다.
3. `MqttInboundAdapter.receiveBatch()`가 `totalBatches`를 증가시킨다.
4. 배치 안의 각 메시지마다 `markMessage()`가 호출된다.
5. 각 메시지는 결국 `IngestionService.ingest()`를 한 번씩 탄다.

### 주의할 점
- 현재는 배치 전체 트랜잭션이 아니다.
- 중간 하나가 실패하면 그 시점에서 예외가 발생할 수 있다.
- 이미 처리된 앞쪽 메시지는 되돌리지 않는다.

## `GET /ingestion/v1/metrics`
ingestion 전체 상태를 보는 API다.

### 응답 필드
- `totalReceived`
  - 수신 시도 총 개수
- `totalPublished`
  - publish 성공 총 개수
- `totalFailed`
  - 실패 총 개수
- `lastReceivedAt`
  - 마지막 수신 시각
- `lastPublishedAt`
  - 마지막 publish 성공 시각
- `lastFailureStage`
  - 마지막 실패 단계
  - `NORMALIZE` 또는 `PUBLISH`
- `lastFailureReason`
  - 마지막 실패 사유
- `kafkaPublish`
  - Kafka publisher 세부 상태

### `kafkaPublish` 하위 필드
- `enabled`
  - Kafka publisher 사용 가능 여부
- `rawTopic`
  - publish 대상 raw topic
- `totalPublished`
  - Kafka ack 기준 성공 수
- `totalFailed`
  - Kafka callback 기준 실패 수
- `lastPublishedAt`
  - 마지막 Kafka publish 성공 시각
- `lastFailureReason`
  - 마지막 Kafka publish 실패 이유

## `GET /ingestion/v1/mqtt/metrics`
MQTT 입력 경계 자체의 사용량을 본다.

### 응답 필드
- `totalMessages`
  - MQTT adapter를 통해 처리한 메시지 수
- `totalBatches`
  - batch 호출 수
- `lastReceivedAt`
  - 마지막 MQTT 경계 수신 시각
- `lastTopic`
  - 마지막 topic
- `lastQos`
  - 마지막 qos

### 실제 의미
- 이 값은 ingestion 전체 성공/실패와 다를 수 있다.
- 예를 들어 MQTT 입력은 들어왔지만 이후 normalize 실패가 발생할 수 있다.

## `GET /ingestion/v1/mqtt/subscriber`
실제 MQTT subscriber 상태를 본다.

### 응답 필드
- `state`
  - `IDLE`, `READY`, `DEGRADED`, `DISABLED` 같은 subscriber 상태
- `brokerUri`
  - 현재 연결 대상 broker
- `clientId`
  - 실행 시점에 실제로 사용 중인 client id
- `subscriptions`
  - 실제 subscribe 중인 topic 목록
  - shared subscription 사용 시 `$share/{group}/...` 형식으로 보일 수 있다.
- `totalMessages`
  - subscriber callback이 받은 총 메시지 수
- `queuedMessages`
  - 현재 inbound queue에 적재된 메시지 수
- `droppedMessages`
  - queue overflow 등으로 drop된 메시지 수
- `lastReceivedAt`
  - 마지막 수신 시각
- `lastTopic`
  - 마지막 수신 topic
- `lastError`
  - 마지막 오류 메시지

### 실제 동작 로직
1. `MqttInboundSubscriberLifecycle.snapshot()`이 현재 상태를 만든다.
2. queue 크기와 drop 수는 lifecycle 내부 상태를 그대로 노출한다.
3. 운영 중 backlog가 쌓이는지 확인할 때 가장 먼저 보는 API다.

## `GET /ingestion/v1/events`
메모리 publisher나 메모리 버퍼에 쌓인 최근 이벤트를 조회한다.

### 쿼리 파라미터
- `limit`
  - 기본값 `20`
  - 최소 `1`, 최대 `200`

### 응답
- 최근 정규화 이벤트 배열
- 각 원소 구조는 `NormalizedRawEventResponse`

### 실제 동작 로직
1. `IngestionController.recentEvents()`가 `limit`를 받는다.
2. `IngestionService.recentEvents(limit)`를 호출한다.
3. `InMemoryNormalizedRawEventStore.recent(limit)`가 최근 이벤트를 잘라 반환한다.

### 주의할 점
- 운영 모드에서는 최근 이벤트 버퍼를 끌 수 있다.
- `telemetryhub.ingestion.runtime.recent-event-buffer-enabled=false`면 디버깅용 조회 의미가 줄어든다.

## `POST /ingestion/v1/events/clear`
최근 이벤트 메모리 버퍼를 비운다.

### 요청 바디
- 없음

### 응답
- body 없는 성공 응답

### 실제 동작 로직
1. `IngestionController.clearEvents()`가 호출된다.
2. `IngestionService.clearRecentEvents()`가 메모리 버퍼를 비운다.

## API 사용 순서 추천
### 브로커 없이 확인할 때
1. `POST /ingestion/v1/mqtt/messages` 또는 `/mqtt/messages/batch`
2. `GET /ingestion/v1/metrics`
3. `GET /ingestion/v1/mqtt/metrics`
4. `GET /ingestion/v1/events`

### 실제 broker 붙인 뒤 확인할 때
1. `GET /ingestion/v1/mqtt/subscriber`
2. simulator에서 MQTT publish
3. `GET /ingestion/v1/metrics`
4. `GET /ingestion/v1/mqtt/subscriber`

## 현재 시점 평가
현재 `ingestion-service`는 MVP 1차를 넘어서, 실제 scale-out과 운영 관측을 어느 정도 고려한 수집 경계까지 구현된 상태다.

지금 가능한 것:
- 브로커 없는 bridge 개발
- 실제 MQTT subscriber 사용
- shared subscription 기반 scale-out 준비
- Kafka raw topic publish
- ack 기반 Kafka publish 메트릭
- inbound queue backlog 관측

아직 남아 있는 것:
- 실제 broker + Kafka end-to-end smoke test
- DLQ 정책
- invalid payload 장기 보관 전략
- archive source 연동
