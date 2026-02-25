# Kafka 토픽 설계 - Log Compaction & Kafka vs MQ 비교

## 1. cleanup.policy: delete vs compact

### delete (기본값) - 시간/크기 기반 삭제

```yaml
# 토픽 생성 시 설정
retention.ms: 604800000      # 7일 후 삭제 (기본값)
retention.bytes: -1          # 크기 제한 없음 (-1 = 무제한)
```

```
시간이 지나면 오래된 세그먼트부터 삭제

시간 →
[seg0: day1][seg1: day2][seg2: day3][seg3: day4][active segment]
     ↑ 7일 지나면 삭제          ↑ 보존
```

**적합한 경우:** 이벤트 스트림 (주문 생성, 웨이팅 알림 등) - 특정 기간 처리 후 불필요

### compact - 키 기반 최신값 유지

```
원본 로그 (같은 키 여러 값):
[K1:V1][K2:V1][K1:V2][K3:V1][K2:V2][K1:V3]

Compaction 후:
[K1:V3][K2:V2][K3:V1]
→ 각 키의 최신 값만 유지
```

**동작 원리:**

```
┌────────────────────────────────────────────┐
│           Log                              │
│  [clean section] │ [dirty section]         │
│  (이미 compact)  │ (compact 대상)          │
└────────────────────────────────────────────┘
                   │
           Log Cleaner Thread 주기적 실행
                   │
           dirty section에서 키별 최신 값 선별
                   │
           clean section에 병합
```

**적합한 경우:** 현재 상태(State)를 표현하는 토픽
- 사용자 프로필 최신 상태
- 상품 재고 현황
- CDC(Change Data Capture) 스트림

### 삭제 표시 (Tombstone)

compact 토픽에서 데이터 삭제:

```java
// 값을 null로 전송하면 해당 키 삭제 표시(Tombstone)
producer.send(new ProducerRecord<>("user-state", userId, null));

// Compaction 시 Tombstone 이후의 같은 키 없으면 해당 키 완전 삭제
// delete.retention.ms(기본 24시간) 동안 Tombstone 유지 후 삭제
```

### delete + compact 함께 사용

```yaml
cleanup.policy: delete,compact  # 두 정책 동시 적용
retention.ms: 86400000           # 1일 이상 된 세그먼트 삭제
                                 # + 키별 최신값 유지
```

---

## 2. 토픽 설계 원칙

### 파티션 수 결정

```
파티션 수 = 예상 최대 Consumer 인스턴스 수

계산 방법:
1. 초당 유입 메시지 수 측정
2. Consumer 1개가 초당 처리 가능한 메시지 수 측정
3. 파티션 수 = 유입량 / Consumer 처리량

예시:
초당 유입: 10,000 msg/s
Consumer 1개 처리량: 500 msg/s
필요 파티션 수: 10,000 / 500 = 20개
```

**파티션 수 결정 원칙:**
- 처음에는 보수적으로 시작 (3~6개)
- Lag 모니터링 후 필요시 증설
- 한번 줄이는 것은 불가능 (토픽 삭제 후 재생성 필요)
- 키 기반 파티셔닝 사용 중이면 파티션 수 변경 시 같은 키가 다른 파티션으로 이동 → 순서 깨짐 주의

### 파티션 키 설계

```java
// 키 없음: Round-Robin → 순서 보장 없음, 균등 분배
producer.send(new ProducerRecord<>("events", null, message));

// restaurantId 키: 같은 식당의 이벤트는 같은 파티션
// → 식당 단위 순서 보장
producer.send(new ProducerRecord<>("waiting-events", restaurantId, message));

// userId 키: 같은 사용자의 이벤트는 같은 파티션
// → 사용자 단위 순서 보장
producer.send(new ProducerRecord<>("user-events", userId, message));
```

**키 분포 주의 (Hot Partition):**

```
문제: 특정 키에 메시지가 집중되면 하나의 파티션만 과부하

예시: 이벤트 시간대에 특정 식당의 웨이팅이 폭발적으로 증가
→ 해당 restaurantId 파티션에 메시지 집중

해결:
1. 복합 키 사용: restaurantId + timestamp.secondOfMinute
2. 커스텀 Partitioner로 분산 로직 구현
3. 특정 토픽의 처리량을 아예 분리 (별도 토픽)
```

### 토픽 네이밍 컨벤션

```
{도메인}.{이벤트타입}.{버전}

예시:
waiting.registered.v1      # 웨이팅 등록 이벤트
waiting.called.v1          # 웨이팅 호출 이벤트
restaurant.updated.v2      # 식당 정보 변경 이벤트 (v2 스키마)
payment.completed.v1

Dead Letter Topic:
waiting.registered.v1.DLT  # 처리 실패한 메시지
```

### 스키마 호환성 (하위 호환 유지)

```json
// v1 스키마
{"waitingId": 123, "restaurantId": 456}

// v2 스키마 (하위 호환 O - 필드 추가)
{"waitingId": 123, "restaurantId": 456, "userId": 789}

// v2 스키마 (하위 호환 X - 필드 변경, 하면 안 됨)
{"id": 123, "restaurantId": 456}  ← waitingId → id 변경
```

