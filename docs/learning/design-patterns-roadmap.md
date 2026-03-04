# 디자인 패턴 (Design Patterns) 학습 로드맵

## 개요
- **주제**: GoF 디자인 패턴 + 실무에서 자주 쓰이는 아키텍처 패턴
- **예상 학습 범위**: Phase 1~4
- **선행 지식**: Java OOP (상속, 다형성, 인터페이스), SOLID 원칙 기초
- **관련 기술 스택**: Spring Framework, JPA, Kafka, Redis

---

## Phase 1: 기초 (Foundation)

### 학습 목표
- 디자인 패턴이 왜 필요한지 이해한다
- 3가지 분류(생성/구조/행위)를 구분할 수 있다
- 가장 자주 쓰이는 5개 패턴을 직접 구현할 수 있다

### 핵심 개념

| 개념 | 설명 |
|------|------|
| 디자인 패턴 | 반복되는 설계 문제에 대한 재사용 가능한 해결책 템플릿 |
| GoF (Gang of Four) | 23개 패턴을 정리한 원서의 저자 4인. 패턴의 표준 분류 기준 |
| 생성 패턴 (Creational) | 객체 생성 방식을 캡슐화 — Factory, Builder, Singleton 등 |
| 구조 패턴 (Structural) | 클래스/객체를 조합하여 더 큰 구조 형성 — Adapter, Decorator, Proxy 등 |
| 행위 패턴 (Behavioral) | 객체 간 책임 분배와 통신 방식 — Strategy, Observer, Template Method 등 |
| SOLID 원칙 | 패턴의 근간이 되는 5가지 객체지향 설계 원칙 |

### 패턴을 왜 배우는가?

```
문제: 코드 변경이 생길 때마다 여러 곳을 수정해야 한다
원인: 변경되는 부분과 변경되지 않는 부분이 분리되지 않았다
해결: 패턴을 통해 "변경의 이유"를 기준으로 구조를 나눈다
```

> 패턴은 "이 상황에서 이렇게 구조를 잡으면 나중에 변경이 쉬워진다"는 경험의 압축이다.

### 학습 항목

- [ ] **1-1. SOLID 원칙 복습**
  - SRP(단일 책임), OCP(개방-폐쇄), LSP(리스코프 치환), ISP(인터페이스 분리), DIP(의존 역전)
  - 패턴은 SOLID 원칙의 구체적 구현 방법이므로 원칙을 먼저 체화해야 한다
  - 실습: 기존 코드에서 SRP 위반을 찾아 분리해보기

- [ ] **1-2. Strategy 패턴**
  - 알고리즘을 인터페이스로 분리하여 런타임에 교체 가능하게 만든다
  - 핵심: `if-else`/`switch`로 분기하는 코드를 다형성으로 대체
  ```java
  // Before: 분기문이 계속 늘어남
  if (type.equals("KAKAO")) { sendKakao(); }
  else if (type.equals("SMS")) { sendSms(); }

  // After: Strategy
  public interface NotificationStrategy {
      void send(String message, String target);
  }

  // 새 채널 추가 시 새 클래스만 만들면 됨 (OCP)
  public class KakaoStrategy implements NotificationStrategy { ... }
  public class SmsStrategy implements NotificationStrategy { ... }
  ```
  - 실습: 알림 발송 방식을 Strategy로 분리해보기

- [ ] **1-3. Factory Method 패턴**
  - 객체 생성 로직을 서브클래스에 위임하여 생성과 사용을 분리
  - 핵심: `new`를 직접 호출하지 않고, 팩토리에 위임
  ```java
  // 정적 팩토리 메서드 (가장 실무에서 흔한 형태)
  public class Waiting {
      public static Waiting create(Long restaurantId, Long userId) {
          Waiting w = new Waiting();
          w.restaurantId = restaurantId;
          w.userId = userId;
          w.status = WaitingStatus.WAITING;
          w.validate();
          return w;
      }
  }
  ```
  - Booster 프로젝트에서 이미 사용 중: 도메인 엔티티의 `create()`, DTO의 `from()`/`of()`
  - 실습: 도메인 엔티티에 정적 팩토리 메서드를 적용해보기

