# Kotlin Coroutines 완벽 가이드

코루틴을 전혀 모르는 상태에서 실무에 적용할 수 있을 때까지, 빠지는 내용 없이 정리한다.

---

## 목차

1. [코루틴이란 무엇인가](#1-코루틴이란-무엇인가)
2. [왜 코루틴이 필요한가](#2-왜-코루틴이-필요한가)
3. [코루틴의 기술적 배경](#3-코루틴의-기술적-배경)
4. [첫 번째 코루틴 만들기](#4-첫-번째-코루틴-만들기)
5. [suspend 함수](#5-suspend-함수)
6. [코루틴 빌더](#6-코루틴-빌더)
7. [CoroutineScope와 구조화된 동시성](#7-coroutinescope와-구조화된-동시성)
8. [CoroutineContext와 Dispatcher](#8-coroutinecontext와-dispatcher)
9. [Job과 생명주기](#9-job과-생명주기)
10. [취소와 타임아웃](#10-취소와-타임아웃)
11. [예외 처리](#11-예외-처리)
12. [async/await로 병렬 처리](#12-asyncawait로-병렬-처리)
13. [Flow - 비동기 스트림](#13-flow---비동기-스트림)
14. [Channel - 코루틴 간 통신](#14-channel---코루틴-간-통신)
15. [Mutex와 동시성 제어](#15-mutex와-동시성-제어)
16. [Spring Boot에서 코루틴 사용하기](#16-spring-boot에서-코루틴-사용하기)
17. [테스트](#17-테스트)
18. [코루틴 vs 다른 비동기 모델 비교](#18-코루틴-vs-다른-비동기-모델-비교)
19. [실전 패턴 모음](#19-실전-패턴-모음)
20. [흔한 실수와 주의점](#20-흔한-실수와-주의점)
21. [디버깅](#21-디버깅)
22. [성능 특성과 언제 써야 하는가](#22-성능-특성과-언제-써야-하는가)

---

## 1. 코루틴이란 무엇인가

### 한 문장 정의

> 코루틴은 **일시 중단(suspend)했다가 나중에 재개(resume)할 수 있는 경량 실행 단위**이다.

### 비유로 이해하기

식당에서 주문하는 상황을 생각해 보자.

**스레드 방식 (블로킹)**
```
1. 카운터에서 주문한다
2. 음식이 나올 때까지 카운터 앞에 서서 기다린다  ← 아무것도 못 함
3. 음식을 받는다
```
카운터 앞에 서 있는 동안 다른 일을 전혀 할 수 없다.

**코루틴 방식 (논블로킹)**
```
1. 카운터에서 주문한다
2. 진동벨을 받고 자리에 앉아서 다른 일을 한다  ← 일시 중단
3. 벨이 울리면 가서 음식을 받는다              ← 재개
```
기다리는 동안 다른 일을 할 수 있다.

### 핵심 특징

| 특징 | 설명 |
|------|------|
| **경량** | 하나의 스레드에서 수천~수만 개의 코루틴 실행 가능 |
| **일시 중단** | I/O 대기 중 스레드를 점유하지 않고 양보 |
| **순차적 코드** | 콜백/Promise 없이 동기 코드처럼 작성 |
| **구조화된 동시성** | 부모-자식 관계로 생명주기 자동 관리 |

### 코루틴 ≠ 스레드

```
스레드 1개 = OS가 관리하는 실행 단위 (무거움, ~1MB 스택)
코루틴 1개 = Kotlin 런타임이 관리하는 실행 단위 (가벼움, ~수백 바이트)

스레드 1개 위에서 코루틴 수천 개가 번갈아 실행될 수 있다.
```

```
┌─────────────────── Thread 1 ──────────────────────┐
│                                                    │
│  ┌─Coroutine A─┐  ┌─Coroutine B─┐  ┌─Coroutine C─┐│
│  │ 실행        │  │ 중단(I/O)   │  │ 실행        ││
│  │ 중단(I/O)   │  │ 재개 → 실행 │  │ 중단(I/O)   ││
│  │ 재개 → 실행 │  │ 완료        │  │ 재개 → 실행 ││
│  └─────────────┘  └─────────────┘  └─────────────┘│
└────────────────────────────────────────────────────┘
```

---

## 2. 왜 코루틴이 필요한가

### 문제: 동시에 많은 요청을 처리해야 한다

웹 서버가 1,000명의 동시 요청을 처리한다고 하자.

#### 방법 1: 스레드 1개 = 요청 1개 (전통적 방식)

```
요청 1 → 스레드 1 → DB 쿼리 대기(200ms) → 응답
요청 2 → 스레드 2 → DB 쿼리 대기(200ms) → 응답
...
요청 1000 → 스레드 1000 → DB 쿼리 대기(200ms) → 응답
```

**문제점:**
- 스레드 1,000개 = 메모리 ~1GB 소비
- 대부분 시간을 "대기"하며 스레드를 점유
- 스레드 풀 고갈 → 요청 거부

#### 방법 2: 콜백 (Node.js 스타일)

```javascript
getUser(id, function(user) {
    getOrders(user.id, function(orders) {
        getPayments(orders[0].id, function(payments) {
            // 콜백 지옥
        });
    });
});
```

**문제점:** 콜백 지옥, 에러 처리 복잡, 가독성 최악

#### 방법 3: 리액티브 (WebFlux / Reactor)

```java
Mono.fromCallable(() -> userRepository.findById(id))
    .flatMap(user -> orderRepository.findByUser(user))
    .flatMap(orders -> paymentRepository.findByOrder(orders.get(0)))
    .subscribe(payments -> { ... });
```

**문제점:** 학습 곡선 높음, 디버깅 어려움, 모든 코드가 리액티브로 전염

#### 방법 4: 코루틴

```kotlin
suspend fun handleRequest(id: Long): Response {
    val user = userRepository.findById(id)          // 중단점
    val orders = orderRepository.findByUser(user)    // 중단점
    val payments = paymentRepository.findByOrder(orders[0]) // 중단점
    return Response(user, orders, payments)
}
```

**장점:**
- 동기 코드와 동일한 가독성
- 스레드를 점유하지 않아 수천 개 동시 처리 가능
- try-catch로 에러 처리
- 디버깅 시 스택 트레이스 정상 출력

### 수치로 비교

```
10,000개 동시 작업 시:

스레드 방식:
  - 메모리: ~10GB (스레드당 ~1MB)
  - 생성 시간: 수 초
  - 컨텍스트 스위칭: OS 레벨 (무거움)

코루틴 방식:
  - 메모리: ~수십MB (코루틴당 ~수백 바이트~수 KB)
  - 생성 시간: 수십 밀리초
  - 컨텍스트 스위칭: 유저 레벨 (가벼움)
```

---

## 3. 코루틴의 기술적 배경

### Continuation Passing Style (CPS)

코루틴의 핵심은 컴파일러가 `suspend` 함수를 **상태 머신(state machine)**으로 변환하는 것이다.

#### 우리가 작성하는 코드

```kotlin
suspend fun fetchUserData(id: Long): UserData {
    val user = userApi.getUser(id)           // 중단점 1
    val orders = orderApi.getOrders(user.id) // 중단점 2
    return UserData(user, orders)
}
```

#### 컴파일러가 변환한 코드 (개념적)

```kotlin
// 실제로는 이렇게 변환됨 (단순화한 버전)
fun fetchUserData(id: Long, continuation: Continuation<UserData>): Any? {
    val sm = continuation as? FetchUserDataSM ?: FetchUserDataSM(continuation)

    when (sm.label) {
        0 -> {
            sm.label = 1
            val result = userApi.getUser(id, sm)  // 중단될 수 있음
            if (result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
            sm.user = result as User
        }
        1 -> {
            sm.user = sm.result as User
            sm.label = 2
            val result = orderApi.getOrders(sm.user.id, sm)
            if (result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
            sm.orders = result as List<Order>
        }
        2 -> {
            sm.orders = sm.result as List<Order>
            return UserData(sm.user, sm.orders)
        }
    }
}
```

핵심 포인트:
- `suspend` 함수는 `Continuation` 파라미터가 추가된다
- 각 중단점(suspend call)마다 `label`이 증가한다
- 중단 후 재개될 때 `label` 값에 따라 이어서 실행한다
- **스레드를 블록하지 않고** 나중에 다시 불러준다

### Continuation이란?

```kotlin
interface Continuation<in T> {
    val context: CoroutineContext
    fun resumeWith(result: Result<T>)
}
```

"이 작업이 끝나면 이 Continuation의 `resumeWith`를 호출해서 결과를 전달해줘" 라는 의미다.

### 도식으로 보는 중단과 재개

```
Thread A                          Thread B (또는 Thread A)
────────                          ─────────────────────────
코루틴 실행 시작
    │
    ▼
suspend 함수 호출 (예: DB 쿼리)
    │
    ▼
상태 저장 (label, 로컬 변수)
    │
    ▼
COROUTINE_SUSPENDED 반환
    │
    ▼
Thread A 해방됨! (다른 일 가능)
                                  DB 쿼리 완료
                                      │
                                      ▼
                                  continuation.resumeWith(결과)
                                      │
                                      ▼
                                  상태 복원 (label, 로컬 변수)
                                      │
                                      ▼
                                  코루틴 이어서 실행
```

---

## 4. 첫 번째 코루틴 만들기

### 의존성 추가

```groovy
// build.gradle.kts
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Spring 연동 시
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.1")

    // 테스트
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
```

### Hello, Coroutine

```kotlin
import kotlinx.coroutines.*

fun main() = runBlocking {  // 코루틴 세계의 입구
    println("시작: ${Thread.currentThread().name}")

    launch {  // 새 코루틴 시작
        delay(1000)  // 1초 중단 (Thread.sleep이 아님!)
        println("코루틴: ${Thread.currentThread().name}")
    }

    println("메인 코루틴 계속 실행")
}

// 출력:
// 시작: main
// 메인 코루틴 계속 실행
// (1초 후)
// 코루틴: main
```

### 10만 개의 코루틴

```kotlin
fun main() = runBlocking {
    val time = measureTimeMillis {
        val jobs = List(100_000) {
            launch {
                delay(1000)
                print(".")
            }
        }
        jobs.forEach { it.join() }
    }
    println("\n완료: ${time}ms")  // ~1초 (10만 개가 동시에 중단/재개)
}
```

스레드 10만 개를 만들면 OutOfMemoryError가 발생하지만, 코루틴 10만 개는 문제없다.

---

## 5. suspend 함수

### 기본 개념

`suspend` 키워드는 "이 함수는 중단될 수 있다"는 표시이다.

```kotlin
// suspend 함수: 코루틴 안에서만 호출 가능
suspend fun fetchUser(id: Long): User {
    delay(100)  // 네트워크 호출 시뮬레이션
    return User(id, "홍길동")
}

// 일반 함수에서는 호출 불가
fun main() {
    // fetchUser(1) // 컴파일 에러!
}

// 코루틴 안에서 호출
fun main() = runBlocking {
    val user = fetchUser(1) // OK
    println(user)
}
```

### suspend 함수의 규칙

```kotlin
// 1. suspend 함수는 다른 suspend 함수를 호출할 수 있다
suspend fun getFullProfile(id: Long): Profile {
    val user = fetchUser(id)         // suspend 호출
    val orders = fetchOrders(id)     // suspend 호출
    return Profile(user, orders)
}

// 2. suspend 함수는 일반 함수를 호출할 수 있다
suspend fun process(id: Long): String {
    val user = fetchUser(id)  // suspend
    return formatName(user)   // 일반 함수, OK
}

// 3. 일반 함수는 suspend 함수를 직접 호출할 수 없다
fun normalFunction() {
    // fetchUser(1)  // 컴파일 에러!
}
```

### suspend는 전염된다?

```
호출 방향: A → B → C → D (suspend)

D가 suspend이면:
  C도 suspend여야 하고
  B도 suspend여야 하고
  A는 코루틴 빌더(launch, runBlocking 등)로 시작해야 함

이것을 "coloring problem"이라고 부르기도 한다.
```

하지만 코루틴 빌더(`launch`, `async`)를 사용하면 일반 함수에서도 코루틴 세계로 진입할 수 있다.

### 주의: suspend ≠ 자동으로 다른 스레드에서 실행

```kotlin
// 이 함수는 suspend이지만, CPU 작업을 하고 있어서
// 호출한 스레드를 점유한다
suspend fun heavyCalculation(): Int {
    // 이건 BAD! 스레드를 블록함
    var sum = 0
    for (i in 1..1_000_000_000) sum += i
    return sum
}

// 올바른 방법: withContext로 적절한 Dispatcher 지정
suspend fun heavyCalculation(): Int = withContext(Dispatchers.Default) {
    var sum = 0
    for (i in 1..1_000_000_000) sum += i
    sum
}
```

---

## 6. 코루틴 빌더

코루틴 빌더는 "코루틴을 생성하는 함수"이다.

### runBlocking

```kotlin
// 현재 스레드를 블로킹하면서 코루틴 실행
// 용도: main 함수, 테스트에서 코루틴 세계 진입점
fun main() = runBlocking {
    // 여기가 코루틴 세계
    delay(1000)
    println("완료")
}

// 주의: 프로덕션 코드에서 runBlocking은 최소한으로 사용
// Spring에서는 프레임워크가 코루틴을 시작해주므로 거의 불필요
```

### launch

```kotlin
// "실행하고 잊기 (fire and forget)" - 반환값 없음
// 반환: Job (취소, 대기 가능)
fun main() = runBlocking {
    val job: Job = launch {
        delay(1000)
        println("launch 완료")
    }

    println("launch 시작됨")
    job.join()  // 코루틴 완료까지 대기
}
```

### async

```kotlin
// 결과를 반환하는 코루틴
// 반환: Deferred<T> (Job의 하위 타입, await()로 결과 받기)
fun main() = runBlocking {
    val deferred: Deferred<Int> = async {
        delay(1000)
        42  // 반환값
    }

    println("계산 중...")
    val result = deferred.await()  // 결과를 기다림
    println("결과: $result")  // 결과: 42
}
```

### coroutineScope

```kotlin
// 자식 코루틴이 모두 완료될 때까지 대기하는 스코프 생성
// launch/async와 달리 새 코루틴을 생성하지 않고, 현재 코루틴 안에서 스코프만 만듦
suspend fun loadData(): Data = coroutineScope {
    val user = async { fetchUser() }
    val orders = async { fetchOrders() }

    Data(user.await(), orders.await())
    // 이 블록이 끝나면 자식 코루틴이 모두 완료됨을 보장
}
```

### supervisorScope

```kotlin
// coroutineScope와 유사하지만, 자식 하나가 실패해도 다른 자식에 영향 없음
suspend fun loadDashboard(): Dashboard = supervisorScope {
    val user = async { fetchUser() }           // 성공
    val recommendations = async { fetchRecs() } // 실패해도 OK

    Dashboard(
        user = user.await(),
        recommendations = try { recommendations.await() } catch (e: Exception) { emptyList() }
    )
}
```

### 빌더 비교표

| 빌더 | 반환 타입 | 스레드 블로킹 | 결과값 | 용도 |
|------|----------|-------------|-------|------|
| `runBlocking` | `T` | O (블로킹) | O | main, 테스트 |
| `launch` | `Job` | X | X | fire-and-forget |
| `async` | `Deferred<T>` | X | O | 병렬 작업 + 결과 |
| `coroutineScope` | `T` | X (중단) | O | 자식 묶기 |
| `supervisorScope` | `T` | X (중단) | O | 독립적 자식 |

---

## 7. CoroutineScope와 구조화된 동시성

### 구조화된 동시성(Structured Concurrency)이란?

> **모든 코루틴은 부모가 있고, 부모의 수명 안에서만 존재한다.**

이것이 코루틴의 가장 중요한 설계 원칙이다.

```
runBlocking (루트)
    ├── launch (자식 1)
    │     ├── launch (손자 1-1)
    │     └── async (손자 1-2)
    └── launch (자식 2)

규칙:
1. 부모는 모든 자식이 완료될 때까지 끝나지 않는다
2. 부모가 취소되면 모든 자식도 취소된다
3. 자식이 예외로 실패하면 부모에게 전파된다 (supervisorScope 제외)
```

### 왜 중요한가?

```kotlin
// BAD: 구조화되지 않은 동시성 (GlobalScope)
fun processRequest() {
    GlobalScope.launch {
        // 이 코루틴은 누가 관리하지?
        // 요청이 취소되어도 계속 실행됨
        // 예외가 발생하면 어디로 가지?
        // 메모리 누수 위험!
        expensiveOperation()
    }
}

// GOOD: 구조화된 동시성
suspend fun processRequest() = coroutineScope {
    launch {
        // 부모(coroutineScope)가 관리
        // 부모 취소 → 자동 취소
        // 예외 → 부모에게 전파
        // 메모리 누수 없음
        expensiveOperation()
    }
}
```

### CoroutineScope 만들기

```kotlin
// 1. 클래스에 스코프 부여 (Android ViewModel, Spring Component 등)
class OrderService : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Default + job

    fun processAsync(orderId: Long) {
        launch {  // this(OrderService)의 스코프에서 실행
            // ...
        }
    }

    fun destroy() {
        job.cancel()  // 모든 자식 코루틴 취소
    }
}

// 2. 더 간단한 방법
class OrderService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun processAsync(orderId: Long) {
        scope.launch { /* ... */ }
    }

    fun destroy() {
        scope.cancel()
    }
}
```

### 스코프 계층 도식

```
CoroutineScope (수명의 시작)
    │
    ├── launch {                    ← Job 1
    │       │
    │       ├── launch { ... }      ← Job 1-1
    │       │
    │       └── async { ... }       ← Job 1-2
    │
    └── launch {                    ← Job 2
            │
            └── launch { ... }      ← Job 2-1

scope.cancel() 호출 시:
  → Job 1 취소 → Job 1-1, 1-2 취소
  → Job 2 취소 → Job 2-1 취소
  → 전체 트리가 깔끔하게 정리됨
```

---

## 8. CoroutineContext와 Dispatcher

### CoroutineContext란?

코루틴이 "어디서", "어떻게" 실행되는지를 결정하는 설정 묶음이다.

```kotlin
// CoroutineContext = Dispatcher + Job + CoroutineName + ExceptionHandler + ...
val context = Dispatchers.IO + SupervisorJob() + CoroutineName("my-coroutine")

launch(context) {
    // 이 코루틴은 IO 스레드에서, SupervisorJob 아래에서, "my-coroutine"이라는 이름으로 실행
}
```

### Dispatcher: 어떤 스레드에서 실행할 것인가

| Dispatcher | 스레드 풀 | 용도 |
|-----------|----------|------|
| `Dispatchers.Main` | UI 스레드 (Android/Desktop) | UI 업데이트 |
| `Dispatchers.Default` | CPU 코어 수만큼 | CPU 집약적 작업 (계산, 정렬, JSON 파싱) |
| `Dispatchers.IO` | 최대 64개 (설정 가능) | I/O 작업 (네트워크, 파일, DB) |
| `Dispatchers.Unconfined` | 호출 스레드 | 테스트/특수 상황 |

### 왜 구분하는가?

```
CPU 집약적 작업 (Dispatchers.Default)
  - 스레드를 계속 사용함 (계산 중)
  - 코어 수만큼만 있으면 충분 (8코어 → 8스레드)
  - 더 많은 스레드 = 컨텍스트 스위칭 오버헤드만 증가

I/O 작업 (Dispatchers.IO)
  - 스레드가 대부분 대기 상태 (네트워크/디스크 응답 기다림)
  - 많은 스레드가 필요 (동시에 여러 I/O 가능)
  - 64개까지 자동으로 확장
```

### withContext: 실행 중 Dispatcher 전환

```kotlin
suspend fun processImage(url: String): Bitmap {
    // IO 스레드에서 다운로드
    val data = withContext(Dispatchers.IO) {
        httpClient.download(url)
    }

    // Default 스레드에서 이미지 처리
    val bitmap = withContext(Dispatchers.Default) {
        decodeAndResize(data)
    }

    return bitmap
}
```

### withContext는 launch와 다르다

```kotlin
// launch: 새 코루틴을 시작하고 바로 다음 줄로 넘어감 (비동기)
launch(Dispatchers.IO) {
    saveToDb(data)  // 별도로 실행됨
}
println("이 줄은 saveToDb 완료 전에 실행될 수 있음")

// withContext: 블록이 완료될 때까지 현재 코루틴을 중단 (순차적)
withContext(Dispatchers.IO) {
    saveToDb(data)  // 여기서 중단
}
println("이 줄은 saveToDb 완료 후에 실행됨")
```

### 커스텀 Dispatcher

```kotlin
// 단일 스레드 Dispatcher (순서 보장이 필요할 때)
val singleThread = newSingleThreadContext("my-thread")

// 고정 크기 스레드 풀
val fixedPool = newFixedThreadPoolContext(4, "my-pool")

// 사용 후 반드시 닫기
singleThread.close()
fixedPool.close()

// Java Executor를 Dispatcher로 변환
val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
```

---

## 9. Job과 생명주기

### Job 상태 다이어그램

```
                      ┌──────────┐
                      │   New    │  ← launch(start = LAZY) 로 생성 시
                      └────┬─────┘
                           │ start()
                      ┌────▼─────┐
                ┌─────│  Active  │─────┐
                │     └────┬─────┘     │
                │          │           │
           cancel()    완료됨      예외 발생
                │          │           │
          ┌─────▼──────┐   │    ┌──────▼──────┐
          │ Cancelling │   │    │ Cancelling  │
          └─────┬──────┘   │    └──────┬──────┘
                │          │           │
          ┌─────▼──────┐   │    ┌──────▼──────┐
          │ Cancelled  │   │    │   Failed    │
          └────────────┘   │    └─────────────┘
                     ┌─────▼──────┐
                     │ Completed  │
                     └────────────┘
```

### Job 조작

```kotlin
val job = launch {
    repeat(100) { i ->
        println("작업 $i")
        delay(100)
    }
}

// 상태 확인
println(job.isActive)     // true
println(job.isCompleted)  // false
println(job.isCancelled)  // false

// 완료 대기
job.join()

// 취소
job.cancel()
job.cancelAndJoin()  // 취소 + 완료 대기 (권장)
```

### 지연 시작 (Lazy)

```kotlin
val job = launch(start = CoroutineStart.LAZY) {
    println("이 코루틴은 start() 호출 전까지 실행되지 않음")
}

// 어떤 조건에서만 시작
if (shouldRun) {
    job.start()
}
```

---

## 10. 취소와 타임아웃

### 취소의 기본 원리

코루틴의 취소는 **협력적(cooperative)**이다. 코루틴 스스로가 취소를 확인해야 한다.

```kotlin
val job = launch {
    repeat(1000) { i ->
        println("작업 $i")
        delay(100)  // ← 이 중단점에서 취소가 확인됨
    }
}

delay(500)
job.cancelAndJoin()  // 5번째 반복쯤에서 취소됨
```

### 취소가 안 되는 경우

```kotlin
// BAD: 중단점이 없으면 취소가 확인되지 않음
val job = launch {
    var i = 0
    while (i < 1_000_000_000) {
        i++  // 중단점 없음! 취소 불가!
    }
}

job.cancelAndJoin()  // 취소 요청했지만 무시됨
```

### 취소 가능하게 만드는 방법

```kotlin
// 방법 1: isActive 체크
val job = launch {
    var i = 0
    while (isActive) {  // 취소되면 false
        i++
        if (i % 1000 == 0) yield()  // 또는 주기적으로 yield()
    }
}

// 방법 2: ensureActive()
val job = launch {
    repeat(1_000_000) {
        ensureActive()  // 취소되었으면 CancellationException throw
        heavyWork()
    }
}

// 방법 3: yield() - 다른 코루틴에게 실행 기회를 주면서 취소 체크
val job = launch {
    repeat(1_000_000) {
        yield()  // 중단점 생성 + 취소 체크
        heavyWork()
    }
}
```

### CancellationException

```kotlin
val job = launch {
    try {
        delay(10000)
    } catch (e: CancellationException) {
        // 취소 시 여기로 옴
        println("취소됨!")
        throw e  // 반드시 다시 throw! (또는 catch하지 않기)
    } finally {
        // 정리 작업은 finally에서
        println("리소스 정리")

        // 주의: finally에서 suspend 함수 호출은 기본적으로 무시됨
        // 필요하면 NonCancellable 사용
        withContext(NonCancellable) {
            delay(100)  // 취소된 코루틴에서도 실행됨
            saveProgress()
        }
    }
}
```

> **중요: CancellationException을 삼키면 안 된다!**
> `catch (e: Exception)`으로 잡으면 CancellationException도 잡혀서 취소가 전파되지 않는다.

```kotlin
// BAD
try {
    suspendFunction()
} catch (e: Exception) {  // CancellationException도 잡힘!
    log.error("에러", e)
    // 취소가 무시됨 → 코루틴이 계속 실행됨
}

// GOOD
try {
    suspendFunction()
} catch (e: CancellationException) {
    throw e  // 취소는 다시 throw
} catch (e: Exception) {
    log.error("에러", e)
}

// 더 간단한 방법: runCatching 사용 시 주의
suspendFunction().runCatching { }  // CancellationException을 삼킬 수 있음!
```

### 타임아웃

```kotlin
// withTimeout: 시간 초과 시 TimeoutCancellationException 발생
try {
    val result = withTimeout(3000) {
        fetchFromSlowApi()
    }
} catch (e: TimeoutCancellationException) {
    println("3초 초과!")
}

// withTimeoutOrNull: 시간 초과 시 null 반환 (예외 없음)
val result = withTimeoutOrNull(3000) {
    fetchFromSlowApi()
} ?: fallbackValue
```

---

## 11. 예외 처리

### launch vs async의 예외 처리 차이

```kotlin
// launch: 예외가 즉시 부모로 전파
runBlocking {
    launch {
        throw RuntimeException("launch 에러")
        // → 부모(runBlocking)로 전파 → 프로그램 크래시
    }
}

// async: 예외가 await() 호출 시 전파
runBlocking {
    val deferred = async {
        throw RuntimeException("async 에러")
        // → 아직 전파 안 됨
    }

    // 여기서 전파됨
    try {
        deferred.await()
    } catch (e: RuntimeException) {
        println("잡았다: ${e.message}")
    }
}
```

### CoroutineExceptionHandler

```kotlin
// 최상위 코루틴의 마지막 방어선 (catch-all)
val handler = CoroutineExceptionHandler { context, exception ->
    println("잡히지 않은 예외: ${exception.message}")
    // 로깅, 알림 등
}

val scope = CoroutineScope(SupervisorJob() + handler)

scope.launch {
    throw RuntimeException("에러!")
    // → handler에서 처리됨
}

// 주의: CoroutineExceptionHandler는 launch에만 동작
// async의 예외는 await()에서 처리해야 함
```

### SupervisorJob: 자식 실패 격리

```kotlin
// 일반 Job: 자식 하나 실패 → 형제도 모두 취소
coroutineScope {
    launch { delay(1000); println("작업 1 완료") }
    launch { throw RuntimeException("실패!") }  // 이것 때문에
    launch { delay(1000); println("작업 3 완료") }
    // → 작업 1, 3도 취소됨!
}

// SupervisorJob: 자식 하나 실패 → 형제는 계속 실행
supervisorScope {
    launch { delay(1000); println("작업 1 완료") }  // 정상 실행
    launch { throw RuntimeException("실패!") }       // 혼자 실패
    launch { delay(1000); println("작업 3 완료") }  // 정상 실행
}
```

### 예외 처리 전략 정리

```
                  예외 발생
                     │
            ┌────────┼────────┐
            │        │        │
          launch   async   coroutineScope
            │        │        │
       즉시 전파   await()   즉시 전파
            │     호출 시      │
            │        │        │
            ▼        ▼        ▼
     ┌──────────────────────────────┐
     │     부모 Job에 전파            │
     └──────┬───────────────────────┘
            │
     ┌──────┼──────────┐
     │      │          │
  일반 Job  │   SupervisorJob
     │      │          │
  형제 취소  │    형제 유지
     │      │          │
     ▼      ▼          ▼
     ExceptionHandler
     (최후의 수단)
```

### 실전 예외 처리 패턴

```kotlin
// 패턴 1: try-catch (가장 일반적)
suspend fun safeOperation(): Result<Data> {
    return try {
        val data = riskyApiCall()
        Result.success(data)
    } catch (e: CancellationException) {
        throw e  // 취소는 반드시 다시 throw
    } catch (e: Exception) {
        log.error("API 호출 실패", e)
        Result.failure(e)
    }
}

// 패턴 2: supervisorScope + 개별 try-catch
suspend fun loadDashboard(): Dashboard = supervisorScope {
    val user = async {
        try { fetchUser() } catch (e: Exception) { defaultUser() }
    }
    val orders = async {
        try { fetchOrders() } catch (e: Exception) { emptyList() }
    }
    Dashboard(user.await(), orders.await())
}

// 패턴 3: 재시도
suspend fun <T> retry(
    times: Int = 3,
    delay: Long = 1000,
    block: suspend () -> T,
): T {
    var lastException: Exception? = null
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastException = e
            if (attempt < times - 1) delay(delay * (attempt + 1))
        }
    }
    throw lastException!!
}

// 사용
val data = retry(times = 3, delay = 1000) {
    apiClient.fetchData()
}
```

---

## 12. async/await로 병렬 처리

### 순차 vs 병렬

```kotlin
// 순차 실행: 1초 + 1초 = 총 2초
suspend fun sequential() {
    val user = fetchUser()    // 1초
    val orders = fetchOrders() // 1초
    // 총 2초
}

// 병렬 실행: max(1초, 1초) = 총 1초
suspend fun parallel() = coroutineScope {
    val user = async { fetchUser() }    // 동시 시작
    val orders = async { fetchOrders() } // 동시 시작
    // 총 1초
    process(user.await(), orders.await())
}
```

### 도식

```
순차 실행:
Thread ──[fetchUser 1초]──[fetchOrders 1초]──  총 2초

병렬 실행:
Thread ──[fetchUser 1초    ]──
     └──[fetchOrders 1초   ]──                 총 1초
```

### 여러 작업 병렬 처리

```kotlin
// N개의 작업을 병렬로 실행하고 결과 모으기
suspend fun fetchAllPrices(symbols: List<String>): List<Price> = coroutineScope {
    symbols.map { symbol ->
        async { priceApi.getPrice(symbol) }
    }.awaitAll()  // 모든 Deferred를 한 번에 await
}

// 제한된 병렬도 (한 번에 10개씩)
suspend fun fetchAllPricesThrottled(symbols: List<String>): List<Price> = coroutineScope {
    val semaphore = Semaphore(10)

    symbols.map { symbol ->
        async {
            semaphore.withPermit {
                priceApi.getPrice(symbol)
            }
        }
    }.awaitAll()
}
```

### async 주의사항

```kotlin
// BAD: coroutineScope 없이 async
suspend fun loadData() {
    // async가 어떤 스코프에서 실행되는지 불명확
    // 예외 처리가 어려움
}

// GOOD: 항상 coroutineScope 안에서 async 사용
suspend fun loadData() = coroutineScope {
    val a = async { fetchA() }
    val b = async { fetchB() }
    combine(a.await(), b.await())
}

// BAD: async 즉시 await (순차 실행과 동일, 의미 없음)
val result = async { compute() }.await()

// GOOD: 여러 async를 먼저 시작한 후 나중에 await
val a = async { computeA() }
val b = async { computeB() }
val result = a.await() + b.await()
```

---

## 13. Flow - 비동기 스트림

Flow는 "시간에 걸쳐 여러 값을 방출하는 비동기 시퀀스"이다.
Java의 `Stream`, Reactor의 `Flux`, RxJava의 `Observable`에 해당한다.

### 기본 개념

```
단일 값:  suspend 함수   → suspend fun getUser(): User
여러 값:  Flow           → fun getUsers(): Flow<User>
```

### Flow 생성

```kotlin
// 1. flow 빌더
fun countDown(from: Int): Flow<Int> = flow {
    for (i in from downTo 0) {
        emit(i)       // 값 방출
        delay(1000)   // 1초 간격
    }
}

// 2. 컬렉션에서 Flow로
val flow = listOf(1, 2, 3).asFlow()

// 3. flowOf
val flow = flowOf("a", "b", "c")

// 4. channelFlow (동시성 필요 시)
fun fetchPages(): Flow<Page> = channelFlow {
    (1..10).forEach { page ->
        launch {
            val data = api.getPage(page)  // 병렬 호출
            send(data)                     // 스레드 세이프
        }
    }
}
```

### Flow 수집 (collect)

```kotlin
// Flow는 cold stream: collect할 때까지 실행되지 않음
val flow = flow {
    println("Flow 시작")  // collect 전에는 실행 안 됨
    emit(1)
    emit(2)
}

// collect로 수집
flow.collect { value ->
    println("받은 값: $value")
}
// 출력:
// Flow 시작
// 받은 값: 1
// 받은 값: 2
```

### Flow 연산자

```kotlin
val result = flowOf(1, 2, 3, 4, 5)
    .filter { it % 2 == 0 }              // 짝수만
    .map { it * 10 }                      // 10배
    .onEach { println("처리: $it") }       // 부가 작업
    .toList()                              // 결과 수집

// 결과: [20, 40]
```

### 주요 연산자 목록

```kotlin
// 변환
flow.map { transform(it) }           // 값 변환
flow.flatMapConcat { subFlow(it) }   // Flow를 순차적으로 평탄화
flow.flatMapMerge { subFlow(it) }    // Flow를 병렬로 평탄화
flow.flatMapLatest { subFlow(it) }   // 최신 Flow만 유지

// 필터링
flow.filter { condition(it) }
flow.filterNotNull()
flow.take(5)                          // 처음 5개만
flow.drop(3)                          // 처음 3개 건너뛰기
flow.distinctUntilChanged()           // 연속 중복 제거

// 결합
flow1.zip(flow2) { a, b -> "$a-$b" } // 1:1 결합
flow1.combine(flow2) { a, b -> "$a-$b" } // 최신 값끼리 결합

// 누적
flow.reduce { acc, value -> acc + value }
flow.fold(0) { acc, value -> acc + value }

// 수집
flow.collect { }                      // 모든 값 소비
flow.toList()                         // List로 변환
flow.first()                          // 첫 번째 값
flow.single()                         // 값이 정확히 1개
flow.count()                          // 개수
```

### Flow의 컨텍스트

```kotlin
// flowOn: upstream의 Dispatcher 변경
fun fetchUsers(): Flow<User> = flow {
    // 이 블록은 IO에서 실행
    val users = db.queryAll()
    users.forEach { emit(it) }
}.flowOn(Dispatchers.IO)  // emit까지가 IO에서 실행

// collect는 호출자의 Dispatcher에서 실행
withContext(Dispatchers.Main) {
    fetchUsers().collect { user ->
        // 이 블록은 Main에서 실행
        updateUI(user)
    }
}
```

### 에러 처리

```kotlin
flow
    .catch { e ->
        // upstream에서 발생한 예외 처리
        emit(fallbackValue)    // 대체 값 방출 가능
        log.error("에러", e)
    }
    .onCompletion { cause ->
        // finally와 유사 (성공/실패 모두)
        if (cause != null) println("에러로 종료: $cause")
        else println("정상 종료")
    }
    .collect { value ->
        // 여기서 발생한 예외는 catch에서 잡히지 않음!
        process(value)
    }
```

### StateFlow와 SharedFlow

```kotlin
// StateFlow: 상태 홀더 (항상 최신 값 하나를 가짐)
class ViewModel {
    private val _uiState = MutableStateFlow(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val data = repository.fetchData()
            _uiState.value = UiState.Success(data)
        }
    }
}

// SharedFlow: 이벤트 브로드캐스트 (구독자 여러 명)
class EventBus {
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun emit(event: Event) {
        _events.emit(event)
    }
}
```

### StateFlow vs SharedFlow

| 특성 | StateFlow | SharedFlow |
|------|-----------|------------|
| 초기값 | 필수 | 불필요 |
| 최신값 유지 | O (`.value`) | X (설정 가능) |
| 중복 값 방출 | X (같은 값 무시) | O |
| 구독자 0명일 때 | 값 유지 | 값 버림 (설정 가능) |
| 용도 | 상태 관리 | 이벤트 전달 |

---

## 14. Channel - 코루틴 간 통신

Channel은 코루틴 간에 값을 주고받는 파이프이다.

### 기본 사용

```kotlin
val channel = Channel<Int>()

// 생산자
launch {
    for (i in 1..5) {
        channel.send(i)      // 수신자가 없으면 중단
        println("보냄: $i")
    }
    channel.close()           // 전송 완료
}

// 소비자
launch {
    for (value in channel) {  // close될 때까지 반복
        println("받음: $value")
    }
}
```

### Channel 용량 (버퍼)

```kotlin
// RENDEZVOUS (기본): 버퍼 없음 → send와 receive가 만날 때까지 대기
val ch = Channel<Int>()

// BUFFERED: 기본 버퍼 크기 (64)
val ch = Channel<Int>(Channel.BUFFERED)

// 고정 크기 버퍼
val ch = Channel<Int>(10)

// UNLIMITED: 무한 버퍼 (메모리 주의)
val ch = Channel<Int>(Channel.UNLIMITED)

// CONFLATED: 최신 값만 유지 (이전 값은 버림)
val ch = Channel<Int>(Channel.CONFLATED)
```

### produce 빌더

```kotlin
// Channel + launch를 합친 편의 함수
fun CoroutineScope.produceNumbers(): ReceiveChannel<Int> = produce {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

val numbers = produceNumbers()
repeat(5) {
    println(numbers.receive())
}
numbers.cancel()
```

### Fan-out / Fan-in

```kotlin
// Fan-out: 하나의 Channel을 여러 소비자가 처리
val channel = produce { repeat(100) { send(it) } }

repeat(3) { workerId ->
    launch {
        for (item in channel) {
            println("Worker $workerId 처리: $item")
        }
    }
}

// Fan-in: 여러 생산자가 하나의 Channel에 전송
val channel = Channel<String>()

launch { repeat(10) { channel.send("A-$it"); delay(100) } }
launch { repeat(10) { channel.send("B-$it"); delay(150) } }

repeat(20) {
    println(channel.receive())
}
```

### Channel vs Flow 선택 기준

| 상황 | 추천 |
|------|------|
| 단일 소비자, 순차 처리 | Flow |
| 여러 소비자 (fan-out) | Channel |
| 생산자-소비자 분리 | Channel |
| 데이터 변환 체인 | Flow |
| 핫 스트림 (항상 활성) | Channel / SharedFlow |
| 콜드 스트림 (요청 시 시작) | Flow |

---

## 15. Mutex와 동시성 제어

코루틴도 공유 자원에 동시 접근하면 race condition이 발생한다.

### 문제 상황

```kotlin
var counter = 0

coroutineScope {
    repeat(10_000) {
        launch(Dispatchers.Default) {
            counter++  // 스레드 안전하지 않음!
        }
    }
}

println(counter)  // 10000이 아닐 수 있음!
```

### 해결책 1: Mutex (코루틴용 락)

```kotlin
val mutex = Mutex()
var counter = 0

coroutineScope {
    repeat(10_000) {
        launch(Dispatchers.Default) {
            mutex.withLock {  // 한 번에 하나의 코루틴만 진입
                counter++
            }
        }
    }
}

println(counter)  // 정확히 10000
```

### Mutex vs synchronized

```kotlin
// synchronized: 스레드를 블로킹 → 코루틴에서 비권장
synchronized(lock) {
    // 이 안에서 suspend 함수 호출 불가
    // 스레드를 점유하므로 코루틴의 장점이 사라짐
}

// Mutex: 코루틴을 중단 → 스레드 해방
mutex.withLock {
    // suspend 함수 호출 가능
    delay(100)
    fetchData()
}
```

### 해결책 2: 단일 스레드 한정

```kotlin
// 모든 접근을 단일 스레드로 한정
val counterContext = newSingleThreadContext("counter")
var counter = 0

coroutineScope {
    repeat(10_000) {
        launch {
            withContext(counterContext) {
                counter++
            }
        }
    }
}
```

### 해결책 3: AtomicInteger

```kotlin
val counter = AtomicInteger(0)

coroutineScope {
    repeat(10_000) {
        launch(Dispatchers.Default) {
            counter.incrementAndGet()
        }
    }
}
```

### 해결책 4: Actor 패턴

```kotlin
sealed class CounterMsg
data object Increment : CounterMsg()
data class GetCount(val response: CompletableDeferred<Int>) : CounterMsg()

fun CoroutineScope.counterActor() = actor<CounterMsg> {
    var counter = 0
    for (msg in channel) {
        when (msg) {
            is Increment -> counter++
            is GetCount -> msg.response.complete(counter)
        }
    }
}
```

---

## 16. Spring Boot에서 코루틴 사용하기

### 의존성

```groovy
// build.gradle.kts
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebFlux 필요
}
```

### WebFlux + 코루틴 컨트롤러

Spring WebFlux는 코루틴을 네이티브로 지원한다. suspend 함수를 그대로 사용할 수 있다.

```kotlin
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {
    // suspend 함수 → Spring이 자동으로 코루틴으로 실행
    @GetMapping("/{id}")
    suspend fun getUser(@PathVariable id: Long): UserResponse {
        return userService.findById(id)
    }

    // Flow → Spring이 자동으로 SSE/JSON 스트리밍
    @GetMapping("/stream")
    fun streamUsers(): Flow<UserResponse> {
        return userService.findAllAsFlow()
    }
}
```

### 서비스 레이어

```kotlin
@Service
class UserService(
    private val userRepository: UserCoroutineRepository,
    private val orderClient: OrderClient,
) {
    suspend fun findById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException(id)
        return UserResponse.from(user)
    }

    // 병렬 호출
    suspend fun getUserDashboard(id: Long): DashboardResponse = coroutineScope {
        val user = async { userRepository.findById(id) }
        val orders = async { orderClient.getOrders(id) }
        val notifications = async { notificationClient.getUnread(id) }

        DashboardResponse(
            user = user.await() ?: throw UserNotFoundException(id),
            orders = orders.await(),
            notifications = notifications.await(),
        )
    }

    fun findAllAsFlow(): Flow<UserResponse> {
        return userRepository.findAll()
            .map { UserResponse.from(it) }
    }
}
```

### R2DBC + 코루틴 Repository

```kotlin
// Spring Data R2DBC의 코루틴 지원
interface UserCoroutineRepository : CoroutineCrudRepository<User, Long> {
    suspend fun findByEmail(email: String): User?
    fun findByStatus(status: UserStatus): Flow<User>
}
```

### WebClient + 코루틴

```kotlin
@Component
class OrderClient(
    private val webClient: WebClient,
) {
    // Mono → suspend 변환
    suspend fun getOrder(id: Long): Order {
        return webClient.get()
            .uri("/api/orders/$id")
            .retrieve()
            .bodyToMono<Order>()
            .awaitSingle()  // Mono를 suspend로 변환
    }

    // Flux → Flow 변환
    fun getOrders(userId: Long): Flow<Order> {
        return webClient.get()
            .uri("/api/orders?userId=$userId")
            .retrieve()
            .bodyToFlux<Order>()
            .asFlow()  // Flux를 Flow로 변환
    }
}
```

### Spring MVC에서 코루틴 사용 (WebFlux 없이)

Spring MVC(블로킹)에서도 코루틴을 사용할 수 있지만, 방식이 다르다.

```kotlin
@RestController
class UserController(
    private val userService: UserService,
) {
    // 방법 1: runBlocking (비권장 - 스레드 블로킹)
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse = runBlocking {
        userService.findById(id)
    }

    // 방법 2: 내부 로직에서만 코루틴 활용
    @GetMapping("/dashboard/{id}")
    fun getDashboard(@PathVariable id: Long): DashboardResponse = runBlocking {
        coroutineScope {
            val user = async(Dispatchers.IO) { userService.findById(id) }
            val orders = async(Dispatchers.IO) { orderService.findByUser(id) }
            DashboardResponse(user.await(), orders.await())
        }
    }
}
```

> **참고:** Spring MVC + 코루틴은 스레드를 블로킹하므로 코루틴의 논블로킹 장점을 완전히 활용할 수 없다.
> 논블로킹이 필요하면 WebFlux를 사용하자.

### Kafka Consumer에서 코루틴

```kotlin
@Component
class EventConsumer(
    private val eventService: EventService,
) {
    // 코루틴 스코프 관리
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @KafkaListener(topics = ["events"])
    fun consume(payload: String) {
        scope.launch {
            eventService.process(payload)
        }
    }

    @PreDestroy
    fun destroy() {
        scope.cancel()
    }
}
```

---

## 17. 테스트

### 의존성

```groovy
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
```

### runTest

```kotlin
@Test
fun `suspend 함수 테스트`() = runTest {
    // runTest는 가상 시간을 사용 → delay가 즉시 실행됨
    val result = fetchUser(1L)
    assertThat(result.name).isEqualTo("홍길동")
}
```

### 가상 시간 (Virtual Time)

```kotlin
@Test
fun `delay가 포함된 로직 테스트`() = runTest {
    var counter = 0

    launch {
        delay(1000)  // 실제로 1초 기다리지 않음!
        counter++
        delay(1000)
        counter++
    }

    advanceTimeBy(1000)
    runCurrent()
    assertThat(counter).isEqualTo(1)

    advanceTimeBy(1000)
    runCurrent()
    assertThat(counter).isEqualTo(2)

    // 또는 모든 시간을 한 번에 진행
    advanceUntilIdle()
}
```

### TestDispatcher

```kotlin
@Test
fun `Dispatcher를 주입하여 테스트`() = runTest {
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val service = MyService(testDispatcher)

    service.doWork()
    advanceUntilIdle()  // 모든 코루틴 완료

    assertThat(service.result).isNotNull()
}

// 프로덕션 코드에서 Dispatcher를 주입 가능하게 설계
class MyService(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    var result: String? = null

    suspend fun doWork() {
        withContext(dispatcher) {
            delay(1000)
            result = "완료"
        }
    }
}
```

### Flow 테스트

```kotlin
@Test
fun `Flow 방출 값 테스트`() = runTest {
    val flow = flowOf(1, 2, 3)
        .map { it * 2 }

    // 방법 1: toList
    val result = flow.toList()
    assertThat(result).containsExactly(2, 4, 6)

    // 방법 2: Turbine 라이브러리 (더 세밀한 테스트)
    flow.test {
        assertThat(awaitItem()).isEqualTo(2)
        assertThat(awaitItem()).isEqualTo(4)
        assertThat(awaitItem()).isEqualTo(6)
        awaitComplete()
    }
}

@Test
fun `StateFlow 테스트`() = runTest {
    val viewModel = MyViewModel()

    viewModel.uiState.test {
        assertThat(awaitItem()).isEqualTo(UiState.Loading)  // 초기값

        viewModel.loadData()

        assertThat(awaitItem()).isEqualTo(UiState.Success("데이터"))
    }
}
```

### Turbine 의존성 (Flow 테스트)

```groovy
testImplementation("app.cash.turbine:turbine:1.2.0")
```

---

## 18. 코루틴 vs 다른 비동기 모델 비교

### 전체 비교

| 특성 | Thread | CompletableFuture | Reactor/WebFlux | Coroutine |
|------|--------|-------------------|-----------------|-----------|
| 메모리 | ~1MB/스레드 | 낮음 | 낮음 | 매우 낮음 |
| 가독성 | 좋음 | 보통 | 나쁨 | 좋음 |
| 학습 곡선 | 낮음 | 중간 | 높음 | 중간 |
| 디버깅 | 좋음 | 나쁨 | 매우 나쁨 | 보통 |
| 취소 지원 | 수동 | 제한적 | 있음 | 내장 |
| 에러 처리 | try-catch | exceptionally | onError | try-catch |
| 구조화된 동시성 | 없음 | 없음 | 없음 | 내장 |
| 스레드 블로킹 | O | O (join 시) | X | X |

### Java Virtual Thread와의 비교

Java 21의 Virtual Thread도 경량 스레드이다. 차이점:

```
Virtual Thread:
  - OS 스레드와 1:1이 아닌, 플랫폼 스레드에 매핑
  - 기존 Java 코드 변경 없이 적용 가능 (Thread.ofVirtual())
  - 블로킹 I/O를 만나면 자동으로 플랫폼 스레드 양보
  - 구조화된 동시성 없음 (StructuredTaskScope는 preview)
  - 순수 Java, 추가 라이브러리 불필요

Coroutine:
  - Kotlin 전용
  - suspend 키워드로 중단점 명시
  - 구조화된 동시성 내장 (부모-자식 관계)
  - Flow, Channel 등 풍부한 동시성 도구
  - 취소가 체계적 (Job 트리)
  - 가상 시간 테스트 지원
```

### 언제 무엇을 선택할까?

```
Java + Spring MVC → Virtual Thread (가장 간단)
Java + Spring WebFlux → Reactor (이미 사용 중이면)
Kotlin + Spring WebFlux → Coroutine (가장 자연스러움)
Kotlin + Android → Coroutine (사실상 표준)
Kotlin + Spring MVC → Virtual Thread 또는 Coroutine (상황에 따라)
```

---

## 19. 실전 패턴 모음

### 패턴 1: 타임아웃 + 폴백

```kotlin
suspend fun fetchWithFallback(id: Long): Data {
    return withTimeoutOrNull(3000) {
        primaryApi.fetch(id)
    } ?: run {
        log.warn("Primary API 타임아웃, fallback 사용")
        fallbackApi.fetch(id)
    }
}
```

### 패턴 2: 여러 소스 중 가장 빠른 응답

```kotlin
suspend fun fetchFastest(id: Long): Data = coroutineScope {
    select {
        async { apiA.fetch(id) }.onAwait { it }
        async { apiB.fetch(id) }.onAwait { it }
        async { apiC.fetch(id) }.onAwait { it }
    }
    // 가장 빨리 응답한 값 반환, 나머지는 자동 취소
}
```

### 패턴 3: 주기적 작업

```kotlin
fun CoroutineScope.launchPeriodicTask(
    interval: Long,
    action: suspend () -> Unit,
): Job = launch {
    while (isActive) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("주기 작업 실패", e)
        }
        delay(interval)
    }
}

// 사용
val job = scope.launchPeriodicTask(60_000) {
    syncData()
}
```

### 패턴 4: 배치 처리 (chunked + 병렬)

```kotlin
suspend fun processAll(items: List<Item>) {
    items.chunked(100).forEach { batch ->
        coroutineScope {
            batch.map { item ->
                async(Dispatchers.IO) {
                    processItem(item)
                }
            }.awaitAll()
        }
        log.info("배치 처리 완료: ${batch.size}건")
    }
}
```

### 패턴 5: Debounce (검색어 입력)

```kotlin
fun searchQueryFlow(queryFlow: Flow<String>): Flow<List<SearchResult>> {
    return queryFlow
        .debounce(300)                    // 300ms 동안 입력 없으면 실행
        .filter { it.length >= 2 }         // 2글자 이상
        .distinctUntilChanged()            // 같은 쿼리 무시
        .flatMapLatest { query ->          // 이전 검색 취소, 최신만 유지
            flow {
                emit(searchApi.search(query))
            }
        }
}
```

### 패턴 6: Circuit Breaker (간단 버전)

```kotlin
class SimpleCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Long = 30_000,
) {
    private val failures = AtomicInteger(0)
    private var lastFailureTime = 0L
    private var state = State.CLOSED

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeout) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            else -> {}
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private fun onSuccess() {
        failures.set(0)
        state = State.CLOSED
    }

    private fun onFailure() {
        lastFailureTime = System.currentTimeMillis()
        if (failures.incrementAndGet() >= failureThreshold) {
            state = State.OPEN
        }
    }
}
```

---

## 20. 흔한 실수와 주의점

### 실수 1: GlobalScope 사용

```kotlin
// BAD: 생명주기 관리 불가, 메모리 누수 위험
fun handleRequest() {
    GlobalScope.launch {
        // 누가 이 코루틴을 관리하지?
        // 앱이 종료되어도 계속 실행될 수 있음
    }
}

// GOOD: 적절한 스코프 사용
class MyService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handleRequest() {
        scope.launch { /* ... */ }
    }

    fun close() {
        scope.cancel()  // 모든 코루틴 정리
    }
}
```

### 실수 2: suspend 함수 안에서 블로킹 호출

```kotlin
// BAD: JDBC는 블로킹 I/O → 코루틴 스레드를 점유
suspend fun getUser(id: Long): User {
    return jdbcTemplate.queryForObject(...)  // 블로킹!
}

// GOOD: withContext(Dispatchers.IO)로 감싸기
suspend fun getUser(id: Long): User = withContext(Dispatchers.IO) {
    jdbcTemplate.queryForObject(...)
}

// BEST: R2DBC 같은 논블로킹 드라이버 사용
suspend fun getUser(id: Long): User {
    return r2dbcRepository.findById(id) ?: throw NotFoundException()
}
```

### 실수 3: CancellationException 삼키기

```kotlin
// BAD
suspend fun riskyOperation() {
    try {
        someApiCall()
    } catch (e: Exception) {  // CancellationException도 잡힘!
        log.error("에러", e)
        // 코루틴 취소가 무시됨 → 좀비 코루틴
    }
}

// GOOD
suspend fun riskyOperation() {
    try {
        someApiCall()
    } catch (e: CancellationException) {
        throw e  // 취소는 항상 재throw
    } catch (e: Exception) {
        log.error("에러", e)
    }
}
```

### 실수 4: async 결과를 무시

```kotlin
// BAD: async의 예외가 무시될 수 있음
coroutineScope {
    async { riskyOperation() }  // await 안 함 → 예외 누락 가능
}

// GOOD: 결과가 필요 없으면 launch 사용
coroutineScope {
    launch { riskyOperation() }
}
```

### 실수 5: withContext를 너무 잘게 쪼갬

```kotlin
// BAD: 매번 컨텍스트 전환 오버헤드
suspend fun processItems(items: List<Item>) {
    items.forEach { item ->
        withContext(Dispatchers.IO) {  // N번 전환!
            save(item)
        }
    }
}

// GOOD: 한 번에 감싸기
suspend fun processItems(items: List<Item>) = withContext(Dispatchers.IO) {
    items.forEach { item ->
        save(item)
    }
}
```

### 실수 6: 코루틴 안에서 Thread.sleep 사용

```kotlin
// BAD: 스레드 자체를 블로킹
launch {
    Thread.sleep(1000)  // 스레드 점유!
}

// GOOD: delay 사용
launch {
    delay(1000)  // 코루틴만 중단, 스레드 해방
}
```

### 실수 7: mutable 공유 상태를 보호하지 않음

```kotlin
// BAD: race condition
var count = 0
coroutineScope {
    repeat(10_000) {
        launch(Dispatchers.Default) {
            count++  // 안전하지 않음!
        }
    }
}

// GOOD: Mutex, AtomicInteger, 또는 단일 스레드 한정
val mutex = Mutex()
var count = 0
coroutineScope {
    repeat(10_000) {
        launch(Dispatchers.Default) {
            mutex.withLock { count++ }
        }
    }
}
```

### 실수 8: Flow를 collect하지 않음

```kotlin
// BAD: Flow는 cold stream → collect 안 하면 실행 안 됨!
fun refresh() {
    fetchUsers()  // 아무 일도 안 일어남
        .map { it.name }
}

// GOOD
suspend fun refresh() {
    fetchUsers()
        .map { it.name }
        .collect { println(it) }  // 이때 실행됨
}
```

---

## 21. 디버깅

### 코루틴 이름 지정

```kotlin
launch(CoroutineName("user-fetch")) {
    // 로그에 코루틴 이름 출력됨
    log.info("작업 시작")
}
```

### JVM 옵션으로 디버그 모드 활성화

```
-Dkotlinx.coroutines.debug
```

이 옵션을 켜면 `Thread.currentThread().name`에 코루틴 정보가 포함된다:
```
main @coroutine#1
DefaultDispatcher-worker-1 @user-fetch#2
```

### 스택 트레이스 개선

```kotlin
// 코루틴 스택 트레이스 추적 활성화
// application.yml 또는 JVM 옵션
-Dkotlinx.coroutines.stacktrace.recovery=true
```

### 로깅 패턴

```kotlin
private val log = LoggerFactory.getLogger(javaClass)

suspend fun process(id: Long) {
    log.info("[${coroutineContext[CoroutineName]?.name}] 처리 시작: $id")
    // ...
}
```

---

## 22. 성능 특성과 언제 써야 하는가

### 코루틴이 효과적인 경우

| 상황 | 이유 |
|------|------|
| 대량의 동시 I/O | 스레드 수천 개 대신 코루틴 수천 개 |
| 여러 API 병렬 호출 | async/await로 간결하게 |
| 실시간 데이터 스트리밍 | Flow로 백프레셔 처리 |
| 타임아웃이 필요한 작업 | withTimeout 내장 |
| 취소가 필요한 작업 | 구조화된 동시성으로 자동 취소 |
| Android UI | 메인 스레드 블로킹 방지 |

### 코루틴이 불필요하거나 부적절한 경우

| 상황 | 이유 |
|------|------|
| 단순 CRUD (동시성 없음) | 오버엔지니어링 |
| CPU 집약적 계산만 있는 경우 | 코루틴보다 parallelStream이 간단 |
| 기존 Java 라이브러리가 블로킹만 지원 | withContext(IO)로 감싸야 하므로 이점 적음 |
| 팀 전체가 Java만 사용 | 학습 비용 대비 이점 평가 필요 |

### 성능 수치 (참고용)

```
코루틴 생성: ~수 마이크로초
스레드 생성: ~수백 마이크로초 ~ 수 밀리초

코루틴 컨텍스트 스위칭: ~수십 나노초 (유저 레벨)
스레드 컨텍스트 스위칭: ~수 마이크로초 (커널 레벨)

코루틴 메모리: ~수백 바이트 ~ 수 KB
스레드 메모리: ~512KB ~ 1MB (스택)
```

### 정리: 코루틴 도입 판단 기준

```
다음 중 2개 이상 해당하면 코루틴을 고려하라:

✅ Kotlin을 메인 언어로 사용
✅ 동시에 처리할 I/O 작업이 많다 (100+)
✅ 여러 외부 API를 병렬로 호출해야 한다
✅ 실시간 데이터 처리가 필요하다 (WebSocket, SSE)
✅ 작업 취소와 타임아웃이 중요하다
✅ Spring WebFlux를 사용하거나 도입 예정이다
```

---

## 용어 정리

| 용어 | 설명 |
|------|------|
| **suspend** | 일시 중단 가능한 함수 표시 |
| **Continuation** | 중단된 코루틴의 "나머지 계산"을 나타내는 객체 |
| **Dispatcher** | 코루틴이 실행될 스레드(풀)를 결정 |
| **Job** | 코루틴의 생명주기를 나타내는 핸들 |
| **Deferred** | 결과를 가진 Job (async의 반환 타입) |
| **Scope** | 코루틴의 생명주기 범위 |
| **Flow** | 비동기 데이터 스트림 (cold) |
| **Channel** | 코루틴 간 통신 파이프 (hot) |
| **StateFlow** | 항상 최신 값을 가진 Flow |
| **SharedFlow** | 여러 구독자에게 브로드캐스트하는 Flow |
| **Structured Concurrency** | 부모-자식 관계로 코루틴 생명주기 관리 |
| **Cooperative Cancellation** | 코루틴이 스스로 취소 여부를 확인하는 방식 |
