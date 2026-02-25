# Kafka Consumer Offset 관리 심화

## 1. Offset이란?

**파티션 내에서 메시지의 위치를 나타내는 순번.**
Consumer는 자신이 어디까지 읽었는지 offset을 `__consumer_offsets` 토픽에 기록한다.

```
Partition 0:  [msg0][msg1][msg2][msg3][msg4][msg5]
               offset: 0    1    2    3    4    5

Consumer A가 msg2까지 처리했다면 → commit offset = 3 (다음에 읽을 위치)

재시작 후:
Consumer A → __consumer_offsets에서 offset=3 확인 → msg3부터 재개
```

---

## 2. __consumer_offsets 토픽

Consumer가 commit한 offset이 저장되는 **Kafka 내부 토픽.**

```
__consumer_offsets 토픽 구조

Key:   [group.id, topic, partition]
Value: [offset, metadata, timestamp]

예시:
Key:   "notification-group", "waiting-events", 0
Value: offset=1234, timestamp=2024-01-01T10:00:00
```

- 기본 50개 파티션으로 구성 (대규모 클러스터에서 부하 분산)
- `group.id` 기반 해싱으로 특정 파티션에 저장
- 내부 토픽이므로 직접 접근 대신 `kafka-consumer-groups.sh`로 확인

```bash
# Consumer Group의 현재 offset 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group notification-group

# 출력:
# GROUP             TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# notification-group waiting-events  0          1234            1240            6
# notification-group waiting-events  1          890             895             5
```

---

## 3. Auto Commit의 위험성

`enable.auto.commit=true` (Spring Kafka 기본값: true)

### Auto Commit 동작 방식

```
poll() 호출 → 메시지 배치 반환
  ↓
처리 중...
  ↓
auto.commit.interval.ms(5000ms) 경과
  ↓
백그라운드에서 자동으로 현재 offset commit
```

### 위험 시나리오 1: 메시지 유실

```
poll() → [msg1, msg2, msg3] 반환
  ↓
처리 시작
  ↓
5초 경과 → 자동 commit (offset=4)  ← msg3까지 처리 완료로 표시됨
  ↓
msg2 처리 중 Consumer 장애 발생!
  ↓
재시작 → offset=4 부터 시작
→ msg2, msg3 처리 안 됐지만 commit돼서 영원히 유실
```

### 위험 시나리오 2: 중복 처리

```
poll() → [msg1, msg2, msg3] 반환
  ↓
msg1, msg2 처리 완료
  ↓
msg3 처리 중 Consumer 장애 (아직 commit 전)
  ↓
재시작 → 마지막 commit offset부터 → msg1부터 재처리
→ msg1, msg2 중복 처리
```

> **결론**: `enable.auto.commit=false`로 설정하고 수동 commit을 사용하는 것이 안전하다.

---

## 4. Commit 전략 3가지

### 4-1. Auto Commit (자동 커밋)

```yaml
spring.kafka.consumer:
  enable-auto-commit: true
  auto-commit-interval: 5000  # 5초마다 자동 commit
```

| 장점 | 단점 |
|------|------|
| 설정 간단 | 메시지 유실 가능 |
| 코드 불필요 | 중복 처리 가능 |
| | 처리 완료 시점 보장 없음 |

**적합한 경우**: 로그 수집 등 약간의 유실이 허용되는 경우

### 4-2. Manual Sync Commit (수동 동기 커밋)

```java
@KafkaListener(topics = "waiting-events")
public void handle(ConsumerRecord<String, String> record,
                   Acknowledgment ack) {
    try {
        process(record); // 처리 완료 후
        ack.acknowledge(); // 동기 commit (완료 확인 후 반환)
    } catch (Exception e) {
        // 처리 실패 시 commit 안 함 → 재시도
        log.error("처리 실패, 재시도 대기: {}", e.getMessage());
    }
}
```

```yaml
spring.kafka:
  consumer:
    enable-auto-commit: false
  listener:
    ack-mode: MANUAL_IMMEDIATE  # acknowledge() 호출 시 즉시 commit
```

| 장점 | 단점 |
|------|------|
| 처리 완료 후 확실한 commit | commit 응답 대기로 처리량 낮음 |
| 유실 없음 | Broker 응답 대기 시간만큼 느림 |

**적합한 경우**: 결제, 중요 비즈니스 이벤트 처리

### 4-3. Manual Async Commit (수동 비동기 커밋)

```java
@KafkaListener(topics = "waiting-events")
public void handle(ConsumerRecord<String, String> record,
                   Acknowledgment ack) {
    process(record);
    ack.acknowledge(); // 비동기로 commit (응답 안 기다림)
}
```

```yaml
spring.kafka.listener:
  ack-mode: MANUAL  # poll 완료 시 한 번에 batch commit
```

| 장점 | 단점 |
|------|------|
| commit 응답 대기 없음 → 처리량 높음 | 실패 시 재시도 복잡 |
| | commit 순서 보장 필요 |

### 배치 단위 커밋 (가장 효율적)

