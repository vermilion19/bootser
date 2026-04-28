# Device Simulator 구조 심층 해설

> 이 문서는 `device-simulator-review.md`의 표 수준 정리를 넘어,
> **"왜 이 클래스가 존재하는가"** 를 중심으로 설계 의도와 판단 근거를 서술한다.

---

## 1. 이 모듈이 존재하는 이유

TelemetryHub는 차량/IoT 디바이스에서 올라오는 텔레메트리 데이터를 수집·처리하는 시스템이다.
그런데 ingestion-service, stream-processor 등을 개발하는 동안 **실제 디바이스는 없다.**

두 가지 대안이 있었다.

| 대안 | 문제 |
|------|------|
| k6 같은 부하 테스트 도구로 HTTP 요청 보내기 | MQTT → ingestion → Kafka 전체 경로 검증 불가 |
| 실제 MQTT 브로커에 연결하는 프로그램 따로 작성 | 브로커 없는 환경에서는 실행 자체가 안 됨 |

device-simulator는 이 두 문제를 동시에 해결한다.

- **실제 MQTT 브로커가 없어도 동작**한다 (Stub 구현체 덕분에)
- **브로커가 있으면 실제 MQTT 프로토콜로 발행**한다
- **HTTP로 ingestion을 직접 호출**하는 Bridge 모드도 있어 MQTT 없이 전체 파이프라인 검증 가능
- **시나리오 주입**으로 지연 이벤트, 중복, 폭주 재연결 같은 엣지 케이스를 재현한다

---

## 2. 패키지 구조와 계층 분리 의도

```
devicesimulator/
├── domain/           # 순수 비즈니스 개념 (기술 의존성 없음)
├── config/           # 설정값 바인딩, 조건부 빈 등록
├── application/      # 비즈니스 흐름 조율 (외부 기술에 의존하지 않음)
│   ├── control/      # 상태 전이 및 제어 명령
│   ├── preview/      # 이벤트 샘플 생성
│   ├── publisher/    # 퍼블리셔 포트 정의
│   └── runtime/      # 실행 루프 및 메트릭
├── infrastructure/   # 외부 기술과의 통합 (MQTT, HTTP, 메모리)
│   ├── message/      # 직렬화, 토픽 계산
│   ├── mqtt/         # MQTT 프로토콜 구현
│   ├── publisher/    # 퍼블리셔 구현체들
│   └── store/        # 인메모리 저장소
└── web/              # REST API 진입점
```

**계층 분리를 한 핵심 이유:**

application 계층은 `SimulationEventPublisher` 인터페이스(포트)만 알고, 실제로 MQTT로 보내는지 HTTP로 보내는지 모른다. 이 덕분에:

- MQTT 브로커가 없는 CI 환경에서도 application 계층의 로직을 테스트할 수 있다.
- 새로운 전송 방식(예: Kafka Direct)을 추가해도 application 코드는 건드리지 않는다.

---

## 3. Domain 계층: 왜 Enum 두 개만 있는가

### `SimulationScenario`

```
STEADY           → 정상적인 이벤트 흐름
RECONNECT_STORM  → 신호 약화, 재연결 폭주
LATE_EVENT       → 타임스탬프가 30~180초 과거인 이벤트
DUPLICATE_EVENT  → 중복 위험이 표시된 이벤트
```

이 enum이 domain 계층에 있는 이유는 "어떤 상황을 시뮬레이션할 것인가"가 인프라(MQTT, HTTP)나 구현 방식과 무관한 **비즈니스 개념**이기 때문이다.
PreviewService가 이 값을 보고 이벤트를 어떻게 생성할지 결정하므로, domain에 두어야 application 계층에서 import할 수 있다.

### `SimulatorStatus`

```
IDLE     → 시작 전
RUNNING  → 루프 실행 중
STOPPED  → 명시적으로 중지됨
```

단순해 보이지만, 이 enum이 있어야 `ControlService`의 상태 전이를 if/else 문자열 비교 없이 명확하게 표현할 수 있다.

---

## 4. Config 계층: 설정값과 조건부 빈 등록

