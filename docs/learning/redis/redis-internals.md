# Redis 내부 구조 - 싱글 스레드 & 자료구조 인코딩

## 1. 싱글 스레드 모델

Redis는 **명령어 처리를 단일 스레드**로 수행한다.

```
클라이언트 A ─┐
클라이언트 B ─┤──► [이벤트 루프] ──► [명령어 큐] ──► 순차 처리
클라이언트 C ─┘
```

### 왜 싱글 스레드인가?

```
멀티 스레드의 문제:
- 공유 자료구조에 Lock 필요
- Context Switch 비용
- Deadlock 위험

Redis 싱글 스레드의 장점:
- Lock 없음 → 원자성 자동 보장
- Context Switch 없음
- 구현 단순성
- 메모리 접근이 주 작업 → CPU 병목 아님
```

> **핵심**: Redis는 메모리 기반이라 I/O가 아닌 CPU가 병목이 될 거라 생각하지만, 실제로는 네트워크 I/O가 병목. 싱글 스레드여도 초당 수십만 op/s 처리 가능.

---

## 2. I/O Multiplexing (epoll)

싱글 스레드지만 수천 개 클라이언트를 동시에 처리하는 원리.

### 기존 방식 vs epoll

```
[Thread-per-Connection 방식]
클라이언트 1000개 → 스레드 1000개 → 메모리 낭비 + Context Switch 폭발

[epoll 방식]
클라이언트 1000개 → 커널 epoll이 이벤트 감시 → 이벤트 발생한 소켓만 알림
→ 단일 스레드가 이벤트 있는 소켓만 처리
```

### epoll 동작 원리

```c
// 의사 코드 (실제 Redis ae.c 기반)
epoll_create();  // epoll 인스턴스 생성

while (true) {
    events = epoll_wait(timeout);  // 이벤트 발생 대기 (블로킹)
    for each event in events {
        if (readable) processRead(fd);
        if (writable) processWrite(fd);
    }
}
```

```
클라이언트 A: "GET key" 전송
                │
                ▼
          epoll이 감지 (A 소켓에 읽기 이벤트)
                │
                ▼
          이벤트 루프: A 소켓에서 명령 읽기
                │
                ▼
          명령 실행 (GET key → 메모리 조회)
                │
                ▼
          결과를 A 소켓에 쓰기
                │
          다음 이벤트로 이동
```

### 지원 Multiplexer (OS별)

| OS | 사용 방식 |
|----|----------|
| Linux | **epoll** (가장 효율적) |
| macOS | kqueue |
| Windows | select (제한적) |
| 기타 | select |

---

## 3. Redis 이벤트 루프 구조

```
┌─────────────────────────────────────────────────────┐
│                   Event Loop (ae.c)                  │
│                                                      │
│  ┌──────────────┐    ┌────────────────────────────┐  │
│  │  Time Events │    │    File Events (I/O)        │  │
│  │              │    │                             │  │
│  │ - serverCron │    │ - 클라이언트 명령 읽기       │  │
│  │   (100ms)    │    │ - 결과 쓰기                 │  │
│  │ - 만료 키 검사│    │ - 새 연결 accept            │  │
│  │ - RDB/AOF    │    │ - 복제 데이터 전송           │  │
│  └──────────────┘    └────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### serverCron 주요 작업

```
serverCron (100ms 주기):
├── 만료 키 랜덤 샘플링 후 삭제 (Active Expiry)
├── 메모리 사용량 업데이트
├── 통계 정보 수집 (INFO 명령 데이터)
├── AOF 버퍼 플러시
├── RDB 저장 조건 확인
└── Slow Log 정리
```

### Active vs Passive Expiry

```
Passive Expiry (게으른 삭제):
접근 시 만료 확인 → 만료됐으면 삭제
문제: 접근 안 하면 영원히 메모리 점유

