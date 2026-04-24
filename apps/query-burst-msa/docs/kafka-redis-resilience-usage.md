# query-burst-msa Kafka, Redis, Resilience 적용 정리

## 요약

| 기술 | 적용 서비스 | 역할 |
|---|---|---|
| Kafka | `order-service`, `analytics-service`, `ranking-service` | 주문 이벤트 비동기 전파, Read Model 갱신 |
| Redis | `ranking-service` | 실시간 랭킹 저장, Kafka Consumer 멱등성 키 저장 |
| Resilience | `order-service` | `catalog-service` HTTP 호출 보호용 Circuit Breaker + TimeLimiter |

## 서비스별 한눈에 보기

| 서비스 | Kafka | Redis | Resilience |
|---|---|---|---|
| `order-service` | Producer | 사용 안 함 | 사용 |
| `analytics-service` | Consumer | 사용 안 함 | 사용 안 함 |
| `ranking-service` | Consumer | 사용 | 사용 안 함 |
| `catalog-service` | 사용 안 함 | 사용 안 함 | 사용 안 함 |
| `member-service` | 사용 안 함 | 사용 안 함 | 사용 안 함 |

## 1. Kafka 적용 위치

### 1-1. `order-service`: Outbox 기반 Producer

`order-service`는 주문 상태 변경 시 바로 Kafka에 쓰지 않고, 먼저 Outbox 테이블에 이벤트를 저장한 뒤 스케줄러가 Kafka로 중계합니다.

- 주문 생성 시 `catalogServiceClient.reserve(...)` 호출 후 주문 저장, 그리고 `appendOutboxEvent(...)`로 `ORDER_CREATED` 이벤트를 저장
- 결제/배송/취소 시에도 동일하게 Outbox 이벤트를 추가
- 별도 스케줄러 `OrderOutboxRelay`가 `PENDING` 이벤트를 조회해 `order-events` 토픽으로 발행

근거:

- `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/application/OrderApplicationService.java`
  - `createOrder()`에서 Outbox 저장: 59-84
  - `pay()/ship()/deliver()/cancel()`에서 상태 변경 후 Outbox 저장: 87-122
  - `appendOutboxEvent()`에서 `outboxEventRepository.save(...)`: 125-140
- `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/event/OrderOutboxRelay.java`
  - `@Scheduled(fixedDelay = 3000)`: 28
  - `kafkaTemplate.send(KafkaTopic.ORDER_EVENTS.getTopic(), ...)`: 34
  - 발행 성공 시 `markPublished`, 실패 시 `markFailed`: 35-37

정리하면 Kafka는 `order-service`에서 "주문 이벤트를 외부로 내보내는 비동기 이벤트 버스" 역할입니다.

### 1-2. `analytics-service`: `order-events` Consumer

`analytics-service`는 Kafka `order-events` 토픽을 구독해서 일별 매출/상품 매출 Read Model을 PostgreSQL에 반영합니다.

- `@KafkaListener(topics = "order-events", groupId = "query-burst-msa-analytics")`
- 수신한 이벤트를 `AnalyticsService.apply(...)`로 전달
- `ORDER_CREATED`, `ORDER_CANCELED`만 반영
- 이미 처리한 이벤트는 `ProcessedOrderEvent` 테이블로 중복 방지

근거:

- `apps/query-burst-msa/analytics-service/src/main/java/com/booster/queryburstmsa/analytics/event/AnalyticsOrderEventConsumer.java`
  - Kafka Listener 등록: 17-19
- `apps/query-burst-msa/analytics-service/src/main/java/com/booster/queryburstmsa/analytics/application/AnalyticsService.java`
  - 이벤트 타입 필터: 58-61
  - 중복 체크 `existsByEventKey(...)`: 63-66
  - 집계 반영 후 처리 이력 저장: 71-83

정리하면 Kafka는 `analytics-service`에서 "주문 이벤트를 받아 분석용 Read Model을 갱신하는 입력 채널"입니다.

