---
name: event-flow
description: 분산 시스템 간의 이벤트 흐름 문서화
user-invocable: true
---

## 작업

$ARGUMENTS로 지정된 코드를 기준으로 Kafka 등 메시징 시스템의 이벤트 흐름을 추적하고 시각화합니다.

### 분석 대상

- `$ARGUMENTS`가 클래스명이면: 해당 클래스에서 시작/종료되는 이벤트 흐름 추적
- `$ARGUMENTS`가 토픽명이면: 해당 토픽의 Producer/Consumer 관계 정리
- `$ARGUMENTS`가 없으면: 프로젝트 전체 이벤트 흐름 맵 생성

### 분석 관점

#### 1. 이벤트 발행 (Producer)
- `ApplicationEventPublisher` 사용 위치
- `KafkaTemplate.send()` 호출 위치
- Outbox 테이블에 저장되는 이벤트 타입
- 발행 시점 (트랜잭션 내/외)

#### 2. 이벤트 소비 (Consumer)
- `@EventListener` / `@TransactionalEventListener` 핸들러
- `@KafkaListener` 처리 로직
- Consumer Group 및 파티션 전략
- 멱등성 보장 여부 (중복 메시지 처리)

#### 3. 데이터 정합성
- Outbox Pattern 적용 여부
- 이벤트 발행 보장 수준 (at-least-once / at-most-once / exactly-once)
- 보상 트랜잭션(Saga) 존재 여부
- 이벤트 순서 보장 여부

#### 4. 장애 시나리오
- Consumer 실패 시 재처리 경로
- DLQ(Dead Letter Queue) 존재 여부
- 메시지 유실 가능 지점

### 출력 형식

```markdown
## 이벤트 흐름 분석

### 요약
- 기준점: [클래스/토픽명]
- 관련 서비스: [서비스 목록]
- 토픽 수: N개
- 정합성 수준: (Strong / Eventual / Weak)

---

### 이벤트 흐름도

```
┌──────────────────┐     ┌─────────────┐     ┌────────────────────┐
│ WaitingService   │     │   Kafka     │     │ NotificationService│
│                  │     │             │     │                    │
│ waiting.call()   │     │             │     │                    │
│   │              │     │             │     │                    │
│   ├─ DB 저장     │     │             │     │                    │
│   ├─ Outbox 저장 │     │             │     │                    │
│   │              │     │             │     │                    │
│ OutboxRelay      │────▶│ waiting.    │────▶│ @KafkaListener     │
│ (Polling 3s)     │     │ called      │     │ handleCalledEvent()│
│                  │     │             │     │   └─ SMS 발송      │
└──────────────────┘     └─────────────┘     └────────────────────┘
```

### 이벤트 상세

#### [E-1] WAITING_CALLED
- **Producer**: `WaitingService.call()` → Outbox 저장
- **Topic**: `waiting.called`
- **Consumer**: `NotificationEventConsumer.handleCalledEvent()`
- **Payload**:
```json
{
  "waitingId": 123,
  "phoneNumber": "010-1234-5678",
  "restaurantName": "맛집"
}
```
- **정합성**: Outbox Pattern (at-least-once)
- **멱등성**: 처리 ID 기반 중복 방지

### 잠재적 문제

#### [P-1] 제목
- **위치**: 흐름 내 특정 지점
- **문제**: 설명
- **영향**: 장애 시나리오
- **해결**: 개선 방안
```

### 사용 예시

```
/event-flow WaitingService          # 웨이팅 서비스 이벤트 흐름 추적
/event-flow CouponIssueService      # 쿠폰 발행 이벤트의 시작부터 끝까지 추적
/event-flow topics/order-events     # 특정 토픽 관련 발행/구독 관계 정리
/event-flow                         # 프로젝트 전체 이벤트 맵 생성
```
