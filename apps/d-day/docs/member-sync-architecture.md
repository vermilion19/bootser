# D-Day Service 회원 동기화 아키텍처

## 개요

d-day-service에서 사용자별 기능(즐겨찾기, 커스텀 D-Day, 알림 설정)을 구현하기 위한 회원 정보 관리 전략.

---

## 1. 왜 회원 테이블이 필요한가?

### 문제 상황

d-day-service에 회원 관련 테이블 없이 `user_id`만 사용할 경우:

| 문제 | 설명 |
|------|------|
| 참조 무결성 불가 | `user_id`가 실제 존재하는지 DB 레벨에서 검증 불가 |
| 고아 데이터 발생 | 회원 탈퇴 시 관련 D-Day 데이터 정리 어려움 |
| 조회 성능 저하 | 사용자 닉네임 표시 시 매번 auth-service 호출 필요 |
| 장애 전파 | auth-service 장애 시 d-day-service 조회도 실패 |

### 결론

**최소 참조 테이블(MemberReference)** 필요

---

## 2. 데이터 설계

### 원칙: 최소 참조 테이블 (Thin Copy)

```
┌────────────────────────────────────────────────────────────────┐
│                 auth-service (Source of Truth)                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Member                                                   │  │
│  │  - id (PK)                                               │  │
│  │  - email                                                 │  │
│  │  - password (암호화)                                      │  │
│  │  - nickname                                              │  │
│  │  - profile_image_url                                     │  │
│  │  - role                                                  │  │
│  │  - status (ACTIVE, SUSPENDED, DELETED)                   │  │
│  │  - created_at, updated_at                                │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
                    이벤트 발행 (Kafka)
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│                 d-day-service (Local Copy)                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MemberReference (최소 참조 테이블)                        │  │
│  │  - id (PK, auth-service의 member_id와 동일)              │  │
│  │  - nickname (표시용, 비정규화)                            │  │
│  │  - status (ACTIVE/DELETED - 탈퇴 처리용)                 │  │
│  │  - synced_at (마지막 동기화 시점)                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### 저장할 정보 vs 저장하지 않을 정보

| 저장 O | 저장 X | 이유 |
|--------|--------|------|
| id | email | 인증 정보는 auth 전담 |
| nickname | password | 보안 데이터 분산 금지 |
| status | role | 권한은 Gateway/Auth에서 처리 |
| synced_at | profile_image_url | 필요 시 CDN URL만 저장 |

---

## 3. 엔티티 구조

```
MemberReference (1) ─┬─ (N) CustomDday          사용자 커스텀 D-Day
                     ├─ (N) DdayFavorite        즐겨찾기
                     └─ (1) NotificationSetting  알림 설정
```

### MemberReference

```java
@Entity
@Table(name = "member_reference")
public class MemberReference {
    @Id
    private Long id;  // auth-service member_id와 동일 (auto-generate X)

    private String nickname;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;  // ACTIVE, DELETED

    private LocalDateTime syncedAt;
}
```

### CustomDday (사용자 커스텀 D-Day)

```java
@Entity
@Table(name = "custom_dday")
public class CustomDday {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberReference member;

    private String title;
    private LocalDate targetDate;
    private String memo;
    private boolean deleted;  // Soft Delete
}
```

### DdayFavorite (즐겨찾기)

```java
@Entity
@Table(name = "dday_favorite")
public class DdayFavorite {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberReference member;

    private Long specialDayId;  // SpecialDay FK
    private LocalDateTime createdAt;
}
```

### NotificationSetting (알림 설정)

```java
@Entity
@Table(name = "notification_setting")
public class NotificationSetting {
    @Id
    private Long memberId;  // MemberReference와 1:1

    private boolean enabled;
    private int remindDaysBefore;  // D-Day 며칠 전 알림
}
```

---

## 4. 동기화 전략

### 권장: 혼합 방식 (이벤트 기반 + API Fallback)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         동기화 아키텍처                                  │
└─────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │ 이벤트 기반 (Kafka) - 주요 동기화 채널                        │
  │                                                             │
  │  MemberCreatedEvent  → MemberReference INSERT               │
  │  MemberUpdatedEvent  → MemberReference UPDATE               │
  │  MemberDeletedEvent  → status=DELETED + Soft Delete 처리    │
  └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ API 호출 (Feign) - 보조/복구 채널                            │
  │                                                             │
  │  Case 1: 이벤트 유실 복구                                    │
  │    - 요청 시 member_id가 로컬에 없으면 API로 조회 후 저장     │
  │                                                             │
  │  Case 2: 정합성 검증 배치                                    │
  │    - 주기적으로 auth-service와 데이터 비교/보정              │
  └─────────────────────────────────────────────────────────────┘
```

### 이벤트 토픽 설계

```
Topic: member-events
Partition Key: member_id (순서 보장)
```

#### MemberCreatedEvent

```json
{
  "eventId": "uuid",
  "eventType": "MEMBER_CREATED",
  "timestamp": "2026-02-06T10:00:00",
  "payload": {
    "memberId": 12345,
    "nickname": "홍길동"
  }
}
```

