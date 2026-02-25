# Spring 핵심 개념 면접 정리

## 1. IoC & DI

### IoC (Inversion of Control, 제어의 역전)

객체의 생성과 의존성 관리를 **개발자가 아닌 컨테이너(Spring)가 담당**하는 원칙.

```
// 전통적인 방식 (개발자가 제어)
class OrderService {
    private PaymentService paymentService = new PaymentService(); // 직접 생성
}

// IoC 방식 (컨테이너가 제어)
class OrderService {
    private PaymentService paymentService; // 컨테이너가 주입
}
```

### DI (Dependency Injection, 의존성 주입) 방식 3가지

| 방식 | 코드 | 특징 |
|------|------|------|
| **생성자 주입** | `@RequiredArgsConstructor` | 불변성 보장, 순환 의존 컴파일 타임 감지, **권장** |
| **세터 주입** | `@Autowired` on setter | 선택적 의존성, 주입 전 null 위험 |
| **필드 주입** | `@Autowired` on field | 코드 간결, 테스트 어려움, **비권장** |

```java
// 생성자 주입 (권장)
@Service
@RequiredArgsConstructor
public class OrderService {
    private final PaymentService paymentService; // final → 불변 보장
}
```

> **면접 포인트**: "왜 생성자 주입을 권장하나?" → ① 불변성(`final`) 보장 ② 순환 의존성을 애플리케이션 시작 시점에 감지 ③ 테스트 시 Mock 주입이 쉬움 ④ 필수 의존성임이 명확함.

### IoC 컨테이너

| 인터페이스 | 설명 |
|-----------|------|
| **BeanFactory** | 기본 컨테이너. 빈을 Lazy 로딩 (최초 요청 시 초기화) |
| **ApplicationContext** | BeanFactory 확장. Eager 로딩, 이벤트 발행, 국제화, AOP 통합. **실무에서 사용** |

---

## 2. Bean 라이프사이클

```
Spring 컨테이너 시작
        │
        ▼
1. 빈 정의 로드 (@Component 스캔, @Bean 메서드)
        │
        ▼
2. 빈 인스턴스 생성 (생성자 호출)
        │
        ▼
3. 의존성 주입 (DI)
        │
        ▼
4. 초기화 콜백
   - @PostConstruct 메서드 실행
   - InitializingBean.afterPropertiesSet()
        │
        ▼
5. 빈 사용 (애플리케이션 실행)
        │
        ▼
6. 소멸 콜백 (컨테이너 종료 시)
   - @PreDestroy 메서드 실행
   - DisposableBean.destroy()
        │
        ▼
Spring 컨테이너 종료
```

```java
@Component
public class MyBean {

    @PostConstruct
    public void init() {
        // DB 연결, 캐시 워밍업 등 초기화 로직
    }

    @PreDestroy
    public void destroy() {
        // 리소스 반납, 연결 해제 등 정리 로직
    }
}
```

### Bean Scope

| 스코프 | 설명 | 기본값 |
|--------|------|--------|
| **singleton** | 컨테이너당 1개 인스턴스 | ✅ 기본값 |
| **prototype** | 요청마다 새 인스턴스 생성 | |
| **request** | HTTP 요청당 1개 (웹) | |
| **session** | HTTP 세션당 1개 (웹) | |

> **면접 포인트**: "싱글톤 빈에 prototype 빈을 주입하면?" → 싱글톤이 한 번 생성될 때만 주입받으므로 prototype이 의미 없어진다. 해결책: `ObjectProvider<T>` 또는 `@Lookup` 사용.

---

## 3. Spring AOP

### AOP 핵심 개념

| 용어 | 설명 | 예시 |
|------|------|------|
| **Aspect** | 횡단 관심사 모듈 | 트랜잭션, 로깅, 캐시 |
| **Join Point** | Advice가 적용 가능한 지점 | 메서드 실행 시점 |
| **Pointcut** | Join Point의 조건 (어디에 적용할지) | `@Transactional`이 붙은 메서드 |
| **Advice** | 실제 부가 기능 로직 | `@Before`, `@After`, `@Around` |
| **Weaving** | Pointcut에 Advice를 적용하는 과정 | 프록시 생성 |

### 프록시 기반 동작 원리

Spring AOP는 **실제 객체를 감싸는 Proxy 객체**를 생성하여 부가 기능을 적용한다.

```
클라이언트 → Proxy 객체 → 실제 Bean
                │
                ├─ @Before Advice 실행
                ├─ 실제 메서드 호출
                └─ @After Advice 실행
```

### JDK Dynamic Proxy vs CGLIB

