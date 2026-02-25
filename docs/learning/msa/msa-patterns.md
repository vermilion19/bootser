# MSA 심화 패턴 정리

## 1. MSA의 핵심 과제

모놀리식에서 MSA로 전환하면 생기는 근본적인 문제들.

```
모놀리식                         MSA
┌──────────────────┐            ┌──────────┐ ┌──────────┐ ┌──────────┐
│                  │            │ Order    │ │ Payment  │ │ Waiting  │
│  Order + Payment │   →        │ Service  │ │ Service  │ │ Service  │
│  + Waiting ...   │            └────┬─────┘ └────┬─────┘ └────┬─────┘
│                  │                 │              │              │
│  단일 DB 트랜잭션│            각자 DB    ←── 네트워크 통신 ──→
└──────────────────┘
      ACID 보장                  분산 트랜잭션 문제 발생
```

### MSA의 핵심 과제

| 과제 | 설명 | 해결 패턴 |
|------|------|-----------|
| **분산 트랜잭션** | 여러 서비스의 데이터 정합성 | Saga 패턴 |
| **데이터 조회 성능** | 여러 서비스 데이터를 합쳐야 할 때 | CQRS |
| **이벤트 발행 보장** | 트랜잭션 커밋 후 이벤트 유실 | Outbox 패턴 |
| **서비스 간 장애 전파** | 하나의 서비스 장애가 연쇄 실패 | Circuit Breaker |
| **결과적 일관성** | 즉각 일관성 포기, 최종 일관성 보장 | Eventual Consistency |

---

## 2. Saga 패턴

분산 환경에서 **여러 서비스에 걸친 트랜잭션을 관리**하는 패턴.
각 서비스의 로컬 트랜잭션을 순서대로 실행하고, 실패 시 보상 트랜잭션(Compensating Transaction)으로 롤백.

### 2-1. Choreography Saga (안무 방식)

서비스들이 **이벤트로 서로 반응**. 중앙 조율자 없음.

```
OrderService       PaymentService      InventoryService
    │                    │                    │
    │ OrderCreated ──────►│                    │
    │                    │ PaymentCompleted ──►│
    │                    │                    │ InventoryReserved
    │◄───────────────────────────────────────│
    │ OrderConfirmed                          │
    │                    │                    │
    │                    │    [결제 실패 시]   │
    │                    │ PaymentFailed ─────►│
    │◄───────────────────────────────────────│
    │ OrderCancelled (보상 트랜잭션)          │
```

```java
// OrderService: 주문 생성 후 이벤트 발행
@Transactional
public Order createOrder(CreateOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);

    // Outbox에 이벤트 저장 (트랜잭션 보장)
    outboxRepository.save(OutboxEvent.of("OrderCreated", order));
    return order;
}

// PaymentService: OrderCreated 이벤트 구독
@KafkaListener(topics = "order-events")
public void onOrderCreated(OrderCreatedEvent event) {
    try {
        paymentService.processPayment(event.getOrderId());
        kafkaProducer.send("payment-events", new PaymentCompletedEvent(...));
    } catch (Exception e) {
        kafkaProducer.send("payment-events", new PaymentFailedEvent(...));
    }
}
```

**장점**: 서비스 간 결합도 낮음, 중앙 조율자 없어 단일 장애점 없음
**단점**: 전체 흐름 파악 어려움, 디버깅/모니터링 복잡

### 2-2. Orchestration Saga (오케스트라 방식)

**중앙 오케스트레이터**가 각 서비스에 직접 명령.

```
              Saga Orchestrator
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
  OrderService PaymentService InventoryService
        │           │           │
        │←──────────────────────┘
        │ 각 서비스 결과를 오케스트레이터가 수집
        │ 실패 시 보상 명령 직접 발행
```

