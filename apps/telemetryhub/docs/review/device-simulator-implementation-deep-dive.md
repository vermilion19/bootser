# Device Simulator 상세 구현 및 동작 정리

이 문서는 `apps/telemetryhub/device-simulator`의 실제 코드 기준 동작을 정리한다.
기존 `device-simulator-deep-dive.md`가 아키텍처와 설계 의도 중심이라면, 이 문서는 API 호출 이후 어떤 클래스가 어떤 순서로 실행되는지, 이벤트가 어떻게 생성되고 발행되는지에 초점을 둔다.

> 참고: 기존 deep-dive 문서에는 현재 코드와 다른 설명이 일부 있다. 예를 들어 `SimulationEventBatch`에는 `runtimeState`나 `generatedAt` 필드가 없고, `DrivingEventType`도 현재는 `HARD_BRAKE`, `OVERSPEED`, `CRASH`만 존재한다.

## 전체 실행 흐름

```text
HTTP API
  -> DeviceSimulatorController
  -> DeviceSimulatorControlService
  -> DeviceSimulatorLoopService
  -> DeviceEventPreviewService
  -> SimulationEventBatch
  -> RoutingSimulationEventPublisher
  -> LOGGING | MEMORY | BRIDGE | MQTT
```

가장 중요한 시작점은 `DeviceSimulatorController`의 `/sim/v1/start` API다.

```java
@PostMapping("/start")
public Mono<ApiResponse<SimulatorStatusResponse>> start(
        @RequestParam(defaultValue = "1000") @Min(1) int devices,
        @RequestParam(defaultValue = "1000") @Min(100) int intervalMs,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1) int qos,
        @RequestParam(defaultValue = "100") @Min(1) int connectRampUpPerSecond
) {
    SimulatorRuntimeState state = controlService.start(devices, intervalMs, qos, connectRampUpPerSecond);
    return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(state)));
}
```

요청이 들어오면 `DeviceSimulatorControlService.start(...)`가 실행되고, 여기서 실행 상태를 `RUNNING`으로 만든 뒤 `DeviceSimulatorLoopService.start(...)`를 호출한다.

## 상태 관리

현재 실행 상태는 `SimulatorRuntimeState`에 저장된다.

```java
public record SimulatorRuntimeState(
        SimulatorStatus status,
        int deviceCount,
        int publishIntervalMs,
        int qos,
        int connectRampUpPerSecond,
        int scenarioPercent,
        SimulationScenario scenario,
        Instant updatedAt
) {
}
```

`DeviceSimulatorControlService`는 이 상태를 `AtomicReference<SimulatorRuntimeState>`로 들고 있다.

```java
private final AtomicReference<SimulatorRuntimeState> runtimeState =
        new AtomicReference<>(SimulatorRuntimeState.idle());
```

상태 변경 API들은 기존 객체를 수정하지 않고 새 `SimulatorRuntimeState`를 만들어 교체한다.

```text
start()
  -> status=RUNNING인 새 상태 생성
  -> runtimeState.set(next)
  -> loopService.start(next)

stop()
  -> status=STOPPED인 새 상태 생성
  -> runtimeState.set(next)
  -> loopService.stop()

scale()
  -> deviceCount만 바꾼 새 상태 생성
  -> runtimeState.set(next)
  -> loopService.refresh(next)

applyScenario()
  -> scenario, scenarioPercent를 바꾼 새 상태 생성
  -> runtimeState.set(next)
  -> loopService.refresh(next)
```

주의할 점은 `connectRampUpPerSecond`가 현재 상태에는 저장되지만 실제 연결 ramp-up 로직에는 사용되지 않는다는 것이다.
현재 코드에서는 미래 확장용 또는 미구현 파라미터에 가깝다.

## 반복 발행 루프

반복 실행은 `DeviceSimulatorLoopService`가 담당한다.

```java
public synchronized void start(SimulatorRuntimeState runtimeState) {
    stop();

    ScheduledFuture<?> future = executorService.scheduleAtFixedRate(
            () -> publishCycle(runtimeStateProvider(runtimeState)),
            0,
            runtimeState.publishIntervalMs(),
            TimeUnit.MILLISECONDS
    );
    loopRef.set(future);
}
```

동작 순서는 다음과 같다.

```text
start(runtimeState)
  -> 기존 루프 stop()
  -> scheduleAtFixedRate(...) 등록
  -> initialDelay=0
  -> period=runtimeState.publishIntervalMs()
```

매 주기마다 `publishCycle(...)`이 실행된다.