- [ ] **1-4. Singleton 패턴**
  - 인스턴스가 하나만 존재하도록 보장
  - Java에서 직접 구현보다 Spring Bean(`@Component`)을 사용하는 것이 표준
  ```java
  // 직접 구현 (알아두되, 실무에서는 거의 안 씀)
  public class ConnectionPool {
      private static final ConnectionPool INSTANCE = new ConnectionPool();
      private ConnectionPool() {}
      public static ConnectionPool getInstance() { return INSTANCE; }
  }

  // Spring 방식 (사실상 Singleton)
  @Component
  public class ConnectionPool { ... }
  ```
  - 주의: 멀티스레드 환경에서의 동기화 문제, 테스트 어려움

- [ ] **1-5. Observer 패턴**
  - 상태 변경 시 의존 객체들에게 자동 통지
  - Spring의 `ApplicationEventPublisher`가 대표적 구현체
  ```java
  // Spring Event = Observer 패턴
  publisher.publishEvent(new WaitingCalledEvent(waitingId));

  @EventListener
  public void handleWaitingCalled(WaitingCalledEvent event) {
      // 알림 발송, 로그 기록 등
  }
  ```
  - Booster 프로젝트에서 이미 사용 중: Outbox 패턴의 이벤트 발행

- [ ] **1-6. Builder 패턴**
  - 복잡한 객체의 생성 과정을 단계별로 분리
  - Lombok `@Builder`로 자동 생성하는 것이 실무 표준
  ```java
  OutboxEvent.builder()
      .aggregateType("WAITING")
      .aggregateId(waiting.getId())
      .eventType("CALLED")
      .payload(jsonPayload)
      .build();
  ```
  - 실습: 필수/선택 파라미터가 많은 객체에 Builder 적용해보기