```java
@Service
public class OrderSagaOrchestrator {

    public void execute(CreateOrderCommand command) {
        // Step 1: 주문 생성
        OrderCreatedResult orderResult = orderService.createOrder(command);
        if (orderResult.isFailed()) {
            return; // 보상 없음 (첫 단계 실패)
        }

        // Step 2: 결제
        PaymentResult paymentResult = paymentService.pay(orderResult.getOrderId());
        if (paymentResult.isFailed()) {
            orderService.cancelOrder(orderResult.getOrderId()); // 보상
            return;
        }

        // Step 3: 재고 차감
        InventoryResult inventoryResult = inventoryService.reserve(orderResult.getOrderId());
        if (inventoryResult.isFailed()) {
            paymentService.refund(paymentResult.getPaymentId()); // 보상
            orderService.cancelOrder(orderResult.getOrderId()); // 보상
            return;
        }

        orderService.confirmOrder(orderResult.getOrderId());
    }
}
```

**장점**: 전체 흐름이 한 곳에 집중, 디버깅 쉬움
**단점**: 오케스트레이터가 단일 장애점, 서비스 결합도 증가

### Choreography vs Orchestration 선택 기준

| 상황 | 권장 방식 |
|------|-----------|
| 단순한 흐름 (2~3 단계) | Choreography |
| 복잡한 흐름 (조건 분기, 병렬 처리) | Orchestration |
| 서비스 간 결합 최소화 우선 | Choreography |
| 흐름 추적/모니터링이 중요 | Orchestration |

---

## 3. CQRS (Command Query Responsibility Segregation)

**쓰기(Command)와 읽기(Query) 모델을 분리**하는 패턴.

### 왜 필요한가?

```
// 일반적인 문제 상황
// "최근 7일간 사용자별 주문 내역과 결제 금액 합계를 보여줘"
// → OrderService + PaymentService + UserService 데이터를 JOIN해야 함
// → 서비스 간 API 호출 N번, 성능 문제, 서비스 결합도 증가

// CQRS 해결책: 별도 읽기 모델(Read Model)에 미리 집계해 저장
```

### CQRS 구조

```
                    Command Side                 Query Side
                    (쓰기 모델)                  (읽기 모델)

클라이언트 ──►  CommandHandler                QueryHandler ◄── 클라이언트
                    │                               │
                    ▼                               ▼
              Write DB (정규화)             Read DB (비정규화)
              OrderTable                    OrderSummaryView
              PaymentTable                  (JOIN 결과 미리 저장)
              UserTable
                    │
                    │ 이벤트 발행 (OrderCreated 등)
                    ▼
              Event Handler ──────────────► Read DB 업데이트
```

```java
// Command 모델: 정합성 우선, 정규화된 도메인 모델
@Transactional
public void createOrder(CreateOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);
    eventPublisher.publish(new OrderCreatedEvent(order)); // 이벤트 발행
}

// Query 모델: 성능 우선, 비정규화된 뷰 모델
@EventListener
public void onOrderCreated(OrderCreatedEvent event) {
    // 읽기용 DB에 집계 데이터 미리 저장
    OrderSummaryView view = OrderSummaryView.builder()
        .orderId(event.getOrderId())
        .userId(event.getUserId())
        .restaurantName(event.getRestaurantName())
        .totalAmount(event.getTotalAmount())
        .build();
    orderSummaryRepository.save(view);
}

// 조회: 복잡한 JOIN 없이 단순 조회
public List<OrderSummaryView> getOrderHistory(Long userId) {
    return orderSummaryRepository.findByUserId(userId);
}
```

### CQRS 주의사항

- **Read Model 지연**: 이벤트 처리 전까지 Read Model이 최신 상태가 아닐 수 있음 (Eventual Consistency)
- **복잡성 증가**: 단순한 서비스에는 과도한 설계
- **적용 기준**: 읽기/쓰기 비율 불균형이 심할 때, 복잡한 조회 쿼리가 많을 때

---

## 4. Outbox 패턴 (Transactional Outbox)

**"DB 저장 성공 = 이벤트 발행 보장"** 을 위한 패턴.

### 왜 필요한가?

