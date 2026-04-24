# TelemetryHub Device Simulator Review

## 목적
`device-simulator`는 실제 차량/IoT 디바이스 대신 텔레메트리 이벤트를 만들어 내는 모듈이다.

현재 구현 목표는 두 가지다.

1. 브로커 없이도 이벤트 생성과 시나리오 적용을 검증할 수 있어야 한다.
2. 나중에 MQTT 브로커를 붙여도 구조를 다시 뜯지 않도록 전송 경계를 미리 분리해둬야 한다.

즉, 지금의 시뮬레이터는 단순 랜덤 데이터 생성기가 아니라 아래 두 역할을 같이 가진다.

- 제어 가능한 이벤트 발생기
- 전송 경계를 가진 퍼블리셔 실험 모듈

## 전체 구조
```text
DeviceSimulatorController
  -> DeviceSimulatorControlService
     -> DeviceSimulatorLoopService
        -> DeviceEventPreviewService
        -> SimulationEventPublisher
           -> RoutingSimulationEventPublisher
              -> LoggingSimulationEventPublisher
              -> InMemorySimulationEventPublisher
              -> MqttSimulationEventPublisher
                    -> MqttConnectionManager
                       -> StubMqttConnectionManager
                       -> RealMqttConnectionManager
```

## 동작 흐름
1. `POST /sim/v1/start` 호출로 시뮬레이터를 시작한다.
2. `DeviceSimulatorControlService`가 현재 런타임 상태를 갱신하고 loop를 시작한다.
3. `DeviceSimulatorLoopService`가 주기적으로 배치를 생성한다.
4. `DeviceEventPreviewService`가 `contracts` 기반 이벤트를 만든다.
5. `RoutingSimulationEventPublisher`가 현재 모드에 따라 `LOGGING`, `MEMORY`, `MQTT` 중 하나로 라우팅한다.
6. `MEMORY` 모드면 최근 발행 메시지를 저장하고, `MQTT` 모드면 연결 관리자 경유로 publish한다.
7. `GET /sim/v1/metrics`, `GET /sim/v1/published-messages`로 상태를 확인한다.

## 패키지별 역할

### 1. bootstrap
- `DeviceSimulatorApplication`
  - Spring Boot 시작점
  - `@ConfigurationPropertiesScan`으로 publisher 설정을 바인딩한다.

### 2. domain
- `SimulationScenario`
  - 시뮬레이터 시나리오 종류
  - `STEADY`, `RECONNECT_STORM`, `LATE_EVENT`, `DUPLICATE_EVENT`
- `SimulatorStatus`
  - 시뮬레이터 상태
  - `IDLE`, `RUNNING`, `STOPPED`

### 3. application
- `SimulatorRuntimeState`
  - 현재 시뮬레이터 실행 설정과 상태를 담는 런타임 모델
  - 디바이스 수, 주기, qos, scenario, update 시각을 가진다.
- `SimulationEventBatch`
  - 한 번의 루프에서 생성된 이벤트 묶음
  - `telemetry`, `deviceHealth`, `drivingEvent`를 함께 운반한다.
- `SimulationEventPublisher`
  - 이벤트 배치를 어디로 보낼지 추상화한 인터페이스
  - loop는 이 인터페이스만 알고 실제 전송 방식은 모른다.
- `SimulatorBatchSummary`
  - 마지막 루프에서 몇 건이 생성됐는지 요약한다.
- `SimulatorMetricsSnapshot`
  - 누적 cycle 수, 이벤트 수, 마지막 publish 시각, 마지막 배치 요약을 가진다.
- `DeviceEventPreviewService`
  - 실제 이벤트 생성기
  - `contracts`의 `TelemetryEvent`, `DeviceHealthEvent`, `DrivingEvent`를 랜덤 생성한다.
  - 시나리오에 따라 속도, signalStrength, late event 시간 차이, duplicate 위험 표시를 다르게 만든다.
- `DeviceSimulatorLoopService`
  - 스케줄러 기반 루프 실행기
  - 주기마다 배치를 생성하고 publisher로 넘긴다.
  - 누적 metrics도 여기서 갱신한다.
