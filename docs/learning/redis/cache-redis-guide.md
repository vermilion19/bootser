# 캐시 & Redis 완벽 가이드

## 목차
1. [캐시 기초](#1-캐시-기초)
2. [캐시 전략 (읽기/쓰기)](#2-캐시-전략-읽기쓰기)
3. [캐시 문제와 해결책](#3-캐시-문제와-해결책)
4. [Redis 기초](#4-redis-기초)
5. [Redis Cluster & Sharding](#5-redis-cluster--sharding)
6. [분산 환경 패턴](#6-분산-환경-패턴)
7. [실무 패턴](#7-실무-패턴)

---

## 1. 캐시 기초

### 1.1 Cache란?

**자주 사용하는 데이터를 빠른 저장소에 임시 보관**하여 성능을 향상시키는 기술

```
┌─────────┐      ┌─────────┐      ┌─────────┐
│  Client │ ──── │  Cache  │ ──── │   DB    │
└─────────┘      └─────────┘      └─────────┘
                  (메모리)         (디스크)
                  ~0.1ms           ~10ms

속도 차이: 약 100배
```

### 1.2 Hot Data vs Cold Data

| 구분 | Hot Data | Cold Data |
|------|----------|-----------|
| 정의 | 자주 접근하는 데이터 | 거의 접근하지 않는 데이터 |
| 예시 | 인기 상품, 실시간 랭킹 | 오래된 주문 내역, 탈퇴 회원 |
| 저장소 | 캐시 (Redis) | DB, Archive |
| TTL | 짧게 (분~시간) | 길게 또는 없음 |

```java
// Hot Data: 캐시에 적극적으로
@Cacheable(value = "hotProducts", key = "#productId")
public Product getHotProduct(Long productId) {
    return productRepository.findById(productId);
}

// Cold Data: 캐시 없이 직접 DB
public Order getColdOrder(Long orderId) {
    return orderArchiveRepository.findById(orderId);  // Archive DB
}
```

**Hot/Cold 분리 전략:**
```
┌──────────────────────────────────────────────────┐
│                    요청 빈도                      │
│  ████████████████████  Hot (20%)  → Redis        │
│  ████                  Warm       → Redis + DB   │
│  ██                    Cold (80%) → DB only      │
└──────────────────────────────────────────────────┘
파레토 법칙: 20%의 데이터가 80%의 요청을 처리
```

---

## 2. 캐시 전략 (읽기/쓰기)

### 2.1 Look-Aside (Cache-Aside)

**가장 일반적인 패턴**. 애플리케이션이 캐시와 DB를 직접 관리.

```
읽기 흐름:
┌────────┐  1.조회   ┌────────┐
│  App   │ ───────► │ Cache  │
│        │ ◄─────── │        │
└────────┘  2.Hit?  └────────┘
    │
    │ 3.Miss시
    ▼
┌────────┐  4.저장   ┌────────┐
│   DB   │ ───────► │ Cache  │
└────────┘          └────────┘
```

```java
@Service
public class ProductService {
    private final RedisTemplate<String, Product> redisTemplate;
    private final ProductRepository productRepository;

    public Product getProduct(Long id) {
        String key = "product:" + id;

        // 1. 캐시 조회
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;  // Cache Hit
        }

        // 2. Cache Miss → DB 조회
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));

        // 3. 캐시에 저장
        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));

        return product;
    }
}
```

**Spring @Cacheable 사용:**
```java
@Cacheable(value = "products", key = "#id", unless = "#result == null")
public Product getProduct(Long id) {
    return productRepository.findById(id).orElse(null);
}
```

| 장점 | 단점 |
|------|------|
| 구현 간단 | Cache Miss 시 지연 발생 |
| 읽기 많은 워크로드에 적합 | DB-캐시 불일치 가능 |
| 캐시 장애 시 DB로 Fallback | 첫 요청은 항상 느림 |

### 2.2 Write-Through

**쓰기 시 캐시와 DB를 동시에 업데이트**

```
쓰기 흐름:
┌────────┐  1.쓰기   ┌────────┐  2.쓰기   ┌────────┐
│  App   │ ───────► │ Cache  │ ───────► │   DB   │
└────────┘          └────────┘          └────────┘
                    (동기적으로 둘 다 저장)
```

```java
@Service
public class ProductService {

    @Transactional
    public Product updateProduct(Long id, ProductUpdateRequest request) {
        // 1. DB 업데이트
        Product product = productRepository.findById(id)
            .orElseThrow();
        product.update(request);
        productRepository.save(product);

        // 2. 캐시 업데이트 (동기)
        String key = "product:" + id;
        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));

        return product;
    }
}
```

| 장점 | 단점 |
|------|------|
| 데이터 일관성 보장 | 쓰기 지연 증가 |
| 캐시 항상 최신 | 쓰기 많으면 비효율 |

### 2.3 Write-Behind (Write-Back)

**캐시에 먼저 쓰고, DB는 비동기로 나중에**

```
쓰기 흐름:
┌────────┐  1.쓰기   ┌────────┐
│  App   │ ───────► │ Cache  │
└────────┘          └────────┘
                         │
                         │ 2.비동기 (배치)
                         ▼
                    ┌────────┐
                    │   DB   │
                    └────────┘
```

```java
@Service
public class ViewCountService {
    private final RedisTemplate<String, Long> redisTemplate;
    private final Queue<ViewEvent> writeQueue = new ConcurrentLinkedQueue<>();

    // 조회수는 캐시에만 즉시 반영
    public void incrementViewCount(Long articleId) {
        String key = "view:" + articleId;
        redisTemplate.opsForValue().increment(key);

        // 큐에 이벤트 추가 (비동기 DB 반영용)
        writeQueue.offer(new ViewEvent(articleId, LocalDateTime.now()));
    }

    // 주기적으로 DB 반영
    @Scheduled(fixedDelay = 5000)
    public void flushToDb() {
        List<ViewEvent> batch = new ArrayList<>();
        ViewEvent event;
        while ((event = writeQueue.poll()) != null && batch.size() < 1000) {
            batch.add(event);
        }
        if (!batch.isEmpty()) {
            articleRepository.batchUpdateViewCounts(batch);
        }
    }
}
```

| 장점 | 단점 |
|------|------|
| 쓰기 성능 최고 | 데이터 유실 위험 |
| DB 부하 감소 | 구현 복잡 |
| 배치 처리 가능 | 일관성 보장 어려움 |

### 2.4 Read-Through

**캐시가 DB 조회까지 담당** (캐시 라이브러리가 처리)

```java
// Spring Cache의 @Cacheable이 Read-Through 패턴
@Cacheable(value = "products", key = "#id")
public Product getProduct(Long id) {
    // 캐시 미스 시에만 이 코드 실행
    return productRepository.findById(id).orElseThrow();
}
```

### 2.5 캐시 전략 비교

| 전략 | 읽기 | 쓰기 | 일관성 | 사용 케이스 |
|------|------|------|--------|------------|
| Look-Aside | 앱이 관리 | 앱이 관리 | 중간 | 범용 |
| Write-Through | - | 동기 저장 | 높음 | 일관성 중요 |
| Write-Behind | - | 비동기 저장 | 낮음 | 쓰기 많음, 유실 허용 |
| Read-Through | 캐시가 관리 | - | 중간 | 읽기 추상화 |

---

## 3. 캐시 문제와 해결책

### 3.1 Cache Penetration (캐시 관통)

**존재하지 않는 데이터를 반복 요청**하여 매번 DB를 조회하는 문제

```
공격 시나리오:
요청: GET /users/-1  (존재하지 않는 ID)

┌────────┐      ┌────────┐      ┌────────┐
│ Client │ ──── │ Cache  │ ──── │   DB   │
└────────┘      └────────┘      └────────┘
                 Miss!           Not Found
                 Miss!           Not Found
                 Miss!           Not Found
                 (매번 DB 조회 → DB 과부하)
```

#### 해결책 1: Null Object Pattern

**존재하지 않는 키도 캐시에 저장** (빈 값 또는 특수 객체)

```java
@Service
public class UserService {

    private static final User NULL_USER = User.empty();  // Null Object

    public User getUser(Long id) {
        String key = "user:" + id;

        User cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // Null Object인지 확인
            if (cached.equals(NULL_USER)) {
                return null;  // 또는 throw NotFoundException
            }
            return cached;
        }

        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            // 존재하지 않는 키도 캐시! (짧은 TTL)
            redisTemplate.opsForValue().set(key, NULL_USER, Duration.ofMinutes(5));
            return null;
        }

        redisTemplate.opsForValue().set(key, user, Duration.ofHours(1));
        return user;
    }
}
```

```java
// Null Object 정의
@Getter
public class User {
    private Long id;
    private String name;

    private static final User EMPTY = new User(-1L, "NULL");

    public static User empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}
```

#### 해결책 2: Bloom Filter

**확률적 자료구조**로 "존재하지 않음"을 빠르게 판단

```
Bloom Filter 특성:
- "없다" → 100% 확실히 없음
- "있다" → 있을 수도 있음 (False Positive 가능)

┌────────┐      ┌─────────────┐      ┌────────┐      ┌────────┐
│ Client │ ──── │ Bloom Filter│ ──── │ Cache  │ ──── │   DB   │
└────────┘      └─────────────┘      └────────┘      └────────┘
                 "없음" → 즉시 반환
                 "있을수도" → 캐시 조회 진행
```

```java
@Service
public class UserService {
    private final BloomFilter<Long> userIdFilter;

    @PostConstruct
    public void initBloomFilter() {
        // Guava Bloom Filter
        userIdFilter = BloomFilter.create(
            Funnels.longFunnel(),
            1_000_000,      // 예상 요소 수
            0.01            // False Positive 확률 1%
        );

        // 기존 사용자 ID 모두 등록
        userRepository.findAllIds().forEach(userIdFilter::put);
    }

    public User getUser(Long id) {
        // 1. Bloom Filter로 빠른 체크
        if (!userIdFilter.mightContain(id)) {
            // 확실히 없음 → DB 조회 안 함
            throw new NotFoundException("User not found: " + id);
        }

        // 2. 있을 수도 있음 → 정상 캐시/DB 조회
        return getUserFromCacheOrDb(id);
    }

    // 새 사용자 생성 시 Bloom Filter에 추가
    public User createUser(UserCreateRequest request) {
        User user = userRepository.save(new User(request));
        userIdFilter.put(user.getId());  // Bloom Filter 업데이트
        return user;
    }
}
```

**Redisson Bloom Filter 사용:**
```java
@Configuration
public class BloomFilterConfig {

    @Bean
    public RBloomFilter<Long> userBloomFilter(RedissonClient redisson) {
        RBloomFilter<Long> bloomFilter = redisson.getBloomFilter("user:bloomfilter");
        // 100만 개 요소, 3% false positive
        bloomFilter.tryInit(1_000_000L, 0.03);
        return bloomFilter;
    }
}
```

### 3.2 Cache Stampede (캐시 쇄도)

**캐시 만료 시 동시에 많은 요청이 DB로 몰리는 문제**

```
시나리오: 인기 상품 캐시 만료

시간 T: 캐시 만료!
        ↓
요청 1 ──┐
요청 2 ──┼──► DB 동시 조회 (100개 요청이 동시에!)
요청 3 ──┤     ↓
...     ──┘   DB 과부하 → 장애
```

#### 해결책 1: Distributed Lock (분산 락)

**한 요청만 DB 조회, 나머지는 대기**

```java
@Service
public class ProductService {
    private final RedissonClient redissonClient;

    public Product getProduct(Long id) {
        String cacheKey = "product:" + id;
        String lockKey = "lock:product:" + id;

        // 1. 캐시 확인
        Product cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 분산 락 획득 시도
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 최대 5초 대기, 락 획득 시 10초 보유
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (acquired) {
                // 3. Double-Check: 락 획득 사이에 다른 스레드가 캐시 채웠을 수 있음
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                // 4. DB 조회 및 캐시 저장
                Product product = productRepository.findById(id).orElseThrow();
                redisTemplate.opsForValue().set(cacheKey, product, Duration.ofMinutes(30));
                return product;
            } else {
                // 5. 락 획득 실패 → 잠시 후 캐시 재확인
                Thread.sleep(100);
                return redisTemplate.opsForValue().get(cacheKey);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

#### 해결책 2: Jitter (랜덤 TTL)

**TTL에 랜덤 값을 더해 만료 시점 분산**

```java
@Service
public class ProductService {
    private final Random random = new Random();

    public void cacheProduct(Product product) {
        String key = "product:" + product.getId();

        // 기본 TTL 30분 + 랜덤 0~5분
        long baseTtl = 30;
        long jitter = random.nextInt(5);  // 0~4분 랜덤
        long ttl = baseTtl + jitter;

        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(ttl));
    }
}
```

```
Jitter 없이:
상품1 TTL: 30분 ──────────────────────────┤ 만료
상품2 TTL: 30분 ──────────────────────────┤ 만료
상품3 TTL: 30분 ──────────────────────────┤ 만료
                                          ↑
                                    동시 만료!

Jitter 적용:
상품1 TTL: 32분 ────────────────────────────┤ 만료
상품2 TTL: 28분 ──────────────────────────┤ 만료
상품3 TTL: 34분 ──────────────────────────────┤ 만료
                                  분산 만료!
```

#### 해결책 3: Probabilistic Early Recomputation (PER)

**만료 전 확률적으로 미리 갱신**

```java
@Service
public class ProductService {
    private final Random random = new Random();

    public Product getProduct(Long id) {
        String key = "product:" + id;
        String ttlKey = "product:ttl:" + id;

        Product cached = redisTemplate.opsForValue().get(key);
        Long savedAt = redisTemplate.opsForValue().get(ttlKey);

        if (cached != null && savedAt != null) {
            long age = System.currentTimeMillis() - savedAt;
            long ttl = 30 * 60 * 1000;  // 30분

            // 남은 시간 비율에 따라 갱신 확률 계산
            double remainingRatio = 1.0 - ((double) age / ttl);

            // 남은 시간이 적을수록 갱신 확률 증가
            // 예: 남은 시간 10% → 갱신 확률 90%
            if (random.nextDouble() > remainingRatio) {
                // 비동기로 미리 갱신
                CompletableFuture.runAsync(() -> refreshCache(id));
            }

            return cached;
        }

        return refreshCache(id);
    }

    private Product refreshCache(Long id) {
        Product product = productRepository.findById(id).orElseThrow();
        String key = "product:" + id;
        String ttlKey = "product:ttl:" + id;

        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));
        redisTemplate.opsForValue().set(ttlKey, System.currentTimeMillis(), Duration.ofMinutes(30));

        return product;
    }
}
```

#### 해결책 4: Request Collapsing (요청 병합)

**동일 키에 대한 동시 요청을 하나로 병합**

```java
@Service
public class ProductService {
    // 진행 중인 요청을 추적
    private final ConcurrentMap<Long, CompletableFuture<Product>> inFlightRequests
        = new ConcurrentHashMap<>();

    public Product getProduct(Long id) {
        String key = "product:" + id;

        // 1. 캐시 확인
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 2. 이미 진행 중인 요청이 있으면 그 결과를 기다림
        CompletableFuture<Product> existingRequest = inFlightRequests.get(id);
        if (existingRequest != null) {
            try {
                return existingRequest.get(5, TimeUnit.SECONDS);  // 기존 요청 결과 대기
            } catch (Exception e) {
                // fallback
            }
        }

        // 3. 새 요청 등록 및 실행
        CompletableFuture<Product> newRequest = new CompletableFuture<>();
        CompletableFuture<Product> previous = inFlightRequests.putIfAbsent(id, newRequest);

        if (previous != null) {
            // 다른 스레드가 먼저 등록함 → 그 결과 대기
            try {
                return previous.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // fallback
            }
        }

        try {
            // 4. DB 조회
            Product product = productRepository.findById(id).orElseThrow();
            redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));

            newRequest.complete(product);  // 대기 중인 스레드들에게 결과 전달
            return product;
        } catch (Exception e) {
            newRequest.completeExceptionally(e);
            throw e;
        } finally {
            inFlightRequests.remove(id);  // 정리
        }
    }
}
```

```
Request Collapsing 동작:

시간 T: 캐시 만료
요청 1 ──► DB 조회 시작
요청 2 ──► 요청 1 결과 대기
요청 3 ──► 요청 1 결과 대기
          ...
요청 1 완료 ──► 결과를 요청 2, 3에게 전달

DB 조회: 100번 → 1번
```

### 3.3 Rate Limit

**요청 수를 제한**하여 시스템 보호

```java
@Service
public class RateLimiter {

    // Sliding Window Rate Limiter (Redis)
    public boolean isAllowed(String userId, int maxRequests, int windowSeconds) {
        String key = "ratelimit:" + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 1. 윈도우 밖의 오래된 요청 제거
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. 현재 윈도우 내 요청 수 확인
        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);

        if (count != null && count >= maxRequests) {
            return false;  // 제한 초과
        }

        // 3. 현재 요청 추가
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));

        return true;
    }
}
```

```java
// 사용
@RestController
public class ApiController {
    private final RateLimiter rateLimiter;