```
// 이벤트 발행 실패 시나리오
@Transactional
public void createOrder(CreateOrderCommand command) {
    orderRepository.save(order);         // DB 저장 성공
    kafkaProducer.send("order-events", event); // 네트워크 오류로 실패!
    // → DB에는 저장됐지만 다른 서비스는 모름 → 데이터 불일치
}
```

### Outbox 패턴 구조

```
같은 트랜잭션 내에서
┌─────────────────────────────────┐
│  orderRepository.save(order)    │  ← 주문 저장
│  outboxRepository.save(event)   │  ← 이벤트도 같은 DB에 저장
└─────────────────────────────────┘
              ↓ 커밋 (둘 다 성공 or 둘 다 실패)

OutboxMessageRelay (별도 스케줄러)
    └─ Outbox 테이블 polling
    └─ Kafka로 발행
    └─ 발행 완료 표시
```

```java
// 1. 트랜잭션 내에서 Outbox에 이벤트 저장
@Transactional
public Waiting registerWaiting(RegisterWaitingCommand command) {
    Waiting waiting = Waiting.create(command);
    waitingRepository.save(waiting);

    outboxRepository.save(OutboxEvent.builder()
        .aggregateType("WAITING")
        .aggregateId(waiting.getId())
        .eventType("REGISTERED")
        .payload(objectMapper.writeValueAsString(waiting))
        .status(OutboxStatus.PENDING)
        .build());

    return waiting;
}

// 2. 스케줄러가 주기적으로 Outbox polling 후 Kafka 발행
@Scheduled(fixedDelay = 3000)
@ShedLock(name = "outbox-relay", lockAtMostFor = "PT10S") // 분산 락으로 중복 실행 방지
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository.findByStatus(OutboxStatus.PENDING);
    events.forEach(event -> {
        kafkaProducer.send(event.getTopic(), event.getPayload());
        event.markAsPublished();
    });
}
```

### At-least-once & 멱등성

Outbox 패턴은 **At-least-once** 보장 (네트워크 오류 시 재발행 가능).
소비자는 **멱등성(Idempotency)** 을 보장해야 한다.

```java
// Consumer 멱등성 처리 예시
@KafkaListener(topics = "waiting-events")
public void handleWaitingRegistered(WaitingRegisteredEvent event) {
    // 이미 처리한 이벤트면 스킵 (eventId로 중복 체크)
    if (processedEventRepository.exists(event.getEventId())) {
        return;
    }

    notificationService.sendWaitingConfirmation(event);
    processedEventRepository.save(event.getEventId());
}
```

---

## 5. Eventual Consistency (결과적 일관성)

MSA에서 **즉각적인 일관성(Strong Consistency)을 포기**하고, 시간이 지나면 일관성이 맞춰지는 것을 허용.

### CAP 정리

분산 시스템에서 동시에 3가지를 모두 보장할 수 없다.

| 속성 | 설명 |
|------|------|
| **C**onsistency (일관성) | 모든 노드가 항상 같은 데이터를 반환 |
| **A**vailability (가용성) | 모든 요청에 응답 (실패 없이) |
| **P**artition Tolerance (분단 허용) | 네트워크 분단이 발생해도 동작 |

> MSA는 네트워크 기반이므로 P는 포기 불가. **CP(일관성 우선)** 또는 **AP(가용성 우선)** 선택.

### 보상(Compensating Transaction)과 사가

```
[성공 흐름]
OrderCreated → PaymentCompleted → InventoryReserved → OrderConfirmed

[실패 흐름 - 재고 부족]
OrderCreated → PaymentCompleted → InventoryFailed
                                        ↓
                              PaymentRefunded (보상)
                                        ↓
                              OrderCancelled (보상)
```

보상 트랜잭션 원칙:
- 보상은 **비즈니스 수준**에서 처리 (DELETE가 아닌 상태 변경)
- 보상 자체도 실패할 수 있으므로 재시도 가능하게 설계 (멱등성)