### `SimulatorRuntimeProperties`

```yaml
telemetryhub.simulator.runtime:
  published-message-buffer-enabled: true
  max-published-messages: 500
```

이 설정이 별도 클래스인 이유: **런타임 보조 설정**과 **전송 설정**의 성격이 다르기 때문이다.
`RuntimeProperties`는 "발행한 메시지를 메모리에 몇 개나 쌓아둘 것인가"라는 디버깅/관측 관련 설정이고,
`PublisherProperties`는 "어디로 어떻게 보낼 것인가"라는 전송 관련 설정이다.
관심사가 다르니 클래스를 분리했다.

### `SimulatorPublisherProperties`

```yaml
telemetryhub.simulator.publisher:
  mode: MEMORY          # LOGGING | MEMORY | BRIDGE | MQTT
  bridge:
    enabled: false
    ingestion-base-url: http://localhost:8092
    request-timeout-seconds: 5
  mqtt:
    enabled: false
    real-client-enabled: false
    broker-uri: tcp://localhost:1883
    qos: 0
    retry:
      max-attempts: 5
      initial-delay-ms: 1000
      backoff-multiplier: 2.0
      jitter-enabled: true
```

`mode` 필드 하나로 런타임에 어떤 퍼블리셔를 쓸지 결정하는 핵심 설정이다.
bridge, mqtt 설정이 같은 클래스에 중첩된 이유는 "어디로 보낼 것인가"라는 단일 관심사를 한 곳에 모으기 위함이다.

### `MqttConnectionManagerConfig` — 조건부 빈 등록

```java
@Bean
@ConditionalOnProperty(name = "...real-client-enabled", havingValue = "true")
public MqttConnectionManager realMqttConnectionManager(...) {
    return new RealMqttConnectionManager(...);
}

@Bean
@ConditionalOnProperty(name = "...real-client-enabled", havingValue = "true", matchIfMissing = false)
public MqttConnectionManager stubMqttConnectionManager(...) {
    return new StubMqttConnectionManager(...);
}
```

이 config가 별도 클래스로 분리된 이유: **"어떤 구현체를 쓸지"** 결정하는 책임을 구현체 자체에 두면 안 되기 때문이다.
`RealMqttConnectionManager`는 자신이 "실제 클라이언트다"만 알면 되고, "언제 사용되는지"는 config가 결정한다.
이 분리 덕분에 테스트에서 `StubMqttConnectionManager`를 직접 주입하는 것도 자연스러워진다.

---

## 5. Application 계층: 비즈니스 흐름 조율

### 5-1. `SimulatorRuntimeState` — 불변 상태 스냅샷

```java
public record SimulatorRuntimeState(
    SimulatorStatus status,
    int devices,
    long publishIntervalMs,
    int qos,
    int connectRampUpPerSecond,
    SimulationScenario scenario,
    int scenarioPercent
) {}
```

**왜 record(불변 객체)인가?**

`ControlService`는 `AtomicReference<SimulatorRuntimeState>`로 상태를 관리한다.
만약 상태를 mutable 객체로 만들면 스레드 A가 읽는 도중 스레드 B가 일부 필드만 수정하는 상황(partial update)이 생긴다.
불변 record를 쓰면 "새 상태를 만들어 통째로 교체"하는 방식이 강제되므로 race condition이 구조적으로 차단된다.

### 5-2. `DeviceSimulatorControlService` — 상태 전이와 루프 제어

이 클래스가 하는 일은 두 가지다:

1. **상태 전이**: `SimulatorRuntimeState`를 새로 만들어 `AtomicReference`에 저장
2. **루프 제어**: `DeviceSimulatorLoopService`의 start/stop/refresh를 호출

```
start(devices, intervalMs, ...)
  → SimulatorRuntimeState(RUNNING, ...) 생성
  → runtimeState.set(next)
  → loopService.start(next)

stop()
  → SimulatorRuntimeState(STOPPED, ...) 생성
  → loopService.stop()

scale(devices)
  → devices 수만 바꾼 새 상태 생성
  → loopService.refresh() (실행 중이면 재시작)

applyScenario(scenario, percent)
  → 시나리오만 바꾼 새 상태 생성
  → loopService.refresh()
```

