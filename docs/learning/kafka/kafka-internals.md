# Kafka 아키텍처 & 내부 구조

## 1. Kafka 전체 구조

```
Producer                  Kafka Cluster                    Consumer
                    ┌─────────────────────────┐
┌────────┐          │  ┌────────┐ ┌────────┐  │         ┌──────────────┐
│Producer│──────────┼─►│Broker 1│ │Broker 2│  │─────────│Consumer      │
│        │          │  │        │ │        │  │         │Group A       │
│        │          │  │Broker 3│ │        │  │         └──────────────┘
└────────┘          │  └────────┘ └────────┘  │
                    │                          │         ┌──────────────┐
                    │  ┌─────────────────────┐ │─────────│Consumer      │
                    │  │  ZooKeeper / KRaft  │ │         │Group B       │
                    │  └─────────────────────┘ │         └──────────────┘
                    └─────────────────────────┘
```

### 핵심 구성 요소

| 구성 요소 | 역할 |
|-----------|------|
| **Producer** | 토픽에 메시지 발행 |
| **Broker** | 메시지 저장 및 전달. 여러 Broker가 클러스터 구성 |
| **Topic** | 메시지의 논리적 분류 단위 (이메일의 받은편지함 개념) |
| **Partition** | 토픽을 나눈 물리적 단위. 병렬 처리의 기본 단위 |
| **Consumer** | 토픽에서 메시지 소비 |
| **Consumer Group** | 같은 그룹 내 Consumer들이 파티션을 나눠 처리 |
| **Controller** | 클러스터 메타데이터 관리, Leader Election 담당 (Broker 중 1대) |

---

## 2. Topic / Partition / Offset

### 파티션 구조

```
Topic: "waiting-events"
┌──────────────────────────────────────────────────────┐
│  Partition 0: [msg0][msg1][msg2][msg3][msg4]──────►  │
│               offset 0  1    2    3    4              │
│                                                      │
│  Partition 1: [msg0][msg1][msg2]────────────────►    │
│               offset 0  1    2                       │
│                                                      │
│  Partition 2: [msg0][msg1][msg2][msg3]───────────►   │
│               offset 0  1    2    3                  │
└──────────────────────────────────────────────────────┘
```

- **Offset**: 파티션 내 메시지의 고유 순번. 파티션 내에서만 순서 보장
- **파티션 간 순서 보장 없음**: Partition 0의 msg3이 Partition 1의 msg0보다 나중에 처리될 수 있음
- **같은 키 → 같은 파티션**: 키 기반 파티셔닝으로 특정 데이터의 순서 보장 가능

### Producer와 파티션 결정

```java
// 키 없음 → Round-Robin (기본)
producer.send(new ProducerRecord<>("waiting-events", message));

// 키 있음 → hash(key) % partitionCount
producer.send(new ProducerRecord<>("waiting-events", restaurantId, message));
// restaurantId가 같으면 항상 같은 파티션 → 해당 식당의 이벤트 순서 보장
```

### Consumer Group과 파티션 할당

```
Topic: "waiting-events" (Partition 3개)

Consumer Group A (인스턴스 3개)     → 파티션 1:1 매핑 (최적)
  Consumer A1 → Partition 0
  Consumer A2 → Partition 1
  Consumer A3 → Partition 2

Consumer Group A (인스턴스 2개)     → 일부 Consumer가 여러 파티션 담당
  Consumer A1 → Partition 0, 1
  Consumer A2 → Partition 2

Consumer Group A (인스턴스 4개)     → Consumer 1개가 유휴 상태 (파티션보다 많으면 낭비)
  Consumer A1 → Partition 0
  Consumer A2 → Partition 1
  Consumer A3 → Partition 2
  Consumer A4 → 유휴 (할당 없음)
```

> **핵심**: Consumer 인스턴스를 늘려도 파티션 수를 초과하면 처리량이 늘지 않는다. 병렬 처리 한계 = 파티션 수.

---

