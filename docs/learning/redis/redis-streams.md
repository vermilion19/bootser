# Redis Streams - 메시지 스트리밍 & Pub/Sub vs Streams vs Kafka 비교

## 1. Redis Streams란?

Redis 5.0에서 추가된 **append-only log 자료구조**. 메시지를 영구적으로 저장하고, Consumer Group을 통해 Kafka처럼 메시지를 분산 처리할 수 있다.

```
Stream: "orders"

ID(시간-순번)    데이터
1700000001000-0  {userId: 1, product: "A"}
1700000002000-0  {userId: 2, product: "B"}
1700000002000-1  {userId: 3, product: "C"}  ← 같은 ms에 2번째
1700000003000-0  {userId: 4, product: "D"}
                                              ← 여기에 계속 추가
```

---

## 2. 기본 명령어

### 메시지 추가 (XADD)

```bash
# ID 자동 생성 (*): 현재_ms-순번
XADD orders * userId 1 product "Apple" price 1000
# → "1700000001234-0"

# ID 직접 지정 (드물게 사용)
XADD orders 1700000001234-1 userId 2 product "Banana"

# 최대 길이 제한 (오래된 메시지 자동 삭제)
XADD orders MAXLEN ~ 10000 * userId 3 product "Cherry"
# ~ : approximate trimming (성능 우선, 정확히 10000개가 아닐 수 있음)
```

### 메시지 조회 (XREAD)

```bash
# 처음부터 읽기 (0-0: 가장 오래된 ID)
XREAD COUNT 10 STREAMS orders 0-0

# 특정 ID 이후 읽기
XREAD COUNT 10 STREAMS orders 1700000001234-0

# 새 메시지를 블로킹하며 대기 ($ = 현재 마지막 이후)
XREAD COUNT 10 BLOCK 5000 STREAMS orders $
# 5000ms 대기, 새 메시지 오면 즉시 반환

# 여러 스트림 동시 읽기
XREAD COUNT 10 STREAMS orders payments 1700000001234-0 1700000005678-0
```

### 범위 조회 (XRANGE / XREVRANGE)

```bash
# 특정 범위 조회
XRANGE orders 1700000001000-0 1700000002000-0

# - 는 최솟값, + 는 최댓값
XRANGE orders - +          # 전체 조회
XRANGE orders - + COUNT 5  # 최대 5개

# 역순 조회
XREVRANGE orders + - COUNT 5  # 최신 5개
```

### 스트림 정보

```bash
XLEN orders        # 메시지 수
XINFO STREAM orders # 스트림 상세 정보
```

---

## 3. Consumer Group

**여러 Consumer가 메시지를 나눠 처리**하는 기능. Kafka Consumer Group과 유사.

### Consumer Group 생성

```bash
# 스트림 처음($는 현재 마지막)부터 새 메시지만 처리
XGROUP CREATE orders order-processors $

# 스트림 처음(0)부터 처리 (기존 메시지도 처리)
XGROUP CREATE orders order-processors 0

# 스트림이 없으면 자동 생성 (MKSTREAM)
XGROUP CREATE orders order-processors $ MKSTREAM
```

### Consumer Group에서 메시지 읽기 (XREADGROUP)

```bash
# consumer-1이 메시지 가져가기
XREADGROUP GROUP order-processors consumer-1 COUNT 5 STREAMS orders >

# > : 아직 다른 Consumer에게 할당 안 된 새 메시지
# 반환된 메시지는 consumer-1의 PEL(Pending Entry List)에 추가됨
```

```
처리 흐름:
┌─────────────────────────────────────────────┐
│  Stream: "orders"                           │
│  [msg1][msg2][msg3][msg4][msg5]             │
└──────────────────┬──────────────────────────┘
                   │ XREADGROUP
        ┌──────────┴──────────┐
        ▼                     ▼
   consumer-1            consumer-2
  [msg1, msg3]           [msg2, msg4]
   처리 중 (PEL)          처리 중 (PEL)
        │                     │
        ▼ XACK                ▼ XACK
   처리 완료               처리 완료
   (PEL에서 제거)          (PEL에서 제거)
```

