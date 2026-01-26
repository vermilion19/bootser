# Firstcome-Firstserved (선착순 주문 시스템)

WebFlux 기반의 고성능 선착순 주문 처리 시스템입니다.

## 핵심 특징

- **Non-Blocking I/O**: Spring WebFlux + Reactor
- **원자적 재고 관리**: Redis Lua Script
- **비동기 DB 저장**: In-Memory Event Bus + Batch Insert
- **DDD + Hexagonal Architecture**

---

## 디렉토리 구조 (DDD)

```
firstcome-firstserved/
├── config/                          # 설정
│   ├── FaviconFilter.java           # favicon.ico 404 방지
│   └── RedisConfig.java             # Redis 설정
│
├── order/                           # Order 도메인
│   ├── domain/                      # 도메인 레이어 (순수 비즈니스)
│   │   ├── OrderStatus.java         # Value Object (상태)
│   │   ├── event/
│   │   │   └── OrderCreatedEvent.java   # 도메인 이벤트
│   │   ├── exception/
│   │   │   └── OrderErrorCode.java      # 도메인 에러코드
│   │   └── port/
│   │       └── StockPort.java           # 포트 인터페이스 (추상화)
│   │
│   ├── application/                 # 애플리케이션 레이어
│   │   ├── OrderService.java        # 주문 유스케이스
│   │   ├── OrderEventPublisher.java # 이벤트 발행
│   │   └── OrderEventWorker.java    # 이벤트 소비 (DB 저장)
│   │
│   ├── infrastructure/              # 인프라 레이어
│   │   ├── adapter/
│   │   │   └── RedisStockAdapter.java   # Redis 어댑터 (Port 구현)
│   │   └── persistence/
│   │       ├── OrderEntity.java         # R2DBC 엔티티
│   │       └── OrderRepository.java     # Repository
│   │
│   └── web/                         # 표현 레이어
│       ├── OrderController.java     # REST Controller
│       └── dto/
│           ├── OrderRequest.java
│           └── OrderResponse.java
│
└── FirstcomeFirstservedApplication.java
```

### 레이어별 의존성 규칙

```
web → application → domain ← infrastructure
                      ↑
              (Port 인터페이스)
```

- **domain**: 순수 비즈니스 로직, 외부 의존성 없음
- **application**: domain 사용, 유스케이스 조율
- **infrastructure**: domain의 Port 인터페이스 구현
- **web**: HTTP 요청/응답 처리

---

## 전체 흐름도

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────>│  Controller │────>│   Service   │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    │                          │                          │
                    ▼                          ▼                          ▼
            ┌───────────────┐         ┌───────────────┐         ┌───────────────┐
            │     Redis     │         │    Event      │         │   Response    │
            │  (재고 차감)   │         │   Publisher   │         │   (202 OK)    │
            └───────────────┘         └───────┬───────┘         └───────────────┘
                                              │
                                              ▼
                                      ┌───────────────┐
                                      │    Event      │
                                      │    Worker     │
                                      └───────┬───────┘
                                              │
                                              ▼
                                      ┌───────────────┐
                                      │   Database    │
                                      │ (Batch Insert)│
                                      └───────────────┘
```

---

## 상세 코드 분석

### 1. OrderController - HTTP 요청 처리

```java
@PostMapping
public Mono<ResponseEntity<ApiResponse<OrderResponse>>> createOrder(
        @Valid @RequestBody Mono<OrderRequest> requestMono  // ← Non-Blocking 요청 읽기
) {
    return requestMono
            .flatMap(orderService::processOrder)  // ← flatMap 사용
            .map(response -> ResponseEntity...);  // ← map 사용
}
```

#### flatMap vs map 사용 기준

| 코드 | 반환 타입 | 사용 연산자 |
|------|----------|------------|
| `orderService.processOrder()` | `Mono<OrderResponse>` | **flatMap** |
| `ResponseEntity.ok(...)` | `ResponseEntity<T>` | **map** |

**규칙**: 반환값이 `Mono/Flux`면 `flatMap`, 일반 객체면 `map`

```java
// ❌ map으로 Mono 반환 시 중첩 발생
requestMono.map(req -> orderService.processOrder(req))
// 결과: Mono<Mono<OrderResponse>>

// ✅ flatMap은 평탄화(flatten)
requestMono.flatMap(req -> orderService.processOrder(req))
// 결과: Mono<OrderResponse>
```

---

### 2. OrderService - 비즈니스 로직

```java
public Mono<OrderResponse> processOrder(OrderRequest request) {
    String orderId = UUID.randomUUID().toString();

    return stockPort.decrease(request.itemId(), request.quantity())  // 1. Redis 재고 차감
            .flatMap(isSuccess -> {
                if (!isSuccess) {
                    return Mono.error(new CoreException(OrderErrorCode.SOLD_OUT));  // 2. 실패 시 에러
                }

                eventPublisher.publish(OrderCreatedEvent.of(...));  // 3. 이벤트 발행 (Fire & Forget)

                return Mono.just(OrderResponse.of(...));  // 4. 즉시 응답
            });
}
```

#### 왜 flatMap인가?

```java
stockPort.decrease()  →  Mono<Boolean>
    │
    └── flatMap 내부:
            Mono.error()  →  Mono<T>      // flatMap 필요
            Mono.just()   →  Mono<T>      // flatMap 필요
