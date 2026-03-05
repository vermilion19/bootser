# Java vs Kotlin: 코틀린스럽게 코딩하기

Java 개발자가 Kotlin으로 전환할 때 흔히 빠지는 "Java를 Kotlin 문법으로 번역만 한" 코드에서 벗어나,
진짜 Kotlin 다운 코드를 작성하는 방법을 정리한다.

---

## 목차

1. [Null 처리](#1-null-처리)
2. [데이터 클래스](#2-데이터-클래스)
3. [컬렉션 처리](#3-컬렉션-처리)
4. [스코프 함수](#4-스코프-함수)
5. [분기 처리](#5-분기-처리)
6. [문자열 처리](#6-문자열-처리)
7. [기본값과 named argument](#7-기본값과-named-argument)
8. [확장 함수](#8-확장-함수)
9. [sealed class로 도메인 모델링](#9-sealed-class로-도메인-모델링)
10. [코루틴과 비동기 처리](#10-코루틴과-비동기-처리)
11. [DSL 스타일 빌더](#11-dsl-스타일-빌더)
12. [프로퍼티와 위임](#12-프로퍼티와-위임)
13. [구조 분해](#13-구조-분해)
14. [싱글톤과 companion object](#14-싱글톤과-companion-object)
15. [Spring Boot에서의 Kotlin](#15-spring-boot에서의-kotlin)

---

## 1. Null 처리

Java에서는 null 체크가 반복적이고 장황하다. Kotlin은 타입 시스템 레벨에서 null safety를 지원한다.

### Java

```java
public String getUserCity(User user) {
    if (user != null) {
        Address address = user.getAddress();
        if (address != null) {
            return address.getCity();
        }
    }
    return "Unknown";
}

// Optional 사용
public String getUserCity(User user) {
    return Optional.ofNullable(user)
            .map(User::getAddress)
            .map(Address::getCity)
            .orElse("Unknown");
}
```

### Kotlin - 안 좋은 예 (Java 번역체)

```kotlin
fun getUserCity(user: User?): String {
    if (user != null) {
        val address = user.address
        if (address != null) {
            return address.city
        }
    }
    return "Unknown"
}
```

### Kotlin - 좋은 예

```kotlin
// safe call + elvis
fun getUserCity(user: User?): String =
    user?.address?.city ?: "Unknown"
```

### 핵심 연산자 정리

| 연산자 | 의미 | 예시 |
|--------|------|------|
| `?.` | null이면 건너뛰기 | `user?.name` |
| `?:` | null이면 대체값 (Elvis) | `name ?: "익명"` |
| `!!` | null이 아님을 단언 (웬만하면 쓰지 말 것) | `name!!` |
| `?.let { }` | null이 아닐 때만 실행 | `user?.let { save(it) }` |

### 실전 패턴

```kotlin
// null이 아닐 때만 로직 실행
fun processOrder(order: Order?) {
    order?.let { validOrder ->
        println("주문 처리: ${validOrder.id}")
        repository.save(validOrder)
    }
}

// 여러 값이 모두 non-null일 때만 실행
fun createFullName(first: String?, last: String?): String? {
    // 하나라도 null이면 null 반환
    first ?: return null
    last ?: return null
    return "$first $last"
}

// requireNotNull / checkNotNull - 사전 조건 검증
fun getUser(id: Long): User {
    val user = repository.findById(id)
    return requireNotNull(user) { "User not found: $id" }
}
```

---

## 2. 데이터 클래스

Java에서는 단순 데이터 운반 객체(DTO)를 만들 때도 보일러플레이트가 많다.

### Java

```java
// record 이전
public class MemberEvent {
    private final String eventId;
    private final Long memberId;
    private final String nickname;

    public MemberEvent(String eventId, Long memberId, String nickname) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.nickname = nickname;
    }

    // getter, equals, hashCode, toString 생략...
}

// Java 16+ record
public record MemberEvent(String eventId, Long memberId, String nickname) {}
```

### Kotlin

```kotlin
data class MemberEvent(
    val eventId: String,
    val memberId: Long,
    val nickname: String,
)
```

`data class`가 자동으로 제공하는 것:
- `equals()` / `hashCode()`
- `toString()` → `MemberEvent(eventId=abc, memberId=1, nickname=홍길동)`
- `copy()` → 일부 필드만 바꿔 복사
- 구조 분해 (destructuring)

### copy() 활용 - 불변 객체 상태 변경

```kotlin
data class User(val name: String, val age: Int, val active: Boolean)

val user = User("홍길동", 25, true)
val deactivated = user.copy(active = false) // name, age는 유지
```

### Java record와의 차이점

| 기능 | Java record | Kotlin data class |
|------|-------------|-------------------|
| `copy()` | 없음 | 있음 |
| 상속 | 불가 | 불가 (일반 class로 전환 필요) |
| 구조 분해 | 없음 | `val (id, name) = user` |
| 기본값 | 없음 | `val age: Int = 0` |
| 가변 필드 | 없음 (final) | `var` 가능 (비권장) |

---

## 3. 컬렉션 처리

Kotlin의 컬렉션 API는 Java Stream보다 간결하고 직관적이다.

### Java

```java
// 필터 + 변환 + 수집
List<String> activeNames = users.stream()
        .filter(u -> u.isActive())
        .map(User::getName)
        .sorted()
        .collect(Collectors.toList());

// 그룹핑
Map<String, List<User>> byCity = users.stream()
        .collect(Collectors.groupingBy(User::getCity));

// 첫 번째 매칭
Optional<User> admin = users.stream()
        .filter(u -> u.getRole() == Role.ADMIN)
        .findFirst();

// null 제거
List<String> nonNullNames = users.stream()
        .map(User::getNickname)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
```

### Kotlin

```kotlin
// 필터 + 변환 + 정렬 (collect 불필요)
val activeNames = users
    .filter { it.isActive }
    .map { it.name }
    .sorted()

// 그룹핑
val byCity = users.groupBy { it.city }

// 첫 번째 매칭 (Optional이 아니라 nullable)
val admin = users.firstOrNull { it.role == Role.ADMIN }

// null 제거 (filterNotNull 또는 mapNotNull)
val nonNullNames = users.mapNotNull { it.nickname }
```

### 자주 쓰는 컬렉션 함수

```kotlin
val numbers = listOf(1, 2, 3, 4, 5)

// any / none / all
numbers.any { it > 3 }       // true
numbers.none { it > 10 }     // true
numbers.all { it > 0 }       // true

// associate - Map 생성
val idToUser = users.associateBy { it.id }           // Map<Long, User>
val nameToAge = users.associate { it.name to it.age } // Map<String, Int>

// partition - 조건으로 2개 리스트 분리
val (adults, minors) = users.partition { it.age >= 18 }

// flatMap - 중첩 리스트 평탄화
val allTags = posts.flatMap { it.tags }

// sumOf
val totalPrice = orders.sumOf { it.price }

// 빈 컬렉션 생성
val empty = emptyList<String>()
val mutable = mutableListOf<String>()

// 컬렉션 빌더
val list = buildList {
    add("a")
    addAll(otherList)
    if (condition) add("b")
}
```

### 주의: Sequence (대용량 처리)

```kotlin
// 일반 체이닝: 매 단계마다 중간 리스트 생성 (소량에 적합)
val result = users.filter { it.isActive }.map { it.name }

// Sequence: 요소 하나씩 파이프라인 통과 (대용량에 적합, Java Stream과 유사)
val result = users.asSequence()
    .filter { it.isActive }
    .map { it.name }
    .toList()
```

---

## 4. 스코프 함수

Kotlin만의 강력한 기능. 객체의 컨텍스트 안에서 코드 블록을 실행한다.

### 5가지 스코프 함수 비교

| 함수 | 참조 방식 | 반환값 | 주 용도 |
|------|----------|--------|---------|
| `let` | `it` | 람다 결과 | null 체크 후 변환 |
| `run` | `this` | 람다 결과 | 객체 설정 + 결과 계산 |
| `with` | `this` | 람다 결과 | 이미 있는 객체로 작업 |
| `apply` | `this` | 객체 자신 | 객체 초기화/설정 |
| `also` | `it` | 객체 자신 | 부가 작업 (로깅 등) |

### Java

```java
// 객체 생성 + 설정
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

// null 체크 후 변환
String cityUpper = null;
if (user.getAddress() != null) {
    cityUpper = user.getAddress().getCity().toUpperCase();
}

// 로깅 끼워넣기
User savedUser = userRepository.save(user);
log.info("저장 완료: {}", savedUser.getId());
return savedUser;
```

### Kotlin

```kotlin
// apply - 객체 초기화
val client = HttpClient.newBuilder().apply {
    connectTimeout(Duration.ofSeconds(10))
    followRedirects(HttpClient.Redirect.NORMAL)
}.build()

// let - null 체크 후 변환
val cityUpper = user.address?.let { it.city.uppercase() }

// also - 부가 작업 (체이닝 흐름 유지)
val savedUser = userRepository.save(user).also {
    log.info("저장 완료: ${it.id}")
}

// run - 객체 컨텍스트 + 결과 반환
val result = service.run {
    val users = findAll()
    val active = users.count { it.isActive }
    "총 ${users.size}명 중 ${active}명 활성"
}

// with - 이미 있는 객체에 여러 작업
with(config) {
    host = "localhost"
    port = 8080
    timeout = 30
}
```

### 실전에서의 선택 기준

```kotlin
// let: nullable 체크 + 변환
nickname?.let { repository.findByNickname(it) }

// apply: Builder 대체, 객체 설정 후 자기 자신 반환
val entity = MemberReference().apply {
    memberId = event.memberId
    nickname = event.nickname
    status = MemberStatus.ACTIVE
}

// also: 디버깅, 로깅 (원래 흐름에 영향 안 줌)
return process(data).also { log.debug("결과: $it") }

// run: 여러 줄 계산 후 결과 반환
val greeting = user.run { "안녕하세요, $name ($age세)" }
```

---

## 5. 분기 처리

### Java

```java
// if-else
String label;
if (score >= 90) {
    label = "A";
} else if (score >= 80) {
    label = "B";
} else {
    label = "C";
}

// switch
String message = switch (status) {
    case ACTIVE -> "활성";
    case DELETED -> "삭제됨";
    default -> "알 수 없음";
};

// instanceof
if (shape instanceof Circle c) {
    return Math.PI * c.radius() * c.radius();
} else if (shape instanceof Rectangle r) {
    return r.width() * r.height();
}
```

### Kotlin

```kotlin
// if는 식(expression) → 바로 대입 가능
val label = if (score >= 90) "A" else if (score >= 80) "B" else "C"

// when (switch보다 훨씬 강력)
val message = when (status) {
    MemberStatus.ACTIVE -> "활성"
    MemberStatus.DELETED -> "삭제됨"
}

// when + 스마트 캐스트 (instanceof + 캐스팅 자동)
val area = when (shape) {
    is Circle -> Math.PI * shape.radius * shape.radius  // 캐스팅 불필요
    is Rectangle -> shape.width * shape.height
    else -> 0.0
}
```

### when의 다양한 활용

```kotlin
// 범위 매칭
val grade = when (score) {
    in 90..100 -> "A"
    in 80..89 -> "B"
    in 70..79 -> "C"
    else -> "F"
}

// 조건식 (인자 없는 when)
val action = when {
    user.isAdmin -> "관리자 메뉴"
    user.age >= 18 -> "일반 메뉴"
    else -> "미성년자 메뉴"
}

// 여러 값 매칭
val isWeekend = when (day) {
    "SAT", "SUN" -> true
    else -> false
}

// sealed class + when (exhaustive check → else 불필요)
sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val message: String) : Result()
    data object Loading : Result()
}

fun handle(result: Result): String = when (result) {
    is Result.Success -> "성공: ${result.data}"
    is Result.Error -> "실패: ${result.message}"
    is Result.Loading -> "로딩 중..."
    // else 불필요! 컴파일러가 모든 케이스를 체크
}
```

---

## 6. 문자열 처리

### Java

```java
String message = String.format("안녕하세요 %s님, 주문 %d건이 있습니다.", name, count);

// 여러 줄
String query = """
        SELECT *
        FROM users
        WHERE status = 'ACTIVE'
        """;
```

### Kotlin

```kotlin
// 문자열 템플릿 ($ 사용)
val message = "안녕하세요 ${name}님, 주문 ${count}건이 있습니다."

// 단순 변수는 중괄호 생략 가능
val greeting = "Hello, $name"

// 식(expression)도 가능
val info = "이름: ${user.name}, 성인: ${if (user.age >= 18) "O" else "X"}"

// 여러 줄 (trimIndent로 들여쓰기 정리)
val query = """
    SELECT *
    FROM users
    WHERE status = 'ACTIVE'
""".trimIndent()

// 여러 줄 + 변수 바인딩
val sql = """
    SELECT * FROM users
    WHERE city = '$city'
    AND age > $minAge
""".trimIndent()
```

---

## 7. 기본값과 named argument

Java에서는 오버로딩으로 해결하던 것을 Kotlin은 기본값 하나로 해결한다.

### Java

```java
// 오버로딩 지옥
public List<User> findUsers(String city) {
    return findUsers(city, true, 10, 0);
}

public List<User> findUsers(String city, boolean activeOnly) {
    return findUsers(city, activeOnly, 10, 0);
}

public List<User> findUsers(String city, boolean activeOnly, int limit) {
    return findUsers(city, activeOnly, limit, 0);
}

public List<User> findUsers(String city, boolean activeOnly, int limit, int offset) {
    // 실제 로직
}
```

### Kotlin

```kotlin
// 기본값으로 오버로딩 대체
fun findUsers(
    city: String,
    activeOnly: Boolean = true,
    limit: Int = 10,
    offset: Int = 0,
): List<User> {
    // 실제 로직
}

// 호출 시 named argument로 가독성 확보
findUsers("서울")
findUsers("서울", limit = 50)
findUsers("서울", activeOnly = false, offset = 20)
```

### Builder 패턴 대체

```java
// Java - Builder
Notification notification = Notification.builder()
        .title("알림")
        .message("새 메시지")
        .type(NotificationType.INFO)
        .build();
```

```kotlin
// Kotlin - 기본값 + named argument로 Builder 불필요
data class Notification(
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val priority: Int = 0,
    val silent: Boolean = false,
)

val notification = Notification(
    title = "알림",
    message = "새 메시지",
)
```

---

## 8. 확장 함수

기존 클래스를 수정하지 않고 새 함수를 추가한다. Java의 static utility method를 대체한다.

### Java

```java
// 유틸 클래스
public class StringUtils {
    public static boolean isEmail(String s) {
        return s != null && s.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }

    public static String toSlug(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}

// 사용
if (StringUtils.isEmail(input)) { ... }
```

### Kotlin

```kotlin
// 확장 함수 정의
fun String.isEmail(): Boolean =
    matches(Regex("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))

fun String.toSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]"), "-")

// 사용 - 마치 String에 원래 있던 메서드처럼
if (input.isEmail()) { ... }
val slug = title.toSlug()
```

### 실전 활용

```kotlin
// 날짜 포맷
fun LocalDate.toKoreanFormat(): String =
    "${year}년 ${monthValue}월 ${dayOfMonth}일"

val today = LocalDate.now().toKoreanFormat() // "2026년 2월 10일"

// nullable 확장
fun String?.orEmpty(): String = this ?: ""

// 컬렉션 확장
fun <T> List<T>.secondOrNull(): T? = if (size >= 2) this[1] else null

// 도메인 특화 확장
fun Long.toWon(): String = "%,d원".format(this)
val price = 15000L.toWon() // "15,000원"
```

---

## 9. sealed class로 도메인 모델링

Java의 enum + 상속 조합을 깔끔하게 대체한다.

### Java

```java
// 제한된 계층 구조 표현이 어려움
public abstract class ApiResult<T> {
    // ...
}

public class Success<T> extends ApiResult<T> {
    private final T data;
    // constructor, getter, equals, hashCode...
}

public class Error<T> extends ApiResult<T> {
    private final String message;
    private final int code;
    // constructor, getter, equals, hashCode...
}
```

### Kotlin

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}

// 사용 - when에서 exhaustive check
fun <T> handleResult(result: ApiResult<T>) = when (result) {
    is ApiResult.Success -> println("성공: ${result.data}")
    is ApiResult.Error -> println("에러(${result.code}): ${result.message}")
    is ApiResult.Loading -> println("로딩 중...")
    // else 불필요 - 컴파일러가 모든 경우를 보장
}
```

### 이벤트 모델링에 활용

```kotlin
sealed class MemberCommand {
    data class Signup(val nickname: String, val email: String) : MemberCommand()
    data class UpdateProfile(val nickname: String) : MemberCommand()
    data class Delete(val reason: String?) : MemberCommand()
}

fun execute(command: MemberCommand) = when (command) {
    is MemberCommand.Signup -> createMember(command.nickname, command.email)
    is MemberCommand.UpdateProfile -> updateNickname(command.nickname)
    is MemberCommand.Delete -> deleteMember(command.reason)
}
```

---

## 10. 코루틴과 비동기 처리

Java의 CompletableFuture / Virtual Threads 대비 코루틴은 순차적 코드처럼 비동기 로직을 작성할 수 있다.

### Java (CompletableFuture)

```java
public CompletableFuture<OrderResult> processOrder(Long orderId) {
    return CompletableFuture.supplyAsync(() -> orderService.findById(orderId))
            .thenCompose(order -> CompletableFuture.supplyAsync(() -> {
                paymentService.process(order);
                return order;
            }))
            .thenApply(order -> {
                notificationService.send(order.getUserId(), "주문 완료");
                return new OrderResult(order, true);
            })
            .exceptionally(e -> {
                log.error("주문 처리 실패", e);
                return new OrderResult(null, false);
            });
}
```

### Kotlin (Coroutine)

```kotlin
// 순차적으로 읽히지만 비동기로 동작
suspend fun processOrder(orderId: Long): OrderResult {
    return try {
        val order = orderService.findById(orderId)  // suspend
        paymentService.process(order)                // suspend
        notificationService.send(order.userId, "주문 완료") // suspend
        OrderResult(order, true)
    } catch (e: Exception) {
        log.error("주문 처리 실패", e)
        OrderResult(null, false)
    }
}

// 병렬 처리가 필요할 때
suspend fun getDashboard(userId: Long): Dashboard = coroutineScope {
    val orders = async { orderService.findByUser(userId) }
    val notifications = async { notificationService.findByUser(userId) }
    val profile = async { userService.getProfile(userId) }

    // 3개 동시 실행, 모두 완료될 때까지 대기
    Dashboard(
        orders = orders.await(),
        notifications = notifications.await(),
        profile = profile.await(),
    )
}
```

### Flow (리액티브 스트림 대체)

```kotlin
// Java Flux 느낌의 스트리밍
fun watchPriceChanges(symbol: String): Flow<Price> = flow {
    while (true) {
        val price = priceService.getLatest(symbol)
        emit(price)
        delay(1000)
    }
}

// 수집
watchPriceChanges("BTC")
    .filter { it.changeRate > 0.05 }
    .collect { price -> notify(price) }
```

---

## 11. DSL 스타일 빌더

Kotlin의 람다 수신 객체를 활용하면 읽기 쉬운 DSL을 만들 수 있다.

### Java

```java
List<Route> routes = List.of(
    new Route("GET", "/users", userController::list),
    new Route("POST", "/users", userController::create),
    new Route("GET", "/users/{id}", userController::get)
);
```

### Kotlin DSL

```kotlin
// 라우팅 DSL
fun routes() = router {
    "/users".nest {
        GET("", userHandler::list)
        POST("", userHandler::create)
        GET("/{id}", userHandler::get)
    }
}

// HTML DSL
fun renderPage(user: User) = html {
    head { title("프로필") }
    body {
        h1 { +"${user.name}님의 프로필" }
        ul {
            li { +"나이: ${user.age}" }
            li { +"도시: ${user.city}" }
        }
    }
}

// 테스트 DSL
fun testUser(block: UserBuilder.() -> Unit): User {
    return UserBuilder().apply(block).build()
}

val user = testUser {
    name = "홍길동"
    age = 25
    role = Role.ADMIN
}
```

---

## 12. 프로퍼티와 위임

### Java

```java
public class Config {
    private String dbUrl;

    public String getDbUrl() {
        if (dbUrl == null) {
            dbUrl = loadFromEnv("DB_URL"); // 지연 초기화
        }
        return dbUrl;
    }
}
```

### Kotlin

```kotlin
class Config {
    // lazy - 처음 접근 시 초기화 (thread-safe)
    val dbUrl: String by lazy { loadFromEnv("DB_URL") }

    // observable - 값 변경 감지
    var status: String by Delegates.observable("INIT") { _, old, new ->
        log.info("상태 변경: $old -> $new")
    }

    // custom delegate
    var timeout: Int by RangeDelegate(1..60)
}

// Map으로부터 프로퍼티 위임 (JSON 파싱 등에 유용)
class UserConfig(map: Map<String, Any>) {
    val name: String by map
    val age: Int by map
}

val config = UserConfig(mapOf("name" to "홍길동", "age" to 25))
println(config.name) // "홍길동"
```

---

## 13. 구조 분해

### Java

```java
Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
String key = entry.getKey();
Integer value = entry.getValue();
```

### Kotlin

```kotlin
// Pair / Triple
val (name, age) = Pair("홍길동", 25)

// data class 구조 분해
data class User(val name: String, val age: Int, val city: String)
val (name, age, city) = User("홍길동", 25, "서울")

// Map 순회
for ((key, value) in map) {
    println("$key = $value")
}

// 리스트 구조 분해
val (first, second) = listOf("a", "b", "c") // 앞 2개만

// 람다 파라미터에서도
map.forEach { (key, value) -> println("$key: $value") }
users.forEachIndexed { index, user -> println("$index: ${user.name}") }
```

---

## 14. 싱글톤과 companion object

### Java

```java
// 싱글톤
public class EventBus {
    private static final EventBus INSTANCE = new EventBus();
    private EventBus() {}
    public static EventBus getInstance() { return INSTANCE; }
}

// static 메서드 + 상수
public class HttpStatus {
    public static final int OK = 200;
    public static final int NOT_FOUND = 404;

    public static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }
}
```

### Kotlin

```kotlin
// 싱글톤 - object 선언 한 줄
object EventBus {
    fun publish(event: Any) { /* ... */ }
}
EventBus.publish(myEvent)

// companion object = static 대체
class HttpStatus {
    companion object {
        const val OK = 200
        const val NOT_FOUND = 404

        fun isSuccess(code: Int) = code in 200..299
    }
}
HttpStatus.OK
HttpStatus.isSuccess(201)

// companion object에 팩토리 메서드
data class User(val name: String, val role: Role) {
    companion object {
        fun admin(name: String) = User(name, Role.ADMIN)
        fun guest() = User("Guest", Role.GUEST)
    }
}

val admin = User.admin("홍길동")
```

---

## 15. Spring Boot에서의 Kotlin

### 엔티티

```java
// Java
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member extends BaseEntity {
    @Id
    private Long id;
    private String nickname;
    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Builder
    public Member(Long id, String nickname, MemberStatus status) {
        this.id = id;
        this.nickname = nickname;
        this.status = status;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

```kotlin
// Kotlin
@Entity
class Member protected constructor() : BaseEntity() {
    @Id
    val id: Long = 0L

    var nickname: String = ""
        private set // setter는 내부에서만

    @Enumerated(EnumType.STRING)
    var status: MemberStatus = MemberStatus.ACTIVE
        private set

    fun updateNickname(nickname: String) {
        this.nickname = nickname
    }

    companion object {
        fun create(id: Long, nickname: String): Member = Member().apply {
            // 리플렉션 없이 초기화하려면 별도 생성자 패턴 필요
        }
    }
}
```

### 서비스

```java
// Java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;

    public MemberResponse findById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
        return MemberResponse.from(member);
    }
}
```

```kotlin
// Kotlin
@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository, // 생성자 주입 (자동)
) {
    fun findById(id: Long): MemberResponse {
        val member = memberRepository.findById(id)
            .orElseThrow { MemberNotFoundException(id) }
        return MemberResponse.from(member)
    }
}
```

### 컨트롤러

```kotlin
@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
) {
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findById(id))

    @PostMapping
    fun create(@RequestBody @Valid request: CreateMemberRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.create(request))
}
```

### 테스트

```kotlin
@ExtendWith(MockitoExtension::class)
class MemberServiceTest {

    @InjectMocks
    lateinit var memberService: MemberService

    @Mock
    lateinit var memberRepository: MemberRepository

    @Test
    fun `회원 조회 시 존재하면 응답을 반환한다`() { // 백틱으로 한글 테스트명
        // given
        val member = Member.create(1L, "홍길동")
        given(memberRepository.findById(1L)).willReturn(Optional.of(member))

        // when
        val result = memberService.findById(1L)

        // then
        assertThat(result.name).isEqualTo("홍길동")
    }

    @Test
    fun `존재하지 않는 회원 조회 시 예외가 발생한다`() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty())

        assertThatThrownBy { memberService.findById(999L) }
            .isInstanceOf(MemberNotFoundException::class.java)
    }
}
```

---

## 한눈에 보는 요약

| 주제 | Java 스타일 | Kotlin 스타일 |
|------|-----------|-------------|
| Null 처리 | `if (x != null)` / `Optional` | `?.` / `?:` / `let` |
| DTO | `record` / Lombok `@Data` | `data class` + `copy()` |
| 컬렉션 | `.stream().collect()` | `.filter{}.map{}` |
| 객체 설정 | Builder 패턴 | `apply {}` / named argument |
| 분기 | `switch` + `instanceof` | `when` + 스마트 캐스트 |
| 유틸 메서드 | `XxxUtils.method(obj)` | `obj.method()` 확장 함수 |
| 상수/static | `static final` | `companion object` / `const` |
| 싱글톤 | private 생성자 + `INSTANCE` | `object` |
| 비동기 | `CompletableFuture` | `suspend` / `coroutine` |
| 타입 계층 | abstract class + enum | `sealed class` |
| 지연 초기화 | double-check locking | `by lazy` |
| 문자열 | `String.format()` | `"$variable"` 템플릿 |
