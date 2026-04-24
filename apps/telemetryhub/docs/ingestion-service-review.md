# TelemetryHub Ingestion Service Review

## 목적
`ingestion-service`는 simulator 또는 실제 디바이스에서 들어온 메시지를 받아 공통 raw event 형태로 정규화하고, 이후 파이프라인이 소비할 수 있도록 publisher로 넘기는 서비스다.

## 현재 패키지 구조
```text
ingestion
├─ application
│  ├─ failure
│  ├─ ingest
│  ├─ metrics
│  ├─ mqtt
│  ├─ normalize
│  └─ publisher
├─ config
│  ├─ mqtt
│  ├─ publisher
│  └─ runtime
├─ infrastructure
│  ├─ mqtt
│  ├─ normalize
│  ├─ publisher
│  └─ store
└─ web
   └─ dto
```

## 패키지별 역할
| 패키지 | 역할 |
| --- | --- |
| `application.failure` | 실패 단계 구분 |
| `application.ingest` | 전체 수집 orchestration |
| `application.metrics` | 서비스 메트릭 스냅샷 관리 |
| `application.mqtt` | MQTT inbound adapter와 관련 메트릭 |
| `application.normalize` | 정규화된 raw event 모델과 포트 |
| `application.publisher` | publish 포트와 결과 모델 |
| `config.mqtt` | MQTT subscriber 설정 |
| `config.publisher` | Kafka/Memory/Logging publisher 설정 |
| `config.runtime` | 개발용 버퍼와 런타임 옵션 |
| `infrastructure.mqtt` | 실제 MQTT subscriber lifecycle 구현 |
| `infrastructure.normalize` | topic 해석, payload 파싱, Kafka key 생성 |
| `infrastructure.publisher` | 실제 publisher 구현 |
| `infrastructure.store` | 최근 normalized event 메모리 저장소 |
| `web` | 수집/관측 API 진입점 |
| `web.dto` | API 요청/응답 DTO |

## 패키지별 클래스 표

### `application.failure`
| 클래스 | 역할 |
| --- | --- |
| `IngestionFailureStage` | normalize/publish 등 실패 지점을 분류한다. |

### `application.ingest`
| 클래스 | 역할 |
| --- | --- |
| `IngestionMessage` | ingestion이 처리하는 입력 메시지 공통 모델이다. |
| `IngestionService` | 메시지 수신, normalize, publish, 실패 계수 업데이트를 orchestration한다. |

### `application.metrics`
| 클래스 | 역할 |
| --- | --- |
| `IngestionMetricsSnapshot` | 서비스 전체 처리량과 실패 수, 마지막 상태를 담는다. |

### `application.mqtt`
| 클래스 | 역할 |
| --- | --- |
| `MqttInboundAdapter` | MQTT subscriber가 받은 메시지를 ingestion service 경계로 전달한다. |
| `MqttInboundMetricsSnapshot` | MQTT inbound 경로 전용 메트릭을 담는다. |

### `application.normalize`
| 클래스 | 역할 |
| --- | --- |
| `NormalizedRawEvent` | 정규화 완료된 raw event 표현 모델이다. |
| `RawEventNormalizer` | 입력 메시지를 `NormalizedRawEvent`로 변환하는 포트다. |

### `application.publisher`
| 클래스 | 역할 |
| --- | --- |
| `IngestionPublishResult` | publish 결과를 나타내는 모델이다. |
| `IngestionPublisher` | downstream publish 포트다. |

### `config.mqtt`
| 클래스 | 역할 |
| --- | --- |
| `IngestionMqttSubscriberProperties` | broker URI, client id, shared subscription, queue 등 MQTT subscriber 설정을 담는다. |

### `config.publisher`
| 클래스 | 역할 |
| --- | --- |
| `IngestionPublisherProperties` | publisher mode, Kafka topic, key strategy 설정을 담는다. |

### `config.runtime`
| 클래스 | 역할 |
| --- | --- |
| `IngestionRuntimeProperties` | recent-event buffer 활성화 여부 등 런타임 보조 설정을 담는다. |

### `infrastructure.mqtt`
| 클래스 | 역할 |
| --- | --- |
| `MqttInboundSubscriberLifecycle` | MQTT broker subscribe, queue 적재, worker 처리까지 관리한다. |
| `MqttSubscriberSnapshot` | subscriber 상태, queue depth, dropped count 등을 보여준다. |
| `MqttSubscriberState` | MQTT subscriber 상태 enum이다. |

### `infrastructure.normalize`
| 클래스 | 역할 |
| --- | --- |
| `IngestionTopicResolver` | topic 기반으로 이벤트 타입을 판별한다. |
| `JsonRawEventNormalizer` | payload를 contracts 이벤트로 파싱해 normalized raw event를 만든다. |
| `KafkaEventKeyResolver` | Kafka partition key 전략에 따라 key를 계산한다. |

### `infrastructure.publisher`
| 클래스 | 역할 |
| --- | --- |
| `InMemoryIngestionPublisher` | normalized event를 메모리에 보관한다. |
| `KafkaIngestionPublisher` | normalized event를 Kafka raw topic으로 전송한다. |
| `KafkaPublishSnapshot` | Kafka publish 성공/실패 관측 정보를 담는다. |
| `LoggingIngestionPublisher` | publish 내용을 로그로 남긴다. |
| `RoutingIngestionPublisher` | 현재 mode에 따라 실제 publisher를 선택한다. |

### `infrastructure.store`
| 클래스 | 역할 |
| --- | --- |
| `InMemoryNormalizedRawEventStore` | 최근 normalized event를 bounded buffer로 유지한다. |

### `web`
| 클래스 | 역할 |
| --- | --- |
| `IngestionController` | HTTP 수집 API와 metrics/이벤트 조회 API를 제공한다. |

### `web.dto`
| 클래스 | 역할 |
| --- | --- |
| `IngestMessageRequest` | 일반 ingest API 요청 DTO |
| `IngestionMetricsResponse` | ingestion 메트릭 응답 DTO |
| `MqttInboundBatchRequest` | MQTT batch ingest 요청 DTO |
| `MqttInboundMessageRequest` | MQTT 단건 ingest 요청 DTO |
| `MqttInboundMetricsResponse` | MQTT inbound 메트릭 응답 DTO |
| `MqttSubscriberResponse` | subscriber 상태 응답 DTO |
| `NormalizedRawEventResponse` | 최근 normalized event 조회 응답 DTO |

## 전체 흐름
```text
HTTP or MQTT
  -> IngestionController
  -> IngestionService
  -> JsonRawEventNormalizer
  -> RoutingIngestionPublisher
     -> LOGGING / MEMORY / KAFKA
```

## 주요 API
- `POST /ingestion/v1/messages`
- `POST /ingestion/v1/mqtt/messages`
- `POST /ingestion/v1/mqtt/messages/batch`
- `GET /ingestion/v1/metrics`
- `GET /ingestion/v1/mqtt/metrics`
- `GET /ingestion/v1/mqtt/subscriber`
- `GET /ingestion/v1/events`
- `POST /ingestion/v1/events/clear`

## 구조 변경 이유
정규화, MQTT 경계, publisher, 메모리 저장소가 한곳에 섞여 있으면 수집 흐름을 읽기 어렵다. 지금 구조는 입력 경계와 내부 처리 경계를 분리해서 다음이 쉬워졌다.

- MQTT 입력 경로와 일반 HTTP 입력 경로 구분
- normalize 로직과 publish 로직 구분
- Kafka publisher와 개발용 publisher 구분
- 관측용 메트릭과 실제 처리 로직 구분
