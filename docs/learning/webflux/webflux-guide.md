# WebFlux 완벽 가이드

> 초심자부터 실무 적용까지

---

## 목차

### Part 1: 기초 (이 문서에서 자세히 다룸)
- [1. Reactive Programming이란?](#1-reactive-programming이란)
- [2. Mono와 Flux 이해하기](#2-mono와-flux-이해하기)
- [3. 핵심 연산자](#3-핵심-연산자)
- [4. Backpressure](#4-backpressure)
- [5. Scheduler와 스레드 모델](#5-scheduler와-스레드-모델)

### Part 2: 중급
- [6. WebFlux vs Spring MVC](#6-webflux-vs-spring-mvc)
- [6.5 flatMap 심화 이해](#65-flatmap-심화-이해)
- [7. Virtual Thread vs WebFlux](#7-virtual-thread-vs-webflux)
- [8. 에러 처리 패턴](#8-에러-처리-패턴)
- [9. 테스트 전략](#9-테스트-전략)

### Part 3: 실무
- [10. 실무에서 반드시 피해야 할 안티패턴](#10-실무-안티패턴)
- [11. 디버깅과 트러블슈팅](#11-디버깅)
- [12. 성능 튜닝](#12-성능-튜닝)
- [13. 기존 MVC에서 마이그레이션](#13-마이그레이션)

---

# Part 1: 기초

## 1. Reactive Programming이란?

### 1.1 전통적인 명령형 프로그래밍의 문제

```java
// 명령형: 스레드가 I/O 대기 중 블로킹됨
public User getUser(Long id) {
    User user = userRepository.findById(id);  // DB 응답까지 스레드 대기
    return user;
}
```

**문제점:**
- 스레드는 DB 응답을 기다리는 동안 아무 일도 하지 않음
- 동시 요청 1000개 = 스레드 1000개 필요
- 스레드 생성/컨텍스트 스위칭 비용 발생

### 1.2 Reactive의 핵심 아이디어

```java
// Reactive: 스레드가 블로킹되지 않음
public Mono<User> getUser(Long id) {
    return userRepository.findById(id);  // 즉시 반환, 나중에 데이터 도착
}
```

**핵심 개념:**
- **논블로킹(Non-blocking)**: 작업 완료를 기다리지 않고 즉시 반환
- **이벤트 기반**: 데이터가 준비되면 콜백으로 알림
- **선언적**: "무엇을" 할지 정의, "어떻게"는 프레임워크가 처리

### 1.3 Reactive Streams 스펙

WebFlux는 **Reactive Streams** 표준을 구현한 **Project Reactor** 기반입니다.

```
┌─────────────┐     subscribe      ┌─────────────┐
│  Publisher  │ ◄────────────────  │  Subscriber │
│  (Mono/Flux)│                    │  (Consumer) │
└─────────────┘                    └─────────────┘
       │                                  ▲
       │  onSubscribe(Subscription)       │
       ├──────────────────────────────────┤
       │  request(n)                      │
       │◄─────────────────────────────────┤
       │  onNext(data) * n                │
       ├──────────────────────────────────┤
       │  onComplete() 또는 onError()     │
       └──────────────────────────────────┘
```

**4가지 핵심 인터페이스:**
| 인터페이스 | 역할 |
|-----------|------|
| `Publisher<T>` | 데이터 생산자 (Mono, Flux) |
| `Subscriber<T>` | 데이터 소비자 |
| `Subscription` | Publisher-Subscriber 연결 |
| `Processor<T,R>` | Publisher + Subscriber |

---

## 2. Mono와 Flux 이해하기

### 2.1 Mono: 0 또는 1개의 데이터

```java
// 단일 값
Mono<User> user = Mono.just(new User("홍길동"));

// 빈 값 (null 대신)
Mono<User> empty = Mono.empty();

// 에러
Mono<User> error = Mono.error(new RuntimeException("Not found"));

// 지연 생성 (구독 시점에 실행)
Mono<User> deferred = Mono.defer(() -> Mono.just(fetchUser()));

// 비동기 작업 래핑
Mono<String> async = Mono.fromCallable(() -> blockingOperation());
```

**사용 시점:**
- 단일 엔티티 조회 (`findById`)
- 저장/수정 결과 반환
- API 단일 응답

### 2.2 Flux: 0~N개의 데이터 스트림

```java
// 여러 값
Flux<Integer> numbers = Flux.just(1, 2, 3, 4, 5);

// 컬렉션에서
Flux<User> users = Flux.fromIterable(userList);

// 범위
Flux<Integer> range = Flux.range(1, 10);  // 1부터 10개

// 무한 스트림 (주의!)
Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));

// 프로그래밍 방식 생성
Flux<String> generated = Flux.generate(
    () -> 0,  // 초기 상태
    (state, sink) -> {
        sink.next("Value " + state);
        if (state == 10) sink.complete();
        return state + 1;
    }
);
```

**사용 시점:**
- 목록 조회 (`findAll`)
- 실시간 이벤트 스트림 (SSE)
- 페이징 데이터

### 2.3 Cold vs Hot Publisher

#### Cold Publisher (기본)
```java
// 구독할 때마다 처음부터 실행
Mono<Integer> cold = Mono.fromCallable(() -> {
    System.out.println("계산 실행!");
    return calculateExpensiveValue();
});

cold.subscribe(v -> System.out.println("구독자1: " + v));  // 계산 실행!
cold.subscribe(v -> System.out.println("구독자2: " + v));  // 계산 실행! (다시)
```

#### Hot Publisher
```java
// 한 번만 실행, 결과 공유
Mono<Integer> hot = Mono.fromCallable(() -> {
    System.out.println("계산 실행!");
    return calculateExpensiveValue();
}).cache();  // 또는 share()

hot.subscribe(v -> System.out.println("구독자1: " + v));  // 계산 실행!
hot.subscribe(v -> System.out.println("구독자2: " + v));  // 캐시된 값 사용
```

### 2.4 아무도 구독하지 않으면 아무 일도 안 일어난다!

```java
// ❌ 잘못된 코드 - 실행 안 됨!
public void saveUser(User user) {
    userRepository.save(user);  // Mono 반환, 하지만 구독 안 함
}

// ✅ 올바른 코드
public Mono<User> saveUser(User user) {
    return userRepository.save(user);  // 호출자가 구독
}

// 또는 부득이하게 바로 실행해야 할 때
public void saveUser(User user) {
    userRepository.save(user)
        .subscribe();  // 명시적 구독 (권장하지 않음)
}
```

---

## 3. 핵심 연산자

### 3.1 변환 연산자

#### map: 동기 변환
```java
Mono<User> user = userRepository.findById(id);
Mono<String> name = user.map(u -> u.getName());  // User → String
```

#### flatMap: 비동기 변환 (가장 중요!)
```java
// 사용자 조회 후 → 주문 조회 (비동기 체이닝)
Mono<List<Order>> orders = userRepository.findById(userId)
    .flatMap(user -> orderRepository.findByUserId(user.getId()));

// Flux에서 flatMap은 순서 보장 안 됨!
Flux<Order> orders = userIds.flatMap(id -> orderRepository.findByUserId(id));
```

#### flatMapSequential: 순서 보장 flatMap
```java
// 순서 보장이 필요할 때
Flux<Order> orders = userIds.flatMapSequential(id ->
    orderRepository.findByUserId(id)
);
```

#### concatMap: 순차 실행
```java
// 하나씩 순차적으로 (병렬 X)
Flux<Order> orders = userIds.concatMap(id ->
    orderRepository.findByUserId(id)
);
```

### 3.2 필터링 연산자

```java
Flux<User> activeUsers = users
    .filter(user -> user.isActive())           // 조건 필터
    .distinct()                                 // 중복 제거
    .take(10)                                   // 처음 10개만
    .skip(5)                                    // 처음 5개 건너뛰기
    .takeWhile(user -> user.getAge() < 30)     // 조건 만족하는 동안만
    .takeLast(3);                              // 마지막 3개만
```

### 3.3 조합 연산자

#### zip: 1:1 결합
```java
Mono<User> user = userRepository.findById(userId);
Mono<Profile> profile = profileRepository.findByUserId(userId);

// 둘 다 완료되면 결합
Mono<UserWithProfile> combined = Mono.zip(user, profile,
    (u, p) -> new UserWithProfile(u, p)
);
```

#### merge: 먼저 오는 대로 합치기
```java
Flux<Event> allEvents = Flux.merge(
    eventSourceA.getEvents(),
    eventSourceB.getEvents()
);  // 순서 보장 안 됨, 속도 우선
```

#### concat: 순서대로 합치기
```java
Flux<Event> allEvents = Flux.concat(
    eventSourceA.getEvents(),  // 이게 완료된 후
    eventSourceB.getEvents()   // 이게 시작
);
```

### 3.4 집계 연산자

```java
Flux<Integer> numbers = Flux.range(1, 100);

Mono<Long> count = numbers.count();                    // 개수
Mono<Integer> sum = numbers.reduce(0, Integer::sum);   // 합계
Mono<List<Integer>> list = numbers.collectList();      // 리스트로
Mono<Map<Boolean, List<Integer>>> grouped = numbers
    .collectMultimap(n -> n % 2 == 0);                // 그룹핑
```

### 3.5 연산자 선택 가이드

```
데이터 변환이 필요한가?
├─ 동기 변환 (단순 값 변환) → map
└─ 비동기 변환 (다른 Mono/Flux 호출) → flatMap

flatMap 사용 시:
├─ 순서 상관없음, 성능 우선 → flatMap
├─ 순서 필요, 병렬 실행 가능 → flatMapSequential
└─ 순서 필요, 순차 실행 필수 → concatMap

여러 Publisher 결합:
├─ 모두 완료 후 결합 → zip
├─ 먼저 오는 대로 → merge
└─ 순서대로 연결 → concat
```

---

## 4. Backpressure

### 4.1 Backpressure란?

생산자가 소비자보다 빠를 때 발생하는 문제를 제어하는 메커니즘.

```
Producer (빠름)          Consumer (느림)
   │                         │
   │ ──── 1000개/초 ────►    │ 처리: 100개/초
   │                         │
   └── 메모리 폭발! 💥 ──────┘
```

### 4.2 Backpressure 전략

```java
Flux<Integer> fastProducer = Flux.range(1, Integer.MAX_VALUE);

// 1. BUFFER (기본값) - 버퍼에 저장 (메모리 주의!)
fastProducer
    .onBackpressureBuffer(1000)  // 최대 1000개 버퍼
    .subscribe(slowConsumer);

// 2. DROP - 처리 못하면 버림
fastProducer
    .onBackpressureDrop(dropped -> log.warn("Dropped: {}", dropped))
    .subscribe(slowConsumer);

// 3. LATEST - 최신 값만 유지
fastProducer
    .onBackpressureLatest()
    .subscribe(slowConsumer);

// 4. ERROR - 예외 발생
fastProducer
    .onBackpressureError()
    .subscribe(slowConsumer);
```

### 4.3 limitRate로 요청량 제어

```java
Flux.range(1, 1000)
    .limitRate(100)  // 한 번에 100개씩만 요청
    .subscribe(this::process);
```

---

## 5. Scheduler와 스레드 모델

### 5.1 기본 스레드 동작

```java
// 기본적으로 구독자의 스레드에서 실행
Flux.just(1, 2, 3)
    .map(i -> {
        System.out.println(Thread.currentThread().getName());  // main
        return i * 2;
    })
    .subscribe();
```

### 5.2 Scheduler 종류

| Scheduler | 용도 | 특징 |
|-----------|------|------|
| `Schedulers.immediate()` | 현재 스레드 | 스레드 전환 없음 |
| `Schedulers.single()` | 단일 재사용 스레드 | 순차 작업 |
| `Schedulers.parallel()` | CPU 바운드 작업 | 코어 수만큼 스레드 |
| `Schedulers.boundedElastic()` | I/O 바운드, 블로킹 | 최대 10*코어 스레드 |
| `Schedulers.fromExecutor()` | 커스텀 스레드풀 | 기존 Executor 활용 |

### 5.3 subscribeOn vs publishOn

```java
Flux.just(1, 2, 3)
    .map(i -> {
        log("A: " + i);  // boundedElastic 스레드
        return i;
    })
    .subscribeOn(Schedulers.boundedElastic())  // 구독 시점 스레드 지정
    .map(i -> {
        log("B: " + i);  // boundedElastic 스레드 (변경 없음)
        return i;
    })
    .publishOn(Schedulers.parallel())  // 이후 연산자 스레드 변경
    .map(i -> {
        log("C: " + i);  // parallel 스레드
        return i;
    })
    .subscribe();
```

**핵심 차이:**
- `subscribeOn`: 소스부터 영향, 위치 상관없음 (한 번만 적용)
- `publishOn`: 이후 연산자에만 영향, 위치 중요 (여러 번 가능)

### 5.4 블로킹 코드 래핑 (실무 필수!)

```java
// ❌ 절대 금지: Event Loop에서 블로킹
public Mono<Data> getData() {
    return Mono.just(legacyBlockingService.getData());  // Event Loop 블로킹!
}

// ✅ 올바른 방법: boundedElastic으로 격리
public Mono<Data> getData() {
    return Mono.fromCallable(() -> legacyBlockingService.getData())
        .subscribeOn(Schedulers.boundedElastic());
}
```

---

# Part 2: 중급

## 6. WebFlux vs Spring MVC

### 6.1 아키텍처 비교

#### Spring MVC: Thread-per-Request 모델

```
요청 1 ──► [Thread-1] ──── DB 조회 (대기) ──── 응답 반환 ──► Thread 반환
요청 2 ──► [Thread-2] ──── DB 조회 (대기) ──── 응답 반환 ──► Thread 반환
요청 3 ──► [Thread-3] ──── DB 조회 (대기) ──── 응답 반환 ──► Thread 반환
  ...
요청 200 ──► [Thread-200] ── DB 조회 (대기) ── 응답 반환 ──► Thread 반환
요청 201 ──► [대기열에서 대기...] (스레드 풀 고갈)
```

**특징:**
- 하나의 요청 = 하나의 스레드 점유
- 스레드가 I/O 대기 중에도 반환되지 않음
- 기본 스레드 풀: 200개 (Tomcat 기준)
- 동시 요청 200개 초과 시 대기열 발생

#### WebFlux: Event Loop 모델

```
                    ┌─────────────────────────────────────┐
요청 1,2,3...1000 ──►│         Event Loop (2~4 스레드)       │
                    │                                     │
                    │  요청1 처리 ─► I/O 요청 ─► 다음 요청   │
                    │  요청2 처리 ─► I/O 요청 ─► 다음 요청   │
                    │       ...                           │
                    │  I/O 완료 콜백 ─► 응답 반환           │
                    └─────────────────────────────────────┘
```

**특징:**
- 소수의 Event Loop 스레드가 수천 요청 처리
- I/O 대기 시 스레드 반환, 콜백으로 재개
- Non-blocking I/O 필수 (R2DBC, WebClient 등)
- 하나의 요청이 여러 스레드에서 처리될 수 있음

### 6.2 성능 특성 비교

#### 처리량 (Throughput)

```
시나리오: 동시 요청 1000개, 각 요청에 100ms I/O 지연

Spring MVC (스레드 풀 200):
├── 처리량: ~2,000 req/s
├── 첫 200개: 즉시 처리 시작
├── 나머지 800개: 대기열
└── 메모리: 200 스레드 × ~1MB = ~200MB

WebFlux (Event Loop 4스레드):
├── 처리량: ~10,000 req/s
├── 모든 요청: 즉시 처리 시작 (논블로킹)
└── 메모리: 4 스레드 × ~1MB + 요청 컨텍스트 = ~50MB
```

#### 지연시간 (Latency)

```
낮은 부하 (동시 10개):
├── MVC: 100ms (I/O 시간만큼)
└── WebFlux: 100ms (비슷함)

높은 부하 (동시 1000개):
├── MVC: 100ms ~ 5초 (대기열 지연)
└── WebFlux: 100~150ms (약간 증가)
```

#### CPU 사용률

```
I/O 바운드 작업:
├── MVC: 낮음 (스레드 대부분 대기)
└── WebFlux: 낮음

CPU 바운드 작업:
├── MVC: 높음
└── WebFlux: 높음 (개선 없음)
```

### 6.3 언제 무엇을 선택해야 하는가?

#### Spring MVC가 적합한 경우

```
✅ MVC 선택:
├── 블로킹 라이브러리 필수 (JPA, JDBC, 레거시 SDK)
├── 팀이 Reactive 경험 없음
├── 단순 CRUD 애플리케이션
├── CPU 바운드 작업 위주
├── 동시 접속자 수 예측 가능 (수백 명 이하)
├── 디버깅/트러블슈팅 용이성 중요
└── 기존 MVC 코드베이스 유지보수
```

```java
// MVC 적합 사례: 관리자 대시보드
@RestController
public class AdminController {
    private final JpaUserRepository userRepository;  // JPA (블로킹)

    @GetMapping("/admin/users")
    public List<User> getUsers() {
        return userRepository.findAll();  // 관리자 몇 명만 사용
    }
}
```

#### WebFlux가 적합한 경우

```
✅ WebFlux 선택:
├── 높은 동시성 필요 (수천~수만 동시 요청)
├── 스트리밍 데이터 (SSE, WebSocket)
├── 마이크로서비스 게이트웨이
├── 실시간 데이터 처리
├── 전체 스택이 Reactive 가능
├── Backpressure 제어 필요
└── 신규 프로젝트 (처음부터 설계)
```

```java
// WebFlux 적합 사례: 실시간 주식 시세
@RestController
public class StockController {

    @GetMapping(value = "/stocks/stream", produces = TEXT_EVENT_STREAM_VALUE)
    public Flux<StockPrice> streamPrices() {
        return stockService.getPriceStream()  // 무한 스트림
            .takeWhile(price -> marketIsOpen());
    }
}
```

### 6.4 성능 비교 시나리오별

| 시나리오 | MVC | WebFlux | 권장 |
|---------|-----|---------|------|
| CRUD API (낮은 부하) | 우수 | 우수 | 둘 다 OK |
| CRUD API (높은 부하) | 보통 | 우수 | WebFlux |
| 파일 업로드/다운로드 | 우수 | 우수 | 둘 다 OK |
| 실시간 스트리밍 | 불가 | 우수 | WebFlux |
| CPU 집약 계산 | 동일 | 동일 | 둘 다 동일 |
| 외부 API 다중 호출 | 보통 | 우수 | WebFlux |
| 레거시 DB (JDBC) | 우수 | 복잡 | MVC |

### 6.5 공존 전략 (같은 프로젝트에서 혼용)

#### 방법 1: 별도 서비스 분리

```
┌─────────────────┐      ┌─────────────────┐
│  MVC 서비스      │      │  WebFlux 서비스  │
│  (관리자 API)    │      │  (실시간 API)    │
│  JPA, Thymeleaf │      │  R2DBC, SSE     │
└────────┬────────┘      └────────┬────────┘
         │                        │
         └──────────┬─────────────┘
                    ▼
              [API Gateway]
```

#### 방법 2: 같은 프로젝트에서 혼용

```java
// Spring Boot는 MVC와 WebFlux 동시 사용 지원하지 않음!
// 하지만 MVC에서 WebClient 사용은 가능

@RestController  // MVC 컨트롤러
public class HybridController {

    private final WebClient webClient;  // WebClient (논블로킹)
    private final JpaRepository jpaRepository;  // JPA (블로킹)

    @GetMapping("/data")
    public Data getData() {
        // WebClient로 외부 API 호출 (논블로킹)
        ExternalData external = webClient.get()
            .uri("/external/api")
            .retrieve()
            .bodyToMono(ExternalData.class)
            .block();  // MVC에서는 block() 필요

        // JPA로 DB 조회 (블로킹)
        LocalData local = jpaRepository.findById(1L).orElseThrow();

        return new Data(external, local);
    }
}
```

#### 방법 3: 점진적 마이그레이션

```
Phase 1: MVC + WebClient (RestTemplate 대체)
    ↓
Phase 2: 신규 기능을 별도 WebFlux 서비스로
    ↓
Phase 3: 필요에 따라 기존 서비스 마이그레이션
```

### 6.6 선택 플로우차트

```
시작
  │
  ▼
블로킹 라이브러리 필수? (JPA, 레거시 SDK)
  │
  ├─ Yes ──► Spring MVC (또는 MVC + Virtual Thread)
  │
  ▼ No
실시간 스트리밍/SSE 필요?
  │
  ├─ Yes ──► WebFlux
  │
  ▼ No
동시 접속 수천 이상?
  │
  ├─ Yes ──► WebFlux
  │
  ▼ No
팀 Reactive 역량?
  │
  ├─ 낮음 ──► Spring MVC
  │
  ▼ 높음
신규 프로젝트?
  │
  ├─ Yes ──► WebFlux (미래 확장성)
  │
  ▼ No
  │
  └──► Spring MVC (유지보수 용이)
```

### 6.7 핵심 정리

```
Spring MVC:
├── 장점: 익숙함, 디버깅 쉬움, 블로킹 라이브러리 호환
├── 단점: 높은 동시성에서 스레드 풀 고갈
└── 적합: 일반 웹 애플리케이션, 관리자 도구

WebFlux:
├── 장점: 높은 동시성, 적은 리소스, 스트리밍
├── 단점: 학습 곡선, 디버깅 어려움, 블로킹 금지
└── 적합: 실시간 시스템, API 게이트웨이, 마이크로서비스
```

---

## 6.5 flatMap 심화 이해

> **flatMap은 WebFlux에서 가장 중요한 연산자입니다. 제대로 이해해야 합니다.**

### 왜 flatMap이 필요한가?

#### 문제 상황: map으로는 안 되는 것

```java
// 사용자 ID로 사용자 조회 후, 그 사용자의 주문 목록 조회
Mono<User> userMono = userRepository.findById(userId);

// ❌ map 사용 시: Mono<Mono<List<Order>>> 가 됨!
Mono<Mono<List<Order>>> nested = userMono.map(user ->
    orderRepository.findByUserId(user.getId())  // Mono<List<Order>> 반환
);
// 결과: Mono 안에 Mono가 중첩됨 (사용 불가)

// ✅ flatMap 사용 시: Mono<List<Order>> 가 됨!
Mono<List<Order>> orders = userMono.flatMap(user ->
    orderRepository.findByUserId(user.getId())  // Mono<List<Order>> 반환
);
// 결과: 중첩이 "평탄화(flatten)"되어 하나의 Mono
```

### map vs flatMap 비교

```java
// map: 값 → 값 (동기 변환)
Mono<User> user = Mono.just(new User("홍길동"));
Mono<String> name = user.map(u -> u.getName());  // User → String

// flatMap: 값 → Publisher (비동기 변환)
Mono<User> user = Mono.just(new User("홍길동"));
Mono<Profile> profile = user.flatMap(u ->
    profileRepository.findByUserId(u.getId())  // User → Mono<Profile>
);
```

**핵심 차이:**
| | map | flatMap |
|---|---|---|
| 입력 | 값 | 값 |
| 람다 반환 | 값 | Mono/Flux (Publisher) |
| 결과 | 그대로 래핑 | 평탄화 (중첩 제거) |
| 용도 | 동기 변환 | 비동기 체이닝 |

### flatMap 사용 시점 판단법

```java
// 질문: 람다 안에서 뭘 반환하나?

// Case 1: 단순 값 반환 → map
user.map(u -> u.getName())  // String 반환

// Case 2: Mono/Flux 반환 → flatMap
user.flatMap(u -> orderRepository.findByUserId(u.getId()))  // Mono 반환

// Case 3: 조건부 Mono 반환 → flatMap
user.flatMap(u -> {
    if (u.isPremium()) {
        return premiumService.getFeatures(u.getId());  // Mono 반환
    } else {
        return Mono.just(BasicFeatures.DEFAULT);  // Mono 반환
    }
})
```

**쉬운 판단법:**
```
내가 호출하는 메서드가 Mono/Flux를 반환하나?
├── Yes → flatMap 사용
└── No → map 사용
```

### 실전 예제: 여러 비동기 작업 연결

```java
// 시나리오: 사용자 조회 → 주문 조회 → 결제 정보 조회

// ❌ 잘못된 코드: map 남용
public Mono<Mono<Mono<PaymentInfo>>> wrong(Long userId) {
    return userRepository.findById(userId)  // Mono<User>
        .map(user -> orderRepository.findLatestByUserId(user.getId()))  // Mono<Mono<Order>>
        .map(orderMono -> orderMono.map(order ->  // Mono<Mono<Mono<PaymentInfo>>>
            paymentRepository.findByOrderId(order.getId())));
}
// 결과: 중첩된 Mono 지옥!

// ✅ 올바른 코드: flatMap 체이닝
public Mono<PaymentInfo> correct(Long userId) {
    return userRepository.findById(userId)  // Mono<User>
        .flatMap(user ->
            orderRepository.findLatestByUserId(user.getId()))  // Mono<Order>
        .flatMap(order ->
            paymentRepository.findByOrderId(order.getId()));  // Mono<PaymentInfo>
}
// 결과: 깔끔한 Mono<PaymentInfo>
```

### flatMap과 Flux: 순서 보장 안 됨!

```java
Flux<Long> userIds = Flux.just(1L, 2L, 3L);

// ⚠️ flatMap: 병렬 실행, 순서 보장 안 됨
userIds.flatMap(id -> userRepository.findById(id))
    .subscribe(user -> System.out.println(user.getId()));
// 출력: 2, 1, 3 (순서 랜덤!)

// ✅ flatMapSequential: 병렬 실행, 순서 보장
userIds.flatMapSequential(id -> userRepository.findById(id))
    .subscribe(user -> System.out.println(user.getId()));
// 출력: 1, 2, 3 (순서 보장)

// ✅ concatMap: 순차 실행, 순서 보장 (느림)
userIds.concatMap(id -> userRepository.findById(id))
    .subscribe(user -> System.out.println(user.getId()));
// 출력: 1, 2, 3 (순서 보장, 하나씩 실행)
```

### flatMap 동시성 제어

```java
Flux<Long> manyIds = Flux.range(1, 1000).map(Long::valueOf);

// 기본: 256개 동시 실행 (너무 많을 수 있음)
manyIds.flatMap(id -> callExternalApi(id))
    .subscribe();

// 동시성 제한: 10개씩만
manyIds.flatMap(id -> callExternalApi(id), 10)
    .subscribe();

// 동시성 + 최대 버퍼
manyIds.flatMap(id -> callExternalApi(id), 10, 32)
    .subscribe();
```

### 중간 결과가 필요할 때: flatMap + Tuple/zip

```java
// 사용자 조회 후 주문 조회, 응답에 둘 다 필요

// 방법 1: Tuple 사용
public Mono<OrderWithUser> getOrderWithUser(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(order ->
            userRepository.findById(order.getUserId())
                .map(user -> Tuples.of(order, user)))  // Tuple2<Order, User>
        .map(tuple -> new OrderWithUser(tuple.getT1(), tuple.getT2()));
}

// 방법 2: zip 사용
public Mono<OrderWithUser> getOrderWithUser(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(order ->
            Mono.zip(
                Mono.just(order),
                userRepository.findById(order.getUserId())
            ))
        .map(tuple -> new OrderWithUser(tuple.getT1(), tuple.getT2()));
}

// 방법 3: 중간 객체로 전달 (가독성 좋음)
public Mono<OrderWithUser> getOrderWithUser(Long orderId) {
    return orderRepository.findById(orderId)
        .flatMap(order ->
            userRepository.findById(order.getUserId())
                .map(user -> new OrderWithUser(order, user)));
}
```

### flatMap 안에서 에러 처리

```java
public Mono<OrderResult> processOrder(OrderRequest request) {
    return validateRequest(request)
        .flatMap(validated -> {
            // flatMap 내부에서 에러 발생 가능
            return inventoryService.checkStock(validated.getItems())
                .flatMap(stockOk -> {
                    if (!stockOk) {
                        // 에러 반환
                        return Mono.error(new InsufficientStockException());
                    }
                    return processPayment(validated);
                });
        })
        .onErrorResume(InsufficientStockException.class, e ->
            Mono.just(OrderResult.outOfStock()));
}
```

### flatMap 사용 요약

```
flatMap 사용 체크리스트:

1. 언제 사용?
   └─ 람다가 Mono/Flux를 반환할 때

2. map과 헷갈릴 때?
   └─ 반환 타입 확인: Mono/Flux면 flatMap

3. Flux에서 순서가 필요하면?
   └─ flatMapSequential 또는 concatMap

4. 동시 실행 개수 제한?
   └─ flatMap(fn, concurrency)

5. 중간 결과 보존?
   └─ Tuple, zip, 또는 중간 객체 사용

6. 에러 처리?
   └─ flatMap 체인 끝에 onErrorResume/onErrorMap
```

---

## 7. Virtual Thread vs WebFlux

### 7.1 Virtual Thread란?

Java 21+에서 도입된 **경량 스레드**. OS 스레드가 아닌 JVM이 관리하는 스레드.

```java
// 기존 Platform Thread
Thread platformThread = new Thread(() -> doWork());  // OS 스레드 1개 점유

// Virtual Thread (Java 21+)
Thread virtualThread = Thread.ofVirtual().start(() -> doWork());  // OS 스레드 공유
```

**핵심 원리:**
```
┌─────────────────────────────────────────────────────┐
│                    JVM                               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ VT 1    │ │ VT 2    │ │ VT 3    │ │ VT 1000 │   │
│  │(blocked)│ │(running)│ │(blocked)│ │(running)│   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   │
│       │           │           │           │         │
│       └─────┬─────┴─────┬─────┴─────┬─────┘         │
│             ▼           ▼           ▼               │
│     ┌───────────┐ ┌───────────┐ ┌───────────┐      │
│     │ Carrier   │ │ Carrier   │ │ Carrier   │      │
│     │ Thread 1  │ │ Thread 2  │ │ Thread 3  │      │
│     └───────────┘ └───────────┘ └───────────┘      │
│         (실제 OS 스레드: CPU 코어 수만큼)            │
└─────────────────────────────────────────────────────┘
```

- Virtual Thread가 블로킹되면 Carrier Thread에서 **unmount**
- 다른 Virtual Thread가 해당 Carrier Thread 사용
- I/O 완료 시 다시 **mount**되어 실행 재개

### 7.2 동작 방식 비교

#### Traditional Thread (Spring MVC)
```java
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    User user = userRepository.findById(id);  // 스레드 블로킹
    Profile profile = profileService.getProfile(user.getId());  // 스레드 블로킹
    return enrichUser(user, profile);
}
// 동시 1000 요청 = 1000 OS 스레드 필요 (리소스 과다)
```

#### Virtual Thread (Spring MVC + VT)
```java
// 코드는 동일! 설정만 변경
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    User user = userRepository.findById(id);  // VT unmount, Carrier 반환
    Profile profile = profileService.getProfile(user.getId());  // VT unmount
    return enrichUser(user, profile);
}
// 동시 1000 요청 = 1000 Virtual Thread (Carrier는 코어 수만큼)
```

```yaml
# application.yml - Virtual Thread 활성화
spring:
  threads:
    virtual:
      enabled: true
```

#### WebFlux (Reactive)
```java
@GetMapping("/user/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return userRepository.findById(id)  // 논블로킹, 즉시 반환
        .flatMap(user -> profileService.getProfile(user.getId())
            .map(profile -> enrichUser(user, profile)));
}
// 동시 1000 요청 = Event Loop 스레드 몇 개로 처리
```

### 7.3 성능 비교

| 시나리오 | Virtual Thread | WebFlux |
|---------|----------------|---------|
| **I/O 바운드 (DB, API 호출)** | 우수 | 우수 |
| **CPU 바운드 (계산 집약)** | 동일 | 동일 (병목은 CPU) |
| **메모리 사용량** | 낮음 (VT당 ~KB) | 매우 낮음 |
| **처리량 (Throughput)** | 높음 | 매우 높음 |
| **지연시간 (Latency)** | 약간 높음 | 낮음 |

**벤치마크 결과 (일반적 경향):**
```
동시 요청 10,000개, I/O 지연 100ms 시나리오:

Platform Thread (200 pool):  처리량 ~2,000 req/s
Virtual Thread:              처리량 ~9,000 req/s
WebFlux:                     처리량 ~12,000 req/s

* 실제 결과는 워크로드에 따라 다름
```

### 7.4 선택 가이드라인

#### Virtual Thread를 선택해야 할 때

```
✅ Virtual Thread 추천:
├── 기존 Spring MVC 코드가 많다
├── 팀이 Reactive 경험이 없다
├── 블로킹 라이브러리 사용 필수 (JPA, JDBC, 레거시 SDK)
├── 코드 가독성/디버깅 용이성 중요
├── 단순 CRUD 애플리케이션
└── Java 21+ 사용 가능
```

```java
// Virtual Thread 적합 사례: 레거시 + 신규 혼합
@Service
public class OrderService {
    private final JpaOrderRepository orderRepository;  // JPA (블로킹)
    private final LegacyPaymentClient paymentClient;   // 레거시 SDK (블로킹)

    public Order createOrder(OrderRequest request) {
        // 익숙한 동기 코드 스타일 유지
        Order order = orderRepository.save(new Order(request));
        paymentClient.processPayment(order.getId());  // VT가 알아서 처리
        return order;
    }
}
```

#### WebFlux를 선택해야 할 때

```
✅ WebFlux 추천:
├── 신규 프로젝트 (처음부터 Reactive 설계)
├── 실시간 스트리밍 필요 (SSE, WebSocket)
├── Backpressure 제어 필수 (대용량 데이터 스트림)
├── 전체 스택이 Reactive (R2DBC, WebClient, Reactive Redis)
├── 최대 처리량 + 최소 지연시간 필수
├── 팀이 Reactive에 익숙하거나 학습 의지 있음
└── 함수형 프로그래밍 선호
```

```java
// WebFlux 적합 사례: 실시간 대시보드
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<DashboardData>> streamEvents() {
    return Flux.interval(Duration.ofSeconds(1))
        .flatMap(tick -> metricsService.getCurrentMetrics())
        .map(data -> ServerSentEvent.builder(data).build())
        .takeUntilOther(shutdownSignal);  // Backpressure + 정상 종료
}
```

### 7.5 잘못된 선택의 결과

#### WebFlux에서 블로킹 코드 사용 (치명적)
```java
// ❌ Event Loop 스레드 블로킹 = 전체 시스템 마비
@GetMapping("/data")
public Mono<Data> getData() {
    Data data = jdbcTemplate.queryForObject(...);  // Event Loop 블로킹!
    return Mono.just(data);
}

// 증상: 요청 몇 개만 처리되다가 전체 멈춤
```

#### Virtual Thread에서 synchronized 사용 (Pinning)
```java
// ⚠️ synchronized는 Carrier Thread를 고정시킴 (Pinning)
public synchronized void criticalSection() {
    doBlockingIO();  // Carrier Thread가 해제 안 됨!
}

// 해결: ReentrantLock 사용
private final ReentrantLock lock = new ReentrantLock();
public void criticalSection() {
    lock.lock();
    try {
        doBlockingIO();  // VT unmount 가능
    } finally {
        lock.unlock();
    }
}
```

### 7.6 하이브리드 접근법

```java
// WebFlux 컨트롤러 + 블로킹 서비스 격리
@RestController
public class HybridController {

    private final BlockingLegacyService legacyService;

    @GetMapping("/hybrid")
    public Mono<Result> hybridEndpoint() {
        return reactiveService.getData()  // Reactive
            .flatMap(data ->
                // 블로킹 서비스는 boundedElastic에서 실행
                Mono.fromCallable(() -> legacyService.process(data))
                    .subscribeOn(Schedulers.boundedElastic())
            );
    }
}
```

### 7.7 결정 플로우차트

```
시작
  │
  ▼
Java 21+ 사용 가능?
  │
  ├─ No ──► WebFlux (또는 Java 업그레이드)
  │
  ▼ Yes
기존 MVC 코드 많음?
  │
  ├─ Yes ──► Virtual Thread (마이그레이션 비용 최소화)
  │
  ▼ No
실시간 스트리밍/Backpressure 필요?
  │
  ├─ Yes ──► WebFlux
  │
  ▼ No
전체 스택 Reactive 가능?
  │
  ├─ No ──► Virtual Thread
  │
  ▼ Yes
팀 Reactive 역량?
  │
  ├─ 낮음 ──► Virtual Thread (학습 비용 고려)
  │
  ▼ 충분
  │
  └──► WebFlux
```

---

## 8. 에러 처리 패턴

### 8.1 에러 처리 연산자 개요

| 연산자 | 용도 | 반환 타입 |
|--------|------|----------|
| `onErrorReturn` | 에러 시 기본값 반환 | 값 |
| `onErrorResume` | 에러 시 대체 Publisher | Mono/Flux |
| `onErrorMap` | 에러 타입 변환 | Throwable |
| `onErrorComplete` | 에러 무시하고 완료 | - |
| `doOnError` | 에러 로깅 (사이드 이펙트) | - |

### 8.2 onErrorReturn: 기본값 반환

```java
// 단순 기본값
Mono<User> user = userRepository.findById(id)
    .onErrorReturn(new User("Guest"));

// 특정 예외만 처리
Mono<User> user = userRepository.findById(id)
    .onErrorReturn(NotFoundException.class, new User("Guest"));

// 조건부 기본값
Mono<User> user = userRepository.findById(id)
    .onErrorReturn(
        e -> e instanceof TimeoutException,
        new User("Timeout Fallback")
    );
```

**주의:** 기본값은 **미리 생성**되므로 무거운 객체는 `onErrorResume` 사용

### 8.3 onErrorResume: 대체 로직 실행

```java
// 캐시 실패 시 DB 조회
Mono<User> user = cacheRepository.findById(id)
    .onErrorResume(e -> {
        log.warn("Cache failed, falling back to DB", e);
        return dbRepository.findById(id);
    });

// 예외 타입별 분기
Mono<User> user = userService.findById(id)
    .onErrorResume(NotFoundException.class, e -> Mono.empty())
    .onErrorResume(TimeoutException.class, e ->
        fallbackService.findById(id))
    .onErrorResume(e ->
        Mono.error(new ServiceException("Unexpected error", e)));
```

### 8.4 onErrorMap: 예외 변환

```java
// 하위 예외를 도메인 예외로 변환
Mono<Order> order = paymentGateway.processPayment(request)
    .onErrorMap(WebClientException.class, e ->
        new PaymentFailedException("Payment gateway error", e))
    .onErrorMap(TimeoutException.class, e ->
        new PaymentTimeoutException("Payment timed out", e));

// 예외 래핑 (스택트레이스 보존)
Mono<Data> data = externalApi.call()
    .onErrorMap(e -> new ExternalServiceException(
        "Failed to call external API: " + e.getMessage(), e));
```

### 8.5 Retry 패턴

#### 기본 retry
```java
// 최대 3번 재시도
Mono<Response> response = externalApi.call()
    .retry(3);
```

#### retryWhen: 고급 재시도 전략
```java
// 지수 백오프 (Exponential Backoff)
Mono<Response> response = externalApi.call()
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(2))
        .jitter(0.5)  // 50% 랜덤 지연 추가 (thundering herd 방지)
        .filter(e -> e instanceof TransientException)  // 특정 예외만
        .onRetryExhaustedThrow((spec, signal) ->
            new ServiceUnavailableException("Retry exhausted", signal.failure()))
    );

// 재시도 로깅
Mono<Response> response = externalApi.call()
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
        .doBeforeRetry(signal ->
            log.warn("Retry #{} due to {}",
                signal.totalRetries() + 1,
                signal.failure().getMessage()))
    );
```

#### 재시도 vs 즉시 실패 판단
```java
Mono<Response> response = externalApi.call()
    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
        .filter(e -> isRetryable(e)));  // 재시도 가능한 예외만

private boolean isRetryable(Throwable e) {
    return e instanceof TimeoutException
        || e instanceof ConnectException
        || (e instanceof WebClientResponseException wce
            && wce.getStatusCode().is5xxServerError());
}
```

### 8.6 타임아웃 처리

```java
// 기본 타임아웃
Mono<Response> response = externalApi.call()
    .timeout(Duration.ofSeconds(5));  // TimeoutException 발생

// 타임아웃 시 대체 값
Mono<Response> response = externalApi.call()
    .timeout(Duration.ofSeconds(5), Mono.just(fallbackResponse));

// 타임아웃 + 재시도 조합
Mono<Response> response = externalApi.call()
    .timeout(Duration.ofSeconds(2))  // 개별 호출 타임아웃
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .filter(e -> e instanceof TimeoutException))
    .timeout(Duration.ofSeconds(10));  // 전체 작업 타임아웃
```

### 8.7 전역 예외 처리 (WebFlux)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(NotFoundException e) {
        return Mono.just(new ErrorResponse(
            "NOT_FOUND",
            e.getMessage(),
            LocalDateTime.now()
        ));
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleValidation(ValidationException e) {
        return Mono.just(new ErrorResponse(
            "VALIDATION_ERROR",
            e.getMessage(),
            LocalDateTime.now()
        ));
    }

    // 예상치 못한 예외
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return Mono.just(new ErrorResponse(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            LocalDateTime.now()
        ));
    }
}

public record ErrorResponse(
    String code,
    String message,
    LocalDateTime timestamp
) {}
```

### 8.8 WebClient 에러 처리

```java
@Component
public class ExternalApiClient {

    private final WebClient webClient;

    public Mono<UserDto> getUser(Long id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            // HTTP 상태 코드별 예외 변환
            .onStatus(
                status -> status == HttpStatus.NOT_FOUND,
                response -> Mono.error(new UserNotFoundException(id))
            )
            .onStatus(
                HttpStatusCode::is4xxClientError,
                response -> response.bodyToMono(ErrorBody.class)
                    .flatMap(body -> Mono.error(
                        new ClientException(body.message())))
            )
            .onStatus(
                HttpStatusCode::is5xxServerError,
                response -> Mono.error(new ExternalServiceException())
            )
            .bodyToMono(UserDto.class)
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                .filter(e -> e instanceof ExternalServiceException));
    }
}
```

### 8.9 에러 처리 체이닝 패턴

```java
public Mono<OrderResult> processOrder(OrderRequest request) {
    return validateRequest(request)
        .flatMap(this::checkInventory)
        .flatMap(this::reserveInventory)
        .flatMap(this::processPayment)
        .flatMap(this::createOrder)
        // 단계별 에러 처리
        .onErrorMap(InventoryException.class, e ->
            new OrderFailedException("Inventory check failed", e))
        .onErrorMap(PaymentException.class, e ->
            new OrderFailedException("Payment failed", e))
        // 보상 트랜잭션
        .onErrorResume(OrderFailedException.class, e -> {
            log.error("Order failed, compensating...", e);
            return compensate(request)
                .then(Mono.error(e));  // 보상 후 에러 전파
        });
}
```

### 8.10 에러 처리 결정 가이드

```
에러 발생 시:
  │
  ├─ 복구 가능?
  │   ├─ Yes: 기본값으로 충분?
  │   │   ├─ Yes → onErrorReturn
  │   │   └─ No (대체 로직 필요) → onErrorResume
  │   │
  │   └─ No: 다른 예외로 변환 필요?
  │       ├─ Yes → onErrorMap
  │       └─ No → 그대로 전파 (처리 안 함)
  │
  ├─ 재시도로 해결 가능?
  │   ├─ 일시적 오류 (네트워크, 타임아웃) → retryWhen + backoff
  │   └─ 영구적 오류 (404, 비즈니스 에러) → 재시도 불필요
  │
  └─ 로깅만 필요?
      └─ doOnError (에러는 그대로 전파)
```

---

## 9. 테스트 전략

### 9.1 테스트 의존성 설정

```groovy
// build.gradle
dependencies {
    testImplementation 'io.projectreactor:reactor-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### 9.2 StepVerifier 기본 사용법

StepVerifier는 Reactive 스트림을 **구독하고 검증**하는 도구.

```java
@Test
void mono_기본_테스트() {
    Mono<String> mono = Mono.just("Hello");

    StepVerifier.create(mono)
        .expectNext("Hello")
        .verifyComplete();  // 완료 시그널 검증
}

@Test
void flux_여러_값_테스트() {
    Flux<Integer> flux = Flux.just(1, 2, 3);

    StepVerifier.create(flux)
        .expectNext(1)
        .expectNext(2)
        .expectNext(3)
        .verifyComplete();

    // 또는 한 번에
    StepVerifier.create(flux)
        .expectNext(1, 2, 3)
        .verifyComplete();
}

@Test
void 에러_테스트() {
    Mono<String> errorMono = Mono.error(new RuntimeException("Oops"));

    StepVerifier.create(errorMono)
        .expectError(RuntimeException.class)
        .verify();

    // 에러 메시지 검증
    StepVerifier.create(errorMono)
        .expectErrorMessage("Oops")
        .verify();

    // 에러 상세 검증
    StepVerifier.create(errorMono)
        .expectErrorMatches(e ->
            e instanceof RuntimeException && e.getMessage().contains("Oops"))
        .verify();
}
```

### 9.3 고급 StepVerifier 패턴

```java
@Test
void 조건부_검증() {
    Flux<User> users = userService.findAllActive();

    StepVerifier.create(users)
        .expectNextMatches(user -> user.isActive())
        .expectNextMatches(user -> user.isActive())
        .thenConsumeWhile(User::isActive)  // 나머지 모두 active인지
        .verifyComplete();
}

@Test
void 개수_검증() {
    Flux<Integer> flux = Flux.range(1, 100);

    StepVerifier.create(flux)
        .expectNextCount(100)
        .verifyComplete();
}

@Test
void 부분_검증_후_취소() {
    Flux<Integer> infiniteFlux = Flux.interval(Duration.ofMillis(100))
        .map(Long::intValue);

    StepVerifier.create(infiniteFlux)
        .expectNext(0, 1, 2)
        .thenCancel()  // 구독 취소
        .verify();
}

@Test
void recordWith로_수집_후_검증() {
    Flux<String> flux = Flux.just("a", "b", "c");

    StepVerifier.create(flux)
        .recordWith(ArrayList::new)
        .expectNextCount(3)
        .consumeRecordedWith(list -> {
            assertThat(list).hasSize(3);
            assertThat(list).contains("a", "b", "c");
        })
        .verifyComplete();
}
```

### 9.4 시간 기반 테스트 (withVirtualTime)

실제 시간을 기다리지 않고 **가상 시간**으로 테스트.

```java
@Test
void interval_테스트() {
    // ❌ 실제로 3초 기다림
    Flux<Long> flux = Flux.interval(Duration.ofSeconds(1)).take(3);
    StepVerifier.create(flux)
        .expectNext(0L, 1L, 2L)
        .verifyComplete();  // 3초 소요!

    // ✅ 가상 시간으로 즉시 테스트
    StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofSeconds(1)).take(3))
        .thenAwait(Duration.ofSeconds(3))
        .expectNext(0L, 1L, 2L)
        .verifyComplete();  // 즉시 완료!
}

@Test
void 타임아웃_테스트() {
    StepVerifier.withVirtualTime(() ->
            Mono.delay(Duration.ofSeconds(10))
                .timeout(Duration.ofSeconds(5)))
        .thenAwait(Duration.ofSeconds(5))
        .expectError(TimeoutException.class)
        .verify();
}

@Test
void 지연_검증() {
    StepVerifier.withVirtualTime(() ->
            Mono.just("data").delayElement(Duration.ofHours(1)))
        .expectSubscription()
        .expectNoEvent(Duration.ofMinutes(59))  // 59분 동안 이벤트 없음
        .thenAwait(Duration.ofMinutes(1))
        .expectNext("data")
        .verifyComplete();
}
```

**주의:** `withVirtualTime` 내부에서 Publisher 생성 필수!
```java
// ❌ 잘못된 사용
Flux<Long> flux = Flux.interval(Duration.ofSeconds(1));  // 외부 생성
StepVerifier.withVirtualTime(() -> flux)  // 가상 시간 적용 안 됨!

// ✅ 올바른 사용
StepVerifier.withVirtualTime(() ->
    Flux.interval(Duration.ofSeconds(1)))  // 내부 생성
```

### 9.5 WebTestClient

WebFlux 컨트롤러 통합 테스트용 클라이언트.

```java
@WebFluxTest(UserController.class)
class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @Test
    void getUser_성공() {
        User mockUser = new User(1L, "홍길동", "hong@test.com");
        when(userService.findById(1L)).thenReturn(Mono.just(mockUser));

        webTestClient.get()
            .uri("/users/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(User.class)
            .value(user -> {
                assertThat(user.getName()).isEqualTo("홍길동");
                assertThat(user.getEmail()).isEqualTo("hong@test.com");
            });
    }

    @Test
    void getUser_없음_404() {
        when(userService.findById(999L)).thenReturn(Mono.empty());

        webTestClient.get()
            .uri("/users/999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void createUser_성공() {
        CreateUserRequest request = new CreateUserRequest("홍길동", "hong@test.com");
        User createdUser = new User(1L, "홍길동", "hong@test.com");

        when(userService.create(any())).thenReturn(Mono.just(createdUser));

        webTestClient.post()
            .uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.name").isEqualTo("홍길동");
    }

    @Test
    void getAllUsers_스트리밍() {
        Flux<User> users = Flux.just(
            new User(1L, "User1", "user1@test.com"),
            new User(2L, "User2", "user2@test.com")
        );
        when(userService.findAll()).thenReturn(users);

        webTestClient.get()
            .uri("/users")
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(User.class)
            .hasSize(2);
    }
}
```

### 9.6 서비스 레이어 테스트

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_성공() {
        CreateUserCommand command = new CreateUserCommand("홍길동", "hong@test.com");
        User savedUser = new User(1L, "홍길동", "hong@test.com");

        when(userRepository.existsByEmail("hong@test.com"))
            .thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class)))
            .thenReturn(Mono.just(savedUser));
        when(emailService.sendWelcome(any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(userService.create(command))
            .expectNext(savedUser)
            .verifyComplete();

        verify(emailService).sendWelcome(savedUser);
    }

    @Test
    void createUser_이메일_중복() {
        CreateUserCommand command = new CreateUserCommand("홍길동", "existing@test.com");

        when(userRepository.existsByEmail("existing@test.com"))
            .thenReturn(Mono.just(true));

        StepVerifier.create(userService.create(command))
            .expectError(DuplicateEmailException.class)
            .verify();

        verify(userRepository, never()).save(any());
    }
}
```

### 9.7 Repository 테스트 (R2DBC)

```java
@DataR2dbcTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_and_findById() {
        User user = new User(null, "홍길동", "hong@test.com");

        StepVerifier.create(
            userRepository.save(user)
                .flatMap(saved -> userRepository.findById(saved.getId()))
        )
        .assertNext(found -> {
            assertThat(found.getName()).isEqualTo("홍길동");
            assertThat(found.getEmail()).isEqualTo("hong@test.com");
        })
        .verifyComplete();
    }

    @Test
    void findByEmail() {
        User user = new User(null, "홍길동", "hong@test.com");

        StepVerifier.create(
            userRepository.save(user)
                .then(userRepository.findByEmail("hong@test.com"))
        )
        .assertNext(found ->
            assertThat(found.getName()).isEqualTo("홍길동"))
        .verifyComplete();
    }
}
```

### 9.8 block() 사용 (테스트 한정)

단순 테스트에서는 `block()`으로 동기화 가능. **프로덕션 코드 금지!**

```java
@Test
void 간단한_테스트_block_사용() {
    User user = userService.findById(1L).block();

    assertThat(user).isNotNull();
    assertThat(user.getName()).isEqualTo("홍길동");
}

@Test
void 리스트_테스트() {
    List<User> users = userService.findAll()
        .collectList()
        .block();

    assertThat(users).hasSize(3);
}
```

### 9.9 테스트 헬퍼 패턴

```java
public class ReactiveTestHelper {

    // 비동기 작업 완료 대기
    public static <T> T awaitResult(Mono<T> mono) {
        return mono.block(Duration.ofSeconds(10));
    }

    // Flux를 리스트로 수집
    public static <T> List<T> awaitResults(Flux<T> flux) {
        return flux.collectList().block(Duration.ofSeconds(10));
    }

    // 에러 검증
    public static void assertError(Mono<?> mono, Class<? extends Throwable> errorType) {
        StepVerifier.create(mono)
            .expectError(errorType)
            .verify(Duration.ofSeconds(5));
    }

    // Publisher를 테스트 구독자로 검증
    public static <T> void assertPublishes(Publisher<T> publisher, T... expected) {
        StepVerifier.create(publisher)
            .expectNext(expected)
            .verifyComplete();
    }
}
```

### 9.10 테스트 전략 요약

| 테스트 대상 | 도구 | 권장 방식 |
|------------|------|----------|
| Mono/Flux 로직 | StepVerifier | 모든 시그널(next, error, complete) 검증 |
| 시간 의존 로직 | withVirtualTime | 가상 시간으로 빠른 테스트 |
| 컨트롤러 | WebTestClient | HTTP 요청/응답 전체 검증 |
| 서비스 로직 | StepVerifier + Mock | 의존성 모킹 후 스트림 검증 |
| 간단한 검증 | block() | 테스트 코드 한정 사용 |

```
테스트 작성 체크리스트:
├─ [ ] 정상 케이스 (happy path)
├─ [ ] 빈 결과 (empty)
├─ [ ] 에러 케이스 (각 예외 타입별)
├─ [ ] 타임아웃 동작
├─ [ ] 취소 시 리소스 정리
└─ [ ] 동시성 (필요시)
```

---

# Part 3: 실무

## 10. 실무 안티패턴

### 10.1 Event Loop에서 블로킹 (가장 치명적!)

WebFlux는 소수의 Event Loop 스레드로 수천 개의 요청을 처리합니다.
**단 하나의 블로킹 호출이 전체 시스템을 마비**시킬 수 있습니다.

```java
// ❌ 치명적: Event Loop 스레드 블로킹
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    // JDBC는 블로킹!
    User user = jdbcTemplate.queryForObject(
        "SELECT * FROM users WHERE id = ?", User.class, id);
    return Mono.just(user);
}

// ❌ 치명적: Thread.sleep
@GetMapping("/slow")
public Mono<String> slow() {
    Thread.sleep(1000);  // Event Loop 블로킹!
    return Mono.just("done");
}

// ❌ 치명적: 동기 HTTP 호출
@GetMapping("/external")
public Mono<Data> callExternal() {
    // RestTemplate은 블로킹!
    Data data = restTemplate.getForObject("http://api.com/data", Data.class);
    return Mono.just(data);
}
```

**증상:**
- 처음 몇 개 요청은 정상 처리
- 갑자기 모든 요청이 타임아웃
- CPU 사용률 낮은데 응답 없음

**해결책:**
```java
// ✅ 블로킹 코드는 boundedElastic으로 격리
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return Mono.fromCallable(() ->
            jdbcTemplate.queryForObject(
                "SELECT * FROM users WHERE id = ?", User.class, id))
        .subscribeOn(Schedulers.boundedElastic());
}

// ✅ 또는 Reactive 라이브러리 사용
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return r2dbcRepository.findById(id);  // R2DBC는 논블로킹
}
```

### 10.2 block() 남용

```java
// ❌ 프로덕션 코드에서 block() 사용
@Service
public class UserService {
    public User getUser(Long id) {
        return userRepository.findById(id).block();  // 절대 금지!
    }

    public void saveUser(User user) {
        userRepository.save(user).block();  // 절대 금지!
    }
}

// ❌ flatMap 내부에서 block()
public Mono<Order> processOrder(OrderRequest request) {
    return Mono.just(request)
        .flatMap(req -> {
            User user = userService.findById(req.getUserId()).block();  // 데드락!
            return createOrder(user, req);
        });
}
```

**block()이 허용되는 유일한 경우:**
```java
// ✅ 테스트 코드
@Test
void testGetUser() {
    User user = userService.findById(1L).block();
    assertThat(user).isNotNull();
}

// ✅ main 메서드 (애플리케이션 초기화)
public static void main(String[] args) {
    Config config = configService.loadConfig().block();
    startApplication(config);
}
```

### 10.3 구독 누락

```java
// ❌ 구독하지 않음 = 실행 안 됨!
@PostMapping("/users")
public ResponseEntity<Void> createUser(@RequestBody UserRequest request) {
    userService.createUser(request);  // Mono 반환, 하지만 구독 안 함!
    return ResponseEntity.ok().build();
}

// ❌ fire-and-forget에서 구독 누락
public void sendNotification(Event event) {
    notificationService.send(event);  // 실행 안 됨!
}
```

**해결책:**
```java
// ✅ 반환하여 프레임워크가 구독하게 함
@PostMapping("/users")
public Mono<ResponseEntity<User>> createUser(@RequestBody UserRequest request) {
    return userService.createUser(request)
        .map(user -> ResponseEntity.created(URI.create("/users/" + user.getId()))
            .body(user));
}

// ✅ fire-and-forget은 명시적 구독 (주의해서 사용)
public void sendNotification(Event event) {
    notificationService.send(event)
        .doOnError(e -> log.error("Notification failed", e))
        .subscribe();  // 에러 처리 필수!
}

// ✅ 더 나은 방법: 호출자가 구독
public Mono<Void> sendNotification(Event event) {
    return notificationService.send(event);
}
```

### 10.4 무한 스트림 메모리 누수

```java
// ❌ 무한 스트림 + collectList = OOM
Flux.interval(Duration.ofMillis(1))
    .collectList()  // 무한히 메모리 증가!
    .subscribe();

// ❌ 무한 스트림 구독 후 취소 안 함
Disposable subscription = Flux.interval(Duration.ofSeconds(1))
    .subscribe(System.out::println);
// subscription.dispose() 호출 안 함 = 영원히 실행

// ❌ 버퍼 제한 없이 사용
fastProducer
    .onBackpressureBuffer()  // 무제한 버퍼 = OOM 위험
    .subscribe(slowConsumer);
```

**해결책:**
```java
// ✅ 종료 조건 명시
Flux.interval(Duration.ofSeconds(1))
    .take(100)  // 100개만
    .collectList()
    .subscribe();

// ✅ 구독 관리
@Component
public class MetricsCollector implements DisposableBean {
    private Disposable subscription;

    @PostConstruct
    public void start() {
        subscription = Flux.interval(Duration.ofSeconds(1))
            .flatMap(tick -> collectMetrics())
            .subscribe();
    }

    @Override
    public void destroy() {
        if (subscription != null) {
            subscription.dispose();  // 정리!
        }
    }
}

// ✅ 버퍼 제한
fastProducer
    .onBackpressureBuffer(1000,
        dropped -> log.warn("Dropped: {}", dropped),
        BufferOverflowStrategy.DROP_OLDEST)
    .subscribe(slowConsumer);
```

### 10.5 Context 전파 실수

```java
// ❌ Context가 전파되지 않음
public Mono<User> getUser(Long id) {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.get("traceId");  // 없을 수 있음!
        return userRepository.findById(id);
    });
}

// 호출 측에서 Context 설정했지만...
getUser(1L)
    .contextWrite(Context.of("traceId", "abc123"))
    .subscribe();

// ❌ publishOn 후 Context 접근 주의
Mono.just("data")
    .publishOn(Schedulers.parallel())
    .flatMap(data -> {
        // Context는 있지만, ThreadLocal 기반 로깅은 안 됨!
        MDC.put("traceId", "...");  // 다른 스레드!
    });
```

**해결책:**
```java
// ✅ Context 안전하게 접근
public Mono<User> getUser(Long id) {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.getOrDefault("traceId", "unknown");
        log.info("TraceId: {}", traceId);
        return userRepository.findById(id);
    });
}

// ✅ MDC 전파를 위한 Hook 설정
@Configuration
public class ReactorMdcConfig {
    @PostConstruct
    public void setupMdcHook() {
        Hooks.onEachOperator(
            "mdc",
            Operators.lift((scannable, subscriber) ->
                new MdcContextSubscriber<>(subscriber))
        );
    }
}

// ✅ Micrometer Context Propagation 사용 (권장)
// Spring Boot 4.0+에서 자동 설정
```

### 10.6 flatMap 내 공유 상태

```java
// ❌ flatMap 내에서 외부 상태 변경 (Race Condition!)
List<User> results = new ArrayList<>();  // 공유 상태

Flux.range(1, 100)
    .flatMap(id -> userRepository.findById(id)
        .doOnNext(user -> results.add(user)))  // 동시 접근!
    .subscribe();

// ❌ AtomicInteger도 위험할 수 있음
AtomicInteger counter = new AtomicInteger();

Flux.range(1, 1000)
    .flatMap(i -> someAsyncOperation(i)
        .doOnNext(result -> {
            int count = counter.incrementAndGet();
            if (count == 1000) {
                // 이 조건이 여러 번 만족될 수 있음!
                processComplete();
            }
        }))
    .subscribe();
```

**해결책:**
```java
// ✅ Reactor 연산자로 수집
Flux.range(1, 100)
    .flatMap(id -> userRepository.findById(id))
    .collectList()  // 스레드 안전하게 수집
    .subscribe(results -> process(results));

// ✅ reduce로 집계
Flux.range(1, 1000)
    .flatMap(i -> someAsyncOperation(i))
    .count()  // 또는 reduce
    .doOnNext(count -> {
        if (count == 1000) {
            processComplete();
        }
    })
    .subscribe();

// ✅ 불변 객체 사용
Flux.range(1, 100)
    .flatMap(id -> userRepository.findById(id))
    .reduce(List.<User>of(), (list, user) -> {
        var newList = new ArrayList<>(list);
        newList.add(user);
        return newList;
    })
    .subscribe();
```

### 10.7 안티패턴 체크리스트

```
코드 리뷰 시 확인 사항:

[ ] Event Loop에서 블로킹 호출 없음
    - JDBC, JPA 사용하면서 boundedElastic 없음?
    - Thread.sleep() 있음?
    - 동기 HTTP 클라이언트(RestTemplate) 사용?

[ ] block() 호출 없음 (테스트 제외)

[ ] 모든 Mono/Flux가 구독됨
    - void 메서드에서 Mono 반환값 무시?

[ ] 무한 스트림에 종료 조건 있음
    - take(), takeUntil(), takeWhile() 사용?
    - Disposable 정리?

[ ] 버퍼에 제한 있음
    - onBackpressureBuffer() 무제한?

[ ] flatMap 내 공유 상태 없음
    - 외부 List, Map 변경?
    - 외부 AtomicXxx 접근?
```

---

## 11. 디버깅

### 11.1 log() 연산자

가장 기본적인 디버깅 도구. 모든 Reactive 시그널을 출력합니다.

```java
Flux.range(1, 3)
    .log()  // 기본 로깅
    .map(i -> i * 2)
    .subscribe();

// 출력:
// INFO  - | onSubscribe([Synchronous Fuseable] FluxRange.RangeSubscription)
// INFO  - | request(unbounded)
// INFO  - | onNext(1)
// INFO  - | onNext(2)
// INFO  - | onNext(3)
// INFO  - | onComplete()
```

```java
// 카테고리 지정
Flux.range(1, 3)
    .log("MyFlux")  // 로거 이름 지정
    .subscribe();

// 특정 시그널만 로깅
Flux.range(1, 3)
    .log("MyFlux", Level.INFO, SignalType.ON_NEXT, SignalType.ON_ERROR)
    .subscribe();

// 체인 중간에 삽입하여 디버깅
userRepository.findById(id)
    .log("after-findById")
    .flatMap(user -> orderRepository.findByUserId(user.getId()))
    .log("after-findOrders")
    .subscribe();
```

### 11.2 checkpoint() 사용법

에러 발생 시 스택트레이스에 위치 정보를 추가합니다.

```java
// ❌ 기본 에러 스택트레이스: 어디서 발생했는지 알기 어려움
Flux.just(1, 2, 0)
    .map(i -> 100 / i)
    .subscribe();
// java.lang.ArithmeticException: / by zero
//     at Flux.map(...)  // 어느 map?

// ✅ checkpoint로 위치 추적
Flux.just(1, 2, 0)
    .map(i -> 100 / i)
    .checkpoint("division-operation")  // 설명 추가
    .subscribe();
// java.lang.ArithmeticException: / by zero
//     ...
// Assembly trace from producer [checkpoint("division-operation")]
```

```java
// 체인 전체에 checkpoint 배치
public Mono<OrderResult> processOrder(OrderRequest request) {
    return validateRequest(request)
        .checkpoint("after-validation")
        .flatMap(this::checkInventory)
        .checkpoint("after-inventory-check")
        .flatMap(this::processPayment)
        .checkpoint("after-payment")
        .flatMap(this::createOrder)
        .checkpoint("after-order-creation");
}
```

### 11.3 Hooks.onOperatorDebug() (개발 환경 전용!)

모든 연산자에 자동으로 스택트레이스를 수집합니다.

```java
// ⚠️ 개발/테스트 환경에서만 사용!
// 성능 오버헤드 매우 큼 (30~300% 성능 저하)

@Configuration
@Profile("dev")  // 개발 환경에서만!
public class ReactorDebugConfig {
    @PostConstruct
    public void enableDebug() {
        Hooks.onOperatorDebug();
    }
}
```

**프로덕션 대안: ReactorDebugAgent**
```groovy
// build.gradle
dependencies {
    runtimeOnly 'io.projectreactor:reactor-tools'
}
```

```java
// main 메서드 최상단에서 호출
public static void main(String[] args) {
    ReactorDebugAgent.init();  // 바이트코드 조작으로 성능 영향 최소화
    SpringApplication.run(Application.class, args);
}
```

### 11.4 BlockHound 설정

블로킹 호출을 런타임에 감지하는 도구.

```groovy
// build.gradle
dependencies {
    testImplementation 'io.projectreactor.tools:blockhound:1.0.9.RELEASE'
}
```

```java
// 테스트에서 사용
@BeforeAll
static void setupBlockHound() {
    BlockHound.install();
}

@Test
void shouldNotBlock() {
    // 블로킹 호출 시 예외 발생!
    Mono.fromCallable(() -> {
        Thread.sleep(100);  // BlockingOperationError!
        return "data";
    }).block();
}
```

```java
// 커스텀 허용 목록
BlockHound.install(builder -> builder
    // 특정 메서드 허용
    .allowBlockingCallsInside(
        "com.example.LegacyService",
        "legacyMethod"
    )
    // 특정 클래스 전체 허용
    .allowBlockingCallsInside(
        "java.util.UUID",
        "randomUUID"
    )
);
```

### 11.5 스택트레이스 읽는 법

Reactive 스택트레이스는 일반적인 것과 다릅니다.

```
// 일반적인 Reactive 에러 스택트레이스
java.lang.RuntimeException: Something went wrong
    at com.example.UserService.lambda$getUser$0(UserService.java:25)
    at reactor.core.publisher.FluxMapFuseable$MapFuseableSubscriber.onNext(...)
    at reactor.core.publisher.FluxFilterFuseable$FilterFuseableSubscriber.onNext(...)
    at reactor.core.publisher.FluxMapFuseable$MapFuseableSubscriber.onNext(...)
    ...
    (100+ lines of Reactor internals)
```

**읽는 방법:**
1. 첫 줄: 실제 예외 타입과 메시지
2. 두 번째 줄: 예외 발생 위치 (`lambda$getUser$0` → getUser 메서드 내 람다)
3. 나머지: Reactor 내부 (대부분 무시 가능)

**디버깅 팁:**
```java
// doOnError로 중간 에러 로깅
userRepository.findById(id)
    .doOnError(e -> log.error("findById failed for id: {}", id, e))
    .flatMap(user -> orderService.getOrders(user.getId()))
    .doOnError(e -> log.error("getOrders failed", e))
    .subscribe();

// 예외에 컨텍스트 추가
userRepository.findById(id)
    .switchIfEmpty(Mono.error(
        new NotFoundException("User not found: " + id)))  // id 포함!
    .flatMap(user -> ...)
```

### 11.6 디버깅 도구 선택 가이드

| 상황 | 도구 |
|------|------|
| 특정 지점 데이터 확인 | `log()` |
| 에러 발생 위치 추적 | `checkpoint()` |
| 개발 중 전체 디버깅 | `Hooks.onOperatorDebug()` |
| 프로덕션 디버깅 | `ReactorDebugAgent` |
| 블로킹 감지 (테스트) | `BlockHound` |
| 중간 상태 로깅 | `doOnNext()`, `doOnError()` |

```java
// 종합 예제: 프로덕션 수준 디버깅
public Mono<Order> createOrder(OrderRequest request) {
    String requestId = UUID.randomUUID().toString();

    return Mono.just(request)
        .doOnSubscribe(s -> log.info("[{}] Order creation started", requestId))
        .flatMap(this::validateRequest)
        .checkpoint("validation")
        .doOnNext(req -> log.debug("[{}] Validation passed", requestId))
        .flatMap(this::processPayment)
        .checkpoint("payment")
        .doOnNext(payment -> log.debug("[{}] Payment: {}", requestId, payment.getId()))
        .flatMap(this::saveOrder)
        .checkpoint("save")
        .doOnSuccess(order ->
            log.info("[{}] Order created: {}", requestId, order.getId()))
        .doOnError(e ->
            log.error("[{}] Order creation failed", requestId, e))
        .contextWrite(Context.of("requestId", requestId));
}
```

---

## 12. 성능 튜닝

### 12.1 커넥션 풀 설정

#### R2DBC 커넥션 풀
```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:pool:postgresql://localhost:5432/db
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      max-life-time: 1h
      max-acquire-time: 5s  # 커넥션 획득 타임아웃
      max-create-connection-time: 10s
```

```java
// 프로그래밍 방식 설정
@Bean
public ConnectionPool connectionPool() {
    ConnectionPoolConfiguration config = ConnectionPoolConfiguration.builder()
        .connectionFactory(connectionFactory)
        .name("r2dbc-pool")
        .initialSize(10)
        .maxSize(50)
        .maxIdleTime(Duration.ofMinutes(30))
        .maxLifeTime(Duration.ofHours(1))
        .maxAcquireTime(Duration.ofSeconds(5))
        .acquireRetry(3)
        .metricsRecorder(new MicrometerPoolMetricsRecorder(meterRegistry, "r2dbc"))
        .build();

    return new ConnectionPool(config);
}
```

#### WebClient 커넥션 풀
```java
@Bean
public WebClient webClient() {
    // HTTP 클라이언트 설정
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .responseTimeout(Duration.ofSeconds(10))
        .connectionProvider(
            ConnectionProvider.builder("custom")
                .maxConnections(500)              // 전체 최대 연결
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .pendingAcquireMaxCount(1000)    // 대기열 크기
                .metrics(true)                    // 메트릭 활성화
                .build()
        );

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
}
```

### 12.2 버퍼 크기 조정

```java
// prefetch: 미리 요청할 요소 수 (기본 256)
Flux.range(1, 10000)
    .flatMap(this::processItem, 256)  // concurrency = 256
    .subscribe();

// limitRate: 청크 단위로 요청
Flux.range(1, 10000)
    .limitRate(100)  // 100개씩 요청
    .subscribe();

// buffer: 청크로 모아서 처리
Flux.range(1, 10000)
    .buffer(100)  // List<Integer> 100개씩
    .flatMap(batch -> processBatch(batch))
    .subscribe();

// bufferTimeout: 개수 또는 시간 기준
eventFlux
    .bufferTimeout(100, Duration.ofMillis(500))  // 100개 또는 500ms마다
    .flatMap(this::processBatch)
    .subscribe();
```

### 12.3 Scheduler 커스터마이징

```java
// 커스텀 Scheduler 생성
Scheduler customScheduler = Schedulers.newBoundedElastic(
    10,                     // 스레드 수
    100000,                 // 작업 큐 크기
    "custom-elastic",       // 스레드 이름 prefix
    60,                     // TTL (초)
    true                    // daemon 스레드 여부
);

// 사용
Mono.fromCallable(() -> blockingOperation())
    .subscribeOn(customScheduler)
    .subscribe();

// 정리
customScheduler.dispose();
```

```java
// parallel Scheduler 커스터마이징
Scheduler customParallel = Schedulers.newParallel(
    "custom-parallel",
    Runtime.getRuntime().availableProcessors() * 2  // 코어 * 2
);

// 특정 작업용 전용 Scheduler
@Bean
public Scheduler paymentScheduler() {
    return Schedulers.newBoundedElastic(
        5,          // 결제 처리 전용 5개 스레드
        1000,
        "payment"
    );
}
```

### 12.4 메모리 프로파일링

```java
// 메모리 누수 의심 시 확인 사항

// 1. Disposable 누수 확인
public class EventProcessor {
    private final List<Disposable> subscriptions = new ArrayList<>();

    public void start() {
        // 구독 추적
        Disposable sub = eventFlux
            .subscribe(this::process);
        subscriptions.add(sub);
    }

    public void stop() {
        // 모든 구독 정리
        subscriptions.forEach(Disposable::dispose);
        subscriptions.clear();
    }
}

// 2. 메모리 사용량 모니터링
Flux.interval(Duration.ofSeconds(10))
    .doOnNext(tick -> {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory used: {} MB", used / 1024 / 1024);
    })
    .subscribe();

// 3. Hooks로 구독 추적
Hooks.onEachOperator("memory-tracker", operator -> {
    // 구독 생성/종료 추적
    return operator;
});
```

### 12.5 메트릭 수집 (Micrometer)

```java
// Reactor 메트릭 활성화
@Configuration
public class ReactorMetricsConfig {
    @PostConstruct
    public void enableMetrics() {
        Schedulers.enableMetrics();  // Scheduler 메트릭
    }
}
```

```java
// 커스텀 메트릭
@Component
public class OrderService {
    private final MeterRegistry meterRegistry;
    private final Counter orderCounter;
    private final Timer orderTimer;

    public OrderService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.orderCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .register(meterRegistry);
        this.orderTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .register(meterRegistry);
    }

    public Mono<Order> createOrder(OrderRequest request) {
        return Mono.just(request)
            .flatMap(this::processOrder)
            .doOnSuccess(order -> orderCounter.increment())
            .metrics()  // 자동 메트릭 (구독, 요청, 에러 등)
            .name("order.creation")
            .tag("type", request.getType())
            .tap(Micrometer.observation(observationRegistry));
    }
}
```

```yaml
# application.yml - Prometheus 설정
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, metrics
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
        r2dbc.pool: true
```

### 12.6 성능 튜닝 체크리스트

```
성능 점검 항목:

[ ] 커넥션 풀 적정 크기
    - R2DBC: 동시 요청 수 고려
    - WebClient: 외부 API 호출 패턴 고려

[ ] Scheduler 적합성
    - CPU 바운드: parallel (코어 수)
    - I/O 바운드: boundedElastic
    - 블로킹 격리: 전용 Scheduler

[ ] 버퍼/Prefetch 조정
    - 메모리 vs 처리량 트레이드오프
    - flatMap concurrency 적정값

[ ] 메트릭 수집
    - 요청/응답 시간
    - 에러율
    - 커넥션 풀 사용률
    - 메모리 사용량

[ ] GC 튜닝
    - G1GC 또는 ZGC 권장
    - 힙 크기 적정화
```

---

## 13. 마이그레이션

### 13.1 점진적 마이그레이션 전략

**Big Bang 접근 (비권장):**
```
MVC 전체 → WebFlux 전체 (리스크 높음)
```

**점진적 접근 (권장):**
```
Phase 1: WebClient 도입 (RestTemplate 대체)
    ↓
Phase 2: 신규 서비스를 WebFlux로 개발
    ↓
Phase 3: 주요 컨트롤러 마이그레이션
    ↓
Phase 4: 데이터 레이어 R2DBC 전환
    ↓
Phase 5: 레거시 정리
```

### 13.2 RestTemplate → WebClient

```java
// Before: RestTemplate (블로킹)
@Service
public class ExternalApiService {
    private final RestTemplate restTemplate;

    public User getUser(Long id) {
        return restTemplate.getForObject(
            "http://api.com/users/{id}",
            User.class,
            id
        );
    }

    public List<Order> getOrders(Long userId) {
        ResponseEntity<List<Order>> response = restTemplate.exchange(
            "http://api.com/users/{userId}/orders",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<Order>>() {},
            userId
        );
        return response.getBody();
    }
}

// After: WebClient (논블로킹)
@Service
public class ExternalApiService {
    private final WebClient webClient;

    public Mono<User> getUser(Long id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(User.class);
    }

    public Flux<Order> getOrders(Long userId) {
        return webClient.get()
            .uri("/users/{userId}/orders", userId)
            .retrieve()
            .bodyToFlux(Order.class);
    }

    // MVC에서 WebClient 사용 시 (과도기)
    public User getUserBlocking(Long id) {
        return getUser(id)
            .timeout(Duration.ofSeconds(10))
            .block();  // MVC에서는 block() 가능 (권장하지 않음)
    }
}
```

### 13.3 MVC Controller → WebFlux Controller

```java
// Before: Spring MVC
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody @Valid UserRequest request) {
        User user = userService.create(request);
        URI location = URI.create("/api/users/" + user.getId());
        return ResponseEntity.created(location).body(user);
    }
}

