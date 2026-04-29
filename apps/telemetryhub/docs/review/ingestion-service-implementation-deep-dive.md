# Ingestion Service 상세 구현 및 동작 정리

이 문서는 `apps/telemetryhub/ingestion-service`의 실제 코드 기준 동작을 정리한다.
기존 `ingestion-service-review.md`가 패키지 구조와 클래스 목록 중심이라면, 이 문서는 메시지가 들어온 이후 어떤 클래스가 어떤 순서로 실행되는지, 정규화와 발행이 어떻게 동작하는지에 초점을 둔다.

## 전체 실행 흐름

ingestion-service에는 두 가지 입력 경로가 있다.

```text
[경로 1] HTTP 직접 수집
  HTTP POST /ingestion/v1/messages
  -> IngestionController
  -> IngestionService.ingest(IngestionMessage)
  -> JsonRawEventNormalizer.normalize()
  -> RoutingIngestionPublisher.publish()
     -> LOGGING | MEMORY | KAFKA

[경로 2] MQTT 구독 (실시간)
  MQTT broker
  -> MqttInboundSubscriberLifecycle (InboundCallback)
  -> LinkedBlockingQueue
  -> inboundWorker thread
  -> MqttInboundAdapter.receive()
  -> IngestionService.ingest(IngestionMessage)
  -> (이후 경로 1과 동일)

[경로 3] HTTP MQTT 단건 / 배치 (Bridge용)
  HTTP POST /ingestion/v1/mqtt/messages
  HTTP POST /ingestion/v1/mqtt/messages/batch
  -> IngestionController
  -> MqttInboundAdapter.receive() or receiveBatch()
  -> IngestionService.ingest(IngestionMessage)
  -> (이후 경로 1과 동일)
```

세 경로 모두 결국 `IngestionService.ingest(IngestionMessage)`로 합쳐진다.

## IngestionMessage — 서비스 경계의 공통 모델

모든 입력 경로는 최종적으로 `IngestionMessage`로 변환된다.

```java
public record IngestionMessage(
        String topic,
        int qos,
        String payload,
        Instant receivedAt
) {
}
```

`receivedAt`은 각 경계에서 직접 `Instant.now()`로 찍는다.
- HTTP 직접 수집: `IngestionController`에서 찍음
- MQTT 경로: `MqttInboundAdapter.receive()`에서 찍음
- 배치 경로: 배치 전체에 단일 `batchReceivedAt`을 사용하고, 개별 메시지도 같은 시각으로 설정

## IngestionService — 처리 orchestration

`IngestionService.ingest()`는 normalize → publish 두 단계로 나뉘며, 각 단계 결과를 메트릭에 기록한다.

```java
public NormalizedRawEvent ingest(IngestionMessage message) {
    markReceived(message.receivedAt());
    try {
        NormalizedRawEvent normalized = rawEventNormalizer.normalize(message);
        IngestionPublishResult publishResult = ingestionPublisher.publish(normalized);
        if (!publishResult.success()) {
            markFailed(message.receivedAt(), IngestionFailureStage.PUBLISH, publishResult.failureReason());
            throw new IllegalStateException(publishResult.failureReason());
        }
        markPublished(normalized.ingestTime());
        return normalized;
    } catch (RuntimeException exception) {
        if (!(exception instanceof IllegalStateException)) {
            markFailed(message.receivedAt(), IngestionFailureStage.NORMALIZE, exception.getMessage());
        }
        throw exception;
    }
}
```

실패 단계 처리에서 중요한 점이 있다.

- normalize 실패: `RuntimeException`이 던져지면 `NORMALIZE` stage로 기록하고 예외를 재전파
- publish 실패: `publishResult.success()==false`이면 `PUBLISH` stage로 기록하고 `IllegalStateException` throw
- 이미 publish 실패로 던진 `IllegalStateException`은 catch 블록에서 `instanceof` 체크로 재기록하지 않음

메트릭은 `AtomicReference<IngestionMetricsSnapshot>`으로 관리하며, 매번 새 record를 만들어 교체한다.