```java
private void publishCycle(SimulatorRuntimeState runtimeState) {
    SimulationEventBatch batch = previewService.generatePreview(runtimeState, runtimeState.deviceCount());
    eventPublisher.publish(batch, runtimeState);

    SimulatorBatchSummary batchSummary = new SimulatorBatchSummary(
            runtimeState.deviceCount(),
            batch.telemetryEvents().size(),
            batch.deviceHealthEvents().size(),
            batch.drivingEvents().size(),
            Instant.now()
    );

    metricsRef.updateAndGet(current -> new SimulatorMetricsSnapshot(
            current.cycleCount() + 1,
            current.telemetryCount() + batch.telemetryEvents().size(),
            current.deviceHealthCount() + batch.deviceHealthEvents().size(),
            current.drivingEventCount() + batch.drivingEvents().size(),
            batchSummary.generatedAt(),
            batchSummary
    ));
}
```

`runtimeStateProvider(runtimeState)`는 현재 단순히 같은 `runtimeState`를 반환한다.
즉 루프가 실행 중일 때 상태 객체를 동적으로 읽는 구조가 아니라, `scale()`이나 `applyScenario()`에서 `loopService.refresh(next)`를 호출해 기존 루프를 재시작하는 방식이다.

## 이벤트 생성

이벤트 생성은 `DeviceEventPreviewService.generatePreview(...)`가 담당한다.

```java
public SimulationEventBatch generatePreview(SimulatorRuntimeState runtimeState, int count) {
    List<TelemetryEvent> telemetryEvents = new ArrayList<>(count);
    List<DeviceHealthEvent> deviceHealthEvents = new ArrayList<>(count);
    List<DrivingEvent> drivingEvents = new ArrayList<>();

    for (int i = 0; i < count; i++) {
        String deviceId = "device-%05d".formatted(i + 1);
        telemetryEvents.add(createTelemetryEvent(deviceId, runtimeState.scenario()));
        deviceHealthEvents.add(createDeviceHealthEvent(deviceId, runtimeState.scenario()));

        DrivingEvent drivingEvent = createDrivingEvent(
                deviceId,
                runtimeState.scenario(),
                runtimeState.scenarioPercent()
        );
        if (drivingEvent != null) {
            drivingEvents.add(drivingEvent);
        }
    }

    return new SimulationEventBatch(telemetryEvents, deviceHealthEvents, drivingEvents);
}
```

`deviceCount`만큼 반복하면서 각 device에 대해 기본적으로 다음 이벤트를 만든다.

| 이벤트 | 생성 개수 |
|---|---:|
| `TelemetryEvent` | device당 1개 |
| `DeviceHealthEvent` | device당 1개 |
| `DrivingEvent` | 확률적으로 0개 또는 1개 |

`DrivingEvent` 생성 확률은 다음 로직으로 결정된다.

```java
int threshold = scenarioPercent > 0 ? scenarioPercent : 15;
if (ThreadLocalRandom.current().nextInt(100) >= threshold) {
    return null;
}
```

기본 확률은 15%다. `/sim/v1/scenario/{scenario}?percent=50`처럼 호출하면 driving event가 약 50% 확률로 생성된다.

## 시나리오별 데이터 변화

현재 지원하는 시나리오는 `SimulationScenario` enum에 정의되어 있다.

```java
public enum SimulationScenario {
    STEADY,
    RECONNECT_STORM,
    LATE_EVENT,
    DUPLICATE_EVENT
}
```

시나리오별 효과는 다음과 같다.

| 시나리오 | 실제 코드상 효과 |
|---|---|
| `STEADY` | 일반 랜덤 값 생성 |
| `RECONNECT_STORM` | telemetry speed를 낮게 생성하고, health signalStrength를 낮게 생성 |
| `LATE_EVENT` | `eventTime`을 `ingestTime`보다 30~180초 과거로 설정 |
| `DUPLICATE_EVENT` | health `errorCode`에 `DUPLICATE_RISK` 설정, driving event type은 `CRASH` |

중요한 점은 `DUPLICATE_EVENT`가 실제 중복 eventId를 만들지는 않는다는 것이다.
`metadata(...)` 메서드는 매번 `UUID.randomUUID()`를 생성한다.

```java
return new EventMetadata(
        UUID.randomUUID().toString(),
        deviceId,
        eventType,
        eventTime,
        ingestTime
);
```

따라서 현재의 `DUPLICATE_EVENT`는 "중복 위험을 나타내는 데이터"이지, 실제 동일 eventId를 재사용하는 중복 이벤트 시뮬레이션은 아니다.

## SimulationEventBatch의 의미