**왜 ControlService와 LoopService를 분리했는가?**

ControlService는 "**무엇을** 어떻게 실행할 것인가"를 결정하고,
LoopService는 "**어떻게** 반복 실행할 것인가"를 담당한다.
이 두 책임을 합치면 클래스가 스케줄러 설정, 상태 전이, 메트릭 관리를 모두 담당하게 되어
어느 하나를 바꿀 때 다른 것에 영향을 줄 가능성이 높아진다.

### 5-3. `DeviceSimulatorLoopService` — 실행 루프와 메트릭

```java
// 핵심 구조
private final ScheduledExecutorService scheduler;
private final AtomicReference<ScheduledFuture<?>> loopRef;
private final AtomicReference<SimulatorMetricsSnapshot> metricsRef;

void start(SimulatorRuntimeState state) {
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
        () -> publishCycle(state),
        0,
        state.publishIntervalMs(),
        TimeUnit.MILLISECONDS
    );
    loopRef.set(future);
}

void publishCycle(SimulatorRuntimeState state) {
    SimulationEventBatch batch = previewService.generatePreview(state, state.devices());
    eventPublisher.publish(batch, state);
    updateMetrics(batch); // 누적 카운트 업데이트
}
```

**왜 `ScheduledExecutorService`를 쓰는가?**

Spring의 `@Scheduled`는 시작 시점이나 주기를 런타임에 바꿀 수 없다.
반면 `scheduleAtFixedRate`로 직접 등록하면 `scale()`이나 `applyScenario()` 호출 시 기존 Future를 cancel하고 새 파라미터로 재등록하는 것이 가능하다.

**왜 메트릭을 `AtomicReference`로 관리하는가?**

`cycleCount`, `telemetryCount` 등을 단순 `long` 필드로 두면 루프 스레드가 쓰는 중에 HTTP 요청 스레드가 읽을 때 값이 깨진다. `AtomicReference<SimulatorMetricsSnapshot>`은 불변 스냅샷을 통째로 교체하므로 락 없이 thread-safe하게 읽힌다.

### 5-4. `DeviceEventPreviewService` — 이벤트 샘플 생성

이 클래스는 순수한 데이터 생성기다. 의존성은 없고 입력(상태, 개수)을 받아 `SimulationEventBatch`를 반환한다.

```
generatePreview(state, count):
  for i in 0..count:
    deviceId = "device-{padded i}"
    
    TelemetryEvent:
      lat/lon: 서울 근방 랜덤
      speed: 0~120 (RECONNECT_STORM이면 0~20)
      heading: 0~360
      eventTime: now() (LATE_EVENT이면 now() - 30~180초)
    
    DeviceHealthEvent:
      battery: 0~100%
      temperature: -10~60℃
      signalStrength: 1~5 (RECONNECT_STORM이면 1~2)
      errorCode: "" (DUPLICATE_EVENT이면 "DUPLICATE_RISK")
    
    DrivingEvent (15% 확률로 생성):
      type: HARD_BRAKE | RAPID_ACCEL | SHARP_TURN | CRASH
      severity: LOW | MEDIUM | HIGH
```

**왜 PreviewService가 LoopService와 분리되어 있는가?**

`GET /sim/v1/preview` API는 시뮬레이터가 실제로 실행 중이 아니어도 "만약 실행한다면 어떤 이벤트가 나올까"를 미리 볼 수 있어야 한다. LoopService와 분리되어 있기 때문에 Controller가 LoopService를 거치지 않고 PreviewService를 직접 호출하는 게 가능하다.

### 5-5. `SimulationEventPublisher` 인터페이스 — 포트 정의

```java
public interface SimulationEventPublisher {
    void publish(SimulationEventBatch batch, SimulatorRuntimeState state);
}
```

