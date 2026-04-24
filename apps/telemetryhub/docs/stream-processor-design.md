# TelemetryHub Stream Processor Design

## 목적
`stream-processor`는 Kafka raw topic에서 이벤트를 읽어 실시간 집계를 만들고, 그 결과를 state store와 read model 테이블에 반영하는 서비스다.

## 현재 패키지 구조
```text
streamprocessor
├─ application
│  ├─ metrics
│  ├─ plan
│  └─ projection
├─ config
├─ domain
│  ├─ entity
│  └─ repository
├─ infrastructure
│  ├─ projection
│  ├─ serde
│  └─ topology
└─ web
   └─ dto
```

## 패키지별 역할
| 패키지 | 역할 |
| --- | --- |
| `application.metrics` | projection write 메트릭 수집 |
| `application.plan` | topology/aggregation 계획 정의 |
| `application.projection` | topology와 DB write 사이의 포트 |
| `config` | Kafka Streams, state dir, threads 설정 |
| `domain` | raw event, aggregate, key, entity, repository |
| `infrastructure.projection` | 실제 DB upsert writer 구현 |
| `infrastructure.serde` | Kafka Streams JSON serde |
| `infrastructure.topology` | 실제 Kafka Streams topology 구현 |
| `web` | stream processor 관측 API |
| `web.dto` | metrics 응답 DTO |

## 패키지별 클래스 표

### `application.metrics`
| 클래스 | 역할 |
| --- | --- |
| `ProjectionType` | projection 종류를 구분하는 enum이다. |
| `StreamProcessorMetricsCollector` | projection write 성공/실패 메트릭을 수집한다. |
| `StreamProcessorMetricsSnapshot` | 외부 노출용 메트릭 스냅샷이다. |

### `application.plan`
| 클래스 | 역할 |
| --- | --- |
| `AggregationPlan` | 개별 집계 계획을 표현한다. |
| `DefaultStreamTopologyPlanner` | 기본 집계 계획을 조합한다. |
| `StreamTopologyPlan` | 전체 topology 계획 묶음이다. |
| `StreamTopologyPlanner` | topology 계획 생성 포트다. |

### `application.projection`
| 클래스 | 역할 |
| --- | --- |
| `DeviceLastSeenProjectionWriter` | device last seen read model 기록 포트 |
| `DrivingEventCounterProjectionWriter` | driving event counter 기록 포트 |
| `EventsPerMinuteProjectionWriter` | events per minute 기록 포트 |
| `RegionHeatmapProjectionWriter` | region heatmap 기록 포트 |

### `config`
| 클래스 | 역할 |
| --- | --- |
| `KafkaStreamsConfig` | Kafka Streams bean과 공통 설정을 등록한다. |
| `StreamProcessorProperties` | topic, state dir, thread 수, late-event grace 등을 담는다. |

### `domain`
| 클래스 | 역할 |
| --- | --- |
| `RawEventMessage` 등 raw-event 모델 | Kafka raw topic의 입력 모델이다. |
| `DeviceLastSeenAggregate` 등 aggregate 모델 | topology가 만드는 집계 모델이다. |
| `EventsPerMinuteKey` 등 key 모델 | 집계 key를 표현한다. |
| `...Entity` | read model 테이블 엔티티다. |
| `...Repository` | read model repository다. |

### `infrastructure.projection`
| 클래스 | 역할 |
| --- | --- |
| `DrivingEventCounterProjection` | driving event payload를 집계에 맞게 투영한다. |
| `JpaDeviceLastSeenProjectionWriter` | device last seen upsert를 수행한다. |
| `JpaDrivingEventCounterProjectionWriter` | driving event counter upsert를 수행한다. |
| `JpaEventsPerMinuteProjectionWriter` | events per minute upsert를 수행한다. |
| `JpaRegionHeatmapProjectionWriter` | region heatmap upsert를 수행한다. |
| `RegionHeatmapProjection` | telemetry payload를 heatmap 좌표로 투영한다. |

### `infrastructure.serde`
| 클래스 | 역할 |
| --- | --- |
| `JsonPojoDeserializer` | JSON을 POJO로 역직렬화한다. |
| `JsonPojoSerializer` | POJO를 JSON으로 직렬화한다. |
| `JsonSerdeFactory` | topology에서 쓸 serde를 생성한다. |

### `infrastructure.topology`
| 클래스 | 역할 |
| --- | --- |
| `DeviceLastSeenTopology` | 디바이스 최신 이벤트 상태를 유지한다. |
| `DrivingEventCounterTopology` | 디바이스/이벤트 종류/분 버킷 기준 카운트를 올린다. |
| `EventsPerMinuteTopology` | 이벤트 타입별 분 단위 처리량을 집계한다. |
| `LateEventPolicySupport` | grace 기준으로 너무 늦은 이벤트를 걸러낸다. |
| `RawEventDeduplicationSupport` | `eventId` 기준 중복 처리를 막는다. |
| `RegionHeatmapTopology` | telemetry 좌표를 grid로 내려 지역별 카운트를 만든다. |

### `web`
| 클래스 | 역할 |
| --- | --- |
| `StreamProcessorController` | projection write 메트릭 조회 API를 제공한다. |

### `web.dto`
| 클래스 | 역할 |
| --- | --- |
| `StreamProcessorMetricsResponse` | 메트릭 응답 DTO |

## 현재 구현 범위
- `device_last_seen`
- `events_per_minute`
- `driving_event_counter`
- `region_heatmap`
- projection write metrics
- `/stream/v1/metrics`

## 구조 변경 이유
topology, serde, projection writer, 메트릭 코드가 한 레벨에 섞여 있으면 stream 처리 구조를 읽기 어렵다. 지금은 stream 계산, 직렬화, DB 반영, 메트릭 수집이 분리돼서 책임이 더 명확하다.