| 항목 | JDK Dynamic Proxy | CGLIB |
|------|-------------------|-------|
| 조건 | 인터페이스가 있을 때 | 인터페이스 없을 때 (or 설정 시) |
| 방식 | `java.lang.reflect.Proxy` | 바이트코드 조작, 서브클래싱 |
| 제한 | 인터페이스 메서드만 프록시 가능 | `final` 클래스/메서드 불가 |
| Spring Boot | 기본값: **CGLIB** (`proxyTargetClass=true`) | |

### ⚠️ Self-invocation (자기 호출) 문제

```java
@Service
public class OrderService {

    public void processOrder() {
        validateAndSave(); // ❌ 프록시를 거치지 않음 → @Transactional 무시
    }

    @Transactional
    public void validateAndSave() {
        // ...
    }
}
```

**원인**: `this.validateAndSave()`는 프록시가 아닌 실제 객체를 직접 호출하기 때문.

**해결책**:
```java
// 1. 별도 클래스(Bean)로 분리 (권장)
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderSaveService orderSaveService;

    public void processOrder() {
        orderSaveService.validateAndSave(); // ✅ 프록시를 통해 호출
    }
}

// 2. ApplicationContext에서 자기 자신을 주입 (비권장)
@Service
public class OrderService {
    @Autowired
    private OrderService self;

    public void processOrder() {
        self.validateAndSave(); // ✅ 프록시를 통해 호출
    }
}
```

---

## 4. @Transactional 심화

### 동작 원리

```
클라이언트
    │
    ▼
Proxy (TransactionInterceptor)
    │  ① 트랜잭션 시작 (Connection.setAutoCommit(false))
    ▼
실제 Service 메서드 실행
    │  ② 정상 완료 → commit()
    │  ② 예외 발생 → rollback()
    ▼
Proxy (TransactionInterceptor)
```

### 전파 레벨 (Propagation)

| 전파 레벨 | 설명 |
|-----------|------|
| **REQUIRED** | 기존 트랜잭션 있으면 합류, 없으면 새로 생성 **(기본값)** |
| **REQUIRES_NEW** | 항상 새 트랜잭션 생성. 기존은 일시 정지 |
| **SUPPORTS** | 기존 트랜잭션 있으면 합류, 없으면 비트랜잭션으로 실행 |
| **NOT_SUPPORTED** | 항상 비트랜잭션으로 실행. 기존은 일시 정지 |
| **MANDATORY** | 기존 트랜잭션이 반드시 있어야 함. 없으면 예외 |
| **NEVER** | 트랜잭션이 있으면 예외 |
| **NESTED** | 중첩 트랜잭션 (Savepoint 활용) |

```java
// REQUIRES_NEW 사용 사례: 로그는 메인 트랜잭션 실패해도 남겨야 할 때
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveLog(String message) { ... }
```

### Rollback 규칙

```java
// 기본: RuntimeException, Error만 롤백
// Checked Exception은 롤백 안 됨

@Transactional(rollbackFor = Exception.class)     // 모든 예외 롤백
@Transactional(noRollbackFor = RuntimeException.class) // Runtime 예외도 롤백 안 함
```

> **면접 포인트**: "`@Transactional`을 `private` 메서드에 붙이면?" → CGLIB은 서브클래싱 기반이므로 `private` 메서드를 오버라이드할 수 없다. 프록시가 동작하지 않아 트랜잭션이 무시된다.

### readOnly = true 효과

```java
@Transactional(readOnly = true)
public List<Order> findAll() { ... }
```

- JPA: `flush()` 호출 안 함 → Dirty Checking 생략 → 성능 향상
- DB: 읽기 전용 커넥션 사용 가능 (Master-Slave 분기)

---

## 5. Spring MVC 요청 처리 흐름

```
HTTP 요청
    │
    ▼
DispatcherServlet (Front Controller)
    │
    ├─ 1. HandlerMapping: URL → Handler(Controller) 탐색
    │
    ├─ 2. HandlerAdapter: Handler 실행 방법 결정
    │       (파라미터 바인딩, @RequestBody 변환 등)
    │
    ├─ 3. Handler(Controller) 실행
    │       → Service 호출 → 결과 반환
    │
    ├─ 4. HandlerAdapter: 반환값 처리
    │       (@ResponseBody → HttpMessageConverter로 JSON 변환)
    │
    └─ 5. ViewResolver (REST면 생략, 템플릿이면 뷰 렌더링)

HTTP 응답 반환
```

### HttpMessageConverter

`@RequestBody` / `@ResponseBody` 처리 시 동작.

| 컨버터 | 처리 대상 |
|--------|-----------|
| `StringHttpMessageConverter` | `String` |
| `MappingJackson2HttpMessageConverter` | JSON (객체 ↔ JSON) |
| `ByteArrayHttpMessageConverter` | `byte[]` |

