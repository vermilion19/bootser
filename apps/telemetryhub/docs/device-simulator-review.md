# TelemetryHub Device Simulator Review

## 목적
`device-simulator`는 차량/IoT 디바이스가 보내는 것과 유사한 텔레메트리 이벤트를 생성하고, 제어 API를 통해 실행 상태를 조작하며, 여러 publisher 모드로 전송 경계를 검증하기 위한 모듈이다.

## 현재 패키지 구조
```text
devicesimulator
├─ application
│  ├─ control
│  ├─ preview
│  ├─ publisher
│  └─ runtime
├─ config
│  └─ mqtt
├─ domain
├─ infrastructure
│  ├─ message
│  ├─ mqtt
│  ├─ publisher
│  └─ store
└─ web
   └─ dto
```

## 패키지별 역할
| 패키지 | 역할 |
| --- | --- |
| `application.control` | 시뮬레이터 상태 전이와 제어 명령 처리 |
| `application.preview` | 샘플 이벤트 생성과 미리보기 로직 |
| `application.publisher` | 루프가 publisher에 넘기는 공통 배치/포트 정의 |
| `application.runtime` | 실제 실행 루프, 런타임 상태, 메트릭 관리 |
| `config` | publisher 모드, runtime 옵션, MQTT 옵션 설정 |
| `config.mqtt` | real/stub MQTT connection manager bean 선택 |
| `domain` | 시뮬레이터 상태와 시나리오 enum |
| `infrastructure.message` | topic 계산, payload 직렬화, outbound message 구성 |
| `infrastructure.mqtt` | MQTT 전송 전용 publisher와 connection manager 구현 |
| `infrastructure.publisher` | `LOGGING`, `MEMORY`, `BRIDGE`, `MQTT` publisher 구현 |
| `infrastructure.store` | 개발/디버깅용 인메모리 발행 메시지 저장소 |
| `web` | 제어 및 관측 API 진입점 |
| `web.dto` | API 요청/응답 DTO |

## 패키지별 클래스 표

### `application.control`
| 클래스 | 역할 |
| --- | --- |
| `DeviceSimulatorControlService` | `start`, `stop`, `scale`, `applyScenario`를 처리하고 runtime state와 loop를 함께 제어한다. |

### `application.preview`
| 클래스 | 역할 |
| --- | --- |
| `DeviceEventPreviewService` | 현재 시나리오와 디바이스 수를 기준으로 `TelemetryEvent`, `DeviceHealthEvent`, `DrivingEvent` 샘플을 만든다. |

### `application.publisher`
| 클래스 | 역할 |
| --- | --- |
| `SimulationEventBatch` | 한 번의 publish cycle에서 생성된 이벤트 묶음을 표현한다. |
| `SimulationEventPublisher` | 실제 전송 구현체가 따라야 하는 publisher 포트다. |

### `application.runtime`
| 클래스 | 역할 |
| --- | --- |
| `DeviceSimulatorLoopService` | 주기적으로 이벤트를 생성하고 publisher에 넘기는 실행 루프를 관리한다. |
| `SimulatorBatchSummary` | 마지막 배치의 이벤트 개수와 요약 정보를 담는다. |
| `SimulatorMetricsSnapshot` | 누적 생성량과 마지막 배치 정보를 담는 메트릭 스냅샷이다. |
| `SimulatorRuntimeState` | 현재 실행 여부, 디바이스 수, 시나리오 같은 런타임 상태를 표현한다. |

### `config`
| 클래스 | 역할 |
| --- | --- |
| `SimulatorPublisherProperties` | publisher mode, bridge, MQTT 관련 설정을 담는다. |
| `SimulatorRuntimeProperties` | 인메모리 버퍼 사용 여부 같은 런타임 보조 설정을 담는다. |

### `config.mqtt`
| 클래스 | 역할 |
| --- | --- |
| `MqttConnectionManagerConfig` | `real-client-enabled` 설정에 따라 real/stub connection manager를 등록한다. |