    @GetMapping("/api/products/{id}")
    public Product getProduct(@PathVariable Long id, @RequestHeader("X-User-Id") String userId) {
        // 분당 100회 제한
        if (!rateLimiter.isAllowed(userId, 100, 60)) {
            throw new TooManyRequestsException("Rate limit exceeded");
        }
        return productService.getProduct(id);
    }
}
```

---

## 4. Redis 기초

### 4.1 Redis란?

**In-Memory 데이터 저장소**. 캐시, 세션, 메시지 브로커 등 다용도.

```
특징:
- 메모리 기반 → 초고속 (읽기/쓰기 ~0.1ms)
- 다양한 자료구조 (String, Hash, List, Set, Sorted Set, ...)
- 싱글 스레드 → 원자적 연산 보장
- Persistence 옵션 (RDB, AOF)
- 클러스터/복제 지원
```

### 4.2 주요 자료구조

```java
@Service
public class RedisExamples {

    // 1. String - 단순 키-값
    public void stringExample() {
        redisTemplate.opsForValue().set("user:1:name", "Kim");
        redisTemplate.opsForValue().set("counter", "0");
        redisTemplate.opsForValue().increment("counter");  // 원자적 증가
    }

    // 2. Hash - 객체 저장
    public void hashExample() {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put("user:1", "name", "Kim");
        hash.put("user:1", "email", "kim@example.com");
        Map<String, String> user = hash.entries("user:1");
    }

