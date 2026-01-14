# 대용량 트래픽 & 동시성 프로젝트 아이디어

> Waiting 시스템 외에 도전해볼 만한 주제들

---

## 추천 주제 TOP 5

| 순위 | 주제 | 난이도 | 면접 빈출 | 추천도 |
|------|------|--------|----------|--------|
| 🥇 | 선착순 쿠폰 발급 | ⭐⭐ | ⭐⭐⭐ | 강력 추천 |
| 🥈 | 좌석 예약 (콘서트/영화) | ⭐⭐⭐ | ⭐⭐⭐ | 추천 |
| 🥉 | 포인트/머니 시스템 | ⭐⭐⭐ | ⭐⭐ | 추천 |
| 4위 | 실시간 투표/설문 | ⭐⭐ | ⭐⭐ | 괜찮음 |
| 5위 | 분산 Rate Limiter | ⭐⭐ | ⭐⭐ | 괜찮음 |

---

## 1. 선착순 쿠폰 발급 시스템 (강력 추천)

### 왜 추천하는가?
- 면접 단골 질문: "동시에 10만 명이 쿠폰 요청하면 어떻게 처리?"
- 기술 스택이 현재 프로젝트(Redis, Kafka, 분산 락)와 겹침
- 명확한 정합성 기준: "정확히 10,000개만 발급"

### 시나리오
```
- 쿠폰 수량: 10,000개
- 동시 요청: 100,000명
- 제한: 1인 1매
- 요구사항: 정확히 10,000개만 발급, 초과/중복 발급 절대 불가
```

### 핵심 동시성 문제

| 문제 | 설명 | 발생 상황 |
|------|------|----------|
| **재고 초과 발급** | 10,000개 넘게 발급됨 | 동시 요청 시 재고 체크 후 차감 전에 다른 요청 끼어듦 |
| **중복 발급** | 같은 사용자가 여러 장 받음 | 발급 여부 체크와 발급 사이에 다른 요청 끼어듦 |
| **DB 병목** | DB 커넥션 고갈 | 10만 건 동시 INSERT 시도 |

### 해결 방안

#### 방법 1: Redis Lua Script (원자적 연산)
```java
String luaScript = """
    -- KEYS[1]: 재고 키, KEYS[2]: 발급자 Set
    -- ARGV[1]: 사용자 ID

    -- 1. 재고 확인
    local stock = tonumber(redis.call('GET', KEYS[1]))
    if stock == nil or stock <= 0 then
        return -1  -- 재고 없음
    end

    -- 2. 중복 발급 확인
    local alreadyIssued = redis.call('SISMEMBER', KEYS[2], ARGV[1])
    if alreadyIssued == 1 then
        return -2  -- 이미 발급됨
    end

    -- 3. 원자적으로 재고 차감 + 발급자 기록
    redis.call('DECR', KEYS[1])
    redis.call('SADD', KEYS[2], ARGV[1])

    return 1  -- 발급 성공
""";

// 실행
Long result = redisTemplate.execute(
    new DefaultRedisScript<>(luaScript, Long.class),
    List.of("coupon:stock:EVENT001", "coupon:issued:EVENT001"),
    userId
);
```

#### 방법 2: 요청 큐잉 (Kafka)
```
사용자 요청 → Kafka 토픽에 발행 → Consumer가 순차 처리
                                    ↓
                            Redis로 재고/중복 체크
                                    ↓
                            발급 결과 DB 저장
```

### 아키텍처
```
┌─────────┐     ┌─────────────┐     ┌─────────┐
│  Client │────▶│   Gateway   │────▶│  Coupon │
└─────────┘     │ Rate Limit  │     │ Service │
                └─────────────┘     └────┬────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    │                    │                    │
               ┌────▼────┐         ┌─────▼─────┐        ┌─────▼─────┐
               │  Redis  │         │   Kafka   │        │    DB     │
               │ - 재고   │         │ - 요청 큐  │        │ - 발급 이력│
               │ - 중복Set│         │ - 비동기   │        │ - 쿠폰 정보│
               └─────────┘         └───────────┘        └───────────┘
```

### 기술 스택
- Redis: 재고 관리, 중복 체크 (Lua Script)
- Kafka: 요청 큐잉, 비동기 DB 저장
- Spring Boot: API 서버
- PostgreSQL: 발급 이력 영속화

---

## 2. 좌석 예약 시스템 (콘서트/영화)

### 시나리오
```
- 공연: BTS 콘서트
- 좌석: 5,000석
- 동시 접속: 50,000명
- 제한: 좌석 선택 후 5분 내 결제 필요
```

### 핵심 동시성 문제

| 문제 | 설명 |
|------|------|
| **같은 좌석 동시 선택** | A, B 둘 다 A-15 좌석 선택 성공 |
| **점유 해제 타이밍** | 결제 실패 시 점유 해제가 안 됨 |
| **유령 점유** | 점유만 하고 결제 안 하는 어뷰징 |

