# Kotlin Coroutines 심화 정리

## 1. Coroutine이란?

**중단(suspend)과 재개(resume)가 가능한 경량 스레드.**
OS 스레드를 블로킹하지 않고, JVM 레벨에서 코루틴을 스케줄링한다.

```
// 전통적인 블로킹 방식
Thread 1: [작업 A──────────────][작업 B──────]
                  │ I/O 대기(블로킹) │

// 코루틴 방식
Thread 1: [작업 A 시작][작업 B 시작][작업 A 재개][작업 B 재개]
                  ↑ suspend         ↑ resume
                  → 스레드 양보, 다른 코루틴 실행
```

### Thread vs Coroutine

| 항목 | Thread | Coroutine |
|------|--------|-----------|
| 생성 비용 | 큼 (~1MB 스택) | 매우 작음 (~수 KB) |
| 최대 수 | OS 제한 (수천) | 수십만 가능 |
| 블로킹 I/O | OS 스레드 점유 | `suspend`로 스레드 해제 |
| 컨텍스트 스위칭 | OS 커널 개입 | JVM 레벨 (훨씬 저렴) |
| 취소 | 강제 중단 어려움 | 구조적 취소 지원 |

---

## 2. suspend 함수

`suspend` 키워드가 붙은 함수는 **중단될 수 있는 함수**.
다른 `suspend` 함수 또는 코루틴 빌더 내에서만 호출 가능.

```kotlin
// suspend 함수 선언
suspend fun fetchUser(id: Long): User {
    delay(1000) // 1초 동안 스레드를 블로킹하지 않고 중단
    return userRepository.findById(id) // 실제로는 suspend DB 호출
}

// 컴파일 후 내부적으로 Continuation 파라미터가 추가됨 (CPS 변환)
// fun fetchUser(id: Long, continuation: Continuation<User>): Any
```

### delay vs Thread.sleep

```kotlin
// Thread.sleep: OS 스레드를 블로킹
Thread.sleep(1000) // 1초간 스레드 점유

// delay: 코루틴만 중단, 스레드는 해제
delay(1000) // 스레드는 다른 코루틴 실행 가능
```

---

## 3. CoroutineScope & 구조적 동시성

모든 코루틴은 **CoroutineScope 안에서 실행**된다.
부모 스코프가 취소되면 자식 코루틴도 모두 취소된다 → **구조적 동시성(Structured Concurrency)**.

```kotlin
// CoroutineScope 생성
val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// 코루틴 빌더
scope.launch {         // 결과 없음 (fire-and-forget)
    doSomething()
}

val deferred = scope.async { // 결과 있음 (Deferred<T>)
    fetchData()
}
val result = deferred.await() // 결과 대기
```

### 구조적 동시성 장점

```kotlin
coroutineScope {        // 이 블록 내 모든 자식 코루틴이 완료되어야 반환
    launch { task1() }
    launch { task2() }
    launch { task3() }
} // task1, task2, task3 모두 완료 후 진행
  // 하나라도 실패하면 나머지도 취소
```

---

## 4. Job & SupervisorJob

### Job

코루틴의 생명주기를 관리하는 객체.

```
Job 상태 전이:
New → Active → Completing → Completed
                    ↓
               Cancelling → Cancelled
```

```kotlin
val job = scope.launch { longRunningTask() }

job.isActive    // 실행 중인지
job.cancel()    // 취소 요청
job.join()      // 완료까지 대기 (suspend)
```

### Job vs SupervisorJob

```kotlin
// Job: 자식 하나가 실패하면 부모와 다른 자식도 모두 취소
val scope = CoroutineScope(Job())
scope.launch { throw Exception() } // → 전체 스코프 취소
scope.launch { doOther() }         // → 이것도 취소됨

// SupervisorJob: 자식 하나의 실패가 다른 자식에 영향 없음
val scope = CoroutineScope(SupervisorJob())
scope.launch { throw Exception() } // → 이 코루틴만 실패
scope.launch { doOther() }         // → 정상 실행 유지
```

> **실무 포인트**: 서버 애플리케이션의 CoroutineScope는 일반적으로 `SupervisorJob`을 사용한다. 개별 요청 처리 실패가 전체 서비스에 영향을 주면 안 되기 때문. (Booster의 `ChatService`가 이 패턴)

---

## 5. Dispatcher (스케줄러)

코루틴이 **어떤 스레드에서 실행될지** 결정.