// After: WebFlux
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<User> getAllUsers() {
        return userService.findAll();  // 스트리밍!
    }

    @PostMapping
    public Mono<ResponseEntity<User>> createUser(
            @RequestBody @Valid Mono<UserRequest> request) {
        return request
            .flatMap(userService::create)
            .map(user -> ResponseEntity
                .created(URI.create("/api/users/" + user.getId()))
                .body(user));
    }
}
```

### 13.4 JPA → R2DBC

```java
// Before: JPA Entity
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders;  // 관계 매핑

    // getters, setters
}

// After: R2DBC Entity (관계 매핑 없음!)
@Table("users")
public class User {
    @Id
    private Long id;

    private String name;
    private String email;

    // R2DBC는 관계 매핑 미지원
    // orders는 별도 쿼리로 조회

    // getters, setters
}
```

```java
// Before: JPA Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByNameContaining(String name);
    Optional<User> findByEmail(String email);
}

// After: R2DBC Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Flux<User> findByNameContaining(String name);
    Mono<User> findByEmail(String email);

    // 커스텀 쿼리
    @Query("SELECT * FROM users WHERE created_at > :since")
    Flux<User> findRecentUsers(@Param("since") LocalDateTime since);
}
```

```java
// 관계 데이터 처리
@Service
public class UserService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    // Before: JPA (Lazy Loading)
    public User getUserWithOrders(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.getOrders().size();  // Lazy 로딩 트리거
        return user;
    }

    // After: R2DBC (명시적 조인)
    public Mono<UserWithOrders> getUserWithOrders(Long id) {
        return userRepository.findById(id)
            .zipWith(orderRepository.findByUserId(id).collectList())
            .map(tuple -> new UserWithOrders(tuple.getT1(), tuple.getT2()));
    }
}
```

### 13.5 Service Layer 마이그레이션

```java
// Before: 동기 서비스
@Service
@Transactional
public class OrderService {

