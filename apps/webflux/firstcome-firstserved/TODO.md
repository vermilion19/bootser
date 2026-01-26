# Firstcome-Firstserved 개선 TODO

## 개요

대기업 면접관 관점에서 지적될 수 있는 부분들을 개선하는 작업 목록입니다.

---

## 1순위: 데이터 정합성 ⭐⭐⭐

### 1.1 Idempotency Key 구현
- [x] `IdempotencyKeyFilter` 또는 AOP 구현
- [x] Redis에 Idempotency Key 저장 (TTL 설정)
- [x] 중복 요청 시 기존 응답 반환
- [x] 테스트 코드 작성

**목적**: 네트워크 타임아웃 등으로 인한 중복 주문 방지

```
요청 흐름:
Client → [Idempotency Check] → Service → Response
              ↓ (중복 시)
         기존 Response 반환
```

### 1.2 Redis 재고 복구 메커니즘
- [ ] DB 저장 실패 시 Redis 재고 롤백 로직
- [ ] `StockPort`에 `increase()` 메서드 추가
- [ ] 보상 트랜잭션 적용

**목적**: Redis 재고 차감 후 DB 저장 실패 시 정합성 유지

### 1.3 Dead Letter Queue (DLQ) 구현
- [ ] `FailedOrderEvent` 엔티티/테이블 생성
- [ ] 실패한 이벤트 DLQ 저장
- [ ] DLQ 재처리 스케줄러
- [ ] DLQ 모니터링 API

**목적**: 이벤트 처리 실패 시 데이터 손실 방지

---

## 2순위: 안정성 ⭐⭐

### 2.1 Circuit Breaker 적용
- [ ] Resilience4j 의존성 추가
- [ ] Redis 호출에 Circuit Breaker 적용
- [ ] Fallback 전략 구현 (장애 시 대기열 안내 등)
- [ ] Circuit Breaker 상태 모니터링

**목적**: Redis 장애 시 전체 서비스 다운 방지

### 2.2 Health Check 강화
- [ ] Redis 연결 상태 체크
- [ ] DB 연결 상태 체크
- [ ] 커스텀 Health Indicator 구현

**목적**: 인프라 장애 조기 감지

### 2.3 Graceful Shutdown
- [ ] 미처리 이벤트 flush 로직
- [ ] Shutdown Hook 등록
- [ ] 버퍼 drain 대기 시간 설정

**목적**: 서버 재시작 시 이벤트 유실 최소화

---

## 3순위: 관측성 ⭐

### 3.1 Micrometer 메트릭
- [ ] 주문 TPS 메트릭
- [ ] 재고 현황 메트릭
- [ ] 이벤트 처리 지연 시간
- [ ] DLQ 적재량

**목적**: 시스템 상태 실시간 모니터링

### 3.2 분산 트레이싱
- [ ] Micrometer Tracing 설정
- [ ] Tempo/Zipkin 연동
- [ ] TraceId 로그 출력

**목적**: 요청 흐름 추적, 병목 구간 분석

### 3.3 알림 설정
- [ ] 재고 임계치 알림 (10% 이하)
- [ ] 에러율 임계치 알림
- [ ] DLQ 적재 알림

**목적**: 장애 조기 대응

---

## 4순위: 보안/기능 확장

### 4.1 Rate Limiting
- [ ] IP 기반 요청 제한
- [ ] 사용자 기반 요청 제한
- [ ] Rate Limit 초과 시 429 응답

**목적**: DDoS, 악의적 요청 방어

### 4.2 주문 조회 API
- [ ] `GET /api/v1/orders/{orderId}` - 단건 조회
- [ ] `GET /api/v1/orders?userId={userId}` - 내 주문 목록

**목적**: 사용자 주문 확인 기능

### 4.3 관리자 API
- [ ] `POST /api/v1/admin/stock` - 재고 설정
- [ ] `GET /api/v1/admin/stats` - 통계 조회
- [ ] `GET /api/v1/admin/dlq` - DLQ 조회

**목적**: 운영 편의성

---

## 진행 상태

| 우선순위 | 항목 | 상태 | 완료일 |
|---------|------|------|--------|
| 1.1 | Idempotency Key | ⬜ 대기 | - |
| 1.2 | Redis 재고 복구 | ⬜ 대기 | - |
| 1.3 | Dead Letter Queue | ⬜ 대기 | - |
| 2.1 | Circuit Breaker | ⬜ 대기 | - |
| 2.2 | Health Check | ⬜ 대기 | - |
| 2.3 | Graceful Shutdown | ⬜ 대기 | - |
| 3.1 | Micrometer 메트릭 | ⬜ 대기 | - |
| 3.2 | 분산 트레이싱 | ⬜ 대기 | - |
| 3.3 | 알림 설정 | ⬜ 대기 | - |
| 4.1 | Rate Limiting | ⬜ 대기 | - |
| 4.2 | 주문 조회 API | ⬜ 대기 | - |
| 4.3 | 관리자 API | ⬜ 대기 | - |

---

## 참고 자료

- [Idempotency Key - Stripe API](https://stripe.com/docs/api/idempotent_requests)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Circuit Breaker - Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker)
- [Dead Letter Queue Pattern](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html)
