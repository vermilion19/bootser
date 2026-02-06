# 회원 이벤트 파이프라인 설계

auth-service에서 회원 이벤트를 발행하고 d-day-service에서 수신하는 Kafka 기반 이벤트 파이프라인.

---

## 목차

1. [현재 상태](#현재-상태)
2. [전체 아키텍처](#전체-아키텍처)
3. [의존성 구성](#의존성-구성)
4. [공통 이벤트 정의](#공통-이벤트-정의)
5. [auth-service 구조](#auth-service-구조)
6. [d-day-service 구조](#d-day-service-구조)
7. [Kafka 토픽 설계](#kafka-토픽-설계)
8. [DLT 전략](#dlt-전략)
9. [구현 체크리스트](#구현-체크리스트)

---

## 현재 상태

| 서비스 | storage-kafka 의존성 | Kafka 사용 |
|--------|---------------------|------------|
| auth-service | ❌ 없음 | - |
| d-day-service | ❌ 없음 | - |
| waiting-service | ✅ 있음 | Outbox 패턴 |
| notification-service | ✅ 있음 | Consumer + DLT |

---

## 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            전체 이벤트 흐름                                  │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
  │   auth-service  │         │      Kafka      │         │ d-day-service   │
  └────────┬────────┘         └────────┬────────┘         └────────┬────────┘
           │                           │                           │
           │  1. 회원가입/수정/탈퇴     │                           │
           │                           │                           │
           │  ┌─────────────────────┐  │                           │
           │  │ Outbox 테이블 저장   │  │                           │
           │  │ (트랜잭션 내)        │  │                           │
           │  └──────────┬──────────┘  │                           │
           │             │             │                           │
           │  2. OutboxRelay 스케줄러  │                           │
           │─────────────┼────────────>│ Topic: member-events      │
           │             │             │───────────────────────────>│
           │             │             │                           │ 3. Consumer
           │             │             │                           │    처리
           │             │             │                           │
           │             │             │         실패 시            │
           │             │             │<───────────────────────────│
           │             │             │ Topic: member-events.DLT  │
           │             │             │───────────────────────────>│
           │             │             │                           │ 4. DLT
           │             │             │                           │    Consumer
```

---

## 의존성 구성

### auth-service/build.gradle

```groovy
dependencies {
    // 기존 의존성...
    implementation project(':libs:storage-kafka')  // 추가
}
```

### d-day-service/build.gradle

```groovy
dependencies {
    // 기존 의존성...
    implementation project(':libs:storage-kafka')  // 추가
}
```

---

## 공통 이벤트 정의

### 위치

```
libs/core-web/src/main/java/com/booster/core/web/event/member/
├── MemberEvent.java           # 공통 베이스
├── MemberCreatedEvent.java    # 회원 생성
├── MemberUpdatedEvent.java    # 회원 정보 수정
└── MemberDeletedEvent.java    # 회원 탈퇴
```

### MemberEvent (Base)

| 필드 | 타입 | 설명 |
|------|------|------|
| eventId | String | UUID, 멱등성 키 |
| eventType | String | CREATED, UPDATED, DELETED |
| memberId | Long | 회원 ID |
| timestamp | LocalDateTime | 이벤트 발생 시간 |

### MemberCreatedEvent

| 필드 | 타입 | 설명 |
|------|------|------|
| nickname | String | 닉네임 |
| email | String | 이메일 (옵션) |

### MemberUpdatedEvent

| 필드 | 타입 | 설명 |
|------|------|------|
| nickname | String | 변경된 닉네임 |
| changedFields | List<String> | 변경된 필드 목록 |

### MemberDeletedEvent

| 필드 | 타입 | 설명 |
|------|------|------|
| deletedAt | LocalDateTime | 탈퇴 시간 |

---

## auth-service 구조

```
apps/auth-service/src/main/java/com/booster/authservice/
├── domain/
│   ├── User.java
│   ├── UserRepository.java
│   └── outbox/
│       ├── MemberOutboxEvent.java       # Outbox 엔티티
│       └── MemberOutboxRepository.java
│
├── application/
│   ├── AuthService.java                 # 수정: 이벤트 저장 로직 추가
│   └── MemberEventPublisher.java        # 이벤트 생성 헬퍼
│
└── event/
    └── MemberOutboxRelay.java           # 스케줄러 (Kafka 발행)
```

### MemberOutboxEvent 엔티티

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| eventId | String | UUID (멱등성) |
| aggregateType | String | "MEMBER" |
| aggregateId | Long | member_id |
| eventType | String | CREATED, UPDATED, DELETED |
| payload | String | JSON 직렬화된 이벤트 |
| published | boolean | 발행 여부 |
| createdAt | LocalDateTime | 생성 시간 |

### 이벤트 발행 흐름

```
1. AuthService.processOAuthLogin() 호출
      │
      ▼
2. User 저장 (신규 or 기존)
      │
      ▼
3. 신규 회원인 경우
      │
      ▼
4. MemberEventPublisher.publishCreated(user)
      │
      ▼
5. MemberOutboxEvent INSERT (같은 트랜잭션)
      │
      ▼
6. 트랜잭션 커밋
      │
      ▼
7. MemberOutboxRelay 스케줄러 (3초마다)
      │
      ▼
8. Kafka로 메시지 발행
      │
      ▼
9. published = true 업데이트
```

---

## d-day-service 구조

```
apps/d-day/d-day-service/src/main/java/com/booster/dday/
├── member/
│   ├── domain/
│   │   ├── MemberReference.java
│   │   ├── MemberStatus.java           # ACTIVE, DELETED
│   │   └── MemberReferenceRepository.java
│   │
│   ├── application/
│   │   └── MemberSyncService.java       # 이벤트 처리 비즈니스 로직
│   │
│   ├── event/
│   │   ├── MemberEventConsumer.java     # 메인 Consumer
│   │   └── MemberDltConsumer.java       # DLT Consumer
│   │
│   └── infrastructure/
│       └── AuthServiceClient.java       # Feign (Fallback용)
```

### Consumer 처리 흐름

```
1. MemberEventConsumer.consume() 호출
      │
      ▼
2. 이벤트 타입 확인 (CREATED, UPDATED, DELETED)
      │
      ▼
3. MemberSyncService 위임
      │
      ├── CREATED: MemberReference INSERT
      ├── UPDATED: MemberReference UPDATE
      └── DELETED: status = DELETED, 관련 데이터 정리
      │
      ▼
4. 처리 완료 (Kafka offset commit)
```

---

## Kafka 토픽 설계

### 토픽 목록

| 토픽 | 용도 |
|------|------|
| member-events | 회원 이벤트 메인 토픽 |
| member-events.DLT | Dead Letter Topic |

### 토픽 설정

| 설정 | member-events | member-events.DLT |
|------|---------------|-------------------|
| Partitions | 3 | 1 |
| Replication (개발) | 1 | 1 |
| Replication (운영) | 3 | 3 |
| Retention | 7일 | 30일 |
| Cleanup Policy | delete | delete |

### Partition Key 전략

- Key: `memberId` (String)
- 동일 회원의 이벤트는 동일 파티션으로 → 순서 보장

---

## DLT 전략

### 재시도 흐름

```
  Consumer 수신
       │
       ▼
  ┌─────────────┐
  │  처리 시도   │
  └──────┬──────┘
         │
    성공 │        실패
         │          │
         ▼          ▼
      완료     ┌─────────────┐
               │  재시도 3회  │  ← backoff: 1초, 2초, 4초
               └──────┬──────┘
                      │
                 3회 모두 실패
                      │
                      ▼
               ┌─────────────┐
               │  DLT 전송   │
               └──────┬──────┘
                      │
                      ▼
               ┌─────────────┐
               │ DLT Consumer│
               └─────────────┘
```

### DLT Consumer 역할

| 역할 | 설명 |
|------|------|
| 로깅 | 실패 이벤트 상세 로그 |
| DB 저장 | 실패 이벤트 테이블 저장 (수동 처리용) |
| 알림 | 슬랙/이메일 알림 발송 |
| 메트릭 | 실패율 모니터링 |

### 재시도 설정

```yaml
# application.yml
spring:
  kafka:
    consumer:
      properties:
        # 재시도 관련 설정
        max.poll.interval.ms: 300000
    listener:
      # 수동 커밋 (처리 완료 후 커밋)
      ack-mode: MANUAL_IMMEDIATE
```

---

## 구현 체크리스트

### Phase 1: 인프라 준비 ✅ 완료

- [x] libs/core-web에 MemberEvent 클래스 정의
  - [x] MemberEvent.java (record + EventType enum)
- [x] Kafka에 member-events 토픽 생성

### Phase 2: auth-service (Producer) ✅ 완료

- [x] build.gradle에 storage-kafka 의존성 추가
- [x] application.yml에 Kafka 설정 추가
- [x] MemberOutboxEvent 엔티티 생성
- [x] MemberOutboxRepository 생성
- [x] MemberEventPublisher 서비스 생성
- [x] AuthService에 이벤트 발행 로직 추가
- [x] MemberOutboxRelay 스케줄러 생성
- [x] User ID를 Builder에서 생성하도록 수정

### Phase 3: d-day-service (Consumer)

- [ ] build.gradle에 storage-kafka 의존성 추가
- [ ] application.yml에 Kafka Consumer 설정 추가
- [ ] MemberReference 엔티티 생성
- [ ] MemberStatus enum 생성
- [ ] MemberReferenceRepository 생성
- [ ] MemberSyncService 생성
- [ ] MemberEventConsumer 생성 (@KafkaListener)
- [ ] MemberDltConsumer 생성

### Phase 4: 테스트 & 안정화

- [ ] auth-service 이벤트 발행 테스트
- [ ] d-day-service 이벤트 수신 테스트
- [ ] 이벤트 유실 시나리오 테스트
- [ ] DLT 동작 검증
- [ ] 멱등성 테스트 (동일 이벤트 중복 수신)

---

## 관련 문서

- [회원 동기화 아키텍처](./member-sync-architecture.md)

---
Step : Kafka 토픽 생성

방법 A: docker-compose로 Kafka 실행 중이면 CLI로 생성

# 로컬에서 실행
docker exec -it booster-kafka kafka-topics --create \
--bootstrap-server localhost:9092 \
--topic member-events \
--partitions 3 \
--replication-factor 1

docker exec -it booster-kafka kafka-topics --create \
--bootstrap-server localhost:9092 \
--topic member-events.DLT \
--partitions 1 \
--replication-factor 1
