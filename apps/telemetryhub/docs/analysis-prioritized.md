# TelemetryHub 우선순위 이슈

> 업데이트: 2026-04-24  
> 범위: `apps/telemetryhub`  
> 기준: 현재 구현을 `analysis-report.md`와 대조한 리뷰 결과

## 요약

이 문서는 다음 두 조건을 모두 만족하는 이슈만 남긴다.

- 현재 코드베이스에서 실제로 확인되는 문제
- 정합성, scale-out, 운영 안정성에 영향을 줄 만큼 우선순위가 높은 문제

캐시 튜닝, 일반적인 최적화, 추측성 미래 리스크 같은 항목은 의도적으로 제외했다.

## P0

### [x] 1. MQTT subscriber를 안전하게 scale-out 할 수 없음

**파일**

- [MqttInboundSubscriberLifecycle.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/MqttInboundSubscriberLifecycle.java:66)
- [IngestionMqttSubscriberProperties.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/config/IngestionMqttSubscriberProperties.java:14)

**문제**

- 모든 인스턴스가 같은 고정 `clientId`를 사용한다.
- 모든 인스턴스가 shared subscription이 아닌 일반 subscription을 사용한다.

**영향**

- 여러 ingestion 인스턴스가 서로의 연결을 끊을 수 있다.
- 연결이 유지되더라도 같은 MQTT 메시지를 여러 인스턴스가 소비하고 Kafka에 중복 발행할 수 있다.

**개선 방향**

- 인스턴스별로 고유한 `clientId`를 사용하게 한다.
- 실제 scale-out을 위해 shared subscription 전략을 도입한다.

### [x] 2. Backfill이 더 최신의 realtime device state를 덮어쓸 수 있음

**파일**

- [DeviceLastSeenBackfillWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/DeviceLastSeenBackfillWriter.java:47)
- [JpaDeviceLastSeenProjectionWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/infrastructure/JpaDeviceLastSeenProjectionWriter.java:27)

**문제**

- Backfill은 기존 row가 더 최신인지 확인하지 않고 `device_last_seen`에 쓴다.
- Realtime writer도 conflict key만 기준으로 갱신하고, 최신 이벤트 보호 조건이 없다.

**영향**

- 오래된 backfill 데이터가 더 최신의 realtime 상태를 대체할 수 있다.
- replay나 reprocessing 중에 `device_last_seen` 값이 틀어질 수 있다.

**개선 방향**

- `WHERE excluded.last_event_time > current.last_event_time` 형태의 보호 조건을 추가한다.
- realtime write와 backfill write 사이의 우선순위를 명시적으로 정의한다.

### [x] 3. Backfill에서 `last_ingest_time`이 잘못 기록됨

**파일**

- [DeviceLastSeenBackfillWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/DeviceLastSeenBackfillWriter.java:60)

**문제**

- `last_ingest_time`에 `eventTime`이 채워진다.

**영향**

- ingest latency가 의미를 잃는다.
- replay된 데이터가 잘못된 시점 메타데이터와 함께 저장된다.

**개선 방향**

- backfill raw model에 `ingestTime`을 추가한다.
- 단순히 event time을 복사하지 말고, 실제 ingest timestamp 의미에 맞게 기록한다.

### [x] 4. Kafka publish 결과 집계가 thread-safe하지 않고 ack 기반도 아님

**파일**

- [KafkaIngestionPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/KafkaIngestionPublisher.java:38)

**문제**

- snapshot 갱신이 `get` 다음 `set` 방식이라서 동시 publish 시 메트릭 갱신이 유실될 수 있다.
- broker ack 전에, `send()` 직후 즉시 success를 카운트한다.

**영향**

- 부하 상황에서 publish 메트릭이 실제와 다를 수 있다.
- async send 실패가 정확히 반영되지 않는다.

**개선 방향**

- snapshot 갱신을 atomic update 함수로 바꾼다.
- success/failure 집계를 Kafka send callback 안으로 옮긴다.

## P1

### [x] 5. MQTT callback thread가 ingestion 작업을 직접 수행함

**파일**

- [MqttInboundSubscriberLifecycle.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/MqttInboundSubscriberLifecycle.java:157)

**문제**

- `messageArrived()`가 애플리케이션 처리를 직접 호출한다.

**영향**

- downstream 처리가 느리면 MQTT 메시지 처리가 막힐 수 있다.
- broker 부하 상황에서 중복 전달과 latency 리스크가 커진다.

**개선 방향**

- 내부 queue 또는 worker pool로 callback 수신과 실제 처리를 분리한다.

### [x] 6. Kafka producer 신뢰성 설정이 아직 기본값에 의존함

**파일**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/resources/application.yml:4)

**문제**

- `acks`, idempotence, retries, compression, batching에 대한 명시적 producer 설정이 없다.

**영향**

- 신뢰성과 throughput 동작을 직접 통제할 수 없다.
- 운영 환경 동작이 프레임워크 기본값에 지나치게 의존하게 된다.

**개선 방향**