```java
private void markReceived(Instant receivedAt) {
    metricsRef.updateAndGet(current -> new IngestionMetricsSnapshot(
            current.totalReceived() + 1,
            current.totalPublished(),
            current.totalFailed(),
            receivedAt,
            current.lastPublishedAt(),
            current.lastFailureStage(),
            current.lastFailureReason()
    ));
}
```

`totalReceived`는 normalize/publish 성공 여부와 무관하게 먼저 올라간다.
`totalPublished`는 publish 성공 후에 올라간다.
`totalFailed`는 normalize 또는 publish 실패 시 올라간다.

`markPublished`에서는 `normalized.ingestTime()`을 사용한다는 점에 주의.
`message.receivedAt()`이 아니라, normalize 결과에서 추출한 ingestTime을 쓴다.

## 정규화 — JsonRawEventNormalizer

정규화는 두 단계로 나뉜다.

```text
1. topic 끝부분으로 EventType 판별 (IngestionTopicResolver)
2. payload를 해당 EventType 모델로 파싱하고 NormalizedRawEvent 생성
```

### 토픽 판별

`IngestionTopicResolver`는 topic 끝부분만 본다.

```java
if (topic.endsWith("/telemetry")) return EventType.TELEMETRY;
if (topic.endsWith("/device-health")) return EventType.DEVICE_HEALTH;
if (topic.endsWith("/driving-event")) return EventType.DRIVING_EVENT;
throw new CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "unsupported topic: " + topic);
```

지원하지 않는 topic이면 `CoreException`으로 즉시 실패한다.
`startsWith` 체크는 없다. 즉, topic이 `telemetryhub/devices/xxx/telemetry`여도, `/telemetry`로만 끝나면 통과된다.

### payload 파싱

파싱 대상은 세 가지다.

```java
case TELEMETRY -> {
    TelemetryEvent event = JsonUtils.fromJson(message.payload(), TelemetryEvent.class);
    return normalizeWithMetadata(event.metadata(), EventType.TELEMETRY, message);
}
case DEVICE_HEALTH -> {
    DeviceHealthEvent event = JsonUtils.fromJson(message.payload(), DeviceHealthEvent.class);
    return normalizeWithMetadata(event.metadata(), EventType.DEVICE_HEALTH, message);
}
case DRIVING_EVENT -> {
    DrivingEvent event = JsonUtils.fromJson(message.payload(), DrivingEvent.class);
    return normalizeWithMetadata(event.metadata(), EventType.DRIVING_EVENT, message);
}
```

파싱 후에는 공통 검증이 들어간다.

```text
metadata == null          -> INVALID_INPUT_VALUE
deviceId 비어있음          -> INVALID_INPUT_VALUE
eventId 비어있음           -> INVALID_INPUT_VALUE
metadata.eventType() != expectedType -> INVALID_INPUT_VALUE (topic-payload 불일치)
```

마지막 검증은 topic과 payload의 eventType이 일치하는지 확인한다.
예를 들어 `/telemetry` topic으로 들어온 payload에 `eventType=DEVICE_HEALTH`가 들어있으면 실패한다.

### ingestTime, eventTime 결정

```java
Instant ingestTime = metadata.ingestTime() != null ? metadata.ingestTime() : message.receivedAt();
Instant eventTime = metadata.eventTime() != null ? metadata.eventTime() : ingestTime;
```

payload의 metadata에 `ingestTime`이 있으면 그것을 쓰고, 없으면 `message.receivedAt()`을 fallback으로 사용한다.
`eventTime`도 마찬가지로 metadata 우선, 없으면 `ingestTime` 사용.
이 덕분에 simulator가 보내는 `LATE_EVENT` 시나리오(eventTime을 과거로 설정)도 정확히 반영된다.

### Kafka 파티션 키 결정

`KafkaEventKeyResolver`는 설정값(`keyStrategy`)에 따라 세 가지 방식으로 키를 결정한다.

| keyStrategy | key 값 |
|---|---|
| `DEVICE_ID` | `deviceId` |
| `EVENT_ID` | `eventId` |
| `EVENT_TYPE_AND_DEVICE_ID` | `"TELEMETRY:device-00001"` 형태 |

