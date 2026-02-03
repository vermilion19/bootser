# Kafka Producer/Consumer 및 파티션 설정 가이드

## 1. Kafka 클라이언트 구분

Kafka 클라이언트는 **Producer**와 **Consumer**로 구분되며, 하나의 서비스가 두 역할을 동시에 수행할 수 있습니다.

### Booster 프로젝트 예시: waiting-service

`waiting-service`는 **Producer이면서 동시에 Consumer**입니다.

| 역할 | 클래스 | 토픽 | 설명 |
|------|--------|------|------|
| **Producer** | `OutboxMessageRelay` | `booster.waiting.events` | Outbox 패턴으로 대기 이벤트 발행 |
| **Producer** | `WaitingEventProducer` | `${app.kafka.topics.waiting-events}` | 대기 상태 변경 이벤트 발행 |
| **Consumer** | `RestaurantEventConsumer` | `booster.restaurant.events` | 식당 정보 변경 이벤트 수신 → 캐시 갱신 |

### 이벤트 흐름도

```
┌─────────────────────┐
│  restaurant-service │
└──────────┬──────────┘
           │ produce
           ▼
   [booster.restaurant.events]
           │
           │ consume
           ▼
┌─────────────────────┐
│   waiting-service   │ ◀── Producer이자 Consumer
└──────────┬──────────┘
           │ produce
           ▼
   [booster.waiting.events]
           │
           │ consume
           ▼
┌─────────────────────┐
│ notification-service│
└─────────────────────┘
```

---

## 2. 파티션(Partition) 개념

### 파티션이란?

- 토픽을 **물리적으로 분할**한 단위
- 각 파티션은 독립적인 로그 파일로 저장됨
- **같은 파티션 내에서만 메시지 순서가 보장**됨

```
Topic: booster.waiting.events
├── Partition 0: [msg1] [msg4] [msg7] ...
├── Partition 1: [msg2] [msg5] [msg8] ...
└── Partition 2: [msg3] [msg6] [msg9] ...
```

### 파티션의 역할

| 목적 | 설명 |
|------|------|
| **병렬 처리** | 여러 Consumer가 각 파티션을 동시에 처리 |
| **확장성** | Consumer 인스턴스 추가로 처리량 증가 |
| **순서 보장** | Key 기반 파티셔닝으로 특정 데이터의 순서 보장 |

---

## 3. 기본값 및 현재 설정

### Kafka 브로커 기본값

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `num.partitions` | 1 | 토픽 자동 생성 시 파티션 수 |
| `default.replication.factor` | 1 | 복제본 수 |
| `auto.create.topics.enable` | true | 토픽 자동 생성 여부 |

### Booster 프로젝트 현재 설정

```yaml
# dockers/infrastructure/docker-compose.yml
kafka:
  environment:
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"  # 토픽 자동 생성
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

현재 **파티션 설정이 명시되어 있지 않아** 기본값 1개 파티션이 사용됩니다.

---

## 4. 파티션 설정 방법

### 방법 1: Spring Boot @Bean으로 토픽 정의 (권장)

```java
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic waitingEventsTopic() {
        return TopicBuilder.name("booster.waiting.events")
                .partitions(3)           // 파티션 수
                .replicas(1)             // 복제본 수 (브로커 수 이하)
                .build();
    }

    @Bean
    public NewTopic restaurantEventsTopic() {
        return TopicBuilder.name("booster.restaurant.events")
                .partitions(3)
                .replicas(1)
                .compact()               // 로그 컴팩션 (선택)
                .build();
    }
}
```

### 방법 2: application.yml 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: waiting-service-group
      auto-offset-reset: latest
    admin:
      properties:
        default.replication.factor: 1
```

### 방법 3: docker-compose에서 브로커 기본값 변경

```yaml
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 3  # 자동 생성 토픽의 기본 파티션 수
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
```

### 방법 4: Kafka CLI로 직접 생성

```bash
# 토픽 생성
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic booster.waiting.events \
  --partitions 3 \
  --replication-factor 1

# 토픽 목록 확인
kafka-topics.sh --list --bootstrap-server localhost:9092

# 토픽 상세 정보
kafka-topics.sh --describe \
  --bootstrap-server localhost:9092 \
  --topic booster.waiting.events
```

---

## 5. 파티션 수 결정 기준

