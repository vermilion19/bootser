# Kafka Producer 심화 - 배치, 압축, 성능 최적화

## 1. Producer 내부 구조

```
Producer.send(record)
        │
        ▼
┌───────────────────────────────────────┐
│          RecordAccumulator            │
│                                       │
│  Partition 0: [batch: msg1, msg2]     │
│  Partition 1: [batch: msg3]           │
│  Partition 2: [batch: msg4, msg5, msg6]│
└───────────────┬───────────────────────┘
                │  linger.ms 경과 or batch.size 충족
                ▼
┌───────────────────────────────────────┐
│            Sender Thread              │
│  (백그라운드에서 Broker로 전송)        │
└───────────────┬───────────────────────┘
                │
                ▼
           Kafka Broker
```

- `send()`는 즉시 반환 (비동기). 실제 전송은 Sender 스레드가 담당
- **RecordAccumulator**: 파티션별 배치 버퍼. 배치가 차거나 `linger.ms`가 경과하면 전송

---

## 2. linger.ms & batch.size 상호작용

### 기본 설정값

```yaml
spring.kafka.producer:
  properties:
    linger.ms: 0        # 기본: 배치 차기를 기다리지 않고 즉시 전송
    batch.size: 16384   # 기본: 16KB (배치 최대 크기)
```

### 동작 원리

```
[linger.ms=0, 트래픽 낮을 때]
send(msg1) → 즉시 전송 (배치 1개)
send(msg2) → 즉시 전송 (배치 1개)
send(msg3) → 즉시 전송 (배치 1개)
→ 네트워크 요청 3번, 효율 낮음

[linger.ms=10ms, 트래픽 낮을 때]
send(msg1) → 10ms 대기
send(msg2) → (배치에 추가)
send(msg3) → (배치에 추가)
10ms 경과 → [msg1, msg2, msg3] 한번에 전송
→ 네트워크 요청 1번, 효율 높음

[batch.size=16384 먼저 충족되면]
배치가 16KB 가득 참 → linger.ms 경과 안 해도 즉시 전송
→ 처리량 높은 경우 linger.ms보다 batch.size가 더 자주 동작
```

### 트래픽 수준별 권장 설정

| 상황 | linger.ms | batch.size | 설명 |
|------|-----------|------------|------|
| 처리량 최우선 | 20~100ms | 65536 (64KB) | 배치를 크게, 더 오래 모음 |
| 지연 최우선 | 0~5ms | 16384 (기본) | 가능한 빨리 전송 |
| 균형 (권장) | 5~20ms | 32768 (32KB) | 일반적인 실무 설정 |

```yaml
# 처리량 우선 설정 예시 (Booster Outbox Relay 등)
spring.kafka.producer:
  properties:
    linger.ms: 20
    batch.size: 65536
    buffer.memory: 67108864  # 64MB (전체 버퍼 크기)
```

---

## 3. 압축 (Compression)

배치 단위로 압축 → 네트워크 비용, 디스크 비용 절감.

```
Producer 측:
[msg1, msg2, msg3] → 압축 → [compressed batch] → Broker로 전송

Broker 측:
compressed 상태로 디스크 저장 (압축 해제 안 함)

Consumer 측:
[compressed batch] 수신 → 압축 해제 → [msg1, msg2, msg3] 처리
```

> CPU 비용: Producer (압축) + Consumer (해제), Broker는 투명하게 통과

### 압축 알고리즘 비교

| 알고리즘 | 압축률 | 속도 | CPU 비용 | 권장 상황 |
|----------|--------|------|----------|-----------|
| `none` | - | - | 없음 | 개발/테스트 |
| `gzip` | 높음 | 느림 | 높음 | 처리량 적고 저장 공간 중요할 때 |
| `snappy` | 보통 | 빠름 | 낮음 | 범용, CPU 여유 없을 때 |
| `lz4` | 보통 | 매우 빠름 | 낮음 | **처리량 중요, 권장** |
| `zstd` | 높음 | 빠름 | 보통 | **압축률과 속도 균형, 최신 권장** |

```yaml
spring.kafka.producer:
  compression-type: lz4   # 또는 zstd
```

### 압축 효율 극대화 조건

