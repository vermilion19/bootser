# Spring Internals for Experienced Backend Interviews
**부제:** 빈, 생명주기, 후처리기, 컴포넌트 스캔, 트랜잭션, 프록시, 자동설정까지 한 번에 파고드는 스터디 노트

- 파일 목적: **경력직 백엔드 면접 대비용**
- 독자 가정: Spring Boot / Spring MVC / JPA 를 실무에서 사용해 봤고, 이제 **"왜 그렇게 동작하는가"** 를 설명해야 하는 사람
- 권장 사용법:
  1. 먼저 전체를 1회 정독한다.
  2. 각 장의 **"면접에서 이렇게 답하라"** 파트를 소리 내서 설명한다.
  3. 코드 예제를 직접 실행해 본다.
  4. 마지막의 **꼬리질문 세트**로 자가 면접을 한다.

> 이 문서는 Markdown 기준으로 작성되었기 때문에 실제 페이지 수는 렌더러에 따라 달라진다. 다만 일반적인 A4 인쇄/미리보기 기준으로 **50페이지 이상 분량**이 나오도록 충분히 길고 촘촘하게 구성했다.

---

## 문서의 핵심 목표

이 문서를 끝내고 나면 아래 질문들에 대해 **암기 수준이 아니라 구조적으로** 답할 수 있어야 한다.

- 스프링에서 빈(bean)은 정확히 무엇인가?
- 빈은 어디서 등록되고, 빈 정의(BeanDefinition)는 언제 만들어지는가?
- `@Component` 와 `@Bean` 은 어떻게 다른가?
- Component Scan 은 내부적으로 어떤 방식으로 클래스를 읽고 후보를 찾는가?
- BeanFactory 와 ApplicationContext 는 어떻게 다른가?
- 빈 생성 파이프라인은 어떤 순서로 흘러가는가?
- BeanPostProcessor 와 BeanFactoryPostProcessor 는 무엇이 다르고, 각각 언제 개입하는가?
- `@Autowired` 는 누가 처리하는가?
- `@PostConstruct`, `@PreDestroy`, `InitializingBean`, `DisposableBean`, `initMethod`, `destroyMethod` 의 순서는 어떻게 되는가?
- `@Transactional` 은 누가 해석하고, 트랜잭션은 어디서 시작되고 어디서 끝나는가?
- 왜 self-invocation 에서는 `@Transactional` 이 안 먹는가?
- Spring Boot 자동설정은 어떤 파일에서 읽고, 왜 `@ComponentScan` 으로 찾지 않는가?
- 왜 순환참조는 위험하고, 3단계 캐시는 정확히 무엇을 푸는 장치인가?

---

## 읽는 순서 추천

### 1회독: 큰 그림
1. Spring 을 한 문장으로 설명하기
2. Bean / BeanDefinition / BeanFactory / ApplicationContext
3. refresh() 전체 흐름
4. Bean 등록 방식
5. Bean 생명주기
6. BeanPostProcessor / BeanFactoryPostProcessor
7. `@Transactional`
8. Boot 자동설정

### 2회독: 면접 대비 심화
1. Component Scan 내부 동작
2. `@Configuration(proxyBeanMethods = true/false)`
3. Autowired 후보 선택 알고리즘
4. 순환참조와 3단계 캐시
5. AOP 프록시와 self-invocation
6. 트랜잭션 전파와 rollback-only
7. `@Repository` 의 예외 변환
8. scoped proxy 와 prototype 주입

### 3회독: 실무 연결
1. 애플리케이션 시작 성능
2. scan 범위 최소화
3. Bean 수 관리
4. 트랜잭션 범위 최소화
5. 프록시로 인해 생기는 디버깅 이슈
6. 테스트 환경에서 Bean 교체
7. 조건부 빈 등록과 자동설정 override

---

## 면접 답변의 기준선

경력직 면접에서는 아래 두 레벨을 오간다.

### 레벨 1: 개념 설명
- "스프링은 IoC 컨테이너를 중심으로 객체 생성, 의존성 연결, 라이프사이클, 부가기능(AOP/트랜잭션)을 관리하는 프레임워크입니다."

### 레벨 2: 내부 동작 설명
- "실제로는 `ApplicationContext` 가 `BeanDefinition` 을 보관하고, `refresh()` 과정에서 `BeanFactoryPostProcessor` 가 빈 정의를 가공한 뒤, `BeanPostProcessor` 가 실제 빈 인스턴스 생성 전후에 개입합니다. `@Autowired` 는 `AutowiredAnnotationBeanPostProcessor` 가 처리하고, `@Transactional` 은 AOP 프록시와 `TransactionInterceptor` 를 통해 동작합니다."

### 레벨 3: 함정/예외/설계 판단까지
- "`@Transactional` 은 보통 프록시 기반이기 때문에 self-invocation 에서는 적용되지 않고, 프라이빗 메서드에는 프록시가 개입할 수 없습니다. 그래서 트랜잭션 경계는 외부에서 호출되는 public service 메서드에 두는 것이 안전합니다. 또한 `REQUIRES_NEW` 는 별도 물리 트랜잭션과 추가 커넥션이 필요하므로 풀 크기 설계와 연결해서 봐야 합니다."

이 문서는 **레벨 3까지 답하는 것**을 목표로 한다.

# 1. Spring 을 한 문장으로 설명하기

## 1-1. 가장 짧은 답변
스프링은 **객체 생성과 연결을 애플리케이션 코드에서 분리하고**, 그 위에 트랜잭션, AOP, 이벤트, 웹, 데이터 접근 같은 공통 인프라를 올리는 프레임워크다.

## 1-2. 면접에서 자주 나오는 질문
- IoC 란 무엇인가?
- DI 는 IoC 와 같은가?
- 왜 객체 생성을 컨테이너가 관리해야 하는가?
- 스프링을 안 쓰면 무엇이 불편해지는가?
- ApplicationContext 는 왜 필요한가?

## 1-3. IoC 와 DI
많이 헷갈리는 지점부터 정리하자.

### IoC (Inversion of Control)
제어의 역전.  
원래는 애플리케이션 코드가 직접 객체를 만들고, 객체의 생명주기와 조립 순서를 통제한다.  
IoC 환경에서는 **그 제어권 일부를 컨테이너가 가져간다.**

### DI (Dependency Injection)
의존성 주입.  
IoC 를 실현하는 대표적인 방법이다.  
필요한 의존 객체를 직접 new 하지 않고 외부에서 주입받는다.

즉,

- IoC 는 더 큰 개념
- DI 는 IoC 를 구현하는 대표 패턴

## 1-4. 왜 중요한가
백엔드 서비스가 커지면 아래 문제가 생긴다.

- 객체 의존관계가 복잡해진다.
- 환경별 구현체가 바뀐다. (local / dev / prod / test)
- 공통 부가기능이 늘어난다. (로깅, 보안, 트랜잭션, 모니터링)
- 객체 생성 시점과 초기화 순서가 중요해진다.
- 테스트에서 일부 빈만 교체하고 싶어진다.

Spring 은 이 문제들을 **컨테이너 중심의 메타데이터 모델** 로 해결한다.

- 객체 자체보다 **BeanDefinition** 을 먼저 관리한다.
- 실제 인스턴스는 필요할 때 생성한다.
- 생성 이후에는 post-processor 와 proxy 로 기능을 덧씌운다.
- 최종적으로 애플리케이션 개발자는 "무엇을 원한다" 를 어노테이션과 설정으로 표현한다.

## 1-5. 면접용 30초 답변
"스프링은 IoC 컨테이너를 통해 객체 생성과 의존성 연결을 관리하고, 그 위에 AOP, 트랜잭션, 이벤트, 웹 기능을 제공하는 프레임워크입니다. 핵심은 애플리케이션 코드가 객체 생성과 부가기능 구현에 직접 얽히지 않게 하는 데 있습니다."

## 1-6. 면접용 3분 답변
"실제로는 스프링이 객체 자체를 바로 관리하는 게 아니라 BeanDefinition 이라는 메타데이터를 먼저 수집합니다. 이 메타데이터는 `@Component`, `@Bean`, `@Import`, 자동설정 같은 경로를 통해 등록됩니다. 이후 `ApplicationContext.refresh()` 과정에서 BeanFactoryPostProcessor 가 빈 정의를 수정하고, BeanPostProcessor 가 실제 빈 생성 전후 단계에 개입합니다. 그래서 `@Autowired`, `@PostConstruct`, `@Transactional` 같은 기능이 각자 다른 확장 포인트를 통해 동작합니다. 즉 스프링을 잘 이해한다는 건 어노테이션 이름을 아는 게 아니라, 이 어노테이션이 빈 정의 단계에서 작동하는지, 인스턴스 주입 단계에서 작동하는지, 아니면 프록시/AOP 단계에서 작동하는지를 설명할 수 있다는 뜻입니다."

## 1-7. 꼬리질문 포인트
- BeanDefinition 이 구체적으로 뭘 담고 있나?
- AOP 는 왜 프록시인가? bytecode weaving 이랑 차이는?
- 어떤 어노테이션은 scan 단계에서 먹고, 어떤 건 runtime 에 먹는 이유는?
- 자동설정과 사용자 정의 빈이 충돌하면 누가 이기나?

# 2. Bean, BeanDefinition, BeanFactory, ApplicationContext

## 2-1. Bean 이란 무엇인가

많은 사람이 "스프링이 관리하는 객체"라고만 외운다. 틀린 말은 아니지만 면접에서는 부족하다.

좀 더 정확히 말하면:

> Bean 은 **Spring IoC 컨테이너에 등록되어** 이름(name), 타입(type), 스코프(scope), 생성 방식, 의존관계, 초기화/소멸 메타데이터 등을 가진 **관리 대상 객체**다.

즉 Bean 은 단순한 Java object 와 달리 다음을 가진다.

- 컨테이너가 관리한다.
- 이름이 있다.
- 생성 전략이 있다.
- 주입 규칙이 있다.
- 생명주기 콜백이 있다.
- 후처리 대상이 된다.
- 프록시로 대체될 수 있다.

### 그냥 new 한 객체와의 차이
```java
OrderService s1 = new OrderService();      // 일반 객체
OrderService s2 = context.getBean(OrderService.class); // 빈
```

둘 다 메모리의 객체지만, `s2` 는 컨테이너가 관리한다는 점에서 완전히 다르다.

## 2-2. BeanDefinition 이란 무엇인가

스프링 내부에서는 실제 빈 객체를 바로 관리하기보다 먼저 **BeanDefinition** 을 관리한다.

BeanDefinition 에는 보통 아래 정보가 들어간다.

- 빈 클래스 이름
- factory method / factory bean 정보
- scope (singleton, prototype, ...)
- lazy 여부
- primary 여부
- qualifiers
- dependsOn
- init method / destroy method
- constructor argument
- property values
- role (application / support / infrastructure)

핵심은 이거다.

> 스프링은 "객체" 보다 먼저 "객체를 어떻게 만들 것인가에 대한 정의" 를 관리한다.

이 관점이 왜 중요하냐면,
BeanFactoryPostProcessor 가 실제 인스턴스가 아니라 **BeanDefinition** 을 수정할 수 있기 때문이다.

## 2-3. BeanFactory
`BeanFactory` 는 가장 기본적인 IoC 컨테이너 인터페이스다.

주요 역할:

- 빈 조회
- 빈 생성
- 의존성 주입
- 스코프 관리

보통 우리가 직접 쓰는 것은 `ApplicationContext` 이고, 내부 핵심 구현은 `DefaultListableBeanFactory` 가 맡는 경우가 많다.

### BeanFactory 의 감각
- "빈을 생성하고 보관하는 공장"
- 매우 코어한 레벨
- 최소 기능
- 웹, 이벤트, 메시지, 환경 설정 같은 부가 기능 없음

## 2-4. ApplicationContext
`ApplicationContext` 는 BeanFactory 를 확장한 상위 컨테이너다.

추가로 제공하는 것들:

- 이벤트 발행/구독
- 국제화(MessageSource)
- Resource 로딩
- Environment / PropertySource
- ApplicationContextAware 등 다양한 aware 콜백
- 자동으로 BeanPostProcessor 탐지 및 적용
- 편리한 애노테이션 기반 설정 지원

실무에서 스프링을 쓴다는 것은 거의 항상 `ApplicationContext` 를 쓴다는 뜻이다.

## 2-5. DefaultListableBeanFactory
이름을 기억할 필요는 없지만, 경력직 면접에서는 한 번쯤 언급될 수 있다.

이 구현체는 보통 다음 역할을 가진다.

- BeanDefinition registry
- bean lookup by name / type
- autowire candidate resolution
- alias 관리
- singleton registry 와 연동
- preInstantiateSingletons 수행

즉, 스프링 컨테이너의 실질적인 핵심 엔진이라고 볼 수 있다.

## 2-6. Bean 이름
빈은 이름이 있다. 이름은 단순 표시값이 아니라 lookup key 이다.

### 이름 생성 예시
```java
@Component
public class OrderService {}
```

기본 이름은 `orderService` 가 된다.

```java
@Component("customOrderService")
public class OrderService {}
```

이 경우 이름은 `customOrderService`.

### `@Bean`
```java
@Bean
public OrderService orderService() {
    return new OrderService();
}
```

기본 빈 이름은 메서드명 `orderService`.

## 2-7. Bean alias
하나의 빈에 여러 이름(alias)을 붙일 수 있다.  
운영 코드에서 자주 쓰진 않지만 XML 기반 설정, 레거시 호환, 특정 통합 환경에서 등장한다.

## 2-8. Bean role
스프링 내부에는 빈 역할 개념이 있다.

- `ROLE_APPLICATION`: 사용자가 정의한 일반 빈
- `ROLE_SUPPORT`: 보조 역할
- `ROLE_INFRASTRUCTURE`: 프레임워크 인프라 빈

이걸 아는 이유는 디버깅 시 "왜 이런 정체불명 빈이 있지?" 를 설명할 때 도움이 되기 때문이다.  
예: AutoProxyCreator, TransactionInterceptor, 내부 advisor 들은 대개 infrastructure 빈이다.

## 2-9. 면접 답변 포인트
**Q. Bean 과 일반 객체의 차이는?**  
A. "Bean 은 단순 객체가 아니라 스프링 컨테이너에 등록되어 생명주기, 주입, 후처리, 프록시 적용, 스코프 관리의 대상이 되는 객체입니다."

**Q. BeanDefinition 이 왜 필요한가?**  
A. "컨테이너가 객체 인스턴스를 만들기 전에 메타데이터 단계에서 빈을 조작할 수 있기 때문입니다. 그래서 BeanFactoryPostProcessor 가 인스턴스 생성 전에 빈 정의를 수정할 수 있습니다."

**Q. BeanFactory 와 ApplicationContext 차이는?**  
A. "BeanFactory 는 핵심 IoC 기능만 제공하고, ApplicationContext 는 그 위에 이벤트, 메시지, 환경, 리소스, 자동 후처리 등록 등 엔터프라이즈 개발에 필요한 부가기능을 포함한 상위 컨테이너입니다."

# 3. 애플리케이션 시작과 `refresh()` 전체 흐름

## 3-1. 왜 `refresh()` 가 중요한가
스프링 내부 동작을 설명할 때 제일 중요한 메서드 하나만 고르라면 `ApplicationContext.refresh()` 다.

이 메서드가 하는 일은 단순하지 않다.

- 설정을 읽는다.
- BeanDefinition 을 준비한다.
- BeanFactoryPostProcessor 를 실행한다.
- BeanPostProcessor 를 등록한다.
- 메시지 소스, 이벤트 멀티캐스터를 준비한다.
- 싱글톤 빈을 생성한다.
- 컨텍스트 refresh 완료 이벤트를 발행한다.

즉, **스프링 컨테이너가 살아나는 전체 과정** 이 여기에 담겨 있다.

## 3-2. Boot 레벨 vs Framework 레벨
Spring Boot 애플리케이션 시작은 대략 이렇게 본다.

1. `SpringApplication.run(...)`
2. ApplicationContext 타입 결정
3. Environment 준비
4. 초기화기(ApplicationContextInitializer) 적용
5. context 생성
6. bean definitions 로딩
7. `context.refresh()`
8. runner / event 발행

Boot 는 시작 절차를 편하게 감싸주는 래퍼에 가깝고,
실제 컨테이너를 완성하는 핵심은 결국 **Framework 의 `refresh()`** 다.

## 3-3. `refresh()` 주요 단계
실제 내부 구현은 더 복잡하지만 면접용으로 가장 중요한 단계만 순서대로 보자.

### 1) `prepareRefresh()`
- 컨텍스트 상태 초기화
- PropertySource 검증
- early event 저장 준비

### 2) `obtainFreshBeanFactory()`
- 새로운 BeanFactory 준비
- 기존 BeanDefinition 로드/갱신

### 3) `prepareBeanFactory(beanFactory)`
- classloader, expression resolver, property editor 등 설정
- 몇몇 aware 인터페이스 처리기 준비
- environment, system properties 같은 resolvable dependency 등록

### 4) `postProcessBeanFactory(beanFactory)`
- 서브클래스가 추가 가공할 수 있는 훅

### 5) `invokeBeanFactoryPostProcessors(beanFactory)`
- 가장 중요
- **BeanDefinition 단계** 의 후처리 실행
- `BeanDefinitionRegistryPostProcessor` 먼저
- 그 다음 일반 `BeanFactoryPostProcessor`

여기서 `@Configuration` 클래스 해석, `@Bean` 등록, component scan 확장, placeholder 처리 등이 일어난다.

### 6) `registerBeanPostProcessors(beanFactory)`
- 또 중요
- 나중에 빈 인스턴스 생성 시 개입할 **BeanPostProcessor 등록**
- 여기서 등록된 후처리기들이 이후 모든 애플리케이션 빈 생성에 영향을 준다.

### 7) `initMessageSource()`
- i18n 메시지 소스 준비

### 8) `initApplicationEventMulticaster()`
- 이벤트 발행기 준비

### 9) `onRefresh()`
- 컨텍스트 구현체별 추가 동작

### 10) `registerListeners()`
- 리스너 등록 및 early event 발행

### 11) `finishBeanFactoryInitialization(beanFactory)`
- conversion service 준비
- embedded value resolver 등 마무리
- **남은 non-lazy singleton 빈 생성**
- 보통 우리가 "스프링이 빈을 띄운다" 고 느끼는 순간이 여기

### 12) `finishRefresh()`
- 라이프사이클 프로세서 호출
- `ContextRefreshedEvent` 발행

## 3-4. 중요한 면접 포인트
### BeanFactoryPostProcessor 와 BeanPostProcessor 의 순서
많이 꼬아 묻는다.

- BeanFactoryPostProcessor: **빈 인스턴스 생성 전**, BeanDefinition 수정
- BeanPostProcessor: **빈 생성 과정 중/후**, 인스턴스 가공

즉 먼저 정의를 정리하고, 나중에 객체를 가공한다.