`SimulationEventBatch`는 한 번의 publish cycle에서 생성된 세 종류의 이벤트 리스트를 묶는 record다.

```java
public record SimulationEventBatch(
        List<TelemetryEvent> telemetryEvents,
        List<DeviceHealthEvent> deviceHealthEvents,
        List<DrivingEvent> drivingEvents
) {
    public int totalCount() {
        return telemetryEvents.size() + deviceHealthEvents.size() + drivingEvents.size();
    }
}
```

여기에는 `runtimeState`나 `generatedAt`이 없다.
실행 상태는 publisher 호출 시 별도 인자로 전달되고, 생성 시각은 metrics 갱신 시 `SimulatorBatchSummary`에 기록된다.

## Publisher 라우팅

발행 방식 선택은 `RoutingSimulationEventPublisher`가 담당한다.

```java
public void publish(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
    if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.MQTT) {
        mqttPublisher.publishBatch(batch, runtimeState);
        return;
    }

    if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.MEMORY) {
        memoryPublisher.publishBatch(batch, runtimeState);
        return;
    }

    if (publisherProperties.getMode() == SimulatorPublisherProperties.PublisherMode.BRIDGE) {
        bridgePublisher.publishBatch(batch, runtimeState);
        return;
    }

    loggingPublisher.publishBatch(batch, runtimeState);
}
```

기본 설정은 `application.yml` 기준 `MEMORY`다.

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: MEMORY
```

따라서 기본 실행에서는 실제 MQTT로 메시지가 나가지 않고 메모리 버퍼에 저장된다.

## 메시지 직렬화와 토픽

대부분의 publisher는 먼저 `SimulationEventSerializer`를 통해 `SimulationEventBatch`를 `List<MessageEnvelope>`로 변환한다.

```java
public List<MessageEnvelope> serialize(SimulationEventBatch batch) {
    List<MessageEnvelope> messages = new ArrayList<>(batch.totalCount());

    for (TelemetryEvent event : batch.telemetryEvents()) {
        messages.add(new MessageEnvelope(
                topicResolver.telemetryTopic(event),
                event.metadata().deviceId(),
                toJson(event)
        ));
    }

    // device health, driving event도 같은 방식으로 변환

    return messages;
}
```

토픽은 `TelemetryTopicResolver`에서 계산한다.

```java
public String telemetryTopic(TelemetryEvent event) {
    return "telemetryhub/devices/%s/telemetry".formatted(event.metadata().deviceId());
}

public String deviceHealthTopic(DeviceHealthEvent event) {
    return "telemetryhub/devices/%s/device-health".formatted(event.metadata().deviceId());
}

public String drivingEventTopic(DrivingEvent event) {
    return "telemetryhub/devices/%s/driving-event".formatted(event.metadata().deviceId());
}
```

최종 메시지는 다음 형태다.

```text
topic   = telemetryhub/devices/{deviceId}/...
key     = deviceId
payload = JSON event
```

## MEMORY 모드

기본값인 `MEMORY` 모드에서는 `InMemorySimulationEventPublisher`가 실행된다.

```java
public void publishBatch(SimulationEventBatch batch, SimulatorRuntimeState runtimeState) {
    Instant publishedAt = Instant.now();
    List<MemoryPublishedMessage> messages = eventSerializer.serialize(batch).stream()
            .map(message -> new MemoryPublishedMessage(
                    message.topic(),
                    message.key(),
                    message.payload(),
                    publishedAt
            ))
            .toList();

    messageStore.append(messages);
}
```

메시지는 `InMemoryPublishedMessageStore`에 저장된다.

```java
private final LinkedBlockingDeque<MemoryPublishedMessage> messages;
```

새 메시지는 앞에 들어간다.
버퍼가 꽉 차면 가장 오래된 메시지를 뒤에서 제거한 뒤 다시 앞에 넣는다.

```java
if (!messages.offerFirst(message)) {
    messages.pollLast();
    messages.offerFirst(message);
}
```

이 버퍼는 `/sim/v1/published-messages` API에서 조회할 수 있다.
운영용 영속 저장소가 아니라 개발 중 최근 발행 payload와 topic을 확인하기 위한 디버깅 버퍼다.

## MQTT 모드

MQTT 모드에서는 `MqttSimulationEventPublisher.publishBatch(...)`가 실행된다.

```text
1. batch를 MessageEnvelope 리스트로 변환
2. mqtt.enabled=false면 DISABLED snapshot 기록 후 return
3. connectionManager.connect()
4. READY가 아니면 DEGRADED snapshot 기록 후 return
5. messages를 for문으로 하나씩 publish
6. 중간 실패 시 break
7. 성공/실패 결과를 MqttPublisherSnapshot에 기록
```

실제 코드 흐름은 다음과 같다.

```java
List<MessageEnvelope> messages = eventSerializer.serialize(batch);

