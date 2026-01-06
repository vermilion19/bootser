# Booster 프로젝트 명세서

## 1. 프로젝트 개요
**Booster**는 식당 대기열 및 알림을 관리하기 위해 설계된 마이크로서비스 기반 애플리케이션입니다. Spring Boot, Spring Cloud 및 다양한 저장소 기술(PostgreSQL, Redis, Kafka)을 활용하여 확장성과 신뢰성을 보장합니다.

## 2. 아키텍처
이 프로젝트는 다음과 같이 구분된 멀티 모듈 Gradle 구조를 따릅니다:
- **Apps**: 실행 가능한 마이크로서비스.
- **Infrastructure**: 게이트웨이 및 디스커버리와 같은 지원 서비스.
- **Libs**: 공통 기능을 위한 공유 라이브러리.

### 2.1. 모듈 구조
```
booster/
├── apps/
│   ├── waiting-service       # 고객 대기열 관리
│   ├── restaurant-service    # 식당 정보 관리
│   └── notification-service  # 알림 처리 (예: Slack)
├── infrastructure/
│   ├── discovery-service     # 서비스 디스커버리를 위한 Eureka Server
│   ├── gateway-service       # 라우팅을 위한 Spring Cloud Gateway
├── libs/
│   ├── common                # 공통 유틸리티 (Jackson, Apache Commons)
│   ├── core-web              # 웹 관련 공유 컴포넌트 (전역 예외 처리)
│   ├── storage-db            # JPA 및 QueryDSL 설정
│   ├── storage-redis         # Redis 설정 (Redisson, Embedded Redis)
│   └── storage-kafka         # Kafka 설정
└── playground/               # 실험용 코드
```

## 3. 마이크로서비스 상세

### 3.1. 인프라 (Infrastructure)
#### Discovery Service
- **포트**: 8761
- **기술**: Netflix Eureka Server
- **역할**: 모든 마이크로서비스를 위한 서비스 레지스트리.

#### Gateway Service
- **포트**: 6000
- **기술**: Spring Cloud Gateway
- **라우트**:
  - `/waiting/**` -> `waiting-service`
  - `/restaurants/**` -> `restaurant-service`
- **기능**: 로드 밸런싱 (`lb://`), Prefix 제거.

### 3.2. 애플리케이션 서비스 (Application Services)
#### Waiting Service
- **포트**: 랜덤 (0)
- **의존성**: `core-web`, `storage-db`, `storage-redis`, `storage-kafka`
- **기능**:
  - 대기열 관리.
  - 분산 스케줄링을 위해 Redis와 **ShedLock** 사용.
  - `waiting-events` Kafka 토픽으로 이벤트 발행.
  - Eureka에 등록.

#### Restaurant Service
- **포트**: 랜덤 (0)
- **의존성**: `core-web`, `storage-db`, `storage-kafka`, `Thymeleaf`
- **기능**:
  - 식당 데이터 관리.
  - `waiting-events` 소비 (그룹 ID: `waiting-service-group-out`).
  - Eureka에 등록.

#### Notification Service
- **포트**: 8083
- **의존성**: `core-web`, `storage-db`, `storage-kafka`
- **기능**:
  - `waiting-events` 소비 (그룹 ID: `notification-service-group`).
  - Kafka 컨슈머를 위한 **배치 리스너(Batch Listener)** 지원.
  - Webhook을 통한 Slack 연동.

## 4. 기술 스택
- **언어**: Java 25 (Virtual Threads 활성화)
- **프레임워크**: Spring Boot 4.0
- **빌드 도구**: Gradle
- **서비스 디스커버리**: Netflix Eureka
- **API 게이트웨이**: Spring Cloud Gateway
- **데이터베이스**: PostgreSQL (운영), H2 (테스트)
- **캐싱/락**: Redis (Redisson, ShedLock)
- **메시징**: Apache Kafka
- **ORM**: JPA (Hibernate), QueryDSL

## 5. 주요 설정
- **Kafka**:
  - 부트스트랩 서버: `localhost:9092`
  - 토픽: `waiting-events`
- **모니터링**: Actuator 활성화 (Health, Info, Metrics, Prometheus).

## 6. 개발 및 테스트
- **Test Fixtures**: `storage-db` 및 `storage-redis`에서 테스트 설정 공유를 위해 사용.
- **Testcontainers**: PostgreSQL 및 Redis 테스트를 위해 통합됨.
- **Embedded Redis**: `waiting-service`에서 로컬 개발용으로 사용.