### 왜 이 순서여야 하나
빈 정의가 확정되지도 않았는데 인스턴스를 만들면 안 된다.
예를 들어 `@Configuration` 클래스가 `@Bean` 메서드를 추가 등록하기도 전에 다른 빈을 생성해 버리면 컨테이너 일관성이 깨진다.

## 3-5. preInstantiateSingletons
싱글톤은 기본적으로 컨텍스트 시작 시 미리 만들어진다.

장점:
- 시작 시점에 오류를 빨리 발견
- 운영 중 첫 요청 지연 감소

단점:
- 시작 시간이 길어질 수 있음
- 무거운 초기화가 많으면 부팅 비용 증가

그래서 `@Lazy` 는 시작 비용과 런타임 첫 접근 비용 사이의 trade-off 다.

## 3-6. 예외가 발생하면
`refresh()` 도중 실패하면 컨텍스트는 보통 초기화 실패로 보고, 이미 생성한 singleton 들을 정리하려고 시도한다.

면접에서 이런 질문이 나올 수 있다.
- "초기화 도중 예외가 나면 일부 빈만 살아남을 수 있나요?"
- "빈 생성 실패 시 destroy callback 도 호출되나요?"

핵심 답변:
- 완전한 정상 상태가 아니므로 부분 생성 상태가 있을 수 있다.
- 하지만 컨테이너는 일관성을 위해 이미 생성한 singleton 을 정리하려고 한다.
- 다만 외부 리소스를 직접 열어둔 코드가 있다면 애플리케이션 레벨 cleanup 이 안전해야 한다.

## 3-7. 실무 디버깅 포인트
부팅 문제를 잡을 때는 아래가 핵심이다.

- 어떤 단계에서 죽는가?
- BeanDefinition 등록 실패인가?
- 의존성 주입 실패인가?
- circular dependency 인가?
- BeanPostProcessor 에서 감싼 프록시 문제인가?
- `ConditionEvaluationReport` 를 보면 자동설정이 왜 켜졌고 왜 안 켜졌는지 보인다.

## 3-8. 한 줄 요약
`refresh()` 는 **정의 준비 → 후처리기 등록 → 싱글톤 생성 → 이벤트 발행** 의 순서로 컨테이너를 완성한다.

# 4. 빈이 등록되는 모든 경로

많은 개발자가 빈 등록을 `@Component` 와 `@Bean` 만으로 기억한다.  
하지만 실제로는 훨씬 다양한 경로가 있다. 경력직 면접에서는 이 그림을 한 번에 설명할 수 있어야 한다.

---

## 4-1. XML
레거시지만 원리를 설명할 때는 여전히 좋다.

```xml
<bean id="orderService" class="com.example.OrderService"/>
```

장점:
- 메타데이터가 외부화됨
- 컨테이너 관점이 더 명확함

단점:
- 타입 안전성이 약함
- 현대 자바 개발 흐름과 거리 있음

## 4-2. `@Component` 계열 + Component Scan
```java
@Service
public class OrderService {}
```

컨테이너가 클래스패스를 스캔해서 후보 클래스를 찾아 BeanDefinition 으로 등록한다.

## 4-3. `@Configuration` + `@Bean`
```java
@Configuration
public class AppConfig {

    @Bean
    public OrderService orderService(MemberRepository memberRepository) {
        return new OrderService(memberRepository);
    }
}
```

이 방식은 **메서드 호출로 빈 생성 로직을 직접 표현**한다.  
컴포넌트 스캔보다 생성 과정이 명시적이다.

## 4-4. `@Import`
```java
@Configuration
@Import(OtherConfig.class)
public class AppConfig {}
```

다른 설정 클래스를 가져와 등록한다.

### 왜 쓰는가
- 모듈화
- 조건부 조합
- 자동설정 확장

## 4-5. `ImportSelector`
어떤 클래스를 등록할지 **동적으로 선택**한다.

```java
public class MyImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {
            "com.example.FooConfig",
            "com.example.BarConfig"
        };
    }
}
```

이건 "어노테이션 하나 붙이면 내부적으로 여러 설정을 가져오는" 패턴에 자주 쓰인다.

## 4-6. `DeferredImportSelector`
조금 더 늦은 시점에 import 를 선택한다.  
자동설정과 비슷한 흐름, 대규모 설정 조합에서 중요하다.

## 4-7. `ImportBeanDefinitionRegistrar`
BeanDefinition 자체를 직접 등록한다.

```java
public class MyRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
        RootBeanDefinition bd = new RootBeanDefinition(MyService.class);
        registry.registerBeanDefinition("myService", bd);
    }
}
```

이 레벨까지 가면 "스프링이 메타데이터 기반 컨테이너" 라는 사실이 아주 분명해진다.

## 4-8. `FactoryBean`
조금 헷갈리지만 중요하다.

- `@Bean` 은 빈을 만드는 "설정 메서드"
- `FactoryBean` 은 **빈 생성 로직 자체를 캡슐화한 빈**

예:
```java
public class MyClientFactoryBean implements FactoryBean<MyClient> {
    @Override
    public MyClient getObject() {
        return new MyClient("https://api.example.com");
    }

    @Override
    public Class<?> getObjectType() {
        return MyClient.class;
    }
}
```

## 4-9. 프로그래밍 방식 등록
직접 registry 나 beanFactory 를 사용해 등록할 수 있다.

```java
GenericApplicationContext context = new GenericApplicationContext();
context.registerBean(OrderService.class);
context.refresh();
```

테스트, 프레임워크 확장, 라이브러리 개발에서 유용하다.

## 4-10. Spring Boot 자동설정
실무 체감상 가장 많이 만나는 등록 경로다.

- starter 의존성을 넣으면
- 자동설정 후보가 로드되고
- 조건(`@ConditionalOnClass`, `@ConditionalOnMissingBean` 등)을 통과하면
- BeanDefinition 이 자동 등록된다.

즉 "내가 등록하지 않은 것 같은데 빈이 생겼다" 의 상당수는 자동설정 때문이다.

## 4-11. 경력직 면접에서 중요한 정리
"빈은 수동과 자동 둘 다로 등록됩니다. 수동은 `@Bean`, XML, 프로그래밍 등록, `ImportBeanDefinitionRegistrar` 같은 방식이고, 자동은 Component Scan 과 Boot 자동설정이 대표적입니다. 내부적으로는 결국 BeanDefinitionRegistry 에 BeanDefinition 이 들어간다는 점이 공통입니다."

## 4-12. 자주 나오는 꼬리질문
- `@Component` 와 `@Bean` 중 무엇을 언제 쓰나?
- 자동설정된 빈을 내가 override 할 수 있나?
- `@Import` 와 component scan 차이는?
- `FactoryBean` 과 팩토리 패턴은 어떻게 다른가?
- 조건부 빈 등록은 어느 단계에서 결정되나?

# 5. Component Scan 딥다이브

`@ComponentScan` 은 "스프링이 클래스패스를 돌아다니며 후보 클래스를 찾는 기능" 정도로만 알면 부족하다.  
면접에서는 다음까지 설명해야 한다.

- 어디를 스캔하는가
- 무엇을 후보로 보는가
- 어떻게 클래스 로딩 비용을 줄이는가
- 이름은 어떻게 정하는가
- 필터는 어떻게 적용하는가
- meta-annotation 은 어떻게 동작하는가

---

## 5-1. 기본 동작
```java
@SpringBootApplication
public class App {}
```

`@SpringBootApplication` 안에는 `@ComponentScan` 이 포함되어 있다.  
기본적으로 애플리케이션 클래스가 속한 패키지와 그 하위 패키지를 스캔한다.

그래서 패키지 구조가 매우 중요하다.

### 잘못된 예
- 애플리케이션 메인 클래스가 너무 상위 패키지에 있으면 불필요한 범위까지 스캔
- 너무 하위 패키지면 필요한 빈을 놓침

## 5-2. 어떤 어노테이션이 후보인가
기본적으로 `@Component` 와 그 메타 애노테이션을 가진 클래스가 후보가 된다.

즉 아래는 전부 대상이다.

- `@Component`
- `@Service`
- `@Repository`
- `@Controller`
- `@RestController` (`@Controller` + `@ResponseBody`)
- 사용자 정의 stereotype annotation (`@Component` 를 메타로 가진 것)

예:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface UseCase {
}
```

이렇게 만들면 `@UseCase` 도 scan 대상이다.

## 5-3. 어떻게 클래스 로딩 없이 읽는가
이 부분이 면접에서 깊이 있어 보이는 포인트다.

스프링은 후보 탐색 시 무작정 모든 클래스를 로딩하지 않으려 한다.  
클래스를 실제 JVM 에 로딩하는 비용은 크고, 클래스 초기화 부작용도 있다.

그래서 스프링은 보통 **ASM 기반 메타데이터 리더**를 사용해 `.class` 파일의 메타정보를 읽는다.

즉,
- 먼저 class file metadata 를 읽고
- 후보인지 판정하고
- 실제 필요한 시점에만 클래스를 로딩한다

이게 Component Scan 의 성능과 안정성에 중요하다.

## 5-4. candidate component provider
내부적으로는 `ClassPathScanningCandidateComponentProvider` 같은 컴포넌트가 스캔을 담당한다.

핵심 아이디어:
- base package 를 순회
- resource pattern 으로 class 파일 검색
- metadata reader 로 annotation 확인
- include / exclude filter 적용
- BeanDefinition 생성

## 5-5. include / exclude filters
```java
@ComponentScan(
    basePackages = "com.example",
    includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = MyMarker.class),
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = LegacyService.class)
)
```

FilterType 예:
- ANNOTATION
- ASSIGNABLE_TYPE
- ASPECTJ
- REGEX
- CUSTOM

이걸 이해하면 "왜 어떤 클래스는 scan 되었고 어떤 건 안 되었나" 를 설명할 수 있다.

## 5-6. 기본 필터 끄기
```java
@ComponentScan(useDefaultFilters = false)
```

이렇게 하면 기본 `@Component` 계열 탐지를 끄고 내가 지정한 필터만 사용한다.

실무에서는 흔하진 않지만 라이브러리 설계, 제한된 탐색, 테스트 환경에서 가끔 유용하다.

## 5-7. Bean 이름 생성
기본 이름은 클래스명의 decapitalize 이다.

- `OrderService` -> `orderService`
- `URLService` -> 상황에 따라 주의

커스텀 `BeanNameGenerator` 를 쓸 수도 있다.

### 왜 중요하나
- 빈 이름 충돌
- 모듈 간 클래스명 중복
- 완전수식명(FQCN) 기반 이름 전략이 필요한 경우

## 5-8. stereotype 별 의미
### `@Component`
가장 일반적인 stereotype.  
"이 클래스는 scan 후보" 정도의 의미.

### `@Service`
의미상 서비스 계층 표현. 기능상 `@Component` 와 동일.

### `@Repository`
기능상 `@Component` + 예외 변환 대상이 될 수 있다는 추가 의미.

### `@Controller`
MVC 계층과 결합될 수 있음.  
scan 측면에서는 `@Component` 계열.

즉 **scan 후보 여부** 관점에서는 거의 동일하고,  
**후속 인프라가 특별 취급하느냐** 가 차이다.

## 5-9. scan 범위를 어떻게 설계할까
대규모 서비스에서 패키지 구조는 아키텍처 문서 수준으로 중요하다.

추천 기준:
- application bootstrap 패키지를 root 로 둔다
- 외부 라이브러리 패키지와 충돌하지 않게 한다
- 멀티모듈이면 모듈 경계를 분명히 한다
- scan 범위를 최소화한다
- 테스트에서는 필요 시 명시적 `classes=` 로 범위를 좁힌다

## 5-10. 흔한 실수
1. 메인 클래스를 너무 상위 패키지에 둔다.
2. 공통 모듈 전체를 무심코 스캔한다.
3. 이름 충돌을 빈번히 만든다.
4. meta-annotation 으로 만든 커스텀 stereotype 의 의미를 팀이 공유하지 않는다.
5. scan 대상이 아닌 패키지에 중요한 설정 클래스를 둔다.

## 5-11. 면접용 답변
"Component Scan 은 base package 아래의 classpath 를 스캔해 `@Component` 계열 또는 그 meta-annotation 을 가진 클래스를 후보로 찾고, 이들을 BeanDefinition 으로 등록합니다. 내부적으로는 보통 ASM 기반 metadata reading 을 사용해서 클래스를 즉시 로딩하지 않고 어노테이션 메타정보를 먼저 확인합니다. 이후 필터와 BeanNameGenerator 를 적용해 최종 빈 등록이 이뤄집니다."

## 5-12. 꼬리질문 포인트
- meta-annotation 은 왜 유용한가?
- scan 과 import 중 어떤 방식을 선호하나?
- Boot auto-config 는 왜 component scan 안 쓰나?
- 스캔 범위가 너무 넓으면 어떤 비용이 생기나?

# 6. `@Configuration` 과 `@Bean` 의 진짜 동작

겉으로 보기엔 단순하다.

```java
@Configuration
public class AppConfig {
    @Bean
    public OrderService orderService() {
        return new OrderService(memberRepository());
    }

    @Bean
    public MemberRepository memberRepository() {
        return new MemoryMemberRepository();
    }
}
```

하지만 면접에서는 보통 여기서 끝나지 않는다.

- `@Configuration` 과 그냥 `@Component` 에 `@Bean` 메서드 두는 것의 차이
- `proxyBeanMethods`
- CGLIB enhancement
- inter-bean method call
- lite mode / full mode
- static `@Bean`
- method parameter injection

---

## 6-1. `@Bean` 의 의미
`@Bean` 은 "이 메서드의 반환값을 빈으로 등록하라" 는 선언이다.

즉,
- 클래스 레벨 stereotype 가 아니라
- **메서드 레벨 빈 정의 방식**

메서드명 기본값이 빈 이름이 된다.

## 6-2. `@Configuration` 이 왜 필요한가
`@Configuration` 은 해당 클래스를 **설정 클래스**로 인식하게 한다.

특히 중요한 것은 **full mode** 에서 CGLIB 으로 서브클래싱되어
`@Bean` 메서드 호출을 가로채고 singleton semantics 를 보장한다는 점이다.

예:
```java
@Configuration
public class AppConfig {

    @Bean
    public A a() {
        return new A(b());
    }

    @Bean
    public B b() {
        return new B();
    }
}
```

겉으로 보면 `a()` 안에서 `b()` 를 직접 호출했으니 매번 new B() 가 될 것 같지만,
full `@Configuration` mode 에서는 CGLIB 프록시가 개입해 실제 컨테이너의 singleton `b` 빈을 반환한다.

## 6-3. lite mode
아래처럼 `@Configuration` 없이 `@Component` 등에 `@Bean` 메서드가 있을 수 있다.

```java
@Component
public class LiteConfig {

    @Bean
    public B b() {
        return new B();
    }

    @Bean
    public A a() {
        return new A(b());
    }
}
```

이 경우 `b()` 직접 호출은 일반 자바 메서드 호출이다.
즉, 컨테이너 singleton 보장이 깨질 수 있다.

### 핵심
- full mode (`@Configuration`): inter-bean method call 안전
- lite mode: 메서드 직접 호출 시 그냥 자바 호출

## 6-4. `proxyBeanMethods = false`
최근 실무에서 자주 본다.

```java
@Configuration(proxyBeanMethods = false)
public class FastConfig {
    @Bean
    public A a(B b) {
        return new A(b);
    }

    @Bean
    public B b() {
        return new B();
    }
}
```

이 설정은 CGLIB enhancement 를 하지 않는다.

장점:
- 조금 더 가볍다
- configuration class proxy 비용 감소
- 특히 자동설정 클래스에서 많이 쓰임

주의:
- `@Bean` 메서드끼리 직접 호출해 singleton 보장을 기대하면 안 된다.
- 대신 **메서드 파라미터 주입**을 사용해야 한다.

## 6-5. 실무 권장 패턴
### 잘못된 패턴
```java
@Bean
public A a() {
    return new A(b());
}
```

### 권장 패턴
```java
@Bean
public A a(B b) {
    return new A(b);
}
```

이렇게 하면 `proxyBeanMethods = false` 여도 안전하다.

## 6-6. `ConfigurationClassPostProcessor`
`@Configuration` 클래스가 특별 취급되는 이유는 누군가가 이것을 해석해 주기 때문이다.
그 주체가 핵심적으로 `ConfigurationClassPostProcessor` 다.

이 후처리기는:
- `@Configuration` 탐지
- `@ComponentScan` 처리
- `@Import` 처리
- `@ImportResource` 처리
- `@Bean` 메서드 추출
- 필요한 경우 설정 클래스 enhancement 준비

즉 annotation 기반 Java config 세계의 부트스트랩 엔진이다.

## 6-7. static `@Bean`
질문 빈도가 꽤 높다.

```java
@Configuration
public class MyConfig {

    @Bean
    public static BeanFactoryPostProcessor myPostProcessor() {
        return beanFactory -> { };
    }
}
```

왜 static 이 권장될까?

BeanFactoryPostProcessor 는 **아주 이른 시점** 에 필요하다.  
설정 클래스 인스턴스를 먼저 만들어야만 post-processor 를 얻을 수 있다면 순서가 꼬일 수 있다.  
static `@Bean` 은 설정 클래스 인스턴스 없이도 생성 가능해 이 문제를 피한다.

## 6-8. `@Import` 와 조합
`@Configuration` 은 모듈화의 기본 단위다.

- core config
- data config
- web config
- security config

를 나눠서 `@Import` 로 조립할 수 있다.

## 6-9. 면접 함정
**Q. `@Configuration` 과 `@Component` + `@Bean` 의 차이는?**  
A. "`@Configuration` 은 full mode 에서 CGLIB enhancement 를 통해 `@Bean` 메서드 간 직접 호출에도 컨테이너 singleton semantics 를 보장합니다. 반면 lite mode 에서는 그냥 자바 메서드 호출이므로 그 보장이 없습니다."

**Q. `proxyBeanMethods = false` 는 왜 쓰나요?**  
A. "설정 클래스 프록시 비용을 줄이기 위해 사용하고, 그 대신 `@Bean` 메서드 간 직접 호출을 피하고 메서드 파라미터 주입을 사용해야 합니다."

## 6-10. 핵심 요약
- `@Bean`: 메서드 레벨 빈 정의
- `@Configuration`: 설정 클래스 해석 및 필요 시 CGLIB enhancement
- full mode: inter-bean call safe
- lite mode / `proxyBeanMethods=false`: 더 가볍지만 직접 호출 주의

# 7. 의존성 주입의 실제 알고리즘

`@Autowired` 를 그냥 "자동으로 넣어주는 어노테이션" 으로만 알면 경력 면접에서는 금방 막힌다.  
실제로는 누가, 언제, 어떤 규칙으로 후보를 고르고 주입하는지 설명해야 한다.

---

## 7-1. 누가 처리하는가
핵심 주체는 `AutowiredAnnotationBeanPostProcessor` 다.

즉 `@Autowired` 는 컴파일러 기능이 아니고, 컨테이너가 빈 생성 과정에서 **후처리기** 를 통해 해석한다.

이 사실에서 중요한 결론 2개가 나온다.

1. 컨테이너 밖에서 만든 객체에는 `@Autowired` 가 자동 적용되지 않는다.
2. BeanPostProcessor 자체나 BeanFactoryPostProcessor 타입에는 `@Autowired` 적용이 제한되거나 특별한 주의가 필요하다.

## 7-2. 생성자 주입
가장 권장되는 방식이다.

```java
@Service
public class OrderService {
    private final MemberRepository memberRepository;