    public Order createOrder(OrderRequest request) {
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));

        if (!inventoryService.checkStock(request.getItems())) {
            throw new InsufficientStockException();
        }

        Order order = new Order(user, request.getItems());
        Order saved = orderRepository.save(order);

        notificationService.sendOrderConfirmation(saved);

        return saved;
    }
}

// After: Reactive 서비스
@Service
public class OrderService {
    private final TransactionalOperator transactionalOperator;

    public Mono<Order> createOrder(OrderRequest request) {
        return userRepository.findById(request.getUserId())
            .switchIfEmpty(Mono.error(new NotFoundException("User not found")))
            .flatMap(user ->
                inventoryService.checkStock(request.getItems())
                    .filter(available -> available)
                    .switchIfEmpty(Mono.error(new InsufficientStockException()))
                    .thenReturn(user))
            .map(user -> new Order(user, request.getItems()))
            .flatMap(orderRepository::save)
            .delayUntil(order -> notificationService.sendOrderConfirmation(order))
            .as(transactionalOperator::transactional);  // 트랜잭션
    }
}
```

### 13.6 공존 기간 관리

```java
// MVC와 WebFlux 공존 설정
@Configuration
public class HybridConfig {

    // MVC용 블로킹 서비스 래퍼
    @Bean
    public UserServiceAdapter userServiceAdapter(ReactiveUserService reactiveService) {
        return new UserServiceAdapter(reactiveService);
    }
}