기본값은 `DEVICE_ID`다. 같은 device의 이벤트가 항상 같은 파티션으로 들어가므로, 다운스트림에서 device 단위 순서 보장이 필요할 때 유리하다.

### NormalizedRawEvent — normalize 결과 모델

```java
public record NormalizedRawEvent(
        EventType eventType,
        String eventId,
        String deviceId,
        Instant eventTime,
        Instant ingestTime,
        String sourceTopic,
        String kafkaKey,
        String payload
) {
}
```

`payload`는 원본 JSON 문자열 그대로 들어간다. 파싱 결과를 다시 직렬화하지 않는다.
`sourceTopic`은 입력으로 들어온 MQTT topic 이름을 그대로 저장한다.
`kafkaKey`는 위에서 결정된 키 전략 결과다.

## Publisher 라우팅 — RoutingIngestionPublisher

`RoutingIngestionPublisher`는 설정된 mode에 따라 실제 publisher를 선택한다.

```java
public IngestionPublishResult publish(NormalizedRawEvent event) {
    if (publisherProperties.getMode() == PublisherMode.KAFKA) {
        return kafkaPublisher.publish(event);
    }
    if (publisherProperties.getMode() == PublisherMode.MEMORY) {
        return memoryPublisher.publish(event);
    }
    return loggingPublisher.publish(event);
}
```

기본값은 `MEMORY`다.

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: MEMORY
```

세 가지 publisher는 Bean으로 항상 등록되어 있다.
`@ConditionalOnProperty`로 선택적으로 등록하는 구조가 아니라, `RoutingIngestionPublisher`가 런타임에 분기한다.

### LOGGING publisher

실제 저장이나 전송 없이 로그만 남긴다.

```java
log.info(
    "Published raw event to logging sink: eventType={}, deviceId={}, eventId={}, topic={}, kafkaKey={}",
    event.eventType(), event.deviceId(), event.eventId(), event.sourceTopic(), event.kafkaKey()
);
return IngestionPublishResult.success("LOGGING");
```

항상 success를 반환한다.

### MEMORY publisher

`InMemoryIngestionPublisher`는 `InMemoryNormalizedRawEventStore`에 이벤트를 저장한다.

```java
public IngestionPublishResult publish(NormalizedRawEvent event) {
    store.append(event);
    log.info("Published raw event to memory sink: ...");
    return IngestionPublishResult.success("MEMORY");
}
```

`InMemoryNormalizedRawEventStore`는 `LinkedBlockingDeque`를 bounded buffer로 사용한다.

```java
private final LinkedBlockingDeque<NormalizedRawEvent> events;