### 추천 리소스
- 서적: 「Head First Design Patterns」 (입문용, 그림 중심)
- 서적: 「GoF의 디자인 패턴」 (레퍼런스, 필요할 때 찾아보기)
- 웹: [Refactoring Guru - Design Patterns](https://refactoring.guru/design-patterns) (시각적 설명 우수)

---

## Phase 2: 실전 적용 (Practice)

### 학습 목표
- Spring 프레임워크가 내부적으로 사용하는 패턴을 식별할 수 있다
- 프로젝트에서 패턴 적용이 필요한 상황을 판단할 수 있다
- 안티패턴을 인식하고 피할 수 있다

### 학습 항목

- [ ] **2-1. Template Method 패턴**
  - 알고리즘의 골격을 상위 클래스에서 정의하고, 세부 단계를 하위에서 구현
  - Spring의 `JdbcTemplate`, `RestTemplate`, `AbstractRoutingDataSource` 등이 이 패턴
  ```java
  // 추상 클래스에서 흐름 정의
  public abstract class AbstractSyncService {
      public final int sync(int year, CountryCode code) {
          List<?> data = fetchExternal(year, code);  // 단계 1
          List<?> filtered = filterNew(data);          // 단계 2
          return saveAll(filtered);                    // 단계 3
      }

      protected abstract List<?> fetchExternal(int year, CountryCode code);
      protected abstract List<?> filterNew(List<?> data);
      protected abstract int saveAll(List<?> entities);
  }
  ```
  - Strategy와 비교: Template Method는 상속 기반, Strategy는 구성(Composition) 기반
  - 실습: Booster의 SyncService 로직을 Template Method로 리팩토링 해보기

- [ ] **2-2. Decorator 패턴**
  - 기존 객체에 동적으로 새 기능을 추가 (래핑)
  - Java I/O Stream이 대표적: `new BufferedReader(new InputStreamReader(System.in))`
  ```java
  // 캐싱을 Decorator로 적용하는 예
  public interface SpecialDayReader {
      List<SpecialDay> findAll(CountryCode code);
  }

  public class DbSpecialDayReader implements SpecialDayReader { ... }

  public class CachingSpecialDayReader implements SpecialDayReader {
      private final SpecialDayReader delegate;
      private final Cache cache;

      public List<SpecialDay> findAll(CountryCode code) {
          return cache.getOrLoad(code, () -> delegate.findAll(code));
      }
  }
  ```
  - Spring `@Transactional`, `@Cacheable`, `@Async`가 AOP를 통한 Decorator 역할

- [ ] **2-3. Adapter 패턴**
  - 호환되지 않는 인터페이스를 변환하여 함께 동작하게 한다
  ```java
  // 외부 API 응답 → 내부 도메인 모델 변환
  // NagerHolidayDto.toEntity()가 이미 Adapter 역할
  public record NagerHolidayDto(String name, String date, ...) {
      public SpecialDay toEntity(CountryCode countryCode) {
          return SpecialDay.create(
              this.name,
              LocalDate.parse(this.date),
              countryCode,
              SpecialDayCategory.PUBLIC_HOLIDAY
          );
      }
  }
  ```
  - Booster의 `NagerDateClient`가 외부 API를 내부 인터페이스에 맞추는 Adapter

- [ ] **2-4. Facade 패턴**
  - 복잡한 서브시스템을 단순한 인터페이스로 감싸서 제공
  ```java
  // Booster에서 이미 사용 중인 Facade
  @Service
  public class WaitingFacade {
      private final WaitingService waitingService;
      private final NotificationService notificationService;
      private final QueueService queueService;

      // 여러 서비스의 조합을 하나의 메서드로 제공
      public void callNext(Long restaurantId) {
          Waiting w = waitingService.callNext(restaurantId);
          queueService.updatePosition(w);
          notificationService.sendCallNotification(w);
      }
  }
  ```
  - Service와 Facade의 차이: Service는 단일 도메인 로직, Facade는 여러 Service를 조율

- [ ] **2-5. Proxy 패턴**
  - 실제 객체에 대한 접근을 제어하는 대리인
  - Spring AOP의 핵심 메커니즘 (JDK Dynamic Proxy, CGLIB)
  ```
  클라이언트 → Proxy(트랜잭션 시작) → 실제 서비스 → Proxy(트랜잭션 커밋)
  ```
  - `@Transactional`, `@Cacheable`이 동작하려면 반드시 프록시를 통해 호출되어야 함
  - 주의: 같은 클래스 내 `this.method()` 호출 시 프록시를 우회하여 AOP가 적용되지 않음

- [ ] **2-6. 안티패턴 인식**

  | 안티패턴 | 증상 | 해결 패턴 |
  |---------|------|----------|
  | God Class | 한 클래스가 1000줄 이상, 책임이 5개 이상 | Facade + 역할별 Service 분리 |
  | Spaghetti Code | if-else/switch 분기 10개 이상 | Strategy 또는 State |
  | Copy-Paste | 비슷한 코드가 3곳 이상 중복 | Template Method 또는 공통 추출 |
  | Premature Abstraction | 사용처가 1개인데 인터페이스/추상화 도입 | 삭제. 중복이 3회 이상 발생할 때 추상화 |
  | Singleton 남용 | 전역 상태 공유로 테스트 불가 | DI(의존성 주입)로 전환 |

### 추천 리소스
- 서적: 「Effective Java 3/E」 (Joshua Bloch) — 아이템 1~5가 생성 패턴의 실전 적용
- 서적: 「Clean Code」 — 패턴을 적용하기 전에 기본 코드 품질부터

---

## Phase 3: 심화 (Advanced)

### 학습 목표
- 패턴 간 관계와 트레이드오프를 설명할 수 있다
- 분산 시스템에서 사용되는 아키텍처 패턴을 이해한다
- 면접에서 패턴에 대해 깊이 있는 답변을 할 수 있다

### 학습 항목

- [ ] **3-1. State 패턴**
  - 객체의 내부 상태에 따라 행동이 변경되는 경우
  ```java
  // Booster의 Waiting 상태 전이가 State 패턴 적용 후보
  // Before: 엔티티 내부에 상태별 if문
  public void call() {
      if (this.status != WaitingStatus.WAITING) {
          throw new IllegalStateException();
      }
      this.status = WaitingStatus.CALLED;
  }

  // After: State 패턴 (상태가 5개 이상이고, 전이 규칙이 복잡할 때 고려)
  public interface WaitingState {
      WaitingState call(Waiting context);
      WaitingState cancel(Waiting context);
  }
  ```
  - 판단 기준: 상태가 3개 이하이고 전이가 단순하면 if문이 낫다. 과도한 적용 주의

- [ ] **3-2. Chain of Responsibility 패턴**
  - 요청을 처리할 수 있는 핸들러 체인을 구성
  - Spring Security의 `FilterChain`, Servlet의 `Filter`가 대표적
  ```
  요청 → AuthFilter → RateLimitFilter → LogFilter → Controller
  ```
  - 각 필터는 다음 필터의 존재를 모름 → 느슨한 결합

- [ ] **3-3. 분산 시스템 패턴 — Outbox Pattern**
  - Booster에서 이미 사용 중인 핵심 패턴
  ```
  [서비스] → 트랜잭션 { DB 저장 + Outbox 테이블 저장 }
               ↓
  [스케줄러] → Outbox 조회 → Kafka 발행 → 상태 업데이트
  ```
  - 해결하는 문제: DB 커밋은 성공했지만 Kafka 발행이 실패하는 이중 쓰기(Dual Write) 문제
  - 트레이드오프: At-least-once 보장 → Consumer 측 멱등성(Idempotency) 필요

- [ ] **3-4. 분산 시스템 패턴 — Circuit Breaker**
  - 외부 서비스 장애가 전파되지 않도록 차단
  ```
  CLOSED(정상) → 실패 임계값 초과 → OPEN(차단) → 일정 시간 후 → HALF_OPEN(시도) → 성공 → CLOSED
  ```
  - Booster의 Resilience4j 설정과 연계
  - State 패턴의 실전 적용 사례 (내부적으로 CLOSED/OPEN/HALF_OPEN 상태 전이)

- [ ] **3-5. 분산 시스템 패턴 — CQRS (Command Query Responsibility Segregation)**
  - 쓰기 모델과 읽기 모델을 분리
  ```
  Command (쓰기) → DB(PostgreSQL) → 이벤트 발행
  Query (읽기)  → Cache(Redis) 또는 별도 읽기 DB
  ```
  - Booster에서 Redis SortedSet으로 대기 순번을 조회하는 것이 CQRS의 일부

- [ ] **3-6. 패턴 간 비교와 선택 기준**

  | 상황 | 추천 패턴 | 이유 |
  |------|----------|------|
  | 알고리즘 교체가 필요 | Strategy | 구성(Composition) 기반, 런타임 교체 |
  | 처리 흐름은 같고 세부만 다름 | Template Method | 상속 기반, 골격 재사용 |
  | 객체에 기능을 동적 추가 | Decorator | 래핑으로 기존 코드 수정 없이 확장 |
  | 외부 시스템과 연동 | Adapter | 인터페이스 불일치 해결 |
  | 복잡한 서브시스템 단순화 | Facade | 진입점을 하나로 줄임 |
  | 상태에 따라 행동이 다름 | State | 상태 전이 로직 캡슐화 |
  | 요청을 여러 핸들러가 처리 | Chain of Responsibility | 핸들러 추가/제거 유연 |

### 면접 빈출 질문

- **Q: Strategy vs Template Method 차이는?**
  - Strategy는 구성(Composition), Template Method는 상속. Strategy가 더 유연하지만 객체 수가 늘어남. Template Method는 코드 재사용에 유리하지만 상속 계층이 깊어질 수 있음.

- **Q: 왜 Singleton을 직접 구현하지 않고 Spring Bean을 쓰는가?**
  - 직접 구현 시 테스트 어려움(목 교체 불가), 멀티스레드 동기화 복잡성, 전역 상태 오염. Spring DI는 이 문제들을 컨테이너 레벨에서 해결.

- **Q: Proxy 패턴에서 self-invocation 문제란?**
  - 같은 클래스 내부에서 `this.method()` 호출 시 프록시를 거치지 않아 `@Transactional`, `@Cacheable` 등 AOP가 동작하지 않음. 해결: 별도 Bean으로 분리하거나 `self` 주입.

- **Q: Observer 패턴의 단점은?**
  - 이벤트 발행자가 구독자 실행 순서를 제어할 수 없음. 디버깅 시 이벤트 흐름 추적이 어려움. 순환 의존 발생 가능.

- **Q: Outbox 패턴에서 이벤트 중복 발행을 어떻게 처리하는가?**
  - Producer 측: Outbox 테이블의 상태 컬럼(`PENDING` → `PUBLISHED`)으로 중복 발행 최소화. Consumer 측: 멱등 키(Idempotency Key)로 중복 처리 방지.

- **Q: CQRS를 도입하면 무엇이 복잡해지는가?**
  - 데이터 일관성이 Eventual Consistency로 변경됨. 읽기/쓰기 모델 동기화 로직 필요. 시스템 복잡도 증가. 단순 CRUD에는 과설계.

### 추천 리소스
- 서적: 「Patterns of Enterprise Application Architecture」 (Martin Fowler) — 아키텍처 패턴 레퍼런스
- 서적: 「도메인 주도 설계」 (Eric Evans) — DDD와 패턴의 연결
- 웹: [Microsoft Cloud Design Patterns](https://learn.microsoft.com/en-us/azure/architecture/patterns/) — 분산 시스템 패턴

---

## Phase 4: 운영 / 설계 (Production)

### 학습 목표
- 새로운 기능 요구사항에 적합한 패턴을 선택하고 설계할 수 있다
- 패턴을 남용하지 않는 판단력을 갖춘다
- 팀에게 패턴 적용 이유를 설명하고 설득할 수 있다

### 학습 항목

- [ ] **4-1. 패턴 적용 의사결정 프레임워크**
  ```
  1. 현재 코드에 문제가 있는가? (변경이 어려운가?)
     → No: 패턴 적용하지 않는다
     → Yes: 다음 단계로

  2. 이 문제가 반복될 것인가?
     → No: 단순 리팩토링으로 충분
     → Yes: 패턴 적용 검토

  3. 팀원이 이 패턴을 이해할 수 있는가?
     → No: 더 단순한 해결책을 찾거나, 문서화 후 적용
     → Yes: 적용
  ```

- [ ] **4-2. Booster 프로젝트에 패턴 맵핑**

  | Booster 코드 | 적용된 패턴 | 위치 |
  |-------------|-----------|------|
  | `Waiting.create()` | Factory Method | 도메인 엔티티 |
  | `@Builder` (Lombok) | Builder | DTO, Entity |
  | `ApplicationEventPublisher` | Observer | 이벤트 레이어 |
  | `Facade` 클래스들 | Facade | application 레이어 |
  | `NagerDateClient` | Adapter | infrastructure 레이어 |
  | `OutboxMessageRelay` | Outbox (Polling Publisher) | event 레이어 |
  | `@Transactional` / `@Cacheable` | Proxy + Decorator | AOP |
  | Resilience4j CircuitBreaker | Circuit Breaker + State | resilience 설정 |
  | Spring Bean (`@Service`) | Singleton (컨테이너 관리) | 전역 |
  | `FilterChain` (Gateway) | Chain of Responsibility | Gateway 서비스 |
  | Redis 읽기 / DB 쓰기 분리 | CQRS (부분 적용) | waiting 도메인 |

- [ ] **4-3. 코드 리뷰에서 패턴 관점 체크리스트**
  - [ ] 새로운 `if-else` 분기가 3개 이상 추가되었는가? → Strategy 검토
  - [ ] 비슷한 코드가 2곳 이상 중복되었는가? → Template Method 또는 공통 추출 검토
  - [ ] 외부 시스템 연동이 추가되었는가? → Adapter + Circuit Breaker 검토
  - [ ] 여러 서비스를 조합하는 로직인가? → Facade 검토
  - [ ] 사용처가 1개인 인터페이스가 추가되었는가? → 과도한 추상화 의심

- [ ] **4-4. 패턴을 적용하지 않아야 할 때**
  - 코드가 충분히 단순하고 변경 가능성이 낮을 때
  - 팀 전체가 해당 패턴에 익숙하지 않을 때 (학습 비용 > 이득)
  - 추상화 레이어가 이미 3단계 이상일 때
  - "혹시 나중에 필요할 수도 있으니까" → YAGNI 원칙 위반

### 실무 체크리스트
- [ ] 새 기능 설계 시 적용 가능한 패턴을 1~2개 후보로 검토했는가
- [ ] 패턴 적용 시 팀원이 이해할 수 있도록 주석 또는 ADR(Architecture Decision Record) 작성
- [ ] 패턴 적용 전/후 코드 복잡도 비교 (적용 후가 더 복잡하면 재고)
- [ ] 단위 테스트로 패턴의 확장 포인트가 정상 동작하는지 검증

---

## 연관 학습 경로

- **SOLID 원칙 심화**: 패턴의 근간. 원칙을 깊이 이해하면 패턴이 자연스럽게 보인다
- **DDD (Domain-Driven Design)**: Booster 프로젝트의 설계 철학. 패턴과 함께 도메인 모델링 능력 향상
- **Spring 내부 구조**: Spring이 사용하는 패턴을 소스 레벨에서 분석하면 이해가 깊어진다
- **리팩토링**: 패턴 적용은 곧 리팩토링. Martin Fowler의 리팩토링 카탈로그와 병행 학습 추천
