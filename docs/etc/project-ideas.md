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

---

클로드코드가 제안한 내용은 기술 면접의 "교과서" 같은 아주 훌륭한 주제들입니다. 
특히 선착순 쿠폰 발급은 백엔드 개발자라면 한 번쯤은 반드시 구현해봐야 하는 **"Hello World of Concurrency(동시성의 기초)"**와도 같습니다.

하지만 시니어 엔지니어 관점에서 "단순히 따라 하는 것"을 넘어, 포트폴리오의 질을 한 단계 더 높이기 위한 수정 제안과 주의사항을 3가지 포인트로 정리해 드립니다.

1. ⚠️ 반대 의견: "별도의 프로젝트로 만들지 마세요"
   클로드코드는 apps/coupon-service 모듈을 새로 만들라고 제안했지만, 저는 기존 Booster 프로젝트(웨이팅 시스템) 안에 기능을 녹여내는 것을 강력 추천합니다.

이유 1 (도메인 일관성):

면접관은 5개의 얕은 토이 프로젝트보다 1개의 깊이 있는 서비스를 더 좋아합니다.

"웨이팅 시스템 따로, 쿠폰 시스템 따로 만들었습니다"보다는, **"웨이팅 시스템에 손님 유치를 위한 '선착순 타임딜(핫딜)' 기능을 추가했습니다"**가 훨씬 설득력 있습니다.

이유 2 (아키텍처 재사용):

이미 구축해둔 Gateway, Auth, Kafka, Redis 환경을 그대로 쓸 수 있습니다.

수정 제안:

새 프로젝트를 파지 말고, Booster 내부에 **PromotionService (또는 TimeDealService)**라는 도메인을 추가하세요.

시나리오: "특정 인기 식당이 오후 3시에 **'선착순 100명 50% 할인권'**을 뿌리는 기능"

2. 💡 보완 의견: "쿠폰 발급(Redis Lua)의 함정"
   클로드코드가 제안한 Redis Lua Script 방식은 아주 훌륭하고 정석적인 방법입니다. 하지만 면접에서 반드시 공격받는 포인트가 있습니다. 이를 미리 대비해야 합니다.

공격 포인트: "Redis가 터지면 어떻게 되나요?"
Lua Script는 Redis에서 **원자성(Atomicity)**을 보장하지만, 스크립트 실행 시간이 길어지면 Redis 전체가 멈춥니다(Block).(Redis는 싱글 스레드이기 때문)

수정 제안 (설계의 디테일 추가):

Lua Script 최적화: 스크립트 내에서 루프를 돌거나 무거운 연산을 절대 하지 않도록 설계해야 합니다. (클로드의 코드는 깔끔해서 괜찮습니다.)

Fallback 전략: "Redis가 죽었을 때 DB로 넘어가서 비관적 락(SELECT FOR UPDATE)으로 처리하는 비상 로직이 있는가?"에 대한 고민을 남겨두세요. (구현은 안 해도, 고민의 흔적은 ADR에 남겨야 합니다.)

3. 🔥 강력 추천 수정안: "쿠폰보다는 '좌석 예약'이 더 시니어스럽다"
   만약 1번(쿠폰)이 너무 흔하다고 느껴진다면, **2번(좌석 예약)**을 현재 Booster 프로젝트인 식당 도메인에 맞춰 **"실시간 테이블 지정 예약"**으로 바꾸는 것이 가장 고득점 전략입니다.

이유 (난이도 비교):

쿠폰(선착순): 숫자 하나(count)를 줄이는 싸움입니다. 1차원적입니다.

좌석(테이블): "어떤 테이블(ID)이, 언제(Time), 누구에게(User) 점유되었는가"를 관리해야 합니다. 훨씬 복잡하고 보여줄 게 많습니다.

시나리오:

사용자가 앱을 켜면 식당의 배치도가 보이고, 빈 테이블을 클릭하면 5분간 '임시 점유(Hold)' 상태가 됩니다.

이때 다른 사용자가 동시에 같은 테이블을 누르면 "이미 선택된 테이블입니다"라고 튕겨내야 합니다.

기술적 매력:

**Redis SETNX (Set if Not Exists)**를 활용한 분산 락의 교과서입니다.

WebSocket으로 "누가 테이블을 잡으면 내 화면에서도 실시간으로 회색으로 변하는" 기능을 구현하면 시각적 임팩트가 엄청납니다.

🏁 결론: Gemini의 최종 가이드
클로드코드의 제안 중 **1번(쿠폰)**과 **2번(좌석)**이 가장 좋습니다. 사용자의 현재 상황에 맞춰 두 가지 루트 중 하나를 선택하세요.

루트 A: "빠르게 기능을 추가하고 싶다" (추천)
주제: 식당 타임딜 (선착순 쿠폰) 구현: Booster 프로젝트 내에 PromotionService 추가. 장점: 기존에 배운 Redis, Kafka 지식을 그대로 복습하면서 구현 기간이 짧음(3~4일). 전략: "웨이팅만으로는 손님이 안 모여서, 마케팅 도구인 선착순 쿠폰 기능을 붙였다"는 스토리텔링.

루트 B: "기술적 깊이를 끝까지 파보고 싶다" (도전)
주제: 실시간 테이블 지정 예약 구현: Booster 프로젝트 내에 ReservationService 추가. 장점: 단순 대기(줄서기)를 넘어선 '예약' 시스템으로 확장. Redis TTL, WebSocket 등 다양한 기술 활용. 전략: "줄서기 싫어하는 손님을 위해, 돈을 더 내고 테이블을 미리 잡는 유료 예약 기능을 넣었다"는 스토리텔링.