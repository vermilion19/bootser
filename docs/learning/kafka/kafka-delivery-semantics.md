# Kafka 전달 보장 & Rebalance 심화

## 1. 전달 보장 (Delivery Semantics) 3단계

메시지가 "얼마나 확실하게" 전달되는지에 대한 보장 수준.

| 보장 수준 | 설명 | 특징 |
|-----------|------|------|
| **At-most-once** | 최대 1번 전달 | 유실 가능, 중복 없음 |
| **At-least-once** | 최소 1번 전달 | 중복 가능, 유실 없음 |
| **Exactly-once** | 정확히 1번 전달 | 중복 없음, 유실 없음 |

---

## 2. At-most-once (최대 1번)

**"빠르게 보내되 유실을 감수"**

```
Producer                 Broker
   │  send(msg)            │
   │──────────────────────►│ 저장 시도
   │                        │
   │                        X  장애 발생
   │ (ACK 없음)             │
   │  다음 메시지로 진행     │
   └────────────────────────┘
   → msg 유실 (재시도 안 함)
```

```yaml
# At-most-once 설정
spring.kafka.producer:
  acks: 0         # ACK 기다리지 않음
  retries: 0      # 재시도 없음
```

**사용 사례**: 로그 수집, IoT 센서 데이터 (일부 유실이 허용되고 처리량이 중요한 경우)

---

## 3. At-least-once (최소 1번)

**"유실은 안 되지만 중복은 감수"** → Kafka의 기본 동작 방식

```
Producer                 Broker
   │  send(msg)            │
   │──────────────────────►│ 저장 성공
   │                        │
   │         ACK            │
   │◄─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │  (네트워크 유실)
   │ ACK 못 받음            │
   │  재시도: send(msg)     │
   │──────────────────────►│ 이미 저장된 msg 중복 저장!
   │         ACK            │
   │◄──────────────────────│
   └────────────────────────┘
   → msg 중복 발행됨
```

```yaml
# At-least-once 설정
spring.kafka.producer:
  acks: all    # 모든 ISR 저장 확인
  retries: 3   # 실패 시 재시도
```

**Consumer 측 멱등성 처리 필수**:
```java
@KafkaListener(topics = "waiting-events")
public void handle(WaitingEvent event) {
    if (processedEvents.contains(event.getEventId())) return; // 중복 skip
    process(event);
    processedEvents.add(event.getEventId());
}
```

---

## 4. Exactly-once (정확히 1번)

### 4-1. Idempotent Producer (멱등성 프로듀서)

**같은 메시지를 여러 번 보내도 Broker에 1번만 저장**

```
Producer 시작 시 Broker로부터 PID(Producer ID) 발급
각 메시지에 (PID, 파티션, Sequence Number) 부여

Producer                              Broker
  │  send(PID=1, Seq=0, msg)           │
  │─────────────────────────────────► │ 저장 (Seq=0)
  │  ACK 유실                          │
  │  재시도: send(PID=1, Seq=0, msg)  │
  │─────────────────────────────────► │ Seq=0 이미 있음 → 중복 무시
  │  ACK                               │
  │◄─────────────────────────────────  │
  └─────────────────────────────────── ┘
```

```yaml
spring.kafka.producer:
  properties:
    enable.idempotence: true                        # PID + Sequence Number 활성화
    max.in.flight.requests.per.connection: 5        # 멱등성 시 최대 5
    acks: all                                       # 멱등성 시 자동으로 all
    retries: 2147483647                             # 멱등성 시 자동으로 최대값
```

> **제한**: 단일 파티션, 단일 Producer 세션 내에서만 보장. Producer가 재시작되면 PID가 바뀌어 보장 안 됨.

### 4-2. Transactional Producer (트랜잭션 프로듀서)

**여러 파티션/토픽에 걸친 원자적 발행** + Producer 재시작해도 보장

```java
// 설정
props.put("transactional.id", "waiting-service-tx-1"); // 고유 ID (재시작해도 유지)

// 사용
producer.initTransactions();

try {
    producer.beginTransaction();

    // 여러 토픽에 원자적으로 발행
    producer.send(new ProducerRecord<>("waiting-events", key, value1));
    producer.send(new ProducerRecord<>("audit-log", key, value2));

    producer.commitTransaction(); // 두 메시지 모두 원자적으로 커밋
} catch (Exception e) {
    producer.abortTransaction(); // 두 메시지 모두 롤백
}
```

```yaml
spring.kafka.producer:
  transaction-id-prefix: waiting-service-  # transactional.id 자동 생성
```

### 4-3. Exactly-once 전체 흐름 (EOS)

```
Producer (idempotent + transactional)
    │
    │ 원자적 발행
    ▼
Kafka Broker (Transaction Coordinator)
    │
    │ isolation.level=read_committed
    ▼
Consumer (커밋 완료된 메시지만 읽음)
```

```yaml
spring.kafka.consumer:
  properties:
    isolation.level: read_committed  # 트랜잭션 커밋된 메시지만 소비
```

> **실무 주의**: Exactly-once는 성능 비용이 있고 복잡성이 높다. 결제, 금융처럼 중복이 절대 안 되는 경우가 아니라면 At-least-once + Consumer 멱등성 처리가 더 현실적이다.

---

## 5. Rebalance 심화

### Rebalance란?

Consumer Group 내 파티션 할당이 **재조정**되는 과정.