// 어댑터 패턴: Reactive → Blocking
public class UserServiceAdapter {
    private final ReactiveUserService reactiveService;
    private final Duration timeout = Duration.ofSeconds(10);

    public User findById(Long id) {
        return reactiveService.findById(id)
            .block(timeout);  // MVC에서 사용
    }

    public List<User> findAll() {
        return reactiveService.findAll()
            .collectList()
            .block(timeout);
    }
}

// 점진적 전환 전략
@RestController
public class UserController {
    private final UserServiceAdapter legacyAdapter;  // 레거시 지원
    private final ReactiveUserService reactiveService;  // 신규

    // 레거시 엔드포인트 (유지)
    @GetMapping("/v1/users/{id}")
    public User getUser(@PathVariable Long id) {
        return legacyAdapter.findById(id);
    }

    // 신규 엔드포인트 (Reactive)
    @GetMapping("/v2/users/{id}")
    public Mono<User> getUserReactive(@PathVariable Long id) {
        return reactiveService.findById(id);
    }
}
```

### 13.7 마이그레이션 체크리스트

```
마이그레이션 단계별 확인:

Phase 1: WebClient 도입
[ ] RestTemplate 사용처 파악
[ ] WebClient 빈 설정
[ ] 커넥션 풀 설정
[ ] 타임아웃/재시도 설정
[ ] 기존 테스트 통과 확인