| Dispatcher | 스레드풀 | 용도 |
|-----------|---------|------|
| `Dispatchers.Main` | 메인(UI) 스레드 1개 | Android UI 업데이트 |
| `Dispatchers.IO` | 최대 64개 (또는 CPU 수) | 파일 I/O, 네트워크, DB |
| `Dispatchers.Default` | CPU 코어 수 | CPU 집약 연산 (정렬, 파싱) |
| `Dispatchers.Unconfined` | 제한 없음 | 테스트, 특수 케이스 |

```kotlin
// Dispatcher 전환
withContext(Dispatchers.IO) {
    // IO 스레드에서 실행
    val data = readFile()

    withContext(Dispatchers.Default) {
        // CPU 연산은 Default로 전환
        processData(data)
    }
}
```

---

## 6. Flow

**비동기 데이터 스트림**. 여러 값을 순차적으로 emit하는 cold stream.

```kotlin
// Flow 생성 (cold: collect 할 때만 실행)
fun getNumbers(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        emit(i) // 값 방출
    }
}

// Flow 수집
getNumbers()
    .filter { it % 2 == 1 }
    .map { it * 2 }
    .collect { value ->
        println(value) // 2, 6
    }
```

### Flow 연산자

```kotlin
flow
    .map { transform(it) }           // 변환
    .filter { condition(it) }        // 필터링
    .take(5)                         // 5개만
    .debounce(300)                   // 300ms 내 마지막 값만 (검색창 입력 등)
    .conflate()                      // 처리 못 한 값 버리고 최신값만
    .buffer(64)                      // 버퍼링
    .catch { e -> emit(default) }    // 예외 처리
    .onEach { log(it) }              // 사이드 이펙트
    .flowOn(Dispatchers.IO)          // 업스트림 Dispatcher 전환
```

### Cold Flow vs Hot Flow

| 항목 | Cold Flow | Hot Flow (SharedFlow/StateFlow) |
|------|-----------|--------------------------------|
| 실행 시점 | `collect` 시 시작 | 독립적으로 실행 |
| 구독자 | 각자 독립 스트림 | 여러 구독자가 같은 스트림 공유 |
| 예시 | DB 쿼리, HTTP 요청 | WebSocket 메시지, UI 상태 |

---

## 7. SharedFlow & StateFlow

### SharedFlow

**여러 구독자에게 이벤트를 브로드캐스트**하는 hot stream.

```kotlin
// 생성
val sharedFlow = MutableSharedFlow<ChatMessage>(
    replay = 0,           // 새 구독자에게 이전 값 replay 개수
    extraBufferCapacity = 64 // emit() 버퍼 크기
)

// emit (suspend, 버퍼 가득 차면 대기)
sharedFlow.emit(message)

// tryEmit (non-suspend, 버퍼 가득 차면 false 반환)
val success = sharedFlow.tryEmit(message)

// 구독
sharedFlow.collect { message -> handle(message) }
```

> **Booster 연결**: `ChatService`의 `localConnections`가 `MutableSharedFlow<ChatMessage>` 맵으로 사용자별 메시지 스트림 관리.

### StateFlow

**현재 상태를 보관**하는 hot stream. 항상 최신 값 1개를 유지.

```kotlin
val stateFlow = MutableStateFlow<Int>(0) // 초기값 필수

stateFlow.value = 42 // 값 업데이트

stateFlow.collect { state -> render(state) }
// 새 구독자는 즉시 현재 값(42)을 받음

// SharedFlow와 차이
// StateFlow: replay=1, distinctUntilChanged (같은 값이면 emit 안 함)
// SharedFlow: 더 유연하게 설정 가능
```

---

## 8. Coroutine 취소와 예외 처리

### 취소 (Cancellation)

```kotlin
val job = scope.launch {
    repeat(1000) { i ->
        delay(100)           // suspend 지점에서 취소 체크됨
        println("step $i")
    }
}

delay(500)
job.cancel() // 취소 요청 → 다음 suspend 지점에서 CancellationException 발생

// CPU 집약 코드에서는 직접 취소 체크
suspend fun heavyComputation() {
    for (i in 1..1000000) {
        ensureActive() // 취소됐으면 CancellationException 던짐
        compute(i)
    }
}
```

### 예외 처리