**Rebalance 트리거 조건:**
- Consumer 추가 (스케일 아웃)
- Consumer 제거 (장애, 종료)
- `session.timeout.ms` 초과 (Heartbeat 끊김)
- `max.poll.interval.ms` 초과 (처리 지연)
- 토픽 파티션 수 변경

### 5-1. Eager Rebalance (Stop-The-World)

기존 방식. **모든 Consumer가 파티션을 반납 후 새로 할당.**

```
[Before]                [Rebalance 발생]            [After]
C1 → P0, P1             모든 Consumer            C1 → P0
C2 → P2, P3      →      파티션 반납       →      C2 → P1, P2
                         (일시 중단)              C3 → P3  (신규)

문제: P0, P1, P2, P3 모두 일시 중단
     Rebalance 시간 동안 전체 Consumer Group 처리 중단
```

**할당 전략 (Eager):**
- `RangeAssignor`: 토픽별로 파티션 범위를 순서대로 할당 (기본)
- `RoundRobinAssignor`: 전체 파티션을 Round-Robin으로 할당

### 5-2. Cooperative Incremental Rebalance

Kafka 2.4+. **영향받는 파티션만 재할당, 나머지는 중단 없이 계속 처리.**

```
[Before]                [1차 Rebalance]              [2차 Rebalance]
C1 → P0, P1             C1 → P0, P1         →        C1 → P0
C2 → P2, P3             C2 → P2, P3                  C2 → P2, P3
                         C3 → 없음 (참여)              C3 → P1  (P1만 이동)

핵심: P0, P2, P3는 중단 없이 계속 처리
     P1만 C1→C3로 이동 (영향 최소화)
```

**할당 전략 (Cooperative):**
- `CooperativeStickyAssignor`: 이전 할당을 최대한 유지하면서 필요한 파티션만 이동

```yaml
spring.kafka.consumer:
  properties:
    partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

> **실무 권장**: 항상 `CooperativeStickyAssignor` 사용. Eager Rebalance는 Consumer 수 × 파티션 수가 많을수록 중단 시간이 길어진다.

### Rebalance 발생 시 Consumer 상태 전이

```
STABLE            PREPARING_REBALANCE       COMPLETING_REBALANCE      STABLE
(정상 처리)              │                          │                (재할당 완료)
    │           Rebalance 트리거                    │
    └──────────────────►│                          │
                         │ JoinGroup 요청           │
                         │ (모든 Consumer가 참여)   │
                         └─────────────────────────►│
                                                    │ SyncGroup 요청
                                                    │ (새 할당 수신)
                                                    └────────────────► STABLE
```

### Rebalance 최소화 전략

```yaml
spring.kafka.consumer:
  properties:
    # 1. Heartbeat를 session.timeout.ms의 1/3로 설정
    session.timeout.ms: 45000
    heartbeat.interval.ms: 15000

    # 2. 처리 시간이 긴 경우 poll 간격 여유 확보
    max.poll.interval.ms: 300000
    max.poll.records: 100          # 한 번에 적게 가져와서 처리 시간 감소

    # 3. Cooperative 전략으로 영향 최소화
    partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor

    # 4. Static Membership: Consumer 재시작 시 같은 파티션 재할당 (Rebalance 없이)
    group.instance.id: consumer-instance-1  # 고정 ID 부여
```

### Static Membership

Consumer에 고정 ID를 부여해 **재시작 시 Rebalance 없이 기존 파티션을 다시 할당**받는 기능.

```
[기존 방식: Consumer 재시작]
C1 종료 → Rebalance 발생 → P0 다른 Consumer로 이동
C1 재시작 → Rebalance 발생 → P0 다시 C1으로 이동
→ 2번의 Rebalance

[Static Membership: group.instance.id 설정]
C1 종료 → session.timeout.ms 내 재시작이면 Rebalance 없음
C1 재시작 → 기존 P0 그대로 유지
→ Rebalance 0번 (timeout 내 재시작 시)
```

---

## 6. 면접 Q&A

**Q. Kafka는 기본적으로 At-least-once인데, 이를 어떻게 다루나?**
> Consumer 측에서 멱등성을 보장한다. 처리한 `eventId`를 DB나 Redis에 저장해 중복 요청을 skip한다. `eventId`는 UUID 또는 Snowflake ID처럼 글로벌 유일 값을 사용한다. 이 방식이 Exactly-once보다 단순하고 성능 비용이 낮아 실무에서 더 많이 사용한다.

**Q. Idempotent Producer와 Transactional Producer의 차이는?**
> Idempotent Producer는 단일 파티션 내에서 같은 PID + Sequence Number의 중복 메시지를 Broker가 무시한다. Transactional Producer는 여러 파티션/토픽에 걸친 발행을 원자적으로 처리하며, `isolation.level=read_committed`인 Consumer는 커밋 완료된 메시지만 읽는다.

**Q. Rebalance Storm이란?**
> Rebalance가 연쇄적으로 반복 발생하는 상황. Consumer가 무거운 처리를 하다 `max.poll.interval.ms`를 초과 → Rebalance → 다른 Consumer가 파티션 인수 → 동일 처리 반복 → 또 초과 → 또 Rebalance. `max.poll.records`를 줄이거나 처리 로직을 비동기화하여 해결.

**Q. Cooperative Rebalance를 사용하면 중복 소비가 생길 수 있지 않나?**
> 2차 Rebalance에서 파티션이 이동되는 동안 이전 Consumer가 아직 처리 중인 메시지가 있을 수 있다. 이 경우 새 Consumer가 같은 메시지를 다시 처리할 수 있으므로 Consumer의 멱등성 처리는 여전히 필요하다.