### 고려사항

| 요소 | 설명 |
|------|------|
| **Consumer 병렬성** | 파티션 수 = Consumer 인스턴스 최대 수 |
| **처리량 요구사항** | 높은 처리량 → 파티션 수 증가 |
| **순서 보장 범위** | 같은 Key의 메시지는 같은 파티션 → 순서 보장 |
| **브로커 리소스** | 파티션이 많으면 메모리, 파일 핸들 증가 |
| **리밸런싱 시간** | 파티션이 많으면 Consumer 그룹 리밸런싱 시간 증가 |

### 파티션 vs Consumer 관계

```
파티션 3개, Consumer 2개:
├── Consumer A: Partition 0, 1
└── Consumer B: Partition 2

파티션 3개, Consumer 3개:
├── Consumer A: Partition 0
├── Consumer B: Partition 1
└── Consumer C: Partition 2

파티션 3개, Consumer 4개:
├── Consumer A: Partition 0
├── Consumer B: Partition 1
├── Consumer C: Partition 2
└── Consumer D: (대기 - 할당된 파티션 없음)
```

**결론**: 파티션 수 이상의 Consumer는 의미 없음

### 권장 파티션 수

| 상황 | 권장 파티션 수 |
|------|---------------|
| 개발/테스트 | 1~3 |
| 소규모 운영 | 3~6 |
| 중규모 운영 | 6~12 |
| 대규모 운영 | 12+ (처리량에 따라) |

---

## 6. Key 기반 파티셔닝 (순서 보장)

### 문제 상황

```
waitingId=100의 이벤트:
  1. CREATED  → Partition 0
  2. CALLED   → Partition 1  ← 순서 보장 안됨!
  3. ENTERED  → Partition 2
```

### 해결: Key로 파티션 고정

```java
// waitingId를 key로 사용 → 같은 대기건은 항상 같은 파티션으로
kafkaTemplate.send(
    "booster.waiting.events",
    String.valueOf(waitingId),  // key
    event                        // value
);
```

```
waitingId=100의 이벤트 (key="100"):
  1. CREATED  → Partition 1  (hash("100") % 3 = 1)
  2. CALLED   → Partition 1  ← 순서 보장!
  3. ENTERED  → Partition 1
```

### Booster 프로젝트 적용 예시

```java
// OutboxMessageRelay.java
kafkaTemplate.send(
    "booster.waiting.events",
    String.valueOf(event.getAggregateId()),  // waitingId를 key로
    event.getPayload()
);
```

이미 `aggregateId`를 Key로 사용하고 있어 **동일 대기건의 이벤트 순서가 보장**됩니다.

---

## 7. Consumer Group과 파티션

### Consumer Group이란?

- 같은 `group.id`를 가진 Consumer들의 집합
- 하나의 파티션은 그룹 내 **하나의 Consumer에만 할당**
- 다른 그룹은 같은 메시지를 독립적으로 소비 가능

```
Topic: booster.waiting.events (3 partitions)

Consumer Group A (notification-service):
├── Consumer A1: Partition 0, 1
└── Consumer A2: Partition 2

Consumer Group B (analytics-service):
├── Consumer B1: Partition 0, 1, 2  ← 모든 메시지 수신 (독립적)
```

### 설정 예시

```java
@KafkaListener(
    topics = "booster.restaurant.events",
    groupId = "waiting-service-group"  // Consumer Group 지정
)
public void consume(String message) {
    // ...
}
```

---

## 8. 운영 체크리스트

### 토픽 생성 시

- [ ] 파티션 수 결정 (예상 Consumer 인스턴스 수 고려)
- [ ] Replication Factor 결정 (브로커 수 이하)
- [ ] Key 전략 결정 (순서 보장이 필요한 경우)

### Consumer 설정 시

- [ ] `group.id` 설정
- [ ] `auto.offset.reset` 설정 (earliest/latest)
- [ ] 멱등성(Idempotency) 처리 (중복 메시지 대비)

### 모니터링

- [ ] Consumer Lag 확인 (처리 지연 여부)
- [ ] 파티션 분배 상태 확인
- [ ] 처리량(Throughput) 모니터링

---

## 참고 자료

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/)
- [Kafka UI](http://localhost:8989) - Booster 프로젝트 Kafka 모니터링