    public OrderService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}
```

### 장점
- 불변성
- 필수 의존성 명확
- 테스트 용이
- 순환참조 조기 발견

### 어떤 생성자를 고르는가
- 생성자가 하나면 `@Autowired` 없어도 사용 가능
- 여러 생성자가 있으면 `@Autowired` 우선순위와 required 여부 고려
- 가장 만족 가능한 생성자를 선택

## 7-3. 필드 주입
```java
@Autowired
private MemberRepository memberRepository;
```

편하지만 경력 면접에서는 보통 단점도 함께 말해야 한다.

- 불변성 약함
- 테스트 어려움
- 숨은 의존성
- 리플렉션 의존
- 순환참조를 늦게 드러낼 수 있음

실무 코드베이스에 레거시로 많더라도, 선호는 생성자 주입이다.

## 7-4. setter / 메서드 주입
선택 의존성이나 재설정 가능성이 있는 경우 쓸 수 있다.

```java
@Autowired(required = false)
public void setDiscountPolicy(DiscountPolicy discountPolicy) {
    this.discountPolicy = discountPolicy;
}
```

## 7-5. 타입 기반 조회
`@Autowired` 는 기본적으로 타입 기반이다.

문제는 같은 타입 빈이 여러 개일 때다.

### 예
```java
@Component
class RateDiscountPolicy implements DiscountPolicy {}

@Component
class FixedDiscountPolicy implements DiscountPolicy {}
```

이 경우 `DiscountPolicy` 단일 주입은 모호하다.

해결 방법:
- `@Qualifier`
- `@Primary`
- 이름 매칭
- 컬렉션 주입

## 7-6. `@Qualifier`
```java
@Autowired
public OrderService(@Qualifier("rateDiscountPolicy") DiscountPolicy discountPolicy) {
    this.discountPolicy = discountPolicy;
}
```

면접 포인트:
- `@Qualifier` 는 단순 문자열이라기보다 **후보 선택을 좁히는 메타데이터**
- 커스텀 qualifier annotation 도 가능

## 7-7. `@Primary`
```java
@Bean
@Primary
public DiscountPolicy rateDiscountPolicy() { ... }
```

여러 후보가 있을 때 기본 선택권을 준다.
하지만 명시적 `@Qualifier` 가 있으면 보통 그쪽이 우선이다.

## 7-8. 컬렉션/맵 주입
```java
@Autowired
public OrderService(Map<String, DiscountPolicy> policyMap,
                    List<DiscountPolicy> policies) {
    ...
}
```

이 패턴은 전략 패턴 구현에 유용하다.

### 면접 포인트
- "동일 타입 여러 구현체를 한 번에 받고 싶을 때 컬렉션 주입을 사용할 수 있습니다."
- 순서가 필요하면 `@Order` 나 `Ordered` 도 고려할 수 있다.

## 7-9. `Optional`, `ObjectProvider`, `Provider`
선택 주입/지연 조회를 위해 자주 등장한다.

```java
@Autowired
public MyService(ObjectProvider<HeavyClient> clientProvider) {
    this.clientProvider = clientProvider;
}
```

### 왜 쓰는가
- optional dependency
- prototype 을 singleton 에서 매번 새로 받고 싶을 때
- 실제 사용 시점까지 조회 지연
- 순환참조 회피의 임시 수단으로도 보이지만 남용 금지

## 7-10. `@Lazy`
의존 객체 자체를 프록시로 주입해 실제 사용 시점까지 초기화를 늦춘다.

```java
@Autowired
public MyService(@Lazy HeavyClient heavyClient) {
    this.heavyClient = heavyClient;
}
```

### 주의
- 진짜 문제(설계 상 순환참조, 과도한 초기화 비용)를 숨길 수 있다.
- 디버깅 시 실제 타입이 프록시여서 혼란이 생긴다.

## 7-11. `@Value`
프로퍼티 값을 주입하는 방식이다.

```java
@Value("${app.name}")
private String appName;
```

이건 타입 기반 빈 탐색과는 성격이 다르다.
Environment / PropertyResolver / conversion service 와 엮인다.

## 7-12. `@Resource`, `@Inject`
- `@Resource`: 주로 이름 기반 성향
- `@Inject`: JSR 표준, 의미는 비슷
- 실무에서 Spring 네이티브 기능과 문맥이 잘 맞는 건 `@Autowired`

## 7-13. resolvable dependency
스프링은 일부 타입을 일반 빈이 아니어도 주입할 수 있게 해둔다.

예:
- `ApplicationContext`
- `BeanFactory`
- `Environment`
- `ResourceLoader`
- `ApplicationEventPublisher`

이건 컨테이너가 특별 취급하는 resolvable dependency 다.

## 7-14. 주입 시점
중요하다.

- 생성자 주입: 인스턴스 생성 시점
- 필드/메서드 주입: 인스턴스 생성 직후, 초기화 전에

그래서 필드 주입이 "객체가 생성되긴 했지만 아직 완전한 상태가 아닌 순간" 을 만들 수 있다.

## 7-15. 면접용 한 줄 정리
"`@Autowired` 는 스프링이 BeanPostProcessor 를 통해 처리하며, 기본적으로 타입 기반으로 후보를 찾고 `@Qualifier`, `@Primary`, 이름, 컬렉션 주입 규칙 등을 사용해 최종 대상을 결정합니다. 생성자 주입이 가장 명시적이고 안전한 방식입니다."

## 7-16. 꼬리질문
- `@Autowired(required=false)` 와 `Optional<T>` 차이?
- 왜 BeanPostProcessor 에는 `@Autowired` 가 잘 안 먹는가?
- 빈이 두 개인데 하나도 `@Primary` 가 아니면 어떻게 되나?
- generic type 은 후보 선택에 어떻게 반영되나?

# 8. 빈 생성 파이프라인과 생명주기

스프링 면접에서 제일 자주 나오고, 동시에 대답이 가장 어설퍼지기 쉬운 주제다.  
"빈 생명주기" 를 묻는 질문은 사실 아래를 함께 묻는 경우가 많다.

- 인스턴스는 언제 만들어지나
- 의존성은 언제 주입되나
- Aware 는 언제 불리나
- `@PostConstruct` 와 `afterPropertiesSet()` 순서는?
- 프록시는 언제 씌워지나
- destroy callback 은 언제 불리나
- prototype 은 왜 destroy 관리가 안 되나

---

## 8-1. 큰 그림
싱글톤 빈 기준으로 매우 단순화하면:

1. BeanDefinition 준비
2. 빈 인스턴스 생성
3. 의존성 주입
4. Aware 콜백
5. BeanPostProcessor before-init
6. init 콜백 (`@PostConstruct`, `afterPropertiesSet`, custom init-method`)
7. BeanPostProcessor after-init
8. 사용
9. 컨텍스트 종료 시 destroy 콜백 (`@PreDestroy`, `destroy`, custom destroy-method`)

하지만 면접에서는 이 순서를 정확히 구분해야 한다.

## 8-2. instantiate
먼저 생성자를 호출해 인스턴스를 만든다.

- 생성자 선택
- constructor argument 해석
- 필요 시 factory method 사용
- circular reference 대응을 위한 early singleton exposure 준비 가능

## 8-3. populate properties
생성자 이후 필드/세터 등의 의존성 주입이 일어난다.

- `@Autowired`
- `@Value`
- `@Resource`
- XML property injection
- 기타 annotation 기반 주입

즉 객체는 "생성되었지만 아직 초기화가 끝나지 않은 상태" 를 거친다.

## 8-4. Aware callbacks
아래 인터페이스를 구현하면 컨테이너가 관련 객체를 주입해 준다.

- `BeanNameAware`
- `BeanClassLoaderAware`
- `BeanFactoryAware`
- `EnvironmentAware`
- `ResourceLoaderAware`
- `ApplicationEventPublisherAware`
- `ApplicationContextAware`

### 왜 존재하나
컨테이너와 더 강하게 상호작용해야 할 때 쓰인다.
다만 애플리케이션 코드에서 남용하면 스프링 의존도가 너무 커진다.

## 8-5. BeanPostProcessor before initialization
후처리기의 `postProcessBeforeInitialization()` 이 호출된다.

여기서 할 수 있는 일:
- 공통 초기화
- annotation 해석 일부
- 초기화 전 상태 변경

## 8-6. init callbacks
보통 다음 순서로 생각하면 된다.

1. `@PostConstruct`
2. `InitializingBean.afterPropertiesSet()`
3. custom `initMethod`

실제 환경과 후처리기 구성에 따라 세부 차이가 보일 수 있으나, 면접에서는 이 순서를 기억하면 된다.

### `@PostConstruct`
JSR/자카르타 계열 어노테이션.
스프링은 `CommonAnnotationBeanPostProcessor` 류가 처리한다.

### `InitializingBean`
스프링 인터페이스 직접 구현.
프레임워크 의존성이 생기므로 애플리케이션 코드에는 덜 선호.

### `initMethod`
`@Bean(initMethod="...")` 또는 XML 설정으로 지정.
외부 라이브러리 빈, 소스 수정 불가 클래스에 유용.

## 8-7. BeanPostProcessor after initialization
`postProcessAfterInitialization()` 단계.

매우 중요하다.

왜냐하면 **프록시 wrapping 이 주로 여기서 일어나기 때문**이다.

예:
- AOP proxy
- transaction proxy
- validation proxy
- 기타 decorator 형태

즉 최종적으로 컨테이너에 저장되는 객체는 원본이 아니라 **프록시일 수 있다.**

## 8-8. 사용 단계
여기서부터 애플리케이션 코드가 빈을 사용한다.
하지만 이때의 빈은 다음 중 하나일 수 있다.

- 원본 객체
- JDK dynamic proxy
- CGLIB subclass proxy
- scoped proxy

그래서 `getClass()` 결과가 예상과 다를 수 있다.

## 8-9. destroy 단계
컨텍스트 종료 시 singleton 빈은 destroy 콜백이 불린다.

대표 방식:
- `@PreDestroy`
- `DisposableBean.destroy()`
- custom `destroyMethod`

### 언제 중요한가
- 커넥션 정리
- 스레드풀 정리
- 네트워크 client close
- 파일 핸들 닫기
- metrics flush

## 8-10. prototype 빈은 왜 destroy 관리가 약한가
prototype 은 요청할 때마다 새 객체를 반환한다.
컨테이너는 생성까지만 관여하고, 이후 lifecycle 전체를 추적하지 않는다.

그래서 destroy callback 을 자동 보장하지 않는다.
즉 prototype 은 "new 를 컨테이너가 대신해 주는 수준" 에 가깝다.

## 8-11. SmartInitializingSingleton
모든 singleton 초기화가 끝난 뒤 한 번 호출되는 훅.

언제 유용한가:
- 모든 빈 준비 후 동작해야 하는 초기 검증
- registry 조합
- 캐시 preload
- 리스너 등록 마무리

`@PostConstruct` 와 차이:
- `@PostConstruct` 는 그 빈 하나의 초기화 직후
- `SmartInitializingSingleton` 은 **전체 singleton 초기화 완료 후**

## 8-12. Lifecycle / SmartLifecycle
컨텍스트 시작/종료와 더 넓게 연결되는 훅.

- start / stop
- phase 제어 가능
- autoStartup

메시지 리스너, scheduler, 연결 유지 컴포넌트에서 의미가 크다.

## 8-13. 프록시와 초기화 순서의 함정
면접에서 자주 나온다.

### 질문
"`@PostConstruct` 안에서 `@Transactional` 메서드를 호출하면 트랜잭션이 적용되나요?"

보통 안전한 답변:
- 초기화 시점에는 아직 최종 프록시가 완전히 적용되기 전이거나,
- self-invocation 문제로 인해 기대한 방식으로 작동하지 않을 수 있다.
- 따라서 `@PostConstruct` 에서 트랜잭션/비동기/보안 프록시에 기대는 코드는 피하는 것이 좋다.

## 8-14. 라이프사이클 전체 그림 ASCII
```text
BeanDefinition 등록
    ↓
instantiate (생성자/factory method)
    ↓
dependency injection (필드/세터/메서드)
    ↓
Aware callbacks
    ↓
BeanPostProcessor.beforeInitialization
    ↓
@PostConstruct
    ↓
InitializingBean.afterPropertiesSet
    ↓
custom initMethod
    ↓
BeanPostProcessor.afterInitialization
    ↓
(최종 빈 사용: 원본 또는 프록시)
    ↓
컨텍스트 종료
    ↓
@PreDestroy
    ↓
DisposableBean.destroy
    ↓
custom destroyMethod
```

## 8-15. 면접 답변 예시
"스프링 빈의 생명주기는 빈 정의 등록 후 인스턴스 생성, 의존성 주입, aware callback, post-processor 전처리, 초기화 콜백, post-processor 후처리, 사용, 종료 순서로 이해하면 됩니다. `@Autowired` 는 생성 후 초기화 전에 적용되고, `@PostConstruct` 는 그 뒤에 실행되며, AOP 프록시는 보통 after-initialization 단계에서 감싸집니다."

## 8-16. 실무 포인트
- 무거운 외부 호출을 `@PostConstruct` 에 넣지 말 것
- 예외 발생 시 부팅 전체 실패 가능
- 긴 초기화는 readiness / warm-up 전략과 분리 검토
- prototype 은 자원 해제 책임이 사용자에게 갈 수 있음

# 9. BeanPostProcessor 딥다이브

스프링을 "어노테이션 프레임워크" 로만 보면 절대 안 보이는 핵심 확장 포인트가 바로 BeanPostProcessor 다.  
정말 많은 기능이 이 한 축 위에 올라가 있다.

- `@Autowired`
- `@PostConstruct`
- AOP 프록시
- 예외 변환
- 커스텀 annotation 기반 확장

즉 BeanPostProcessor 를 이해하면
"스프링이 빈을 어떻게 가공하는가" 가 보인다.

---

## 9-1. 기본 개념
`BeanPostProcessor` 는 빈 인스턴스 생성 과정에 끼어들어 전후 처리를 할 수 있는 인터페이스다.

대표 메서드:
```java
Object postProcessBeforeInitialization(Object bean, String beanName)
Object postProcessAfterInitialization(Object bean, String beanName)
```

반환값으로 **다른 객체를 돌려줄 수도 있다.**
이게 매우 중요하다.
프록시를 씌운 새 객체를 반환하면 컨테이너는 그걸 최종 빈으로 사용한다.

## 9-2. 왜 중요한가
스프링의 많은 어노테이션은 단독으로는 아무것도 하지 않는다.
실제로 동작하게 만드는 주체가 후처리기다.

예:
- `@Autowired` → `AutowiredAnnotationBeanPostProcessor`
- `@PostConstruct` → `CommonAnnotationBeanPostProcessor`
- AOP / `@Transactional` → AutoProxyCreator + interceptor chain

즉 어노테이션은 **메타데이터**, 후처리기가 **실행 엔진** 이다.

## 9-3. 등록 시점
후처리기 자체는 `refresh()` 중 `registerBeanPostProcessors()` 단계에서 먼저 등록된다.
그리고 그 이후 생성되는 일반 빈들에 대해 적용된다.

여기서 중요한 함정:
- 후처리기보다 먼저 생성된 일부 인프라 빈에는 후처리기가 덜 적용될 수 있다.
- 후처리기 자신에게 또 다른 후처리기가 완전하게 적용되지 않을 수 있다.

## 9-4. ordering
후처리기가 여러 개면 순서가 중요하다.

- `PriorityOrdered`
- `Ordered`
- 나머지

왜 중요하냐면,
`@Autowired` 를 먼저 해야 할 수도 있고,
그 후에 프록시를 감싸야 할 수도 있기 때문이다.

## 9-5. BeanPostProcessor 는 인스턴스를 다룬다
BeanFactoryPostProcessor 와의 차이를 다시 강조하자.

- BeanFactoryPostProcessor: BeanDefinition 수정
- BeanPostProcessor: bean instance 수정

면접에서 두 개를 섞어 말하면 깊이가 떨어져 보인다.

## 9-6. 대표적인 사용 패턴
### 1) 공통 초기화
모든 특정 타입/어노테이션 대상 빈에 공통 속성 설정

### 2) validation
초기화 후 검증

### 3) wrapper / decorator
프록시 또는 래퍼로 감싸기

### 4) annotation driven behavior
특정 어노테이션 해석 후 동작 적용

## 9-7. 프록시 생성과 BeanPostProcessor
AOP 프록시는 보통 AutoProxyCreator 류가 BeanPostProcessor 로 동작하며 after-init 단계에서 빈을 감싼다.

그래서 질문이 자주 나온다.

**Q. 트랜잭션 프록시는 언제 만들어지나요?**  
A. "대개 BeanPostProcessor/AutoProxyCreator 가 초기화 후 단계에서 해당 빈을 프록시로 감쌉니다."

## 9-8. BeanPostProcessor 계열 심화
실제론 확장 인터페이스가 많다.

- `InstantiationAwareBeanPostProcessor`
- `SmartInstantiationAwareBeanPostProcessor`
- `MergedBeanDefinitionPostProcessor`
- `DestructionAwareBeanPostProcessor`

### 왜 이렇게 많은가
빈 생성 파이프라인의 서로 다른 지점에 개입하기 위해서다.

#### `InstantiationAwareBeanPostProcessor`
- 인스턴스 생성 전후 개입
- property population 전에 개입
- 특정 상황에서는 기본 인스턴스화 자체를 대체 가능

#### `SmartInstantiationAwareBeanPostProcessor`
- 예측 타입
- 후보 생성자 결정
- early bean reference 제공

AOP 와 순환참조가 섞이는 어려운 영역에서 등장한다.

#### `MergedBeanDefinitionPostProcessor`
- 병합된 BeanDefinition 기반 후처리
- annotation metadata 캐싱 등에 도움

#### `DestructionAwareBeanPostProcessor`
- destroy 직전 개입

## 9-9. custom BeanPostProcessor 예제
```java
@Component
public class LoggingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.startsWith("order")) {
            System.out.println("[beforeInit] " + beanName + " -> " + bean.getClass());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.startsWith("order")) {
            System.out.println("[afterInit] " + beanName + " -> " + bean.getClass());
        }
        return bean;
    }
}
```

이걸 돌려보면 어떤 빈이 프록시로 바뀌는지 눈에 보인다.

## 9-10. self-application 문제
후처리기 자신이 다시 후처리 대상이 되는 문제는 복잡하다.
그래서 스프링은 후처리기를 먼저 특수 취급하고 등록한다.

즉 **후처리기 시스템은 일반 빈 생성 파이프라인 바깥쪽의 메타 레이어** 라고 생각하면 이해가 쉽다.

## 9-11. 성능 측면
후처리기는 모든 빈 생성에 개입할 수 있으므로 남용하면 시작 성능에 영향을 준다.

특히 위험한 패턴:
- 매 빈마다 무거운 리플렉션
- 네트워크 호출
- 과도한 스캔
- 너무 넓은 pointcut 계산

## 9-12. 면접용 답변
"BeanPostProcessor 는 빈 인스턴스 생성 과정의 전후에 개입하는 확장 포인트입니다. `@Autowired`, `@PostConstruct`, AOP 프록시 같은 기능이 이 축 위에서 동작합니다. 핵심은 반환값으로 다른 객체를 돌려줄 수 있다는 점이며, 그래서 프록시 wrapping 이 가능합니다."

## 9-13. 꼬리질문
- BeanPostProcessor 와 AOP 의 관계는?
- `postProcessAfterInitialization()` 에서 프록시를 반환하면 원본은 어디 가나?
- BeanPostProcessor 에 `@Autowired` 가 안 되는 이유는?
- 왜 후처리기 순서가 중요한가?

# 10. BeanFactoryPostProcessor 와 BeanDefinitionRegistryPostProcessor

이 둘은 BeanPostProcessor 보다 한 단계 더 이른, **메타데이터 단계** 의 확장 포인트다.  
경력직 면접에서는 둘의 차이를 정확히 말해야 한다.

---

## 10-1. BeanFactoryPostProcessor
핵심 개념:

> 빈 인스턴스를 만들기 전에, BeanFactory 에 등록된 **BeanDefinition** 들을 수정할 수 있는 후처리기

즉 실제 객체를 만지면 안 된다.
이 단계의 대상은 **정의** 다.

### 왜 인스턴스를 만지면 안 되나
너무 일찍 bean 을 생성해 버리면:
- 후처리기 적용 순서가 깨지고
- 아직 등록되지 않은 다른 후처리기를 놓칠 수 있고
- 컨테이너 일관성이 무너진다

## 10-2. 대표 사용 사례
- Property placeholder 치환
- BeanDefinition 속성 수정
- 기본값 주입
- 특정 스코프/조건 강제 변경
- 커스텀 framework bootstrap

## 10-3. BeanDefinitionRegistryPostProcessor
이건 더 강하다.

> BeanDefinitionRegistry 를 직접 다뤄 **새 BeanDefinition 을 추가 등록** 할 수 있다.

즉 "기존 정의 수정" 을 넘어서,
"정의 자체를 더 넣는" 단계다.

### 왜 필요한가
`@Configuration` 해석 같은 작업은 다른 빈 정의를 많이 추가한다.
예: `@Bean` 메서드를 읽어 BeanDefinition 으로 바꾸기.

## 10-4. 순서
아주 중요하다.

1. `BeanDefinitionRegistryPostProcessor`
2. 그 과정에서 추가 등록된 BeanFactoryPostProcessor 포함
3. 일반 `BeanFactoryPostProcessor`

즉 "정의를 더 넣을 기회" 를 먼저 주고,
그 뒤에 전체 정의를 가공한다.

## 10-5. 대표 구현체: `ConfigurationClassPostProcessor`
이 클래스는 사실상 annotation 기반 설정의 심장부다.

하는 일:
- `@Configuration` 처리
- `@ComponentScan` 처리
- `@Import` 처리
- `@ImportResource` 처리
- `@Bean` 메서드 등록

즉 Spring Java Config 세계는 상당 부분 이 후처리기에서 시작한다.

## 10-6. static `@Bean` 이 권장되는 이유 재정리
BeanFactoryPostProcessor 를 `@Bean` 으로 등록할 때 static 권장이 붙는 이유는,
설정 클래스 인스턴스 생성 없이도 조기에 post-processor 를 얻어야 하기 때문이다.

예:
```java
@Configuration
public class PostProcessorConfig {

