# TelemetryHub Priority Issues

> Updated: 2026-04-24  
> Scope: `apps/telemetryhub`  
> Basis: current implementation review against `analysis-report.md`

## Summary

This document keeps only the issues that are both:

- grounded in the current codebase
- high enough priority to affect correctness, scale-out, or operational safety

Items such as cache tuning, generalized optimization, and speculative future risks are intentionally excluded.

## P0

### [x] 1. MQTT subscriber cannot scale out safely

**Files**

- [MqttInboundSubscriberLifecycle.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/MqttInboundSubscriberLifecycle.java:66)
- [IngestionMqttSubscriberProperties.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/config/IngestionMqttSubscriberProperties.java:14)

**Problem**

- All instances use the same fixed `clientId`.
- All instances use normal subscriptions, not shared subscriptions.

**Impact**

- Multiple ingestion instances can disconnect each other.
- Even if they stay connected, the same MQTT message can be consumed by multiple instances and published to Kafka more than once.

**Direction**

- make `clientId` unique per instance
- introduce shared subscription strategy for real scale-out

### [x] 2. Backfill can overwrite fresher realtime device state

**Files**

- [DeviceLastSeenBackfillWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/DeviceLastSeenBackfillWriter.java:47)
- [JpaDeviceLastSeenProjectionWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/infrastructure/JpaDeviceLastSeenProjectionWriter.java:27)

**Problem**

- Backfill writes `device_last_seen` without checking whether the existing row is newer.
- Realtime writer also updates by conflict key only, without a latest-event guard.

**Impact**

- Old backfill data can replace newer realtime state.
- `device_last_seen` may become incorrect during replay or reprocessing.

**Direction**

- add `WHERE excluded.last_event_time > current.last_event_time` style guard
- define explicit precedence between realtime and backfill writes

### [x] 3. `last_ingest_time` is written incorrectly in backfill

**File**

- [DeviceLastSeenBackfillWriter.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/DeviceLastSeenBackfillWriter.java:60)

**Problem**

- `last_ingest_time` is populated with `eventTime`.

**Impact**

- ingest latency becomes meaningless
- replayed data is stored with incorrect timing metadata

**Direction**

- add `ingestTime` to backfill raw model
- write real ingest timestamp semantics, not duplicated event time

### [x] 4. Kafka publish result accounting is not thread-safe and not ack-based

**File**

- [KafkaIngestionPublisher.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/KafkaIngestionPublisher.java:38)

**Problem**

- snapshot updates use `get` then `set`, so concurrent publishes can lose metric updates
- success is counted immediately after `send()`, before broker ack

**Impact**

- publish metrics can lie under load
- async send failures are not reflected correctly

**Direction**

- switch snapshot updates to atomic update functions
- move success/failure accounting into Kafka send callback

## P1

### [x] 5. MQTT callback thread performs ingestion work directly

**File**

- [MqttInboundSubscriberLifecycle.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/MqttInboundSubscriberLifecycle.java:157)

**Problem**

- `messageArrived()` directly invokes application processing.

**Impact**

- slow downstream processing can block MQTT message handling
- under broker pressure this increases duplicate delivery and latency risk

**Direction**

- separate callback reception from processing with an internal queue or worker pool

### [x] 6. Kafka producer reliability settings are still default

**File**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/resources/application.yml:4)

**Problem**

- no explicit producer settings for `acks`, idempotence, retries, compression, or batching

**Impact**

- reliability and throughput behavior are not controlled
- production behavior will depend too much on framework defaults

**Direction**

- define explicit producer policy for this pipeline

### [x] 7. In-memory debug buffers can behave poorly under burst load

**Files**

- [InMemoryPublishedMessageStore.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/infrastructure/InMemoryPublishedMessageStore.java:24)
- [InMemoryNormalizedRawEventStore.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/ingestion-service/src/main/java/com/booster/telemetryhub/ingestion/infrastructure/InMemoryNormalizedRawEventStore.java:24)