- `DeviceSimulatorControlService`
  - 컨트롤 플레인 중심 서비스
  - start, stop, scale, scenario 변경, metrics 조회, memory publish 조회를 담당한다.

### 4. config
- `SimulatorPublisherProperties`
  - publisher 모드와 MQTT 설정을 바인딩한다.
  - `LOGGING`, `MEMORY`, `MQTT` 모드 선택의 기준이 된다.
  - MQTT broker URI, clientId, qos, retain, retry 정책을 포함한다.
- `MqttConnectionManagerConfig`
  - `real-client-enabled` 값에 따라 실제/스텁 MQTT 연결 관리자를 선택한다.

### 5. infrastructure
- `RoutingSimulationEventPublisher`
  - 실제 publisher 선택기
  - 현재 설정에 따라 logging, memory, mqtt 중 하나를 호출한다.
- `LoggingSimulationEventPublisher`
  - 메시지를 실제로 보내지 않고 로그만 남긴다.
  - 샘플 topic과 payload 크기를 확인할 수 있다.
- `InMemorySimulationEventPublisher`
  - 직렬화된 메시지를 메모리 저장소에 넣는다.
  - 브로커 없이 simulator를 검증하는 현재 기본 모드다.
- `InMemoryPublishedMessageStore`
  - 최근 publish 메시지 버퍼
  - 최대 500개까지 보관한다.
- `MemoryPublishedMessage`
  - 메모리 모드에서 저장되는 메시지 모델
  - topic, key, payload, publishedAt을 가진다.
- `SimulationEventSerializer`
  - 이벤트 배치를 `MessageEnvelope` 목록으로 바꾼다.
  - topic 계산과 JSON 직렬화를 묶는 계층이다.
- `TelemetryTopicResolver`
  - 이벤트 종류별 topic 규칙을 계산한다.
  - 현재 규칙은 `telemetryhub/devices/{deviceId}/...` 형태다.
- `MessageEnvelope`
  - topic, key, payload를 담는 직렬화 결과 모델
- `MqttSimulationEventPublisher`
  - MQTT 모드의 publisher
  - 현재는 연결 관리자 경유 publish와 snapshot 갱신까지 담당한다.
  - 실제 브로커가 없더라도 상태 모델은 유지된다.
- `MqttConnectionManager`
  - MQTT 연결 책임을 추상화한 인터페이스
  - `connect`, `disconnect`, `publish`를 정의한다.
- `StubMqttConnectionManager`
  - 브로커 없는 개발용 스텁 구현
  - 설정상 enabled면 READY 상태처럼 동작한다.
- `RealMqttConnectionManager`
  - Paho 기반 실제 MQTT 연결 관리자
  - 나중에 브로커가 준비되면 이 구현이 활성화된다.
- `MqttConnectionState`
  - MQTT 연결 상태 enum
- `MqttPublisherSnapshot`
  - MQTT 퍼블리셔 관측 모델
  - 마지막 시도, 마지막 성공, 실패 횟수, retry 정책을 담는다.
- `MqttRetryPolicySnapshot`
  - retry 정책 스냅샷

### 6. web
- `DeviceSimulatorController`
  - 외부 제어 API 진입점
  - 시작, 중지, 스케일, 시나리오 변경, 상태 조회, metrics 조회, preview 조회, published message 조회를 제공한다.

### 7. web.dto
- `SimulatorStatusResponse`
  - 현재 시뮬레이터 상태 응답
- `SimulatorPreviewResponse`
  - 샘플 생성 이벤트 응답
- `SimulatorMetricsResponse`
  - 루프 누적 통계 + MQTT 퍼블리셔 상태 응답
- `PublishedMessageResponse`
  - 메모리 버퍼에 저장된 메시지 응답

## Publisher 모드 설명

### 왜 publisher 모드가 필요한가
시뮬레이터는 "이벤트를 만든다"와 "그 이벤트를 어디로 보낸다"를 분리해두고 있다.

이유는 간단하다.

- 브로커가 없을 때도 이벤트 생성 로직을 먼저 개발할 수 있어야 한다.
- 나중에 브로커가 생기면 전송 방식만 바꾸고 나머지 코드는 유지하고 싶다.
- 로그 확인, 메모리 적재 확인, 실제 MQTT 전송은 개발 단계마다 필요한 방식이 다르다.

