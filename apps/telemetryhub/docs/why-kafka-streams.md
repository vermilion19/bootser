# Stream Processor에서 Kafka Streams를 선택한 이유

> Planning.md의 설계 의도와 실제 구현 코드를 기반으로 분석

---

## TL;DR

Planning.md에서 정의한 4가지 요구사항(신뢰성, 확장성, 재현성, 관측 가능성) 중  
**신뢰성(중복/순서/지연)** 과 **재현성(재처리)** 을 해결하는 데  
Kafka Streams의 **내장 상태 저장소(in-process RocksDB)** 가 핵심이었다.

---

## 1. 단순 Kafka Consumer로 해결 안 되는 문제들

Planning.md 섹션 1에서 커넥티드 디바이스 데이터의 특성을 이렇게 정의했다.

```
- late arrival  (늦은 도착)
- duplicate     (중복)
- out-of-order  (순서 뒤섞임)
```

단순 `@KafkaListener` Consumer는 메시지를 받아서 처리하는 것만 한다.  
위 세 문제를 해결하려면 **"현재까지 뭘 봤는지"** 를 어딘가에 기억해야 한다.

Kafka Streams는 그 상태를 **in-process 상태 저장소(RocksDB)** 로 내장한다.  
단순 Consumer는 이를 외부 Redis 또는 DB 조회로 대체해야 한다.

---

## 2. 상태 저장소를 실제로 활용하는 코드

### 2-1. Deduplication — `RawEventDeduplicationSupport.java`

```java
// eventId를 키로 RocksDB 상태 저장소에 기록
if (store.get(value.eventId()) != null) {
    context.forward(record.withValue(null)); // 이미 본 이벤트 → null 처리
    return;
}
store.put(value.eventId(), seenAt); // 처음 본 이벤트 → 기록 후 통과
```

만약 단순 Consumer였다면 이 `store`가 Redis 또는 PostgreSQL 조회로 대체된다.  
메시지 1건마다 외부 네트워크 I/O가 발생한다.  
Kafka Streams는 이를 **로컬 RocksDB 조회(마이크로초 단위)** 로 해결한다.

### 2-2. Late Event 필터링 — `LateEventPolicySupport.java`

```java
// ingestTime - eventTime > grace period → drop
Duration delay = Duration.between(event.eventTime(), event.ingestTime());
return delay.compareTo(lateEventGrace) <= 0;
```

단순 Consumer에서도 구현 가능한 로직이지만,  
집계 토폴로지에 **선언적으로 끼워넣을 수 있다**는 것이 Kafka Streams의 강점이다.  
`filter → selectKey → mapValues → groupByKey → reduce → foreach` 체인으로 파이프라인 전체가 하나의 흐름으로 읽힌다.

### 2-3. 집계 상태 유지 — `DeviceLastSeenTopology.java`

```java
.groupByKey(...)
.reduce(
    DeviceLastSeenAggregate::merge,           // 최신 상태로 병합
    Materialized.as(plan.stateStoreName())    // 상태 저장소에 영속
)
.toStream()
.foreach((deviceId, aggregate) -> projectionWriter.upsert(aggregate));
```

`reduce`가 실행될 때마다 상태 저장소에서 이전 상태를 읽고 `merge`를 적용한다.  
단순 Consumer는 `aggregate`의 현재 값을 매번 DB에서 읽어야 한다.

---

## 3. 재처리(Backfill)와의 분리 구조

Planning.md 섹션 9.2에서 이렇게 정의했다.

```
grace period 밖은 배치/보정(backfill)으로 처리
```

스트리밍과 배치를 명시적으로 분리한 이유는 Kafka Streams가 **offset 기반** 으로 동작하기 때문이다.

```
Kafka Streams 재처리 흐름:
  auto.offset.reset = earliest
      ↓
  과거 raw-events 재소비 (retention 기간 내)
      ↓
  상태 저장소 재구성
      ↓
  DB upsert (최신성 WHERE 조건으로 충돌 방지)
```

단순 Consumer는 offset 재설정만으로는 집계 상태가 없어서 재처리 결과가 부정확하다.  
Kafka Streams는 상태 저장소를 함께 재구성하므로 **동일 로직으로 실시간/재처리를 공유** 할 수 있다.

---

## 4. DB 쓰기 최적화 — `BufferedJdbcProjectionWriter.java`

Kafka Streams 기능이 아니라 별도로 구현한 부분이지만, 설계 분리 관점에서 주목할 만하다.

```java
// 건별 upsert가 아니라 버퍼에 누적 후 배치 flush
protected final void upsertBuffered(T aggregate) {
    synchronized (bufferMonitor) {
        bufferedAggregates.merge(bufferKey(aggregate), aggregate, this::mergeAggregates);
        if (bufferedAggregates.size() >= batchSize) {
            aggregatesToFlush = drainBufferLocked();
        }
    }
    // 배치 upsert
}
```

Kafka Streams의 `foreach`는 메시지 단위로 콜백을 부르지만,  
그 안에서 DB 쓰기를 배치로 묶는 것은 애플리케이션 레이어에서 분리해 구현했다.

- **Kafka Streams** → 스트림 파이프라인 (필터, 집계, 상태 관리)
- **BufferedJdbcProjectionWriter** → 쓰기 최적화 (버퍼링, 배치 flush, 실패 재큐)

---

## 5. 대안과의 트레이드오프

| 대안 | 선택하지 않은 이유 |
|------|--------------------|
| 단순 `@KafkaListener` | dedup을 위해 Redis/DB 외부 I/O 필요. 상태 복구(재처리) 직접 구현해야 함 |
| Spring WebFlux + Reactor Kafka | 상태 저장소 없음. 복잡한 상태 관리를 직접 구현해야 함 |
| Apache Flink | 별도 Flink 클러스터 필요. 운영 복잡도가 Spring 생태계와 맞지 않음 |
| Spark Streaming | 마이크로 배치 방식. 이벤트 단위 처리 어려움 |

---

## 6. 현재 구현의 미활용 지점 (냉정한 평가)

Kafka Streams를 선택했지만, 현재 코드에서 아직 최대로 활용하지 못하는 부분이 있다.

### 소스 스트림 4번 중복 생성

```java
// DeviceLastSeenTopology, EventsPerMinuteTopology,
// DrivingEventCounterTopology, RegionHeatmapTopology
// 각각이 동일한 소스 토픽을 독립적으로 구독
KStream<String, RawEventMessage> sourceStream = streamsBuilder.stream(
    sourceTopic,
    Consumed.with(...)
);
```

4개의 집계 토폴로지가 같은 토픽을 4번 읽는다.  
토폴로지 안에서 소스 스트림을 공유하면 Consumer 그룹이 1개로 줄어 Kafka 파티션 재분배 부하가 감소한다.

### `suppress` 미사용

```java
// 현재: reduce 결과가 이벤트마다 DB에 emit
.reduce(DeviceLastSeenAggregate::merge, ...)
.toStream()
.foreach(projectionWriter::upsert); // 이벤트마다 호출

// 개선 가능: suppress로 윈도우 닫힐 때만 emit
.suppress(Suppressed.untilWindowCloses(...))
.toStream()
.foreach(projectionWriter::upsert); // 윈도우당 1회
```

---

## 결론

Kafka Streams를 선택한 핵심 이유는  
**외부 의존성 없이 in-process로 상태를 관리할 수 있기 때문** 이다.

Planning.md에서 정의한 중복/지연/순서 문제를 Redis나 DB 없이 해결하고,  
offset 기반 재처리로 Backfill과 실시간 처리를 같은 로직으로 공유할 수 있다는 점이 결정적이었다.