### Self-Healing 패턴

Redis 등 일시적 저장소 유실 시 DB에서 복구하는 패턴.

```java
// Booster 프로젝트 예시: Redis 대기열 유실 시 DB에서 복구
@Scheduled(fixedDelay = 60000)
public void healRedisFromDatabase() {
    if (redisHealthCheck.isDown()) {
        List<Waiting> activeWaitings = waitingRepository.findAllActive();
        activeWaitings.forEach(waiting ->
            redisQueue.add(waiting.getId(), waiting.getCreatedAt().toEpochSecond())
        );
        log.info("[SELF-HEAL] Redis 대기열 DB에서 복구 완료: {}건", activeWaitings.size());
    }
}
```

---

## 6. Circuit Breaker

서비스 A가 서비스 B를 호출할 때, **B가 계속 실패하면 빠르게 실패 처리(Fail Fast)** 하여 연쇄 장애를 방지.

### 상태 전이

```
         실패율 임계값 초과
CLOSED ──────────────────────► OPEN
(정상 호출)                   (즉시 실패 반환)
    ▲                              │
    │          대기 시간 경과      │
    │   ◄──────────────────── HALF-OPEN
    │                         (일부 요청 시도)
    │ 성공                         │ 실패
    └──────────────────────────────┘
```

```java
// Resilience4j 설정 (Booster 프로젝트 패턴)
@CircuitBreaker(name = "restaurant-service", fallbackMethod = "fallback")
public RestaurantInfo getRestaurantInfo(Long restaurantId) {
    return restaurantClient.getInfo(restaurantId);
}

// Fallback: 서킷 열렸을 때 대체 응답
public RestaurantInfo fallback(Long restaurantId, Exception e) {
    log.warn("[CIRCUIT OPEN] restaurant-service 호출 실패: {}", e.getMessage());
    return RestaurantInfo.defaultInfo(restaurantId); // 캐시된 기본값 반환
}
```

```yaml
# application.yml CircuitBreaker 설정
resilience4j:
  circuitbreaker:
    instances:
      restaurant-service:
        sliding-window-size: 10          # 최근 10회 호출 기준
        failure-rate-threshold: 50       # 50% 실패 시 OPEN
        wait-duration-in-open-state: 10s # 10초 후 HALF-OPEN
        permitted-calls-in-half-open: 3  # HALF-OPEN에서 3회 시도
```

### Bulkhead (격벽 패턴)

서비스별로 **스레드 풀을 분리**하여 하나의 서비스 장애가 다른 서비스에 영향을 주지 않도록.

```
일반 방식:
모든 서비스 호출 → 공유 스레드 풀 → restaurant-service 느려짐 → 전체 스레드 소진 → 전체 장애

Bulkhead 적용:
restaurant-service 호출 → 전용 스레드 풀 (10개)
payment-service 호출    → 전용 스레드 풀 (5개)
notification 호출       → 전용 스레드 풀 (3개)
→ restaurant-service 장애가 다른 서비스에 영향 없음
```

---

## 7. API Gateway 패턴

클라이언트와 내부 서비스 사이의 **단일 진입점**.

```
클라이언트
    │
    ▼
API Gateway (Spring Cloud Gateway)
    │  ① 인증/인가 (JWT 검증)
    │  ② 라우팅 (URL → 서비스 매핑)
    │  ③ 로드밸런싱 (Eureka 연동)
    │  ④ Rate Limiting (요청 수 제한)
    │  ⑤ 서킷브레이커
    │
    ├──► Waiting Service  :8080
    ├──► Restaurant Service :8081
    ├──► Auth Service      :8082
    └──► Notification Service :8083
```

```yaml
# Spring Cloud Gateway 라우팅 설정
spring:
  cloud:
    gateway:
      routes:
        - id: waiting-service
          uri: lb://waiting-service   # Eureka 로드밸런싱
          predicates:
            - Path=/api/waitings/**
          filters:
            - AuthenticationFilter    # JWT 검증 필터
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10  # 초당 10개
                redis-rate-limiter.burstCapacity: 20  # 순간 최대 20개
```

