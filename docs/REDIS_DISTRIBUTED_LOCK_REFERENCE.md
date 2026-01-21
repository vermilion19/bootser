# Redis 분산락 레퍼런스 가이드

> Booster 프로젝트에서 사용 중인 Redis 분산락 패턴 정리

## 개요

프로젝트에서 두 가지 분산락 패턴을 사용하고 있습니다:

| 패턴 | 라이브러리 | 용도 | 사용 위치 |
|------|-----------|------|----------|
| `@DistributedLock` | Redisson | 비즈니스 로직 동시성 제어 | `libs/storage-redis` |
| `@SchedulerLock` | ShedLock | 스케줄러 중복 실행 방지 | `apps/waiting-service` |

---

## 1. @DistributedLock (Redisson 기반)

### 1.1 용도
- 동일 리소스에 대한 **동시 요청 제어**
- 예: 같은 식당에 대기 등록이 동시에 들어올 때 순번 충돌 방지

### 1.2 의존성 (build.gradle)

```gradle
// libs/storage-redis/build.gradle
dependencies {
    api 'org.redisson:redisson-spring-boot-starter:4.0.0'
    api 'org.springframework.boot:spring-boot-starter-aop:4.0.0-M2'
}

// 사용하는 서비스의 build.gradle
dependencies {
    implementation project(':libs:storage-redis')
}
```

### 1.3 어노테이션 정의

**파일**: `libs/storage-redis/src/main/java/com/booster/storage/redis/lock/DistributedLock.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    // 락의 키 값 (SpEL 지원, 예: "#request.restaurantId")
    String key();

    // 락의 시간 단위 (기본: SECONDS)
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    // 락 획득을 위해 대기하는 시간 (기본 5초)
    long waitTime() default 5L;

    // 락을 임대하는 시간 (기본 3초)
    long leaseTime() default 3L;
}
```

### 1.4 AOP 구현

**파일**: `libs/storage-redis/src/main/java/com/booster/storage/redis/lock/DistributedLockAop.java`

```java
@Aspect
@Component
@Order(1)  // 트랜잭션 AOP보다 먼저 실행 (Lock -> Transaction)
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";
    private final RedissonClient redissonClient;

    @Around("@annotation(com.booster.storage.redis.lock.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // 1. 키 생성 (SpEL 파싱)
        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.key()
        );

        RLock rLock = redissonClient.getLock(key);

        try {
            // 2. 락 획득 시도
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!available) {
                log.warn("[Lock Failed] key: {}", key);
                throw new RuntimeException("현재 요청량이 많아 처리가 지연되고 있습니다.");
            }

            // 3. 비즈니스 로직 수행
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            // 4. 락 해제 (내 락인지 확인 후 해제)
            try {
                if (rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                }
            } catch (IllegalMonitorStateException e) {
                log.info("Redisson Lock Already UnLock {} {}", method.getName(), key);
            }
        }
    }
}
```

### 1.5 SpEL 파서 유틸리티

**파일**: `libs/storage-redis/src/main/java/com/booster/storage/redis/utils/CustomSpringELParser.java`

```java
public class CustomSpringELParser {

    private CustomSpringELParser() {}

    public static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, Object.class);
    }
}
```

### 1.6 사용 예시

**파일**: `apps/waiting-service/.../WaitingRegisterFacade.java`

```java
@Component
@RequiredArgsConstructor
public class WaitingRegisterFacade {
    private final WaitingService waitingService;

    // 식당 ID 기준으로 락 (동일 식당에 동시 등록 방지)
    @DistributedLock(key = "'waiting:restaurant:' + #request.restaurantId()")
    public RegisterWaitingResponse register(RegisterWaitingRequest request) {
        return waitingService.registerInternal(request);
    }

    @DistributedLock(key = "'waiting:restaurant:' + #command.restaurantId()")
    public RegisterWaitingResponse postpone(PostponeCommand command) {
        return waitingService.postponeInternal(command.waitingId());
    }
}
```

### 1.7 핵심 포인트