```kotlin
// CoroutineExceptionHandler: 처리되지 않은 예외 처리
val handler = CoroutineExceptionHandler { _, exception ->
    log.error("Coroutine failed: {}", exception.message)
}

val scope = CoroutineScope(SupervisorJob() + handler)

// launch: 예외 즉시 전파
scope.launch {
    throw RuntimeException("launch error")
} // → handler로 전달

// async: await() 호출 시 예외 전파
val deferred = scope.async {
    throw RuntimeException("async error")
}
try {
    deferred.await() // 여기서 예외 발생
} catch (e: RuntimeException) {
    // 처리
}
```

### CancellationException 주의

```kotlin
// CancellationException은 정상 취소 신호 → 재던져야 함
try {
    delay(1000)
} catch (e: CancellationException) {
    throw e  // ✅ 반드시 재던져야 취소가 전파됨
} catch (e: Exception) {
    handleError(e)
}

// 또는 NonCancellable로 취소 불가 블록 보호
withContext(NonCancellable) {
    // 취소돼도 반드시 실행해야 하는 정리 로직 (DB close 등)
    cleanup()
}
```

---

## 9. Spring WebFlux + Coroutines

Spring WebFlux는 Kotlin Coroutines와 자연스럽게 통합된다.

```kotlin
@RestController
class UserController(private val userService: UserService) {

    // Mono<T> 대신 suspend fun 사용 가능
    @GetMapping("/users/{id}")
    suspend fun getUser(@PathVariable id: Long): User {
        return userService.findById(id) // suspend 함수 직접 호출
    }

    // Flux<T> 대신 Flow<T> 사용 가능
    @GetMapping("/users", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getUsers(): Flow<User> {
        return userService.findAll() // Flow 반환
    }
}
```

### ReactiveRedisTemplate + Coroutines

```kotlin
// Reactor 타입을 Coroutine으로 변환
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.awaitFirst

// Mono<T> → suspend
val result = redisTemplate.opsForValue().get(key).awaitSingleOrNull()

// Flux<T> → Flow<T>
val flow = redisTemplate.opsForList().range(key, 0, -1).asFlow()
```

---

## 10. 면접 Q&A

**Q. 코루틴과 스레드의 핵심 차이는?**
> 스레드는 OS가 관리하는 실행 단위로 블로킹 시 OS 스레드를 점유한다. 코루틴은 JVM이 관리하는 경량 실행 단위로, `suspend` 시 스레드를 해제하여 다른 코루틴이 사용할 수 있게 한다. 수천 개의 스레드는 메모리와 컨텍스트 스위칭 비용이 크지만, 수십만 개의 코루틴은 가능하다.

**Q. `launch`와 `async`의 차이는?**
> `launch`는 결과가 없는 코루틴을 시작하고 `Job`을 반환한다. `async`는 결과가 있는 코루틴을 시작하고 `Deferred<T>`를 반환하며, `await()`로 결과를 받는다. `async`의 예외는 `await()` 호출 시 전파된다.

**Q. `coroutineScope`와 `supervisorScope`의 차이는?**
> `coroutineScope`는 자식 하나가 실패하면 나머지 자식도 모두 취소된다. `supervisorScope`는 자식의 실패가 다른 자식에 영향을 주지 않는다. 독립적인 작업들을 병렬로 실행할 때 `supervisorScope` 사용.

**Q. Flow의 `flowOn`은 어떻게 동작하나?**
> `flowOn`은 **업스트림(flowOn 위쪽)** 코드의 실행 Dispatcher를 변경한다. 다운스트림(collect 쪽)은 영향을 받지 않는다. `withContext`를 Flow 안에서 쓰는 것과 달리, `flowOn`은 내부적으로 채널을 만들어 Dispatcher를 분리한다.

**Q. `SharedFlow`의 `replay`를 언제 설정하나?**
> 늦게 구독한 수신자에게 과거 이벤트를 전달해야 할 때 설정한다. 예: 앱 시작 후 구독했는데 이전 상태를 알아야 할 때. 채팅처럼 실시간 이벤트만 필요하면 `replay=0`으로 설정 (Booster ChatService 패턴).

**Q. `StateFlow`와 `SharedFlow`를 언제 각각 쓰나?**
> `StateFlow`는 "현재 상태"를 표현할 때 사용한다 (UI 상태, 설정값 등). 항상 최신값 1개를 유지하고 같은 값은 emit하지 않는다. `SharedFlow`는 "이벤트"를 표현할 때 사용한다 (클릭 이벤트, 메시지 수신 등). 값 유지 없이 발생한 이벤트를 구독자에게 브로드캐스트.
