# Redis 트랜잭션 & Pipeline & Sentinel 심화

## 1. MULTI / EXEC / DISCARD

Redis 트랜잭션은 **명령어 묶음을 원자적으로 실행**한다.

```
클라이언트                    Redis Server
   │                              │
   │  MULTI                       │
   │─────────────────────────────►│ 트랜잭션 시작, "OK"
   │                              │
   │  SET balance 100             │
   │─────────────────────────────►│ "QUEUED" (실행 안 함, 큐에 저장)
   │                              │
   │  DECRBY balance 30           │
   │─────────────────────────────►│ "QUEUED"
   │                              │
   │  EXEC                        │
   │─────────────────────────────►│ 큐의 명령들을 순서대로 원자적 실행
   │◄─────────────────────────────│ [OK, 70] (각 명령의 결과)
   └──────────────────────────────┘
```

```bash
MULTI
SET balance 100
DECRBY balance 30
INCRBY other_balance 30
EXEC
# 결果:
# 1) OK
# 2) 70
# 3) 130
```

### DISCARD (트랜잭션 취소)

```bash
MULTI
SET key1 "value1"
SET key2 "value2"
DISCARD  # 큐 버리고 트랜잭션 종료
# → "OK"
# key1, key2 변경 없음
```

---

## 2. 트랜잭션의 에러 처리

### 큐잉 에러 (컴파일 에러)

```bash
MULTI
SET key1 value1
UNKNOWNCMD   # 존재하지 않는 명령
SET key2 value2
EXEC
# → EXECABORT: 큐잉 중 에러 발생 → 전체 취소
```

### 실행 에러 (런타임 에러)

```bash
MULTI
SET key1 "hello"
INCR key1       # String에 INCR → 런타임 에러
SET key2 "world"
EXEC
# 결果:
# 1) OK      ← SET key1 성공
# 2) ERROR   ← INCR 실패 (타입 오류)
# 3) OK      ← SET key2 성공 ← 에러 있어도 나머지는 실행됨!
```

> **주의**: Redis 트랜잭션은 **실행 에러가 있어도 롤백하지 않는다.** 일부 성공, 일부 실패 가능. RDBMS의 트랜잭션과 다름.

---

## 3. WATCH - 낙관적 락

EXEC 실행 전 감시 대상 키가 변경되면 **트랜잭션을 취소**한다.

```
클라이언트 A                  Redis                 클라이언트 B
   │                           │                         │
   │  WATCH balance            │                         │
   │─────────────────────────► │                         │
   │  MULTI                    │                         │
   │─────────────────────────► │                         │
   │  GET balance              │                         │
   │─────────────────────────► │                         │
   │◄─────────────────────────  │ 100                    │
   │  DECRBY balance 30        │                         │
   │─────────────────────────► │ QUEUED                  │
   │                           │  SET balance 200 ◄─────│ (B가 먼저 변경!)
   │  EXEC                     │                         │
   │─────────────────────────► │                         │
   │◄─────────────────────────  │ nil ← 감시 키 변경 감지, 취소
```

```bash
WATCH balance

MULTI
current = GET balance    # 이 시점 이후 balance가 바뀌면 EXEC 실패
DECRBY balance 30
EXEC
# → nil: 다른 클라이언트가 balance를 변경했으면 트랜잭션 취소
# → 결과 배열: 변경 없으면 성공
```

### WATCH를 이용한 낙관적 락 패턴

```java
// Java (Jedis)
public boolean decrementBalance(String userId, int amount) {
    try (Jedis jedis = pool.getResource()) {
        String key = "balance:" + userId;

        while (true) {
            jedis.watch(key);                      // 감시 시작
            String balance = jedis.get(key);
            int current = Integer.parseInt(balance);

            if (current < amount) {
                jedis.unwatch();
                return false;  // 잔액 부족
            }

            Transaction tx = jedis.multi();
            tx.set(key, String.valueOf(current - amount));
            List<Object> results = tx.exec();      // EXEC

            if (results != null) {
                return true;   // 성공
            }
            // results == null: 다른 클라이언트가 변경 → 재시도
        }
    }
}
```