public synchronized void append(NormalizedRawEvent event) {
    if (!runtimeProperties.isRecentEventBufferEnabled()) {
        return;
    }
    if (!events.offerFirst(event)) {
        events.pollLast();
        events.offerFirst(event);
    }
}
```

새 이벤트는 앞(first)에 들어간다. 버퍼가 꽉 차면 가장 오래된 이벤트(last)를 제거하고 다시 앞에 넣는다.
기본 크기는 `maxRecentEvents=500`이다.

`recentEventBufferEnabled=false`이면 append도, recent()도 동작하지 않는다.

주의할 점이 있다. `InMemoryIngestionPublisher`와 `InMemoryNormalizedRawEventStore`는 역할이 다르다.

| 클래스 | 역할 |
|---|---|
| `InMemoryIngestionPublisher` | MEMORY mode일 때 publish를 담당 (IngestionPublisher 구현체) |
| `InMemoryNormalizedRawEventStore` | 모든 mode에서 normalize 결과를 디버깅용으로 버퍼링 |

`InMemoryIngestionPublisher`는 내부적으로 `InMemoryNormalizedRawEventStore`를 통해 저장한다.
그런데 `IngestionService`도 `InMemoryNormalizedRawEventStore`를 직접 의존한다(`recentEvents`, `clearRecentEvents`).

즉 KAFKA 모드에서도 `InMemoryNormalizedRawEventStore`를 통해 최근 이벤트를 조회할 수 있는데, 이는 `InMemoryIngestionPublisher.publish()`를 통해서가 아니라 `IngestionService`가 store를 직접 접근하는 경로만 남아 있다는 의미다.

현재 코드에서 KAFKA 모드로 실행하면 `InMemoryNormalizedRawEventStore`에는 아무것도 쌓이지 않는다. MEMORY 모드일 때만 `InMemoryIngestionPublisher`가 `store.append()`를 호출하기 때문이다.

### KAFKA publisher

`KafkaIngestionPublisher`는 `KafkaTemplate`으로 비동기 전송한다.

```java
public IngestionPublishResult publish(NormalizedRawEvent event) {
    if (!publisherProperties.getKafka().isEnabled()) {
        recordFailure("kafka.enabled=false");
        return IngestionPublishResult.failure("KAFKA", "kafka.enabled=false");
    }

    try {
        CompletableFuture<?> sendFuture = stringKafkaTemplate.send(
                publisherProperties.getKafka().getRawTopic(),
                event.kafkaKey(),
                JsonUtils.toJson(event)
        );
        sendFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                recordFailure(throwable.getMessage());
                return;
            }
            recordSuccess();
        });
        return IngestionPublishResult.success("KAFKA");
    } catch (RuntimeException exception) {
        recordFailure(exception.getMessage());
        return IngestionPublishResult.failure("KAFKA", exception.getMessage());
    }
}
```

핵심은 `IngestionPublishResult.success("KAFKA")`를 즉시 반환하지만, 실제 Kafka 전송 결과는 `whenComplete`에서 비동기로 처리된다는 점이다.

```text
publish() 호출
  -> KafkaTemplate.send() 호출 (비동기)
  -> 즉시 success 반환 -> IngestionService.ingest()는 성공으로 처리
  -> 나중에 실제 Kafka ack가 도착하면
     -> 성공: recordSuccess() -> snapshotRef 갱신
     -> 실패: recordFailure() -> snapshotRef 갱신, 경고 로그
```

따라서 `IngestionService`의 `totalPublished` 메트릭과 `KafkaPublishSnapshot`의 `totalPublished`는 다를 수 있다.
전자는 send() 직후 올라가고, 후자는 실제 ack 이후 올라간다.

`kafka.enabled=false`인 경우에는 send() 시도 없이 즉시 failure를 반환한다.
이때는 `IngestionService`에서 publish 실패로 처리되고 `totalFailed`가 올라간다.

전송할 payload는 `JsonUtils.toJson(event)`이다. `NormalizedRawEvent` 전체가 JSON으로 직렬화된다.
topic과 eventId, deviceId 등 메타데이터까지 포함되므로 다운스트림이 원본 payload 외의 정보도 함께 받는다.

기본 설정값은 다음과 같다.

```yaml
telemetryhub:
  ingestion:
    publisher:
      kafka:
        enabled: false
        raw-topic: telemetryhub.raw-events
        key-strategy: DEVICE_ID
```

KAFKA 모드로 실제 전송하려면 두 가지를 모두 설정해야 한다.

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: KAFKA
      kafka:
        enabled: true
```

`mode: KAFKA`만 설정하고 `kafka.enabled=false`면 `RoutingIngestionPublisher`는 Kafka publisher를 선택하지만, publisher 내부에서 즉시 실패를 반환한다.

## MQTT 입력 경로 — MqttInboundSubscriberLifecycle

`MqttInboundSubscriberLifecycle`은 `SmartLifecycle`을 구현해 Spring context 시작/종료 시 자동으로 연결/해제한다.

```java
@Override
public boolean isAutoStartup() {
    return true;
}

@Override
public int getPhase() {
    return 0;
}
```

### 시작 순서

```text
start()
  -> mqtt.enabled=false? -> DISABLED 상태로 return
  -> real-client-enabled=false? -> IDLE 상태로 return (실제 연결 없음)
  -> state = CONNECTING
  -> initializeInboundProcessing() (queue + worker thread 시작)
  -> client ID 계산 (resolveClientId)
  -> MqttClient 생성 + callback 등록
  -> broker 연결
  -> 구독 등록 (subscribeAll)
  -> state = READY
```