이 인터페이스가 `application.publisher` 패키지에 있는 이유:
LoopService(application 계층)가 "어디로 보낼지"(infrastructure 계층)를 직접 알면 안 된다.
인터페이스를 application에 두고 구현체를 infrastructure에 두는 것이 **의존성 역전 원칙(DIP)**의 적용이다.

### 5-6. `SimulationEventBatch` — 한 사이클의 이벤트 묶음

```java
public record SimulationEventBatch(
    List<TelemetryEvent> telemetryEvents,
    List<DeviceHealthEvent> deviceHealthEvents,
    List<DrivingEvent> drivingEvents,
    SimulatorRuntimeState runtimeState,
    Instant generatedAt
) {}
```

하나의 publish cycle에서 세 종류의 이벤트가 함께 생성된다. 이걸 각각의 List로 따로 전달하면 메서드 시그니처가 복잡해지고 누락 위험도 있다. record 하나로 묶으면 "한 사이클의 결과"라는 의미가 코드에 명확히 드러난다.

---

## 6. Infrastructure 계층: 외부 기술 통합

### 6-1. `message` 패키지 — 직렬화와 토픽 계산

#### `TelemetryTopicResolver`

MQTT topic은 `telemetryhub/devices/{deviceId}/telemetry` 형태다.
이 계산 로직이 Serializer 안에 하드코딩되어 있으면 topic 규칙이 바뀔 때 Serializer를 수정해야 한다.
별도 클래스로 분리하면 topic 규칙 변경 → `TelemetryTopicResolver`만 수정하면 끝난다.

#### `SimulationEventSerializer`

```
batch → List<MessageEnvelope>

  telemetryEvents → topic: telemetryhub/devices/{id}/telemetry
                    key: deviceId
                    payload: JSON(TelemetryEvent)
  
  deviceHealthEvents → topic: telemetryhub/devices/{id}/health
  
  drivingEvents → topic: telemetryhub/devices/{id}/driving
```

**왜 직렬화를 별도 클래스에 두는가?**

`MqttSimulationEventPublisher`가 직접 Jackson을 호출하면 "MQTT로 보낸다"와 "JSON으로 변환한다"는 두 책임이 합쳐진다. 직렬화가 Serializer 클래스에 있으면 `InMemoryPublisher`나 `BridgePublisher`도 같은 직렬화 로직을 재사용할 수 있다.

#### `MessageEnvelope`

```java
public record MessageEnvelope(String topic, String key, String payload) {}
```

`topic`, `key`, `payload`는 MQTT 메시지의 핵심 3요소다.
이 record가 있어서 Serializer의 출력 타입과 각 Publisher의 입력 타입이 일치한다. Publisher 구현체들이 공통 타입을 다루므로 `RoutingPublisher`가 단순하게 유지된다.

---

### 6-2. `mqtt` 패키지 — MQTT 프로토콜 구현

#### `MqttConnectionManager` 인터페이스

```java
public interface MqttConnectionManager {
    MqttConnectionState connect();
    void disconnect();
    PublishResult publish(MessageEnvelope envelope);
}
```

이 인터페이스가 필요한 이유는 하나다: **실제 브로커가 없어도 전체 흐름을 테스트할 수 있어야 한다.**

| 구현체 | 사용 환경 |
|--------|---------|
| `RealMqttConnectionManager` | real-client-enabled=true, 실제 MQTT 브로커 필요 |
| `StubMqttConnectionManager` | 기본값, 브로커 없이 동작 |

인터페이스가 없으면 "실제 클라이언트 vs 스텁"을 `MqttSimulationEventPublisher` 내부에서 if/else로 분기해야 하고, 새 브로커 종류(예: AWS IoT Core)를 추가할 때 기존 코드를 수정해야 한다.

#### `RealMqttConnectionManager`

```
connect():
  - Eclipse Paho MqttClient 생성
  - options: timeout=5초, autoReconnect=false, cleanSession=true
  - client.connect(options)
  - 성공 → READY, 실패 → DEGRADED

publish(envelope):
  - state != READY이면 즉시 실패 반환
  - MqttMessage(payload, qos, retain) 생성
  - client.publish(topic, message)
```