## 3. Log Segment 구조

Broker는 각 파티션을 **디스크의 로그 파일**로 저장한다. 단일 파일이 아닌 **세그먼트 단위**로 분할.

```
/var/kafka-logs/waiting-events-0/   (Partition 0의 저장 경로)
├── 00000000000000000000.log         ← 실제 메시지 데이터
├── 00000000000000000000.index       ← offset → 파일 위치 인덱스
├── 00000000000000000000.timeindex   ← timestamp → offset 인덱스
├── 00000000000000001000.log         ← 1000번 offset부터의 세그먼트
├── 00000000000000001000.index
└── 00000000000000001000.timeindex
     ↑ 파일명 = 해당 세그먼트의 첫 번째 offset
```

### .log 파일 구조

```
[batch1 header][record1][record2][record3]
[batch2 header][record4][record5]
[batch3 header][record6]
...
```

- 메시지를 **배치 단위**로 묶어 순차 저장
- 항상 파일 끝에 **Append Only** → 수정/삭제 없음 → 순차 I/O 성능

### .index 파일 (Sparse Index)

```
offset 0    → 파일 위치 0
offset 100  → 파일 위치 4201
offset 200  → 파일 위치 8803
...
```

모든 offset을 저장하지 않고 **듬성듬성(Sparse)** 저장한다.
특정 offset 조회 시: 인덱스에서 근처 위치를 찾고 `.log`에서 순차 탐색.

---

## 4. Kafka가 빠른 이유

### 4-1. 순차 I/O (Sequential I/O)

```
랜덤 I/O (일반 DB)         순차 I/O (Kafka)
┌─┐ ┌─┐ ┌─┐              ┌─────────────────┐
│A│ │C│ │B│  디스크 헤드   │ A B C D E F ... │  항상 끝에 추가
└─┘ └─┘ └─┘  이리저리 이동  └─────────────────┘  헤드 이동 최소
   → 느림                       → HDD도 빠름
```

- HDD의 경우 랜덤 I/O와 순차 I/O 속도 차이: 최대 100배
- Kafka는 **Append-Only** 구조로 항상 순차 쓰기

### 4-2. Page Cache 활용

OS의 **페이지 캐시**를 통해 메시지를 메모리에서 직접 전달.

```
일반적인 파일 전송:
디스크 → [OS 페이지 캐시] → [JVM 힙] → [소켓 버퍼] → 네트워크
                              ↑ 불필요한 복사

Kafka (Zero-Copy):
디스크 → [OS 페이지 캐시] ──────────────► 네트워크
                             sendfile() 시스템 콜
                             커널 공간에서 직접 전달
```

- JVM 힙을 거치지 않아 **GC 부담 없음**
- Producer가 방금 보낸 메시지는 페이지 캐시에 있으므로 Consumer가 거의 즉시 수신

### 4-3. Zero-Copy (sendfile)

```java
// 일반 방식: 4번 복사
FileChannel.read() → Java 힙 버퍼
Java 힙 버퍼 → 소켓 버퍼 (write)

// Zero-Copy: 커널이 직접 처리 (2번 복사)
FileChannel.transferTo(socketChannel)
→ OS가 페이지 캐시 → 소켓 버퍼 직접 복사 (커널 공간 내)
```

### 4-4. 배치 처리와 압축

```
Producer → [msg1, msg2, msg3, ...] 배치로 묶어 전송
         → 네트워크 요청 횟수 감소
         → lz4/zstd 압축으로 전송량 감소
         → Broker도 배치 단위로 디스크에 저장
```

---

## 5. Replication (복제)

### Leader / Follower 구조

```
Partition 0 (replicas=3)

Broker 1: [Leader]   ← Producer/Consumer가 직접 통신
Broker 2: [Follower] ← Leader로부터 복제
Broker 3: [Follower] ← Leader로부터 복제

ISR (In-Sync Replica): Leader를 따라잡고 있는 복제본 집합
현재 ISR = {Broker1, Broker2, Broker3}
```