    // 3. List - 큐, 스택
    public void listExample() {
        ListOperations<String, String> list = redisTemplate.opsForList();
        list.rightPush("queue", "task1");  // 큐: 오른쪽 삽입
        list.leftPop("queue");             // 큐: 왼쪽 추출
    }

    // 4. Set - 고유 값 집합
    public void setExample() {
        SetOperations<String, String> set = redisTemplate.opsForSet();
        set.add("tags:article:1", "java", "spring", "redis");
        set.members("tags:article:1");  // 모든 태그
        set.isMember("tags:article:1", "java");  // 포함 여부
    }

    // 5. Sorted Set - 정렬된 집합 (랭킹)
    public void sortedSetExample() {
        ZSetOperations<String, String> zset = redisTemplate.opsForZSet();
        zset.add("ranking:game", "player1", 1500);
        zset.add("ranking:game", "player2", 2000);
        zset.add("ranking:game", "player3", 1800);

        // 상위 3명 (점수 내림차순)
        Set<String> top3 = zset.reverseRange("ranking:game", 0, 2);
        // [player2, player3, player1]

        // 특정 플레이어 순위
        Long rank = zset.reverseRank("ranking:game", "player1");  // 2 (0-based)
    }
}
```

### 4.3 List Cache

**여러 항목을 리스트로 캐싱**하는 패턴

```java
@Service
public class TimelineService {