```

람다 내부에서 `Mono`를 반환하므로 `flatMap` 필수.

---

### 3. RedisStockAdapter - 원자적 재고 차감

```java
public Mono<Boolean> decrease(Long itemId, int quantity) {
    return redisTemplate.execute(decreaseStockScript, List.of(key), List.of(String.valueOf(quantity)))
            .next()    // Flux<Long> → Mono<Long>
            .map(result -> result >= 0);  // Long → Boolean (동기 변환)
}
```

#### Lua Script (`decrease_stock.lua`)

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock >= tonumber(ARGV[1]) then
    return redis.call('DECRBY', KEYS[1], ARGV[1])
else
    return -1
end
```

- **원자적 실행**: GET + DECRBY가 하나의 트랜잭션
- **경쟁 조건 방지**: 동시 요청에도 재고 정합성 보장

---

### 4. OrderEventPublisher - 이벤트 발행

```java
private final Sinks.Many<OrderCreatedEvent> sink =
        Sinks.many().multicast().onBackpressureBuffer();

public void publish(OrderCreatedEvent event) {
    sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(3)));
}
```

#### Sinks.Many 특성

| 타입 | 특징 |
|------|------|
| `unicast()` | 단일 구독자만 허용 |
| `multicast()` | 여러 구독자 허용, **구독 시점부터** 수신 |
| `replay()` | 여러 구독자 허용, **과거 이벤트도** 버퍼링하여 재생 |

---

### 5. OrderEventWorker - 비동기 DB 저장

```java
@PostConstruct
public void init() {
    eventPublisher.asFlux()
            .bufferTimeout(10, Duration.ofMillis(100))  // 배치 그룹핑
            .publishOn(Schedulers.boundedElastic())     // 별도 스레드풀
            .flatMap(eventList -> {
                var entities = eventList.stream()
                        .map(this::toEntity)
                        .toList();

                return orderRepository.saveAll(entities)  // Batch Insert
                        .collectList();
            })
            .onErrorContinue((error, obj) -> log.error(...))  // 에러 시 계속 진행
            .subscribe();  // 구독 시작
}
```

#### bufferTimeout 동작

```
이벤트 도착: [E1] [E2] [E3] ... [E10]  →  100ms 전에 10개 모임
                    │
                    ▼
              [E1~E10] 배치로 묶어서 DB Insert (1회)

이벤트 도착: [E1] [E2] [E3]  →  100ms 경과
                    │
                    ▼
              [E1~E3] 배치로 묶어서 DB Insert (1회)
```

**장점**: DB Insert 쿼리 횟수를 1/10로 줄임

---

## Reactive Operators 정리

### flatMap vs map

```java
// map: T → R (동기 변환)
Mono<String>.map(s -> s.length())  →  Mono<Integer>

// flatMap: T → Mono<R> (비동기 변환, 평탄화)
Mono<String>.flatMap(s -> Mono.just(s.length()))  →  Mono<Integer>
```

### 자주 쓰는 연산자

| 연산자 | 용도 | 예시 |
|--------|------|------|
| `map` | 동기 변환 | `mono.map(x -> x + 1)` |
| `flatMap` | 비동기 변환 | `mono.flatMap(x -> repository.find(x))` |
| `switchIfEmpty` | 빈 값 대체 | `mono.switchIfEmpty(Mono.error(...))` |
| `defaultIfEmpty` | 빈 값 기본값 | `mono.defaultIfEmpty(0L)` |
| `doOnNext` | 사이드 이펙트 | `mono.doOnNext(x -> log.info(...))` |
| `onErrorResume` | 에러 복구 | `mono.onErrorResume(e -> Mono.just(default))` |
| `onErrorContinue` | 에러 무시 후 계속 | Flux에서 일부 실패해도 계속 진행 |

---

## API 명세

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/orders` | 주문 생성 |
| GET | `/api/v1/orders/count` | 주문 건수 조회 |
| GET | `/api/v1/orders/stock/{itemId}` | 재고 조회 |

### 요청/응답 예시

```bash
# 주문 생성
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "itemId": 1, "quantity": 1}'

# 응답 (202 Accepted)
{
  "result": "SUCCESS",
  "data": {
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "message": "주문이 접수되었습니다."
  },
  "message": null,
  "errorCode": null
}
```

---

## 성능 테스트 (k6)

```bash
k6 run order-test.js
```

### 테스트 시나리오

1. **Ramp-up**: 5초간 0 → 500 유저
2. **Peak**: 10초간 1000 유저 유지
3. **Ramp-down**: 5초간 1000 → 0 유저

### 성공 지표

- 95% 요청이 500ms 이내 처리
- Redis 재고와 DB 주문 건수 일치

---

## 의존성

```groovy
dependencies {
    implementation project(':libs:core-webflux')  // 공통 WebFlux 모듈
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

    runtimeOnly 'io.r2dbc:r2dbc-h2'
    runtimeOnly 'com.h2database:h2'
}
```