Phase 2: 컨트롤러 마이그레이션
[ ] 반환 타입 Mono/Flux로 변경
[ ] @Valid + Mono 조합 처리
[ ] 예외 핸들러 WebFlux 버전 작성
[ ] WebTestClient 테스트 작성

Phase 3: 서비스 마이그레이션
[ ] 트랜잭션 처리 변경 (TransactionalOperator)
[ ] 예외 처리 패턴 변경
[ ] 이벤트 발행 패턴 변경

Phase 4: 데이터 레이어
[ ] R2DBC 드라이버 추가
[ ] 엔티티 수정 (관계 매핑 제거)
[ ] Repository 인터페이스 변경
[ ] 복잡한 쿼리 재작성

Phase 5: 정리
[ ] 레거시 코드 제거
[ ] 어댑터 제거
[ ] 의존성 정리
[ ] 문서화
```

### 13.8 마이그레이션 시 주의사항

```
⚠️ 흔한 실수:

1. 트랜잭션 혼용
   - JPA @Transactional과 R2DBC TransactionalOperator 혼용 불가
   - 같은 트랜잭션에서 JPA + R2DBC 사용 불가

2. 테스트 환경
   - @DataJpaTest → @DataR2dbcTest
   - TestEntityManager → DatabaseClient
   - 테스트 DB 초기화 방식 변경