    // 타임라인 캐시 (최근 100개만)
    public void addToTimeline(Long userId, Post post) {
        String key = "timeline:" + userId;

        // 왼쪽에 추가 (최신이 앞)
        redisTemplate.opsForList().leftPush(key, post);

        // 100개 초과 시 오래된 것 제거
        redisTemplate.opsForList().trim(key, 0, 99);
    }

    public List<Post> getTimeline(Long userId, int page, int size) {
        String key = "timeline:" + userId;
        int start = page * size;
        int end = start + size - 1;

        List<Post> cached = redisTemplate.opsForList().range(key, start, end);

        if (cached == null || cached.isEmpty()) {
            // 캐시 미스 → DB에서 로드
            return loadTimelineFromDb(userId, page, size);
        }

        return cached;
    }
}
```

---

## 5. Redis Cluster & Sharding

### 5.1 Shard (샤딩)

**데이터를 여러 노드에 분산 저장**

```
샤딩 전:
┌──────────────────────────────────────┐
│           Single Redis               │
│  모든 데이터: 1억 건                  │
│  메모리: 100GB                        │
│  처리량: 10만 QPS                     │
└──────────────────────────────────────┘
          ↓ 한계 도달

샤딩 후:
┌────────────┐ ┌────────────┐ ┌────────────┐
│  Shard 1   │ │  Shard 2   │ │  Shard 3   │
│  3,300만건 │ │  3,300만건 │ │  3,300만건 │
│  33GB      │ │  33GB      │ │  33GB      │
│  3.3만 QPS │ │  3.3만 QPS │ │  3.3만 QPS │
└────────────┘ └────────────┘ └────────────┘
         총 10만 QPS, 100GB
