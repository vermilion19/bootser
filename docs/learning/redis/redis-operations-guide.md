# Redis 운영 가이드 - 장애 시나리오와 대응 전략

## 목차

1. [장애 발생 가능 시나리오](#1-장애-발생-가능-시나리오)
2. [장애 대응 방법](#2-장애-대응-방법)
3. [실제 운영 예제](#3-실제-운영-예제)
4. [운영 시 주의점](#4-운영-시-주의점)
5. [모니터링 체크리스트](#5-모니터링-체크리스트)

---

## 1. 장애 발생 가능 시나리오

### 1.1 메모리 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| OOM (Out of Memory) | maxmemory 초과, eviction 정책 미설정 | 쓰기 실패, 프로세스 kill |
| 메모리 단편화 | 잦은 키 생성/삭제, 큰 값 저장 | `mem_fragmentation_ratio` > 1.5 |
| 대용량 키(Big Key) | 단일 키에 수백MB 데이터 | 삭제/조회 시 블로킹, 레이턴시 급증 |

### 1.2 연결 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| 연결 풀 고갈 | 커넥션 누수, 풀 크기 부족 | `JedisConnectionException`, 타임아웃 |
| maxclients 초과 | 클라이언트 수 > 설정값 | `ERR max number of clients reached` |
| 네트워크 타임아웃 | 네트워크 지연, Slow Query | `RedisCommandTimeoutException` |

### 1.3 복제/클러스터 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| Master 다운 | HW 장애, OOM kill | 쓰기 불가, Sentinel/Cluster failover 발동 |
| Replica 복제 지연 | 네트워크 지연, Master 부하 | 읽기 데이터 불일치 |
| Split Brain | 네트워크 파티션 | 두 Master에 동시 쓰기 → 데이터 충돌 |
| Full Resync 반복 | repl-backlog 부족 | Master CPU/네트워크 폭증 |
| Cluster Slot 누락 | 노드 다운, 마이그레이션 실패 | `CLUSTERDOWN` 에러 |

### 1.4 성능 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| Slow Query | `KEYS *`, `SMEMBERS` (대량 데이터), Lua 스크립트 | 전체 요청 블로킹 |
| Hot Key | 특정 키에 요청 집중 | 해당 노드 CPU 100%, 레이턴시 급증 |
| Thundering Herd | 캐시 만료 시 동시 DB 조회 | DB 부하 폭증, 연쇄 장애 |
| AOF Rewrite 블로킹 | 대량 데이터 상태에서 AOF 재작성 | fork 시 메모리 2배 사용, 응답 지연 |

### 1.5 데이터 장애

| 유형 | 원인 | 증상 |
|------|------|------|
| 데이터 유실 | 비동기 복제 중 Master 다운 | failover 후 최근 쓰기 데이터 소실 |
| RDB/AOF 손상 | 디스크 장애, 비정상 종료 | 재시작 시 데이터 로드 실패 |
| TTL 미설정 | 캐시 키에 만료 시간 누락 | 메모리 누수, 오래된 데이터 잔존 |
| 분산 락 미해제 | 락 소유 프로세스 비정상 종료 | 데드락, 후속 작업 무한 대기 |

---

## 2. 장애 대응 방법

### 2.1 메모리 장애 대응

**메모리 정책 설정:**
```redis
# redis.conf
maxmemory 4gb
maxmemory-policy allkeys-lru   # 캐시 용도: LRU 기반 제거
# maxmemory-policy volatile-lru  # TTL이 설정된 키만 제거 (세션 등 혼합 용도)
```

**Big Key 탐지 및 제거:**
```bash
# Big Key 스캔 (운영 중 안전)
redis-cli --bigkeys --i 0.1

# 메모리 사용량 확인
redis-cli MEMORY USAGE <key>

# Big Key 비동기 삭제 (블로킹 방지)
redis-cli UNLINK <key>
```

**메모리 단편화 대응:**
```redis
# 단편화 비율 확인
INFO memory
# mem_fragmentation_ratio > 1.5이면 조치 필요

# Active Defrag 활성화 (Redis 4.0+)
CONFIG SET activedefrag yes
CONFIG SET active-defrag-threshold-lower 10
```

### 2.2 연결 장애 대응

**Redisson 커넥션 풀 설정 (Booster 프로젝트):**
```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 50       # 최대 커넥션 수
          max-idle: 20         # 최대 유휴 커넥션
          min-idle: 5          # 최소 유휴 커넥션
          max-wait: 3000ms     # 커넥션 획득 대기 시간
```

**Redisson 설정:**
```java
@Bean
public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer()
        .setAddress("redis://localhost:6379")
        .setConnectionPoolSize(64)          // 커넥션 풀 크기
        .setConnectionMinimumIdleSize(10)   // 최소 유휴 커넥션
        .setConnectTimeout(10000)           // 연결 타임아웃
        .setTimeout(3000)                   // 명령 타임아웃
        .setRetryAttempts(3)                // 재시도 횟수
        .setRetryInterval(1500);            // 재시도 간격(ms)
    return Redisson.create(config);
}
```

### 2.3 복제/클러스터 장애 대응

**Sentinel 구성 (자동 failover):**
```redis
# sentinel.conf
sentinel monitor booster-master 192.168.1.10 6379 2   # 쿼럼 2
sentinel down-after-milliseconds booster-master 5000    # 5초 무응답 시 down 판정
sentinel failover-timeout booster-master 60000          # failover 타임아웃
sentinel parallel-syncs booster-master 1                # 동시 sync replica 수
```

**Split Brain 방지:**
```redis
# Master가 최소 1개의 Replica와 연결되어 있어야 쓰기 허용
min-replicas-to-write 1
min-replicas-max-lag 10   # 복제 지연 최대 10초
```

**Full Resync 방지:**
```redis
# repl-backlog 크기 충분히 설정 (기본 1MB → 256MB)
repl-backlog-size 256mb
repl-backlog-ttl 3600
```

### 2.4 성능 장애 대응

**Slow Query 방지:**
```redis
# Slow Log 설정
slowlog-log-slower-than 10000   # 10ms 이상 기록
slowlog-max-len 128

# Slow Log 확인
SLOWLOG GET 10
```

**위험한 명령어 차단:**
```redis
# redis.conf - 운영 환경에서 차단
rename-command KEYS ""
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command DEBUG ""
```

**Hot Key 대응:**
```java
// 로컬 캐시(L1) + Redis(L2) 2단 캐시 구조
@Cacheable(cacheNames = "restaurant", key = "#restaurantId",
           cacheManager = "caffeineCacheManager")  // L1: Caffeine
public Restaurant getRestaurant(Long restaurantId) {
    // L1 miss → L2(Redis) 조회
    String cached = redisTemplate.opsForValue().get("restaurant:" + restaurantId);
    if (cached != null) return deserialize(cached);

    // L2 miss → DB 조회
    Restaurant restaurant = restaurantRepository.findById(restaurantId).orElseThrow();
    redisTemplate.opsForValue().set("restaurant:" + restaurantId, serialize(restaurant), Duration.ofMinutes(10));
    return restaurant;
}
```

**Thundering Herd(Cache Stampede) 방지:**
```java
// 분산 락을 이용한 Cache Stampede 방지
public WaitingRank getWaitingRank(Long waitingId) {
    String cacheKey = "waiting:rank:" + waitingId;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return deserialize(cached);

    // 락을 획득한 하나의 요청만 DB 조회
    RLock lock = redissonClient.getLock("lock:" + cacheKey);
    try {
        if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
            // Double-check: 락 대기 중 다른 스레드가 캐시를 채웠을 수 있음
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return deserialize(cached);

            WaitingRank rank = calculateRankFromDB(waitingId);
            redisTemplate.opsForValue().set(cacheKey, serialize(rank), Duration.ofSeconds(30));
            return rank;
        }
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
    throw new RuntimeException("캐시 갱신 대기 시간 초과");
}
```

### 2.5 분산 락 장애 대응

**ShedLock - 스케줄러 중복 실행 방지 (Booster 프로젝트):**
```java
// OutboxMessageRelay에서 사용 중
@Scheduled(fixedDelay = 3000)
@SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT3S")
public void publishEvents() {
    // ShedLock이 Redis 기반 분산 락으로 중복 실행 방지
}
```

**Redisson 분산 락 안전 패턴:**
```java
public void processWithLock(String resourceId) {
    RLock lock = redissonClient.getLock("lock:resource:" + resourceId);
    try {
        // waitTime: 락 획득 대기 시간, leaseTime: 자동 해제 시간
        boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
        if (!acquired) {
            throw new LockAcquisitionException("락 획득 실패: " + resourceId);
        }
        // 비즈니스 로직 수행
        doBusinessLogic(resourceId);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
    } finally {
        // 반드시 현재 스레드가 소유한 경우에만 해제
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

### 2.6 데이터 유실 대응 (Self-Healing)

**Booster 프로젝트 - Redis 유실 시 DB 복구:**
```java
// Redis SortedSet 대기열이 유실된 경우 DB에서 복구
public void recoverWaitingQueue(Long restaurantId) {
    List<Waiting> activeWaitings = waitingRepository
        .findByRestaurantIdAndStatusOrderByCreatedAt(restaurantId, WaitingStatus.WAITING);

    RScoredSortedSet<String> sortedSet = redissonClient
        .getScoredSortedSet("waiting:queue:" + restaurantId);

    sortedSet.clear();
    for (int i = 0; i < activeWaitings.size(); i++) {
        Waiting w = activeWaitings.get(i);
        sortedSet.add(w.getCreatedAt().toEpochSecond(ZoneOffset.UTC), String.valueOf(w.getId()));
    }
    log.info("대기열 복구 완료: restaurantId={}, count={}", restaurantId, activeWaitings.size());
}
```

---

## 3. 실제 운영 예제

### 3.1 Booster 프로젝트 - Redis 사용 흐름

```
[waiting-service]
  ├─ 대기 등록
  │   ├─ DB: waiting 테이블 INSERT
  │   └─ Redis: SortedSet에 추가 (score=등록시간)
  │       ZADD waiting:queue:{restaurantId} {timestamp} {waitingId}
  │
  ├─ 순번 조회 (실시간)
  │   └─ Redis: ZRANK waiting:queue:{restaurantId} {waitingId}
  │       → O(logN) 으로 즉시 응답
  │
  ├─ 호출 처리
  │   ├─ DB: waiting 상태 → CALLED
  │   ├─ Redis: ZREM waiting:queue:{restaurantId} {waitingId}
  │   └─ Outbox: 이벤트 저장 → Kafka 발행
  │
  └─ 분산 락 (ShedLock)
      └─ OutboxMessageRelay: Redis 기반 분산 락으로 중복 실행 방지
```

### 3.2 장애 시나리오별 대응 시뮬레이션

**시나리오 A: Redis Master 다운**

```
1. Sentinel이 5초 내 감지 → Replica를 Master로 승격 (자동 failover)
2. 비동기 복제 특성상 최근 쓰기 일부 유실 가능
3. 대기열 데이터 불일치 감지 시 Self-Healing 작동:
   - DB의 WAITING 상태 데이터로 Redis SortedSet 재구성
4. 조치:
   - 기존 Master 복구 후 Replica로 연결
   - 데이터 정합성 확인 스크립트 실행
```

**시나리오 B: 메모리 사용량 90% 초과 알림**

```
1. redis-cli INFO memory로 상세 확인
2. Big Key 스캔: redis-cli --bigkeys
3. 즉시 대응:
   - 불필요한 키 정리 (TTL 미설정 키 확인)
   - maxmemory-policy가 적절한지 확인
4. 근본 대응:
   - Big Key 분할 (Hash → 여러 Hash로 분산)
   - maxmemory 증설 또는 클러스터 노드 추가
```

**시나리오 C: Hot Key로 특정 노드 CPU 100%**

```
1. 인기 식당 웨이팅 조회가 waiting:queue:{restaurantId}에 집중
2. 즉시 대응:
   - 해당 키에 대해 로컬 캐시(Caffeine) 적용 (TTL 1~2초)
   - 읽기 요청을 Replica로 분산
3. 근본 대응:
   - 키 분산: waiting:queue:{restaurantId}:{shard}로 샤딩
   - Read Replica 구성으로 읽기 분산
```

**시나리오 D: 분산 락 데드락 발생**

```
1. OutboxMessageRelay가 락을 잡은 채로 비정상 종료
2. ShedLock의 lockAtMostFor="PT30S" 설정으로 30초 후 자동 해제
3. 다음 스케줄링 주기에 다른 인스턴스가 락 획득하여 정상 동작
4. Redisson 락의 경우 leaseTime 설정으로 자동 해제 보장
5. 주의: leaseTime 없이 lock()을 호출하면 watchdog이 자동 연장
   → 프로세스 다운 시 watchdog도 종료되어 30초 후 자동 해제
```

---

## 4. 운영 시 주의점

### 4.1 메모리 관리

- **maxmemory 필수 설정**: 미설정 시 OS 메모리 전부 사용 → OOM kill 위험
- **TTL 필수**: 캐시 용도의 모든 키에 만료 시간 설정
- **Big Key 금지**: 단일 키 10MB 이상 지양. Hash/List는 1만 개 요소 이하 권장
- **메모리 여유**: maxmemory는 물리 메모리의 70% 이하로 설정 (fork 시 2배 필요)

### 4.2 명령어 사용 주의

| 위험 명령어 | 문제 | 대안 |
|------------|------|------|
| `KEYS *` | O(N) 풀스캔, 블로킹 | `SCAN` (커서 기반 순회) |
| `FLUSHALL/FLUSHDB` | 전체 데이터 삭제 | 운영 환경에서 차단 |
| `SMEMBERS` (대량) | 대량 데이터 시 블로킹 | `SSCAN` |
| `DEL` (Big Key) | 블로킹 삭제 | `UNLINK` (비동기 삭제) |
| `SAVE` | 포그라운드 RDB 생성 | `BGSAVE` |

### 4.3 영속성(Persistence) 설정

```redis
# RDB: 주기적 스냅샷 (빠른 복구, 일부 데이터 유실 가능)
save 900 1        # 900초 내 1건 이상 변경 시
save 300 10       # 300초 내 10건 이상 변경 시

# AOF: 모든 쓰기 기록 (데이터 안전, 디스크 I/O 높음)
appendonly yes
appendfsync everysec   # 매초 fsync (성능/안전 균형)

# 권장: RDB + AOF 동시 사용
aof-use-rdb-preamble yes   # AOF 파일에 RDB 프리앰블 포함 (빠른 로드)
```

**용도별 권장:**

| 용도 | RDB | AOF | 이유 |
|------|-----|-----|------|
| 순수 캐시 | OFF | OFF | 유실 허용, 성능 최우선 |
| 세션 저장소 | ON | ON | 사용자 세션 유실 방지 |
| 대기열 (Booster) | ON | ON | DB 복구 가능하지만 빠른 복구 선호 |
| 분산 락 | OFF | OFF | 락은 휘발성, TTL로 자동 해제 |

### 4.4 클러스터/Sentinel 운영

- **Sentinel**: 최소 3대, 서로 다른 물리 서버에 배치
- **Cluster**: 최소 6노드(Master 3 + Replica 3)
- **failover 후 확인 사항**:
  1. 새 Master 정상 동작 확인
  2. 기존 Master 복구 후 Replica로 연결 확인
  3. 클라이언트가 새 Master를 정상 인식하는지 확인
- **네트워크 타임아웃**: `cluster-node-timeout`은 보수적으로 설정 (15초 권장)

### 4.5 보안

```redis
# 비밀번호 설정
requirepass <strong-password>

# ACL (Redis 6.0+)
user booster-app on >password ~waiting:* ~restaurant:* +@all -@dangerous

# 위험 명령어 비활성화
rename-command FLUSHALL ""
rename-command CONFIG ""
rename-command SHUTDOWN ""

# 바인드 주소 제한
bind 192.168.1.0/24
protected-mode yes

# TLS
tls-port 6380
tls-cert-file /path/to/redis.crt
tls-key-file /path/to/redis.key
```

### 4.6 Spring 애플리케이션 레벨 주의점

- **직렬화**: `GenericJackson2JsonRedisSerializer` 사용 시 클래스 정보 포함 → 역직렬화 호환성 주의
- **트랜잭션과 캐시 순서**: DB 트랜잭션 커밋 후 캐시 갱신 (트랜잭션 실패 시 캐시 오염 방지)
- **커넥션 반환**: try-with-resources 또는 RedisTemplate 사용으로 커넥션 누수 방지
- **Pipeline 활용**: 여러 명령을 한 번에 보내 RTT 절감
  ```java
  redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      for (String key : keys) {
          connection.stringCommands().get(key.getBytes());
      }
      return null;
  });
  ```

---

## 5. 모니터링 체크리스트

### 필수 모니터링 메트릭

| 메트릭 | 임계값 예시 | 의미 |
|--------|------------|------|
| `used_memory` / `maxmemory` | > 80% | 메모리 부족 임박 |
| `mem_fragmentation_ratio` | > 1.5 또는 < 1.0 | 단편화 또는 스왑 사용 |
| `connected_clients` | > maxclients * 0.8 | 커넥션 고갈 임박 |
| `instantaneous_ops_per_sec` | 급격한 변화 | 트래픽 이상 |
| `keyspace_hits / misses` | hit rate < 90% | 캐시 효율 저하 |
| `latest_fork_usec` | > 1초 | RDB/AOF 포크 지연 |
| `rejected_connections` | > 0 | 커넥션 거부 발생 |
| `evicted_keys` | 급증 | 메모리 부족으로 키 제거 |
| `repl_backlog_active` | 0 | 복제 백로그 비활성 |
| Waiting Queue Size | 비정상 증가 | 대기열 데이터 이상 |

### Redis INFO 명령으로 빠른 진단

```bash
# 전체 상태 확인
redis-cli INFO

# 섹션별 확인
redis-cli INFO memory         # 메모리 상태
redis-cli INFO clients        # 클라이언트 연결 상태
redis-cli INFO stats          # 통계 (히트율, 초당 처리량)
redis-cli INFO replication    # 복제 상태
redis-cli INFO persistence    # RDB/AOF 상태
redis-cli INFO commandstats   # 명령어별 통계

# 실시간 모니터링
redis-cli --stat              # 1초 간격 실시간 통계
redis-cli LATENCY LATEST      # 레이턴시 이벤트
redis-cli CLIENT LIST         # 연결된 클라이언트 목록
```

### Grafana 대시보드 구성 권장

```
1. Overview: 메모리 사용률, 커넥션 수, 초당 명령 처리량
2. Memory: used_memory 추이, 단편화 비율, eviction 발생 건수
3. Performance: 레이턴시(p50/p95/p99), Slow Log 건수, 명령어별 처리량
4. Replication: 복제 지연, Master-Replica 연결 상태
5. Application: 캐시 히트율, Waiting Queue 크기, 분산 락 획득/실패 비율
```

### Prometheus 알림 규칙 예시

```yaml
groups:
  - name: redis-alerts
    rules:
      - alert: RedisHighMemoryUsage
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis 메모리 사용률 80% 초과 ({{ $value | humanizePercentage }})"

      - alert: RedisDown
        expr: redis_up == 0
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "Redis 인스턴스 다운"

      - alert: RedisHighFragmentation
        expr: redis_mem_fragmentation_ratio > 1.5
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Redis 메모리 단편화 비율 {{ $value }}"

      - alert: RedisLowHitRate
        expr: redis_keyspace_hits_total / (redis_keyspace_hits_total + redis_keyspace_misses_total) < 0.9
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Redis 캐시 히트율 90% 미만"

      - alert: RedisRejectedConnections
        expr: increase(redis_rejected_connections_total[5m]) > 0
        labels:
          severity: critical
        annotations:
          summary: "Redis 커넥션 거부 발생"

      - alert: RedisReplicationBroken
        expr: redis_connected_slaves < 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Redis Replica 연결 끊김"
```