if (!publisherProperties.getMqtt().isEnabled()) {
    snapshotRef.set(currentSnapshot(MqttConnectionState.DISABLED, 0, attemptedAt, "mqtt.enabled=false"));
    return;
}

MqttConnectionState connectionState = connectionManager.connect();

if (connectionState != MqttConnectionState.READY) {
    snapshotRef.set(currentSnapshot(MqttConnectionState.DEGRADED, 0, attemptedAt, "mqtt connection is not ready"));
    return;
}

for (MessageEnvelope message : messages) {
    MqttConnectionManager.PublishResult result = connectionManager.publish(message);
    if (!result.successful()) {
        failureReason = result.failureReason();
        break;
    }
    publishedCount++;
}
```

중요한 점은 MQTT에서도 `SimulationEventBatch` 하나를 MQTT 메시지 하나로 보내지 않는다는 것이다.
batch를 여러 `MessageEnvelope`로 풀고, 각 envelope를 개별 MQTT publish한다.

실제 Paho publish는 `RealMqttConnectionManager.publish(...)`에서 일어난다.

```java
MqttMessage mqttMessage = new MqttMessage(message.payload().getBytes(StandardCharsets.UTF_8));
mqttMessage.setQos(publisherProperties.getMqtt().getQos());
mqttMessage.setRetained(publisherProperties.getMqtt().isRetain());
client.publish(message.topic(), mqttMessage);
```

실제 MQTT broker로 전송하려면 설정이 모두 맞아야 한다.

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: MQTT
      mqtt:
        enabled: true
        real-client-enabled: true
```

`real-client-enabled=false`이면 `StubMqttConnectionManager`가 선택된다.
이 경우 connect/publish는 성공처럼 보이지만 실제 broker로 메시지는 나가지 않는다.

## MQTT ConnectionManager 선택

`MqttConnectionManagerConfig`는 설정값에 따라 실제 구현체를 선택한다.

```java
@Bean
@ConditionalOnProperty(
        prefix = "telemetryhub.simulator.publisher.mqtt",
        name = "real-client-enabled",
        havingValue = "true"
)
public MqttConnectionManager realMqttConnectionManager(...) {
    return new RealMqttConnectionManager(...);
}

@Bean
@ConditionalOnProperty(
        prefix = "telemetryhub.simulator.publisher.mqtt",
        name = "real-client-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public MqttConnectionManager stubMqttConnectionManager(...) {
    return new StubMqttConnectionManager(...);
}
```

`RealMqttConnectionManager`는 Eclipse Paho `MqttClient`를 사용한다.

```java
client = new MqttClient(
        publisherProperties.getMqtt().getBrokerUri(),
        publisherProperties.getMqtt().getClientId()
);
client.connect(connectOptions());
```

connect option은 다음과 같다.

```java
options.setConnectionTimeout(publisherProperties.getMqtt().getConnectionTimeoutSeconds());
options.setAutomaticReconnect(false);
options.setCleanSession(true);
```

`StubMqttConnectionManager`는 실제 연결 없이 상태만 `READY`로 바꾸고, publish도 성공으로 반환한다.

## BRIDGE 모드

`BRIDGE` 모드에서는 `BridgeSimulationEventPublisher`가 실행된다.
이 모드는 MQTT broker를 거치지 않고 ingestion-service의 HTTP endpoint로 batch를 보낸다.

대상 URL은 다음 조합이다.

```text
ingestion-base-url + mqtt-batch-path
```

기본값은 다음과 같다.

```text
http://localhost:8092/ingestion/v1/mqtt/messages/batch
```

전송 payload는 `BridgeBatchRequest` 형태다.

```java
private record BridgeBatchRequest(
        List<BridgeMessageRequest> messages
) {
}

private record BridgeMessageRequest(
        String topic,
        int qos,
        String payload
) {
}
```

다만 `bridge.enabled=false`면 실제 전송하지 않고 경고 로그만 남긴 뒤 return한다.

## LOGGING 모드

`LOGGING` 모드에서는 `LoggingSimulationEventPublisher`가 실행된다.
실제 저장이나 외부 전송은 하지 않고, batch 요약과 샘플 topic, payload 크기를 로그로 남긴다.