```java
@KafkaListener(topics = "waiting-events")
public void handleBatch(List<ConsumerRecord<String, String>> records,
                        Acknowledgment ack) {
    for (ConsumerRecord<String, String> record : records) {
        process(record); // 배치 전체 처리
    }
    ack.acknowledge(); // 배치 전체 처리 완료 후 1번 commit
}
```

```yaml
spring.kafka.listener:
  type: BATCH          # 배치 리스너 활성화
  ack-mode: BATCH      # 배치 완료 후 commit
```

---

## 5. Commit 전략 비교

| 전략 | 유실 | 중복 | 처리량 | 복잡도 |
|------|------|------|--------|--------|
| Auto Commit | 가능 | 가능 | 높음 | 낮음 |
| Manual Sync | 없음 | 가능(재시작 시) | 낮음 | 보통 |
| Manual Async | 없음 | 가능(재시작 시) | 높음 | 높음 |
| Batch Commit | 없음 | 가능(재시작 시) | 매우 높음 | 보통 |

> **중복은 항상 가능**: Commit 후 응답 오기 전 Consumer 장애 나면 재시작 시 같은 offset부터 다시 처리. Consumer 멱등성 처리는 모든 전략에서 필요.

---

## 6. Offset Reset 전략

Consumer Group이 처음 구독하거나 offset이 만료됐을 때 어디서부터 읽을지 결정.

```yaml
spring.kafka.consumer:
  auto-offset-reset: earliest  # 파티션의 처음부터 (기본: latest)
```

| 설정 | 동작 | 사용 시점 |
|------|------|-----------|
| `earliest` | 가장 오래된 메시지부터 | 모든 메시지를 처음부터 처리해야 할 때 |
| `latest` | 가장 최신 메시지부터 | 이전 메시지 불필요, 새 메시지만 처리 |
| `none` | offset 없으면 예외 | 명시적으로 처리 (실수 방지) |

### 수동 Offset Reset (운영 명령)

```bash
# Consumer Group 중지 후 실행

# 특정 토픽의 특정 파티션 offset 지정
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-events:0 \
  --reset-offsets --to-offset 1000 --execute

# 특정 시점으로 offset 이동
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-events \
  --reset-offsets --to-datetime 2024-01-01T00:00:00.000 --execute

# 가장 처음으로 이동 (전체 재처리)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group \
  --topic waiting-events \
  --reset-offsets --to-earliest --execute
```

> **주의**: offset reset은 Consumer Group이 완전히 중지된 상태에서만 가능. 실행 전 반드시 처리 결과의 중복/유실 영향을 분석해야 한다.

---

## 7. Consumer Lag 모니터링

```
Consumer Lag = Log-End-Offset - Current-Offset

예시:
Partition 0: LOG-END=1240, CURRENT=1234, LAG=6
Partition 1: LOG-END=895,  CURRENT=890,  LAG=5
TOTAL LAG = 11
```

### Lag이 증가하는 원인과 대응

```
Lag 증가 원인 트리
├── 처리 속도 < 유입 속도
│   ├── 외부 API 지연 → Circuit Breaker, 비동기화
│   ├── DB 쿼리 느림 → 쿼리 최적화, 인덱스
│   └── 비즈니스 로직 복잡 → 배치 처리, 병렬화
│
├── Consumer 수 부족
│   └── 인스턴스 수 증가 (파티션 수 이하로)
│
└── 파티션 수 부족
    └── 파티션 증설 (키 기반 파티셔닝 영향 분석 필수)
```

```yaml
# Prometheus + Grafana 알림 설정 (kafka-operations-guide.md 참고)
# Lag > 10,000이면 warning 알림
```

---

## 8. 면접 Q&A

**Q. Auto Commit을 사용하면 안 되는 이유는?**
> commit 시점과 처리 완료 시점이 달라서 유실과 중복이 모두 발생할 수 있다. 5초 주기로 commit되는 동안 Consumer가 죽으면 아직 처리 안 된 메시지가 commit될 수 있고(유실), 처리 후 commit 전에 죽으면 재시작 시 중복 처리된다. 실무에서는 `enable.auto.commit=false`로 설정하고 처리 완료 후 수동 commit이 표준이다.

**Q. Commit을 얼마나 자주 해야 하나?**
> 너무 자주 commit하면 Broker에 commit 요청이 많아져 overhead가 생기고, 너무 드물게 하면 Consumer 장애 시 재처리 범위가 커진다. 배치 단위로 처리하고 배치 완료 후 1번 commit하는 것이 처리량과 안전성의 균형점이다. 중요한 비즈니스 로직은 레코드 단위로 commit하는 것을 권장한다.

**Q. Offset이 만료되면 어떻게 되나?**
> `__consumer_offsets`에 저장된 commit 정보는 `offsets.retention.minutes`(기본 7일) 이후 삭제된다. 만료 후 Consumer가 재시작하면 `auto.offset.reset` 설정에 따라 동작한다. `latest`면 그 시점의 최신 offset부터 읽어 이전 메시지가 유실되므로, `earliest`로 설정하거나 offset 만료 전에 재시작되도록 관리해야 한다.