즉 `publisher mode`는 "생성된 이벤트 배치를 어떤 방식으로 소비할지"를 고르는 스위치다.

현재 이 선택은 [SimulatorPublisherProperties.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/config/SimulatorPublisherProperties.java) 와 [RoutingSimulationEventPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/RoutingSimulationEventPublisher.java) 에서 이루어진다.

### 현재 기본값
현재 [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/resources/application.yml) 기준 기본 publisher 모드는 `MEMORY`다.

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: MEMORY
```

이 뜻은:

- 이벤트는 실제로 생성된다.
- topic/payload 직렬화도 실제처럼 수행된다.
- 결과는 브로커가 아니라 메모리 저장소에 쌓인다.
- `GET /sim/v1/published-messages`로 최근 발행 메시지를 바로 볼 수 있다.

브로커가 없는 현재 단계에서는 이 모드가 가장 실용적이다.

### publisher 모드를 수정하려면
가장 쉬운 방법은 [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/resources/application.yml)의 `telemetryhub.simulator.publisher.mode` 값을 바꾸는 것이다.

가능한 값:

- `LOGGING`
- `MEMORY`
- `MQTT`

예시:

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: LOGGING
```

또는

```yaml
telemetryhub:
  simulator:
    publisher:
      mode: MQTT
      mqtt:
        enabled: true
        real-client-enabled: true
        broker-uri: tcp://localhost:1883
```

실행 환경에서 프로퍼티로 덮어써도 된다. 예를 들면:

```powershell
.\gradlew :apps:telemetryhub:device-simulator:bootRun --args="--telemetryhub.simulator.publisher.mode=LOGGING"
```

또는:

```powershell
.\gradlew :apps:telemetryhub:device-simulator:bootRun --args="--telemetryhub.simulator.publisher.mode=MQTT --telemetryhub.simulator.publisher.mqtt.enabled=true --telemetryhub.simulator.publisher.mqtt.real-client-enabled=true"
```

### 각 모드의 차이

#### 1. `LOGGING`
역할:
- 이벤트를 직렬화한 뒤 실제 전송은 하지 않고 로그만 남긴다.

장점:
- 가장 단순하다.
- 브로커 없이 바로 동작한다.
- topic 계산과 payload 크기 정도를 가볍게 확인할 수 있다.

한계:
- 실제 publish 결과를 API로 재확인하기 어렵다.
- 데이터가 저장되지 않는다.

언제 쓰면 좋은가:
- 정말 초기 단계에서 topic 규칙만 보고 싶을 때
- 로그 위주로 빠르게 확인할 때

관련 클래스:
- [LoggingSimulationEventPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/LoggingSimulationEventPublisher.java)

#### 2. `MEMORY`
역할:
- 이벤트를 직렬화한 뒤 메모리 저장소에 적재한다.

장점:
- 브로커 없이도 "실제로 publish된 결과처럼" 검증할 수 있다.
- `GET /sim/v1/published-messages`로 최근 메시지를 조회할 수 있다.
- 현재 개발 단계에서 가장 유용하다.

한계:
- 외부 시스템과 연결되지는 않는다.
- 프로세스가 내려가면 데이터는 사라진다.
- 진짜 네트워크 장애나 브로커 반응은 검증할 수 없다.

언제 쓰면 좋은가:
- 브로커 없이 simulator를 마무리할 때
- ingestion-service 붙이기 전에 payload 구조를 검증할 때
- scenario별 메시지 차이를 눈으로 확인할 때

관련 클래스:
- [InMemorySimulationEventPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/InMemorySimulationEventPublisher.java)
- [InMemoryPublishedMessageStore.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/InMemoryPublishedMessageStore.java)

#### 3. `MQTT`
역할:
- 이벤트를 실제 MQTT publish 경로로 보낸다.

장점:
- 실제 브로커와 붙이면 end-to-end smoke test가 가능하다.
- topic, payload, connection manager, publish 경계를 실제처럼 확인할 수 있다.
- 향후 reconnect/backoff, 장애 시나리오 검증 기반이 된다.

한계:
- 브로커가 필요하다.
- 네트워크와 broker 상태에 따라 실패 가능성이 생긴다.
- 지금 시점에서는 브로커가 없으니 기본 개발 모드로는 부적합하다.

