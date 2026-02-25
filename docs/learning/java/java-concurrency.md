# Java 동시성 면접 핵심 정리

## 1. Java Memory Model (JMM)

멀티스레드 환경에서 각 스레드는 성능을 위해 **메인 메모리의 값을 CPU 캐시에 복사**해 사용한다.
이때 스레드 간 데이터 가시성(Visibility) 문제가 발생한다.

```
Thread 1                Thread 2
  │                        │
  ▼                        ▼
CPU Cache 1           CPU Cache 2
  │  ↕                     │  ↕
  └──────── Main Memory ───┘

Thread 1이 x = 1로 수정해도
Thread 2의 CPU Cache에는 아직 x = 0일 수 있음
→ 가시성(Visibility) 문제
```

### happens-before 원칙

JMM이 보장하는 "A 작업이 B보다 반드시 먼저 보인다"는 규칙.

| 규칙 | 설명 |
|------|------|
| **프로그램 순서** | 같은 스레드에서 앞선 코드는 뒷 코드보다 먼저 실행 |
| **모니터 락** | `unlock()`은 이후의 `lock()`보다 먼저 보임 |
| **volatile** | 쓰기는 이후의 읽기보다 먼저 보임 |
| **스레드 시작** | `Thread.start()`는 스레드 내 모든 작업보다 먼저 |
| **스레드 종료** | 스레드 내 모든 작업은 `Thread.join()` 이전에 완료 |

---

## 2. volatile

변수를 CPU 캐시가 아닌 **항상 메인 메모리에서 읽고 쓰도록** 강제한다.

```java
// volatile 없을 때: Thread 2는 flag 변경을 영원히 못 볼 수 있음
private boolean flag = false;

// volatile 사용: 가시성 보장
private volatile boolean flag = false;

// Thread 1
flag = true;

// Thread 2
while (!flag) { } // flag 변경 즉시 감지
```

### volatile의 한계

**가시성만 보장, 원자성은 보장 안 함.**

```java
private volatile int count = 0;

// Thread 1, 2 동시에 실행 시 Race Condition 발생
count++; // 읽기 → 증가 → 쓰기 (3단계, 원자적이지 않음)
```

> **면접 포인트**: "volatile과 synchronized의 차이?" → volatile은 가시성만 보장. synchronized는 가시성 + 원자성(상호배제) 모두 보장. 단순 플래그 변수엔 volatile, 복합 연산엔 synchronized 또는 Atomic 사용.

---

## 3. synchronized

임계 구역(Critical Section)에 **한 번에 하나의 스레드만 진입**하도록 보장한다.

```java
public class Counter {
    private int count = 0;

    // 메서드 레벨: 인스턴스의 this를 락으로 사용
    public synchronized void increment() {
        count++;
    }

    // 블록 레벨: 더 세밀한 락 범위 지정
    public void incrementBlock() {
        synchronized (this) {
            count++;
        }
    }

    // static: 클래스 객체(Counter.class)를 락으로 사용
    public static synchronized void staticMethod() { ... }
}
```

### 모니터(Monitor)

모든 Java 객체는 하나의 모니터를 가진다. `synchronized`는 이 모니터를 획득/해제하는 것.

```
Thread 1 → 모니터 획득 → 임계 구역 진입
Thread 2 → 모니터 획득 시도 → BLOCKED 상태로 대기
Thread 1 → 모니터 해제
Thread 2 → 모니터 획득 → 임계 구역 진입
```

### synchronized 한계

- **재진입 가능(Reentrant)**: 같은 스레드가 이미 획득한 락을 다시 획득 가능
- **공정성 없음**: 대기 중인 스레드 중 누가 먼저 획득할지 보장 안 됨
- **인터럽트 불가**: 락 대기 중 인터럽트 처리 불가

---

## 4. ReentrantLock

`synchronized`의 한계를 극복한 명시적 락.

```java
private final ReentrantLock lock = new ReentrantLock();

public void increment() {
    lock.lock();
    try {
        count++;
    } finally {
        lock.unlock(); // 반드시 finally에서 해제
    }
}

// 타임아웃 락 획득 시도
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try {
        // 임계 구역
    } finally {
        lock.unlock();
    }
} else {
    // 락 획득 실패 처리
}
```

### synchronized vs ReentrantLock 비교