- 이 파이프라인에 맞는 producer 정책을 명시적으로 정의한다.

### [x] 7. In-memory debug buffer가 burst load에서 비효율적으로 동작할 수 있음

**파일**

- [InMemoryPublishedMessageStore.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/InMemoryPublishedMessageStore.java:24)
- [InMemoryNormalizedRawEventStore.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/InMemoryNormalizedRawEventStore.java:24)

**문제**

- trim 로직이 `ConcurrentLinkedDeque.size()` 기반이다.
- append와 trim이 capacity 제한 하에서 atomic하게 보장되지 않는다.

**영향**

- 불필요한 CPU 오버헤드가 생긴다.
- burst traffic에서 일시적으로 크기가 과도하게 늘어날 수 있다.

**개선 방향**

- bounded structure를 사용하거나 더 엄격한 append/trim 전략으로 바꾼다.

### [x] 8. Simulator control과 loop lifecycle에 race window가 있음

**파일**

- [DeviceSimulatorControlService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:32)
- [DeviceSimulatorLoopService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:30)

**문제**

- runtime state 변경과 loop start/stop이 동기화되어 있지 않다.
- 여러 start/refresh/stop 호출이 동시에 들어오면 서로 끼어들 수 있다.

**영향**

- 중복 loop 실행이나 오래된 runtime 설정 사용이 발생할 수 있다.

**개선 방향**

- lifecycle mutation을 직렬화한다.

## P2

### [x] 9. Stream processor state durability가 운영 준비 수준이 아님

**파일**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/resources/application.yml:28)

**문제**

- state dir가 임시 저장소를 사용한다.
- standby replica 수가 `0`이다.

**영향**

- restart 또는 reschedule 이후 restore 비용이 커진다.
- failover 복구 시간이 필요 이상으로 길어진다.

**개선 방향**

- container/runtime에서 state dir를 persistent volume으로 옮긴다.
- 운영 환경에서는 standby replica 수를 늘린다.

### [x] 10. File backfill reader가 매칭된 데이터를 전부 메모리에 올림

**파일**

- [FileBackfillSourceReader.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/FileBackfillSourceReader.java:36)

**문제**

- NDJSON source는 stream 방식으로 읽지만, 이후 전체를 in-memory list로 물질화한다.

**영향**

- 큰 replay 파일이 메모리 압박이나 OOM을 유발할 수 있다.

**개선 방향**

- chunk 기반 batch reading으로 전환한다.

### [x] 11. Event dedup이 구현되어 있지 않음

**파일**

- [EventsPerMinuteTopology.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/infrastructure/EventsPerMinuteTopology.java:31)
- [analysis-report.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/analysis-report.md:1)

**문제**

- 같은 `eventId`가 반복 전달되면 반복해서 집계된다.

**영향**

- MQTT/Kafka 중복 전달이 aggregate를 왜곡할 수 있다.

**개선 방향**

- dedup 경계를 정한다: ingestion에서 할지 streams에서 할지 선택한다.

### [x] 12. Late-event policy가 문서화되어 있지만 실제로 구현되어 있지 않음

**파일**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/resources/application.yml:24)
- [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md:1)

**문제**

- `late-event-grace` 속성은 있지만, 현재 topology는 대부분 진짜 event-time window grace 처리 대신 수동 bucketting에 의존한다.

**영향**

- 지연 이벤트가 명확하고 강제된 정책 아래에서 처리되지 않는다.

**개선 방향**

- grace-aware event-time aggregation을 실제로 구현하거나, 지원 전까지는 오해를 부르는 설정을 제거한다.

## 권장 수정 순서

1. MQTT subscriber의 scale-out semantics를 먼저 수정한다.
2. `device_last_seen`의 realtime/backfill 정합성을 수정한다.
3. Kafka publisher 집계와 callback 기반 결과 처리를 수정한다.
4. MQTT callback thread와 downstream 처리를 분리한다.
5. In-memory buffer와 simulator lifecycle race를 보강한다.
6. Stream state durability와 backfill 메모리 사용 방식을 개선한다.
7. Dedup과 실제 late-event 처리를 추가한다.

## 진행 메모

지난 패스에서 완료된 항목:

- P0.1 고유 MQTT client id 및 shared subscription 지원
- P0.2 `device_last_seen`용 latest-write guard
- P0.3 backfill `device_last_seen`의 올바른 `ingestTime` 처리
- P0.4 ack 기반 및 atomic Kafka publish snapshot 갱신
- P1.6 명시적 Kafka producer 신뢰성 설정
- P1.5 inbound MQTT queue와 worker 분리
- P1.7 bounded in-memory debug buffer
- P1.8 synchronized simulator lifecycle mutation
- P2.9 Docker용 persistent stream state dir 및 standby replica runtime 기본값
- P2.10 전체 in-memory 물질화 없는 chunked backfill source 처리
- P2.11 count 중심 stream topology에 대한 eventId 기반 dedup
- P2.12 `eventTime`과 `ingestTime` 기반의 명시적 late-event grace filtering