**배포 순서:** Consumer 먼저 배포 → Producer 배포
→ Consumer가 v2 스키마를 이미 처리할 수 있는 상태에서 Producer가 v2 전송

---

## 3. Kafka vs RabbitMQ

### 핵심 철학 차이

| 항목 | Kafka | RabbitMQ |
|------|-------|----------|
| **패러다임** | **Pull** (Consumer가 가져감) | **Push** (Broker가 밀어줌) |
| **메시지 보존** | 보존 기간까지 디스크에 유지 | 소비 후 즉시 삭제 (기본) |
| **재소비** | 가능 (offset 되감기) | 불가 (이미 삭제됨) |
| **Consumer 그룹** | 같은 메시지를 다른 그룹이 독립 소비 | 큐에서 1번만 소비됨 |
| **순서 보장** | 파티션 내 보장 | 큐 내 보장 |
| **처리량** | 매우 높음 (100만 msg/s) | 높음 (수만 msg/s) |
| **지연** | 낮음 (ms 단위) | 매우 낮음 (μs 단위) |
| **라우팅** | 토픽 기반 단순 | Exchange/Binding으로 복잡한 라우팅 가능 |

### 메시지 소비 모델 차이

```
Kafka (Pull):
Consumer: "파티션 0의 offset 1234부터 줘"
Broker: [msg1234, msg1235, msg1236] 반환
Consumer: 처리 후 offset commit

RabbitMQ (Push):
Broker: Consumer에게 메시지 밀어줌
Consumer: 처리 후 ACK
ACK 받으면 Broker가 메시지 삭제
```

### 사용 시나리오별 선택

| 시나리오 | 선택 | 이유 |
|---------|------|------|
| 대용량 이벤트 스트리밍 | **Kafka** | 높은 처리량, 보존, 재처리 |
| MSA 이벤트 기반 통신 | **Kafka** | Consumer Group별 독립 소비 |
| 실시간 로그/메트릭 수집 | **Kafka** | 순차 I/O, 고처리량 |
| 작업 큐 (Task Queue) | **RabbitMQ** | Push 모델, 복잡한 라우팅 |
| 즉각적인 알림 (μs 지연) | **RabbitMQ** | 낮은 지연 |
| 복잡한 메시지 라우팅 | **RabbitMQ** | Exchange/Binding 유연성 |
| 이벤트 재처리/감사 | **Kafka** | 메시지 보존 후 재소비 가능 |

### 면접 답변 예시

> **"왜 RabbitMQ 대신 Kafka를 선택했나?"**
>
> Booster 프로젝트에서 Kafka를 선택한 이유는 세 가지입니다.
>
> 첫째, **Consumer Group별 독립 소비**입니다. `waiting-called` 이벤트를 notification-service와 analytics-service가 각자 독립적으로 소비해야 했는데, Kafka는 같은 이벤트를 여러 Consumer Group이 각각 처음부터 소비할 수 있습니다.
>
> 둘째, **메시지 보존과 재처리**입니다. notification-service에 장애가 생겨도 Kafka에 메시지가 남아 있어 장애 복구 후 이어서 처리할 수 있습니다.
>
> 셋째, **대용량 처리량**입니다. 대기 피크 타임에 초당 수만 건의 이벤트가 발생해도 순차 I/O와 배치 처리로 안정적으로 처리합니다.

---

## 4. 면접 Q&A

**Q. Log Compaction은 언제 실행되나?**
> 백그라운드 Log Cleaner 스레드가 주기적으로 dirty ratio(`min.cleanable.dirty.ratio`, 기본 50%)를 초과한 세그먼트를 compaction한다. 실시간으로 동작하지 않으므로 compaction 전까지는 같은 키의 여러 버전이 공존할 수 있다.

**Q. Kafka의 Pull 방식이 RabbitMQ의 Push 방식보다 좋은 이유는?**
> Consumer가 자신의 처리 속도에 맞게 메시지를 가져오므로 Consumer 과부하를 스스로 조절할 수 있다. Push 방식은 Consumer가 느려도 Broker가 계속 밀어붙여 Consumer 메모리 압박이 생길 수 있다. Kafka는 Backpressure가 자연스럽게 처리된다.

**Q. 토픽의 파티션을 줄이는 방법은?**
> 없다. Kafka는 파티션 감소를 지원하지 않는다. 줄여야 한다면 새 토픽을 적절한 파티션 수로 생성하고, 기존 토픽의 Consumer가 모두 소비를 완료한 후 새 토픽으로 마이그레이션해야 한다. 파티션 수는 처음부터 신중하게 결정해야 한다.

**Q. 같은 키 메시지가 Compact 토픽에서 순서대로 처리될 보장이 있나?**
> Compaction은 동일 파티션 내의 같은 키에 대해서만 동작하고, 최신 값을 유지하므로 Consumer는 항상 최신 상태를 읽는다. 단, Compaction이 완료되지 않은 dirty section에서는 여러 버전을 읽을 수 있으므로, Consumer는 나중에 받은 값이 더 최신임을 인지하고 처리해야 한다.