    @Bean
    public static BeanFactoryPostProcessor customBfpp() {
        return beanFactory -> {
            BeanDefinition bd = beanFactory.getBeanDefinition("orderService");
            bd.setLazyInit(true);
        };
    }
}
```

## 10-7. 커스텀 예제
```java
@Component
public class LazyAllServicesPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(name);
            if (bd.getBeanClassName() != null && bd.getBeanClassName().endsWith("Service")) {
                bd.setLazyInit(true);
            }
        }
    }
}
```

### 주의
실무에서 이런 전역 변경은 강력하지만 위험하다.
명시성이 떨어지고 예측하기 어려워진다.

## 10-8. RegistryPostProcessor 예제
```java
@Component
public class DynamicBeanRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        RootBeanDefinition bd = new RootBeanDefinition(HelloService.class);
        registry.registerBeanDefinition("helloService", bd);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }
}
```

## 10-9. 언제 써야 하나
애플리케이션 코드에서는 거의 잘 안 쓴다.
하지만 아래 상황에서는 중요하다.

- 프레임워크/라이브러리 개발
- 조직 공통 스타터 제작
- 대규모 정책 주입
- 동적 빈 등록
- 테스트 인프라 확장

## 10-10. 면접 답변 포인트
"BeanFactoryPostProcessor 는 bean instance 가 아니라 BeanDefinition 을 수정하는 확장 포인트이고, BeanDefinitionRegistryPostProcessor 는 더 이른 단계에서 새로운 BeanDefinition 을 직접 등록할 수 있는 확장 포인트입니다. `ConfigurationClassPostProcessor` 가 대표 사례입니다."

## 10-11. 자주 나오는 꼬리질문
- 왜 BFPP 에서 getBean() 하면 위험한가?
- `@Value` 치환은 어느 단계에서 되나?
- `@Configuration` 은 누가 해석하나?
- 자동설정 클래스도 결국 어떤 후처리기에서 처리되나?

# 11. 중요한 내장 후처리기들

"스프링이 알아서 해준다" 는 말은 대부분 내부에 어떤 후처리기가 있다는 뜻이다.  
경력직이라면 최소한 핵심 내장 후처리기 이름과 역할 정도는 연결해서 설명할 수 있어야 한다.

---

## 11-1. `AutowiredAnnotationBeanPostProcessor`
담당:
- `@Autowired`
- `@Value`
- `@Inject` 일부 처리

개입 시점:
- 생성자 선택
- 필드 주입
- 메서드 주입

포인트:
- 필드는 생성 직후, config 메서드 호출 전에 주입된다.
- 단순 reflection 주입이 아니라 candidate resolution 알고리즘이 들어간다.
- `SmartInstantiationAwareBeanPostProcessor` 계열 역할도 수행한다.

## 11-2. `CommonAnnotationBeanPostProcessor`
담당:
- `@PostConstruct`
- `@PreDestroy`
- `@Resource`
- 기타 공통 자카르타/JSR 계열 annotation

즉 lifecycle callback 과 일부 표준 의존성 주입을 지원한다.

## 11-3. `ApplicationContextAwareProcessor`
담당:
- `ApplicationContextAware`
- `EnvironmentAware`
- `ResourceLoaderAware`
- `ApplicationEventPublisherAware`
등의 aware 콜백 지원

이건 "컨테이너 인프라 객체를 특정 인터페이스를 통해 주입" 하는 역할이다.

## 11-4. AutoProxyCreator 계열
대표적으로 `AnnotationAwareAspectJAutoProxyCreator` 류가 있다.

담당:
- AOP 적용 대상 탐색
- advisor 체인 구성
- 빈을 프록시로 wrapping

결과적으로 `@Transactional`, `@Async`, 보안, 커스텀 AOP 등이 이 축과 연결된다.

## 11-5. `PersistenceExceptionTranslationPostProcessor`
이름이 길지만 중요하다.

담당:
- `@Repository` 빈에 대해 persistence exception translation 적용
- DB 접근 기술별 예외를 Spring 의 `DataAccessException` 계층으로 변환

즉 `@Repository` 는 단순한 stereotype 이상 의미를 가진다.

## 11-6. `ConfigurationClassPostProcessor`
정확히는 BeanFactoryPostProcessor/RegistryPostProcessor 축에 있지만 너무 중요해서 다시 적는다.

담당:
- `@Configuration`
- `@ComponentScan`
- `@Import`
- `@Bean`

즉 애노테이션 기반 설정 세계를 실제 BeanDefinition 으로 바꾸는 핵심 엔진.

## 11-7. `AnnotationConfigUtils`
이름을 꼭 외울 필요는 없지만,
annotation 기반 컨테이너를 쓰면 여러 공통 post-processor/BFPP 를 한 번에 등록해 준다.

즉 우리가 `AnnotationConfigApplicationContext` 나 Boot 를 쓸 때 "기본 인프라가 깔리는" 출발점 중 하나다.

## 11-8. 내장 후처리기를 알면 좋은 이유
1. 어떤 어노테이션이 왜 동작하는지 설명 가능
2. 커스텀 확장 지점 선택 가능
3. 디버깅 포인트가 생김
4. "왜 이 빈은 주입이 안 되지?" 를 구조적으로 분석 가능

## 11-9. 면접 답변 예시
"`@Autowired` 는 `AutowiredAnnotationBeanPostProcessor` 가 처리하고, `@PostConstruct` 는 `CommonAnnotationBeanPostProcessor` 계열이 처리합니다. `@Transactional` 은 별도의 트랜잭션 post-processor가 직접 구현한다기보다 AOP auto-proxy creator 와 `TransactionInterceptor` 의 조합으로 적용됩니다."

## 11-10. 정리 표

| 기능 | 핵심 처리 주체 |
|---|---|
| `@Autowired` | `AutowiredAnnotationBeanPostProcessor` |
| `@Value` | `AutowiredAnnotationBeanPostProcessor` + value resolver |
| `@PostConstruct` / `@PreDestroy` | `CommonAnnotationBeanPostProcessor` |
| Aware 인터페이스 | `ApplicationContextAwareProcessor` |
| `@Configuration`, `@Bean`, `@ComponentScan` | `ConfigurationClassPostProcessor` |
| `@Transactional` | AutoProxyCreator + `TransactionInterceptor` |
| `@Repository` 예외 변환 | `PersistenceExceptionTranslationPostProcessor` |

## 11-11. 꼬리질문
- `@Resource` 와 `@Autowired` 처리 주체가 왜 다른가?
- 왜 어떤 기능은 BPP 이고 어떤 기능은 BFPP 인가?
- 커스텀 annotation 을 만들면 어느 확장 포인트를 택해야 하는가?

# 12. `FactoryBean` 과 일반 Bean, 그리고 흔한 혼동 정리

면접에서 자주 헷갈리는 주제다.

- `@Bean` 과 `FactoryBean`
- 팩토리 패턴과 `FactoryBean`
- `BeanFactory` 와 `FactoryBean`
- `&myFactoryBean` 조회

---

## 12-1. `FactoryBean` 이란
`FactoryBean<T>` 는 **객체 생성 로직을 캡슐화한 특별한 빈**이다.

컨테이너가 이 빈을 일반 빈처럼 관리하지만,
실제 `getBean("myFactoryBean")` 을 하면 `FactoryBean` 자신이 아니라 보통 `getObject()` 결과를 돌려준다.

```java
public class ApiClientFactoryBean implements FactoryBean<ApiClient> {

    @Override
    public ApiClient getObject() {
        return new ApiClient("https://api.example.com");
    }

    @Override
    public Class<?> getObjectType() {
        return ApiClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
```

## 12-2. 왜 필요한가
객체 생성이 단순 생성자 호출보다 복잡한 경우:

- 외부 설정 필요
- 동적 프록시 생성
- 리소스 초기화
- third-party 객체 래핑
- 복잡한 초기화 로직 재사용

## 12-3. 조회 차이
- `getBean("myFactoryBean")` → 생성된 제품 객체
- `getBean("&myFactoryBean")` → `FactoryBean` 자체

이 `&` 규칙은 면접 단골이다.

## 12-4. `@Bean` 과의 차이
### `@Bean`
설정 클래스의 메서드 수준에서 빈 생성.

### `FactoryBean`
생성 로직 그 자체를 빈으로 캡슐화.

간단히 말하면:
- `@Bean`: "이 메서드로 만들어"
- `FactoryBean`: "이 factory 객체가 알아서 만들어"

## 12-5. 언제 무엇을 쓰나
대부분의 애플리케이션에서는 `@Bean` 이 훨씬 간단하다.
`FactoryBean` 은 라이브러리/인프라 확장, 복잡한 생성, 프록시 조립에서 더 의미가 크다.

## 12-6. 예시: JDK dynamic proxy 를 제품으로 제공
```java
public class TxProxyFactoryBean implements FactoryBean<OrderService> {
    private final OrderService target;

    public TxProxyFactoryBean(OrderService target) {
        this.target = target;
    }

    @Override
    public OrderService getObject() {
        return (OrderService) java.lang.reflect.Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class[]{OrderService.class},
                (proxy, method, args) -> {
                    System.out.println("before");
                    Object result = method.invoke(target, args);
                    System.out.println("after");
                    return result;
                }
        );
    }

    @Override
    public Class<?> getObjectType() {
        return OrderService.class;
    }
}
```

## 12-7. 면접용 한 줄
"`FactoryBean` 은 생성 로직을 캡슐화한 특별한 빈이며, `getBean(name)` 호출 시 보통 factory 자신이 아니라 factory 가 만든 제품 객체를 반환합니다. factory 자체가 필요하면 `&name` 으로 조회합니다."

## 12-8. 꼬리질문
- `FactoryBean` 이 singleton 이면 제품도 singleton 인가?
- `isSingleton()` 의미는?
- `@Bean` 으로 충분한데 굳이 `FactoryBean` 쓰는 경우는?

# 13. 스코프(scope)와 scoped proxy

빈은 모두 singleton 이라고 생각하면 절반만 이해한 것이다.  
스코프를 이해해야 웹 요청, 세션, prototype, 그리고 스코프 혼합 문제를 설명할 수 있다.

---

## 13-1. 기본 스코프
대표 스코프:

- singleton
- prototype
- request
- session
- application
- websocket

### singleton
스프링 기본값.
단, GoF singleton 과 동일하지 않다.
**컨테이너 당, 빈 정의 당 하나**다.

### prototype
요청할 때마다 새 인스턴스.
컨테이너는 생성까지만 관여하고 destroy 전체를 관리하지 않는다.

### request / session / application
웹 컨텍스트 범위에서 살아간다.

## 13-2. 왜 스코프가 중요한가
문제는 **긴 스코프 빈이 짧은 스코프 빈을 주입받을 때** 생긴다.

예:
- singleton service 가 request bean 을 직접 주입
- singleton bean 이 prototype bean 을 "항상 새것" 으로 기대

기본 주입만 하면 초기 1회 resolved 된 객체를 계속 쓴다.

## 13-3. singleton 에 prototype 주입
```java
@Component
@Scope("prototype")
class PrototypeBean {}

@Component
class SingletonBean {
    private final PrototypeBean prototypeBean;

    public SingletonBean(PrototypeBean prototypeBean) {
        this.prototypeBean = prototypeBean;
    }
}
```

이렇게 하면 singleton 생성 시점에 주입된 prototype 인스턴스 하나만 계속 쓴다.

### 해결
- `ObjectProvider<PrototypeBean>`
- `Provider<PrototypeBean>`
- `@Lookup`
- scoped proxy (웹 스코프 등)

## 13-4. `ObjectProvider`
```java
@Component
class SingletonBean {
    private final ObjectProvider<PrototypeBean> provider;

    public SingletonBean(ObjectProvider<PrototypeBean> provider) {
        this.provider = provider;
    }