### `domain`
| 클래스 | 역할 |
| --- | --- |
| `SimulationScenario` | `NORMAL`, `LATE_EVENT`, `DUPLICATE`, `RECONNECT_STORM` 같은 시뮬레이션 시나리오를 정의한다. |
| `SimulatorStatus` | `RUNNING`, `STOPPED` 등 시뮬레이터 상태를 표현한다. |

### `infrastructure.message`
| 클래스 | 역할 |
| --- | --- |
| `MessageEnvelope` | topic, key, payload를 함께 담는 outbound message 모델이다. |
| `SimulationEventSerializer` | contracts 이벤트를 JSON payload로 직렬화한다. |
| `TelemetryTopicResolver` | 이벤트 종류에 따라 MQTT topic 규칙을 계산한다. |

### `infrastructure.mqtt`
| 클래스 | 역할 |
| --- | --- |
| `MqttConnectionManager` | connect/publish/disconnect를 추상화한 MQTT 연결 포트다. |
| `MqttConnectionState` | MQTT publisher 연결 상태 enum이다. |
| `MqttPublisherSnapshot` | MQTT publisher 관측 정보 스냅샷이다. |
| `MqttRetryPolicySnapshot` | 재시도 정책 정보를 외부로 보여주기 위한 모델이다. |
| `MqttSimulationEventPublisher` | 배치를 MQTT message로 변환해 connection manager로 publish한다. |
| `RealMqttConnectionManager` | 실제 Paho MQTT 클라이언트로 브로커에 연결한다. |
| `StubMqttConnectionManager` | 브로커 없이도 publish 흐름을 검증하기 위한 스텁 구현이다. |

### `infrastructure.publisher`
| 클래스 | 역할 |
| --- | --- |
| `BridgeSimulationEventPublisher` | MQTT broker 없이 ingestion의 MQTT inbound API로 직접 배치를 넘긴다. |
| `InMemorySimulationEventPublisher` | 발행 메시지를 인메모리 저장소에 쌓는다. |
| `LoggingSimulationEventPublisher` | 발행 메시지 요약을 로그로 남긴다. |
| `RoutingSimulationEventPublisher` | 설정된 mode에 따라 실제 publisher 구현체를 선택한다. |

### `infrastructure.store`
| 클래스 | 역할 |
| --- | --- |
| `InMemoryPublishedMessageStore` | 최근 발행 메시지를 bounded buffer로 유지한다. |
| `MemoryPublishedMessage` | 인메모리 저장소에 기록되는 메시지 표현 모델이다. |

### `web`
| 클래스 | 역할 |
| --- | --- |
| `DeviceSimulatorController` | 시뮬레이터 제어 및 관측 REST API를 제공한다. |

### `web.dto`
| 클래스 | 역할 |
| --- | --- |
| `PublishedMessageResponse` | 최근 발행 메시지 조회 응답 DTO |
| `SimulatorMetricsResponse` | 메트릭 조회 응답 DTO |
| `SimulatorPreviewResponse` | preview API 응답 DTO |
| `SimulatorStatusResponse` | 상태 조회 응답 DTO |

## 전체 흐름
```text
DeviceSimulatorController
  -> DeviceSimulatorControlService
     -> DeviceSimulatorLoopService
        -> DeviceEventPreviewService
        -> RoutingSimulationEventPublisher
           -> LOGGING / MEMORY / BRIDGE / MQTT
```

## 주요 API
- `POST /sim/v1/start`
- `POST /sim/v1/stop`
- `POST /sim/v1/scale`
- `POST /sim/v1/scenario/{scenario}`
- `GET /sim/v1/status`
- `GET /sim/v1/metrics`
- `GET /sim/v1/preview`
- `GET /sim/v1/published-messages`
- `POST /sim/v1/published-messages/clear`

## 구조 변경 이유
이전에는 `application`과 `infrastructure` 아래에 클래스가 같은 깊이로 많이 쌓여 있었다. 지금은 제어, 루프, 메시지 직렬화, MQTT 전송, 인메모리 저장소를 하위 패키지로 분리해서 다음이 쉬워졌다.

- 실행 제어 코드와 실제 publish 코드 구분
- MQTT 전용 코드와 공통 publisher 코드 구분
- 메시지 변환 계층과 전송 계층 구분
- 개발용 저장소와 운영용 전송 경계 구분