```

### 5.2 Redis Cluster

Redis 공식 클러스터 솔루션. **16384개의 해시 슬롯**으로 데이터 분산.

```
Redis Cluster 구조:

        ┌─────────────────────────────────────────┐
        │            16384 Hash Slots             │
        │  0-5460    5461-10922    10923-16383    │
        └─────────────────────────────────────────┘
             │            │              │
             ▼            ▼              ▼
        ┌────────┐   ┌────────┐    ┌────────┐
        │ Node 1 │   │ Node 2 │    │ Node 3 │
        │ Master │   │ Master │    │ Master │
        └────────┘   └────────┘    └────────┘
             │            │              │
             ▼            ▼              ▼
        ┌────────┐   ┌────────┐    ┌────────┐
        │ Node 1'│   │ Node 2'│    │ Node 3'│
        │ Replica│   │ Replica│    │ Replica│
        └────────┘   └────────┘    └────────┘

키 할당: CRC16(key) % 16384 → 슬롯 번호 → 해당 노드
```

```yaml
# application.yml
spring:
  redis:
    cluster:
      nodes:
        - 192.168.1.1:7000
        - 192.168.1.2:7000
        - 192.168.1.3:7000
      max-redirects: 3
```

```java
@Configuration
public class RedisClusterConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration config = new RedisClusterConfiguration()
            .clusterNode("192.168.1.1", 7000)
            .clusterNode("192.168.1.2", 7000)
            .clusterNode("192.168.1.3", 7000);

        return new LettuceConnectionFactory(config);
    }
}
```

### 5.3 Application-Level Sharding

**애플리케이션에서 직접 샤딩 로직 구현**

```java
@Service
public class ShardedRedisService {
    private final List<RedisTemplate<String, Object>> shards;