```
┌─────────────────────────────────────────────────────────────┐
│  @DistributedLock 동작 순서                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. [AOP] 메서드 호출 가로채기                               │
│       ↓                                                     │
│  2. [SpEL] 키 파싱: "'waiting:restaurant:' + #request.id"   │
│       → 결과: "LOCK:waiting:restaurant:123"                 │
│       ↓                                                     │
│  3. [Redisson] rLock.tryLock(5초 대기, 3초 임대)            │
│       ├─ 성공 → 비즈니스 로직 실행                          │
│       └─ 실패 → RuntimeException 발생                       │
│       ↓                                                     │
│  4. [Finally] rLock.unlock() (내 락인지 확인 후)            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.8 주의사항

1. **@Order(1)**: 트랜잭션보다 먼저 실행되어야 함
   - Lock → Transaction → 비즈니스 로직 → Commit → Unlock
   - 트랜잭션이 먼저면 커밋 전에 락이 풀려 동시성 이슈 발생

2. **leaseTime 설정**: 비즈니스 로직 수행 시간보다 길게 설정
   - 기본 3초 → 로직이 오래 걸리면 늘려야 함

3. **waitTime 설정**: 사용자 대기 허용 시간
   - 기본 5초 → 너무 길면 UX 저하

---

## 2. @SchedulerLock (ShedLock)

### 2.1 용도
- **스케줄러 중복 실행 방지**
- 다중 인스턴스 환경에서 동일 스케줄 작업이 한 번만 실행되도록 보장

### 2.2 의존성 (build.gradle)

```gradle
// apps/waiting-service/build.gradle
dependencies {
    // ShedLock 코어
    implementation 'net.javacrumbs.shedlock:shedlock-spring:5.10.0'
    // Redis Provider
    implementation 'net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.10.0'
}
```

### 2.3 설정 클래스

**파일**: `apps/waiting-service/.../config/SchedulerConfig.java`

```java
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // Redis를 이용해 잠금을 관리
        // 두 번째 인자: 키 접두사 (환경별 구분 가능)
        return new RedisLockProvider(connectionFactory, "waiting-lock");
    }
}
```

### 2.4 사용 예시

**파일**: `apps/waiting-service/.../WaitingScheduler.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingScheduler {

    private final WaitingRepository waitingRepository;
    private final RedissonClient redissonClient;

    /**
     * 매일 자정(00:00:00) 실행 - 대기열 초기화
     */
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(
        name = "cleanupWaiting",      // 락 이름 (고유해야 함)
        lockAtMostFor = "50s",        // 최대 락 유지 시간 (데드락 방지)
        lockAtLeastFor = "30s"        // 최소 락 유지 시간 (중복 실행 방지)
    )
    @Transactional
    public void cleanup() {
        log.info("[Scheduler] 대기열 초기화 작업 시작...");

        // 1. DB 정리
        int updatedCount = waitingRepository.bulkUpdateStatusToCanceled();
        log.info("DB 정리 완료: {}건", updatedCount);

        // 2. Redis 정리
        redissonClient.getKeys().deleteByPattern("waiting:ranking:*");
        log.info("Redis 정리 완료");
    }

    /**
     * 1분마다 실행 - 노쇼 처리
     */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(
        name = "checkNoShow",
        lockAtMostFor = "59s",   // 다음 실행 전에 반드시 해제
        lockAtLeastFor = "10s"   // 빠르게 끝나도 10초간 락 유지
    )
    @Transactional
    public void checkNoShow() {
        log.info("노쇼(No-Show) 처리 스케줄러 시작");

        LocalDateTime limitTime = LocalDateTime.now().minusMinutes(5);
        int updatedCount = waitingRepository.updateStatusToNoShow(limitTime);

        if (updatedCount > 0) {
            log.info("{}명의 대기자가 노쇼로 처리되었습니다.", updatedCount);
        }
    }
}
```

### 2.5 핵심 포인트

```
┌─────────────────────────────────────────────────────────────┐
│  @SchedulerLock 파라미터 설명                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  name: "checkNoShow"                                        │
│  └─ Redis 키: "waiting-lock:checkNoShow"                    │
│                                                             │
│  lockAtMostFor: "59s"                                       │
│  └─ 작업이 아무리 길어져도 59초 후 자동 해제 (데드락 방지)   │
│  └─ 1분 주기 스케줄러면 59초 이하로 설정                     │
│                                                             │
│  lockAtLeastFor: "10s"                                      │
│  └─ 작업이 0.1초 만에 끝나도 10초간 락 유지                 │
│  └─ 짧은 간격의 중복 실행 방지                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.6 다중 인스턴스 동작 방식

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Instance A  │    │  Instance B  │    │  Instance C  │
│  @Scheduled  │    │  @Scheduled  │    │  @Scheduled  │
│  00:00:00    │    │  00:00:00    │    │  00:00:00    │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │        Redis           │
              │  KEY: waiting-lock:    │
              │       cleanupWaiting   │
              │  VALUE: Instance A ID  │
              │  TTL: 50s              │
              └────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
   Instance A         Instance B       Instance C
   락 획득 성공        락 획득 실패      락 획득 실패
   → 작업 실행         → 스킵           → 스킵