`real-client-enabled=false`이면 인프라가 없어도 애플리케이션이 정상 기동된다.
이 경우 MQTT subscriber는 동작하지 않고, HTTP 경로로만 메시지를 수집할 수 있다.

### client ID 결정

```java
private String resolveClientId() {
    String suffix = sanitizeSuffix(properties.getClientIdSuffix());
    if (!suffix.isBlank()) {
        return properties.getClientId() + "-" + suffix;
    }
    return properties.getClientId() + "-" + sanitizeSuffix(defaultInstanceSuffix());
}

private String defaultInstanceSuffix() {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    if (runtimeName != null && !runtimeName.isBlank()) {
        return runtimeName;
    }
    return UUID.randomUUID().toString();
}
```

`clientIdSuffix` 설정이 없으면 JVM의 `RuntimeMXBean.getName()`(보통 `PID@hostname` 형태)을 suffix로 사용한다.
이 덕분에 여러 인스턴스가 같은 설정으로 기동해도 client ID가 겹치지 않는다.

알파벳, 숫자, `.`, `_`, `-` 외의 문자는 `-`로 치환(`sanitizeSuffix`)해 MQTT client ID 규격을 맞춘다.

### shared subscription

```java
private String toSharedSubscription(String subscription) {
    if (subscription.startsWith("$share/")) {
        return subscription;
    }
    return "$share/" + properties.getSharedSubscriptionGroup() + "/" + subscription;
}
```

`sharedSubscriptionEnabled=true`이면 일반 subscription을 `$share/{group}/{topic}` 형태로 변환한다.
기본 group 이름은 `telemetryhub-ingestion`이다.

shared subscription을 사용하면 같은 group의 여러 subscriber가 메시지를 분산 수신한다.
scale-out 시 메시지 중복 소비를 방지할 수 있다.

### 메시지 수신 → queue → worker

MQTT broker에서 메시지가 도착하면 `InboundCallback.messageArrived()`가 Paho 내부 스레드에서 실행된다.

```java
public void messageArrived(String topic, MqttMessage message) {
    Instant receivedAt = Instant.now();
    totalMessages.incrementAndGet();
    lastReceivedAt.set(receivedAt);
    lastTopic.set(topic);

    BlockingQueue<InboundMessageEnvelope> queue = inboundQueue;
    if (queue == null) {
        droppedMessages.incrementAndGet();
        lastError.set("inbound queue is not initialized");
        return;
    }
    boolean offered = queue.offer(new InboundMessageEnvelope(topic, message.getQos(), ...));
    if (!offered) {
        droppedMessages.incrementAndGet();
        lastError.set("inbound queue is full");
        log.warn("Dropped inbound MQTT message because queue is full. topic={}", topic);
    }
}
```

Paho callback 스레드는 블로킹시키지 않는다. `queue.offer()`는 non-blocking이므로 큐가 꽉 차면 메시지를 drop한다.
drop된 메시지는 `droppedMessages` 카운터에 기록되고 `snapshot()`을 통해 조회할 수 있다.

기본 queue 크기는 `inboundQueueCapacity=10_000`이다.

worker thread는 별도의 `ExecutorService`에서 queue를 polling한다.

```java
private void runInboundWorker() {
    while (running.get() || (inboundQueue != null && !inboundQueue.isEmpty())) {
        InboundMessageEnvelope envelope = inboundQueue.poll(500, TimeUnit.MILLISECONDS);
        if (envelope == null) continue;
        mqttInboundAdapter.receive(envelope.topic(), envelope.qos(), envelope.payload());
    }
}
```

`running=false`가 되어도 queue에 남은 메시지가 있으면 drain할 때까지 계속 실행된다.
worker thread 수는 `inboundWorkerThreads=1`이 기본값이다.

### 연결 유실 처리

```java
public void connectionLost(Throwable cause) {
    running.set(false);
    state.set(MqttSubscriberState.DEGRADED);
    lastError.set(cause != null ? cause.getMessage() : "connection lost");
}
```