    public PrototypeBean getNewOne() {
        return provider.getObject();
    }
}
```

이 방식이 보통 가장 이해하기 쉽다.

## 13-5. request scope 와 proxy
웹에서 request scope 빈을 singleton controller/service 에 넣고 싶다면 프록시가 필요하다.

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
class RequestContextHolderBean {
}
```

이 경우 실제 주입되는 것은 request bean 자체가 아니라 **scoped proxy** 다.
프록시는 현재 요청 컨텍스트에 맞는 실제 객체를 뒤에서 찾아준다.

## 13-6. 왜 프록시가 필요한가
singleton 은 앱 시작 시 만들어지고 오래 산다.
request bean 은 HTTP 요청이 들어와야 존재한다.

즉 생성 시점 자체가 안 맞는다.
이 간극을 프록시가 메운다.

## 13-7. 면접 포인트
**Q. prototype 을 singleton 에 주입하면 매번 새 객체가 들어가나요?**  
A. "아닙니다. 기본 주입은 singleton 생성 시점에 1회만 이뤄지므로 이후 같은 인스턴스를 참조합니다. 매번 새 인스턴스가 필요하면 `ObjectProvider` 나 method injection 을 써야 합니다."

**Q. request scope 빈을 singleton 에 주입하려면?**  
A. "scoped proxy 를 사용해 지연 위임하도록 할 수 있습니다."

## 13-8. 실무 주의
- scoped proxy 는 편리하지만 실제 타입이 프록시라 디버깅 난도가 오른다.
- request/session scope 남용은 숨은 상태를 만들 수 있다.
- Stateless 서비스를 지향할수록 운영/테스트가 단순해진다.

# 14. 순환참조(circular dependency)와 3단계 캐시

이 주제는 경력직 면접에서 "스프링 내부를 진짜 아는지" 를 확인하기 좋다.  
특히 생성자 주입, setter 주입, AOP 프록시, early exposure 가 섞이면 훨씬 깊어질 수 있다.

---

## 14-1. 순환참조란
A 가 B 를 필요로 하고, B 가 다시 A 를 필요로 하는 경우.

```java
@Component
class A {
    A(B b) {}
}

@Component
class B {
    B(A a) {}
}
```

이건 생성자 주입 기준으로 해결이 불가능하다.
둘 다 완성되기 전에 서로가 필요하기 때문이다.

## 14-2. 왜 생성자 순환참조는 막히는가
생성자 주입은 "완전한 의존 객체" 가 있어야 생성자를 호출할 수 있다.
따라서 A 를 만들려면 B 가 완성돼야 하고, B 를 만들려면 A 가 완성돼야 한다.
결국 deadlock 같은 논리 모순이 된다.

그래서 Spring 은 보통 `BeanCurrentlyInCreationException` 을 낸다.

## 14-3. setter / field 순환참조는 왜 일부 가능했나
옛 스프링에서는 singleton + setter/field injection 의 경우, 객체를 먼저 덜 완성된 상태로 노출하는 방식으로 일부 순환참조를 풀 수 있었다.

이때 등장하는 것이 **3단계 캐시** 다.

## 14-4. 3단계 캐시 구조
싱글톤 생성 관련 핵심 저장소를 단순화하면:

1. `singletonObjects`
   - 완성된 singleton

2. `earlySingletonObjects`
   - 조기 노출된 singleton 참조

3. `singletonFactories`
   - 필요 시 early reference 를 만들어 줄 factory

### 왜 3단계인가
2단계만으로는 AOP 프록시와 조기 참조를 동시에 유연하게 다루기 어렵다.
factory 단계가 있어야 "원본을 줄지, early proxy 를 줄지" 선택할 여지가 생긴다.

## 14-5. 흐름 예시
1. A 생성 시작
2. A 를 완전히 만들기 전, `singletonFactories` 에 early reference factory 등록
3. A 가 B 를 필요로 해서 B 생성 시작
4. B 가 다시 A 를 필요로 함
5. 이때 컨테이너가 A 를 만들고 있는 중이므로 factory 를 통해 early reference 획득
6. B 생성 완료
7. 다시 A 생성 마무리

이 메커니즘 덕분에 일부 setter/field 순환참조가 풀리곤 했다.

## 14-6. 왜 위험한가
가능하다고 좋은 게 아니다.

- 덜 초기화된 객체가 노출될 수 있다.
- 프록시 타이밍과 엮이면 더 복잡하다.
- 설계 상 강한 결합의 신호다.
- 테스트와 유지보수가 어려워진다.
- 초기화 순서 버그를 감춘다.

## 14-7. Boot 에서 기본 금지 흐름
최근 Spring Boot 는 circular references 허용을 기본값으로 꺼 두는 방향이다.
즉 예전처럼 "어느 정도는 돌아가네" 보다
"설계를 고쳐라" 쪽으로 유도한다.

## 14-8. AOP 와 early reference
이게 깊은 꼬리질문 포인트다.

만약 A 가 나중에 프록시 대상이라면,
B 에 주입되는 early reference 도 최종 프록시와 일관성이 있어야 한다.
그래서 `SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference()` 같은 개념이 중요해진다.

즉 3단계 캐시는 단순히 "미완성 객체를 잠깐 보여준다" 수준이 아니라,
**프록시 가능성까지 고려한 early reference 전략** 이다.

## 14-9. 해결 방법
순환참조는 가능하면 구조적으로 없애야 한다.

### 방법 1) 책임 분리
A, B 가 서로 너무 많이 안다 → C 로 공통 관심사 추출

### 방법 2) 방향 재설계
도메인/서비스 의존 방향을 단방향으로 정리

### 방법 3) 이벤트 사용
직접 호출 대신 이벤트 발행

### 방법 4) 조회 시점 지연
정말 불가피하면 `ObjectProvider`, `@Lazy` 등을 고려  
단, 이건 구조 해결이 아니라 지연/우회일 수 있다.

## 14-10. 면접 답변
"생성자 기반 순환참조는 해결할 수 없고 보통 예외가 납니다. setter/field 기반 singleton 순환참조는 과거에는 3단계 캐시(`singletonObjects`, `earlySingletonObjects`, `singletonFactories`)를 통해 일부 해결되기도 했지만, 설계상 좋지 않고 Boot 에서는 기본적으로 금지 방향입니다. 특히 AOP 프록시가 얽히면 early bean reference 와 최종 프록시 일관성이 더 중요해집니다."

## 14-11. 실무 팁
순환참조를 마주치면 "스프링이 풀 수 있나?" 보다 먼저  
"왜 이 두 컴포넌트가 서로를 알아야 하지?" 를 질문해야 한다.

# 15. 어노테이션은 어떻게 실제 동작으로 연결되는가

스프링 어노테이션을 잘 안다는 건 이름을 많이 외우는 게 아니라,
**각 어노테이션이 어느 단계에서 어떤 처리 주체에 의해 의미를 갖는가** 를 아는 것이다.

---

## 15-1. 단계별 분류
아래 표가 매우 중요하다.

| 단계 | 대표 어노테이션/개념 | 처리 주체 |
|---|---|---|
| BeanDefinition 등록 단계 | `@Component`, `@ComponentScan`, `@Configuration`, `@Bean`, `@Import` | `ConfigurationClassPostProcessor` 등 |
| 의존성 주입 단계 | `@Autowired`, `@Value`, `@Resource` | `AutowiredAnnotationBeanPostProcessor`, `CommonAnnotationBeanPostProcessor` |
| 초기화 단계 | `@PostConstruct` | `CommonAnnotationBeanPostProcessor` |
| 프록시/AOP 단계 | `@Transactional`, `@Async`, AOP 관련 | AutoProxyCreator + interceptor |
| 종료 단계 | `@PreDestroy` | `CommonAnnotationBeanPostProcessor` |

이 표를 설명할 수 있으면 면접에서 상당히 강하다.

## 15-2. 왜 중요하나
예를 들어 질문이 들어온다.

### Q. 왜 `new` 로 만든 객체에는 `@Autowired` 가 안 되나요?
A. `@Autowired` 는 컨테이너가 BeanPostProcessor 단계에서 처리하기 때문이다. 컨테이너 밖에서 만든 객체는 그 파이프라인을 지나지 않는다.

### Q. 왜 `@Transactional` 은 private 메서드에 안 먹나요?
A. 어노테이션 자체 문제가 아니라, 보통 프록시 기반 AOP 가 외부 호출 지점에만 개입하기 때문이다.

### Q. 왜 `@PostConstruct` 안에서 트랜잭션 기대하면 위험하죠?
A. lifecycle callback 시점과 프록시 적용 시점이 다르기 때문이다.

## 15-3. 메타데이터 → 처리기 → 결과
스프링 어노테이션의 일반 패턴은 이렇다.

1. 어노테이션이 메타데이터를 제공
2. 어떤 처리기(후처리기/selector/interceptor)가 그 메타데이터를 읽음
3. 빈 정의 수정, 의존성 주입, 프록시 생성, 콜백 실행 등 결과를 만듦

즉 어노테이션을 "마법" 으로 이해하면 안 되고,
**메타데이터와 처리기 간의 계약** 으로 이해해야 한다.

## 15-4. 면접용 한 줄
"스프링 어노테이션은 대부분 단독으로 동작하지 않고, 컨테이너가 특정 단계에서 해당 메타데이터를 해석해 실제 행동으로 연결합니다. 그래서 각 어노테이션이 BeanDefinition 단계인지, 주입 단계인지, 프록시 단계인지 구분해 설명할 수 있어야 합니다."

# 16. `@Component`, `@Service`, `@Repository`, `@Controller` 의 동작 방식

이 어노테이션들은 흔히 stereotype annotation 이라고 부른다.  
질문 자체는 쉬워 보이지만, 경력직 면접에서는 보통 이렇게 깊어진다.

- 전부 `@Component` 랑 같은가?
- `@Repository` 는 왜 특별한가?
- `@Controller` 는 단지 의미 표현인가, 실제 동작 차이가 있는가?
- meta-annotation 은 어떻게 적용되나?

---

## 16-1. 공통점
전부 기본적으로는 `@Component` 계열이다.
즉 Component Scan 에 의해 후보로 탐지될 수 있다.

```java
@Target(TYPE)
@Retention(RUNTIME)
@Component
public @interface Service {
}
```

대략 이런 구조라고 생각하면 된다.

## 16-2. 차이점
### `@Component`
가장 일반적인 stereotype. 의미적 구분 없음.

### `@Service`
도메인/비즈니스 서비스 계층 표현.
핵심 컨테이너 레벨에서 `@Component` 와 큰 기능 차이는 없다.
하지만 팀 규칙, AOP pointcut, 아키텍처 문맥상 의미가 있다.

### `@Repository`
핵심 차이가 있다.
영속성 예외를 스프링의 `DataAccessException` 계층으로 변환하는 대상이 될 수 있다.

즉 단순 라벨이 아니라 **예외 변환 인프라와 연결될 수 있는 stereotype** 이다.

### `@Controller`
MVC 인프라와 연결된다.
컨테이너 관점에서는 scan 후보이고,
웹 프레임워크 관점에서는 handler mapping / adapter 등과 함께 특별 취급된다.

## 16-3. `@Repository` 예외 변환
예:
JPA, JDBC, Hibernate, MyBatis 등은 각자 예외 계층이 다를 수 있다.
Spring 은 `@Repository` 빈에 대해 예외 변환 advisor/proxy 를 적용해
상위 공통 예외 계층으로 맞춰 주려 한다.

### 왜 좋은가
- 기술 종속성 감소
- 서비스 계층이 특정 persistence 기술 예외에 덜 묶임
- 일관된 retry / rollback 판단 가능

## 16-4. `@Controller` vs `@RestController`
`@RestController` 는 보통 `@Controller + @ResponseBody`.
핵심 컨테이너 스캔 관점에서는 둘 다 stereotype 후보다.
차이는 웹 응답 처리 방식에 있다.

## 16-5. 커스텀 stereotype
예:
```java
@Target(TYPE)
@Retention(RUNTIME)
@Service
public @interface UseCase {
}
```

이렇게 meta-annotation 으로 조직/도메인 의미를 부여할 수 있다.

장점:
- pointcut 정의 편리
- 계층 의미 강화
- 아키텍처 룰 표현 가능

주의:
- 너무 많이 만들면 팀이 이해하기 어려워짐
- "이 어노테이션이 정확히 무슨 인프라와 연결되는지" 문서화 필요

## 16-6. 면접용 답변
"`@Component`, `@Service`, `@Repository`, `@Controller` 는 모두 기본적으로 component scan 대상 stereotype 이지만, `@Repository` 는 예외 변환, `@Controller` 는 MVC 핸들러 인프라와 결합될 수 있다는 점에서 추가 의미가 있습니다. 그래서 컨테이너 레벨 공통점과 프레임워크 계층별 차이를 함께 설명하는 것이 정확합니다."

# 17. `@Autowired`, `@Value`, `@Resource`, `@PostConstruct`, `@PreDestroy` 내부 동작 정리

이 장은 면접에서 자주 함께 묶여 나온다.  
"그 어노테이션 누가 처리하죠?" 라는 질문이 핵심이다.

---

## 17-1. `@Autowired`
- 처리 주체: `AutowiredAnnotationBeanPostProcessor`
- 시점: 생성자 선택 / 필드 주입 / 메서드 주입
- 기본 전략: 타입 기반 후보 탐색
- 보조 전략: qualifier, primary, 이름, 컬렉션

### 자주 나오는 꼬리질문
- 필드 주입과 생성자 주입의 내부 시점 차이?
- `required=false` 와 Optional 차이?
- 컨테이너 밖 객체에는 왜 안 되나?

## 17-2. `@Value`
- 처리 주체: 보통 `AutowiredAnnotationBeanPostProcessor` 와 embedded value resolver 협업
- 시점: 주입 단계
- 의미: 프로퍼티 값 해석 + 타입 변환

### 포인트
`@Value("${x}")` 는 빈 탐색이 아니라 프로퍼티 해석이다.
그래서 Environment / PropertySources / ConversionService 와 함께 이해해야 한다.

## 17-3. `@Resource`
- 처리 주체: `CommonAnnotationBeanPostProcessor`
- 성향: 이름 기반 우선 문맥

스프링 네이티브라기보다 표준 계열 느낌.
레거시 코드에서 자주 만난다.

## 17-4. `@PostConstruct`
- 처리 주체: `CommonAnnotationBeanPostProcessor`
- 시점: 의존성 주입 후, 초기화 단계
- 용도: 빈이 준비된 직후 초기화 로직

주의:
- 너무 무거운 작업 금지
- 트랜잭션/비동기 프록시 기대 금지
- 외부 빈 접근을 복잡하게 하지 말 것

## 17-5. `@PreDestroy`
- 처리 주체: `CommonAnnotationBeanPostProcessor`
- 시점: singleton 종료 단계
- 용도: 자원 정리

## 17-6. 면접용 정리 문장
"`@Autowired` 는 `AutowiredAnnotationBeanPostProcessor` 가 처리하고, `@PostConstruct`, `@PreDestroy`, `@Resource` 는 `CommonAnnotationBeanPostProcessor` 가 처리합니다. 즉 이들 어노테이션은 자바 문법 기능이 아니라 컨테이너 후처리기가 라이프사이클 단계에서 해석해 주는 메타데이터입니다."

# 18. `@Transactional` 의 본질: 어노테이션이 아니라 AOP + 인터셉터 + 트랜잭션 매니저

트랜잭션을 이해하지 못하면 스프링을 깊게 안다고 보기 어렵다.  
경력직 면접에서는 거의 반드시 나온다.

중요한 문장부터 적자.

> `@Transactional` 은 단독으로는 아무 일도 하지 않는다.  
> 트랜잭션 인프라가 활성화되고, AOP 프록시가 붙고, `TransactionInterceptor` 가 호출을 가로채야 비로소 동작한다.

---

## 18-1. 활성화
보통 다음 중 하나로 활성화된다.

- `@EnableTransactionManagement`
- Spring Boot 자동설정

이 과정에서
- transaction advisor
- `TransactionInterceptor`
- 관련 attribute source
등 인프라 빈이 준비된다.

## 18-2. 어디에 붙이나
```java
@Service
@Transactional
public class OrderService {
}
```

또는 메서드에 붙인다.

### 실무 권장
- 보통 service 계층의 외부 진입 public 메서드
- 트랜잭션 경계가 명확한 곳
- repository 에 남발하지 않음
- controller 에 바로 두기보단 service 에 두는 편이 일반적

## 18-3. 동작 흐름
외부 클라이언트가 프록시 빈을 호출하면:

1. 프록시가 method invocation 을 가로챔
2. `TransactionAttributeSource` 가 이 메서드의 transaction metadata 조회
3. 적절한 `PlatformTransactionManager` 선택
4. 트랜잭션 시작
5. target 메서드 호출
6. 예외/정상 종료에 따라 commit or rollback
7. 리소스 정리

즉 실제 비즈니스 메서드 바깥에 트랜잭션 경계가 둘러싸이는 구조다.

## 18-4. JDK proxy vs CGLIB
스프링 AOP 는 프록시 기반이다.

- 인터페이스가 있으면 JDK dynamic proxy 사용 가능
- 없으면 CGLIB subclass proxy 사용 가능

Boot 에서는 보통 class-based(CGLIB) 프록시를 기본으로 두는 경우가 많다.

### 차이
- JDK proxy: 인터페이스 기반
- CGLIB: 클래스 상속 기반

### 제한
- final class / final method 제약
- private method 는 프록시로 가로채기 어려움
- self-invocation 문제는 둘 다 남는다

## 18-5. self-invocation 문제
가장 자주 나오는 질문 중 하나다.

```java
@Service
public class OrderService {

    @Transactional
    public void outer() {
        inner(); // self-invocation
    }

    @Transactional
    public void inner() {
    }
}
```

`outer()` 안에서 `inner()` 를 직접 부르면, 그 호출은 프록시를 거치지 않고 `this.inner()` 로 간다.
그래서 `inner()` 에 붙은 `@Transactional` 의 별도 의미가 적용되지 않을 수 있다.

### 해결
- 트랜잭션 경계를 외부 서비스로 분리
- 자기 자신 프록시를 주입하는 꼼수는 지양
- 아주 필요하면 `AopContext.currentProxy()` 같은 방식도 있지만 설계적으로 선호되지 않음

## 18-6. public / protected / package-private
전통적으로 프록시 기반 트랜잭션은 public 메서드에 두는 것이 가장 안전하다.
Spring 6 부터 class-based proxy 에서는 protected/package-private 도 일부 지원 문맥이 있지만,
면접에서는 **"외부 호출되는 public service 메서드에 둔다"** 가 가장 보수적이고 안전한 답이다.

## 18-7. private 메서드는?
보통 프록시로 가로채지 못한다.
따라서 private 메서드에 `@Transactional` 붙여도 기대한 동작이 안 나온다.

## 18-8. rollback rules
기본값:
- `RuntimeException`
- `Error`

에 대해 rollback.
checked exception 은 기본 rollback 대상이 아니다.

필요하면:
- `rollbackFor`
- `noRollbackFor`

를 사용한다.

### 면접 포인트
"checked exception 이면 무조건 commit 되나요?"  
→ 기본 규칙은 그렇지만 명시적으로 rollbackFor 지정 가능.

## 18-9. `readOnly = true`
많이 오해한다.

이건 DB 차원에서 무조건 쓰기 금지를 보장한다기보다,
- flush 최적화 힌트
- JDBC/ORM 에 대한 read-only 의도 전달
- 특정 드라이버/DB 에서 최적화 가능성

정도로 이해하는 게 정확하다.

즉 비즈니스 불변성 보장 수단으로 과신하면 안 된다.

## 18-10. `timeout`, `isolation`
DB / 트랜잭션 매니저 / 드라이버 영향과 함께 봐야 한다.
어노테이션 하나로 모든 환경에서 동일하게 강제된다고 생각하면 안 된다.

## 18-11. thread boundary
전통적인 imperative transaction 은 보통 thread-local 기반으로 묶인다.
따라서 새 스레드로 넘어가면 같은 트랜잭션 컨텍스트가 자동 전파되지 않는다.

예:
- `@Async`
- 직접 생성한 thread
- executor 분기

이 영역은 reactive transaction 과도 대비되는 포인트다.

## 18-12. 면접용 핵심 답변
"`@Transactional` 은 어노테이션 자체가 트랜잭션을 여는 게 아니라, 트랜잭션 인프라가 생성한 AOP 프록시가 `TransactionInterceptor` 를 통해 메서드 호출 전후에 트랜잭션을 시작하고 종료하는 구조입니다. 그래서 self-invocation, private 메서드, final 제약, 프록시 방식(JDK/CGLIB) 같은 포인트를 함께 이해해야 합니다."

## 18-13. 자주 나오는 꼬리질문
- 트랜잭션 매니저는 어떻게 찾나요?
- 왜 repository 가 아니라 service 에 두라고 하나요?
- `@Transactional` 과 `@Async` 같이 쓰면?
- 테스트의 `@Transactional` 은 운영과 동일한가?

# 19. 트랜잭션 전파(propagation)와 rollback-only, 그리고 실무 함정

이 장은 `@Transactional` 에서 가장 많이 꼬리질문이 이어지는 부분이다.  
경력직에서는 여기서 깊이가 갈린다.

---

## 19-1. propagation 의 질문
본질은 이거다.

> 이미 트랜잭션이 있는 상황에서, 새로운 메서드가 호출되면 어떻게 할 것인가?

---

## 19-2. `REQUIRED`
기본값.
- 기존 트랜잭션이 있으면 참여
- 없으면 새로 시작

가장 일반적이다.

### 논리 트랜잭션 vs 물리 트랜잭션
면접에서 이 표현이 나오면 좋다.
서로 다른 메서드에 `@Transactional(REQUIRED)` 가 붙어 있어도
같은 외부 호출 흐름 안에서 **하나의 물리 트랜잭션** 에 참여할 수 있다.

## 19-3. rollback-only 와 `UnexpectedRollbackException`
중요하다.

예:
- 외부 서비스 A 가 트랜잭션 시작
- 내부 서비스 B 가 같은 REQUIRED 트랜잭션에 참여
- B 에서 예외를 잡았지만 내부적으로 rollback-only 마킹
- A 는 정상적으로 끝났다고 생각하고 commit 시도
- 실제로는 rollback-only 상태라 `UnexpectedRollbackException`

즉 "예외를 잡았으니 괜찮겠지" 가 아니다.
같은 물리 트랜잭션을 공유하면 rollback-only 전파를 고려해야 한다.

## 19-4. `REQUIRES_NEW`
- 항상 새 물리 트랜잭션
- 기존 트랜잭션 suspend
- 별도 commit/rollback

### 장점
- 감사 로그, 아웃박스, 보상성 처리 등 독립 커밋 필요 시 유용

### 비용/위험
- 추가 커넥션 필요
- 커넥션 풀 압박
- 트랜잭션 중첩으로 복잡도 증가

실무에서 남발하면 오히려 throughput 을 무너뜨릴 수 있다.

## 19-5. `NESTED`
savepoint 기반 중첩 개념.
DB/트랜잭션 매니저 지원 여부와 함께 봐야 한다.
실무에서 REQUIRED, REQUIRES_NEW 만큼 자주 쓰이지는 않는다.

## 19-6. `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, `NEVER`
알고는 있어야 한다.

- `SUPPORTS`: 있으면 참여, 없으면 비트랜잭션
- `NOT_SUPPORTED`: 트랜잭션 없이 실행
- `MANDATORY`: 반드시 기존 트랜잭션 필요
- `NEVER`: 트랜잭션이 있으면 예외

경력직 면접에서는 잘 안 쓰더라도 "언제 필요한지" 정도는 말할 수 있어야 한다.

## 19-7. 실무 예시
### 주문 생성 + 감사 로그
- 주문 저장: REQUIRED
- 감사 로그: REQUIRES_NEW

왜?
주문 실패와 관계없이 감사 이벤트는 남겨야 할 수도 있기 때문이다.

### 하지만 주의
이렇게 분리하면
- 추가 커넥션 사용
- 일관성 모델 변경
- 장애 시 재처리 전략 필요
등이 생긴다.

## 19-8. `readOnly` 와 propagation 조합
외부 readOnly 트랜잭션 안에서 내부 write 가 일어나는 구조는 매우 혼란스럽다.
설계 단계에서 경계를 다시 보는 편이 낫다.

## 19-9. JPA flush 와 트랜잭션
트랜잭션 전파 질문은 JPA flush 와도 자주 엮인다.

- 같은 영속성 컨텍스트를 공유하는가?
- commit 시 flush 되는가?
- query 실행 전에 auto flush 되는가?
- readOnly 에서 flush 최적화가 달라질 수 있는가?

즉 단순히 어노테이션 값만 아는 게 아니라 ORM 동작과 연결해서 봐야 한다.

## 19-10. 면접용 답변
"`REQUIRED` 는 기존 트랜잭션에 참여하므로 논리적으로는 여러 메서드가 따로 보이더라도 물리적으로 하나의 트랜잭션을 공유할 수 있습니다. 이때 내부에서 rollback-only 가 설정되면 외부에서 정상적으로 끝난 것처럼 보여도 최종 commit 시 `UnexpectedRollbackException` 이 날 수 있습니다. `REQUIRES_NEW` 는 완전히 별도 물리 트랜잭션을 사용하므로 독립 커밋이 가능하지만, 추가 커넥션과 복잡도를 감수해야 합니다."

## 19-11. 커넥션 풀 관점의 꼬리질문
`REQUIRES_NEW` 는 "새 트랜잭션" 일 뿐 아니라 보통 **새 DB 리소스/새 커넥션** 을 필요로 한다.
고트래픽 시스템에서는 이게 바로 풀 고갈 문제로 연결된다.
이건 백엔드 경력직 면접에서 꽤 좋은 포인트다.

# 20. 프록시와 AOP: 스프링 부가기능이 붙는 방식

트랜잭션을 제대로 설명하려면 프록시와 AOP 를 이해해야 한다.  
하지만 AOP 를 "관점 지향" 정도로 추상적으로만 말하면 별 도움이 안 된다.  
면접에서는 보통 아래를 묻는다.

- 프록시가 뭐냐
- JDK / CGLIB 차이
- advisor / advice / pointcut
- 왜 self-invocation 이 안 되냐
- final / private 제약은 왜 생기냐
- 프록시가 여러 개면 어떻게 되냐

---

## 20-1. 프록시란
원본 객체 앞에 대리 객체를 두고 호출을 가로채는 구조다.

```text
client -> proxy -> target
```

프록시는 다음 일을 할 수 있다.
- before
- after
- around
- 예외 처리
- 호출 차단
- 로깅/보안/트랜잭션

## 20-2. 왜 프록시가 필요한가
비즈니스 코드 자체에 공통 기능을 흩뿌리면 유지보수가 어려워진다.

예:
```java
public void placeOrder() {
    tx.begin();
    try {
        // business
        tx.commit();
    } catch (Exception e) {
        tx.rollback();
        throw e;
    }
}
```

이걸 모든 메서드에 넣을 수는 없다.
그래서 호출 경계를 가로채는 프록시가 적합하다.

## 20-3. JDK dynamic proxy
- 인터페이스 기반
- target 이 구현한 인터페이스 타입으로 프록시 생성
- 클래스 상속 필요 없음

장점:
- 자바 표준
- 단순

단점:
- 인터페이스에 없는 메서드는 다루기 제한

## 20-4. CGLIB proxy
- 클래스 상속 기반
- target 클래스를 상속한 서브클래스 생성

장점:
- 인터페이스 없이도 가능
- 구체 클래스 기반 프록시 가능

단점:
- final class / final method 제약
- 상속 기반이므로 생성자/메서드 가시성 이슈 고려

## 20-5. advisor / advice / pointcut
### advice
언제 무엇을 할지의 실제 로직  
예: 트랜잭션 시작/커밋/롤백

### pointcut
어디에 적용할지 조건  
예: `execution(* com.example..service..*(..))`

### advisor
pointcut + advice 묶음

스프링 AOP 는 대체로 advisor 체인을 만들어 프록시에 연결한다.

## 20-6. multiple advisors
하나의 빈에 여러 부가기능이 붙을 수 있다.

예:
- transaction
- method validation
- security
- logging

이때 순서가 중요할 수 있다.
`@Order`, `Ordered`, advisor precedence 등을 고려한다.

## 20-7. self-invocation 재정리
프록시는 외부에서 target 으로 들어오는 호출을 가로챈다.
target 내부의 `this.foo()` 는 프록시를 거치지 않는다.

즉 self-invocation 문제는 transaction 뿐 아니라
- caching
- async
- security
등 프록시 기반 기능 전반의 공통 함정이다.

## 20-8. 왜 final/private 가 문제인가
프록시는 보통 메서드 호출을 override/implement 해서 가로챈다.

- final method: override 불가
- private method: 외부 다형적 override 대상 아님

그래서 프록시 기반 기능이 적용되기 어렵다.

## 20-9. 면접용 답변
"스프링 AOP 는 주로 프록시 기반으로 동작하며, 인터페이스가 있으면 JDK 동적 프록시, 없으면 CGLIB 서브클래싱을 사용할 수 있습니다. advisor 는 pointcut 과 advice 의 조합이고, `@Transactional` 도 결국 이 구조 위에 올라갑니다. self-invocation 과 final/private 제약은 이 프록시 모델에서 자연스럽게 발생하는 한계입니다."

# 21. Spring Boot 자동설정(auto-configuration) 내부 구조

Boot 를 쓰면서도 자동설정을 설명하지 못하면 경력직으로는 아쉽다.  
특히 "왜 starter 만 추가했는데 빈이 생기는가" 를 말할 수 있어야 한다.

---

## 21-1. `@SpringBootApplication`
이 어노테이션은 사실 여러 개의 조합이다.

주요 구성:
- `@SpringBootConfiguration`
- `@EnableAutoConfiguration`
- `@ComponentScan`

즉
- 내 애플리케이션 스캔
- 자동설정 활성화
를 한 번에 묶는다.

## 21-2. 자동설정 후보는 어디서 오나
현재 Boot 계열에서는 자동설정 후보 목록을 보통
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
에서 읽는다.

예전 `spring.factories` 시절과 구분해서 설명하면 더 좋다.

## 21-3. 왜 component scan 으로 안 찾나
자동설정은 일반 애플리케이션 컴포넌트와 다르다.

- 라이브러리 내부 인프라 설정
- 조건부 활성화 필요
- 순서 제어 필요
- 사용자 정의 빈보다 뒤로 물러나야 함

그래서 단순 scan 보다 **명시적 imports + 조건부 평가** 모델이 적합하다.

## 21-4. `@Conditional...`
자동설정의 핵심이다.

대표 예:
- `@ConditionalOnClass`
- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnWebApplication`

즉 classpath, 프로퍼티, 기존 빈 존재 여부에 따라 자동설정이 켜지거나 꺼진다.

## 21-5. back-off
실무에서 아주 중요하다.

예:
- Boot 가 기본 `ObjectMapper` 빈을 만들려고 함
- 그런데 사용자가 같은 타입/이름의 빈을 직접 정의
- 자동설정은 보통 `@ConditionalOnMissingBean` 으로 **뒤로 물러남(back off)**

이게 Boot 의 확장 친화성 핵심이다.

## 21-6. ordering
자동설정끼리도 순서가 필요하다.

- 어떤 설정은 DataSource 뒤에 와야 하고
- 어떤 설정은 WebMVC 전에/후에 와야 한다

그래서 `@AutoConfiguration(after=...)` 같은 ordering 개념이 있다.

## 21-7. debug 를 켜면 좋은 이유
`--debug` 또는 condition evaluation report 를 보면
- 어떤 자동설정이 활성화됐는지
- 왜 활성화/비활성화됐는지
를 확인할 수 있다.

이건 Boot 디버깅의 핵심이다.

## 21-8. 사용자 관점 정리
starter 의존성 추가  
→ 관련 auto-config 후보 로드  
→ 조건 평가  
→ BeanDefinition 등록  
→ refresh 과정에서 실제 빈 생성

즉 자동설정도 결국 BeanDefinition 등록 문제로 환원된다.

## 21-9. 면접용 답변
"Spring Boot 자동설정은 `@EnableAutoConfiguration` 을 통해 활성화되고, 현재는 `AutoConfiguration.imports` 에 선언된 자동설정 클래스 후보를 읽어 조건부로 BeanDefinition 을 등록하는 방식입니다. 일반 component scan 이 아니라 명시적 후보 목록과 `@Conditional...` 조합을 쓰며, 사용자 정의 빈이 있으면 `@ConditionalOnMissingBean` 으로 back-off 하는 것이 핵심입니다."

## 21-10. 꼬리질문
- 내 빈과 자동설정 빈이 충돌하면?
- 자동설정을 끄려면?
- 직접 starter 를 만든다면 어떤 구조로 할까?
- AOT/native 환경에서 scan 대신 metadata 최적화가 왜 중요할까?

# 22. 스프링에서 자주 쓰는 면접 답변 템플릿

이 장은 실제 면접에서 바로 말할 수 있게 짧은 템플릿 형태로 정리한다.

---

## 22-1. "빈이 무엇인가요?"
"스프링 빈은 단순한 객체가 아니라 컨테이너에 등록되어 생성 방식, 의존관계, 스코프, 생명주기, 후처리, 프록시 적용 여부까지 관리되는 객체입니다."

## 22-2. "빈은 어떻게 등록되나요?"
"`@Component` 계열과 component scan, `@Bean`, `@Import`, 자동설정, 프로그램적 등록 등 여러 경로가 있지만 결국 내부적으로는 BeanDefinitionRegistry 에 BeanDefinition 이 등록된다는 점이 공통입니다."

## 22-3. "BeanFactory 와 ApplicationContext 차이?"
"BeanFactory 는 핵심 IoC 기능이고, ApplicationContext 는 그 위에 이벤트, 메시지, 환경, 리소스, 자동 후처리 등록 같은 엔터프라이즈 기능을 추가한 상위 컨테이너입니다."

## 22-4. "BeanPostProcessor 는 뭔가요?"
"빈 인스턴스 생성 전후에 개입하는 확장 포인트로, `@Autowired`, `@PostConstruct`, AOP 프록시 같은 많은 기능이 이 위에서 동작합니다. 반환 객체를 바꿀 수 있기 때문에 프록시 wrapping 도 가능합니다."

## 22-5. "BeanFactoryPostProcessor 와 차이?"
"BeanFactoryPostProcessor 는 인스턴스가 아니라 BeanDefinition 을 생성 전에 수정하고, BeanPostProcessor 는 실제 빈 인스턴스를 생성 과정에서 가공합니다."

## 22-6. "Component Scan 은 어떻게 동작하나요?"
"base package 아래 classpath 를 스캔해서 `@Component` 와 그 meta-annotation 을 가진 후보를 찾고, 보통 ASM 기반 metadata reader 로 클래스 로딩 없이 메타정보를 읽은 뒤 BeanDefinition 을 등록합니다."

## 22-7. "`@Autowired` 는 누가 처리하나요?"
"`AutowiredAnnotationBeanPostProcessor` 가 처리합니다. 생성자 선택, 필드/메서드 주입, 타입 기반 후보 해석을 담당합니다."

## 22-8. "빈 생명주기를 설명해 보세요."
"BeanDefinition 등록 후 인스턴스 생성, 의존성 주입, aware callback, before-init post-process, `@PostConstruct`/`afterPropertiesSet`/initMethod 초기화, after-init post-process, 사용, 종료 시 `@PreDestroy`/destroyMethod 순서로 이해하면 됩니다."

## 22-9. "`@Transactional` 은 어떻게 동작하나요?"
"트랜잭션 인프라가 활성화되면 AOP 프록시가 생성되고, 프록시가 `TransactionInterceptor` 를 통해 메서드 호출 전후에 트랜잭션을 시작/종료합니다. 그래서 self-invocation 이나 private/final 제약 같은 프록시 한계가 존재합니다."

## 22-10. "왜 service 계층에 트랜잭션을 두나요?"
"트랜잭션은 보통 유스케이스 단위의 비즈니스 경계와 맞물리기 때문입니다. repository 레벨에 흩뿌리면 상위 흐름에서 원자성 경계를 설명하기 어렵고, 여러 repository 호출을 하나의 트랜잭션으로 묶기 힘들어집니다."

## 22-11. "순환참조는 어떻게 해결되나요?"
"생성자 순환참조는 해결할 수 없고 보통 예외가 납니다. 과거에는 setter/field 기반 singleton 순환참조를 3단계 캐시로 일부 풀 수 있었지만, 설계상 좋지 않고 Boot 에서는 기본적으로 금지 방향입니다."

## 22-12. "Boot 자동설정은 뭐죠?"
"starter 의존성과 classpath, 프로퍼티, 기존 빈 존재 여부 등을 조건으로 자동설정 클래스가 BeanDefinition 을 등록하는 메커니즘입니다. 사용자 정의 빈이 있으면 보통 back-off 하도록 설계됩니다."

# 23. 실무 디버깅 포인트: 어디를 보면 내부가 보이는가

이 장은 실제 운영/개발 시 스프링 동작을 추적하는 방법을 정리한다.

---

## 23-1. 로그 레벨
### 빈 생성/주입 관련
- `org.springframework.beans`
- `org.springframework.context`
- `org.springframework.core`

### 자동설정
- `org.springframework.boot.autoconfigure`

### 트랜잭션
- `org.springframework.transaction`
- `org.springframework.orm.jpa`
- `org.hibernate.SQL`, `org.hibernate.type.descriptor.sql`

### AOP
- `org.springframework.aop`

## 23-2. 조건 평가 리포트
Boot 에서는 `--debug` 나 condition evaluation report 로 자동설정 활성/비활성 원인을 볼 수 있다.
"왜 이 빈이 안 생기지?" 를 잡을 때 매우 강력하다.

## 23-3. Actuator
- `/actuator/beans`
- `/actuator/conditions`
- `/actuator/configprops`

실무에서 컨테이너 상태를 확인하는 데 유용하다.
보안상 운영 공개는 주의.

## 23-4. 디버거 breakpoint 포인트
깊이 파보고 싶다면 아래 클래스에 브레이크포인트를 걸면 좋다.

- `AbstractApplicationContext.refresh`
- `DefaultListableBeanFactory.preInstantiateSingletons`
- `AbstractAutowireCapableBeanFactory.doCreateBean`
- `AutowiredAnnotationBeanPostProcessor`
- `ConfigurationClassPostProcessor`
- `AbstractAutoProxyCreator`
- `TransactionInterceptor`

## 23-5. 프록시 여부 확인
```java
AopUtils.isAopProxy(bean)
AopUtils.isJdkDynamicProxy(bean)
AopUtils.isCglibProxy(bean)
```

혹은 `bean.getClass()` 를 보면 클래스명이 이상하게 보일 수 있다.

## 23-6. BeanDefinition 확인
```java
ConfigurableListableBeanFactory bf = ((ConfigurableApplicationContext) context).getBeanFactory();
for (String name : bf.getBeanDefinitionNames()) {
    BeanDefinition bd = bf.getBeanDefinition(name);
    System.out.println(name + " -> " + bd.getBeanClassName());
}
```

이렇게 해보면 등록된 정의를 직접 볼 수 있다.

## 23-7. 트랜잭션 활성 여부 확인
```java
boolean active = TransactionSynchronizationManager.isActualTransactionActive();
```

디버깅용으로 현재 스레드에 트랜잭션이 묶였는지 확인 가능하다.

## 23-8. 순환참조 의심 시
- 생성자 주입으로 전환해 문제를 빨리 드러내기
- 의존성 그래프 그리기
- `@Lazy` 로 일시 우회했는지 확인
- A와 B 사이 공통 책임 추출 가능성 보기

## 23-9. 스캔 문제 의심 시
- base package 확인
- 테스트 슬라이스가 일부만 로드하는지 확인
- `@Import` 누락 확인
- config class 위치 확인

## 23-10. 부팅 느릴 때
- component scan 범위 축소
- 불필요한 starter 제거
- heavy `@PostConstruct` 제거
- 지나친 reflection / custom post-processor 점검
- lazy init 적용 범위 검토

# 24. 흔한 함정과 오해 30개

이 장은 실제 면접과 실무에서 자주 틀리는 포인트를 모았다.

---

## 24-1.
**오해:** `@Service` 는 `@Component` 와 완전히 동일하니 아무 의미 없다.  
**정리:** 스캔 후보라는 점은 유사하지만, 계층 의미와 AOP pointcut, 팀 규칙 측면에서 충분히 중요하다.

## 24-2.
**오해:** `@Repository` 는 그냥 이름만 다르다.  
**정리:** 예외 변환 인프라와 연결될 수 있다.

## 24-3.
**오해:** `@Autowired` 는 자바가 자동으로 넣어준다.  
**정리:** BeanPostProcessor 가 해석한다.

## 24-4.
**오해:** `@PostConstruct` 에서 무엇이든 해도 된다.  
**정리:** 무거운 외부 호출, 프록시 의존, 복잡한 교차 빈 호출은 위험하다.

## 24-5.
**오해:** `@Transactional` 붙이면 무조건 트랜잭션이 열린다.  
**정리:** 프록시를 거쳐 호출될 때만 기대한 동작이 나온다.

## 24-6.
**오해:** private 메서드에도 `@Transactional` 붙으면 된다.  
**정리:** 프록시 기반 AOP 한계상 기대한 동작이 안 된다.

## 24-7.
**오해:** self-invocation 에서도 트랜잭션이 먹는다.  
**정리:** 프록시를 우회한다.

## 24-8.
**오해:** `readOnly = true` 면 DB 쓰기가 물리적으로 완전 차단된다.  
**정리:** 힌트/최적화 의미가 크고 구현체별 차이가 있다.

## 24-9.
**오해:** prototype 을 singleton 에 주입하면 매번 새 인스턴스다.  
**정리:** 기본 주입은 1회 해석이다.

## 24-10.
**오해:** BeanFactoryPostProcessor 에서 bean 을 조회해도 괜찮다.  
**정리:** 너무 이른 인스턴스 생성으로 컨테이너 일관성을 깨뜨릴 수 있다.

## 24-11.
**오해:** `@Configuration` 이면 항상 프록시된다.  
**정리:** `proxyBeanMethods=false` 면 CGLIB enhancement 를 하지 않는다.

## 24-12.
**오해:** `@Bean` 메서드끼리 직접 호출해도 항상 singleton 이다.  
**정리:** full config mode 에서만 그렇다.

## 24-13.
**오해:** Boot 자동설정은 component scan 으로 찾는다.  
**정리:** 명시적 imports + 조건부 평가다.

## 24-14.
**오해:** 빈 이름은 별 의미 없다.  
**정리:** lookup key 이고, 충돌/qualifier/예외 상황에서 중요하다.

## 24-15.
**오해:** ApplicationContext 는 BeanFactory 의 완전 다른 구현이다.  
**정리:** BeanFactory 기능을 확장한 상위 컨테이너 개념이다.

## 24-16.
**오해:** 생성자 주입이 항상 순환참조를 더 어렵게 만들 뿐이다.  
**정리:** 오히려 문제를 빨리 드러내서 설계를 개선하게 해준다.

## 24-17.
**오해:** 스프링이 순환참조를 해결해 주니 괜찮다.  
**정리:** 해결 가능성과 설계 적절성은 다르다.

## 24-18.
**오해:** BeanPostProcessor 는 어노테이션 처리기 정도다.  
**정리:** 프록시 wrapping 까지 포함하는 핵심 확장 포인트다.

## 24-19.
**오해:** `@Controller` 는 컨테이너와 무관하게 MVC 만 의미한다.  
**정리:** stereotype 이므로 scan 후보이기도 하다.

## 24-20.
**오해:** 하나의 타입 빈이 여러 개면 스프링이 이름 보고 알아서 잘 고른다.  
**정리:** 명확한 규칙 없이 모호하면 예외다.

## 24-21.
**오해:** `ObjectProvider` 는 단지 optional 용이다.  
**정리:** 지연 조회, 반복 조회, scope mismatch 해결에도 유용하다.

## 24-22.
**오해:** `FactoryBean` 과 `BeanFactory` 는 비슷한 개념이다.  
**정리:** 하나는 객체 생성용 특별 빈, 다른 하나는 컨테이너 핵심 인터페이스다.

## 24-23.
**오해:** `@Value` 는 빈 주입과 동일하다.  
**정리:** property resolution + conversion 의 성격이 강하다.

## 24-24.
**오해:** 트랜잭션 전파는 어노테이션 옵션 몇 개 암기하면 끝이다.  
**정리:** rollback-only, 커넥션 풀, ORM flush 와 함께 봐야 한다.

## 24-25.
**오해:** `REQUIRES_NEW` 는 무조건 더 안전하다.  
**정리:** 커넥션 추가 사용과 일관성 모델 변화 비용이 있다.

## 24-26.
**오해:** 프록시는 디버깅에 영향이 없다.  
**정리:** 실제 타입, stack trace, equals/hashCode, serialization 등에 영향을 줄 수 있다.

## 24-27.
**오해:** 자동설정 빈을 override 하는 건 나쁜 냄새다.  
**정리:** 의도적으로 사용자 빈이 우선하도록 설계된 경우가 많다.

## 24-28.
**오해:** 모든 후처리기는 동일한 순서로 그냥 실행된다.  
**정리:** `PriorityOrdered`, `Ordered` 등 정렬 규칙이 있다.

## 24-29.
**오해:** prototype 도 destroy callback 을 컨테이너가 자동 보장한다.  
**정리:** 생성까지만 관여하는 경우가 많다.

## 24-30.
**오해:** 스프링을 깊게 안다는 것은 어노테이션 종류를 많이 아는 것이다.  
**정리:** 진짜 깊이는 "이 어노테이션이 어느 단계에서 어떤 처리기에 의해 동작하는지" 를 설명하는 데 있다.

# 25. 자주 나오는 꼬리질문 세트 1: 빈과 생명주기

아래는 실제 면접처럼 꼬리를 무는 방식으로 구성했다.  
한 질문에 답하면 다음 질문이 자연스럽게 이어진다고 생각하고 연습하면 좋다.

---

## 25-1.
**Q. 스프링 빈이 뭔가요?**  
A. 스프링 컨테이너에 등록되어 생성, 주입, 스코프, 생명주기, 후처리, 프록시 적용을 관리받는 객체입니다.

**꼬리 1. 일반 객체와 차이는?**  
A. 일반 객체는 내가 만들고 내가 생명주기를 관리하지만, 빈은 컨테이너가 정의와 인스턴스를 관리합니다.

**꼬리 2. 빈은 어디 저장되나요?**  
A. 내부적으로는 BeanDefinition 과 singleton registry 같은 구조를 통해 관리됩니다.

**꼬리 3. BeanDefinition 이 필요한 이유는?**  
A. 실제 인스턴스를 만들기 전에 메타데이터 단계에서 후처리하고 조합하기 위해서입니다.

---

## 25-2.
**Q. 빈은 어떻게 등록되나요?**  
A. `@Component` scan, `@Bean`, `@Import`, Boot 자동설정, 프로그램적 등록 등 다양한 경로가 있습니다.

**꼬리 1. 결국 내부 공통점은?**  
A. 모두 BeanDefinitionRegistry 에 BeanDefinition 을 등록한다는 점입니다.

**꼬리 2. `@Component` 와 `@Bean` 차이는?**  
A. 전자는 클래스 스캔 기반 stereotype, 후자는 메서드 기반 명시적 생성입니다.

**꼬리 3. `@Bean` 을 왜 선호할 때가 있죠?**  
A. 외부 라이브러리 객체 등록, 생성 로직 명시화, 조건/파라미터 표현이 쉬울 때입니다.

---

## 25-3.
**Q. 빈 생명주기를 설명해 보세요.**  
A. BeanDefinition 등록 후 인스턴스 생성, 의존성 주입, aware callback, post-process before init, 초기화 콜백, post-process after init, 사용, 종료 시 destroy callback 순서입니다.

**꼬리 1. `@Autowired` 는 언제 되나요?**  
A. 생성 직후, 초기화 전에 주입됩니다. 생성자 주입은 인스턴스화 과정에 포함되고, 필드/세터 주입은 그 직후입니다.

**꼬리 2. `@PostConstruct` 와 `afterPropertiesSet()` 순서는?**  
A. 일반적으로 `@PostConstruct` 후에 `InitializingBean.afterPropertiesSet()`, 그 다음 custom initMethod 순서로 이해합니다.

**꼬리 3. 프록시는 언제 씌워지나요?**  
A. 보통 BeanPostProcessor 의 after-initialization 단계에서 최종 빈을 래핑합니다.

---

## 25-4.
**Q. prototype 빈은 destroy 가 안 되나요?**  
A. 컨테이너가 생성까지만 관여하고 이후 전체 생명주기를 추적하지 않기 때문입니다.

**꼬리 1. 그럼 자원 정리는 누가 하나요?**  
A. 해당 객체를 사용한 코드나 별도 관리 계층이 해야 합니다.

**꼬리 2. prototype 을 singleton 에 넣으면 매번 새로 생성되나요?**  
A. 아니고 singleton 생성 시점 1회 주입입니다.

**꼬리 3. 그럼 매번 새 걸 받으려면?**  
A. `ObjectProvider`, `Provider`, `@Lookup` 같은 지연/반복 조회가 필요합니다.

# 26. 자주 나오는 꼬리질문 세트 2: 후처리기와 설정 클래스

---

## 26-1.
**Q. BeanPostProcessor 가 뭔가요?**  
A. 빈 인스턴스 생성 전후에 개입해 공통 처리를 하거나 프록시로 감쌀 수 있는 확장 포인트입니다.

**꼬리 1. BeanFactoryPostProcessor 와 차이는?**  
A. BeanFactoryPostProcessor 는 인스턴스가 아니라 BeanDefinition 을 수정합니다.

**꼬리 2. 왜 둘을 분리했나요?**  
A. 메타데이터 단계와 인스턴스 단계의 책임을 분리해 컨테이너 초기화 순서를 안전하게 유지하기 위해서입니다.

**꼬리 3. 대표적인 BeanPostProcessor 는?**  
A. `AutowiredAnnotationBeanPostProcessor`, `CommonAnnotationBeanPostProcessor`, AutoProxyCreator 등이 있습니다.

---

## 26-2.
**Q. `@Configuration` 은 누가 처리하나요?**  
A. 핵심적으로 `ConfigurationClassPostProcessor` 가 처리합니다.

**꼬리 1. 이건 BPP 인가요 BFPP 인가요?**  
A. BeanDefinitionRegistryPostProcessor / BeanFactoryPostProcessor 축에 가깝습니다.

**꼬리 2. 왜 이른 단계여야 하죠?**  
A. `@Bean`, `@ComponentScan`, `@Import` 로 추가 BeanDefinition 을 만들어야 하기 때문입니다.

**꼬리 3. `@Configuration` 과 `@Component` + `@Bean` 차이는?**  
A. full config mode 에서 CGLIB enhancement 로 inter-bean call 의 singleton semantics 를 보장한다는 차이가 큽니다.

---

## 26-3.
**Q. `proxyBeanMethods = false` 는 왜 쓰나요?**  
A. 설정 클래스 프록시 비용을 줄이기 위해서입니다.

**꼬리 1. 그럼 뭐가 깨질 수 있죠?**  
A. `@Bean` 메서드 간 직접 호출 시 컨테이너 singleton 보장을 기대하면 안 됩니다.

**꼬리 2. 해결 패턴은?**  
A. `@Bean` 메서드 파라미터로 의존성을 받는 방식입니다.

**꼬리 3. 어디에서 특히 많이 쓰이나요?**  
A. 자동설정 클래스처럼 메서드 직접 호출보다 파라미터 주입 패턴을 쓰는 곳에서 자주 보입니다.

# 27. 자주 나오는 꼬리질문 세트 3: 트랜잭션과 프록시

---

## 27-1.
**Q. `@Transactional` 은 어떻게 동작하나요?**  
A. 트랜잭션 인프라가 만든 AOP 프록시가 `TransactionInterceptor` 를 통해 메서드 호출 전후에 트랜잭션을 시작/종료합니다.

**꼬리 1. self-invocation 은 왜 안 되죠?**  
A. target 내부의 `this` 호출은 프록시를 우회하기 때문입니다.

**꼬리 2. private 메서드에 붙이면 왜 의미 없죠?**  
A. 프록시가 외부 호출 경계를 가로채는 구조라 private 메서드는 적용 대상이 되기 어렵기 때문입니다.

**꼬리 3. 그럼 어디에 두는 게 가장 좋나요?**  
A. 외부에서 호출되는 public service 메서드에 두는 것이 가장 예측 가능하고 안전합니다.

---

## 27-2.
**Q. JDK 프록시와 CGLIB 차이는?**  
A. JDK 는 인터페이스 기반, CGLIB 는 클래스 상속 기반입니다.

**꼬리 1. final class 는 왜 문제죠?**  
A. CGLIB 가 상속해서 프록시를 만들어야 하는데 final 이면 상속이 안 되기 때문입니다.

**꼬리 2. 인터페이스가 있으면 무조건 JDK 프록시인가요?**  
A. 프레임워크/설정에 따라 class-based proxy 를 강제할 수도 있습니다. Boot 에서는 CGLIB 기본 설정이 자주 보입니다.

**꼬리 3. 성능 차이는 큰가요?**  
A. 보통 실무 병목 포인트는 아니고, 기능 제약과 타입/디버깅 차이를 더 중요하게 봅니다.

---

## 27-3.
**Q. propagation REQUIRED 와 REQUIRES_NEW 차이는?**  
A. REQUIRED 는 기존 트랜잭션에 참여하고, REQUIRES_NEW 는 항상 별도 물리 트랜잭션을 시작합니다.

**꼬리 1. REQUIRED 에서 예외를 잡았는데 왜 나중에 `UnexpectedRollbackException` 이 나죠?**  
A. 내부 참여자가 rollback-only 를 표시했기 때문입니다.

**꼬리 2. REQUIRES_NEW 는 왜 위험할 수 있죠?**  
A. 별도 커넥션과 리소스를 사용해 커넥션 풀 압박을 줄 수 있기 때문입니다.

**꼬리 3. 그럼 언제 쓰나요?**  
A. 감사 로그, 아웃박스, 별도 보상 처리처럼 외부 트랜잭션과 독립 커밋이 필요한 경우에 제한적으로 씁니다.

# 28. 자주 나오는 꼬리질문 세트 4: Component Scan 과 자동설정

---

## 28-1.
**Q. Component Scan 은 어떻게 동작하나요?**  
A. base package 아래 classpath 를 스캔해 `@Component` 와 meta-annotation 을 가진 클래스를 찾아 BeanDefinition 으로 등록합니다.

**꼬리 1. 클래스를 다 로딩하나요?**  
A. 보통 ASM 기반 metadata reading 으로 어노테이션 메타정보를 먼저 읽어 비용을 줄입니다.

**꼬리 2. `@Service` 도 scan 되나요?**  
A. 네, `@Component` 의 specialization 이므로 기본 필터 대상입니다.

**꼬리 3. 커스텀 stereotype 도 가능한가요?**  
A. 가능합니다. `@Component` 를 meta-annotation 으로 붙이면 됩니다.

---

## 28-2.
**Q. Boot 자동설정은 scan 과 어떻게 다른가요?**  
A. 자동설정은 일반 component scan 이 아니라 명시적 auto-configuration 후보 목록과 `@Conditional...` 조합으로 BeanDefinition 을 등록합니다.

**꼬리 1. 후보 목록은 어디 있죠?**  
A. 현재는 `AutoConfiguration.imports` 파일에서 읽는 방식이 대표적입니다.

**꼬리 2. 왜 scan 안 쓰나요?**  
A. 라이브러리 인프라 설정은 조건부 활성화와 순서 제어, 사용자 빈 back-off 가 중요하기 때문입니다.

**꼬리 3. 내 빈과 충돌하면?**  
A. 보통 `@ConditionalOnMissingBean` 을 통해 사용자 빈이 우선하도록 설계됩니다.

# 29. 코드로 확인하는 실험 모음

실제로 돌려보면 이해가 훨씬 빨라진다.  
아래 실험은 면접 대비뿐 아니라 실무 감각을 만드는 데도 좋다.

---

## 29-1. BeanPostProcessor 로 프록시 확인하기
```java
@Component
public class SpyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.contains("Service")) {
            System.out.println(beanName + " => " + bean.getClass().getName());
        }
        return bean;
    }
}
```

### 관찰 포인트
- 어떤 빈이 원본이고 어떤 빈이 프록시인지
- `@Transactional` 붙은 서비스가 어떤 클래스명으로 보이는지

## 29-2. lifecycle 로그 찍기
```java
@Component
public class LifeCycleBean implements InitializingBean, DisposableBean {