언제 쓰면 좋은가:
- Docker로 EMQX/Mosquitto 같은 broker를 띄운 뒤
- simulator에서 broker까지 실제 publish가 되는지 확인할 때
- ingestion-service와 연결된 smoke test를 할 때

관련 클래스:
- [MqttSimulationEventPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/MqttSimulationEventPublisher.java)
- [MqttConnectionManager.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/MqttConnectionManager.java)
- [StubMqttConnectionManager.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/StubMqttConnectionManager.java)
- [RealMqttConnectionManager.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/RealMqttConnectionManager.java)

### MQTT 모드에서 `enabled` 와 `real-client-enabled` 차이
이 부분은 헷갈리기 쉬워서 따로 구분해야 한다.

`mode=MQTT`는 "publisher 선택"이다.  
즉 라우팅이 `MqttSimulationEventPublisher`로 간다는 뜻이다.

`mqtt.enabled`는 "MQTT publish 자체를 허용할지" 여부다.

`mqtt.real-client-enabled`는 "연결 관리자를 실제 클라이언트로 쓸지, 스텁으로 쓸지" 여부다.

정리하면:

- `mode=MQTT`, `enabled=false`
  - MQTT 퍼블리셔 경로는 타지만 실제 publish는 비활성 상태
  - snapshot에는 disabled 상태가 남는다.

- `mode=MQTT`, `enabled=true`, `real-client-enabled=false`
  - MQTT 퍼블리셔 경로는 사용
  - 실제 브로커 대신 `StubMqttConnectionManager` 사용
  - 구조 검증용

- `mode=MQTT`, `enabled=true`, `real-client-enabled=true`
  - 실제 Paho 클라이언트 기반 publish 시도
  - 브로커가 있어야 의미가 있다.

### 지금 무엇을 쓰는 게 맞는가
현재는 브로커가 없으므로 `MEMORY`가 가장 적절하다.

추천 이유:

- topic/payload가 실제와 가깝게 만들어진다.
- 브로커 없이도 최근 publish 결과를 API로 확인할 수 있다.
- simulator 개발 완료 기준을 만족시킨다.
- 나중에 broker만 추가되면 `MQTT`로 전환 가능하다.

즉 현재의 기본값 `MEMORY`는 임시방편이 아니라, 브로커 없는 단계에서 가장 검증성이 좋은 모드다.

## 현재 API 역할
- `POST /sim/v1/start`
  - 시뮬레이터 시작
- `POST /sim/v1/stop`
  - 시뮬레이터 정지
- `POST /sim/v1/scale`
  - device 수 변경
- `POST /sim/v1/scenario/{scenario}`
  - 시나리오 변경
- `GET /sim/v1/status`
  - 런타임 상태 조회
- `GET /sim/v1/metrics`
  - 누적 생성 통계와 MQTT 상태 조회
- `GET /sim/v1/preview`
  - 샘플 이벤트 즉시 조회
- `GET /sim/v1/published-messages`
  - 메모리 publisher에 저장된 최근 메시지 조회
- `POST /sim/v1/published-messages/clear`
  - 메모리 버퍼 초기화

## 현재 시점 평가
현재 `device-simulator`는 브로커 없이 개발 가능한 MVP 범위에서는 완료 상태로 볼 수 있다.

이미 가능한 것:
- 제어 API
- 이벤트 생성
- 시나리오 적용
- 루프 실행
- topic/payload 직렬화
- memory publish 검증
- mqtt 확장 구조 확보

아직 남은 것:
- 실제 브로커와 붙여 end-to-end publish 확인
- reconnect/backoff/jitter를 실제 연결 실패에 맞춰 정교화
- device별 연결 세션 모델 고도화

## 권장 다음 단계
시뮬레이터는 여기서 잠시 멈추고 `ingestion-service`로 넘어가는 게 맞다.

이유:
- simulator는 현재 목표 범위에서 충분히 동작한다.
- 이 프로젝트의 핵심 난도는 수집 이후 처리 파이프라인에 있다.
- 브로커는 나중에 docker로 띄운 뒤 `MQTT` 모드 smoke test만 추가하면 된다.