| 항목 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 락 획득 방식 | 자동 | 명시적 `lock()`/`unlock()` |
| 타임아웃 | 불가 | `tryLock(timeout)` 가능 |
| 인터럽트 | 불가 | `lockInterruptibly()` 가능 |
| 공정성 | 없음 | `new ReentrantLock(true)` 로 공정 락 가능 |
| 조건 변수 | `wait()`/`notify()` | `Condition` 객체 (다중 조건 가능) |
| 성능 | Java 6+ 이후 비슷 | 비슷 |

> **면접 포인트**: "언제 ReentrantLock을 쓰나?" → 타임아웃, 공정성, 여러 조건 변수가 필요할 때. 단순한 경우엔 synchronized가 더 간결하고 실수 여지가 없어 선호.

---

## 5. Atomic 클래스

CAS(Compare-And-Swap) 연산으로 **락 없이 원자적 연산**을 수행한다.

```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // 원자적 증가
count.compareAndSet(1, 2); // 현재 값이 1이면 2로 변경

AtomicLong total = new AtomicLong();
AtomicReference<String> ref = new AtomicReference<>("old");
AtomicBoolean flag = new AtomicBoolean(false);
```

### CAS 동작 원리

```
CAS(현재값, 기대값, 새값)
    │
    ├─ 현재값 == 기대값이면 → 새값으로 변경 (성공)
    └─ 현재값 != 기대값이면 → 실패, 재시도 (ABA 문제 주의)
```

```java
// count++ 내부 동작 (의사코드)
int current;
do {
    current = count.get();           // 현재값 읽기
} while (!count.compareAndSet(current, current + 1)); // 성공할 때까지 재시도
```

---

## 6. 동시성 컬렉션

| 클래스 | 설명 | 내부 동작 |
|--------|------|-----------|
| `ConcurrentHashMap` | 스레드 안전 HashMap | 버킷별 락 (Java 8: CAS + synchronized) |
| `CopyOnWriteArrayList` | 쓰기 시 배열 복사 | 읽기는 락 없음, 쓰기는 전체 복사 |
| `BlockingQueue` | 생산자-소비자 패턴 | `put()` 가득 차면 블로킹, `take()` 비면 블로킹 |
| `ConcurrentLinkedQueue` | 락-프리 큐 | CAS 기반 |

```java
// BlockingQueue: 생산자-소비자 패턴
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// 생산자
queue.put(task); // 가득 차면 대기

// 소비자
Task task = queue.take(); // 비어있으면 대기
```

---

## 7. Deadlock

두 스레드가 서로 상대방이 가진 락을 기다리며 **무한 대기**하는 상태.

```java
Object lockA = new Object();
Object lockB = new Object();

// Thread 1: A 획득 후 B 획득 시도
synchronized (lockA) {
    synchronized (lockB) { ... } // Thread 2가 B를 가지고 있어 대기
}

// Thread 2: B 획득 후 A 획득 시도
synchronized (lockB) {
    synchronized (lockA) { ... } // Thread 1이 A를 가지고 있어 대기
}
// → 서로 기다리며 영원히 진행 불가
```

### Deadlock 발생 조건 (코프만 조건 4가지)

| 조건 | 설명 |
|------|------|
| **상호 배제** | 한 번에 하나의 스레드만 자원 사용 |
| **점유와 대기** | 자원을 가진 채 다른 자원 대기 |
| **비선점** | 다른 스레드의 자원을 강제로 빼앗지 못함 |
| **순환 대기** | A→B→C→A 형태의 순환 의존 |

### Deadlock 예방/해결

```java
// 1. 락 획득 순서 고정 (순환 대기 제거)
// 항상 lockA → lockB 순서로만 획득

// 2. tryLock으로 타임아웃 설정
if (lock1.tryLock(1, TimeUnit.SECONDS)) {
    if (lock2.tryLock(1, TimeUnit.SECONDS)) {
        try { ... }
        finally { lock2.unlock(); }
    }
    finally { lock1.unlock(); }
}

// 3. 락 범위 최소화로 중첩 락 회피
```

---

## 8. ThreadPool & Executor

스레드를 직접 생성하지 않고 **풀에서 재사용**하여 생성/소멸 비용 절감.