```java
log.info(
        "Published simulated batch: scenario={}, devices={}, telemetry={}, deviceHealth={}, drivingEvents={}, total={}, sampleTopic={}, samplePayloadSize={}",
        runtimeState.scenario(),
        runtimeState.deviceCount(),
        batch.telemetryEvents().size(),
        batch.deviceHealthEvents().size(),
        batch.drivingEvents().size(),
        batch.totalCount(),
        sample != null ? sample.topic() : null,
        sample != null ? sample.payload().length() : 0
);
```

## Metrics 동작

`DeviceSimulatorLoopService`는 publish cycle이 끝날 때마다 `SimulatorMetricsSnapshot`을 갱신한다.

```java
metricsRef.updateAndGet(current -> new SimulatorMetricsSnapshot(
        current.cycleCount() + 1,
        current.telemetryCount() + batch.telemetryEvents().size(),
        current.deviceHealthCount() + batch.deviceHealthEvents().size(),
        current.drivingEventCount() + batch.drivingEvents().size(),
        batchSummary.generatedAt(),
        batchSummary
));
```

이 값은 `/sim/v1/metrics`에서 조회된다.
응답에는 일반 시뮬레이터 metrics와 MQTT publisher snapshot이 함께 들어간다.

```java
SimulatorMetricsResponse.from(
        controlService.getMetrics(),
        controlService.getMqttPublisherSnapshot()
)
```

MQTT snapshot에는 다음 정보가 포함된다.

```java
public record MqttPublisherSnapshot(
        MqttConnectionState connectionState,
        String brokerUri,
        String clientId,
        int qos,
        boolean retain,
        long lastPublishedMessageCount,
        long consecutiveFailureCount,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastFailureReason,
        MqttRetryPolicySnapshot retryPolicy
) {
}
```

주의할 점은 `MqttRetryPolicySnapshot`이 응답에는 노출되지만, 현재 실제 retry loop는 구현되어 있지 않다는 것이다.
현재 MQTT publish는 실패가 발생하면 해당 cycle의 publish loop를 중단한다.

## API별 역할

| API | 역할 |
|---|---|
| `POST /sim/v1/start` | 시뮬레이터 시작, 반복 발행 루프 등록 |
| `POST /sim/v1/stop` | 반복 발행 루프 중지 |
| `POST /sim/v1/scale?devices=N` | device 수 변경, 실행 중이면 루프 재시작 |
| `POST /sim/v1/scenario/{scenario}?percent=N` | 시나리오와 driving event 생성 비율 변경, 실행 중이면 루프 재시작 |
| `GET /sim/v1/status` | 현재 runtime state 조회 |
| `GET /sim/v1/metrics` | 누적 발행 metrics와 MQTT publisher 상태 조회 |
| `GET /sim/v1/preview?count=N` | 실제 발행 없이 샘플 이벤트 생성 결과 조회 |
| `GET /sim/v1/published-messages?limit=N` | MEMORY 모드에서 최근 저장된 메시지 조회 |
| `POST /sim/v1/published-messages/clear` | MEMORY 메시지 버퍼 초기화 |

## 현재 코드에서 주의할 점

1. `/start`의 `qos` 파라미터는 `SimulatorRuntimeState`에 저장되지만, 실제 MQTT 발행에서는 `runtimeState.qos()`가 아니라 `publisherProperties.getMqtt().getQos()`를 사용한다.

2. `connectRampUpPerSecond`는 상태에는 저장되지만 현재 실제 연결 또는 발행 속도 제어에 사용되지 않는다.

3. `DUPLICATE_EVENT`는 실제 중복 eventId를 만들지 않는다. 매 이벤트마다 새 UUID가 생성된다.

4. `MqttRetryPolicySnapshot`은 metrics 응답에 노출되지만 실제 retry 로직은 구현되어 있지 않다.

5. 기본 설정은 `MEMORY` 모드이며, `mqtt.enabled=false`, `real-client-enabled=false`다. 기본 실행에서는 실제 MQTT broker로 메시지가 나가지 않는다.

6. `SimulationEventBatch`는 MQTT batch message가 아니다. 한 publish cycle의 이벤트 묶음이고, MQTT 발행 시 여러 개의 개별 MQTT message로 풀린다.

## 요약

`device-simulator`는 다음 세 가지 역할을 합친 애플리케이션이다.

```text
이벤트 생성기
  + 반복 실행 루프
  + 발행 방식 라우터
```

복잡해 보이는 이유는 실제 MQTT, Stub MQTT, Memory 디버깅, HTTP Bridge를 모두 한 시뮬레이터에서 지원하기 때문이다.
실제 동작을 이해할 때는 `Controller -> ControlService -> LoopService -> PreviewService -> RoutingPublisher` 순서로 따라가면 된다.
