# TelemetryHub Device Simulator Review

## 목적
`device-simulator`는 차량/IoT 디바이스가 보내는 것과 유사한 텔레메트리 이벤트를 생성하고, 제어 API를 통해 실행 상태를 조작할 수 있게 만든 모듈이다.

현재 이 모듈의 역할은 단순한 랜덤 데이터 생성기가 아니라 아래까지 포함한다.

- 제어 가능한 이벤트 발생기
- 시나리오 기반 데이터 왜곡/지연 재현기
- publisher mode 전환이 가능한 전송 경계
- 브로커 없이도 검증 가능한 개발용 관측 도구

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
              -> BridgeSimulationEventPublisher
              -> MqttSimulationEventPublisher
```

핵심 흐름은 아래와 같다.

1. API가 현재 runtime state를 바꾼다.
2. loop service가 주기적으로 이벤트 배치를 만든다.
3. preview service가 `TelemetryEvent`, `DeviceHealthEvent`, `DrivingEvent`를 생성한다.
4. publisher routing이 현재 mode에 따라 메시지를 보낸다.
5. metrics / published-messages API로 현재 동작을 확인한다.

## 주요 상태 모델
현재 시뮬레이터 상태는 [SimulatorRuntimeState.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/SimulatorRuntimeState.java) 로 표현된다.

필드 의미:

- `status`
  - `IDLE`, `RUNNING`, `STOPPED`
- `deviceCount`
  - 한 번의 loop cycle에서 생성할 디바이스 수
- `publishIntervalMs`
  - loop 주기
- `qos`
  - MQTT publisher 사용 시 QoS
- `connectRampUpPerSecond`
  - 연결 ramp-up 관련 상태값
- `scenarioPercent`
  - 시나리오 영향 비율
- `scenario`
  - `STEADY`, `RECONNECT_STORM`, `LATE_EVENT`, `DUPLICATE_EVENT`
- `updatedAt`
  - 상태 변경 시각

초기 상태는 `idle()` 기준으로:

- `status=IDLE`
- `deviceCount=0`
- `publishIntervalMs=1000`
- `qos=0`
- `connectRampUpPerSecond=100`
- `scenario=STEADY`
- `scenarioPercent=0`

## 시나리오별 실제 변화
[DeviceEventPreviewService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceEventPreviewService.java) 가 시나리오별 이벤트 차이를 만든다.

### `STEADY`
- 기본 랜덤 telemetry / health / driving event 생성

### `RECONNECT_STORM`
- telemetry 속도를 더 낮은 범위로 생성
- device health의 `signalStrength`를 낮게 생성
- driving event는 `HARD_BRAKE` 쪽으로 치우침

### `LATE_EVENT`
- `eventTime`을 `ingestTime`보다 30~180초 과거로 생성
- late event / out-of-order 검증용

### `DUPLICATE_EVENT`
- device health의 `errorCode`에 `DUPLICATE_RISK` 설정
- driving event는 `CRASH` 쪽으로 치우침

중요한 점:

- `scenarioPercent`는 현재 구현상 `DrivingEvent` 생성 threshold에 직접 반영된다.
- 즉 “모든 이벤트의 몇 퍼센트를 바꾼다”기보다, driving event가 얼마나 자주 섞일지에 더 가깝다.

## Publisher Mode
publisher mode는 “생성한 메시지를 어디로 보낼지”를 결정한다.

현재 지원 모드:

- `LOGGING`
- `MEMORY`
- `BRIDGE`
- `MQTT`

### `LOGGING`
- 실제 전송 없이 로그만 남긴다.
- topic / payload 구조 점검용이다.

### `MEMORY`
- 최근 발행 메시지를 메모리에 저장한다.
- 브로커 없이 simulator를 검증할 때 가장 유용하다.
- `GET /sim/v1/published-messages`로 직접 확인 가능하다.

### `BRIDGE`
- ingestion-service의 `/ingestion/v1/mqtt/messages/batch`로 HTTP 브리지 호출을 한다.
- MQTT broker 없이도 simulator -> ingestion 흐름을 검증할 수 있다.

### `MQTT`
- 실제 MQTT publish 경로를 사용한다.
- broker가 준비된 뒤 end-to-end smoke test 용도로 쓴다.

## API 공통 응답 형태
모든 API는 `ApiResponse<T>` 래퍼를 사용한다.

개념적으로는 아래처럼 응답된다.

```json
{
  "success": true,
  "data": {
    "...": "..."
  }
}
```

아래 설명의 응답 필드는 모두 `data` 내부 기준이다.

## API 상세

### 1. `POST /sim/v1/start`
시뮬레이터 loop를 시작한다.

정의 위치:

- [DeviceSimulatorController.start()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:29)

요청 파라미터:

- `devices`
  - 타입: `int`
  - 기본값: `1000`
  - 검증: `@Min(1)`
  - 의미: cycle당 생성할 디바이스 수
- `intervalMs`
  - 타입: `int`
  - 기본값: `1000`
  - 검증: `@Min(100)`
  - 의미: loop 주기
- `qos`
  - 타입: `int`
  - 기본값: `0`
  - 검증: `@Min(0)`, `@Max(1)`
  - 의미: MQTT publisher QoS
- `connectRampUpPerSecond`
  - 타입: `int`
  - 기본값: `100`
  - 검증: `@Min(1)`
  - 의미: 연결 ramp-up 상태값

응답 모델:

- [SimulatorStatusResponse.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/dto/SimulatorStatusResponse.java)

응답 필드:

- `status`
- `deviceCount`
- `publishIntervalMs`
- `qos`
- `connectRampUpPerSecond`
- `scenarioPercent`
- `scenario`
- `updatedAt`

실제 동작 로직:

1. [DeviceSimulatorControlService.start()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:32)가 새 `SimulatorRuntimeState`를 만든다.
2. `status=RUNNING`으로 바꾸고, 요청값을 상태에 반영한다.
3. 기존 `scenario`, `scenarioPercent`는 유지한다.
4. [DeviceSimulatorLoopService.start()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:30)를 호출한다.
5. loop service는 기존 future가 있으면 먼저 `stop()`한 뒤 새 `scheduleAtFixedRate` 작업을 등록한다.

예시:

```http
POST /sim/v1/start?devices=500&intervalMs=1000&qos=1&connectRampUpPerSecond=50
```

### 2. `POST /sim/v1/stop`
시뮬레이터 loop를 멈춘다.

정의 위치:

- [DeviceSimulatorController.stop()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:40)

요청 파라미터:

- 없음

응답 모델:

- `SimulatorStatusResponse`

실제 동작 로직:

1. [DeviceSimulatorControlService.stop()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:48)가 현재 상태를 읽는다.
2. `status=STOPPED`인 새 상태를 만든다.
3. [DeviceSimulatorLoopService.stop()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:48)를 호출해 현재 future를 cancel 한다.
4. 누적 metrics는 유지되고, 새 cycle 생성만 멈춘다.

### 3. `POST /sim/v1/scale`
디바이스 수를 변경한다.

정의 위치:

- [DeviceSimulatorController.scale()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:46)

요청 파라미터:

- `devices`
  - 타입: `int`
  - 검증: `@Min(1)`
  - 의미: 새 device count

응답 모델:

- `SimulatorStatusResponse`

실제 동작 로직:

1. [DeviceSimulatorControlService.scale()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:65)가 현재 상태를 읽는다.
2. `deviceCount`만 바꾼 새 상태를 만든다.
3. [DeviceSimulatorLoopService.refresh()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:42)를 호출한다.
4. loop가 돌고 있으면 기존 loop를 멈추고 새 상태 기준으로 다시 시작한다.

중요한 점:

- scale은 누적 metrics를 리셋하지 않는다.
- 다음 cycle부터 새 값이 반영된다.

### 4. `POST /sim/v1/scenario/{scenario}`
시뮬레이션 시나리오를 변경한다.

정의 위치:

- [DeviceSimulatorController.applyScenario()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:53)

경로 파라미터:

- `scenario`
  - 타입: `SimulationScenario`
  - 허용값:
    - `STEADY`
    - `RECONNECT_STORM`
    - `LATE_EVENT`
    - `DUPLICATE_EVENT`

쿼리 파라미터:

- `percent`
  - 타입: `int`
  - 기본값: `0`
  - 검증: `@Min(0)`, `@Max(100)`
  - 의미: 시나리오 적용 비율

응답 모델:

- `SimulatorStatusResponse`

실제 동작 로직:

1. [DeviceSimulatorControlService.applyScenario()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:82)가 현재 상태를 읽는다.
2. `scenario`, `scenarioPercent`만 바꾼 새 상태를 만든다.
3. loop가 실행 중이면 `refresh()`를 통해 다음 cycle부터 새 시나리오를 반영한다.

예시:

```http
POST /sim/v1/scenario/LATE_EVENT?percent=40
```

### 5. `GET /sim/v1/status`
현재 runtime state를 조회한다.

정의 위치:

- [DeviceSimulatorController.status()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:61)

요청 파라미터:

- 없음

응답 모델:

- `SimulatorStatusResponse`

실제 동작 로직:

- [DeviceSimulatorControlService.getStatus()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:99)가 현재 상태를 그대로 반환한다.

### 6. `GET /sim/v1/metrics`
누적 생성 통계와 MQTT publisher 상태를 조회한다.

정의 위치:

- [DeviceSimulatorController.metrics()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:66)

요청 파라미터:

- 없음

응답 모델:

- [SimulatorMetricsResponse.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/dto/SimulatorMetricsResponse.java)

응답 필드:

- `cycleCount`
  - loop 실행 횟수
- `telemetryCount`
  - 누적 telemetry 개수
- `deviceHealthCount`
  - 누적 device health 개수
- `drivingEventCount`
  - 누적 driving event 개수
- `lastPublishedAt`
  - 마지막 cycle 처리 시각
- `lastBatch`
  - 마지막 cycle 생성 결과 요약
  - `requestedDevices`
  - `telemetryCount`
  - `deviceHealthCount`
  - `drivingEventCount`
  - `generatedAt`
- `mqttPublisher`
  - MQTT publisher 관측 정보
  - `connectionState`
  - `brokerUri`
  - `clientId`
  - `qos`
  - `retain`
  - `lastPublishedMessageCount`
  - `consecutiveFailureCount`
  - `lastAttemptAt`
  - `lastSuccessAt`
  - `lastFailureReason`
  - `retryPolicy`

실제 동작 로직:

1. [DeviceSimulatorLoopService.getMetrics()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:60)에서 loop 통계를 읽는다.
2. [DeviceSimulatorControlService.getMqttPublisherSnapshot()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:107)에서 MQTT publisher snapshot을 읽는다.
3. 응답 DTO가 둘을 합쳐 하나의 응답으로 변환한다.

중요한 점:

- publisher mode가 `MEMORY`여도 `mqttPublisher` 필드는 항상 포함될 수 있다.
- 이 값은 “현재 MQTT publisher 계층의 관측 상태”이지, 반드시 실제 publish가 일어났다는 의미는 아니다.

### 7. `GET /sim/v1/preview`
현재 상태 기준으로 샘플 이벤트를 즉시 생성해서 보여준다.

정의 위치:

- [DeviceSimulatorController.preview()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:73)

요청 파라미터:

- `count`
  - 타입: `int`
  - 기본값: `5`
  - 검증: `@Min(1)`, `@Max(100)`
  - 의미: 샘플 디바이스 수

응답 모델:

- [SimulatorPreviewResponse.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/dto/SimulatorPreviewResponse.java)

응답 필드:

- `telemetryEvents`
- `deviceHealthEvents`
- `drivingEvents`

실제 동작 로직:

1. 현재 runtime state를 읽는다.
2. [DeviceEventPreviewService.generatePreview()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceEventPreviewService.java:19)가 `count` 기준으로 샘플 이벤트를 만든다.
3. 결과는 메모리에 저장되지 않고 실제 publish도 하지 않는다.

중요한 점:

- `count`는 sample device 수이지 loop의 실제 `deviceCount`를 바꾸지 않는다.
- 현재 scenario의 영향을 그대로 받는다.
- `drivingEvents`는 threshold 기반이므로 개수가 `count`보다 적을 수 있다.

### 8. `GET /sim/v1/published-messages`
메모리 publisher에 저장된 최근 메시지를 조회한다.

정의 위치:

- [DeviceSimulatorController.publishedMessages()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:83)

요청 파라미터:

- `limit`
  - 타입: `int`
  - 기본값: `20`
  - 검증: `@Min(1)`, `@Max(200)`
  - 의미: 최근 메시지를 몇 개까지 볼지

응답 모델:

- [PublishedMessageResponse.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/dto/PublishedMessageResponse.java)

응답 필드:

- `topic`
- `key`
- `payload`
- `publishedAt`

실제 동작 로직:

1. [DeviceSimulatorControlService.getRecentPublishedMessages()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:111)가 in-memory store에서 최근 메시지를 읽는다.
2. `limit` 개수만큼 잘라서 응답 DTO로 변환한다.

중요한 점:

- 이 API는 `MEMORY` 모드 검증용이다.
- 운영 모드에서 `published-message-buffer-enabled=false`이면 빈 리스트가 나올 수 있다.

### 9. `POST /sim/v1/published-messages/clear`
메모리 publisher 버퍼를 비운다.

정의 위치:

- [DeviceSimulatorController.clearPublishedMessages()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/web/DeviceSimulatorController.java:93)

요청 파라미터:

- 없음

응답:

- `ApiResponse<Void>` 성공 응답

실제 동작 로직:

- [DeviceSimulatorControlService.clearPublishedMessages()](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:115)가 in-memory buffer를 clear 한다.

## API 사용 추천 순서
브로커 없이 simulator만 검증할 때는 보통 아래 순서가 가장 이해하기 쉽다.

1. `GET /sim/v1/status`
2. `GET /sim/v1/preview?count=3`
3. `POST /sim/v1/start?...`
4. `GET /sim/v1/metrics`
5. `GET /sim/v1/published-messages?limit=20`
6. `POST /sim/v1/scenario/LATE_EVENT?percent=40`
7. `GET /sim/v1/preview?count=3`
8. `POST /sim/v1/stop`

## 자주 헷갈리는 점

- `start`의 `devices`는 한 cycle에서 생성할 device 수다.
- `preview`의 `count`는 sample device 수다. runtime state를 바꾸지 않는다.
- `scenarioPercent`는 전체 이벤트 비율이 아니라 현재 구현상 `DrivingEvent` 생성 threshold에 직접 반영된다.
- `published-messages`는 메모리 버퍼 조회 API다. broker에 실제 전달된 메시지 이력을 조회하는 API가 아니다.
- `metrics`의 MQTT publisher 정보는 publisher mode가 `MEMORY`여도 구조상 항상 응답에 포함될 수 있다.
