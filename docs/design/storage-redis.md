# 📑 Storage-Redis Module Design Record

이 문서는 대규모 트래픽 대기열 시스템의 고가용성과 신뢰성을 보장하기 위한 `storage-redis` 모듈의 설계 의사결정 과정을 기록합니다.

## 📅 Update History
| 날짜 |    수정자     | 내용 | 상태 |
|:---:|:----------:|:---|:---:|
| 2026-01-02 | Juhong Kim | 모듈 초기 설정 및 분산 락/랭킹 리포지토리 검증 완료 | `COMPLETED` |

---

## 🛠 Tech Stack
- **Runtime:** Java 25 (Preview enabled)
- **Framework:** Spring Boot 4.0+
- **Library:** Redisson (Distributed Lock), Spring Data Redis
- **Test:** Embedded Redis (it.ozimov), JUnit 5

---

##  Key Technical Decisions

### 1️⃣ Redis Serialization 전략
* **결정:** `GenericJacksonJsonRedisSerializer` 사용
* **사유:** Spring Boot 4.0 표준 가이드에 따라 명칭이 통합된 최신 직렬화 클래스 채택.
* **해결:** `JavaTimeModule` 등록 및 `WRITE_DATES_AS_TIMESTAMPS` 비활성화를 통해 `LocalDateTime` 데이터를 ISO-8601 문자열 포맷으로 저장하여 가독성과 호환성 확보.

### 2️⃣ 분산 락(Distributed Lock) 설계
* **결정:** Redisson 기반의 `RLock` 활용
* **사유:** 선착순 진입 로직의 원자성(Atomicity) 보장을 위해 Pub/Sub 기반의 효율적인 분산 락 채택.
* **Troubleshooting:** * 테스트 시 쓰레드 재사용으로 인한 **재진입(Reentrancy)** 이슈 발생.
    * `CountDownLatch`와 `Thread.sleep`을 조합하여 락 점유 상태에서 타 쓰레드의 진입 차단을 명확히 검증하도록 테스트 코드 고도화.

### 3️⃣ 테스트 인프라 및 환경 격리
* **결정:** `Embedded Redis`를 이용한 로컬 메모리 테스트 환경 구성
* **사유:** 외부 도커(Docker) 의존성 없이도 CI/CD 및 로컬 환경에서 독립적인 테스트 실행 보장.
* **설정:** Java 25 환경의 네이티브 라이브러리 충돌 방지를 위해 `TestRedisConfig`에서 Redis 서버의 생명주기(`@PostConstruct`, `@PreDestroy`) 수동 관리.

### 4️⃣ 모듈 자동 설정(Auto-Configuration) 최적화
* **결정:** `spring.factories` 대신 `AutoConfiguration.imports` 방식 채택
* **구조:** `RedisModuleConfig` 클래스에 `@ComponentScan`을 적용하여 모듈 내 신규 빈(Bean) 추가 시 설정을 변경할 필요가 없는 구조 설계.

---

## 🧪 Verification Result (Test Cases)
- [x] **대기열 진입 및 순위 조회:** Sorted Set 기반의 순번 정합성 확인
- [x] **대기열 이탈:** 유저 삭제 시 즉각적인 랭킹 업데이트 확인
- [x] **분산 락 원자성:** 50개 동시 요청 시 단 하나의 쓰레드만 락 획득 성공 검증

---

## 📌 Next Steps
- [ ] Redis 장애 상황을 대비한 **Circuit Breaker** 및 **Fallback** 전략 수립
- [ ] 대기열 토큰(Queue Token) 발행 및 유효성 검증 로직 구현용한 모듈 최적화.