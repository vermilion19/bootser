# Query Burst MSA 면접 Q&A

> 경력직 포트폴리오 프로젝트 — 대용량 주문 트래픽 처리 MSA

---

## 목차

1. [프로젝트 개요 및 문제 정의](#1-프로젝트-개요-및-문제-정의)
2. [서비스 분리 기준](#2-서비스-분리-기준)
3. [기술 선택 이유](#3-기술-선택-이유)
4. [아키텍처 패턴 선택 이유](#4-아키텍처-패턴-선택-이유)
5. [설정값 선택 근거](#5-설정값-선택-근거)
6. [동시성 이슈 및 해결 방법](#6-동시성-이슈-및-해결-방법)
7. [고부하 환경 문제 및 해결 방법](#7-고부하-환경-문제-및-해결-방법)
8. [트레이드오프 및 개선 방향](#8-트레이드오프-및-개선-방향)

---

## 1. 프로젝트 개요 및 문제 정의

### Q. 이 프로젝트를 만든 이유와 해결하려는 문제가 무엇인가요?

대용량 트래픽 환경에서 주문 처리를 안정적으로 수행하는 시스템을 설계하고 싶었습니다. 구체적으로 세 가지 문제를 해결하고자 했습니다.

**문제 1 — 재고 초과 주문(Oversell)**: 동시에 수백 명이 같은 상품을 주문할 때, 재고보다 많은 주문이 승인되는 상황. 예를 들어 재고가 50개인 상품에 200개 주문이 동시에 들어오면 잘못 구현하면 70~80개가 승인될 수 있습니다.

**문제 2 — 이벤트 유실**: 주문이 생성됐는데 네트워크 장애로 Kafka 이벤트 발행이 실패하면 통계·랭킹 데이터가 누락됩니다. 반대로 Kafka는 발행했는데 DB 커밋이 실패하면 존재하지 않는 주문에 대한 이벤트가 발행되는 "유령 이벤트"가 생깁니다.

**문제 3 — 중복 이벤트 처리**: Kafka는 at-least-once 전달을 보장하므로 Consumer 재시작이나 리밸런싱 시 동일 이벤트가 두 번 이상 전달될 수 있습니다. 이를 처리하지 않으면 매출 집계가 이중으로 올라가거나 랭킹이 오염됩니다.

### Q. 모놀리스 대신 MSA를 선택한 이유가 무엇인가요?

비즈니스 도메인별로 **부하 특성이 다르기 때문**입니다.

- 상품 탐색(catalog)은 읽기 중심이라 캐싱으로 스케일할 수 있습니다.
- 주문 생성(order)은 쓰기 중심으로 flash sale 같은 이벤트에서 폭발적으로 증가합니다.
- 랭킹 조회(ranking)는 초당 수천 건의 읽기 요청을 Redis 한 대로 처리할 수 있습니다.

이 셋을 하나의 서비스에 묶으면 가장 느린 도메인에 맞춰 스케일아웃해야 하고, 한 도메인의 장애가 전체 서비스를 멈춥니다. MSA로 분리하면 도메인별로 독립 스케일아웃이 가능하고 장애 격리가 됩니다.

---

## 2. 서비스 분리 기준

### Q. 5개 서비스는 어떤 기준으로 나눴나요?

네 가지 기준을 적용했습니다.

**① 단일 책임**: 하나의 서비스는 하나의 비즈니스 도메인의 데이터 소유권을 가집니다. `order-service`만이 주문 데이터의 진실 공급원이고, `catalog-service`만이 재고 데이터의 진실 공급원입니다.

**② CQRS — 쓰기/읽기 부하 분리**: 같은 `order-events`를 구독하더라도 `analytics-service`(PostgreSQL 집계)와 `ranking-service`(Redis 순위)는 조회 패턴과 저장소가 완전히 다릅니다. 하나로 합치면 두 저장소를 모두 다뤄야 하고 독립 스케일이 불가능합니다.

**③ 변경 주기 분리**: `catalog-service`는 상품 정보 변경이 잦고 읽기 캐싱 전략이 필요합니다. `order-service`는 쓰기 처리량 증대가 필요합니다. 분리하면 각자 독립적으로 최적화할 수 있습니다.

**④ DB 소유권 분리**: 서비스 간 DB 직접 참조를 금지했습니다. `order-service`는 `member_id`만 저장하고 회원 정보를 직접 조회하지 않습니다.

```
[Write Side]
order-service    — 주문 생성·상태 전이
catalog-service  — 상품·재고 관리

[Read Side — Event-driven]
analytics-service — 일별 매출 Read Model (PostgreSQL)
ranking-service   — 실시간 상품 랭킹 (Redis)

[독립 도메인]
member-service   — 회원 마스터
```

### Q. order-service와 catalog-service 간 통신을 동기 HTTP로 한 이유는?

**주문 생성 응답을 반환하기 전에 재고 확인 결과가 필요하기 때문**입니다. 재고 예약 결과를 모른 채 주문을 생성하면 재고가 없는 상품에도 주문이 생성되고, 이후 비동기로 취소하는 보상 트랜잭션(Saga)이 필요해집니다. 구현 복잡도가 크게 올라가고 중간 상태가 클라이언트에 노출됩니다.

반면 analytics/ranking은 약간의 지연이 허용됩니다. 주문 직후 랭킹이 수 초 늦게 반영돼도 사용자 경험에 지장이 없으므로 Kafka 비동기로 처리합니다.

---

## 3. 기술 선택 이유

### Q. Redis를 선택한 이유와 어떻게 활용했나요?

`ranking-service`에서 실시간 상품 랭킹 저장소로 활용했습니다. Redis를 선택한 이유는 세 가지입니다.

**① Sorted Set 자료구조**: `ZADD key score member`로 상품별 판매량 누적이 O(log N)이고, `ZREVRANGE`로 Top-K 추출이 O(log N + K)입니다. DB의 `GROUP BY + ORDER BY` 집계보다 수십 배 빠릅니다.

**② 원자적 연산**: `ZINCRBY`(Redisson의 `addScore`)는 원자적이라 동시에 수백 개의 이벤트가 동일 상품 점수를 수정해도 Race Condition이 없습니다.

**③ TTL 자동 만료**: 시간 버킷(`RANK:hourly:yyyyMMddHH`)에 25시간 TTL을 설정하면 자동으로 정리됩니다. DB였다면 별도 정리 배치가 필요합니다.

DB 집계 방식과 비교했을 때, 수백만 건 주문 데이터를 `GROUP BY` 집계하면 인덱스를 활용해도 조회마다 수백 ms가 걸립니다. Redis로 실시간 집계하면 p95 < 100ms를 달성할 수 있습니다.

### Q. Kafka를 선택한 이유는? 단순 HTTP 이벤트 알림은 안 되나요?

**느슨한 결합과 이벤트 내구성** 때문입니다.

HTTP 이벤트 알림 방식이라면 `order-service`가 `analytics-service`와 `ranking-service`의 주소를 알아야 합니다. Consumer가 다운된 순간의 이벤트는 유실됩니다. Consumer를 추가할 때마다 `order-service` 코드를 수정해야 합니다.

Kafka는 이벤트를 브로커가 내구적으로 보관(기본 7일)하므로 Consumer가 다운됐다가 재시작해도 밀린 이벤트를 순서대로 처리할 수 있습니다. Consumer를 추가해도 `order-service`는 변경이 없습니다.

또한 Outbox Pattern과 조합하면 "DB 저장 성공 = 이벤트 발행 보장"이라는 at-least-once 이벤트 발행 보장이 가능합니다.

### Q. analytics-service는 PostgreSQL, ranking-service는 Redis — 같은 이벤트를 다른 저장소로 쓴 이유가 있나요?

두 서비스의 **조회 패턴이 근본적으로 다르기 때문**입니다.

| 구분 | analytics-service | ranking-service |
|------|-------------------|-----------------|
| 조회 예시 | "4월 1일~20일 카테고리별 매출" | "최근 6시간 Top-10 상품" |
| 조회 패턴 | 날짜 범위 집계 쿼리 | 점수 정렬 후 상위 N개 |
| 저장소 | PostgreSQL | Redis |
| 이유 | ACID, 영구 보존, 범위 쿼리 | 고속 정렬, 원자적 점수 증감 |

`analytics-service`는 "이 데이터가 영원히 정확해야 한다"가 요구사항이고, `ranking-service`는 "초고속으로 실시간 순위를 제공해야 한다"가 요구사항입니다. 하나의 저장소로는 두 요구사항을 동시에 최적화할 수 없습니다.

### Q. Resilience4j Circuit Breaker를 적용한 이유는?

`order-service`가 `catalog-service`에 동기 의존하기 때문에, catalog-service가 느려지거나 다운되면 order-service도 함께 멈춥니다. 구체적으로 두 가지 문제가 생깁니다.

**① 레이턴시 전파**: catalog-service가 DB 쿼리로 인해 5초씩 걸리면 order-service의 모든 주문 요청이 5초 이상 블로킹됩니다.

**② 스레드 고갈**: Tomcat 스레드 400개가 모두 catalog 응답을 기다리면 다른 요청(주문 조회, 결제 등)도 처리 못 합니다.

Circuit Breaker는 실패율이 임계값(50%)을 넘으면 이후 요청을 즉시 차단(OPEN)하고 fallback을 반환합니다. catalog-service 장애가 order-service 전체 다운으로 번지는 것을 막습니다.

---

## 4. 아키텍처 패턴 선택 이유

### Q. Outbox Pattern을 선택한 이유는? 단순히 트랜잭션 내에서 Kafka 발행하면 안 되나요?

안 됩니다. 두 시스템(DB, Kafka)에 대한 쓰기를 원자적으로 묶는 것이 불가능하기 때문입니다.

**시나리오 1**: DB 저장 성공 → Kafka 발행 실패 → analytics/ranking에 주문이 반영되지 않음. 매출 집계 누락.

**시나리오 2**: Kafka 발행 성공 → DB 커밋 실패 → 실제로는 없는 주문에 대한 이벤트가 Consumer에 전달됨. "유령 주문"이 랭킹에 반영됨.

Outbox Pattern은 이 문제를 DB 트랜잭션의 원자성으로 해결합니다.

```
BEGIN TRANSACTION
  INSERT INTO orders (...)
  INSERT INTO order_outbox_events (status=PENDING, ...)
COMMIT
→ 둘 다 저장되거나, 둘 다 롤백
```

이후 별도 스케줄러(`OrderOutboxRelay`)가 3초 주기로 PENDING 이벤트를 Kafka에 발행합니다. Kafka 브로커가 잠시 다운돼도 이벤트는 DB에 PENDING 상태로 남아있고, 브로커 복구 후 자동으로 발행됩니다.

### Q. 재고 예약에 2-Phase(예약-확정-해제) 방식을 쓴 이유는?

order-service와 catalog-service가 서로 다른 DB를 가지므로 하나의 분산 트랜잭션으로 묶는 것이 불가능합니다. 단순히 "재고 차감 HTTP 호출 → 주문 저장"으로 구현하면 주문 저장 실패 시 차감된 재고를 수동 롤백해야 하고, 이 롤백도 실패할 수 있습니다.

2-Phase 방식은 재고를 "예약(RESERVED)" 상태로 가상 차감하고, 결제 완료 시 "확정(COMMITTED)", 취소 시 "해제(RELEASED) + 재고 복구"로 상태를 명시적으로 관리합니다.

```
주문 생성 → reserve()  → 재고 차감 + RESERVED 저장
결제 완료 → commit()   → COMMITTED (확정, 취소 불가)
주문 취소 → release()  → RELEASED  + 재고 복구
```

각 단계가 멱등키(`requestId`)로 중복 호출에 안전하게 설계되어 있어서 네트워크 재시도가 발생해도 이중 차감이 일어나지 않습니다.

### Q. analytics-service와 ranking-service의 멱등성 구현 방식이 다른 이유는?

두 서비스의 저장소 특성에 맞게 다르게 구현했습니다.

**analytics-service — DB unique constraint 기반**:

```java
// eventKey = "ORDER_CREATED:1234567890:2025-04-13T18:00:00"
if (processedOrderEventRepository.existsByEventKey(eventKey)) {
    return; // 이미 처리됨
}
// ... 집계 처리 ...
processedOrderEventRepository.save(ProcessedOrderEvent.create(eventKey));
// eventKey에 UNIQUE 제약 → 동시 처리 시 하나만 성공
```

집계 처리와 처리 이력 저장이 동일 DB 트랜잭션 내에서 이루어집니다. 이력이 영구 보존되어 감사 추적이 가능합니다.

**ranking-service — Redis SETNX 기반**:

```java
Boolean firstSeen = stringRedisTemplate.opsForValue()
    .setIfAbsent(eventKey, "1", Duration.ofDays(7));
if (!Boolean.TRUE.equals(firstSeen)) return; // 이미 처리됨
```

멱등성 체크와 Sorted Set 갱신 모두 Redis에서 처리합니다. 저장소가 일치하므로 DB처럼 별도 이력 테이블이 필요 없습니다. TTL 7일은 Kafka 기본 retention 기간과 맞춘 값입니다.

### Q. Redis 랭킹에서 "시간 버킷" 방식을 쓴 이유는?

"최근 N시간"이라는 슬라이딩 윈도우를 지원하기 위해서입니다.

하나의 Sorted Set에 전체 판매량을 누적하면 "최근 6시간 랭킹"을 계산하기 위해 오래된 데이터를 뺀 값을 추적해야 합니다. 이를 정확히 구현하려면 이벤트 시각 정보를 별도로 관리해야 해서 복잡도가 크게 올라갑니다.

1시간 단위로 버킷을 분리하면 `RANK:hourly:2025041318`, `RANK:hourly:2025041317`, ... 이런 키를 요청한 윈도우만큼 임시 Sorted Set에 머지하면 됩니다. 각 버킷은 25시간 TTL로 자동 만료되므로 정리 배치도 필요 없습니다.

---

## 5. 설정값 선택 근거

### Q. DB 커넥션 풀을 maximum-pool-size: 50으로 설정한 이유는?

PostgreSQL은 커넥션 자체가 프로세스이기 때문에 커넥션이 많을수록 컨텍스트 스위칭 비용이 커집니다. 일반적으로 `(CPU 코어 수 × 2) + 스핀들 수` 공식을 씁니다. 개발 환경 기준 4코어라면 약 8~10개가 이론적 최적치입니다.

그런데 부하 테스트 시나리오에서 최대 400 스레드가 동시에 DB 접근을 시도하므로, 커넥션이 너무 적으면 대기 시간이 길어집니다. 실제로 50개로 설정한 후 `hikaricp_connections_pending` 메트릭을 모니터링하며 조정했습니다. 실제 프로덕션이라면 부하 테스트 결과를 보고 재조정이 필요합니다.

### Q. Tomcat 스레드 풀을 max: 400으로 설정한 이유는?

부하 테스트에서 최대 300 VU(Virtual User)를 기준으로 설정했습니다. 스레드가 너무 적으면 요청이 accept-count 큐에 쌓이고, 너무 많으면 컨텍스트 스위칭 오버헤드가 커집니다. 400으로 여유를 두고, `http_server_requests_seconds` 히스토그램으로 대기 시간을 모니터링했습니다.

만약 가상 스레드(Virtual Threads)를 활성화하면 스레드 수 제한이 의미 없어지고 대신 커넥션 풀이 병목이 됩니다. 이 프로젝트는 Java 25 + Spring Boot 4 기반으로 가상 스레드 전환이 가능하지만, 의도적으로 전통적인 스레드 풀로 병목 포인트를 명시적으로 드러냈습니다.

### Q. Circuit Breaker의 failureRateThreshold와 slidingWindowSize는 어떻게 정했나요?

reserve(재고 예약) Circuit Breaker 기준으로 설명하겠습니다.

- `slidingWindowSize: 50`: 최근 50건 기준. 너무 작으면 일시적 오류에 오버반응하고, 너무 크면 장애 감지가 늦습니다.
- `minimumNumberOfCalls: 10`: 10건 미만이면 Circuit Breaker가 동작하지 않습니다. 서버 시작 직후 1~2건 실패로 CB가 열리는 것을 방지합니다.
- `failureRateThreshold: 50`: 50%이상 실패 시 OPEN. catalog-service의 정상적인 재고 부족(REJECTED 응답)은 실패가 아니라 정상 응답이므로, 실제 네트워크 오류·타임아웃만 카운팅됩니다.
- `waitDurationInOpenState: 10s`: 10초 후 HALF_OPEN 상태로 전환해 복구를 시도합니다.
- `timeoutDuration: 3s`: catalog-service 응답 타임아웃. DB의 `connection-timeout: 30s`보다 훨씬 짧게 설정해 스레드 고갈을 방지합니다.

commit/release는 주문 생성의 핵심 경로가 아니므로 임계값을 상대적으로 완화했습니다(failureRateThreshold: 60, slidingWindowSize: 20).

### Q. Outbox 스케줄러 폴링 간격을 3초로 설정한 이유는?

이벤트 발행 지연과 DB 부하의 트레이드오프입니다. 폴링 간격이 짧으면 이벤트가 빠르게 전달되지만 DB 조회 부하가 증가합니다. 긴 경우 그 반대입니다.

analytics-service와 ranking-service는 "수 초 이내 반영"이면 충분하므로 3초를 선택했습니다. 만약 실시간성이 더 중요하다면 DB 폴링 대신 `@TransactionalEventListener`로 커밋 후 발행을 시도하는 방식을 고려할 수 있습니다. 다만 이는 발행 실패 시 재발행을 별도로 구현해야 합니다.

---

## 6. 동시성 이슈 및 해결 방법

### Q. 재고 초과 주문(Oversell) 문제를 어떻게 해결했나요?

비관적 락(Pessimistic Lock)으로 해결했습니다. 재고 예약 시 해당 상품 행을 `SELECT FOR UPDATE`로 잠급니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from ProductEntity p where p.id in :ids")
List<ProductEntity> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
```

동시에 N개의 요청이 같은 상품을 예약하려 해도, DB가 행 단위 잠금을 보장하므로 한 번에 하나의 트랜잭션만 재고를 수정할 수 있습니다. 나머지는 대기하거나 데드락 감지 후 예외가 발생합니다.

낙관적 락(@Version)도 고려했지만, 재고 경합이 심한(flash sale) 환경에서는 대부분의 요청이 OptimisticLockException으로 실패하고 재시도 로직이 복잡해집니다. 비관적 락은 대기가 발생하지만 "재고가 있으면 반드시 처리된다"는 보장이 명확합니다.

실제로 k6 부하 테스트(`concurrency-oversell.js`)로 재고 50개에 4500건 동시 요청을 보내 Oversell이 0건임을 검증했습니다.

```javascript
// 검증 방법
SELECT stock FROM catalog_product WHERE id = {productId};
// 음수면 Oversell 발생 → 테스트 실패
```

### Q. 다품목 주문 시 데드락(Deadlock)이 발생할 수 있지 않나요?

맞습니다. 현재 구현의 알려진 한계입니다.

A가 [상품 1 → 상품 2] 순서로 잠금을 시도하고, B가 [상품 2 → 상품 1] 순서로 잠금을 시도하면 데드락이 발생할 수 있습니다. PostgreSQL은 데드락을 감지해 한 트랜잭션에 오류를 반환합니다.

해결 방법으로 두 가지를 고려했습니다.

**① 잠금 순서 고정**: `productId` 오름차순으로 정렬해 항상 같은 순서로 잠금을 획득하면 데드락 사이클이 생기지 않습니다. 구현이 간단합니다.

**② 분산 락(Redisson)**: DB 잠금 대신 Redis 기반 분산 락으로 애플리케이션 레벨에서 직렬화합니다. scale-out 환경에서 여러 인스턴스가 안전합니다.

현재는 재고 예약 실패 시 주문이 `REJECTED` 상태로 저장되므로 데드락으로 인한 재시도 로직이 클라이언트 책임입니다. Idempotency-Key를 지원하므로 재시도가 안전합니다.

### Q. Kafka Consumer 중복 처리 문제를 어떻게 해결했나요?

저장소 특성에 맞게 두 가지 방식을 사용했습니다.

**analytics-service**: DB unique constraint 활용

```java
String eventKey = eventType + ":" + orderId + ":" + occurredAt;
if (processedOrderEventRepository.existsByEventKey(eventKey)) return;
// 집계 처리
// unique constraint가 있어 동시 처리 시 하나만 성공
processedOrderEventRepository.save(ProcessedOrderEvent.create(eventKey));
```

이벤트 키는 `{eventType}:{orderId}:{occurredAt}` 형식입니다. `orderId`만 쓰면 같은 주문의 `ORDER_CREATED`와 `ORDER_CANCELED` 이벤트를 구분할 수 없어서 발생 시각을 포함했습니다.

**ranking-service**: Redis SETNX 원자적 연산 활용

```java
Boolean firstSeen = stringRedisTemplate.opsForValue()
    .setIfAbsent(eventKey, "1", Duration.ofDays(7));
if (!Boolean.TRUE.equals(firstSeen)) return;
```

`setIfAbsent`는 원자적이므로 동시에 두 스레드가 같은 키로 호출해도 정확히 하나만 `true`를 받습니다.

---

## 7. 고부하 환경 문제 및 해결 방법

### Q. catalog-service가 다운됐을 때 주문 서비스는 어떻게 동작하나요?

Circuit Breaker가 동작합니다. catalog-service 호출 실패율이 50%를 넘으면 이후 요청을 즉시 차단하고 fallback 응답을 반환합니다.

```java
private InventoryReservationResponse reserveFallback(Throwable throwable) {
    boolean circuitOpen = throwable instanceof CallNotPermittedException;
    String reason = circuitOpen ? "CIRCUIT_OPEN" : "CATALOG_UNAVAILABLE";
    return new InventoryReservationResponse(null, REJECTED, reason, List.of());
}
```

주문은 `REJECTED` 상태로 생성됩니다. 클라이언트는 명확한 에러 이유를 받고, Idempotency-Key로 나중에 안전하게 재시도할 수 있습니다. catalog-service 복구 후 10초 뒤 HALF_OPEN 상태에서 5건을 프로브해 자동으로 CLOSED로 전환됩니다.

Circuit Breaker 상태는 Grafana로 실시간 모니터링합니다.

```promql
# CB 상태 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="catalog-reserve"}
# 차단된 호출 수
resilience4j_circuitbreaker_calls_total{name="catalog-reserve", kind="not_permitted"}
```

### Q. Kafka 브로커가 다운되면 주문 데이터는 어떻게 되나요?

주문 데이터 자체는 영향이 없습니다. `order-service`의 DB에 정상 저장되어 있습니다.

Outbox Pattern 덕분에 이벤트는 `order_outbox_events` 테이블에 `PENDING` 상태로 남습니다. Kafka 브로커가 복구되면 `OrderOutboxRelay` 스케줄러가 3초 주기로 PENDING 이벤트를 발견하고 Kafka에 발행합니다. at-least-once 보장이므로 analytics/ranking에 데이터가 반영됩니다.

단, 브로커 다운 기간 동안 analytics/ranking 데이터는 지연됩니다. 이는 의도된 트레이드오프입니다(최종 일관성).

### Q. flash sale처럼 짧은 시간에 폭발적 트래픽이 오면 어떻게 처리하나요?

세 단계의 방어선이 있습니다.

**① Nginx 로드밸런서**: Docker Compose 스케일아웃으로 `catalog-service`를 3개, `order-service`를 2개로 늘리면 Nginx가 자동으로 분산합니다. Docker DNS가 5초 주기로 갱신되어 새 인스턴스를 자동 감지합니다.

```bash
docker compose up --scale catalog-service=3 --scale order-service=2 -d
```

**② DB 커넥션 풀 + Tomcat 스레드 풀**: 초과 요청은 accept-count 큐(100개)에서 대기하고, 큐가 차면 503을 반환합니다. 서버가 무한정 느려지는 것을 막습니다.

**③ Circuit Breaker 타임아웃**: catalog-service 응답이 3초를 넘으면 타임아웃으로 처리됩니다. 스레드가 무한정 블로킹되지 않습니다.

부하 테스트(`throughput-order.js`)에서 ramping-arrival-rate로 RPS를 점진적으로 올려 실제 처리량 한계를 측정했습니다. p95 응답시간 3000ms, 에러율 5%가 SLO이고 이를 초과하면 테스트가 자동 중단됩니다.

### Q. 랭킹 서비스의 응답 시간을 어떻게 보장하나요?

Redis Sorted Set의 시간 버킷 머지가 핵심입니다. 읽기 흐름은 다음과 같습니다.

```
1. windowHours 범위의 버킷 키를 순회 (예: 6시간 → 6개 키)
2. 각 버킷의 전체 항목을 임시 Sorted Set에 addScore
3. 임시 Set에서 상위 size개 추출
4. 임시 Set 즉시 삭제
```

Redis는 인메모리이므로 이 연산이 수 밀리초 내에 완료됩니다. 상품 수가 100,000개라도 Redis 내부 skiplist가 O(log N)으로 처리합니다.

DB 기반 집계 방식(`GROUP BY + SUM + ORDER BY`)은 데이터가 쌓일수록 느려지지만, Redis 버킷 방식은 버킷 크기(해당 시간대 거래된 상품 수)에만 의존하므로 누적 데이터 양에 독립적입니다.

### Q. Redis가 다운되면 랭킹 서비스는 어떻게 되나요?

현재는 Redis 장애 시 랭킹 서비스가 완전히 불가합니다. 이 부분은 알려진 한계이며 개선 포인트로 인지하고 있습니다.

개선 방안으로 `analytics-service`의 DB 기반 집계로 폴백하는 방법이 있습니다. "최근 N시간 판매량"은 `product_daily_sales` 테이블로 근사치를 제공할 수 있습니다. 응답이 수백 ms로 느려지지만 서비스는 계속됩니다.

Redis 고가용성을 위해 Redis Sentinel이나 Cluster 구성도 고려할 수 있지만, 이 프로젝트는 학습 목적의 단일 Redis로 구성했습니다.

---

## 8. 트레이드오프 및 개선 방향

### Q. 현재 설계에서 알고 있는 한계와 개선 방향을 설명해주세요.

**① Outbox scale-out 중복 발행**

현재 `order-service`가 2개 인스턴스로 스케일아웃되면, 두 인스턴스가 동시에 같은 PENDING 이벤트를 발견하고 중복 발행할 수 있습니다. `SENDING` 상태 선점으로 완화했지만 완벽하지 않습니다.

해결 방향: ShedLock으로 스케줄러 실행을 하나의 인스턴스로 제한합니다. Consumer 측 멱등성 처리가 이미 되어 있으므로 중복 발행이 오염을 유발하지는 않습니다.

**② 재고 예약 만료 스케줄러 부재**

결제를 진행하지 않고 방치된 예약이 재고를 계속 점유합니다. 15분 만료 후 자동 해제 스케줄러가 필요합니다.

**③ analytics 처리 이력 테이블 무한 증가**

`analytics_processed_order_event` 테이블이 이벤트마다 쌓입니다. Kafka retention 기간(7일)이 지난 레코드는 의미가 없으므로 주기적으로 정리해야 합니다.

**④ catalog-service 동기 의존 → Saga로 전환 검토**

`order-service`가 catalog-service에 동기 의존하는 것이 가장 큰 가용성 리스크입니다. 장기적으로 Saga Choreography 패턴으로 전환하면 완전 비동기가 되어 catalog 장애와 order 서비스를 완전 분리할 수 있습니다. 단, 구현 복잡도가 크게 올라갑니다.

### Q. 이 프로젝트에서 가장 어려웠던 부분은 무엇인가요?

**분산 환경에서 데이터 정합성 경계를 명확히 설계하는 것**입니다.

모놀리스라면 `@Transactional` 하나로 해결되는 문제들이 서비스가 분리되면 각각 다른 방식으로 풀어야 합니다.

- order ↔ catalog 간 재고 정합성 → 2-Phase 예약 패턴
- DB ↔ Kafka 간 이벤트 원자성 → Outbox 패턴
- Kafka Consumer 중복 처리 → 저장소별 멱등성 구현

특히 멱등성 구현에서 DB 기반(analytics)과 Redis 기반(ranking)의 차이를 이해하고 각 저장소 특성에 맞는 구현을 선택하는 과정이 가장 많이 고민했습니다. "동일한 문제도 맥락에 따라 다른 해법이 적절하다"는 것을 설계를 통해 체감했습니다.

### Q. 실제 프로덕션이라면 어떤 부분을 더 보강하겠나요?

**① 관찰성 강화**: 현재 Prometheus + Grafana + Loki + Tempo를 갖췄지만, 알람 임계값 설정과 Runbook 연동이 부족합니다. 특히 `outbox_events` PENDING 개수, CB OPEN 횟수에 알람을 설정해야 합니다.

**② 카오스 엔지니어링**: 현재 부하 테스트는 정상 시나리오 중심입니다. catalog-service를 강제로 다운시켰을 때 CB가 실제로 열리는지, Kafka 브로커 중단 후 Outbox가 정상 재발행하는지 자동 검증하는 시나리오가 필요합니다.

**③ DB 파티셔닝**: `customer_order`와 `analytics_daily_sales_summary` 테이블은 날짜 기반 파티셔닝을 적용하면 오래된 데이터 아카이빙과 쿼리 성능 모두 개선됩니다.

**④ 이벤트 스키마 관리**: 현재 `OrderEventPayload`를 단순 JSON으로 직렬화합니다. Schema Registry + Avro를 도입하면 Consumer 코드 변경 없이 Producer가 필드를 추가할 수 있고, 하위 호환성이 자동 검증됩니다.

---

## 부록 — 핵심 설계 결정 요약표

| 문제 | 채택한 해결책 | 핵심 이유 |
|------|--------------|----------|
| DB ↔ Kafka 원자성 | Outbox Pattern | DB 트랜잭션으로 이벤트 저장 보장 |
| 분산 재고 정합성 | 2-Phase 예약 | 명시적 상태 전이 + 멱등키 |
| 실시간 랭킹 고성능 | Redis Sorted Set 시간 버킷 | O(1) 쓰기, p95 < 100ms 응답 |
| Kafka 중복 처리 | 저장소별 멱등성 | DB=unique constraint, Redis=SETNX |
| catalog 장애 격리 | Circuit Breaker (Resilience4j) | 레이턴시 전파 및 스레드 고갈 방지 |
| Oversell 방지 | SELECT FOR UPDATE | DB 행 잠금으로 직렬화 보장 |
| 서비스 분리 기준 | CQRS + 도메인 소유권 | 부하 특성별 독립 스케일 가능 |