3. 성능 기대치
   - WebFlux가 항상 빠른 것은 아님
   - CPU 바운드 작업은 개선 없음
   - 블로킹 레거시가 많으면 오히려 복잡도만 증가

4. 팀 역량
   - Reactive 학습 곡선 고려
   - 코드 리뷰 기준 수립
   - 디버깅 역량 확보
```

---

# Quick Reference

## 자주 쓰는 패턴

```java
// 1. 조건부 실행
Mono.just(value)
    .filter(v -> v != null)
    .switchIfEmpty(Mono.error(new NotFoundException()));

// 2. 첫 번째 성공 값
Mono.firstWithValue(
    cacheRepository.find(id),
    dbRepository.findById(id)
);

// 3. 타임아웃
externalApi.call()
    .timeout(Duration.ofSeconds(5))
    .onErrorResume(TimeoutException.class, e -> fallback());

// 4. 병렬 실행 후 결합
Mono.zip(
    serviceA.call().subscribeOn(Schedulers.parallel()),
    serviceB.call().subscribeOn(Schedulers.parallel()),
    (a, b) -> combine(a, b)
);

// 5. 리소스 정리
Flux.using(
    () -> openConnection(),           // 리소스 생성
    conn -> queryData(conn),          // 사용
    conn -> conn.close()              // 정리
);
```

## 체크리스트: 내 코드 점검

- [ ] `block()` 호출이 없는가? (테스트 제외)
- [ ] 모든 Mono/Flux가 구독되는가?
- [ ] 블로킹 코드는 `boundedElastic`으로 격리했는가?
- [ ] 무한 Flux에 `take()` 또는 종료 조건이 있는가?
- [ ] 에러 처리가 되어 있는가?
- [ ] 타임아웃이 설정되어 있는가?

---

## 다음 학습 요청 예시

```
"7번 Virtual Thread vs WebFlux 자세히 설명해줘"
"10번 실무 안티패턴 상세 내용 부탁해"
"에러 처리 패턴 코드 예제 포함해서 자세히"
```