```
WATCH 사용 시나리오:
1. WATCH key           # 감시 등록
2. GET key             # 현재 값 읽기
3. 비즈니스 로직       # 새 값 계산
4. MULTI               # 트랜잭션 시작
5. SET key newValue    # 새 값 설정 (큐잉)
6. EXEC                # 실행
   → nil이면: 2번부터 재시도
   → 결과 있으면: 성공
```

---

## 4. Lua 스크립트

**Lua 스크립트는 Redis 서버에서 원자적으로 실행**된다. MULTI/EXEC보다 강력한 원자성 제공.

```bash
# 기본 실행
EVAL "return redis.call('SET', KEYS[1], ARGV[1])" 1 mykey myvalue
#     ↑ 스크립트    ↑ KEYS 수  ↑ KEYS[1]  ↑ ARGV[1]

# GET + SET 원자적으로
EVAL "
  local val = redis.call('GET', KEYS[1])
  if val then
    redis.call('SET', KEYS[1], ARGV[1])
    return val
  end
  return nil
" 1 mykey newvalue
```

### Lua의 장점

```
MULTI/EXEC의 한계:
- 이전 명령 결과를 다음 명령에 사용 불가
- 조건 분기 불가

Lua 스크립트의 장점:
- 이전 결과를 변수에 저장해 다음 명령에 사용 가능
- if/else 조건 분기 가능
- 복잡한 로직을 단일 원자 연산으로 처리
```

### 실무 예시 - 재고 감소 (원자적)

```lua
-- Lua 스크립트 (stock_decrement.lua)
local stock = redis.call('GET', KEYS[1])
if not stock then
    return {err = 'KEY_NOT_FOUND'}
end

stock = tonumber(stock)
local amount = tonumber(ARGV[1])

if stock < amount then
    return {err = 'INSUFFICIENT_STOCK'}
end

redis.call('SET', KEYS[1], stock - amount)
return stock - amount
```

```java
// Spring + Lua 스크립트
@Component
public class StockDecrementScript {
    private final DefaultRedisScript<Long> script;
    private final StringRedisTemplate redisTemplate;

    public StockDecrementScript(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("scripts/stock_decrement.lua")));
        this.script.setResultType(Long.class);
    }

    public Long decrementStock(String productId, long amount) {
        return redisTemplate.execute(
            script,
            List.of("stock:" + productId),
            String.valueOf(amount)
        );
    }
}
```

### 스크립트 캐싱 (EVALSHA)

```bash
# 스크립트를 서버에 캐시하고 SHA1 반환
SCRIPT LOAD "return redis.call('GET', KEYS[1])"
# → "e0e1f9fabfa9d353e5f1b4a4ecb58d5a6d1f0b9a" (SHA1)

# SHA1로 실행 (스크립트 매번 전송 불필요)
EVALSHA e0e1f9fabfa9d353e5f1b4a4ecb58d5a6d1f0b9a 1 mykey

# 캐시 초기화
SCRIPT FLUSH
```

---

## 5. Pipeline (파이프라인)

**여러 명령을 한 번의 네트워크 왕복으로 처리**. 트랜잭션이 아님 - 원자성 보장 없음.

### 기본 vs 파이프라인 비교

```
기본 방식 (10개 명령):
클라이언트 → [SET 1] → 서버 → [OK] → 클라이언트  (1 RTT)
클라이언트 → [SET 2] → 서버 → [OK] → 클라이언트  (1 RTT)
...× 10
= 10 RTT

파이프라인 방식:
클라이언트 → [SET 1, SET 2, ..., SET 10] → 서버  (1 RTT)
클라이언트 ← [OK, OK, ..., OK] ←── 서버
= 1 RTT
```

