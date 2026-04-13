# ADR-002: 재고 예약 2-Phase로 분산 환경의 재고 정합성 보장

- **Status**: Accepted
- **Date**: 2025-04-13

## Context

`order-service`와 `catalog-service`는 물리적으로 분리된 서비스다.  
주문 생성 시 재고 차감과 주문 저장을 하나의 트랜잭션으로 묶을 수 없다.

### 핵심 요구사항

- 재고가 없는 상품은 주문되지 않아야 한다
- 주문이 취소되면 차감된 재고가 복구되어야 한다
- 결제가 완료된 주문만 재고가 최종 확정되어야 한다
- 멱등성: 동일한 주문 요청이 중복으로 들어와도 재고가 이중 차감되지 않아야 한다

### 검토한 대안

**A. 즉시 재고 차감 (단순 HTTP)**
```
POST /api/orders → catalog.deductStock() → save order
```
- 주문 저장 실패 시 차감된 재고를 수동으로 롤백해야 함
- 롤백 자체도 실패할 수 있어 보상 로직이 복잡해짐

**B. Saga Pattern (Choreography)**
- 각 서비스가 이벤트를 발행/구독하며 상태를 전이
- 분산 트랜잭션을 이벤트로 관리하여 결합도 낮음
- 복잡한 실패 보상 흐름, 최종 일관성만 보장 (중간 상태에서 데이터 조회 시 불일치 가능)

**C. 재고 예약 2-Phase (채택)**
- reserve → 재고를 예약 상태로 잠금 (차감하되 확정은 미뤄둠)
- commit → 결제 완료 시 예약을 확정
- release → 주문 취소 시 예약 해제 후 재고 복구
- 각 단계가 멱등하게 설계되어 중복 호출에 안전

## Decision

재고 예약 2-Phase를 채택한다.

### 플로우

```
createOrder()
    ↓
POST /internal/inventory/reservations
    → 재고 차감 + InventoryReservation(RESERVED) 저장
    → reservationId 반환
    ↓
OrderEntity 저장 (status=STOCK_RESERVED, reservationId 보관)

pay()
    ↓
POST /internal/inventory/reservations/{id}/commit
    → InventoryReservation(COMMITTED) 확정
    ↓
OrderEntity.pay()

cancel()
    ↓
POST /internal/inventory/reservations/{id}/release   ← status=STOCK_RESERVED인 경우만
    → 재고 복구 + InventoryReservation(RELEASED)
    ↓
OrderEntity.cancel()
```

### 멱등성 처리

`InventoryReservationRequest`에 `requestId`(= Idempotency-Key)를 포함.  
동일한 `requestId`로 요청이 재시도될 경우 기존 예약 결과를 그대로 반환.

### 재고 부족 처리

```java
OrderStatus status = reservationResponse.status() == InventoryReservationStatus.RESERVED
        ? OrderStatus.STOCK_RESERVED
        : OrderStatus.REJECTED;
```

재고 부족 시에도 주문 엔티티를 `REJECTED` 상태로 저장.  
예외를 던지지 않고 상태로 표현하여 클라이언트가 결과를 명확히 인지.

## Consequences

**장점**
- 재고 정합성을 강하게 보장 (예약된 재고는 다른 주문에 할당되지 않음)
- 결제 취소 시 재고 복구가 명시적이고 추적 가능
- 멱등키로 중복 요청 방지

**단점 및 트레이드오프**
- `catalog-service`가 다운되면 주문 생성 자체가 불가능 → Circuit Breaker 필요 (→ TODO)
- 예약 후 결제/취소 없이 장기간 방치된 예약 처리 정책 필요 (만료 스케줄러 등)
- 동기 HTTP 호출이므로 `catalog-service` 응답 지연이 `order-service` 응답 지연으로 전파됨