#### MemberUpdatedEvent

```json
{
  "eventId": "uuid",
  "eventType": "MEMBER_UPDATED",
  "timestamp": "2026-02-06T11:00:00",
  "payload": {
    "memberId": 12345,
    "nickname": "김철수",
    "changedFields": ["nickname"]
  }
}
```

#### MemberDeletedEvent

```json
{
  "eventId": "uuid",
  "eventType": "MEMBER_DELETED",
  "timestamp": "2026-02-06T12:00:00",
  "payload": {
    "memberId": 12345,
    "deletedAt": "2026-02-06T12:00:00"
  }
}
```

---

## 5. 이벤트 처리 흐름

### Kafka Consumer 처리

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     이벤트 처리 흐름                                     │
└─────────────────────────────────────────────────────────────────────────┘

  Kafka Consumer                    MemberSyncService
       │                                  │
       │  MemberCreatedEvent              │
       │─────────────────────────────────>│
       │                                  │ 1. 멱등성 체크 (eventId)
       │                                  │ 2. MemberReference 저장
       │                                  │ 3. 처리 완료 기록
       │                                  │
       │  MemberDeletedEvent              │
       │─────────────────────────────────>│
       │                                  │ 1. MemberReference.status = DELETED
       │                                  │ 2. 관련 데이터 Soft Delete
       │                                  │    - CustomDday.deleted = true
       │                                  │    - DdayFavorite 삭제
       │                                  │    - NotificationSetting 삭제
```

### API Fallback 처리

```
  CustomDdayService                 AuthServiceClient (Feign)
       │                                  │
       │  커스텀 D-Day 등록 요청           │
       │  (member_id: 999)                │
       │                                  │
       │  MemberReference 조회            │
       │  → 없음 (이벤트 유실)             │
       │                                  │
       │  GET /internal/members/999       │
       │─────────────────────────────────>│
       │                                  │
       │  회원 정보 응답                   │
       │<─────────────────────────────────│
       │                                  │
       │  MemberReference 저장 (복구)      │
       │  커스텀 D-Day 등록 진행           │
```

---

## 6. 동기화 방식 비교

| 기준 | 이벤트 기반 | API 호출 | 혼합 (권장) |
|------|-------------|----------|-------------|
| 일관성 | Eventual | Strong | Eventual + 보정 |
| 결합도 | 낮음 | 높음 | 중간 |
| 장애 전파 | 없음 | 있음 | 최소화 |
| 네트워크 비용 | 낮음 | 높음 | 최적화 |
| 이벤트 유실 대응 | 별도 보정 필요 | 해당 없음 | API로 복구 |

---

## 7. 핵심 설계 원칙

| 원칙 | 적용 |
|------|------|
| Single Source of Truth | auth-service의 Member가 원본 |
| Eventual Consistency | 일시적 불일치 허용, 최종 일관성 보장 |
| 멱등성 | eventId로 중복 처리 방지 |
| Graceful Degradation | 이벤트 유실 시 API Fallback |
| 최소 데이터 복제 | id, nickname, status만 저장 |

---

## 8. 구현 체크리스트

### Phase 1: 기본 동기화

- [ ] MemberReference 엔티티 생성
- [ ] MemberStatus enum (ACTIVE, DELETED)
- [ ] MemberCreatedEvent Consumer 구현
- [ ] MemberUpdatedEvent Consumer 구현
- [ ] MemberDeletedEvent Consumer 구현

### Phase 2: 핵심 기능

- [ ] CustomDday 엔티티 (member_id FK)
- [ ] DdayFavorite 엔티티
- [ ] NotificationSetting 엔티티
- [ ] 각 기능별 Service, Repository

### Phase 3: 안정성 강화

- [ ] AuthServiceClient (Feign) 구현
- [ ] API Fallback 로직 구현
- [ ] 정합성 검증 배치 Job
- [ ] 모니터링 (동기화 지연, 유실 감지)

---

## 9. 관련 파일 경로 (예상)

```
apps/d-day/d-day-service/src/main/java/com/booster/dday/
├── member/
│   ├── domain/
│   │   ├── MemberReference.java
│   │   ├── MemberStatus.java
│   │   └── MemberReferenceRepository.java
│   ├── application/
│   │   └── MemberSyncService.java
│   ├── event/
│   │   └── MemberEventConsumer.java
│   └── infrastructure/
│       └── AuthServiceClient.java
│
├── favorite/
│   ├── domain/
│   │   ├── DdayFavorite.java
│   │   └── DdayFavoriteRepository.java
│   └── application/
│       └── FavoriteService.java
│
├── custom/
│   ├── domain/
│   │   ├── CustomDday.java
│   │   └── CustomDdayRepository.java
│   └── application/
│       └── CustomDdayService.java
│
└── notification/
    ├── domain/
    │   ├── NotificationSetting.java
    │   └── NotificationSettingRepository.java
    └── application/
        └── NotificationSettingService.java
```