```java
// Spring Data Redis Pipeline
List<Object> results = redisTemplate.executePipelined(
    (RedisCallback<Object>) connection -> {
        StringRedisConnection stringRedisConn = (StringRedisConnection) connection;
        for (int i = 0; i < 1000; i++) {
            stringRedisConn.set("key:" + i, "value:" + i);
        }
        return null;  // 파이프라인에서는 항상 null 반환
    }
);
// results: [OK, OK, ..., OK] 1000개
```

### Pipeline vs MULTI/EXEC vs Lua

| 항목 | Pipeline | MULTI/EXEC | Lua |
|------|----------|-----------|-----|
| **원자성** | 없음 | 있음 (실행 에러 제외) | 완전한 원자성 |
| **조건 분기** | 불가 | 불가 | 가능 |
| **이전 결과 참조** | 불가 | 불가 | 가능 |
| **RTT** | 1회 | 2회 이상 | 1회 |
| **사용 목적** | 대량 일괄 실행 | 원자적 명령 묶음 | 복잡한 원자 로직 |

### 언제 Pipeline 사용?

```
적합:
- 캐시 워밍업 (대량 데이터 초기 로드)
- 배치 통계 업데이트 (INCR 1000개)
- 여러 키 일괄 조회 (MGET 대체)

부적합:
- 원자성이 필요한 잔액 변경
- 이전 명령 결과에 따라 다음 명령이 달라지는 경우
```

---

## 6. Sentinel 동작 원리 심화

### Sentinel 구성

```
┌───────────┐   ┌───────────┐   ┌───────────┐
│Sentinel 1 │   │Sentinel 2 │   │Sentinel 3 │
└─────┬─────┘   └─────┬─────┘   └─────┬─────┘
      │               │               │
      └───────────────┼───────────────┘
                      │
      ┌───────────────┼───────────────┐
      │               │               │
┌─────▼─────┐   ┌─────▼─────┐   ┌─────▼─────┐
│  Master   │   │ Replica 1 │   │ Replica 2 │
└───────────┘   └───────────┘   └───────────┘
```

- Sentinel 최소 3개 권장 (홀수) → 쿼럼 투표를 위해
- Sentinel은 Master 장애 감지, Failover 수행, 클라이언트에 새 Master 알림

### 장애 감지 과정

```
[1단계: SDOWN (Subjectively Down)]
Sentinel 1이 Master에게 PING 전송
                │
        응답 없음 (down-after-milliseconds, 기본 30초)
                │
Sentinel 1: "나는 Master가 죽었다고 생각한다" → SDOWN

[2단계: ODOWN (Objectively Down)]
Sentinel 1이 다른 Sentinel들에게 "Master 죽었냐?" 투표 요청
                │
Sentinel 2: "맞아, 나도 응답 없어" (동의)
Sentinel 3: "맞아" (동의)
                │
quorum(기본 2)만큼 동의 → ODOWN 확정

[3단계: Leader Election]
Sentinel들 중 Failover를 수행할 Leader 선출 (Raft 유사 알고리즘)
                │
Sentinel 2가 Leader로 선출됨

[4단계: Failover]
Leader(Sentinel 2)가 Replica 중 새 Master 선택
        ├── replica-priority (낮을수록 우선)
        ├── 복제 지연 (lag) 가장 적은 Replica
        └── Replica ID (타이브레이커)
                │
Replica 1이 새 Master로 승격 (SLAVEOF NO ONE)
Replica 2는 새 Master를 follow
이전 Master가 복구되면 Replica로 전환됨

[5단계: 클라이언트 알림]
Sentinel이 새 Master 주소를 클라이언트에 알림
클라이언트는 Sentinel에게 현재 Master 주소를 질의
```

### Sentinel vs Cluster 선택