---

## 8. Service Discovery (서비스 디스커버리)

MSA에서 서비스의 IP/포트는 동적으로 변한다 (스케일 아웃, 재시작).
**서비스 레지스트리**에 위치를 등록하고 동적으로 탐색.

```
[서비스 등록]
Waiting Service 시작 → Eureka Server에 "waiting-service: 192.168.1.10:8080" 등록
Waiting Service 시작 → Eureka Server에 "waiting-service: 192.168.1.11:8080" 등록

[서비스 탐색]
API Gateway → Eureka에 "waiting-service 어디 있어?" 요청
           ← [192.168.1.10:8080, 192.168.1.11:8080] 반환
           → 로드밸런서로 하나 선택해 요청
```

### Heartbeat & 장애 감지

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30  # 30초마다 Heartbeat
    lease-expiration-duration-in-seconds: 90 # 90초 Heartbeat 없으면 제거
  client:
    registry-fetch-interval-seconds: 30    # 30초마다 레지스트리 갱신
```

---

## 9. 면접 Q&A

**Q. MSA에서 분산 트랜잭션을 2PC(Two-Phase Commit)로 해결하면 안 되나?**
> 이론적으로 가능하지만 실무에서는 지양한다. 2PC는 코디네이터가 단일 장애점이 되고, 모든 참여 서비스가 락을 보유하는 동안 블로킹이 발생해 성능이 급격히 저하된다. MSA의 목적인 서비스 독립성과 가용성에 반한다. 대신 Saga + Eventual Consistency로 설계한다.

**Q. Saga 패턴에서 보상 트랜잭션이 실패하면?**
> 보상 트랜잭션 자체도 멱등성 있게 설계하고 재시도한다. Dead Letter Queue(DLQ)에 실패한 이벤트를 보관하고, 알람을 발생시켜 수동 처리하거나 별도 보상 프로세스를 실행한다. 완벽한 자동화보다 "실패 감지 + 재시도 + 수동 처리 경로" 확보가 중요하다.

**Q. CQRS를 적용하면 Read Model이 항상 최신이 아닌데 괜찮나?**
> 서비스 성격에 따라 다르다. 주문 내역 조회, 통계 대시보드처럼 약간의 지연이 허용되는 경우엔 괜찮다. 잔액 조회처럼 즉각적인 정확성이 필요한 경우엔 Command Side를 직접 조회하거나 Strong Consistency가 필요한 경로를 별도로 둔다.

**Q. Outbox 패턴의 Polling이 DB에 부담을 주지 않나?**
> Outbox 테이블에 `status`와 `created_at`에 인덱스를 걸고, 한 번에 처리할 배치 크기를 제한하면 부담이 크지 않다. 더 나아가면 PostgreSQL의 LISTEN/NOTIFY나 Debezium 같은 CDC(Change Data Capture) 도구로 Polling을 이벤트 방식으로 전환할 수 있다.

**Q. Circuit Breaker의 Fallback에서 뭘 반환해야 하나?**
> ① 캐시된 이전 응답 ② 기본값(Default Value) ③ 빈 응답 + 사용자 안내 메시지 중 비즈니스 요구사항에 맞게 선택. 중요한 건 Fallback이 또 다른 외부 호출을 하지 않는 것. Fallback이 느리면 Circuit Breaker의 의미가 없다.

**Q. 서비스 간 통신을 동기(Feign)와 비동기(Kafka)로 어떻게 나누나?**
> 응답이 즉시 필요한 경우(재고 확인 후 주문 진행)는 동기(Feign). 응답이 즉시 필요하지 않은 경우(주문 완료 후 알림 발송)는 비동기(Kafka). 동기 통신은 호출 체인이 길어질수록 장애 전파 위험이 커지므로 Circuit Breaker와 함께 사용한다.