    public ShardedRedisService(List<RedisTemplate<String, Object>> shards) {
        this.shards = shards;  // 여러 Redis 인스턴스
    }

    // 샤드 선택 로직
    private RedisTemplate<String, Object> getShard(String key) {
        int hash = Math.abs(key.hashCode());
        int shardIndex = hash % shards.size();
        return shards.get(shardIndex);
    }

    public void set(String key, Object value, Duration ttl) {
        RedisTemplate<String, Object> shard = getShard(key);
        shard.opsForValue().set(key, value, ttl);
    }

    public Object get(String key) {
        RedisTemplate<String, Object> shard = getShard(key);
        return shard.opsForValue().get(key);
    }
}
```

| Redis Cluster | Application-Level Sharding |
|---------------|---------------------------|
| 자동 슬롯 분배 | 수동 샤딩 로직 구현 |
| 자동 페일오버 | 별도 구현 필요 |
| 운영 복잡도 높음 | 상대적으로 단순 |
| Cross-slot 명령 제한 | 자유로운 명령 사용 |

### 5.4 Application-Level Replication

**애플리케이션에서 직접 복제 관리**

```java
@Service
public class ReplicatedRedisService {
    private final RedisTemplate<String, Object> master;
    private final List<RedisTemplate<String, Object>> replicas;
    private final AtomicInteger replicaIndex = new AtomicInteger(0);

    // 쓰기는 마스터로
    public void set(String key, Object value) {
        master.opsForValue().set(key, value);
    }