### 1-3. `ranking-service`: `order-events` Consumer

`ranking-service`도 같은 `order-events` 토픽을 구독하지만, 결과를 PostgreSQL이 아니라 Redis 랭킹 구조에 반영합니다.

- `@KafkaListener(topics = "order-events", groupId = "query-burst-msa-ranking")`
- 수신한 이벤트를 `RankingService.apply(...)`로 전달
- `ORDER_CREATED`, `ORDER_CANCELED`만 반영

근거:

- `apps/query-burst-msa/ranking-service/src/main/java/com/booster/queryburstmsa/ranking/event/RankingOrderEventConsumer.java`
  - Kafka Listener 등록: 17-19

정리하면 Kafka는 `ranking-service`에서 "실시간 랭킹 Read Model을 갱신하는 입력 채널"입니다.

### 1-4. 설정 파일 기준 Kafka 연결 서비스

- `order-service` Kafka 설정: `apps/query-burst-msa/order-service/src/main/resources/application.yml`
- `analytics-service` Kafka 설정: `apps/query-burst-msa/analytics-service/src/main/resources/application.yml`
- `ranking-service` Kafka 설정: `apps/query-burst-msa/ranking-service/src/main/resources/application.yml`
- Docker Compose에서 Kafka 연결:
  - `analytics-service`에 `KAFKA_BOOTSTRAP_SERVERS=booster-kafka:9092`
  - `ranking-service`에 `KAFKA_BOOTSTRAP_SERVERS=booster-kafka:9092`
  - `order-service`에 `KAFKA_BOOTSTRAP_SERVERS=booster-kafka:9092`

즉 Kafka는 세 서비스에만 연결되어 있습니다.

## 2. Redis 적용 위치

### 2-1. `ranking-service`: 실시간 랭킹 저장소

Redis는 `ranking-service`에서만 사용됩니다.

핵심 용도는 두 가지입니다.

1. 상품 랭킹 점수 저장
2. Kafka Consumer 멱등성 키 저장

`RankingService.apply(...)` 동작:

- `setIfAbsent(eventKey, "1", 7일)`로 같은 이벤트 중복 처리 방지
- 시간 버킷 키 `RANK:hourly:yyyyMMddHH`에 대해 `addScore(...)`로 상품별 점수 누적
- 버킷 TTL은 25시간

`RankingService.getTopProducts(...)` 동작:

- 최근 `windowHours` 시간 버킷을 읽어 임시 Sorted Set에 합산
- 점수 상위 상품만 추려서 반환
- 임시 키는 조회 후 삭제

근거:

- `apps/query-burst-msa/ranking-service/src/main/java/com/booster/queryburstmsa/ranking/application/RankingService.java`
  - 멱등성 TTL 7일, 버킷 TTL 25시간: 21-23
  - `setIfAbsent(...)`로 중복 방지: 37-40
  - `getScoredSortedSet(...)`, `addScore(...)`, `expire(...)`: 43-47
  - 최근 버킷 머지 후 Top-N 조회: 50-73

### 2-2. `ranking-service`: 초기 데이터 적재도 Redis 직접 사용

개발/로컬 프로필에서는 `RankingDataInitializer`가 Redis 키를 직접 초기화합니다.

- `RANK:hourly:*`
- `ranking:event:*`

근거:

- `apps/query-burst-msa/ranking-service/src/main/java/com/booster/queryburstmsa/ranking/config/RankingDataInitializer.java`
  - 기존 랭킹/이벤트 키 삭제: 55-60
  - `opsForZSet().add(...)`로 샘플 랭킹 적재: 64-70

### 2-3. 설정 파일 기준 Redis 연결 서비스

- `ranking-service` Redis 설정:
  - `apps/query-burst-msa/ranking-service/src/main/resources/application.yml`
  - `spring.data.redis.host`, `spring.data.redis.port`