`automaticReconnect=false`로 설정되어 있으므로 연결이 끊기면 `DEGRADED` 상태로 바뀌고, 자동 재연결은 하지 않는다.
`cleanSession=true`이므로 재연결 시 미수신 메시지는 복구되지 않는다.

### 종료 처리

```java
public synchronized void stop() {
    running.set(false);
    // ...
    client.disconnect();
    client.close();
    // ...
    shutdownInboundProcessing();
    state.set(...IDLE or DISABLED...);
}
```

`shutdownInboundProcessing()`은 `shutdownNow()`로 worker를 즉시 중단하고 5초간 종료를 기다린다.

## MqttInboundAdapter — MQTT 경계

`MqttInboundAdapter`는 MQTT 입력 경로와 `IngestionService` 사이에 위치한다.
MQTT 전용 메트릭(`MqttInboundMetricsSnapshot`)을 별도로 관리한다.

```java
public NormalizedRawEvent receive(String topic, int qos, String payload) {
    Instant receivedAt = Instant.now();
    markMessage(topic, qos, receivedAt);
    return ingestionService.ingest(new IngestionMessage(topic, qos, payload, receivedAt));
}

public List<NormalizedRawEvent> receiveBatch(List<IngestionMessage> messages) {
    Instant batchReceivedAt = Instant.now();
    markBatch(messages.size(), batchReceivedAt);

    List<NormalizedRawEvent> results = new ArrayList<>(messages.size());
    for (IngestionMessage message : messages) {
        markMessage(message.topic(), message.qos(), batchReceivedAt);
        results.add(ingestionService.ingest(
                new IngestionMessage(message.topic(), message.qos(), message.payload(), batchReceivedAt)
        ));
    }
    return results;
}
```

배치 수신 시 `batchReceivedAt`을 한 번 찍고 모든 메시지에 동일하게 사용한다.
각 메시지의 `receivedAt`이 동일한 Instant를 갖게 된다.

`MqttInboundMetricsSnapshot`은 `IngestionService`의 메트릭과 독립적이다.
MQTT 경로를 통한 메시지 수(`totalMessages`), 배치 수(`totalBatches`), 마지막 토픽/QoS를 별도 추적한다.

## 메트릭 구조

ingestion-service에는 세 가지 독립 메트릭이 있다.

| 메트릭 | 위치 | 내용 |
|---|---|---|
| `IngestionMetricsSnapshot` | `IngestionService` | 전체 수신/발행/실패 카운트, 마지막 실패 정보 |
| `MqttInboundMetricsSnapshot` | `MqttInboundAdapter` | MQTT 경로 메시지/배치 수, 마지막 토픽/QoS |
| `KafkaPublishSnapshot` | `KafkaIngestionPublisher` | Kafka 전송 성공/실패 카운트, 마지막 발행 시각 |

`GET /ingestion/v1/metrics` 응답에는 `IngestionMetricsSnapshot`과 `KafkaPublishSnapshot`이 함께 들어간다.
`GET /ingestion/v1/mqtt/metrics`는 `MqttInboundMetricsSnapshot`만 반환한다.
`GET /ingestion/v1/mqtt/subscriber`는 `MqttSubscriberSnapshot`(queue depth, droppedMessages 등)을 반환한다.

## API별 역할

| API | 역할 |
|---|---|
| `POST /ingestion/v1/messages` | HTTP 직접 수집, normalized event 응답 |
| `POST /ingestion/v1/mqtt/messages` | MQTT 단건 Bridge 수집 |
| `POST /ingestion/v1/mqtt/messages/batch` | MQTT 배치 Bridge 수집 |
| `GET /ingestion/v1/metrics` | 전체 수집 메트릭 + Kafka 발행 상태 |
| `GET /ingestion/v1/mqtt/metrics` | MQTT 입력 경로 전용 메트릭 |
| `GET /ingestion/v1/mqtt/subscriber` | MQTT subscriber 상태 (queue depth, dropped 등) |
| `GET /ingestion/v1/events` | 최근 normalized event 조회 (기본 최대 20개) |
| `POST /ingestion/v1/events/clear` | 최근 이벤트 버퍼 초기화 |