> **면접 포인트**: "`@RestController`와 `@Controller`의 차이?" → `@RestController`는 `@Controller + @ResponseBody`의 조합. 모든 핸들러 메서드에 `@ResponseBody`가 자동 적용되어 뷰 렌더링 없이 응답 바디로 직렬화한다.

---

## 6. Spring Boot 자동 구성

### @SpringBootApplication 분해

```java
@SpringBootApplication
=
@SpringBootConfiguration   // @Configuration 상속, 설정 클래스로 등록
+ @EnableAutoConfiguration // META-INF/spring/AutoConfiguration.imports 로드
+ @ComponentScan           // 현재 패키지 이하 컴포넌트 스캔
```

### AutoConfiguration 동작 방식

```
1. JAR 안의 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 읽기
2. 각 AutoConfiguration 클래스의 @Conditional 조건 확인
   - @ConditionalOnClass: 특정 클래스가 클래스패스에 있을 때
   - @ConditionalOnMissingBean: 특정 빈이 없을 때 (사용자 커스터마이징 허용)
   - @ConditionalOnProperty: 특정 프로퍼티가 설정됐을 때
3. 조건을 만족하는 AutoConfiguration만 적용
```

```java
// JacksonAutoConfiguration 예시 (단순화)
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean  // 사용자가 ObjectMapper를 직접 정의하면 이 빈은 생성 안 됨
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

> **면접 포인트**: "Spring Boot가 편한 이유?" → AutoConfiguration이 클래스패스를 분석해 필요한 빈을 자동 등록한다. 개발자는 `application.yml`로 커스터마이징만 하면 된다.

---

## 7. 이벤트 기반 통신 (ApplicationEventPublisher)

Spring 내부 서비스 간 느슨한 결합을 위해 사용. (Booster 프로젝트에서 Outbox 패턴에 활용)

```java
// 이벤트 발행
@Service
@RequiredArgsConstructor
public class WaitingService {
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void register(Waiting waiting) {
        waitingRepository.save(waiting);
        publisher.publishEvent(new WaitingRegisteredEvent(waiting)); // 트랜잭션 내 발행
    }
}

// 이벤트 수신
@Component
public class WaitingEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 커밋 후 실행
    public void handleWaitingRegistered(WaitingRegisteredEvent event) {
        // Outbox 저장, 알림 발송 등
    }
}
```

| `@EventListener` | `@TransactionalEventListener` |
|-----------------|-------------------------------|
| 이벤트 발행 즉시 실행 | 트랜잭션 커밋/롤백 후 실행 |
| 트랜잭션 상태와 무관 | `phase` 설정으로 타이밍 제어 |

---

## 8. 면접 Q&A

**Q. Spring의 핵심 특징 3가지는?**
> IoC/DI(제어의 역전), AOP(관점 지향 프로그래밍), PSA(Portable Service Abstraction). PSA는 JDBC, JPA, Cache 등을 추상화해 구현체가 바뀌어도 코드가 변경되지 않게 한다.

**Q. @Component, @Service, @Repository, @Controller의 차이는?**
> 기능적으로는 모두 `@Component`의 특수화로 빈으로 등록된다는 점은 같다. 의미적으로 계층을 명확히 하고, `@Repository`는 추가로 SQL 예외를 Spring `DataAccessException`으로 변환해준다.

**Q. 순환 의존성(Circular Dependency)이 왜 문제인가?**
> A가 B를, B가 A를 의존하면 생성자 주입 시 서로를 먼저 생성해야 하는 상황이 되어 `BeanCurrentlyInCreationException`이 발생한다. 설계 문제를 나타내므로 의존성 방향을 정리하거나 이벤트로 결합을 끊어야 한다.

**Q. `@Transactional`이 붙은 메서드에서 예외를 catch하면 롤백이 되나?**
> 안 된다. 예외를 catch해서 메서드 밖으로 던지지 않으면 Spring은 예외 발생을 감지하지 못해 커밋한다. 롤백이 필요하다면 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`를 명시적으로 호출해야 한다.

**Q. ApplicationContext를 직접 getBean()으로 쓰는 게 나쁜 이유는?**
> Service Locator 패턴으로 IoC를 역행하는 방식이다. 의존성이 숨겨져 테스트가 어렵고, 컴파일 타임에 오류를 잡을 수 없다. DI를 통해 명시적으로 주입받는 것이 원칙이다.

**Q. `@Async`가 동작하지 않는 경우는?**
> ① `@EnableAsync`가 없는 경우 ② Self-invocation (같은 클래스 내 호출) ③ `private` 메서드 ④ AOP 프록시가 생성되지 않는 경우. `@Transactional`과 동일한 프록시 기반 제약이 적용된다.
