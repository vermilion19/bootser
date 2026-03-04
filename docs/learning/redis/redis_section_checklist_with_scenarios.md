# Redis 실무/면접 체크리스트 (목차 기반 확장판)

> 이 문서는 **제공된 강의 목차**를 바탕으로 “강의에서 다룰 법한 핵심”을 **실무 관점**으로 확장 정리한 것입니다.  
> Redis 버전/배포(Managed/On-prem), 클라이언트 라이브러리에 따라 세부 동작/옵션은 다를 수 있으니, 운영 적용 전에는 반드시 사내 표준/버전 문서를 확인하세요.

---

## 목차
- [섹션 2(추정): Redis의 정체 + Cluster 설계](#섹션-2추정-redis의-정체--cluster-설계)
- [섹션 3: 기본 자료구조/명령어/실습](#섹션-3-기본-자료구조명령어실습)
  - [String](#1-string-캐시-토큰-카운터-락의-기본)
  - [List (+ Blocking)](#2-list--blocking-간단-큐대기열)
  - [Set](#3-set-중복-제거-멱등-처리-체크)
  - [Hash](#4-hash-필드-저장-키-오버헤드-절감)
  - [Sorted Set](#5-sorted-set-랭킹topn스케줄링)
- [섹션 4: 실무 주의사항 + Persistence](#섹션-4-실무-주의사항--persistence)
  - [Keys/스캔/삭제/BigKey/HotKey](#1-keys스캔삭제bigkeyhotkey)
  - [Persistence 3가지 패턴](#2-persistence-3가지-패턴-추정)
- [섹션 5: 전문가 기능](#섹션-5-전문가-기능)
  - [Pub/Sub](#1-pubsub-느슨한-결합-실시간-알림)
  - [Transaction(WATCH/MULTI/EXEC)](#2-transactionwatchmultiexec-acid-오해-정리)
  - [Pipelining](#3-pipelining-rtt-절감)
  - [Lua Script](#4-lua-script-원자성--rtt-한-방)
  - [Streams](#5-streams-영속-메시징--consumer-group)
- [섹션 6: 환경설정 Master](#섹션-6-환경설정-master)
  - [Cache Stampede 대응](#1-cache-stampede-대응-패턴)
  - [Redis Cluster 내부/Hash Slot/Rebalance](#2-redis-cluster-내부hash-slotrebalance)
  - [종합 구성 실습 체크리스트](#3-종합-구성-실습-체크리스트)
- [“상황 → 선택 → 왜?” 시나리오 라이브러리](#상황--선택--왜-시나리오-라이브러리)
- [면접용 한 장 치트시트](#면접용-한-장-치트시트)

---

# 섹션 2(추정): Redis의 정체 + Cluster 설계

## Redis를 한 문장으로
- Redis는 **인메모리 기반**의 Key-Value 저장소이면서, 동시에 **자료구조 서버**(List/Set/ZSet/Hash/Stream)로서 “캐시/락/큐/랭킹/메시징” 문제를 빠르게 푸는 도구입니다.

## 왜 대부분 회사가 쓰나 (실무 관점)
- **초저지연**: 읽기/쓰기 latency를 DB보다 낮게 만들기 쉬움(특히 캐시)
- **원자 연산**: INCR, SET NX, ZINCRBY 등 동시성 문제를 단순화
- **TTL**: 만료 기반 캐시/세션 관리가 쉽다
- **자료구조**: 랭킹/중복제거/큐 등을 DB 스키마 없이 빠르게 구현

## “클러스터”라는 말이 헷갈리기 쉬운 이유
Redis에서 흔히 말하는 “클러스터”는 최소 3가지가 섞입니다.

### 1) Standalone (단일 인스턴스)
- 장점: 단순/싸다/빠르다
- 단점: 장애 시 서비스 영향 큼(단일 실패점)

### 2) Replication (Primary-Replica)
- 목적: **읽기 분산**, 장애 시 복구 옵션 확보
- 특징: Primary에 쓰고 Replica가 복제(일반적으로 비동기)
- 주의: “완전한 동기 복제”로 생각하면 위험. 장애 순간에는 일부 유실 가능성을 전제로 설계.

### 3) Sentinel 기반 HA (자동 failover)
- 목적: **자동 장애 조치(HA)**  
- Sentinel이 primary 장애 감지 → replica를 승격 → 클라이언트가 새 primary로 붙도록 유도
- 주의: failover 중 순간적인 오류/재시도 설계 필요

### 4) Redis Cluster 모드 (샤딩 + 확장)
- 목적: **수평 확장(샤딩)** + (일부) HA
- 특징: 키를 해시 슬롯(0~16383)에 매핑해 여러 노드로 분산 저장
- 주의: 멀티키 연산/트랜잭션은 **동일 슬롯** 제약이 자주 발생 → 키 설계가 운영 품질을 좌우

---

# 섹션 3: 기본 자료구조/명령어/실습

## 1) String: 캐시, 토큰, 카운터, 락의 기본

### 언제 쓰나
- 캐시 값(JSON/blob), 세션 토큰, 단일 상태값, 카운터, 플래그(기능 토글), 간단 락

### 대표 명령어
- 기본 CRUD: `SET`, `GET`, `DEL`, `MGET`
- TTL: `EXPIRE`, `TTL`, `PTTL`
- 원자 카운터: `INCR`, `INCRBY`, `DECR`
- 조건부 set: `SET key val NX`(없을 때만), `SET key val XX`(있을 때만)
- TTL 포함 set: `SET key val EX 60` 또는 `SET key val PX 1500`

### 실무 패턴 1: Cache-aside
```bash
GET product:42
# miss면 DB 조회 후
SET product:42 '{"id":42,"name":"..."}' EX 60
```

**왜?**
- 가장 흔한 캐시 패턴. 애플리케이션이 캐시 히트/미스를 제어한다.

**주의**
- stampede(동시 미스) 대비가 없으면 DB가 터질 수 있음 → 섹션 6 참고

### 실무 패턴 2: 원자 카운터 + TTL (간단 rate limit의 기반)
```bash
INCR rl:ip:1.2.3.4
EXPIRE rl:ip:1.2.3.4 60
```
**주의**
- 위 두 줄은 “원자적으로 묶이지 않음”. 동시에 여러 요청이 들어오면 TTL이 꼬일 수 있어 Lua로 한 방에 처리하는 패턴이 자주 등장(시나리오 참고).

### 실무 패턴 3: 간단 락(뮤텍스)
```bash
SET lock:order:42 <token> NX PX 3000
```
- 성공이면 락 획득, 실패면 다른 프로세스가 락을 가진 상태

**주의(매우 중요)**  
- 락 해제 시에는 “내가 잡은 락인지” 확인 후 해제해야 함(토큰 비교) → Lua로 원자 처리 권장(시나리오 참고).  
- 락은 설계 난이도가 높고, “분산락 = 만능”이 아님. 사용 범위를 최소화하는 게 좋음.

---

## 2) List (+ Blocking): 간단 큐/대기열

### 언제 쓰나
- “유실/중복이 어느 정도 허용되는” 간단 작업 큐, 순서 있는 대기열, 임시 버퍼
- **정확한 재처리/ACK**가 필요하면 Streams로(섹션 5)

### 대표 명령어
- push/pop: `LPUSH`, `RPUSH`, `LPOP`, `RPOP`
- 블로킹 pop: `BLPOP`, `BRPOP`
- 조회/길이: `LRANGE`, `LLEN`

### 기본 큐 패턴(FIFO)
- Producer: `RPUSH queue job`
- Consumer: `BLPOP queue 0` (0이면 무한대기)

```bash
RPUSH jobs '{"id":1}'
BLPOP jobs 0
```

### “우선순위 큐”를 List로 만드는 전형적 방법
- 큐를 여러 개로 나눈다: `jobs:high`, `jobs:low`
- 컨슈머는 high를 먼저 pop 시도

```bash
BLPOP jobs:high jobs:low 0
```

### List 큐의 핵심 한계(면접 포인트)
- 기본적으로 **ACK/재처리/가시성 타임아웃**이 없다.
- 컨슈머가 pop 후 죽으면 작업이 “사라졌다”로 처리될 수 있음(유실) 또는 애플리케이션 방식에 따라 중복 발생.
- 그래서 “업무상 중요한 비동기 작업”은 Streams/Kafka/RabbitMQ 등의 모델을 검토한다.

---

## 3) Set: 중복 제거, 멱등 처리 체크

### 언제 쓰나
- 중복 이벤트 제거, idempotency key 저장, 특정 집합 membership 체크

### 대표 명령어
- 추가/중복 판정: `SADD` (반환값 1이면 신규, 0이면 이미 존재)
- 존재 확인: `SISMEMBER`
- 삭제/개수: `SREM`, `SCARD`
- 안전한 점진 탐색: `SSCAN`

### 실무 패턴: “이 이벤트 처리했나?”
```bash
SADD processed:event_ids evt-123    # 1이면 처음 처리
# 반환이 0이면 중복이므로 스킵
```

### 주의사항
- Set이 무한정 커지면 메모리 폭발
  - 날짜별로 분리: `processed:2026-03-03`
  - TTL 적용: `EXPIRE processed:2026-03-03 604800`(7일)
- 대규모에서 `SMEMBERS`로 전체 조회는 위험(메모리/지연) → `SSCAN`

---

## 4) Hash: 필드 저장, 키 오버헤드 절감

### 언제 쓰나
- “한 엔티티에 여러 속성”이 있고 부분 업데이트가 잦을 때
- JSON 통째로 넣는 String 대비 **부분 업데이트/부분 조회**에 강함

### 대표 명령어
- CRUD: `HSET`, `HGET`, `HMGET`, `HDEL`
- 존재/개수: `HEXISTS`, `HLEN`
- 카운트: `HINCRBY`
- 점진 탐색: `HSCAN`

### 실무 예제: 사용자 프로필
```bash
HSET user:123 name "kim" age "28" email "a@b.com"
HMGET user:123 name email
```

### 주의사항
- `HGETALL`은 필드 수가 많으면 비싸다 → 필요한 필드만 `HMGET`
- Hash도 “빅 해시”가 되면 백업/복제/메모리 측면에서 부담이 커진다

---

## 5) Sorted Set: 랭킹/TopN/스케줄링

### 언제 쓰나
- 리더보드(랭킹), 실시간 인기글, 점수 기반 정렬
- “미래 시간에 실행할 작업” 같은 **스케줄링(딜레이 작업)**에도 자주 사용

### 대표 명령어
- 추가/갱신: `ZADD`, `ZINCRBY`
- TopN: `ZREVRANGE key 0 99 WITHSCORES`
- 순위: `ZRANK`, `ZREVRANK`
- 범위 삭제: `ZREMRANGEBYRANK`, `ZREMRANGEBYSCORE`
- 점진 탐색: `ZSCAN`

### 실무 예제 1: 게임 리더보드
```bash
ZINCRBY leaderboard 10 user:123
ZREVRANGE leaderboard 0 9 WITHSCORES
ZREVRANK leaderboard user:123
```

### 실무 예제 2: 딜레이 작업(스케줄러)
- score = 실행 시각(epoch ms), member = job id
```bash
ZADD delay_jobs 1730000000000 job:abc
# 주기적으로 현재시간 이하를 가져와 처리
ZRANGEBYSCORE delay_jobs -inf <now> LIMIT 0 100
```

### 주의사항
- TopN만 유지하려면 주기적으로 잘라내기 필요
- score 설계(동점/시간가중치/부정행위)에서 실무 난이도 상승

---

# 섹션 4: 실무 주의사항 + Persistence

## 1) Keys/스캔/삭제/BigKey/HotKey

### 왜 `KEYS`가 위험한가 (요지)
- 데이터가 많을수록 `KEYS pattern`은 **전체 키 공간을 훑고**, 그동안 Redis가 바빠져 지연이 튄다.
- 운영에서는 **`SCAN`으로 점진 조회**하는 습관이 중요.

### 점진 탐색 패턴
```bash
SCAN 0 MATCH user:* COUNT 1000
# 다음 커서로 반복
```

### 삭제: `DEL` vs `UNLINK`
- `DEL`: 동기 삭제(큰 키면 삭제 시간이 길어질 수 있음)
- `UNLINK`: 백그라운드(비동기) 삭제에 유리한 경우가 많음(대키 삭제 시 레이턴시 보호 목적)

### BigKey/HotKey 체크리스트
- BigKey(큰 값/큰 컬렉션):
  - [ ] 한 키의 value가 수 MB 이상인 캐시(네트워크/복제/지연 악화)
  - [ ] Set/ZSet/Hash/List가 수백만 원소로 비대해진 키
- HotKey(접근이 몰리는 키):
  - [ ] 인기글 top10 키, 전역 카운터, 전역 락 키
  - [ ] TTL 동시 만료로 특정 키에 요청이 집중

**대응 아이디어(간단)**
- BigKey: 쪼개기(샤딩), 보관 정책(TopN 유지), 압축, value 줄이기
- HotKey: 키 샤딩(예: `counter:{shard}`), 캐시 계층화, 요청 합치기(singleflight), TTL jitter

---

## 2) Persistence 3가지 패턴 (추정)

> 강의 제목이 “3가지 패턴”이므로 실무에서 흔한 3가지 분류로 정리합니다.

### 패턴 A: Pure Cache (영속성 최소/없음)
- **원본 데이터는 DB**에 있고 Redis는 임시 캐시
- 장애로 데이터가 날아가도 “다시 채우면 된다”

**적합**
- 상품 상세/프로필 캐시, 일시 토큰, 계산 결과 캐시

**주의**
- 캐시 미스가 한꺼번에 발생하면 DB가 터진다 → stampede 대비 필수

### 패턴 B: RDB 스냅샷 기반
- 일정 주기로 스냅샷(덤프)을 남겨 복구에 사용
- “최신 몇 분/몇 초 데이터 유실 가능성”을 감수하고 단순성을 얻는 방식

**적합**
- 약간의 유실이 허용되는 데이터
- 복구 시점이 “마지막 스냅샷”이어도 괜찮은 경우

### 패턴 C: AOF(append-only file) 기반
- 명령 로그를 쌓아 복구(대체로 RDB보다 촘촘한 복구 가능)
- 디스크 I/O/재작성(rewrite) 등의 운영 이슈가 생길 수 있음

**적합**
- 유실을 더 줄이고 싶을 때(세션/중요 상태 등)
- 다만 완전한 DB 트랜잭션처럼 생각하면 안 됨(애초에 목적이 다름)

---

# 섹션 5: 전문가 기능

## 1) Pub/Sub: 느슨한 결합, 실시간 알림
### 요약
- `PUBLISH`로 채널에 메시지 발행
- `SUBSCRIBE`로 구독한 클라이언트만 즉시 수신
- **메시지 저장이 기본적으로 없다** → 구독자가 없거나 끊기면 유실

### 대표 명령어
```bash
SUBSCRIBE alarm
PUBLISH alarm "hello"
```

### 언제 쓰나
- 웹소켓 브로드캐스트, 간단한 실시간 알림, 임시 이벤트 트리거

### 주의사항
- “큐/내구성 이벤트”로 쓰면 안 됨 → Streams 사용 고려

---

## 2) Transaction(WATCH/MULTI/EXEC): ACID 오해 정리
### 요약
- MULTI/EXEC는 “명령을 묶어 순서대로 실행”하는 도구
- DB의 트랜잭션처럼 자동 롤백/격리 레벨이 있는 모델과는 다르다
- 경쟁 제어는 `WATCH`(optimistic locking)가 핵심

### 패턴: 잔고 차감(낙관적 락)
```bash
WATCH balance:123
GET balance:123
MULTI
DECRBY balance:123 100
EXEC
```

### 주의사항
- WATCH 후에 다른 클라이언트가 값을 바꾸면 EXEC가 실패할 수 있다 → 재시도 로직 필요
- 조건부 로직이 복잡해지면 Lua로 옮기는 경우가 많다

---

## 3) Pipelining: RTT 절감
### 요약
- 여러 명령을 한 번에 보내 왕복 지연(RTT)을 줄여 처리량/지연을 개선
- 원자성/일관성을 보장하는 기능이 아니라 **네트워크 최적화**

### 언제 쓰나
- 대량 GET/HGET/ZRANGE 등 “조회 다발”에서 체감 효과 큼

### 주의사항
- 응답이 한꺼번에 몰려와 클라이언트 메모리 급증 가능
- 실패/타임아웃 시 “부분 성공” 처리 전략 필요

---

## 4) Lua Script: 원자성 + RTT 한 방
### 요약
- Redis 서버 내부에서 스크립트를 실행
- 조건 검사 + 업데이트를 **원자적으로** 처리 가능
- 네트워크 왕복도 1번으로 줄기 쉬움

### 대표 명령어
- `EVAL`, `EVALSHA`, `SCRIPT LOAD`

### 주의사항(중요)
- Redis는 단일 스레드 이벤트 루프 모델이기 때문에, Lua가 오래 돌면 그 시간 동안 Redis가 막히는 효과가 난다  
  → 긴 루프/대량 처리/큰 스캔 로직을 Lua에 넣지 말 것

---

## 5) Streams: 영속 메시징 + Consumer Group
### 요약
- Pub/Sub의 “유실” 문제를 줄이기 위한 로그/큐 스타일 자료구조
- Consumer group을 통해:
  - 메시지를 분배해서 처리
  - ACK로 처리 완료를 기록
  - 컨슈머 장애 시 pending을 재처리할 수 있음

### 필수 명령어 묶음
```bash
XADD mystream * type order_created orderId 42
XGROUP CREATE mystream mygroup $ MKSTREAM
XREADGROUP GROUP mygroup c1 COUNT 10 BLOCK 2000 STREAMS mystream >
XACK mystream mygroup <id>
XPENDING mystream mygroup
```

### 주의사항
- 스트림이 무한정 커지면 메모리/디스크 부담 → trimming 전략이 필요(예: MAXLEN)
- “정확히 한 번 처리”는 간단히 얻어지지 않는다 → 멱등 처리(중복 허용 설계) 병행

---

# 섹션 6: 환경설정 Master

## 1) Cache Stampede 대응 패턴

### 증상
- TTL 만료 순간, 다수 요청이 동시에 캐시 미스 → DB 폭주 → 장애

### 대응 체크리스트
- [ ] **TTL jitter**: TTL을 랜덤으로 흔들어 동시 만료를 줄인다
- [ ] **singleflight(락)**: 하나의 요청만 DB를 치고 캐시를 채우도록 한다
- [ ] **stale-while-revalidate**: 약간 오래된 값을 잠시 제공하고 백그라운드 갱신
- [ ] **negative caching**: “없음” 결과도 짧게 캐시하여 DB 보호
- [ ] **핫키 보호**: 특정 키에 트래픽이 몰리면 샤딩/계층 캐시/레이트리밋

---

## 2) Redis Cluster 내부/Hash Slot/Rebalance

### 핵심 개념
- Redis Cluster는 키를 **hash slot(0~16383)**에 매핑해 여러 노드로 분산 저장
- 멀티키 연산/트랜잭션은 **같은 슬롯**이어야 하는 경우가 많다
- 클라이언트는 리다이렉션(MOVED/ASK)을 처리해야 한다(보통 라이브러리가 처리)

### 키 태그로 슬롯 고정하기
- `{...}` 안의 부분만 해싱에 사용되는 패턴이 흔하다(클러스터 멀티키를 위해)
- 예: `cart:{user123}:items`, `cart:{user123}:meta`  
  → `{user123}`이 같으므로 같은 슬롯에 배치될 가능성이 높아 멀티키 작업이 쉬워진다

### 운영 명령어(점검용)
- `CLUSTER INFO`
- `CLUSTER NODES`
- `CLUSTER SLOTS`

### Rebalance 시 주의
- 리샤딩/리밸런싱 중에는 리다이렉션이 늘고 지연이 튈 수 있다
- 운영에서는:
  - [ ] 트래픽이 낮은 시간대 수행
  - [ ] 모니터링(지연/에러율/CPU/네트워크)과 함께 진행
  - [ ] 핫키가 특정 슬롯에 몰려있지 않은지 점검

---

## 3) 종합 구성 실습 체크리스트
- [ ] Standalone Redis 띄우고 기본 명령 테스트
- [ ] Replication 구성 후 읽기/쓰기 분리 실험
- [ ] Sentinel 구성 후 failover 시나리오 테스트(재시도/연결 갱신)
- [ ] Redis Cluster 구성 후:
  - [ ] 키 분산 확인
  - [ ] MOVED/ASK 상황에서 클라이언트 동작 확인
  - [ ] 키 태그로 멀티키 시나리오 해결
- [ ] Persistence 조합(RDB/AOF)에서 복구 시나리오 연습

---

# “상황 → 선택 → 왜?” 시나리오 라이브러리

> 면접에서는 “명령어를 아는지”보다 **왜 그 자료구조/패턴을 택했는지**(트레이드오프, 장애 시 동작)를 더 봅니다.

## 시나리오 1) 상품 상세 조회 캐시 (DB 보호 + TTL)
**상황**: 상품 상세 조회 트래픽이 높아 DB가 느려짐. 캐시가 필요함.  
**선택**: String + TTL (`GET/SET EX`), (추가) stampede 대응  
**왜**: 단일 JSON을 빠르게 반환. TTL로 신선도/메모리 제어. 캐시-어사이드가 구현이 단순.

**명령/흐름**
```bash
GET product:42
# miss -> DB 조회 -> set
SET product:42 "<json>" EX 60
```

**주의**
- 핫키/동시 미스 폭탄 → singleflight 또는 stale 전략 필요(시나리오 2)

---

## 시나리오 2) Cache Stampede 방지: “한 명만 DB를 치게”
**상황**: TTL 만료 순간 동시 미스 → DB 폭주.  
**선택**: String + “락 키” (SET NX PX) 또는 Lua로 singleflight  
**왜**: 갱신 작업을 1개로 수렴시켜 DB를 보호.

**예시(단순)**
1) 캐시 미스  
2) `SET lock:product:42 <token> NX PX 3000` 성공한 1명만 DB 조회 후 캐시 set  
3) 실패한 요청은 짧게 대기 후 캐시 재시도

**주의**
- 락 해제는 토큰 비교가 필요(시나리오 4)
- 락 만료 전 DB가 오래 걸리면 중복 갱신 가능 → TTL/타임아웃 설계

---

## 시나리오 3) IP 기준 레이트리밋 (분당 100회)
**상황**: 특정 IP가 API를 도배. 분당 100회 제한.  
**선택**: String 카운터 + TTL (Lua 권장)  
**왜**: INCR는 원자적. TTL로 윈도우 자동 만료.

**Lua로 한 방(개념)**
- 키가 없으면 INCR 후 EXPIRE 설정
- 키가 있으면 INCR만
- 결과가 100 초과면 차단

**주의**
- INCR/EXPIRE를 따로 보내면 TTL 경합이 꼬일 수 있음 → Lua로 합치면 깔끔

---

## 시나리오 4) 분산 락(간단 뮤텍스) + 안전한 해제
**상황**: “주문 생성” 같은 크리티컬 구간 중복 실행을 막고 싶음.  
**선택**: `SET lock:<resource> <token> NX PX <ttl>` + Lua로 안전 해제  
**왜**: 락 획득은 단일 명령으로 원자적. 해제는 “내 락인지” 확인이 필요.

**획득**
```bash
SET lock:order:42 <token> NX PX 3000
```

**해제(개념)**
- if GET(lock) == token then DEL(lock)

**주의**
- 락 TTL은 반드시 필요(죽은 프로세스가 영원히 잠그는 상황 방지)
- 락은 과용하면 병목/데드락/지연을 만든다 → 범위를 최소화

---

## 시나리오 5) “멱등 키”로 결제 중복 요청 방지
**상황**: 결제 API가 재시도되며 같은 요청이 중복 처리될 수 있음.  
**선택**: Set 또는 String(`SET NX`)로 idempotency-key 저장 + TTL  
**왜**: 이미 처리했는지 빠르게 판정. TTL로 저장 기간 제어.

**간단 패턴**
```bash
SET idem:<key> "1" NX EX 86400
# 성공이면 처음 처리, 실패면 이미 처리됨
```

**주의**
- 결과를 저장해야 “재시도에 동일 응답 반환”이 가능(단순 플래그만 저장하면 UX가 애매해질 수 있음)

---

## 시나리오 6) 간단 작업 큐(유실/중복 약간 허용)
**상황**: 이메일 발송 같은 “중복돼도 큰 문제 없는” 작업을 비동기로 처리.  
**선택**: List + `BRPOP/BLPOP`  
**왜**: 구현이 단순하고 빠르다.

```bash
RPUSH mail_jobs "<job>"
BLPOP mail_jobs 0
```

**주의**
- 컨슈머가 pop 후 죽으면 유실/중복 제어가 어렵다 → 중요해지면 Streams로 이동(시나리오 7)

---

## 시나리오 7) “유실 없이 재처리 가능한” 작업 큐
**상황**: 주문 후처리/정산처럼 반드시 처리돼야 하고, 컨슈머 장애 시 재처리가 필요.  
**선택**: Streams + Consumer Group  
**왜**: 메시지 영속 + ACK + pending 재처리 모델 제공.

**핵심 흐름**
- producer: `XADD`
- consumer: `XREADGROUP ... >`
- 처리 완료: `XACK`
- 장애 복구: `XPENDING` / `XCLAIM`(또는 자동화)

**주의**
- 멱등 처리(중복 허용 설계)도 함께 필요(“정확히 한 번”은 애플리케이션 책임이 큼)

---

## 시나리오 8) 게임 리더보드(Top100)
**상황**: 유저 점수를 올리고 상위 100명을 보여줘야 함.  
**선택**: Sorted Set (`ZINCRBY`, `ZREVRANGE`)  
**왜**: score 기반 정렬이 내장. TopN 조회가 간단.

```bash
ZINCRBY leaderboard 50 user:123
ZREVRANGE leaderboard 0 99 WITHSCORES
```

**주의**
- 멤버 수가 계속 늘면 메모리 증가 → 필요하면 기간별 리더보드 분리(일/주/월)

---

## 시나리오 9) “오늘의 유니크 방문자 수(DAU)”
**상황**: 오늘 서비스에 접속한 유니크 사용자 수를 집계.  
**선택**: Set(유저 ID 저장) + `SCARD` + TTL(하루)  
**왜**: 중복 없는 집합으로 정확한 유니크 집계를 쉽게 구현.

```bash
SADD dau:2026-03-03 user:123
SCARD dau:2026-03-03
EXPIRE dau:2026-03-03 172800
```

**주의**
- 유저 수가 매우 크면 메모리 부담. 규모가 커지면 HyperLogLog 같은 대안(정확도 vs 메모리)을 고려하는 질문이 면접에서 나올 수 있음.

---

## 시나리오 10) 사용자 프로필 캐시(필드 단위 업데이트)
**상황**: 프로필 일부만 업데이트가 잦고, 전체 JSON 재직렬화가 부담.  
**선택**: Hash (`HSET/HMGET`)  
**왜**: 부분 업데이트가 쉽고, 필드 조회도 효율적.

```bash
HSET user:123 name "kim" email "a@b.com"
HMGET user:123 name email
```

**주의**
- 필드가 너무 많아지면 `HGETALL` 금지, 필요한 것만 가져오기

---

## 시나리오 11) 실시간 알림(유실 허용)
**상황**: “지금 접속한 사용자”에게만 알림을 뿌리면 됨. 오프라인이면 놓쳐도 됨.  
**선택**: Pub/Sub (`PUBLISH/SUBSCRIBE`)  
**왜**: 가장 단순한 브로드캐스트. 저장/재처리 필요 없을 때 적합.

**주의**
- “반드시 전달”이 필요하면 Pub/Sub가 아니라 Streams나 외부 MQ로

---

## 시나리오 12) “재고 차감”을 원자적으로(동시성 안전)
**상황**: 재고가 0 미만으로 내려가면 안 됨. 다수 요청이 동시에 들어옴.  
**선택**: Lua Script로 “검사 + 차감”을 한 번에  
**왜**: GET 후 DECR 같은 2단계는 경쟁 조건 발생. Lua로 원자 처리 가능.

**핵심 로직(개념)**
- 현재 재고 조회
- 재고 충분하면 차감 후 성공
- 아니면 실패

**주의**
- 스크립트는 짧고 빠르게. 큰 루프/스캔 금지.

---

## 시나리오 13) 딜레이 작업(5분 뒤 실행)
**상황**: 특정 작업을 “지금 말고 5분 후”에 실행하고 싶다.  
**선택**: Sorted Set (score=실행시각), 주기적 poll  
**왜**: 시간 기반 정렬/범위 조회가 쉬움.

```bash
ZADD delay_jobs <now+300000> job:123
ZRANGEBYSCORE delay_jobs -inf <now> LIMIT 0 100
# 처리 후 ZREM
```

**주의**
- 폴링 주기/배치 크기, 중복 처리 방지(멱등), 장애 시 재처리 설계 필요  
- 정확한 “예약/재시도/가시성”이 더 필요하면 Streams와 조합하거나 전용 스케줄러 사용

---

## 시나리오 14) Redis Cluster에서 “멀티키”가 필요한 경우
**상황**: 사용자 장바구니 `items`와 `meta`를 동시에 갱신/조회하고 싶다.  
**선택**: 키 태그 `{user123}`로 같은 슬롯에 몰기  
**왜**: 클러스터 모드에서 멀티키/트랜잭션이 슬롯이 다르면 실패할 수 있음.

예:
- `cart:{user123}:items`
- `cart:{user123}:meta`

**주의**
- 태그 설계가 곧 데이터 배치 설계다. 잘못하면 핫슬롯이 생길 수 있음(특정 user가 트래픽을 몰고 다니는 경우)

---

# 면접용 한 장 치트시트

## 자료구조 선택 요약
- String: 캐시/토큰/카운터/플래그/간단락
- List: 간단 큐/대기열(ACK 필요하면 Streams로)
- Set: 중복 제거/멱등/유니크 집계
- Hash: 엔티티 필드 저장/부분 업데이트
- ZSet: 랭킹/TopN/시간 기반 스케줄링
- Pub/Sub: 실시간 브로드캐스트(유실 허용)
- Streams: 영속 메시징/분산 컨슈머/재처리

## 운영 주의사항 TOP
- `KEYS` 금지, 대신 `SCAN`
- BigKey/HotKey는 지연/복제/메모리 문제의 원흉
- TTL 설계/Stampede 방지가 캐시의 성패를 좌우
- “중요 데이터 영속”에 Redis를 쓰려면 persistence/HA/복구 시나리오를 먼저 설계

---

## 빠른 명령어 묶음(암기용)
- String: `GET SET DEL EXPIRE TTL INCR`
- List: `RPUSH LPUSH BLPOP BRPOP LLEN LRANGE`
- Set: `SADD SISMEMBER SCARD SSCAN`
- Hash: `HSET HGET HMGET HINCRBY HSCAN`
- ZSet: `ZADD ZINCRBY ZREVRANGE ZREVRANK ZREMRANGEBYRANK`
- Pub/Sub: `SUBSCRIBE PUBLISH`
- Tx: `WATCH MULTI EXEC`
- Lua: `EVAL EVALSHA SCRIPT LOAD`
- Streams: `XADD XREADGROUP XACK XPENDING XCLAIM/XAUTOCLAIM`