    // 읽기는 레플리카에서 (라운드 로빈)
    public Object get(String key) {
        int index = Math.abs(replicaIndex.getAndIncrement() % replicas.size());
        RedisTemplate<String, Object> replica = replicas.get(index);
        return replica.opsForValue().get(key);
    }

    // 읽기 부하 분산
    public Object getWithFallback(String key) {
        try {
            return get(key);  // 레플리카에서 읽기
        } catch (Exception e) {
            return master.opsForValue().get(key);  // 실패 시 마스터
        }
    }
}
```

---

## 6. 분산 환경 패턴

### 6.1 Distributed Lock (분산 락)

**여러 인스턴스에서 동시 접근을 제어**

```java
@Service
public class DistributedLockService {
    private final RedissonClient redissonClient;

    // 기본 락
    public void withLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 10초 대기, 30초 후 자동 해제
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                task.run();
            } else {
                throw new LockAcquisitionException("Failed to acquire lock: " + lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // AOP 기반 락
    @DistributedLock(key = "'order:' + #orderId", waitTime = 5, leaseTime = 10)
    public void processOrder(Long orderId) {
        // 락 보장된 상태에서 실행
    }
}
```

**Redisson Lock 종류:**
```java
// 1. 일반 락 (Reentrant Lock)
RLock lock = redissonClient.getLock("myLock");

// 2. 공정 락 (요청 순서대로)
RLock fairLock = redissonClient.getFairLock("fairLock");

// 3. 읽기-쓰기 락
RReadWriteLock rwLock = redissonClient.getReadWriteLock("rwLock");
rwLock.readLock().lock();   // 여러 읽기 동시 가능
rwLock.writeLock().lock();  // 쓰기는 배타적

// 4. 멀티 락 (여러 락을 동시에)
RLock lock1 = redissonClient.getLock("lock1");
RLock lock2 = redissonClient.getLock("lock2");
RLock multiLock = redissonClient.getMultiLock(lock1, lock2);
```

### 6.2 Hot-Key 문제

**특정 키에 요청이 집중**되어 해당 노드에 부하 집중

```
문제 상황:
인기 상품 "product:123"에 초당 10만 요청

┌────────────┐ ┌────────────┐ ┌────────────┐
│  Shard 1   │ │  Shard 2   │ │  Shard 3   │
│  1만 QPS   │ │ 10만 QPS   │ │  1만 QPS   │
│            │ │ ← Hot Key! │ │            │
└────────────┘ └────────────┘ └────────────┘
                    ↑
               병목/과부하
```

#### 해결책 1: Local Cache + Redis

```java
@Service
public class HotKeyService {
    // Local Cache (Caffeine)
    private final Cache<String, Product> localCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofSeconds(10))  // 짧은 TTL
        .build();

    public Product getProduct(Long id) {
        String key = "product:" + id;

        // 1. Local Cache 먼저
        Product local = localCache.getIfPresent(key);
        if (local != null) {
            return local;
        }

        // 2. Redis
        Product redis = redisTemplate.opsForValue().get(key);
        if (redis != null) {
            localCache.put(key, redis);
            return redis;
        }

        // 3. DB
        Product product = productRepository.findById(id).orElseThrow();
        redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));
        localCache.put(key, product);
        return product;
    }
}
```

#### 해결책 2: Key 분산 (Replica Keys)

```java
@Service
public class HotKeyReplicaService {
    private static final int REPLICA_COUNT = 10;
    private final Random random = new Random();

    // 쓰기: 모든 레플리카에 저장
    public void setHotProduct(Product product) {
        for (int i = 0; i < REPLICA_COUNT; i++) {
            String key = "product:" + product.getId() + ":replica:" + i;
            redisTemplate.opsForValue().set(key, product, Duration.ofMinutes(30));
        }
    }

    // 읽기: 랜덤 레플리카에서 읽기
    public Product getHotProduct(Long id) {
        int replica = random.nextInt(REPLICA_COUNT);
        String key = "product:" + id + ":replica:" + replica;
        return redisTemplate.opsForValue().get(key);
    }
}
```

```
Hot Key 분산 후:

원래: product:123 → Shard 2 (10만 QPS 집중)