Active Expiry (주기적 삭제):
serverCron이 100ms마다 만료 키를 랜덤 샘플링(기본 20개)
→ 25% 이상 만료됐으면 다시 샘플링 (반복)
→ 25% 미만이면 다음 주기로

결과: 만료 키가 메모리에 오래 남아 있지 않도록 보장
단, 즉시 삭제는 아님 → 메모리 계획 시 고려
```

---

## 4. Redis 6.0+ I/O Threads

Redis 6.0부터 **I/O 멀티스레딩** 지원 (명령 처리는 여전히 싱글 스레드).

```
[기존 6.0 미만]
단일 스레드: 읽기 → 파싱 → 실행 → 쓰기

[6.0+ I/O Threads]
I/O Thread 1: 소켓 읽기  ─┐
I/O Thread 2: 소켓 읽기  ─┤──► Main Thread: 명령 실행 ──► I/O Threads: 결과 쓰기
I/O Thread 3: 소켓 읽기  ─┘
```

```
# redis.conf
io-threads 4               # I/O 스레드 수 (CPU 코어의 절반 권장)
io-threads-do-reads yes    # 읽기도 멀티스레드로 처리
```

> **효과**: 네트워크 대역폭이 병목인 경우 처리량 2~3배 향상. CPU 연산이 병목인 경우 효과 미미.

---

## 5. 자료구조 내부 인코딩

Redis는 데이터 크기에 따라 **자동으로 내부 인코딩을 전환**한다.

### String (SDS - Simple Dynamic String)

```c
// C의 char* 대신 SDS 사용
struct sdshdr {
    int len;     // 현재 길이 (O(1) strlen)
    int free;    // 여유 공간
    char buf[];  // 실제 데이터
};
```

| 인코딩 | 조건 | 설명 |
|--------|------|------|
| `int` | 정수값 | 포인터 자체에 정수 저장 |
| `embstr` | 44바이트 이하 | SDS + redisObject 연속 할당 |
| `raw` | 44바이트 초과 | SDS 별도 할당 |

```bash
SET key 123
OBJECT ENCODING key  # → "int"

SET key "hello"
OBJECT ENCODING key  # → "embstr"

SET key "a very long string over 44 bytes ..."
OBJECT ENCODING key  # → "raw"
```

### List

| 인코딩 | 조건 | 설명 |
|--------|------|------|
| `listpack` | 요소 128개 이하 & 각 64바이트 이하 | 연속 메모리 배열 (Redis 7.0+) |
| `quicklist` | 초과 시 | 이중 연결 리스트 + 각 노드가 listpack |

```
listpack (소규모):
[entry1][entry2][entry3]  ← 연속 메모리, 캐시 친화적

quicklist (대규모):
[listpack] ↔ [listpack] ↔ [listpack]  ← 이중 연결 리스트
```

### Hash

| 인코딩 | 조건 | 설명 |
|--------|------|------|
| `listpack` | 필드 128개 이하 & 각 64바이트 이하 | key-value 쌍을 연속 배열로 |
| `hashtable` | 초과 시 | 일반 해시 테이블 (O(1) 접근) |

```bash
HSET small f1 v1 f2 v2
OBJECT ENCODING small  # → "listpack"

# 필드 128개 초과하면
OBJECT ENCODING large  # → "hashtable"
```

### Set

| 인코딩 | 조건 | 설명 |
|--------|------|------|
| `listpack` | 128개 이하 & 정수/64바이트 이하 문자열 | |
| `intset` | 모두 정수 & 512개 이하 | 정렬된 정수 배열 |
| `hashtable` | 초과 시 | 일반 해시 테이블 |

```bash
SADD intonly 1 2 3 4
OBJECT ENCODING intonly  # → "intset"

