# CAS 경합과 ThreadLocalRandom

## 1. CAS(Compare-And-Swap)란

CAS는 CPU가 제공하는 원자적(atomic) 명령어로, 세 개의 인자를 받는다.

- **메모리 주소**: 대상 변수
- **기대값(Expected Value)**: 현재 이 값일 것이라고 예상하는 값
- **새 값(New Value)**: 교체하고 싶은 값

```
if (현재값 == 기대값) {
    현재값 = 새값;
    return 성공;
} else {
    return 실패;   // 누군가 먼저 바꿨음 → 재시도
}
```

이 전체 과정이 **하드웨어 수준에서 단일 명령어**로 실행되므로 중간에 다른 스레드가 끼어들 수 없다.
Java에서는 `AtomicLong`, `AtomicInteger` 등이 내부적으로 `Unsafe.compareAndSwapLong()`을 호출하며,
이것이 x86의 `CMPXCHG` 명령어로 변환된다.

---

## 2. java.util.Random의 CAS 경합 문제

`Random`은 **단일 `AtomicLong seed`** 하나를 모든 스레드가 공유한다.

```
[Thread-0] ──┐
[Thread-1] ──┤──→ [ AtomicLong seed ]  ← 하나뿐인 공유 변수
[Thread-2] ──┤
[Thread-3] ──┘
```

### 내부 구현

```java
// java.util.Random.next() 내부
protected int next(int bits) {
    long oldseed, nextseed;
    AtomicLong seed = this.seed;
    do {
        oldseed = seed.get();
        nextseed = (oldseed * 0x5DEECE66DL + 0xBL);
    } while (!seed.compareAndSet(oldseed, nextseed)); // 실패하면 루프 반복
    return (int)(nextseed >>> (48 - bits));
}
```

### 경합 시나리오 (4 스레드 동시 호출)

```
Thread-0: seed=100 읽음 → next=742 계산 → CAS(100→742) 성공!
Thread-1: seed=100 읽음 → next=742 계산 → CAS(100→742) 실패 → 재시도
Thread-2: seed=100 읽음 → next=742 계산 → CAS(100→742) 실패 → 재시도
Thread-3: seed=100 읽음 → next=742 계산 → CAS(100→742) 실패 → 재시도
```

N개 스레드가 동시에 CAS를 시도하면 **1개만 성공하고 N-1개는 실패하여 루프를 재시도**한다.
스레드 수가 늘어날수록 실패율이 급격히 증가하며, CPU 사이클을 낭비한다.

추가로 여러 코어가 같은 캐시 라인에 쓰기를 시도하면 **캐시 라인 무효화(cache line invalidation)** 가
연쇄적으로 발생한다. 한 코어가 seed를 변경하면 다른 모든 코어의 L1/L2 캐시에 있던 해당 라인이
무효화되고 메인 메모리 또는 L3에서 다시 가져와야 한다.

---

## 3. ThreadLocalRandom의 해결 방식

### 핵심 아이디어: 공유 자체를 제거

```
[Thread-0] → [ seed_0 ]  ← Thread 객체 안의 전용 필드
[Thread-1] → [ seed_1 ]
[Thread-2] → [ seed_2 ]
[Thread-3] → [ seed_3 ]
```

각 스레드가 **자기만의 seed를 자기 Thread 객체 안에** 가지고 있으므로
다른 스레드와 공유할 변수가 아예 없다.

### 내부 구현

`java.lang.Thread` 클래스에는 다음 필드들이 선언되어 있다.

```java
// java.lang.Thread 내부
long threadLocalRandomSeed;          // 이 스레드 전용 seed
int  threadLocalRandomProbe;
int  threadLocalRandomSecondarySeed;
```

`ThreadLocalRandom.nextInt()` 호출 시 동작:

```java
// ThreadLocalRandom 내부 (개념적 표현)
public int nextInt(int bound) {
    long s = U.getLong(Thread.currentThread(), SEED_OFFSET); // 현재 스레드 필드 읽기
    s = s * 0x5DEECE66DL + gamma;                            // 다음 seed 계산
    U.putLong(Thread.currentThread(), SEED_OFFSET, s);       // 그냥 덮어씀
    // CAS 없음, synchronized 없음, volatile 없음
    return mix32(s) % bound;
}
```

자기 스레드의 필드를 자기만 읽고 쓰므로 동기화가 필요 없고, **경합이 구조적으로 불가능**하다.

---

## 4. DataInitializer 코드에서 경합이 없는 이유

```java
// Fixed Thread Pool의 각 스레드에서 독립적으로 실행
CompletableFuture.runAsync(() -> {
    List<Object[]> batch = memberBatch(batchStart, batchEnd);
    jdbcTemplate.batchUpdate(sql, batch);
}, executor);

private List<Object[]> memberBatch(long startId, long endId) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    // ↑ 이 람다를 실행하는 스레드의 전용 seed 참조
    //   다른 스레드와 아무것도 공유하지 않음

    for (long id = startId; id <= endId; id++) {
        batch.add(new Object[]{
            GRADES[rnd.nextInt(GRADES.length)],  // CAS 없는 단순 연산
            ...
        });
    }
    return batch;
}
```

8개 스레드가 동시에 `memberBatch()`를 호출해도, 각 스레드가 자기 `Thread` 객체 내부의
`seed` 필드만 건드리기 때문에 서로 간섭이 없다.

---

## 5. 성능 비교

| 스레드 수 | `Random` 처리량 | `ThreadLocalRandom` 처리량 |
|---------|----------------|--------------------------|
| 1       | 기준           | 비슷                      |
| 4       | ~25% (경합으로 하락) | ~400% (선형 증가)       |
| 16      | 더 느려짐 (negative scaling) | ~1600%         |

> 스레드를 추가할수록 `Random`은 오히려 느려지는 반면,
> `ThreadLocalRandom`은 스레드 수에 비례하여 선형으로 성능이 올라간다.

---

## 6. 정리

| 항목 | `Random` | `ThreadLocalRandom` |
|------|----------|---------------------|
| seed 저장 위치 | 힙의 `AtomicLong` (공유) | `Thread` 객체 내부 필드 (스레드별) |
| 난수 생성 동기화 | CAS 루프 (실패 시 재시도) | 없음 (plain read/write) |
| 캐시 라인 경합 | 있음 | 없음 |
| 스레드 증가 시 처리량 | 감소 | 선형 증가 |
| 적합한 환경 | 단일 스레드 또는 단순 사용 | 멀티스레드 병렬 처리 |

**결론**: 멀티스레드 환경에서 난수를 빈번하게 생성할 때는 항상 `ThreadLocalRandom`을 사용한다.