분산: product:123:replica:0 → Shard 1 (1만 QPS)
      product:123:replica:1 → Shard 2 (1만 QPS)
      product:123:replica:2 → Shard 3 (1만 QPS)
      ...
      product:123:replica:9 → Shard 1 (1만 QPS)
```

---

## 7. 실무 패턴

### 7.1 캐시 Warm-up

**서버 시작 시 주요 데이터를 미리 캐시에 로드**

```java
@Component
public class CacheWarmup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("Cache warm-up started");

        // 인기 상품 100개 미리 로드
        List<Product> hotProducts = productRepository.findTop100ByOrderByViewCountDesc();
        hotProducts.forEach(product -> {
            String key = "product:" + product.getId();
            redisTemplate.opsForValue().set(key, product, Duration.ofHours(1));
        });

        log.info("Cache warm-up completed: {} products loaded", hotProducts.size());
    }
}
```

### 7.2 캐시 무효화 전략

```java
@Service
public class ProductService {

    // 1. 단일 키 무효화
    @CacheEvict(value = "products", key = "#id")
    public void updateProduct(Long id, ProductUpdateRequest request) {
        // 업데이트 로직
    }

    // 2. 패턴 기반 무효화
    public void invalidateProductCache(Long id) {
        Set<String> keys = redisTemplate.keys("product:" + id + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // 3. 전체 무효화
    @CacheEvict(value = "products", allEntries = true)
    public void refreshAllProducts() {
        // 전체 캐시 삭제
    }

    // 4. 이벤트 기반 무효화
    @EventListener
    public void onProductUpdated(ProductUpdatedEvent event) {
        String key = "product:" + event.getProductId();
        redisTemplate.delete(key);
    }
}
```

### 7.3 TTL 전략

```java
public class CacheTtlStrategy {

    // 데이터 특성별 TTL
    public static final Duration USER_PROFILE = Duration.ofHours(24);      // 변경 적음
    public static final Duration PRODUCT_DETAIL = Duration.ofMinutes(30);  // 중간
    public static final Duration INVENTORY = Duration.ofSeconds(30);       // 변경 많음
    public static final Duration SEARCH_RESULT = Duration.ofMinutes(5);    // 휘발성
    public static final Duration SESSION = Duration.ofHours(2);            // 세션

    // 동적 TTL (인기도에 따라)
    public Duration getDynamicTtl(Product product) {
        if (product.getViewCount() > 10000) {
            return Duration.ofHours(1);  // 인기 상품은 길게
        } else if (product.getViewCount() > 1000) {
            return Duration.ofMinutes(30);
        } else {
            return Duration.ofMinutes(10);  // 비인기는 짧게
        }
    }
}
```

### 7.4 모니터링

```java
@Component
public class CacheMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public CacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.cacheHitCounter = Counter.builder("cache.hit")
            .description("Cache hit count")
            .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("cache.miss")
            .description("Cache miss count")
            .register(meterRegistry);
    }

    public void recordHit() {
        cacheHitCounter.increment();
    }

    public void recordMiss() {
        cacheMissCounter.increment();
    }

    // Hit Rate 계산
    // cache.hit / (cache.hit + cache.miss)
}
```

---

## 8. 요약 체크리스트

### 캐시 도입 전 확인

```
[ ] 읽기/쓰기 비율 확인 (읽기 많아야 효과적)
[ ] 데이터 일관성 요구 수준 확인
[ ] Hot Data 식별
[ ] TTL 전략 수립
```

### 캐시 문제 대응

| 문제 | 원인 | 해결책 |
|------|------|--------|
| Cache Penetration | 존재하지 않는 키 반복 조회 | Null Object, Bloom Filter |
| Cache Stampede | 동시 만료 → DB 집중 | 분산 락, Jitter, PER |
| Hot Key | 특정 키 집중 | Local Cache, Key 분산 |
| 일관성 문제 | DB-캐시 불일치 | Write-Through, 이벤트 기반 무효화 |

### Redis 운영

```
[ ] 메모리 모니터링 (maxmemory 설정)
[ ] 적절한 eviction 정책 (allkeys-lru 권장)
[ ] Cluster 구성 (HA)
[ ] 백업 설정 (RDB/AOF)
[ ] 모니터링 대시보드 (Grafana)
```