SADD mixed 1 "abc" 2
OBJECT ENCODING mixed  # → "listpack"
```

### Sorted Set (ZSet)

| 인코딩 | 조건 | 설명 |
|--------|------|------|
| `listpack` | 128개 이하 & 각 64바이트 이하 | score+member 쌍 저장 |
| `skiplist` | 초과 시 | Skip List + Hash Table 조합 |

```
Skip List 구조:
Level 4: [head] ──────────────────────────── [tail]
Level 3: [head] ──────── [node3] ──────────── [tail]
Level 2: [head] ── [node2] ── [node3] ──────── [tail]
Level 1: [head] ─ [node1] ─ [node2] ─ [node3] ─ [tail]

O(log N) 탐색 + O(1) 범위 조회
```

### 인코딩 전환 임계값 설정

```
# redis.conf
hash-max-listpack-entries 128
hash-max-listpack-value 64
zset-max-listpack-entries 128
zset-max-listpack-value 64
list-max-listpack-size -2      # 8KB 제한
set-max-intset-entries 512
```

> **메모리 최적화**: 소규모 데이터는 listpack/intset이 메모리 효율적. 임계값을 높이면 메모리 절약되지만 조회 속도가 선형으로 저하됨. 벤치마크 후 결정.

---

## 6. 메모리 관리

### jemalloc

Redis는 glibc malloc 대신 **jemalloc**을 사용한다.

```
장점:
- 메모리 단편화 감소
- Arena 기반 멀티스레드 환경에서 경쟁 없음
- 실제 사용량 추적 용이
```

```bash
INFO memory
# used_memory_human: 1.23M          ← Redis가 사용 중인 메모리
# mem_fragmentation_ratio: 1.5      ← 1.5 이상이면 단편화 심각
# used_memory_rss_human: 1.85M      ← OS가 Redis에 할당한 실제 메모리
```

### 메모리 단편화 해결

```bash
# Redis 4.0+: 자동 단편화 정리
CONFIG SET activedefrag yes
CONFIG SET active-defrag-ignore-bytes 100mb  # 단편화 100MB 초과 시 시작
CONFIG SET active-defrag-threshold-lower 10  # 단편화율 10% 초과 시 시작
```

---

## 7. 면접 Q&A

**Q. Redis가 싱글 스레드인데 어떻게 빠른가?**
> 메모리 기반이라 I/O가 아닌 네트워크가 병목이다. epoll을 통한 I/O Multiplexing으로 단일 스레드가 수천 개의 클라이언트 소켓을 동시에 감시한다. 메모리 접근은 나노초 단위이므로 CPU가 병목이 되지 않는다. 실제로 초당 수십만 명령을 처리할 수 있다.

**Q. Redis 6.0의 I/O 멀티스레딩은 명령도 병렬 처리하는가?**
> 아니다. 소켓 읽기(파싱)와 결과 쓰기만 멀티스레드로 처리하고, 실제 명령 실행은 여전히 Main Thread에서 순차 처리한다. 따라서 원자성 보장은 그대로다. I/O Threads는 네트워크 대역폭 병목을 해결하기 위한 것이다.

**Q. listpack vs ziplist 차이는?**
> ziplist는 Redis 7.0 이전의 인코딩으로, 연속 메모리에 엔트리를 저장하지만 중간 삽입 시 연쇄 업데이트(cascade update) 문제가 있었다. listpack은 Redis 7.0에서 이를 개선한 대체 구조로, 각 엔트리가 자신의 이전 엔트리 길이 대신 자신의 길이만 저장해 연쇄 업데이트 문제를 제거했다.

**Q. Hash를 작게 쪽개서 저장하면 왜 메모리가 절약되나?**
> Hash가 128개 이하의 필드를 가지면 listpack으로 저장된다. listpack은 연속 메모리 배열로 포인터 오버헤드가 없다. 반면 hashtable은 각 버킷에 포인터가 있어 오버헤드가 크다. 대형 Hash 1개보다 소형 Hash 여러 개가 메모리 효율적이며, 이것이 Hash-slot 패턴(userId를 1000으로 나눈 나머지를 키 접두어로 사용)의 원리다.