```
배치가 클수록 압축률 높아짐

linger.ms + batch.size 조합으로 배치를 크게 만들수록
→ 압축 알고리즘이 더 많은 중복 패턴 발견
→ 압축률 향상

실제 효과:
JSON 메시지 100개 배치: 원본 50KB → lz4 압축 → 12KB (76% 절감)
JSON 메시지 10개 배치:  원본 5KB  → lz4 압축 → 2KB  (60% 절감)
```

---

## 4. buffer.memory & max.block.ms

```
Producer.send() → RecordAccumulator 버퍼에 적재
                          │
                    버퍼가 가득 차면?
                          │
                max.block.ms(기본 60초) 동안 대기
                          │
                    여전히 가득 차면?
                          │
                BufferExhaustedException 발생
```

```yaml
spring.kafka.producer:
  properties:
    buffer.memory: 33554432   # 32MB (기본값)
    max.block.ms: 60000       # 60초 대기 후 예외 (기본값)
```

**버퍼 고갈 원인과 대응:**

```
버퍼 고갈 원인:
발행 속도 > 전송 속도

대응:
1. buffer.memory 증가
2. linger.ms 줄여 더 빨리 전송
3. Broker 성능 확인 (네트워크/디스크 병목)
4. 압축 적용으로 전송량 감소
```

---

## 5. in-flight 요청과 순서 보장

```yaml
max.in.flight.requests.per.connection: 5  # 기본값
```

- ACK를 받기 전에 동시에 Broker에 보낼 수 있는 요청 수
- 5개 중 2번째 요청만 실패 후 재시도되면 순서가 바뀔 수 있음

```
순서 문제 발생 시나리오:
req1 (msg1) → 성공
req2 (msg2) → 실패
req3 (msg3) → 성공
req2 재시도  → 성공

Broker에 저장된 순서: msg1, msg3, msg2 ← 순서 뒤바뀜!
```

**순서 보장이 필요한 경우:**

```yaml
spring.kafka.producer:
  properties:
    max.in.flight.requests.per.connection: 1  # 순서 보장 (처리량 감소)
    # 또는
    enable.idempotence: true   # 멱등성 활성화 시 자동으로 순서 보장 (최대 5)
```

---

## 6. Producer 설정 전체 권장값

```yaml
spring:
  kafka:
    producer:
      acks: all                          # 메시지 유실 방지
      retries: 3                         # 재시도 횟수
      compression-type: lz4              # 압축으로 처리량 향상
      properties:
        enable.idempotence: true         # 멱등성 (중복 방지)
        max.in.flight.requests.per.connection: 5  # 멱등성 시 5까지 허용
        linger.ms: 10                    # 배치 효율 향상
        batch.size: 32768                # 32KB 배치
        buffer.memory: 33554432          # 32MB 버퍼
        delivery.timeout.ms: 120000      # 전체 전달 타임아웃 2분
        request.timeout.ms: 30000        # 요청 타임아웃 30초
        retry.backoff.ms: 1000           # 재시도 간격 1초
```

---

## 7. 면접 Q&A

**Q. linger.ms를 늘리면 무조건 좋은가?**
> 처리량은 높아지지만 지연(latency)이 증가한다. 실시간 알림처럼 즉시성이 중요한 경우엔 0~5ms, 배치 처리나 대용량 로그 수집처럼 처리량이 중요한 경우엔 20~100ms로 설정한다. 비즈니스 SLA에 맞게 선택해야 한다.

**Q. 압축은 Broker에서도 CPU를 쓰나?**
> 기본적으로 Broker는 Producer가 보낸 압축 배치를 그대로 디스크에 저장하므로 압축/해제 CPU 비용이 없다. 단, `compression.type`이 Broker와 Producer 간에 다르게 설정되면 Broker가 재압축해야 하므로 동일하게 맞추는 것이 중요하다.

**Q. `buffer.memory`를 크게 설정하면 무조건 좋은가?**
> JVM 힙 메모리의 일부를 차지하므로 GC 대상이 된다. 버퍼 크기만큼 힙 여유가 있어야 하고, `buffer.memory`가 크면 Producer가 Broker 장애를 감추고 메모리에 쌓다가 한꺼번에 OOM이 날 수 있다. `max.block.ms`를 함께 관리해 과도한 적체를 빠르게 감지하는 것이 중요하다.