| 항목 | Sentinel | Cluster |
|------|----------|---------|
| **목적** | HA (고가용성) | HA + 수평 확장 |
| **데이터 분산** | 없음 (단일 Master) | 해시 슬롯 16384개로 분산 |
| **쓰기 처리량** | 단일 Master 한계 | 다수 Master로 확장 |
| **운영 복잡도** | 낮음 | 높음 |
| **Multi-key 명령** | 가능 | 같은 슬롯 키만 가능 |
| **적합한 규모** | 수십 GB | 수백 GB ~ TB |

```
선택 기준:
데이터 크기 < 수십 GB, 쓰기 처리량 단일 Master로 충분 → Sentinel
데이터 크기 > 수십 GB, 쓰기 수평 확장 필요 → Cluster
```

### 클라이언트 Sentinel 연결 (Spring)

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster              # sentinel.conf의 sentinel monitor 이름
        nodes:
          - sentinel-1:26379
          - sentinel-2:26379
          - sentinel-3:26379
```

```java
// Sentinel → Master 주소 자동 조회 흐름
// 1. 클라이언트 → Sentinel들에 연결
// 2. SENTINEL get-master-addr-by-name mymaster 명령으로 Master 주소 조회
// 3. Master에 직접 연결
// 4. Failover 발생 시 Sentinel이 클라이언트에 pub/sub으로 알림
//    (+switch-master 채널)
// 5. 클라이언트가 새 Master 주소로 재연결
```

---

## 7. 면접 Q&A

**Q. Redis 트랜잭션(MULTI/EXEC)이 RDBMS 트랜잭션과 다른 점은?**
> Redis 트랜잭션은 원자적 실행(모두 실행 또는 DISCARD로 모두 취소)을 제공하지만, 실행 중 런타임 에러가 발생해도 롤백하지 않는다. 일부 명령은 성공하고 일부는 실패할 수 있다. 또한 큐잉된 명령을 EXEC 전에 조건에 따라 변경할 수 없다. RDBMS의 Rollback이 필요하다면 Lua 스크립트를 사용하거나 애플리케이션 레벨에서 보상 트랜잭션을 구현해야 한다.

**Q. WATCH와 낙관적 락의 차이는?**
> 개념적으로 같다. WATCH는 Redis의 낙관적 락 구현이다. 트랜잭션 시작 전 키를 감시하고, EXEC 시점에 해당 키가 변경됐으면 트랜잭션을 취소(nil 반환)한다. 충돌이 드물게 발생하는 상황에 효율적이고, 충돌이 자주 발생하면 재시도 루프가 많아져 비효율적이다.

**Q. Lua 스크립트를 사용하는 이유는?**
> MULTI/EXEC는 이전 명령의 결과를 다음 명령에 사용하거나 조건 분기를 할 수 없다. Lua 스크립트는 Redis 서버에서 완전히 원자적으로 실행되므로 복잡한 로직(재고 확인 후 감소, 포인트 적립 등)을 단일 연산으로 처리할 수 있다. 또한 EVALSHA로 스크립트를 캐시하면 네트워크 전송 오버헤드도 줄일 수 있다.

**Q. Sentinel 쿼럼(quorum)이란?**
> Master가 객관적으로 다운됐다고 판단하기 위해 필요한 Sentinel의 동의 수다. quorum=2이면 Sentinel 2개 이상이 Master 다운에 동의해야 ODOWN으로 판정하고 Failover를 시작한다. Sentinel 3대에 quorum=2로 설정하면 네트워크 파티션으로 Sentinel 1대가 고립돼도 나머지 2대의 동의로 올바른 판정을 내릴 수 있다.

**Q. Pipeline이 트랜잭션은 아닌데 왜 성능에 중요한가?**
> Redis 명령 하나의 처리 시간 자체는 마이크로초지만, 네트워크 왕복(RTT)은 수십~수백 마이크로초다. 1000개 명령을 개별 전송하면 RTT만으로도 수십ms가 걸린다. Pipeline으로 묶으면 1번의 RTT로 처리 가능해 처리량이 수십~수백 배 향상된다. 원자성이 필요 없는 대량 배치 작업(캐시 워밍업, 통계 집계)에서 특히 유용하다.