### 처리 완료 확인 (XACK)

```bash
# msg1 처리 완료 신호
XACK orders order-processors 1700000001234-0

# 여러 메시지 한 번에 ACK
XACK orders order-processors 1700000001234-0 1700000001235-0
```

---

## 4. Pending Entry List (PEL)

**ACK 받지 못한 메시지 목록**. Consumer 장애 시 재처리를 위해 사용.

### PEL 조회 (XPENDING)

```bash
# Consumer Group 전체 PEL 요약
XPENDING orders order-processors

# 결과:
# 1) 5                       ← 미처리 메시지 수
# 2) "1700000001234-0"       ← 가장 오래된 pending ID
# 3) "1700000001238-0"       ← 가장 최신 pending ID
# 4) 1) "consumer-1" "3"    ← consumer-1이 3개 보유
#    2) "consumer-2" "2"    ← consumer-2가 2개 보유

# 상세 조회 (범위 + Consumer 필터)
XPENDING orders order-processors - + 10
XPENDING orders order-processors - + 10 consumer-1
```

```bash
# 상세 결과 예:
# 1) 1) "1700000001234-0"     ← 메시지 ID
#    2) "consumer-1"           ← 할당된 Consumer
#    3) 120000                 ← 마지막 전달 후 경과 시간 (ms)
#    4) 1                      ← 전달 횟수
```

### 장애 복구 - XAUTOCLAIM / XCLAIM

```bash
# consumer-1이 2분(120000ms) 이상 처리 안 한 메시지를 consumer-2가 인수
XAUTOCLAIM orders order-processors consumer-2 120000 0-0

# 특정 메시지를 특정 Consumer에게 수동 할당
XCLAIM orders order-processors consumer-2 120000 1700000001234-0
```

```
장애 복구 시나리오:
consumer-1 장애 → PEL에 msg1, msg3이 남아 있음
                    │
                    ▼ 다른 Consumer가 XPENDING으로 감지
                    │
                    ▼ XAUTOCLAIM으로 인수
                consumer-2가 msg1, msg3 재처리
                    │
                    ▼ XACK
                처리 완료
```

---

## 5. 실제 사용 패턴

### Spring Data Redis + Streams

```java
// 메시지 발행
@Service
public class OrderEventPublisher {
    private final StringRedisTemplate redisTemplate;

    public void publish(OrderCreatedEvent event) {
        Map<String, String> fields = Map.of(
            "orderId", event.getOrderId().toString(),
            "userId", event.getUserId().toString(),
            "amount", event.getAmount().toString()
        );

        redisTemplate.opsForStream()
            .add("orders", fields);  // XADD orders * ...
    }
}

// Consumer 설정
@Bean
public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
        RedisConnectionFactory factory) {
    StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions
            .builder()
            .pollTimeout(Duration.ofMillis(100))
            .build();

    return StreamMessageListenerContainer.create(factory, options);
}

// Consumer Group 리스너
@StreamListener
public void onMessage(MapRecord<String, String, String> message) {
    String orderId = message.getValue().get("orderId");
    // 처리 로직
    redisTemplate.opsForStream().acknowledge("orders", "order-processors", message.getId());
}
```

---

## 6. Pub/Sub vs Streams vs Kafka 비교

### Redis Pub/Sub

```bash
# 발행
PUBLISH channel "message"

# 구독 (블로킹)
SUBSCRIBE channel

# 패턴 구독
PSUBSCRIBE order.* # order.created, order.updated 등 모두 수신
```

**Pub/Sub 특성:**

```
Publisher ──► Channel ──► Subscriber 1
                    └──► Subscriber 2

- 메시지 저장 없음: Subscriber가 없으면 메시지 소실
- 연결 중인 모든 Subscriber에게 동시 전달 (Fan-out)
- Consumer Group 없음: 모두 같은 메시지를 받음
- 재처리 불가: 과거 메시지 없음
```