```java
// 고정 크기 스레드풀
ExecutorService pool = Executors.newFixedThreadPool(10);

// 유연한 스레드풀 (실무 권장: 파라미터 직접 지정)
ExecutorService pool = new ThreadPoolExecutor(
    5,                          // corePoolSize: 기본 스레드 수
    20,                         // maximumPoolSize: 최대 스레드 수
    60, TimeUnit.SECONDS,       // keepAliveTime: 유휴 스레드 생존 시간
    new LinkedBlockingQueue<>(100), // workQueue: 대기열
    new ThreadPoolExecutor.CallerRunsPolicy() // 거부 정책
);

// 작업 제출
Future<String> future = pool.submit(() -> "result");
String result = future.get(); // 블로킹 대기

// 종료
pool.shutdown(); // 현재 작업 완료 후 종료
pool.shutdownNow(); // 즉시 종료 (인터럽트)
```

### 거부 정책 (RejectedExecutionHandler)

| 정책 | 동작 |
|------|------|
| `AbortPolicy` | `RejectedExecutionException` 던짐 **(기본값)** |
| `CallerRunsPolicy` | 호출한 스레드가 직접 실행 (속도 조절 효과) |
| `DiscardPolicy` | 조용히 버림 |
| `DiscardOldestPolicy` | 대기열 맨 앞 작업을 버리고 새 작업 추가 |

---

## 9. Virtual Thread (Java 21+)

Project Loom에서 도입. OS 스레드와 1:1 매핑이 아닌 **JVM이 관리하는 경량 스레드**.

```java
// Virtual Thread 생성
Thread vt = Thread.ofVirtual().start(() -> {
    // 블로킹 I/O 가능 (내부적으로 Unmount/Mount)
});

// Virtual Thread 풀 (ExecutorService)
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> someBlockingOperation());
```

### Platform Thread vs Virtual Thread

| 항목 | Platform Thread | Virtual Thread |
|------|----------------|----------------|
| 매핑 | OS Thread 1:1 | Carrier Thread에 N:M |
| 생성 비용 | 큼 (~1MB 스택) | 작음 (~수KB) |
| 최대 수 | 수천 개 | 수백만 개 가능 |
| 블로킹 I/O | OS 스레드 점유 | Carrier Thread 해제 후 재할당 |
| 적합한 상황 | CPU 집약 작업 | I/O 집약 작업 |

```
// Virtual Thread 블로킹 I/O 동작
Thread.sleep(1000) 호출
    → Virtual Thread: Carrier Thread에서 Unmount
    → Carrier Thread: 다른 Virtual Thread 실행
    → 1초 후: Virtual Thread를 다시 Carrier Thread에 Mount
```

> **면접 포인트**: "Virtual Thread가 WebFlux를 대체할 수 있나?" → I/O 집약 작업에서는 Virtual Thread로 동기 코드로도 높은 처리량을 낼 수 있다. 단, WebFlux의 Backpressure, Reactor 연산자 체인은 Virtual Thread가 대체하지 못한다. 용도가 다름.

---

## 10. 면접 Q&A

**Q. Race Condition이란?**
> 여러 스레드가 공유 자원에 동시에 접근할 때, 실행 순서에 따라 결과가 달라지는 상황. `count++`처럼 읽기-수정-쓰기가 원자적이지 않은 연산에서 주로 발생.

**Q. `HashMap`을 멀티스레드 환경에서 쓰면 왜 문제인가?**
> Java 8에서 resize 중 무한루프(CPU 100%) 문제는 해결됐지만, 여전히 데이터 유실, 잘못된 결과를 반환할 수 있다. 멀티스레드 환경에서는 `ConcurrentHashMap`을 사용해야 한다.

**Q. `volatile`은 언제 쓰나?**
> 단일 변수의 가시성만 보장하면 되는 상황에서 사용. 예: `boolean` 플래그, 싱글톤 DCL(Double-Checked Locking) 패턴. 복합 연산(++, 조건부 갱신)에는 사용 불가.

**Q. DCL(Double-Checked Locking) 패턴은?**
```java
public class Singleton {
    private static volatile Singleton instance; // volatile 필수

    public static Singleton getInstance() {
        if (instance == null) {                    // 1차 체크 (락 없음)
            synchronized (Singleton.class) {
                if (instance == null) {            // 2차 체크 (락 있음)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
// volatile 없으면: 객체 초기화 전에 참조가 공개되는 부분 초기화 문제 발생 가능
```

**Q. `synchronized`와 `Lock`은 성능 차이가 있나?**
> Java 6에서 synchronized에 Biased Locking, Thin Lock, Adaptive Spinning 최적화가 도입돼 단순한 경우엔 성능 차이가 거의 없다. ReentrantLock이 더 나은 경우는 공정성, 타임아웃, 다중 조건이 필요할 때다.
