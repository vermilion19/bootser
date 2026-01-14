# Booster - 대용량 트래픽 대응 예약/웨이팅 플랫폼

## 프로젝트 개요

대용량 트래픽에서도 안정적으로 동작하고 Scale-out이 용이한 MSA 기반 웨이팅 시스템.
DDD(Domain-Driven Design) 원칙을 따르며, 이벤트 기반 아키텍처를 채택.

## 기술 스택

- **Language**: Java 25
- **Framework**: Spring Boot 4.0.1, Spring Cloud 2025.1.0
- **Database**: PostgreSQL (운영), H2 (테스트)
- **Cache**: Redis (Redisson)
- **Message Broker**: Apache Kafka
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Resilience**: Resilience4j (CircuitBreaker, Bulkhead, RateLimiter)
- **Observability**: Prometheus, Grafana, Loki, Tempo

## 프로젝트 구조

```
booster/
├── apps/                    # 실행 가능한 서비스 모듈
│   ├── waiting-service/     # 웨이팅 서비스 (핵심)
│   ├── restaurant-service/  # 식당 서비스
│   ├── notification-service/# 알림 서비스 (Kafka Consumer)
│   └── auth-service/        # 인증 서비스
│
├── libs/                    # 공유 라이브러리 모듈
│   ├── common/              # 공통 유틸 (Jackson, Commons)
│   ├── core-web/            # 웹 공통 (DTO, Exception, Event)
│   ├── storage-db/          # JPA 설정, BaseEntity
│   ├── storage-redis/       # Redis 설정, Repository
│   ├── storage-kafka/       # Kafka 설정, Producer/Consumer
│   ├── core-observability/  # 로깅, 메트릭, 트레이싱
│   └── core-resilience/     # CircuitBreaker, Bulkhead 설정
│
├── infrastructure/          # 인프라 서비스
│   ├── discovery-service/   # Eureka Server
│   └── gateway-service/     # API Gateway
│
├── dockers/                 # Docker Compose 설정
│   └── obaservability/      # Prometheus, Grafana, Loki, Tempo
│
└── playground/              # 실험/학습용
```

## 모듈별 DDD 패키지 구조

각 서비스 모듈은 아래 구조를 따름:

```
{service}/src/main/java/com/booster/{service}/
├── {domain-name}/
│   ├── domain/              # 도메인 레이어
│   │   ├── Entity.java      # 엔티티 (Rich Domain Model)
│   │   ├── Repository.java  # Repository 인터페이스
│   │   ├── ValueObject.java # VO, Enum
│   │   └── outbox/          # Outbox 패턴용 엔티티
│   │
│   ├── application/         # 응용 레이어
│   │   ├── Service.java     # 비즈니스 로직
│   │   ├── Facade.java      # 복합 트랜잭션 조율
│   │   └── dto/             # Command, Event DTO
│   │
│   ├── web/                 # 표현 레이어
│   │   ├── controller/      # REST Controller
│   │   ├── dto/request/     # Request DTO
│   │   ├── dto/response/    # Response DTO
│   │   └── advice/          # Exception Handler
│   │
│   ├── event/               # 이벤트 레이어
│   │   ├── EventListener.java    # Spring Event 처리
│   │   ├── EventConsumer.java    # Kafka Consumer
│   │   └── OutboxMessageRelay.java # Outbox 발행
│   │
│   ├── infrastructure/      # 인프라 레이어
│   │   └── Client.java      # 외부 서비스 호출 (Feign)
│   │
│   └── exception/           # 도메인 예외
│
└── config/                  # 설정 클래스
```

## 주요 아키텍처 패턴

### 1. Outbox Pattern
- 트랜잭션 내에서 `OutboxEvent` 테이블에 이벤트 저장
- 별도 스케줄러(`OutboxMessageRelay`)가 Kafka로 발행
- 이벤트 발행 보장 (At-least-once)

### 2. Transactional Outbox + Polling Publisher
```java
// 트랜잭션 내에서 이벤트 저장
outboxRepository.save(OutboxEvent.builder()
    .aggregateType("WAITING")
    .aggregateId(waiting.getId())
    .eventType("CALLED")
    .payload(jsonPayload)
    .build());

// 스케줄러가 polling하여 Kafka 발행
@Scheduled(fixedDelay = 3000)
public void publishEvents() { ... }
```