`autoReconnect=false`인 이유: 재연결 정책을 `MqttSimulationEventPublisher`가 직접 관리하기 때문이다. 클라이언트 레벨에서 자동 재연결을 켜면 재시도 횟수나 백오프 시간을 제어하기 어려워진다.

#### `StubMqttConnectionManager`

```
connect():
  - connectionState = READY (실제 연결 없음)
  - return READY

publish(envelope):
  - state != READY이면 실패 반환
  - 그 외 항상 성공 반환 (실제 전송 없음)
```

개발 초기에 ingestion-service가 준비되지 않아도 시뮬레이터의 이벤트 생성 흐름, 메트릭 수집, API 응답 구조를 먼저 만들고 검증할 수 있다.

#### `MqttSimulationEventPublisher`

MQTT 모드에서 실제 배치 전송을 담당한다.

```
publishBatch(batch, state):
  1. mqtt.enabled=false이면 즉시 리턴 (no-op)
  2. serialize(batch) → List<MessageEnvelope>
  3. connectionManager.connect()
  4. for each envelope:
     - connectionManager.publish(envelope)
     - 실패 시 consecutiveFailureCount++
     - 성공 시 카운트 리셋
  5. snapshot 업데이트
```

**왜 `MqttSimulationEventPublisher`와 `RealMqttConnectionManager`가 별도인가?**

`MqttSimulationEventPublisher`는 "배치를 어떻게 MQTT 메시지로 변환하고 전송 결과를 어떻게 집계할 것인가"를 담당한다.
`RealMqttConnectionManager`는 "Paho 클라이언트를 어떻게 초기화하고 연결을 유지할 것인가"를 담당한다.
이 두 책임이 합쳐지면 "배치 직렬화 로직을 바꾸고 싶을 때" "Paho 클라이언트 설정"도 함께 건드려야 하는 위험이 생긴다.

#### `MqttConnectionState`

```
DISABLED    → mqtt.enabled=false (설정에서 비활성화)
IDLE        → 연결 시도 전
CONNECTING  → 연결 진행 중
READY       → 연결됨, 발행 가능
DEGRADED    → 연결 실패 또는 끊어짐
```

이 enum이 있어서 `/sim/v1/metrics` API가 MQTT 상태를 "연결됨/끊어짐"이 아닌 5단계로 세분화해서 반환할 수 있다. 운영 중 DEGRADED 상태가 길게 지속되면 브로커 문제임을 즉시 알 수 있다.

#### `MqttPublisherSnapshot`, `MqttRetryPolicySnapshot`

```java
public record MqttPublisherSnapshot(
    MqttConnectionState connectionState,
    String brokerUri,
    String clientId,
    int qos,
    boolean retain,
    int lastPublishedMessageCount,
    int consecutiveFailureCount,
    Instant lastAttemptAt,
    Instant lastSuccessAt,
    String lastFailureReason,
    MqttRetryPolicySnapshot retryPolicy
) {}
```

이 record들이 별도로 존재하는 이유: **외부로 노출하는 정보와 내부 상태를 분리**하기 위해서다.
`MqttSimulationEventPublisher`의 필드를 직접 JSON 직렬화하면 나중에 내부 구현을 바꿀 때 API 응답 형태가 의도치 않게 바뀐다. Snapshot record를 별도로 두면 내부 구현과 외부 표현이 독립된다.

---

### 6-3. `publisher` 패키지 — 퍼블리셔 구현체들

#### `RoutingSimulationEventPublisher`

```java
// SimulationEventPublisher 인터페이스 구현체
public void publish(SimulationEventBatch batch, SimulatorRuntimeState state) {
    switch (properties.getMode()) {
        case MQTT   -> mqttPublisher.publishBatch(batch, state);
        case MEMORY -> memoryPublisher.publishBatch(batch, state);
        case BRIDGE -> bridgePublisher.publishBatch(batch, state);
        default     -> loggingPublisher.publishBatch(batch, state);
    }
}
```

**이 클래스가 왜 필요한가?**