    public LifeCycleBean() {
        System.out.println("1. constructor");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("2. @PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("3. afterPropertiesSet");
    }

    public void init() {
        System.out.println("4. custom init");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("5. @PreDestroy");
    }

    @Override
    public void destroy() {
        System.out.println("6. destroy");
    }
}
```

`@Bean(initMethod = "init")` 와 함께 연결해서 실제 순서를 확인해 본다.

## 29-3. self-invocation 확인
```java
@Service
public class TxService {

    @Transactional
    public void outer() {
        System.out.println("outer tx = " + TransactionSynchronizationManager.isActualTransactionActive());
        inner();
    }

    @Transactional
    public void inner() {
        System.out.println("inner tx = " + TransactionSynchronizationManager.isActualTransactionActive());
    }
}
```

### 실험 포인트
- 외부에서 `outer()` 호출 시 `inner()` 의 별도 tx semantics 가 적용되는지
- 다른 빈으로 분리하면 어떻게 달라지는지

## 29-4. prototype 실험
```java
@Component
@Scope("prototype")
class Proto {
    private final UUID id = UUID.randomUUID();
    public UUID getId() { return id; }
}

@Component
class SingletonUser {
    private final ObjectProvider<Proto> provider;
    public SingletonUser(ObjectProvider<Proto> provider) {
        this.provider = provider;
    }
    public void print() {
        System.out.println(provider.getObject().getId());
    }
}
```

### 실험 포인트
- 직접 주입 vs provider 주입 차이

## 29-5. `proxyBeanMethods` 실험
```java
@Configuration(proxyBeanMethods = false)
class LiteLikeConfig {
    @Bean
    public B b() { return new B(); }