### 메시지 발행 흐름 (acks=all)

```
Producer
  │ send("hello")
  ▼
Leader (Broker 1)
  │ 로컬 저장
  │ Follower들에게 복제 요청
  ├──► Follower (Broker 2): 복제 완료 → ACK
  └──► Follower (Broker 3): 복제 완료 → ACK
  │
  │ ISR 전원 ACK 확인 (min.insync.replicas=2 충족)
  ▼
Producer에게 ACK 전송 → 발행 완료
```

### Leader Election

```
[Broker 1 (Leader) 장애]
    │
    ▼
Controller가 장애 감지
    │
    ▼
ISR 목록에서 새 Leader 선출 (Broker 2)
    │
    ▼
메타데이터 업데이트 → Producer/Consumer는 새 Leader로 재연결
```

> **min.insync.replicas**: Producer가 acks=all로 보낼 때, 최소 이 수만큼의 ISR이 있어야 발행 성공. Broker 3대에서 min.insync.replicas=2이면 1대 장애 허용.

---

## 6. ZooKeeper vs KRaft

### ZooKeeper 방식 (Kafka 2.x)

```
┌──────────────────┐     ┌───────────────────┐
│   ZooKeeper      │     │   Kafka Cluster   │
│  (외부 의존성)    │◄────│   Controller      │
│  - 메타데이터 저장│     │   Broker 1        │
│  - Controller 선출│     │   Broker 2        │
└──────────────────┘     └───────────────────┘
```

- 운영 복잡도 높음 (ZooKeeper 클러스터 별도 관리)
- Controller 장애 시 ZooKeeper를 통한 재선출 → 지연 발생

### KRaft 방식 (Kafka 3.3+, 기본값)

```
┌───────────────────────────────────┐
│         Kafka Cluster             │
│  Controller 1 (Raft 투표로 선출)  │
│  Controller 2                     │
│  Broker 1                         │
│  Broker 2                         │
└───────────────────────────────────┘
```

- ZooKeeper 의존성 제거 → 배포/운영 단순화
- 메타데이터를 Kafka 내부 토픽(`__cluster_metadata`)에 저장
- Leader Election 속도 향상 (Raft 합의 알고리즘)
- 수십만 파티션 지원 가능 (ZooKeeper 방식은 수만 파티션 한계)

---

## 7. 면접 Q&A

**Q. Kafka가 RDB나 다른 MQ보다 빠른 이유는?**
> 순차 I/O(Append-Only 로그), Page Cache 활용, Zero-Copy(sendfile), 배치+압축 처리 4가지가 핵심이다. 특히 Zero-Copy로 JVM 힙을 거치지 않아 GC 부담이 없고, OS 페이지 캐시 덕분에 막 발행된 메시지는 디스크가 아닌 메모리에서 Consumer에게 전달된다.

**Q. 파티션이 많을수록 좋은가?**
> 아니다. 파티션이 많으면 ① Controller의 메타데이터 관리 부담 증가 ② Leader Election 시 지연 ③ 파일 디스크립터 소비 증가 ④ 끝 세그먼트를 제외한 나머지는 페이지 캐시에서 밀릴 수 있어 성능 저하. 처리량을 측정하고 필요한 만큼만 설정한다.

**Q. Consumer가 파티션보다 많으면?**
> 초과된 Consumer는 파티션을 할당받지 못해 유휴 상태가 된다. 처리량이 늘지 않고 자원만 낭비. 단, 빠른 Failover 용도로 예비 Consumer를 두는 경우는 있다.

**Q. Kafka의 순서 보장 범위는?**
> **파티션 내에서만** 순서를 보장한다. 같은 키를 가진 메시지는 같은 파티션으로 가므로 키 단위의 순서는 보장된다. 토픽 전체의 전역 순서 보장이 필요하다면 파티션을 1개로 설정해야 하지만, 이는 병렬 처리를 포기하는 것이다.