`LoopService`는 `SimulationEventPublisher`(인터페이스)에 의존한다.
LOGGING, MEMORY, BRIDGE, MQTT 중 어떤 구현체를 써야 하는지 결정하는 책임을 누군가 가져야 한다.
LoopService에 switch 문을 넣으면 "새 publisher 추가 시 LoopService를 수정해야 한다"는 문제가 생긴다.
`RoutingSimulationEventPublisher`가 이 라우팅을 전담하므로 나머지 코드는 변경 없이 새 모드를 추가할 수 있다.

#### `LoggingSimulationEventPublisher`

```
publishBatch():
  - log.info("published {} messages: sample topic={}", ...)
  - 실제 전송 없음
```

로컬에서 처음 시뮬레이터를 실행해볼 때 MQTT 브로커도 없고 ingestion-service도 없어도 "이벤트가 제대로 생성되는지"는 확인해야 한다. LOGGING 모드는 이 목적으로 존재한다.

#### `InMemorySimulationEventPublisher`

```
publishBatch():
  - serialize(batch) → List<MessageEnvelope>
  - 각 envelope → MemoryPublishedMessage(envelope, publishedAt=now())
  - messageStore.append(messages)
```

`GET /sim/v1/published-messages`로 최근 발행 메시지를 조회할 수 있게 해주는 구현체다.
개발 중 "어떤 topic에 어떤 payload가 실제로 발행되는지"를 눈으로 확인하는 데 사용한다.

#### `BridgeSimulationEventPublisher`

```
publishBatch():
  - serialize(batch) → List<MessageEnvelope>
  - WebClient.post(ingestionBaseUrl + "/ingestion/v1/mqtt/messages/batch")
    .body(List<BridgeMessageRequest>)
    .timeout(requestTimeoutSeconds)
    .block()
```

**왜 이 모드가 필요한가?**

MQTT 브로커를 띄우지 않고 ingestion-service만 실행한 상태에서 전체 파이프라인(이벤트 생성 → ingestion → Kafka → stream-processor)을 검증하고 싶을 때 사용한다. 브로커가 없어도 ingestion의 HTTP endpoint로 배치를 직접 밀어 넣을 수 있다.

---

### 6-4. `store` 패키지 — 순환 버퍼

#### `InMemoryPublishedMessageStore`

```java
// 핵심 구조
private final LinkedBlockingDeque<MemoryPublishedMessage> buffer;
private final int maxMessages;

void append(List<MemoryPublishedMessage> messages) {
    synchronized (this) {
        for (var msg : messages) {
            buffer.offerFirst(msg);           // 최신 메시지를 앞에 추가
            if (buffer.size() > maxMessages) {
                buffer.pollLast();            // 오래된 메시지 제거
            }
        }
    }
}

List<MemoryPublishedMessage> recent(int limit) {
    synchronized (this) {
        return buffer.stream().limit(limit).toList();
    }
}
```

**왜 `LinkedBlockingDeque`인가?**

- `offerFirst` + `pollLast` 조합으로 O(1) 삽입/삭제
- 최신 메시지가 항목 앞에 오므로 `recent(20)` 호출 시 앞에서 20개만 슬라이싱하면 끝
- 고정 크기 유지가 간단 (maxMessages 초과 시 오래된 것 자동 제거)

이 저장소는 **운영용 퍼시스턴스가 아닌 개발 디버깅 도구**임을 명확히 하기 위해 `BufferEnabled` 플래그를 두었다. 운영 환경에서 버퍼가 메모리를 차지하지 않도록 `published-message-buffer-enabled=false`로 끌 수 있다.

---

## 7. Web 계층

### `DeviceSimulatorController`

| 엔드포인트 | 메서드 | 역할 |
|-----------|--------|------|
| `/sim/v1/start` | POST | 디바이스 수, 발행 주기, QoS 설정 후 시작 |
| `/sim/v1/stop` | POST | 루프 중지 |
| `/sim/v1/scale` | POST | 실행 중 디바이스 수 변경 (재시작 없이) |
| `/sim/v1/scenario/{scenario}` | POST | 시나리오 적용 (percent로 일부만 적용 가능) |
| `/sim/v1/status` | GET | 현재 상태 (IDLE/RUNNING/STOPPED, 설정값) |
| `/sim/v1/metrics` | GET | 누적 이벤트 수, MQTT 연결 상태 |
| `/sim/v1/preview` | GET | 샘플 이벤트 미리보기 (실행 안 해도 됨) |
| `/sim/v1/published-messages` | GET | 최근 발행 메시지 (MEMORY 모드일 때 유용) |
| `/sim/v1/published-messages/clear` | POST | 버퍼 초기화 |