### 해결 방안

#### 임시 점유 (Redis SETNX + TTL)
```java
public boolean tryHoldSeat(String eventId, String seatId, String userId) {
    String key = "seat:hold:" + eventId + ":" + seatId;

    // SETNX: 없을 때만 설정 (원자적)
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, userId, Duration.ofMinutes(5));

    return Boolean.TRUE.equals(success);
}

public void releaseSeat(String eventId, String seatId, String userId) {
    String key = "seat:hold:" + eventId + ":" + seatId;

    // 본인이 점유한 좌석만 해제
    String holder = redisTemplate.opsForValue().get(key);
    if (userId.equals(holder)) {
        redisTemplate.delete(key);
    }
}
```

#### 결제 연동 (Saga 패턴)
```
[좌석 선택]          [결제 요청]          [좌석 확정]
     │                   │                   │
     ▼                   ▼                   ▼
Redis 임시점유 ──────▶ Payment ──────▶ DB 저장
     │                   │                   │
     │              (실패 시)                │
     ◀───────────── 점유 해제 ◀─────────────┘
                   (보상 트랜잭션)
```

### 실시간 UI (WebSocket)
```java
@Service
public class SeatStatusBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastSeatHeld(String eventId, String seatId) {
        messagingTemplate.convertAndSend(
            "/topic/event/" + eventId + "/seats",
            new SeatStatusMessage(seatId, "HELD")
        );
    }

    public void broadcastSeatReleased(String eventId, String seatId) {
        messagingTemplate.convertAndSend(
            "/topic/event/" + eventId + "/seats",
            new SeatStatusMessage(seatId, "AVAILABLE")
        );
    }
}
```

### 아키텍처
```
┌─────────┐    WebSocket     ┌─────────────┐
│  Client │◀────────────────▶│   Booking   │
└─────────┘                  │   Service   │
                             └──────┬──────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
         ┌────▼────┐          ┌─────▼─────┐         ┌─────▼─────┐
         │  Redis  │          │  Payment  │         │    DB     │
         │ 임시점유 │          │  Service  │         │ 예약 확정  │
         │ TTL 5분  │          └───────────┘         └───────────┘
         └─────────┘
```

---

## 3. 포인트/머니 시스템

### 시나리오
```
- 사용자 잔액: 10,000원
- 동시 요청:
  - 결제 A: 7,000원 사용
  - 결제 B: 5,000원 사용
- 기대 결과: 하나만 성공, 하나는 잔액 부족 실패
```

### 핵심 동시성 문제

| 문제 | 설명 |
|------|------|
| **이중 차감** | 잔액 10,000원인데 12,000원 사용됨 |
| **잔액 불일치** | 적립/사용 내역 합계와 잔액이 안 맞음 |
| **Lost Update** | 동시 업데이트 시 한 쪽 변경이 사라짐 |

### 해결 방안

#### 방법 1: 비관적 락 (SELECT FOR UPDATE)
```java
@Repository
public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointAccount p WHERE p.userId = :userId")
    Optional<PointAccount> findByUserIdWithLock(@Param("userId") Long userId);
}

@Transactional
public void usePoint(Long userId, int amount) {
    PointAccount account = repository.findByUserIdWithLock(userId)
        .orElseThrow(() -> new AccountNotFoundException());

    if (account.getBalance() < amount) {
        throw new InsufficientBalanceException();
    }

    account.deduct(amount);
    transactionRepository.save(new PointTransaction(userId, USE, amount));
}
```

#### 방법 2: 낙관적 락 (@Version)
```java
@Entity
public class PointAccount {
    @Id
    private Long id;

    @Version
    private Long version;  // 자동 증가, 충돌 감지

    private int balance;
}

// 동시 수정 시 OptimisticLockException 발생 → 재시도
```

#### 방법 3: 이벤트 소싱 (완벽한 감사 추적)
```java
@Entity
public class PointEvent {
    @Id
    private Long id;
    private Long userId;
    private EventType type;  // EARN, USE, CANCEL
    private int amount;
    private LocalDateTime occurredAt;
}

// 잔액 = 모든 이벤트의 합계
public int getBalance(Long userId) {
    return eventRepository.findByUserId(userId).stream()
        .mapToInt(e -> e.getType() == EARN ? e.getAmount() : -e.getAmount())
        .sum();
}
```

### 아키텍처 (이벤트 소싱)
```
[적립 요청]     [사용 요청]
     │              │
     ▼              ▼
┌────────────────────────┐
│    Point Service       │
│  - 잔액 계산 (이벤트 합)│
│  - 이벤트 저장          │
└───────────┬────────────┘
            │
     ┌──────┴──────┐
     │             │
┌────▼────┐  ┌─────▼─────┐
│  Redis  │  │    DB     │
│ 잔액 캐시│  │ 이벤트 저장│
└─────────┘  └───────────┘
```