### 3. Redis를 활용한 실시간 순위 관리
- Redisson SortedSet으로 대기 순번 실시간 조회
- Self-Healing: Redis 유실 시 DB에서 복구

### 4. Resilience 패턴
- **CircuitBreaker**: 외부 서비스 장애 전파 방지
- **Bulkhead**: 스레드 격리로 리소스 보호
- **RateLimiter**: 요청 제한

### 5. 분산 락
- **ShedLock + Redis**: 스케줄러 중복 실행 방지

## 자주 쓰는 명령어

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:waiting-service:build

# 테스트 실행
./gradlew test

# 특정 서비스 실행
./gradlew :apps:waiting-service:bootRun

# 인프라 서비스 먼저 실행 (순서 중요)
./gradlew :infrastructure:discovery-service:bootRun
./gradlew :infrastructure:gateway-service:bootRun

# Docker Compose (Observability)
cd dockers/obaservability && docker-compose up -d
```

## 코딩 컨벤션

### 일반 규칙
- 들여쓰기: 4칸 (스페이스)
- 줄바꿈: LF
- 인코딩: UTF-8
- Lombok 적극 활용 (`@RequiredArgsConstructor`, `@Getter`, `@Builder`)

### 네이밍 규칙
- 클래스: PascalCase (`WaitingService`)
- 메서드/변수: camelCase (`registerWaiting`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 패키지: lowercase (`application`, `domain`)

### Domain Entity 규칙
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수
- 정적 팩토리 메서드 사용 (`Waiting.create(...)`)
- Setter 금지, 비즈니스 메서드로 상태 변경 (`waiting.call()`, `waiting.cancel()`)
- 도메인 유효성 검증은 엔티티 내부에서 수행

### DTO 규칙
- Request/Response DTO는 `record` 사용 권장
- `@Builder` 또는 정적 팩토리 메서드 (`of()`) 사용

### Service 규칙
- `@Transactional` 클래스 레벨 선언
- 복합 비즈니스 로직은 `Facade` 패턴 사용
- 이벤트 발행은 `ApplicationEventPublisher` 사용

### 예외 처리
- 도메인별 커스텀 예외 생성 (`DuplicateWaitingException`)
- `@RestControllerAdvice`로 전역 예외 처리
- ErrorCode enum으로 에러 코드 관리

## 테스트 규칙

- 단위 테스트: `src/test/java`
- 통합 테스트: `@SpringBootTest` 사용
- 테스트 픽스처: `testFixtures` 활용 (`libs/storage-db`, `libs/storage-redis`)
- 테스트 DB: H2, 테스트 Redis: Embedded Redis
- spring boot4 에 맞는 의존성사용
- 엣지케이스, 예외사항도 테스트코드 만들것

## Scale-out 고려사항

1. **Stateless 서비스**: 세션은 Redis에 저장
2. **ID 생성**: Snowflake 알고리즘 (분산 환경에서 유일성 보장)
3. **분산 락**: Redis 기반 ShedLock
4. **이벤트 기반 통신**: Kafka로 서비스 간 느슨한 결합
5. **캐시 전략**: Redis Cache-Aside 패턴

## 모듈 의존성 규칙

```
apps/* → libs/* (OK)
libs/* → libs/common (OK)
libs/* → apps/* (FORBIDDEN)
apps/* → apps/* (FORBIDDEN, Kafka/Feign으로 통신)
```

## 참고 문서

- `/docs/` 디렉토리 참고
- Gradle 모듈 초기화: `init-service-module.sh`, `init-lib-module.sh`

# Agent Modes
이 프로젝트에서는 아래 키워드로 시작하면 해당 전문가 모드로 동작할 것.

1. [ARCHITECT]: 코드를 짜지 말고, 파일 구조와 인터페이스 설계만 출력해.
2. [DBA]: 쿼리와 스키마 변경 스크립트(DDL)에만 집중해. 인덱스 전략을 반드시 포함해.
3. [TESTER]: 주어진 코드에 대한 JUnit 테스트 케이스 목록과 엣지 케이스만 나열해.
4. [CODER]: ARCHITECT 가 설계한 내용 바탕으로 실제 구현 코드 작성해.