Controller는 단순 진입점으로 비즈니스 로직 없이 `ControlService`에 위임만 한다.

### Response DTO들 — 왜 각각 별도 클래스인가

```
SimulatorStatusResponse  → /status
SimulatorMetricsResponse → /metrics (SimulatorMetricsSnapshot + MqttPublisherSnapshot 조합)
SimulatorPreviewResponse → /preview
PublishedMessageResponse → /published-messages
```

각 API의 응답 형태가 다르고, 내부 도메인 모델(`SimulatorMetricsSnapshot` 등)의 필드명이나 구조를 그대로 노출하면 내부 구현 변경 시 API가 깨질 수 있다. 별도 DTO를 두어 내부 모델과 외부 표현을 분리한다.

---

## 8. 전체 데이터 흐름 요약

```
1. POST /sim/v1/start?devices=1000&intervalMs=1000
        │
2.  DeviceSimulatorControlService.start()
    - SimulatorRuntimeState(RUNNING, devices=1000, intervalMs=1000) 생성
    - AtomicReference에 저장
        │
3.  DeviceSimulatorLoopService.start(state)
    - ScheduledExecutorService.scheduleAtFixedRate(publishCycle, 0, 1000ms)
        │
4.  [매 1000ms마다] publishCycle()
        │
5.  DeviceEventPreviewService.generatePreview(state, 1000)
    - 1000개 디바이스 × (TelemetryEvent + DeviceHealthEvent + DrivingEvent 15% 확률)
    - SimulationEventBatch 반환
        │
6.  RoutingSimulationEventPublisher.publish(batch, state)
    - mode=MQTT이면 → MqttSimulationEventPublisher
        │
7.  SimulationEventSerializer.serialize(batch)
    - TelemetryEvent → MessageEnvelope(topic=".../{id}/telemetry", key=deviceId, payload=JSON)
    - ...
        │
8.  MqttConnectionManager.connect() → READY
    MqttConnectionManager.publish(envelope) × N
        │
    [RealMqttConnectionManager]  Eclipse Paho → 실제 MQTT 브로커
    [StubMqttConnectionManager]  아무것도 안 함 (항상 성공)
        │
9.  MetricsSnapshot 업데이트 (cycleCount++, telemetryCount += 1000)
```

---

## 9. 설계 결정 요약

| 결정 | 이유 |
|------|------|
| application/infrastructure 계층 분리 | MQTT 브로커 없이도 비즈니스 로직 테스트 가능 |
| `SimulationEventPublisher` 인터페이스 | 새 publisher 추가 시 기존 코드 무수정 (OCP) |
| `MqttConnectionManager` 인터페이스 | Real/Stub 교체 가능, 테스트 용이성 확보 |
| `SimulatorRuntimeState`를 불변 record로 | AtomicReference 교체 패턴으로 race condition 차단 |
| `RoutingSimulationEventPublisher` 별도 분리 | 모드 라우팅 책임을 LoopService에서 제거 |
| `TelemetryTopicResolver` 별도 분리 | topic 규칙 변경 시 단일 지점 수정 |
| `SimulationEventSerializer` 별도 분리 | 직렬화 로직을 여러 Publisher가 재사용 |
| `InMemoryPublishedMessageStore` 순환 버퍼 | 개발 중 메모리 폭증 없이 최근 메시지만 유지 |
| `MqttPublisherSnapshot` 별도 record | 내부 상태와 외부 API 응답 형태 분리 |
| `DeviceEventPreviewService` 별도 분리 | 실행 없이 `/preview` API만으로 샘플 생성 가능 |