```

---

## 3. 두 패턴 비교

| 항목 | @DistributedLock | @SchedulerLock |
|------|------------------|----------------|
| **용도** | 비즈니스 로직 동시성 | 스케줄러 중복 실행 방지 |
| **라이브러리** | Redisson | ShedLock |
| **적용 대상** | 일반 메서드 | @Scheduled 메서드 |
| **키 방식** | SpEL 동적 생성 | 정적 이름 |
| **락 실패 시** | 예외 발생 | 메서드 스킵 |
| **타임아웃** | waitTime + leaseTime | lockAtMostFor |

---

## 4. 새 프로젝트 적용 가이드

### 4.1 비즈니스 로직 분산락 (@DistributedLock)

**Step 1**: 의존성 추가
```gradle
dependencies {
    implementation project(':libs:storage-redis')
}
```

**Step 2**: Facade 클래스 생성
```java
@Component
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderService orderService;

    @DistributedLock(key = "'order:user:' + #request.userId()")
    public OrderResponse createOrder(CreateOrderRequest request) {
        return orderService.createOrderInternal(request);
    }
}
```

**Step 3**: 컨트롤러에서 Facade 호출
```java
@PostMapping("/orders")
public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
    return orderFacade.createOrder(request);  // Service가 아닌 Facade 호출
}
```

### 4.2 스케줄러 분산락 (@SchedulerLock)

**Step 1**: 의존성 추가
```gradle
dependencies {
    implementation 'net.javacrumbs.shedlock:shedlock-spring:5.10.0'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.10.0'
}
```

**Step 2**: 설정 클래스 생성
```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")  // 추가
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "my-service-lock");
    }
}
```

**Step 3**: 스케줄러에 어노테이션 추가
```java
@Scheduled(cron = "0 0 * * * *")  // 매시 정각
@SchedulerLock(name = "hourlyJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
public void hourlyJob() {
    // ...
}
```

---

## 5. 테스트 코드 예시

**파일**: `libs/storage-redis/src/test/java/.../DistributedLockAopTest.java`

```java
@ExtendWith(MockitoExtension.class)
class DistributedLockAopTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    @InjectMocks
    private DistributedLockAop distributedLockAop;

    @Test
    @DisplayName("락 획득 성공: 비즈니스 로직 실행 후 락 해제")
    void lock_success() throws Throwable {
        // given
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(joinPoint.proceed()).willReturn("Success");
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when
        Object result = distributedLockAop.lock(joinPoint);

        // then
        assertThat(result).isEqualTo("Success");
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패: 예외 발생, 비즈니스 로직 미실행")
    void lock_fail() throws Throwable {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> distributedLockAop.lock(joinPoint))
                .isInstanceOf(RuntimeException.class);

        verify(joinPoint, times(0)).proceed();
    }

    @Test
    @DisplayName("비즈니스 로직 예외: 예외 발생해도 락은 반드시 해제")
    void lock_exception() throws Throwable {
        // given
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(joinPoint.proceed()).willThrow(new IllegalArgumentException("에러"));
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        // when & then
        assertThatThrownBy(() -> distributedLockAop.lock(joinPoint))
                .isInstanceOf(IllegalArgumentException.class);

        verify(rLock).unlock();  // 락 해제 확인
    }
}
```

---

## 6. 관련 파일 경로

### 라이브러리 (libs/storage-redis)
- `src/main/java/com/booster/storage/redis/lock/DistributedLock.java`
- `src/main/java/com/booster/storage/redis/lock/DistributedLockAop.java`
- `src/main/java/com/booster/storage/redis/utils/CustomSpringELParser.java`
- `src/test/java/com/booster/storage/redis/lock/DistributedLockAopTest.java`

### 사용 예시 (apps/waiting-service)
- `src/main/java/.../waiting/application/WaitingRegisterFacade.java`
- `src/main/java/.../waiting/application/WaitingScheduler.java`
- `src/main/java/.../config/SchedulerConfig.java`