### 3가지 비교표

| 항목 | Redis Pub/Sub | Redis Streams | Apache Kafka |
|------|--------------|---------------|-------------|
| **메시지 보존** | 없음 | Redis 메모리에 보존 | 디스크에 장기 보존 |
| **Consumer Group** | 없음 (Fan-out만) | 지원 (경쟁 소비) | 지원 (경쟁 소비) |
| **재처리** | 불가 | 가능 (PEL, XRANGE) | 가능 (offset 되감기) |
| **처리량** | 매우 높음 | 높음 | 매우 높음 |
| **지연** | 마이크로초 | 밀리초 | 밀리초~수십ms |
| **영속성** | 없음 | AOF/RDB 의존 | 디스크 기반 |
| **파티셔닝** | 없음 | 없음 (단일 스트림) | 파티션 기반 병렬화 |
| **복잡도** | 매우 낮음 | 보통 | 높음 |
| **운영 비용** | 없음 (Redis 내장) | 없음 (Redis 내장) | 별도 클러스터 필요 |
| **메시지 크기 제한** | 512MB | 512MB | 기본 1MB (설정 가능) |

### 사용 시나리오별 선택

| 시나리오 | 선택 | 이유 |
|---------|------|------|
| 실시간 알림 (채팅, 라이브 업데이트) | **Pub/Sub** | 즉각 전달, 저장 불필요 |
| 캐시 무효화 신호 전파 | **Pub/Sub** | Fan-out, 보존 불필요 |
| 작업 큐 (Task Queue) | **Streams** | Consumer Group, 재처리, 운영 단순 |
| MSA 서비스 간 이벤트 (소규모) | **Streams** | Kafka 도입 비용 절감 |
| 대용량 이벤트 스트리밍 | **Kafka** | 파티셔닝, 장기 보존, 높은 처리량 |
| 이벤트 재처리 (감사/분석) | **Kafka** | 디스크 기반 장기 보존 |
| 여러 서비스가 독립적으로 소비 | **Kafka** | Consumer Group별 독립 offset |

### 결정 기준

```
메시지 보존이 필요한가?
├── No  → Redis Pub/Sub (실시간 알림, 캐시 무효화)
└── Yes → 처리량과 데이터 규모는?
    ├── 소규모, Kafka 비용 부담 → Redis Streams
    └── 대규모, 장기 보존, 파티셔닝 필요 → Kafka
```

---

## 7. 면접 Q&A

**Q. Redis Streams와 Kafka의 가장 큰 차이는?**
> Redis Streams는 단일 로그(파티셔닝 없음)를 Redis 메모리에 저장하므로 처리량에 한계가 있고 Redis 메모리 크기에 의존한다. Kafka는 파티션 단위로 수평 확장 가능하고 디스크에 장기 보존한다. 소규모 MSA에서 별도 Kafka 클러스터 운영 비용이 부담스럽다면 Redis Streams가 실용적이다.

**Q. PEL이 계속 쌓이면 어떻게 되나?**
> Consumer가 XACK를 보내지 않으면 PEL이 계속 증가해 메모리를 소비하고, 장애 시 재처리 대상이 많아진다. 주기적으로 `XPENDING`으로 모니터링하고, 오래된 메시지를 `XAUTOCLAIM`으로 다른 Consumer에게 위임하거나 처리 로직을 수정해야 한다.

**Q. Redis Pub/Sub로 메시지 유실 없이 알림을 보낼 수 있나?**
> 어렵다. Pub/Sub는 Subscriber가 연결 중일 때만 전달되며 메시지를 저장하지 않는다. 유실 없이 보장하려면 Redis Streams를 사용하거나, Pub/Sub와 함께 DB에 알림 상태를 저장하는 방식을 병행해야 한다. 실무에서는 Kafka나 Streams를 사용하고, Pub/Sub는 순간적인 이벤트(채팅, 라이브 상태 변경)에만 사용한다.
