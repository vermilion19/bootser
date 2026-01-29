# Kafka 운영 가이드 - 장애 시나리오와 대응 전략

## 목차

1. [장애 발생 가능 시나리오](#1-장애-발생-가능-시나리오)
2. [장애 대응 방법](#2-장애-대응-방법)
3. [실제 운영 예제](#3-실제-운영-예제)
4. [운영 시 주의점](#4-운영-시-주의점)
5. [모니터링 체크리스트](#5-모니터링-체크리스트)

---

## 1. 장애 발생 가능 시나리오

### 1.1 Broker 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| Broker Down | OOM, 디스크 풀, HW 장애 | Producer/Consumer 타임아웃 |
| Leader Election 지연 | Controller 과부하, ZK/KRaft 불안정 | 일시적 produce/consume 불가 |
| ISR Shrink | Follower 복제 지연 | `under-replicated-partitions` 증가 |

### 1.2 Producer 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| 메시지 유실 | `acks=0` 또는 `acks=1` 설정 | 데이터 정합성 깨짐 |
| 중복 발행 | 네트워크 타임아웃 후 재시도 | Consumer 측 중복 처리 |
| 직렬화 실패 | 스키마 불일치, 잘못된 데이터 | `SerializationException` |
| 버퍼 풀 고갈 | 발행 속도 > 전송 속도 | `BufferExhaustedException` |
| Outbox 적체 | Relay 스케줄러 지연, Kafka 연결 실패 | Outbox 테이블 데이터 누적 |

### 1.3 Consumer 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| Consumer Lag 증가 | 처리 속도 < 유입 속도 | Lag 모니터링 알림 |
| Rebalance Storm | 잦은 Consumer 재시작, 긴 처리 시간 | 전체 Consumer Group 일시 정지 |
| Offset 유실 | `auto.offset.reset=latest` + offset 만료 | 메시지 누락 |
| Poison Pill | 역직렬화 불가 메시지 | Consumer 반복 실패 후 재시작 루프 |
| 중복 소비 | Rebalance 중 offset 미커밋 | 비즈니스 로직 중복 실행 |

### 1.4 인프라/네트워크 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| 네트워크 파티션 | 스위치 장애, DNS 이슈 | 클러스터 분리(Split Brain) |
| 디스크 I/O 병목 | 로그 세그먼트 과다, 느린 디스크 | Produce 레이턴시 급증 |
| ZooKeeper/KRaft 장애 | ZK 클러스터 쿼럼 손실 | Controller 선출 불가, 메타데이터 갱신 중단 |

---

## 2. 장애 대응 방법

### 2.1 Broker 장애 대응

```bash
# 클러스터 상태 확인
kafka-metadata.sh --snapshot /var/kafka-logs/__cluster_metadata-0/00000000000000000000.log --cluster-id <id>

# Under-replicated partition 확인
kafka-topics.sh --bootstrap-server localhost:9092 --describe --under-replicated-partitions

# 특정 Broker의 리더 파티션을 다른 Broker로 이동
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --reassignment-json-file reassign.json --execute

# Preferred Leader Election 실행
kafka-leader-election.sh --bootstrap-server localhost:9092 \
  --election-type PREFERRED --all-topic-partitions
```

**복구 절차:**
1. 장애 Broker 로그 확인 (`server.log`, `controller.log`)
2. OOM이면 힙 사이즈 조정, 디스크 풀이면 오래된 로그 세그먼트 정리
3. Broker 재시작 후 ISR 복귀 확인
4. Preferred Leader Election으로 리더 재분배

### 2.2 Producer 장애 대응

**메시지 유실 방지 설정:**
```yaml
# application.yml (Spring Kafka)
spring:
  kafka:
    producer:
      acks: all                    # 모든 ISR 복제 확인
      retries: 3                   # 재시도 횟수
      properties:
        enable.idempotence: true   # 멱등성 Producer (중복 방지)
        max.in.flight.requests.per.connection: 5  # 멱등성 모드에서 최대 5
        delivery.timeout.ms: 120000
        request.timeout.ms: 30000
        retry.backoff.ms: 1000
```

**Outbox 적체 대응:**
```java
// 1. Outbox 테이블 모니터링 쿼리
// SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '5 minutes';

// 2. 수동 재발행 API 또는 배치 작업
@Scheduled(fixedDelay = 60000)
public void alertStaleOutboxEvents() {
    long staleCount = outboxRepository.countStaleEvents(LocalDateTime.now().minusMinutes(5));
    if (staleCount > 100) {
        log.warn("Outbox 적체 감지: {} 건", staleCount);
        // 알림 발송
    }
}
```

### 2.3 Consumer 장애 대응

**Rebalance Storm 방지:**
```yaml
spring:
  kafka:
    consumer:
      properties:
        max.poll.interval.ms: 300000       # 처리 시간 충분히 확보 (5분)
        max.poll.records: 100              # 한 번에 가져오는 레코드 수 제한
        session.timeout.ms: 45000          # 세션 타임아웃
        heartbeat.interval.ms: 15000       # 하트비트 간격
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

**Poison Pill (역직렬화 실패) 처리:**
```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    // DLT(Dead Letter Topic)로 전송
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate);

    // 3회 재시도 후 DLT로 이동
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

    // 역직렬화 에러는 재시도 없이 즉시 DLT로
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
}
```

**Consumer Lag 대응:**
```bash
# Consumer Group Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group notification-group

# 특정 토픽의 오프셋 리셋 (주의: 데이터 유실 가능)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-group --topic waiting-events \
  --reset-offsets --to-latest --execute
```

**Lag 해소 전략:**
1. Consumer 인스턴스 수 증가 (파티션 수 이하로)
2. `max.poll.records` 조정으로 배치 크기 최적화
3. 처리 로직 비동기화 또는 병렬 처리
4. 파티션 수 확장 (토픽 재생성 없이 가능하나, 키 기반 파티셔닝 시 재분배됨)

### 2.4 중복 소비 대응 (멱등성 보장)

```java
@KafkaListener(topics = "waiting-called-events")
public void handleWaitingCalled(WaitingCalledEvent event) {
    // 멱등성 키로 중복 체크
    if (processedEventRepository.existsByEventId(event.getEventId())) {
        log.info("이미 처리된 이벤트 skip: {}", event.getEventId());
        return;
    }

    // 비즈니스 로직 처리
    notificationService.sendCallNotification(event);

    // 처리 완료 기록
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
}
```

---

## 3. 실제 운영 예제

### 3.1 Booster 프로젝트 - Outbox + Kafka 흐름

```
[waiting-service]
  └─ WaitingFacade.call()
       ├─ waiting.call()                    (도메인 상태 변경)
       ├─ outboxRepository.save(event)      (Outbox 저장, 같은 트랜잭션)
       └─ applicationEventPublisher.publish  (Spring Event)

[OutboxMessageRelay] (3초 주기 폴링)
  └─ outboxRepository.findPendingEvents()
       ├─ kafkaTemplate.send("waiting-called", payload)
       └─ outbox.markPublished()

[notification-service - KafkaConsumer]
  └─ WaitingEventConsumer.onWaitingCalled(event)
       ├─ 중복 체크 (eventId)
       └─ 알림 발송 (SMS/Push)
```

### 3.2 장애 시나리오별 대응 시뮬레이션

**시나리오 A: Kafka Broker 1대 다운 (3대 클러스터)**

```
1. ISR에서 해당 Broker 제거 → Leader Election 발생 (자동)
2. Producer: acks=all이면 나머지 2대로 정상 발행
3. Consumer: 리더가 바뀐 파티션에서 자동으로 fetch 대상 변경
4. 조치: 장애 Broker 복구 → ISR 복귀 확인 → Preferred Leader Election
```

**시나리오 B: Consumer 처리 지연으로 Lag 5만 이상 누적**

```
1. 알림 수신 (Lag > 10000 알림 설정)
2. Consumer 로그 확인 → 외부 API 타임아웃이 원인
3. 즉시 대응: max.poll.records를 500 → 50으로 축소 (poll 간격 내 처리 보장)
4. 근본 대응: 외부 API 호출을 비동기로 전환, Circuit Breaker 적용
5. Lag 해소 후 설정 원복
```

**시나리오 C: Outbox 이벤트 발행 실패 누적**

```
1. outbox_event 테이블에 PENDING 상태 1000건 이상 적체
2. 원인: Kafka 브로커 일시 장애로 send 실패
3. 조치:
   - Kafka 정상화 확인
   - OutboxMessageRelay가 자동으로 재발행 (at-least-once)
   - Consumer 측 멱등성 처리로 중복 안전
4. 장기 적체 시: 배치로 오래된 이벤트 수동 처리 또는 DLT 이동
```

---

## 4. 운영 시 주의점

### 4.1 토픽 설계

- **파티션 수는 신중하게**: 한 번 늘리면 줄일 수 없음. Consumer 인스턴스 최대 수 = 파티션 수
- **리텐션 정책**: `retention.ms`를 비즈니스 요구에 맞게 설정 (기본 7일)
- **키 설계**: 같은 키는 같은 파티션으로 → 순서 보장이 필요한 데이터는 동일 키 사용
  ```
  예: waiting-events 토픽에서 restaurantId를 키로 사용
  → 같은 식당의 웨이팅 이벤트는 순서 보장
  ```
- **토픽 네이밍**: `{도메인}.{이벤트}.{버전}` (예: `waiting.called.v1`)

### 4.2 Producer 운영 주의점

| 항목 | 권장 설정 | 이유 |
|------|-----------|------|
| `acks` | `all` | 메시지 유실 방지 |
| `enable.idempotence` | `true` | 중복 발행 방지 |
| `compression.type` | `lz4` 또는 `zstd` | 네트워크/디스크 절약 |
| `linger.ms` | `5~20` | 배치 효율 향상 |
| `batch.size` | `32768~65536` | 배치 크기 최적화 |

### 4.3 Consumer 운영 주의점

- **auto commit 비활성화**: 수동 커밋으로 정확한 offset 관리
  ```yaml
  spring.kafka.consumer.enable-auto-commit: false
  spring.kafka.listener.ack-mode: MANUAL_IMMEDIATE  # 또는 RECORD
  ```
- **Consumer Group ID는 서비스 단위로 분리**: 서비스별 독립 소비
- **Rebalance 최소화**: `CooperativeStickyAssignor` 사용, 불필요한 재시작 지양
- **처리 실패 전략 필수**: DLT 또는 retry topic 구성

### 4.4 클러스터 운영 주의점

- **Broker 수**: 최소 3대 (replication-factor=3, min.insync.replicas=2)
- **디스크**: SSD 권장, 로그 세그먼트 크기와 리텐션 기간에 따라 용량 산정
- **JVM 힙**: 6~8GB 권장 (너무 크면 GC 문제)
- **OS 튜닝**:
  ```bash
  # 파일 디스크립터 제한 증가
  ulimit -n 100000

  # 페이지 캐시 활용을 위해 힙 외 메모리 충분히 확보
  # 전체 메모리의 50% 이상을 OS 캐시에 할당
  ```
- **Rolling Upgrade**: 한 번에 하나의 Broker만 재시작, ISR 복귀 확인 후 다음 진행

### 4.5 보안

- **인증**: SASL/SCRAM 또는 mTLS 적용
- **인가**: ACL로 토픽별 접근 제어
- **암호화**: SSL/TLS로 전송 구간 암호화
- **네트워크**: Broker 포트(9092/9093)는 내부 네트워크에서만 접근 가능하게 설정

### 4.6 배포 시 주의점

- **Consumer 먼저 배포**: 새 이벤트 스키마를 처리할 수 있어야 함
- **스키마 호환성**: 하위 호환(Backward Compatible) 유지 → 필드 추가만, 삭제/변경 금지
- **Blue-Green 배포 시**: 두 버전이 동시에 같은 Consumer Group에 참여하면 Rebalance 발생 → Rolling 배포 권장

---

## 5. 모니터링 체크리스트

### 필수 모니터링 메트릭

| 메트릭 | 임계값 예시 | 의미 |
|--------|------------|------|
| Consumer Lag | > 10,000 | 처리 지연 |
| Under Replicated Partitions | > 0 | 복제 문제 |
| ISR Shrink Rate | > 0/min | Follower 탈락 |
| Request Latency (p99) | > 500ms | Broker 응답 지연 |
| Active Controller Count | != 1 | Controller 장애 |
| Disk Usage | > 80% | 디스크 부족 |
| Network I/O | 대역폭의 70% | 네트워크 병목 |
| Outbox Pending Count | > 500 | 이벤트 발행 적체 |

### Grafana 대시보드 구성 권장

```
1. Cluster Overview: Broker 상태, 파티션 분포, ISR 현황
2. Producer Metrics: 발행 성공/실패율, 레이턴시, 배치 크기
3. Consumer Metrics: Lag, 처리율, Rebalance 빈도
4. Topic Metrics: 토픽별 메시지 유입량, 파티션별 offset
5. Outbox Metrics: Pending/Published/Failed 건수 추이
```

### Prometheus 알림 규칙 예시

```yaml
groups:
  - name: kafka-alerts
    rules:
      - alert: HighConsumerLag
        expr: kafka_consumer_group_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Consumer lag이 10,000 이상 ({{ $value }})"

      - alert: UnderReplicatedPartitions
        expr: kafka_server_replica_manager_under_replicated_partitions > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Under-replicated partition 발생"

      - alert: OutboxBacklog
        expr: outbox_pending_count > 500
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Outbox 적체 {{ $value }}건"
```