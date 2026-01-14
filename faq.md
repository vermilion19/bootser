# Booster 프로젝트 면접 예상 질문 & 답변

> 대용량 트래픽 관점의 기술 면접 대비

---

## 목차

1. [분산 락 (Distributed Lock)](#1-분산-락-distributed-lock)
2. [Outbox 패턴](#2-outbox-패턴)
3. [Kafka 이벤트 처리](#3-kafka-이벤트-처리)
4. [Redis 활용](#4-redis-활용)
5. [Resilience 패턴](#5-resilience-패턴)
6. [페이지네이션](#6-페이지네이션)
7. [ID 생성 전략](#7-id-생성-전략)
8. [Scale-out 설계](#8-scale-out-설계)
9. [데이터베이스](#9-데이터베이스)
10. [장애 대응](#10-장애-대응)

---

## 1. 분산 락 (Distributed Lock)

### Q1-1. 왜 분산 락을 사용했나요?

**답변:**
웨이팅 등록 시 대기번호를 순차적으로 발급해야 하는데, 여러 서비스 인스턴스가 동시에 같은 식당에 대기 등록을 처리하면 **중복 대기번호**가 발생할 수 있습니다.

예를 들어:
1. 인스턴스 A가 MAX(waitingNumber) = 5를 읽음
2. 인스턴스 B도 MAX(waitingNumber) = 5를 읽음
3. 둘 다 waitingNumber = 6으로 저장 → 중복!

이를 방지하기 위해 **식당 ID 기준으로 분산 락**을 걸어 동시 등록을 순차 처리합니다.

```java
@DistributedLock(key = "'waiting:restaurant:' + #request.restaurantId()")
public RegisterWaitingResponse register(RegisterWaitingRequest request) {
    return waitingService.registerInternal(request);
}
```

---

### Q1-2. 분산 락의 구현 방식을 설명해주세요.

**답변:**
Redisson 클라이언트와 AOP를 활용해 구현했습니다.

```java
@Around("@annotation(DistributedLock)")
public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
    // 1. SpEL로 동적 키 생성
    String key = "LOCK:" + parseSpEL(distributedLock.key());

    RLock rLock = redissonClient.getLock(key);

    try {
        // 2. 락 획득 시도 (waitTime: 5초, leaseTime: 3초)
        boolean available = rLock.tryLock(5, 3, TimeUnit.SECONDS);

        if (!available) {
            throw new RuntimeException("요청량이 많아 처리가 지연됩니다.");
        }

        // 3. 비즈니스 로직 실행
        return joinPoint.proceed();

    } finally {
        // 4. 락 해제 (소유권 확인)
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
        }
    }
}
```

**핵심 설정:**
- `waitTime: 5초` - 락 획득 대기 시간 (이후 fail-fast)
- `leaseTime: 3초` - 락 자동 해제 시간 (데드락 방지)
- `@Order(1)` - @Transactional 전에 실행 (락 → 트랜잭션 순서)

---

### Q1-3. 락 획득에 실패하면 어떻게 되나요?

**답변:**
5초간 대기 후에도 락을 획득하지 못하면 **RuntimeException**을 던지고 클라이언트에 "요청량이 많습니다" 메시지를 반환합니다.

이는 **Fail-Fast 전략**입니다:
- 무한정 대기하면 스레드 풀 고갈
- 클라이언트가 재시도할 수 있도록 빠르게 응답
- 서버 리소스 보호

---

### Q1-4. leaseTime이 만료되기 전에 비즈니스 로직이 끝나지 않으면?

**답변:**
Redisson의 **Lock Watchdog** 메커니즘이 자동으로 락을 연장합니다.

- leaseTime을 명시하지 않으면 기본 30초 + Watchdog이 10초마다 갱신
- leaseTime을 명시하면 Watchdog이 비활성화되므로, 비즈니스 로직이 leaseTime 내에 완료되어야 함

저희 시스템에서는 leaseTime을 3초로 설정했는데, 웨이팅 등록은 일반적으로 50ms 내에 완료되므로 충분합니다.

---

### Q1-5. 분산 락의 한계점은 무엇인가요?

**답변:**

| 한계점 | 설명 | 대응 방안 |
|--------|------|-----------|
| **Single Point of Failure** | Redis 장애 시 락 불가 | Redis Sentinel/Cluster 구성 |
| **락 경합** | 같은 식당에 동시 요청이 몰리면 대기 발생 | waitTime 내 fail-fast |
| **Clock Drift** | 서버 시간 불일치 시 문제 | NTP 동기화 필수 |
| **네트워크 파티션** | 락 획득 후 네트워크 단절 | leaseTime으로 자동 해제 |

**심화:** Redlock 알고리즘으로 Redis 클러스터 환경에서 더 안전한 분산 락 구현 가능 (N/2+1 노드에서 락 획득 필요)

---

### Q1-6. DB의 비관적 락 대신 Redis 분산 락을 선택한 이유는?

**답변:**

| 구분 | DB 비관적 락 | Redis 분산 락 |
|------|-------------|---------------|
| **성능** | DB 커넥션 점유 | 별도 Redis 연결 |
| **확장성** | DB가 병목 | Redis는 인메모리로 빠름 |
| **범위** | 단일 DB 트랜잭션 | 여러 서비스/DB 걸쳐 가능 |
| **락 해제** | 트랜잭션 종료 시 | 명시적 해제 필요 |

대용량 트래픽에서는 **DB 커넥션 풀이 빠르게 고갈**될 수 있어 Redis 분산 락을 선택했습니다.

---

## 2. Outbox 패턴

### Q2-1. Outbox 패턴이 무엇이고 왜 사용했나요?

**답변:**
분산 시스템에서 **DB 트랜잭션과 이벤트 발행의 원자성**을 보장하기 위한 패턴입니다.

**문제 상황:**
```java
@Transactional
public void register() {
    waitingRepository.save(waiting);  // 1. DB 저장 성공
    kafkaTemplate.send(event);        // 2. Kafka 발행 실패 → 이벤트 유실!
}
```

**Outbox 패턴 적용:**
```java
@Transactional
public void register() {
    waitingRepository.save(waiting);           // 1. 비즈니스 데이터 저장
    outboxRepository.save(OutboxEvent.of(...)); // 2. 이벤트도 같은 트랜잭션에 저장
    // → 둘 다 성공하거나 둘 다 실패 (원자성 보장)
}

// 별도 스케줄러 (Polling Publisher)
@Scheduled(fixedDelay = 3000)
public void publishEvents() {
    List<OutboxEvent> events = outboxRepository.findByPublishedFalse();
    for (OutboxEvent event : events) {
        kafkaTemplate.send(event.getPayload());
        event.markAsPublished();
    }
}
```

---

### Q2-2. Outbox 패턴의 전달 보장 수준은?

**답변:**
**At-Least-Once (최소 1회)** 전달을 보장합니다.

| 시나리오 | 결과 | 복구 방법 |
|----------|------|-----------|
| Kafka 발행 성공, DB 업데이트 성공 | 정상 | - |
| Kafka 발행 성공, DB 업데이트 실패 | 중복 발행 가능 | Consumer 멱등성 필요 |
| Kafka 발행 실패 | 이벤트 유지 | 다음 폴링에서 재시도 |
| 서비스 크래시 | OutboxEvent 유지 | 재시작 후 스케줄러가 복구 |

**Consumer 멱등성 구현:**
```java
@KafkaListener(topics = "waiting-events")
public void handle(WaitingEvent event) {
    // 이미 처리된 이벤트인지 확인
    if (notificationRepository.existsByEventId(event.eventId())) {
        log.info("중복 이벤트 무시: {}", event.eventId());
        return;
    }
    // 처리 로직...
}
```

---

### Q2-3. Polling 방식의 단점과 대안은?

**답변:**

**Polling 방식 단점:**
- 주기적 DB 쿼리 → 부하 발생
- 폴링 주기(3초)만큼 지연 발생

**대안: CDC (Change Data Capture)**
- Debezium 같은 도구로 DB 변경 로그(WAL) 감지
- 실시간 이벤트 발행 가능
- 추가 인프라 필요

저희 프로젝트에서는 **단순성과 운영 편의성**을 위해 Polling 방식을 선택했습니다. 3초 지연은 웨이팅 알림에서 허용 가능한 수준입니다.

---

### Q2-4. Outbox 테이블이 무한정 커지면 어떻게 하나요?

**답변:**
발행 완료된 이벤트는 **주기적으로 삭제하거나 아카이빙**합니다.

```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
public void cleanupOutbox() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(7);
    outboxRepository.deleteByPublishedTrueAndCreatedAtBefore(threshold);
}
```

또는 파티셔닝된 테이블을 사용해 오래된 파티션을 DROP하는 방식도 있습니다.

---

## 3. Kafka 이벤트 처리

### Q3-1. Kafka Consumer의 배치 처리를 사용한 이유는?

**답변:**
대용량 트래픽에서 **DB 쓰기 횟수를 줄이고 처리량을 높이기** 위해서입니다.

```yaml
app:
  kafka:
    listener:
      type: batch
      consumer:
        max-poll-records: 500
```

```java
@KafkaListener(topics = "waiting-events")
public void handle(List<WaitingEvent> events) {  // 배치로 수신
    // 500개 이벤트를 한 번에 처리
    List<Notification> notifications = events.stream()
        .map(this::toNotification)
        .toList();

    notificationRepository.saveAll(notifications);  // Bulk Insert
}
```

**성능 비교:**
- 개별 처리: 500 INSERT = 500 DB 왕복
- 배치 처리: 500 INSERT = 1 DB 왕복 (JDBC Batch)

---

### Q3-2. Consumer가 처리 중 실패하면 어떻게 되나요?

**답변:**
**재시도 후 DLQ(Dead Letter Queue)로 이동**합니다.

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    // 1. DLQ로 보내는 recoverer
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(template);

    // 2. 재시도 정책: 1초 간격, 최대 3회
    FixedBackOff backOff = new FixedBackOff(1000L, 3L);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

**처리 흐름:**
```
이벤트 수신 → 처리 실패 → 1초 후 재시도 → 실패 → 1초 후 재시도 → 실패
→ DLQ(topic.DLT)로 이동 → DLQ Consumer가 별도 처리/알림
```

---

### Q3-3. Kafka 파티션 전략은 어떻게 설계했나요?

**답변:**
**식당 ID를 파티션 키**로 사용해 같은 식당의 이벤트는 같은 파티션으로 보냅니다.

```java
kafkaTemplate.send(topic, String.valueOf(restaurantId), event);
//                        ^^^^^^^^^^^^^^^^^^^^^^^^
//                        파티션 키 (같은 키 = 같은 파티션)
```

**이점:**
- 같은 식당의 이벤트는 **순서 보장**
- 다른 식당끼리는 **병렬 처리** 가능

**파티션 수 결정:**
- Consumer 인스턴스 수 × 2 정도
- 너무 많으면 오버헤드, 너무 적으면 병렬성 저하

---

### Q3-4. Exactly-Once 전달이 필요하면 어떻게 하나요?

**답변:**
Kafka의 **Transactional Producer + Idempotent Consumer** 조합으로 구현 가능합니다.

```java
// Producer 설정
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "waiting-producer-1");

// 트랜잭션 발행
kafkaTemplate.executeInTransaction(operations -> {
    operations.send(topic, event);
    return true;
});
```

하지만 **성능 오버헤드**가 있어서, 저희 시스템에서는 At-Least-Once + Consumer 멱등성으로 충분하다고 판단했습니다.

---

## 4. Redis 활용

### Q4-1. Redis SortedSet으로 대기 순번을 관리한 이유는?

**답변:**
**O(log n) 시간복잡도**로 실시간 순위 조회가 가능하기 때문입니다.

```java
// 대기 등록 시 SortedSet에 추가
RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet("waiting:ranking:1");
set.add(waitingNumber, waitingId);  // Score: 대기번호, Member: 대기 ID

// 내 순위 조회 (O(log n))
Integer rank = set.rank(waitingId);  // 0-indexed
```

**DB 조회 vs Redis 조회:**
| 방식 | 쿼리 | 시간복잡도 |
|------|------|-----------|
| DB | `SELECT COUNT(*) WHERE waitingNumber < ?` | O(n) |
| Redis SortedSet | `ZRANK key member` | O(log n) |

대기 인원이 1000명이면 DB는 1000건 스캔, Redis는 ~10회 비교로 끝납니다.

---

### Q4-2. Redis 데이터가 유실되면 어떻게 복구하나요?

**답변:**
**Self-Healing 메커니즘**을 구현했습니다.

```java
public Long getRank(Long waitingId) {
    // 1. Redis 조회 시도
    Long rank = rankingRepository.getRank(waitingUser);

    // 2. Redis Miss (데이터 유실)
    if (rank == null) {
        // 3. DB에서 순위 계산
        long count = waitingRepository.countAhead(restaurantId, waitingNumber);
        rank = count + 1;

        // 4. Redis에 다시 적재
        rankingRepository.add(waitingUser);
        log.info("Self-Healing: Redis 데이터 복구 완료");
    }

    return rank;
}
```

**Redis 영속성 옵션:**
- RDB: 주기적 스냅샷 (데이터 유실 가능)
- AOF: 모든 쓰기 로깅 (복구 가능하지만 느림)

저희는 Self-Healing으로 충분하다고 판단해 별도 영속성 설정은 하지 않았습니다.

---

### Q4-3. Cache-Aside 패턴을 설명해주세요.

**답변:**
애플리케이션이 캐시와 DB를 직접 관리하는 패턴입니다.

```java
public String getRestaurantName(Long restaurantId) {
    String key = "restaurant:name:" + restaurantId;

    // 1. Cache Hit
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return cached;  // 캐시에서 반환
    }

    // 2. Cache Miss → DB 조회
    String name = restaurantClient.getRestaurant(restaurantId).name();

    // 3. 캐시에 저장 (TTL 24시간)
    redisTemplate.opsForValue().set(key, name, Duration.ofHours(24));

    return name;
}
```

**캐시 갱신 전략:**
- TTL 만료 시 자동 갱신
- 식당 정보 변경 시 Kafka 이벤트로 캐시 무효화

---

### Q4-4. 캐시 일관성 문제는 어떻게 해결했나요?

**답변:**
**이벤트 기반 캐시 무효화**를 사용합니다.

```java
// Restaurant Service: 정보 변경 시 이벤트 발행
@Transactional
public void updateRestaurant(Long id, String newName) {
    restaurant.updateName(newName);

    // Outbox에 이벤트 저장
    OutboxEvent event = OutboxEvent.builder()
        .aggregateType("RESTAURANT")
        .eventType("UPDATED")
        .payload(toJson(new RestaurantUpdatedEvent(id, newName)))
        .build();
    outboxRepository.save(event);
}

// Waiting Service: 이벤트 수신 시 캐시 갱신
@KafkaListener(topics = "restaurant-events")
public void handleRestaurantUpdate(RestaurantUpdatedEvent event) {
    restaurantCacheService.updateCache(event.id(), event.newName());
}
```

**일관성 수준:**
- 최종적 일관성 (Eventual Consistency)
- 이벤트 전파까지 수 초간 불일치 가능
- 웨이팅 시스템에서는 허용 가능한 수준

---

### Q4-5. Redis 클러스터 환경에서 주의할 점은?

**답변:**

| 주의점 | 설명 | 대응 |
|--------|------|------|
| **KEYS 명령** | 전체 노드 스캔 → 성능 저하 | SCAN 사용 |
| **Multi-key 연산** | 다른 슬롯 키 연산 불가 | Hash Tag 사용 `{restaurant}:1:name` |
| **Lua Script** | 모든 키가 같은 슬롯에 있어야 함 | 키 설계 시 고려 |
| **장애 복구** | 슬롯 마이그레이션 중 일시적 불가 | 재시도 로직 |

---

## 5. Resilience 패턴

### Q5-1. Circuit Breaker를 사용한 이유와 설정을 설명해주세요.

**답변:**
외부 서비스(Restaurant Service) 장애가 전파되는 것을 방지하기 위해 사용했습니다.

```java
CircuitBreakerConfig.custom()
    .failureRateThreshold(50)        // 실패율 50% 초과 시 OPEN
    .waitDurationInOpenState(Duration.ofSeconds(1))  // OPEN 상태 1초 유지
    .slidingWindowSize(100)          // 최근 100개 요청 기준
    .build();
```

**상태 전이:**
```
CLOSED (정상)
    ↓ 실패율 50% 초과
OPEN (차단) → 모든 요청 즉시 실패 (Fail-Fast)
    ↓ 1초 후
HALF_OPEN (시험) → 일부 요청 허용
    ↓ 성공 시
CLOSED (복구)
```

---

### Q5-2. Bulkhead 패턴은 무엇이고 왜 사용했나요?

**답변:**
**동시 요청 수를 제한**해서 하나의 서비스가 전체 리소스를 독점하는 것을 방지합니다.

```yaml
resilience4j:
  bulkhead:
    instances:
      restaurantService:
        maxConcurrentCalls: 20   # 최대 동시 20개
        maxWaitDuration: 100ms   # 대기 시간 초과 시 실패
```

```java
@Bulkhead(name = "restaurantService", fallbackMethod = "fallback")
public String getRestaurantName(Long id) {
    return restaurantClient.getRestaurant(id).name();
}

public String fallback(Long id, Throwable t) {
    if (t instanceof BulkheadFullException) {
        return "서버가 바쁩니다. 잠시 후 시도해주세요.";
    }
    return "알 수 없는 식당";
}
```

**효과:**
- Restaurant Service 호출이 느려져도 최대 20개만 대기
- 나머지는 빠르게 Fallback 응답
- Waiting Service 전체 스레드 풀 보호

---

### Q5-3. Timeout, Retry, Circuit Breaker의 적용 순서는?

**답변:**
**Retry → Circuit Breaker → Timeout** 순서로 적용합니다.

```
요청 → Timeout(4초) → CircuitBreaker → Retry(3회) → 실제 호출
```

```java
@Retry(name = "restaurantService", fallbackMethod = "fallback")
@CircuitBreaker(name = "restaurantService", fallbackMethod = "fallback")
@TimeLimiter(name = "restaurantService")
public CompletableFuture<String> getRestaurantName(Long id) {
    return CompletableFuture.supplyAsync(() ->
        restaurantClient.getRestaurant(id).name()
    );
}
```

**실행 흐름:**
1. Timeout이 전체 작업 시간 제한 (4초)
2. Circuit Breaker가 실패율 모니터링
3. Retry가 일시적 실패 시 재시도
4. 모두 실패하면 Fallback 실행

---

### Q5-4. Rate Limiter는 언제 사용하나요?

**답변:**
**API 남용 방지**나 **외부 서비스 보호**를 위해 사용합니다.

```yaml
resilience4j:
  ratelimiter:
    instances:
      api:
        limitForPeriod: 100      # 1초당 100개 요청
        limitRefreshPeriod: 1s
        timeoutDuration: 0s      # 대기 없이 즉시 실패
```

저희 프로젝트에서는 API Gateway에서 Rate Limiting을 처리하므로 서비스 레벨에서는 사용하지 않았습니다.

---

## 6. 페이지네이션

### Q6-1. Offset 방식 대신 Cursor 방식을 선택한 이유는?

**답변:**

| 구분 | Offset | Cursor |
|------|--------|--------|
| **쿼리** | `OFFSET 1000 LIMIT 20` | `WHERE id > 1000 LIMIT 20` |
| **성능** | 1000개 스킵 후 20개 반환 | 인덱스로 바로 접근 |
| **일관성** | 중간 삽입 시 중복/누락 | 안정적 |

**Offset 문제 예시:**
1. 1페이지 조회 (1~20번)
2. 새 대기 등록 (21번 → 1번으로 밀림)
3. 2페이지 조회 → 20번이 다시 나옴 (중복)

**Cursor 방식:**
```java
@Query("""
    SELECT w FROM Waiting w
    WHERE w.restaurantId = :restaurantId
      AND (:cursor IS NULL OR w.waitingNumber > :cursor)
    ORDER BY w.waitingNumber ASC
    LIMIT :size
""")
List<Waiting> findWithCursor(...);
```

---

### Q6-2. hasNext를 어떻게 판단하나요?

**답변:**
**size + 1개를 조회**해서 다음 페이지 존재 여부를 판단합니다.

```java
public CursorPageResponse<WaitingListResponse> getWaitingList(
        Long restaurantId, Integer cursor, int size) {

    // 1. size + 1개 조회
    List<Waiting> waitings = waitingRepository.findWithCursor(
        restaurantId, cursor, size + 1  // 21개 조회
    );

    // 2. 21개가 왔으면 다음 페이지 있음
    boolean hasNext = waitings.size() > size;

    // 3. 실제 반환은 size개만
    List<Waiting> content = hasNext
        ? waitings.subList(0, size)  // 20개만
        : waitings;

    // 4. 다음 커서 = 마지막 항목의 waitingNumber
    String nextCursor = hasNext
        ? String.valueOf(content.getLast().getWaitingNumber())
        : null;

    return CursorPageResponse.of(content, nextCursor, hasNext, ...);
}
```

---

### Q6-3. Cursor 방식의 단점은 무엇인가요?

**답변:**

| 단점 | 설명 | 대응 |
|------|------|------|
| **특정 페이지 접근 불가** | 3페이지로 바로 점프 불가 | 무한 스크롤 UI에 적합 |
| **정렬 기준 변경 어려움** | Cursor 필드가 고정됨 | 정렬 기준별 인덱스 필요 |
| **역방향 탐색** | 이전 페이지로 가기 어려움 | 양방향 Cursor 구현 필요 |

웨이팅 리스트는 **최신순 무한 스크롤**이므로 Cursor 방식이 적합합니다.

---

## 7. ID 생성 전략

### Q7-1. Snowflake 알고리즘을 선택한 이유는?

**답변:**
**분산 환경에서 중앙 코디네이터 없이 유일한 ID를 생성**하기 위해서입니다.

| 방식 | 장점 | 단점 |
|------|------|------|
| Auto Increment | 간단 | 단일 DB 의존, 분산 불가 |
| UUID | 분산 가능 | 128bit, 정렬 불가 |
| **Snowflake** | 분산 가능, 64bit, 시간순 정렬 | 시간 동기화 필요 |

---

### Q7-2. Snowflake ID 구조를 설명해주세요.

**답변:**

```
| 1 bit | 41 bits      | 10 bits  | 12 bits  |
| 미사용 | 타임스탬프   | 노드 ID  | 시퀀스   |
```

```java
private synchronized long nextId() {
    long currentTimestamp = System.currentTimeMillis();

    if (currentTimestamp == lastTimestamp) {
        // 같은 밀리초: 시퀀스 증가
        sequence = (sequence + 1) & 4095;  // 0~4095
        if (sequence == 0) {
            // 시퀀스 오버플로우: 다음 밀리초까지 대기
            currentTimestamp = waitNextMillis(currentTimestamp);
        }
    } else {
        // 새 밀리초: 시퀀스 리셋
        sequence = 0;
    }

    lastTimestamp = currentTimestamp;

    return ((currentTimestamp - EPOCH) << 22)
         | (nodeId << 12)
         | sequence;
}
```

**용량:**
- 41bit 타임스탬프: 약 69년
- 10bit 노드 ID: 1024개 인스턴스
- 12bit 시퀀스: 밀리초당 4096개 ID

---

### Q7-3. 시스템 시간이 역행하면 어떻게 되나요?

**답변:**
**ID 충돌을 방지하기 위해 예외를 던집니다.**

```java
if (currentTimestamp < lastTimestamp) {
    throw new IllegalStateException("Clock moved backwards!");
}
```

**대응 방안:**
1. NTP로 시간 동기화 유지
2. 시간 역행 감지 시 서비스 일시 중단
3. 또는 lastTimestamp까지 대기 (짧은 역행의 경우)

---

### Q7-4. 노드 ID는 어떻게 할당하나요?

**답변:**
현재는 **SecureRandom으로 랜덤 생성**합니다.

```java
private long createNodeId() {
    return new SecureRandom().nextInt() & 1023;  // 0~1023
}
```

**운영 환경에서는:**
- 환경변수로 명시적 할당: `NODE_ID=1`
- Kubernetes Pod 이름에서 추출
- ZooKeeper/etcd로 동적 할당

1024개 노드 중 랜덤 충돌 확률은 낮지만, 대규모 운영에서는 명시적 할당이 안전합니다.

---

## 8. Scale-out 설계

### Q8-1. 이 시스템은 어떻게 Scale-out이 가능한가요?

**답변:**

| 컴포넌트 | Scale-out 지원 | 방법 |
|----------|---------------|------|
| **Waiting Service** | ✅ | Stateless, 인스턴스 추가 |
| **분산 락** | ✅ | Redis가 중앙 조정 |
| **스케줄러** | ✅ | ShedLock으로 중복 방지 |
| **세션** | ✅ | Redis에 저장 |
| **ID 생성** | ✅ | Snowflake NodeId로 구분 |

---

### Q8-2. ShedLock의 동작 방식을 설명해주세요.

**답변:**
여러 인스턴스에서 **같은 스케줄러가 중복 실행되는 것을 방지**합니다.

```java
@Scheduled(cron = "0 0 0 * * *")
@SchedulerLock(
    name = "cleanupWaiting",
    lockAtMostFor = "50s",    // 최대 락 유지 시간
    lockAtLeastFor = "30s"    // 최소 락 유지 시간
)
public void cleanup() {
    // 하나의 인스턴스만 실행
}
```

**Redis에 저장되는 락 정보:**
```json
{
  "name": "cleanupWaiting",
  "lockedAt": "2024-01-14T00:00:00",
  "lockUntil": "2024-01-14T00:00:50"
}
```

**lockAtLeastFor의 역할:**
- 작업이 빨리 끝나도 30초간 락 유지
- 다른 인스턴스의 중복 실행 방지

---

### Q8-3. 10,000 TPS를 처리하려면 어떻게 해야 하나요?

**답변:**

**현재 병목 분석:**
1. DB 쓰기: ~1000 TPS (단일 PostgreSQL)
2. Redis 분산 락: ~10,000 TPS
3. Kafka: ~100,000 TPS

**Scale-out 전략:**

| 구간 | 현재 | 개선 방안 |
|------|------|-----------|
| **DB** | 단일 Primary | Read Replica 추가, 샤딩 |
| **Redis** | 단일 | Redis Cluster |
| **Kafka** | 단일 Broker | Broker 추가, 파티션 증가 |
| **Application** | 수동 배포 | K8s HPA (Auto Scaling) |

**추가 최적화:**
- Write는 CQRS 패턴으로 분리
- Event Sourcing으로 쓰기 최적화
- 읽기는 Redis 캐시로 처리

---

## 9. 데이터베이스

### Q9-1. 트랜잭션 격리 수준은 어떻게 설정했나요?

**답변:**
PostgreSQL 기본값인 **Read Committed**를 사용합니다.

| 격리 수준 | 발생 가능 | 성능 |
|-----------|----------|------|
| Read Uncommitted | Dirty Read | 빠름 |
| **Read Committed** | Non-Repeatable Read | 보통 |
| Repeatable Read | Phantom Read | 느림 |
| Serializable | 없음 | 매우 느림 |

웨이팅 시스템에서는:
- 대기번호 중복: 분산 락으로 방지
- 동시성 문제: 낙관적 락(@Version) 또는 분산 락으로 처리

---

### Q9-2. 인덱스는 어떻게 설계했나요?

**답변:**

```sql
-- 1. 중복 등록 체크 (Unique Composite Index)
CREATE UNIQUE INDEX idx_waiting_restaurant_phone_status
ON waiting(restaurant_id, guest_phone, status)
WHERE status = 'WAITING';

-- 2. 대기 목록 조회 (Cursor Pagination)
CREATE INDEX idx_waiting_restaurant_status_number
ON waiting(restaurant_id, status, waiting_number);

-- 3. 내 앞 대기 수 계산
CREATE INDEX idx_waiting_count_ahead
ON waiting(restaurant_id, status, waiting_number);
```

**인덱스 선택 기준:**
- WHERE 조건에 자주 사용되는 컬럼
- ORDER BY에 사용되는 컬럼
- 카디널리티(고유값 수)가 높은 컬럼

---

### Q9-3. Connection Pool 설정은 어떻게 했나요?

**답변:**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # 최대 커넥션 수
      minimum-idle: 10          # 유휴 커넥션 유지
      connection-timeout: 30000 # 커넥션 획득 대기 (30초)
      idle-timeout: 600000      # 유휴 커넥션 제거 (10분)
      max-lifetime: 1800000     # 커넥션 최대 수명 (30분)
```

**Pool Size 공식:**
```
connections = (core_count * 2) + effective_spindle_count
```

일반적으로 CPU 코어 수의 2~4배가 적절합니다. 너무 많으면 DB에 부하, 너무 적으면 대기 발생.

---

## 10. 장애 대응

### Q10-1. Redis 장애 시 서비스는 어떻게 되나요?

**답변:**

| 기능 | Redis 의존 | 장애 시 동작 |
|------|-----------|-------------|
| **분산 락** | 필수 | 등록 실패 (503 반환) |
| **대기 순번** | 캐시 | Self-Healing으로 DB 조회 |
| **식당 이름** | 캐시 | Fallback 메시지 반환 |

**분산 락 장애 대응:**
```java
try {
    boolean locked = rLock.tryLock(5, 3, TimeUnit.SECONDS);
    if (!locked) throw new ServiceUnavailableException();
} catch (RedisConnectionException e) {
    // Redis 연결 실패
    log.error("Redis 연결 실패", e);
    throw new ServiceUnavailableException("잠시 후 다시 시도해주세요.");
}
```

---

### Q10-2. Kafka 장애 시 이벤트는 어떻게 되나요?

**답변:**
**Outbox 패턴 덕분에 이벤트가 유실되지 않습니다.**

```
1. 이벤트 → OutboxEvent 테이블에 저장 (DB 트랜잭션)
2. Kafka 장애 → 발행 실패
3. OutboxEvent의 published = false 유지
4. Kafka 복구 후 → 다음 폴링에서 재발행
```

---

### Q10-3. DB 장애 시 어떻게 대응하나요?

**답변:**

**Read Replica 구성:**
```java
@Transactional(readOnly = true)  // Read Replica로 라우팅
public WaitingDetailResponse getWaiting(Long id) {
    return waitingRepository.findById(id)...;
}
```

**Primary 장애 시:**
1. 쓰기 작업 실패 (등록, 호출 등)
2. 읽기 작업은 Replica로 계속 가능
3. Failover 후 Primary 전환

**Connection Pool 소진 방지:**
```yaml
hikari:
  connection-timeout: 3000  # 3초 내 커넥션 못 얻으면 실패
```

---

### Q10-4. 전체 시스템 장애 복구 절차는?

**답변:**

**복구 우선순위:**
1. **PostgreSQL** - 핵심 데이터
2. **Redis** - 분산 락, 캐시
3. **Kafka** - 이벤트 처리
4. **Application** - 서비스 인스턴스

**복구 후 검증:**
```bash
# 1. DB 연결 확인
SELECT 1;

# 2. Redis 연결 확인
PING

# 3. Kafka 연결 확인
kafka-topics.sh --list

# 4. 헬스체크 엔드포인트
curl http://localhost:8080/actuator/health
```

**데이터 정합성 확인:**
- OutboxEvent에서 published=false인 이벤트 확인
- Redis 대기열과 DB 대기 수 비교
- Consumer Lag 확인

---

## 보너스: 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway + Load Balancer              │
│                    (Rate Limiting, Auth)                    │
└──────────────────┬──────────────────────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼───┐     ┌────▼────┐    ┌────▼────┐
│ App 1 │     │ App 2   │    │ App 3   │  ← Stateless
└───┬───┘     └────┬────┘    └────┬────┘
    │              │              │
    └──────────────┼──────────────┘
                   │
    ┌──────────────┼──────────────┬──────────────┐
    │              │              │              │
┌───▼───┐     ┌────▼────┐   ┌────▼────┐    ┌────▼────┐
│  DB   │     │  Redis  │   │  Kafka  │    │ Eureka  │
│Primary│     │ Cluster │   │ Cluster │    │ Server  │
└───┬───┘     └─────────┘   └────┬────┘    └─────────┘
    │                            │
┌───▼───┐              ┌─────────┴─────────┐
│  DB   │              │                   │
│Replica│         ┌────▼────┐        ┌─────▼─────┐
└───────┘         │Notifi-  │        │Restaurant │
                  │cation   │        │ Service   │
                  │Service  │        └───────────┘
                  └─────────┘
```

---

## 마무리 팁

1. **코드를 보여달라고 하면** - GitHub 링크 또는 노트북에 프로젝트 준비
2. **성능 수치를 물으면** - 부하 테스트 결과 (JMeter, Gatling) 준비
3. **개선점을 물으면** - CDC, Event Sourcing, CQRS 언급
4. **왜 이 기술을 선택했는지** - 항상 Trade-off 관점으로 설명

행운을 빕니다! 🍀