`GET /ingestion/v1/events`의 최대 limit은 `@Max(200)`으로 제한되어 있다.

## 설정 구조

```yaml
telemetryhub:
  ingestion:
    publisher:
      mode: MEMORY             # LOGGING | MEMORY | KAFKA
      kafka:
        enabled: false
        raw-topic: telemetryhub.raw-events
        key-strategy: DEVICE_ID  # DEVICE_ID | EVENT_ID | EVENT_TYPE_AND_DEVICE_ID

    mqtt:
      enabled: false
      real-client-enabled: false
      broker-uri: tcp://localhost:1883
      client-id: telemetryhub-ingestion-service
      client-id-suffix:           # 미설정 시 JVM PID@hostname 사용
      connection-timeout-seconds: 5
      shared-subscription-enabled: false
      shared-subscription-group: telemetryhub-ingestion
      inbound-queue-capacity: 10000
      inbound-worker-threads: 1
      subscriptions:
        - telemetryhub/devices/+/telemetry
        - telemetryhub/devices/+/device-health
        - telemetryhub/devices/+/driving-event

    runtime:
      recent-event-buffer-enabled: true
      max-recent-events: 500
```

## 현재 코드에서 주의할 점

1. **KAFKA publisher의 success 의미**: `IngestionPublishResult.success("KAFKA")`는 Kafka ack를 보장하지 않는다. `KafkaTemplate.send()`가 던지지 않으면 바로 success를 반환하고, 실제 전송 결과는 `whenComplete`에서 비동기로 처리된다. 따라서 `IngestionMetricsSnapshot.totalPublished`와 `KafkaPublishSnapshot.totalPublished`는 시차가 있다.

2. **KAFKA 모드에서 recent events 비어있음**: `InMemoryNormalizedRawEventStore`에 이벤트가 쌓이는 것은 `InMemoryIngestionPublisher.publish()`를 통해서다. KAFKA 모드에서는 이 publisher가 실행되지 않으므로 `GET /ingestion/v1/events`는 항상 빈 리스트를 반환한다.

3. **mqtt.enabled와 real-client-enabled 조합**: `enabled=true`, `real-client-enabled=false`이면 MQTT subscriber는 기동되지만 실제 broker에 연결하지 않는다. 상태는 `IDLE`로 표시된다. HTTP 경로(`/mqtt/messages`)로는 메시지를 수집할 수 있다.

4. **MQTT broker 연결 유실 후 자동 재연결 없음**: `MqttConnectOptions.setAutomaticReconnect(false)`로 설정되어 있다. ingestion-service와 MQTT broker 간 연결이 끊기면 Paho가 재연결을 시도하지 않고 `DEGRADED` 상태로 바뀐다. 서비스를 재시작하거나 별도 복구 절차가 필요하다. Kafka 연결은 Spring Kafka가 내부적으로 재연결을 관리하므로 해당 없다.

5. **배치 수신 시 receivedAt 공유**: `receiveBatch()`에서 한 번의 `Instant.now()`를 배치 전체에 사용한다. 개별 메시지의 receivedAt이 동일하게 설정된다.

6. **topic 검증은 끝부분만**: `IngestionTopicResolver`는 topic이 `/telemetry`, `/device-health`, `/driving-event`로 끝나는지만 확인한다. 앞부분의 prefix(`telemetryhub/devices/xxx/`)는 검증하지 않는다.

## 요약

`ingestion-service`는 세 가지 역할을 합친 서비스다.

```text
입력 경계 (HTTP / MQTT 구독 / HTTP-MQTT Bridge)
  + 정규화 (topic 판별 → payload 파싱 → NormalizedRawEvent 생성)
  + 발행 라우팅 (LOGGING | MEMORY | KAFKA)
```

복잡해 보이는 이유는 MQTT 실시간 구독, HTTP Bridge, 직접 수집 세 경로를 동시에 지원하기 때문이다.
실제 동작을 이해할 때는 `IngestionController → (MqttInboundAdapter →) IngestionService → JsonRawEventNormalizer → RoutingIngestionPublisher` 순서로 따라가면 된다.