**Problem**

- trimming is based on `ConcurrentLinkedDeque.size()`
- append and trim are not capacity-enforced atomically

**Impact**

- avoidable CPU overhead
- temporary overgrowth under burst traffic

**Direction**

- use a bounded structure or a stricter append/trim strategy

### [x] 8. Simulator control and loop lifecycle have race windows

**Files**

- [DeviceSimulatorControlService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorControlService.java:32)
- [DeviceSimulatorLoopService.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/device-simulator/src/main/java/com/booster/telemetryhub/devicesimulator/application/DeviceSimulatorLoopService.java:30)

**Problem**

- runtime state mutation and loop start/stop are not synchronized
- repeated concurrent start/refresh/stop calls can interleave

**Impact**

- duplicate loops or stale runtime configuration are possible

**Direction**

- serialize lifecycle mutations

## P2

### [x] 9. Stream processor state durability is not production-ready

**File**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/resources/application.yml:28)

**Problem**

- state dir uses temp storage
- standby replicas are `0`

**Impact**

- restore cost increases after restart or reschedule
- failover recovery will be slower than necessary

**Direction**

- move state dir to persistent volume in container/runtime
- raise standby replica count for production

### [x] 10. File backfill reader loads all matching data into memory

**File**

- [FileBackfillSourceReader.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/batch-backfill/src/main/java/com/booster/telemetryhub/batchbackfill/infrastructure/FileBackfillSourceReader.java:36)

**Problem**

- NDJSON source is streamed, but then materialized into a full in-memory list

**Impact**

- large replay files can cause memory pressure or OOM

**Direction**

- switch to chunk-based batch reading

### [x] 11. Event dedup is not implemented

**Files**

- [EventsPerMinuteTopology.java](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/java/com/booster/telemetryhub/streamprocessor/infrastructure/EventsPerMinuteTopology.java:31)
- [analysis-report.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/analysis-report.md:1)

**Problem**

- repeated delivery of the same `eventId` is counted repeatedly

**Impact**

- duplicate MQTT/Kafka delivery can skew aggregates

**Direction**

- choose a dedup boundary: ingestion or streams

### [x] 12. Late-event policy is documented but not actually implemented

**Files**

- [application.yml](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/stream-processor/src/main/resources/application.yml:24)
- [stream-processor-design.md](/abs/path/C:/Users/NCand/Documents/bootser/apps/telemetryhub/docs/stream-processor-design.md:1)

**Problem**

- there is a `late-event-grace` property, but current topologies mostly use manual bucketing rather than true event-time window grace handling

**Impact**

- delayed events are not governed by a clear, enforced policy

**Direction**

- either implement true grace-aware event-time aggregation or remove misleading config until supported

## Recommended Fix Order

1. Fix MQTT subscriber scale-out semantics.
2. Fix `device_last_seen` correctness for realtime vs backfill.
3. Fix Kafka publisher accounting and callback-based result handling.
4. Decouple MQTT callback thread from downstream processing.
5. Harden in-memory buffers and simulator lifecycle races.
6. Improve stream state durability and backfill memory behavior.
7. Add dedup and real late-event handling.

## Progress Note

Completed in the last pass:

- P0.1 unique MQTT client id and shared subscription support
- P0.2 latest-write guard for `device_last_seen`
- P0.3 correct `ingestTime` handling for backfill `device_last_seen`
- P0.4 ack-based and atomic Kafka publish snapshot updates
- P1.6 explicit Kafka producer reliability settings
- P1.5 inbound MQTT queue and worker decoupling
- P1.7 bounded in-memory debug buffers
- P1.8 synchronized simulator lifecycle mutations
- P2.9 persistent stream state dir and standby replica runtime defaults for Docker
- P2.10 chunked backfill source processing without full in-memory materialization
- P2.11 eventId-based dedup for count-oriented stream topologies
- P2.12 explicit late-event grace filtering based on `eventTime` and `ingestTime`