- Docker Compose:
  - `ranking-service`에만 `REDIS_HOST=booster-redis`, `REDIS_PORT=6379`

즉 Redis는 `query-burst-msa`에서 `ranking-service` 전용 저장소입니다.

## 3. Resilience 적용 위치

### 3-1. `order-service`: `catalog-service` 호출 보호

Resilience는 `order-service`에서만 사용됩니다. 목적은 `catalog-service` 장애가 주문 서비스 전체 장애로 번지는 것을 막는 것입니다.

적용 대상 HTTP 호출:

- 재고 예약 `reserve`
- 재고 확정 `commit`
- 재고 복구 `release`

구현 방식:

- `CatalogServiceClient`에서 `CircuitBreakerFactory`로 named Circuit Breaker 생성
  - `catalog-reserve`
  - `catalog-commit`
  - `catalog-release`
- 실제 HTTP 호출은 `RestClient`
- 실패 시 fallback 응답 반환
  - `reserve`: `REJECTED`, `CIRCUIT_OPEN` 또는 `CATALOG_UNAVAILABLE`
  - `commit/release`: 동일하게 fallback 응답 반환

근거:

- `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/infrastructure/CatalogServiceClient.java`
  - CircuitBreaker 생성: 47-49
  - `reserveCb.run(...)`: 58-73
  - `commitCb.run(...)`: 81-94
  - `releaseCb.run(...)`: 102-115
  - fallback 응답 생성: 118-137
- `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/application/OrderApplicationService.java`
  - 주문 생성 시 `catalogServiceClient.reserve(...)` 호출: 62-69
  - 결제 시 `catalogServiceClient.commit(...)` 호출: 89-92
  - 취소 시 `catalogServiceClient.release(...)` 호출: 117-119

### 3-2. Circuit Breaker / TimeLimiter 설정

`CatalogCircuitBreakerConfig`에서 호출 성격별로 설정을 분리합니다.

- `catalog-reserve`
  - 실패율 50%면 OPEN
  - OPEN 유지 10초
  - 최소 10건 호출 후 집계
  - TimeLimiter 3초
- `catalog-commit`, `catalog-release`
  - 실패율 60%면 OPEN
  - OPEN 유지 5초
  - 최소 5건 호출 후 집계
  - TimeLimiter 2초

근거:

- `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/config/CatalogCircuitBreakerConfig.java`
  - `reserveConfig`: 30-36
  - `commitReleaseConfig`: 39-46
  - `reserveTimeLimiter`: 48-50
  - `commitReleaseTimeLimiter`: 52-54
  - named 인스턴스 연결: 56-68

### 3-3. 운영 관측 포인트

`order-service`는 Actuator/Prometheus에 Circuit Breaker 관련 메트릭을 노출합니다.

근거:

- `apps/query-burst-msa/order-service/src/main/resources/application.yml`
  - `management.endpoints.web.exposure.include: ... circuitbreakers`
  - `management.health.circuitbreakers.enabled: true`
  - `metrics.distribution.percentiles-histogram.[resilience4j.circuitbreaker.calls]: true`

즉 Resilience는 단순 라이브러리 의존성 수준이 아니라, 설정/호출/fallback/메트릭까지 `order-service`에 실제 적용되어 있습니다.

## 4. 정리

- Kafka는 `order-service`가 이벤트를 발행하고, `analytics-service`와 `ranking-service`가 이를 소비하는 구조다.
- Redis는 `ranking-service`에만 적용되며, 실시간 랭킹 저장과 이벤트 멱등성 체크를 함께 담당한다.
- Resilience는 `order-service -> catalog-service` 동기 HTTP 호출 보호용으로만 적용되어 있으며, Circuit Breaker와 TimeLimiter, fallback 응답, 메트릭 노출까지 포함한다.
- `catalog-service`, `member-service`에는 현재 Kafka/Redis/Resilience 직접 적용 코드가 없다.