---

## 4. 실시간 투표/설문 시스템

### 시나리오
```
- 투표 항목: 5개 선택지
- 동시 투표: 10,000명
- 요구사항: 실시간 결과 업데이트, 중복 투표 방지
```

### 해결 방안

#### Redis로 실시간 집계
```java
public void vote(String pollId, String optionId, String oderId) {
    String votedKey = "poll:voted:" + pollId;
    String countKey = "poll:count:" + pollId;

    // 중복 체크 (Set)
    Boolean isNew = redisTemplate.opsForSet().add(votedKey, oderId);
    if (Boolean.FALSE.equals(isNew)) {
        throw new AlreadyVotedException();
    }

    // 투표 수 증가 (Hash)
    redisTemplate.opsForHash().increment(countKey, optionId, 1);

    // 실시간 브로드캐스트
    broadcastResults(pollId);
}

public Map<String, Long> getResults(String pollId) {
    String countKey = "poll:count:" + pollId;
    return redisTemplate.opsForHash().entries(countKey);
}
```

#### Bloom Filter로 중복 체크 (대용량)
```java
// 메모리 효율적 중복 체크 (오탐 가능, 미탐 불가)
BloomFilter<String> votedFilter = BloomFilter.create(
    Funnels.stringFunnel(Charset.defaultCharset()),
    10_000_000,  // 예상 투표자 수
    0.01         // 오탐률 1%
);

public boolean hasVoted(String oderId) {
    return votedFilter.mightContain(oderId);
}
```

---

## 5. 분산 Rate Limiter

### 알고리즘 비교

| 알고리즘 | 특징 | 장점 | 단점 |
|----------|------|------|------|
| **Fixed Window** | 고정 시간 윈도우 | 단순 | 경계 시점 버스트 |
| **Sliding Window Log** | 요청마다 타임스탬프 기록 | 정확 | 메모리 많이 사용 |
| **Sliding Window Counter** | 이전/현재 윈도우 가중 평균 | 균형 | 약간의 오차 |
| **Token Bucket** | 토큰 채워지는 버킷 | 버스트 허용 | 구현 복잡 |
| **Leaky Bucket** | 일정 속도로 처리 | 안정적 출력 | 버스트 불가 |

### Sliding Window Counter 구현
```java
public boolean isAllowed(String clientId, int limit, int windowSec) {
    long now = System.currentTimeMillis();
    long currentWindow = now / (windowSec * 1000);
    long previousWindow = currentWindow - 1;

    String currentKey = "ratelimit:" + clientId + ":" + currentWindow;
    String previousKey = "ratelimit:" + clientId + ":" + previousWindow;

    // 이전 윈도우 카운트
    Integer prevCount = (Integer) redisTemplate.opsForValue().get(previousKey);
    if (prevCount == null) prevCount = 0;

    // 현재 윈도우 카운트
    Integer currCount = (Integer) redisTemplate.opsForValue().get(currentKey);
    if (currCount == null) currCount = 0;

    // 가중 평균 계산
    double windowProgress = (now % (windowSec * 1000)) / (double)(windowSec * 1000);
    double weightedCount = prevCount * (1 - windowProgress) + currCount;

    if (weightedCount >= limit) {
        return false;  // 제한 초과
    }

    // 카운트 증가
    redisTemplate.opsForValue().increment(currentKey);
    redisTemplate.expire(currentKey, Duration.ofSeconds(windowSec * 2));

    return true;
}
```

---

## 최종 추천

### 선착순 쿠폰 시스템을 추천하는 이유

1. **면접 적중률 높음**
   - "동시성 문제 경험 있나요?" → 쿠폰 시스템 설명
   - "Redis 사용 경험?" → Lua Script로 원자적 연산
   - "Kafka 사용 경험?" → 비동기 처리로 DB 부하 분산

2. **현재 프로젝트와 시너지**
   - Waiting System: 대기열 관리
   - Coupon System: 한정 수량 관리
   - 둘 다 "대용량 트래픽 + 동시성" 키워드

3. **구현 난이도 적절**
   - 핵심 로직: Redis Lua Script (1일)
   - API + Kafka 연동 (2일)
   - 테스트 + 문서화 (1일)
   - 총 4~5일이면 완성 가능

4. **확장 가능성**
   - 타임딜/플래시 세일
   - 이벤트 응모
   - 선착순 예약

---

## 다음 단계

쿠폰 시스템을 시작하려면:

1. `apps/coupon-service` 모듈 생성
2. 핵심 기능 구현
   - 쿠폰 발급 (Redis Lua Script)
   - 발급 이력 저장 (Kafka → DB)
   - 발급 조회 API
3. 동시성 테스트 (1000명 동시 요청)
4. 부하 테스트 + 결과 문서화