    @Bean
    public A a() { return new A(b()); } // 직접 호출 주의
}
```

### 실험 포인트
- `A` 안의 `B` 와 컨테이너의 `B` 가 같은지 비교

## 29-6. 조건부 빈 등록 실험
```java
@Configuration
class ConditionConfig {

    @Bean
    @ConditionalOnMissingBean
    public MyService myService() {
        return new MyService("auto");
    }
}
```

별도 사용자 정의 `MyService` 빈을 추가했을 때 back-off 되는지 확인한다.

## 29-7. circular dependency 실험
생성자 기반 A/B 순환 의존을 만들어 예외를 확인하고,
setter 기반으로 바꾼 뒤 Boot 설정에 따라 어떤 차이가 나는지 비교해 본다.

# 30. 경력직 면접용 deep dive 답변 세트

이 장은 단순 정의형이 아니라 **설계 판단 + 내부 동작 + 실무 trade-off** 까지 포함한 답변 예시다.

---

## 30-1. "왜 생성자 주입을 더 선호하시나요?"
"생성자 주입은 필수 의존성을 객체 생성 시점에 강제하므로 빈이 불완전한 상태로 존재하지 않게 합니다. 또한 필드를 final 로 둘 수 있어 불변성이 좋아지고, 테스트도 수월합니다. 내부적으로도 생성자 주입은 빈 생성 과정에서 의존성이 해결되어야 하므로 순환참조 같은 구조적 문제를 더 빨리 드러냅니다. 반면 필드 주입은 간편하지만 숨은 의존성이 생기고 테스트/유지보수성이 떨어집니다."

## 30-2. "트랜잭션은 왜 service 계층에 두나요?"
"트랜잭션 경계는 보통 하나의 유스케이스 또는 비즈니스 작업 단위와 대응시키는 게 자연스럽습니다. repository 레벨에 두면 여러 repository 호출을 하나의 원자 작업으로 묶기 어려워지고, 트랜잭션 전파 의미도 흐려집니다. 또한 `@Transactional` 은 프록시 기반이라 외부 진입 public 메서드에 두는 것이 self-invocation 문제를 피하기 쉽습니다."

## 30-3. "Component Scan 보다 `@Bean` 을 선호하는 경우가 있나요?"
"네. 외부 라이브러리 객체를 등록해야 하거나 생성 과정이 복잡해 명시적으로 드러내고 싶을 때, 혹은 조건부 등록/설정 파라미터 조합이 중요한 경우 `@Bean` 이 더 적합합니다. 반대로 도메인 내부 컴포넌트는 stereotype + scan 이 간결합니다. 결국 명시성 대 편의성의 trade-off 로 봅니다."

## 30-4. "BeanPostProcessor 를 커스텀으로 만든 경험이 있나요?"
"애플리케이션 코드에서는 남용하지 않지만, 공통 인프라를 붙이거나 특정 annotation 기반 정책을 강제할 때 사용할 수 있습니다. 다만 모든 빈 생성에 개입할 수 있으므로 reflection 비용, 순서, 프록시 중첩 문제를 신중히 봐야 하고, 프레임워크 확장에 가깝다는 인식이 필요합니다."

## 30-5. "Spring Boot 자동설정을 직접 만든다면 어떻게 하실 건가요?"
"사용자 입장에서는 starter 하나로 켜지되, 실제로는 auto-configuration 클래스를 분리하고 `@ConditionalOnClass`, `@ConditionalOnProperty`, `@ConditionalOnMissingBean` 으로 안전하게 가드할 겁니다. 사용자 커스터마이징을 위해 properties class 를 제공하고, 스캔이 아니라 명시적 auto-configuration imports 로 등록해 순서와 활성 조건을 제어하겠습니다."

## 30-6. "순환참조를 해결한 경험이 있나요?"
"가능하면 스프링의 early reference 메커니즘에 기대지 않고 구조를 바꾸는 방향을 우선합니다. 보통 서비스 둘이 서로를 호출하는 상황은 책임 분리가 덜 되었거나 이벤트/도메인 서비스 추출로 풀 수 있는 경우가 많았습니다. 정말 불가피한 경우에도 `@Lazy` 나 provider 는 임시 우회로만 보고, 최종적으로는 의존 방향을 정리하는 쪽이 운영 안정성에 유리하다고 봅니다."

## 30-7. "readOnly 트랜잭션은 어떻게 이해하나요?"
"`readOnly=true` 는 무조건 쓰기 금지 스위치라기보다, ORM/JDBC/DB 에 읽기 의도와 최적화 힌트를 전달하는 성격이 큽니다. JPA/Hibernate 에서는 flush 최적화나 dirty checking 감소 맥락과 연결해서 보기도 하지만, 구현체와 환경 차이가 있으므로 불변성 보장 수단으로 과신하진 않습니다."

# 31. 실제 면접에서 답변을 망치기 쉬운 표현 교정

---

## 31-1. 나쁜 답변
"`@Transactional` 이 붙으면 그 메서드가 트랜잭션을 시작합니다."

### 더 좋은 답변
"`@Transactional` 은 트랜잭션 메타데이터이고, 실제로는 트랜잭션 프록시/인터셉터가 외부 호출을 감싸면서 시작하고 종료합니다."

---

## 31-2. 나쁜 답변
"`@Autowired` 는 스프링이 알아서 넣어줍니다."

### 더 좋은 답변
"`AutowiredAnnotationBeanPostProcessor` 가 타입 기반 후보를 해석해 생성자/필드/메서드에 주입합니다."

---

## 31-3. 나쁜 답변
"`@Configuration` 이면 설정 클래스입니다."

### 더 좋은 답변
"`@Configuration` 은 설정 클래스로 해석되며, full mode 에서는 CGLIB enhancement 로 `@Bean` 메서드 간 직접 호출에도 singleton semantics 를 보장합니다."

---

## 31-4. 나쁜 답변
"BeanPostProcessor 는 초기화 전후에 호출됩니다."

### 더 좋은 답변
"BeanPostProcessor 는 초기화 전후에 개입할 뿐 아니라, after-init 단계에서 다른 객체를 반환해 프록시로 wrapping 할 수 있습니다. 그래서 AOP 의 핵심 확장 축입니다."

---

## 31-5. 나쁜 답변
"컴포넌트 스캔은 `@Component` 를 찾아서 빈으로 등록합니다."

### 더 좋은 답변
"컴포넌트 스캔은 base package 의 classpath 를 탐색하고, 보통 ASM 기반 메타데이터 리더로 클래스 로딩 없이 `@Component` 및 meta-annotation 후보를 찾아 BeanDefinition 으로 등록합니다."

---

## 31-6. 나쁜 답변
"순환참조는 스프링이 해결해 줍니다."

### 더 좋은 답변
"생성자 순환참조는 해결할 수 없고, 과거에는 setter/field 기반 singleton 순환참조를 early reference 와 3단계 캐시로 일부 풀 수 있었지만 설계상 권장되지 않으며 Boot 에서는 기본적으로 금지 방향입니다."

# 32. 스프링 내부를 설명할 때 꼭 구분해야 하는 축 10개

이 장은 전체를 머릿속에 정리하는 압축 지도다.

---

## 32-1. 정의 vs 인스턴스
- 정의: BeanDefinition
- 인스턴스: 실제 object

## 32-2. 등록 단계 vs 생성 단계
- 등록: scan, `@Bean`, import, auto-config
- 생성: instantiate, inject, init, post-process

## 32-3. BeanFactory vs ApplicationContext
- 코어 컨테이너
- 엔터프라이즈 확장 컨테이너

## 32-4. BDRPP/BFPP vs BPP
- 정의 후처리
- 인스턴스 후처리

## 32-5. 원본 객체 vs 프록시 객체
- target
- final exposed bean

## 32-6. singleton scope vs prototype/request scope
- 장수 객체
- 단기 객체

## 32-7. 생성자 주입 vs 필드/세터 주입
- 조기 불변성/명시성
- 후행 주입/유연성

## 32-8. metadata annotation vs runtime behavior
- `@Transactional` 은 메타데이터
- 실제 동작은 interceptor/proxy

## 32-9. logical transaction vs physical transaction
- propagation 이해의 핵심

## 32-10. framework core vs Boot convenience
- Spring Framework: 컨테이너/AOP/transaction 핵심
- Spring Boot: 자동설정/실행 편의/운영 기능

# 33. 정리용 표 모음

## 33-1. 빈 등록 방식 비교

| 방식 | 장점 | 단점 | 대표 사용처 |
|---|---|---|---|
| `@Component` + scan | 간결, 자동 탐지 | 명시성 약함 | 내부 애플리케이션 컴포넌트 |
| `@Bean` | 생성 로직 명시적 | 설정 클래스 필요 | 외부 라이브러리, 복잡한 생성 |
| `@Import` | 모듈 조합 좋음 | 흐름 추적 필요 | 설정 모듈화 |
| `ImportSelector` / Registrar | 매우 강력 | 복잡도 큼 | 프레임워크/라이브러리 |
| 자동설정 | 편의성 최고 | 암묵성 증가 | Boot starter |

## 33-2. 라이프사이클 콜백 비교

| 방식 | 장점 | 단점 |
|---|---|---|
| `@PostConstruct` / `@PreDestroy` | 표준적, 깔끔 | 컨테이너 외 객체에는 무의미 |
| `InitializingBean` / `DisposableBean` | 명시적 | 스프링 의존 |
| initMethod / destroyMethod | 외부 라이브러리 빈에 유용 | 문자열 기반 설정 |

## 33-3. 후처리기 비교

| 구분 | 대상 | 시점 | 대표 예 |
|---|---|---|---|
| `BeanDefinitionRegistryPostProcessor` | BeanDefinition 등록 | 아주 이른 시점 | `ConfigurationClassPostProcessor` |
| `BeanFactoryPostProcessor` | BeanDefinition 수정 | bean 생성 전 | placeholder 처리 |
| `BeanPostProcessor` | bean instance | 생성 과정 | `@Autowired`, `@PostConstruct`, AOP |

## 33-4. 프록시 방식 비교

| 방식 | 장점 | 제약 |
|---|---|---|
| JDK proxy | 표준, 단순 | 인터페이스 기반 |
| CGLIB | 클래스 기반 가능 | final/private 제약 |

## 33-5. 트랜잭션 propagation 요약

| propagation | 의미 | 주의 |
|---|---|---|
| REQUIRED | 있으면 참여, 없으면 생성 | rollback-only 전파 |
| REQUIRES_NEW | 항상 새 tx | 커넥션 추가 사용 |
| NESTED | savepoint 기반 | 지원 여부 확인 |
| SUPPORTS | 있으면 참여 | 비트랜잭션 가능 |
| NOT_SUPPORTED | tx 없이 실행 | 일관성 주의 |
| MANDATORY | 기존 tx 필수 | 없으면 예외 |
| NEVER | tx 있으면 예외 | 제한적 |

# 34. 학습 루틴: 7일 압축 플랜

이 문서를 실제 학습으로 바꾸기 위한 루틴이다.

---

## Day 1
- Bean / BeanDefinition / BeanFactory / ApplicationContext 정리
- `ApplicationContext` 로 직접 bean name 찍어보기
- 면접 답변 5개 소리 내기

## Day 2
- refresh 흐름
- `ConfigurationClassPostProcessor`, `AutowiredAnnotationBeanPostProcessor` 브레이크포인트 실습
- Component Scan 범위 실험

## Day 3
- `@Configuration`, `@Bean`, `proxyBeanMethods`
- full vs lite config 실험
- `@Bean` 간 직접 호출 비교

## Day 4
- 빈 생명주기 실험
- `@PostConstruct`, `afterPropertiesSet`, `destroy` 로그 실험
- custom BeanPostProcessor 만들기

## Day 5
- `@Transactional`, 프록시, self-invocation
- propagation REQUIRED / REQUIRES_NEW 실험
- rollback-only 시나리오 구현

## Day 6
- scope, prototype, request scope, scoped proxy 실험
- circular dependency 실험
- `ObjectProvider` / `@Lazy` 비교

## Day 7
- Boot 자동설정, conditions 리포트 보기
- 꼬리질문 30개 자가 면접
- 회사별 예상 질문에 맞춰 30초/3분 답변 만들기

# 35. 면접 직전 1시간 복습 포인트

---

## 35-1. 반드시 입으로 설명할 수 있어야 하는 것 10개
1. Bean vs BeanDefinition
2. BeanFactory vs ApplicationContext
3. `refresh()` 순서
4. `@Component` vs `@Bean`
5. Component Scan 내부 동작
6. 빈 생명주기
7. BeanPostProcessor vs BeanFactoryPostProcessor
8. `@Autowired` 처리 주체
9. `@Transactional` 의 프록시 기반 동작
10. Boot 자동설정과 back-off

## 35-2. 반드시 주의할 함정 10개
1. self-invocation
2. private/final method
3. `proxyBeanMethods=false`
4. prototype in singleton
5. circular dependency
6. `readOnly=true` 과신
7. `REQUIRES_NEW` 남용
8. `@PostConstruct` 에 heavy logic
9. bean name 충돌
10. scan 범위 과다

## 35-3. 마무리 한 줄
스프링은 "어노테이션 모음" 이 아니라,  
**BeanDefinition → refresh → post-processor → proxy → lifecycle** 로 이어지는 컨테이너 시스템이다.

# 36. 최종 요약: 경력직답게 스프링을 설명하는 프레임

면접에서 스프링 관련 질문이 나오면 아래 프레임으로 말하면 깊이가 생긴다.

---

## 36-1. 1단계: 큰 그림
"스프링은 IoC 컨테이너를 중심으로 객체 생성, 의존성 주입, 생명주기, 부가기능을 관리합니다."

## 36-2. 2단계: 내부 엔진
"실제로는 BeanDefinition 을 먼저 수집하고 `refresh()` 과정에서 BeanFactoryPostProcessor 로 정의를 정리한 뒤, BeanPostProcessor 로 실제 인스턴스를 가공합니다."

## 36-3. 3단계: 어노테이션 해석
"`@Component` 는 등록 단계, `@Autowired` 는 주입 단계, `@PostConstruct` 는 초기화 단계, `@Transactional` 은 프록시/AOP 단계에서 의미를 가집니다."

## 36-4. 4단계: 실무 trade-off
"트랜잭션 경계, scan 범위, 순환참조, 프록시 한계, 스코프 혼합, 자동설정 override 같은 지점이 실무 품질을 좌우합니다."

## 36-5. 5단계: 설계 감각
"스프링을 잘 쓴다는 건 어노테이션을 많이 붙이는 게 아니라, 컨테이너 단계와 프록시 한계를 이해하고 예측 가능한 구조를 만드는 것입니다."

# 부록 A. 빠르게 보는 공식 개념 지도

- Bean: 컨테이너 관리 객체
- BeanDefinition: 빈 메타데이터
- BeanFactory: 코어 IoC 컨테이너
- ApplicationContext: 확장 컨테이너
- refresh(): 컨테이너 초기화 전체 흐름
- BDRPP/BFPP: 빈 정의 단계 후처리
- BPP: 빈 인스턴스 단계 후처리
- `@Configuration`: 설정 클래스
- `@Bean`: 메서드 기반 빈 등록
- Component Scan: classpath 후보 탐색
- AOP proxy: 부가기능 적용 장치
- `@Transactional`: 트랜잭션 메타데이터
- Auto-configuration: 조건부 자동 빈 등록

# 부록 B. 답변 훈련용 압축 카드

## 카드 1. 빈
"스프링 빈은 컨테이너가 관리하는 객체이며, 단순 객체와 달리 생성 방식, 주입, 스코프, 생명주기, 후처리, 프록시 적용의 대상입니다."

## 카드 2. 생명주기
"인스턴스 생성 → 의존성 주입 → aware → before-init → `@PostConstruct` → `afterPropertiesSet` → initMethod → after-init(프록시 가능) → 사용 → `@PreDestroy` → destroy"

## 카드 3. 후처리기
"정의는 BFPP, 인스턴스는 BPP"

## 카드 4. `@Transactional`
"프록시 + 인터셉터 + 트랜잭션 매니저"

## 카드 5. 자동설정
"명시적 후보 목록 + 조건부 등록 + 사용자 빈 back-off"

# 부록 C. 공식 문서 기반으로 기억해 둘 최신 포인트

아래 포인트는 버전 차이에 민감할 수 있으므로 공식 문서를 기준으로 기억하면 좋다.

1. Component Scan 은 classpath scanning 으로 candidate component 를 찾는다.
2. `@Repository`, `@Service`, `@Controller` 는 `@Component` 의 specialization 이다.
3. `@Autowired` 주입은 BeanPostProcessor 를 통해 처리된다.
4. BeanPostProcessor 는 초기화 전후 콜백을 제공하고, 객체를 프록시로 감쌀 수 있다.
5. BeanFactoryPostProcessor 는 bean instance 가 아니라 bean definition 을 다룬다.
6. `@Configuration` 의 `proxyBeanMethods` 는 inter-bean singleton semantics 와 직결된다.
7. `@Transactional` 은 metadata 이며, 활성화된 트랜잭션 관리와 AOP 프록시가 필요하다.
8. 프록시 기반 트랜잭션은 보통 외부 호출만 가로챈다.
9. `REQUIRES_NEW` 는 별도 물리 트랜잭션과 추가 리소스를 요구할 수 있다.
10. Boot 자동설정은 `AutoConfiguration.imports` 기반이며 component scan 대상이 아니다.

# 부록 D. 스스로 확인해야 할 질문 20개

1. 우리 프로젝트에서 bean count 는 몇 개인가?
2. 메인 클래스 위치 때문에 scan 범위가 과도하지 않은가?
3. `@Configuration(proxyBeanMethods=false)` 를 써도 안전한 설정 클래스인가?
4. field injection 이 남아 있는 이유는 무엇인가?
5. 순환참조를 `@Lazy` 로 숨기고 있지 않은가?
6. 트랜잭션 경계가 controller / service / repository 어디에 있는가?
7. self-invocation 으로 인해 `@Transactional` 이 무의미해진 코드는 없는가?
8. `REQUIRES_NEW` 사용 이유가 명확한가?
9. readOnly 트랜잭션은 실제로 최적화에 의미가 있는가?
10. prototype/request scope 사용이 정말 필요한가?
11. scoped proxy 때문에 디버깅이 어려운 지점은 없는가?
12. 자동설정 빈을 override 하는 곳이 문서화되어 있는가?
13. 커스텀 BeanPostProcessor 가 부팅 성능에 영향을 주지 않는가?
14. `@PostConstruct` 에 heavy I/O 가 들어가 있지 않은가?
15. 빈 이름 충돌 가능성은 없는가?
16. `@Repository` 예외 변환이 필요한 계층이 명확한가?
17. `FactoryBean` 을 쓴다면 `&name` 조회 의미를 팀이 알고 있는가?
18. 조건부 빈 등록이 테스트 환경에서 다르게 작동하지 않는가?
19. actuator `/beans`, `/conditions` 로 현재 상태를 확인해 본 적이 있는가?
20. 신입에게 스프링 컨테이너를 5분 안에 설명할 수 있는가?

# 부록 E. 결론

스프링을 깊게 이해한다는 것은 결국 아래 세 문장을 자기 말로 풀어낼 수 있다는 뜻이다.

1. **스프링은 객체를 직접 관리하기보다, 먼저 객체 생성/조립 메타데이터를 관리한다.**
2. **컨테이너는 `refresh()` 과정에서 정의를 정리하고, 인스턴스를 만들고, 후처리기로 가공하며, 필요하면 프록시로 대체한다.**
3. **어노테이션은 마법이 아니라 메타데이터이고, 실제 동작은 후처리기·프록시·인터셉터·트랜잭션 매니저 같은 실행 주체가 만든다.**

면접에서 이 관점이 잡혀 있으면 질문이 꼬리를 물어도 중심을 잃지 않는다.

- 빈이란?
- 어떻게 등록되나?
- 누가 주입하나?
- 언제 초기화되나?
- 왜 프록시가 필요하나?
- `@Transactional` 은 어디서 시작되나?
- 자동설정은 왜 생기나?
- 결국 다 BeanDefinition 과 후처리기의 문제로 환원되는가?

이 질문들이 하나의 그림으로 이어지기 시작하면,
그때부터 스프링은 "외워서 쓰는 프레임워크" 가 아니라
**예측 가능한 런타임 시스템** 으로 보이기 시작한다.
