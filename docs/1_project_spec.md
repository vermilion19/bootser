# 프로젝트: Booster (Backend Skill-Up Project)

## 1. 프로젝트 개요
* **목표:** 고가용성/대규모 트래픽 아키텍처 실습
* **핵심 가치:**
    * **High Availability:** 대량의 트래픽에도 죽지 않는 서버
    * **Data Consistency:** MSA 환경에서의 데이터 정합성 보장 (Saga, Outbox)
    * **Concurrency Control:** 동시성 이슈 제어 (Redis, Lock)
* **기술 스택 (Bleeding Edge):**
    * **Language:** Java 25 (Preview/Latest)
    * **Framework:** Spring Boot 4.x (Snapshot/Milestone)
    * **Database:** PostgreSQL (Database per Service), Redis
    * **Messaging:** Kafka
    * **Architecture:** Monorepo Multi-Module (Apps/Libs 구조)

---

## 2. 모듈 구조 (Project Structure)

### 2.1. Apps (실행 가능한 서비스)
* `apps` 하위 모듈은 실행 가능한 Jar(BootJar)로 빌드됨.

| 모듈명 | 역할 | 비고 |
| :--- | :--- | :--- |
| **waiting-service** | 대기열 서비스 | 대기표 발급, 순서 관리, 입장 처리 (핵심 도메인) |
| **restaurant-service** | 식당 서비스 | 식당 정보 등록/수정, 영업 상태(Open/Close) 관리 |

### 2.2. Libs (공통 라이브러리)
* `libs` 하위 모듈은 일반 Jar로 빌드되며, `apps`에서 의존성으로 사용함.

| 모듈명 | 역할 | 비고 |
| :--- | :--- | :--- |
| **core-web** | 웹 공통 지원 | GlobalExceptionHandler, CommonResponse, Filter |
| **common** | 유틸리티 | 순수 자바 유틸리티 (날짜, 문자열 등) |
| **infra-kafka** | 메시징 인프라 | Kafka 설정, Producer/Consumer 추상화 |
| **storage-db** | DB 퍼시스턴스 | JPA 설정, BaseTimeEntity(Auditing), PostgreSQL Driver |
| **storage-redis**| 캐시/스토리지 | Redis 설정(Serializer), RedisUtils |

---
## 3. 기타 스펙
### 3.1 redis 분산 락 키 설계 명명 규칙
* 형식: {서비스명}:{도메인}:{행위}:{식별자}
* 예시: waiting-service:queue:lock:user-123
---

## 4. 데이터베이스 설계 (ERD) - PostgreSQL

```mermaid
erDiagram
    %% [Schema: db_restaurant] 식당 서비스
    RESTAURANT {
        bigint id PK "식당 ID"
        varchar name "식당 이름"
        int capacity "최대 수용 인원 (물리적)"
        int current_occupancy "현재 입장 인원 (동시성 제어 대상)"
        int max_waiting_limit "대기열 제한 수"
        varchar status "영업 상태 (OPEN, CLOSED, WAITING_CLOSED)"
        timestamp created_at
        timestamp updated_at
    }

    %% [Schema: db_waiting] 대기 서비스
    WAITING {
        bigint id PK "시스템 유니크 ID"
        bigint restaurant_id "식당 ID (논리적 참조)"
        varchar guest_phone "손님 전화번호 (식별자)"
        int party_size "일행 수 (구 head_count)"
        int waiting_number "식당별 대기 순번"
        varchar status "상태 (WAITING, ENTERED, CANCELED)"
        timestamp created_at
        timestamp updated_at
    }

    %% [Schema: db_waiting] 아웃박스
    WAITING_OUTBOX {
        bigint id PK
        varchar aggregate_type
        bigint aggregate_id
        varchar event_type
        jsonb payload "이벤트 내용 (JSONB)"
        varchar status "발행 상태"
        timestamp created_at
    }