# 📨 Storage Kafka Module (`libs/storage-kafka`)

## 1. 개요 (Overview)
`storage-kafka` 모듈은 MSA 환경에서 서비스 간 비동기 메시징을 처리하기 위한 **Kafka 공통 라이브러리**입니다.
Spring Kafka의 자동 설정(`KafkaAutoConfiguration`)에 전적으로 의존하지 않고, 프로젝트 전반의 **데이터 정합성(ISO-8601 날짜 포맷, Unknown Field 무시)**과 **타입 안정성**을 보장하기 위해 수동으로 빈을 구성했습니다.

### 핵심 설계 원칙
1.  **직렬화 일관성:** `common` 모듈의 `JsonUtils.MAPPER`를 강제 사용하여 Redis, DB, Kafka 간 데이터 포맷을 통일했습니다.
2.  **타입 안정성 (Type Safety):** 문자열 토픽 이름 대신 `KafkaTopic` Enum 사용을 강제하여 휴먼 에러를 방지합니다.
3.  **명시적 제어 (Explicit Control):** 보안 패키지(`Trusted Packages`) 설정과 오프셋 전략을 코드 레벨에서 명확히 제어합니다.

---

## 2. 모듈 구조 (Structure)

```text
libs/storage-kafka
├── src/main/java/com/booster/storage/kafka
│   ├── config
│   │   └── KafkaConfig.java       # Producer/Consumer Factory 수동 등록 & 직렬화 설정
│   └── core
│       ├── KafkaProducer.java     # KafkaTemplate을 감싼 Wrapper (Enum 사용 강제)
│       └── KafkaTopic.java        # 시스템 전체 토픽 이름 관리 Enum
└── src/main/resources
    └── META-INF/spring
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports # 자동 설정 등록