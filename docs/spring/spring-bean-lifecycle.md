# 스프링 빈(Bean) 생명주기: “정확히 언제, 누가, 무엇을 하냐”를 단계별로 정리

> 목표  
> - 스프링 컨테이너가 **빈 정의(BeanDefinition)** 를 읽고 → **빈 인스턴스 생성** → **의존성 주입** → **초기화/프록시 적용** → **사용** → **소멸**까지,  
>   각 단계에서 어떤 “확장 포인트(후처리기/콜백)”가 어떤 일을 하는지 **정확한 역할 중심**으로 이해할 수 있게 정리합니다.  
> - 또한 **(1) 컨테이너 관점에서 `getBean()` 요청 흐름**과 **(2) 웹(MVC) 관점에서 HTTP 요청 흐름**을 **아스키 아트로 도식화**합니다.

---

## 0. 용어 빠른 정리

- **BeanDefinition**: “이 빈을 어떻게 만들지”에 대한 설계도(클래스, 스코프, 생성자/프로퍼티 값, 초기화/소멸 메서드, lazy 여부 등)
- **BeanFactory / ApplicationContext**: BeanDefinition을 관리하고 빈을 생성/주입/초기화/소멸하는 컨테이너
- **Post-Processor(후처리기)**: 컨테이너 동작을 가로채서 “정의/생성/초기화/소멸” 단계를 확장하는 플러그인
  - BeanDefinition 단계에 개입: `BeanFactoryPostProcessor`, `BeanDefinitionRegistryPostProcessor`
  - Bean 인스턴스 단계에 개입: `BeanPostProcessor` 계열(특히 AOP 프록시/어노테이션 처리 핵심)
- **AOP 프록시**: “빈 자체”를 감싸는 대리 객체(Proxy). 트랜잭션, 보안, 로깅 같은 횡단 관심사를 삽입.

---

## 1. 큰 그림: 컨테이너 시작부터 싱글톤 빈 준비까지

스프링 부트/스프링의 핵심 흐름은 대략 이렇게 생각하면 됩니다.

```
ApplicationContext#refresh()
   |
   +-- 1) BeanDefinition 로딩/등록 (스캔, XML, @Configuration, @Bean 등)
   |
   +-- 2) BeanFactoryPostProcessor 실행 (BeanDefinition 수정/추가/정리)
   |
   +-- 3) BeanPostProcessor 등록 (빈 생성 과정에 끼어드는 플러그인들)
   |
   +-- 4) (대부분) 싱글톤 빈 미리 생성(preInstantiateSingletons)
   |
   +-- 5) 이벤트 발행/리스너 등 준비 완료 -> 애플리케이션 실행
```

> 중요 포인트  
> - **BPP(BeanPostProcessor)는 “등록” 자체가 3단계**입니다.  
>   그리고 나서야 4단계에서 다른 일반 빈들이 생성될 때 BPP들이 작동합니다.  
> - 그래서 “후처리기가 후처리기를 또 처리한다” 같은 상황이 가능한데, 이는 스프링이 내부적으로 순서를 잘 맞춰줍니다.

---

## 2. BeanFactoryPostProcessor vs BeanPostProcessor: 핵심 차이

### 2.1 BeanFactoryPostProcessor (정의 수준 후처리기)

- **언제?**  
  모든 BeanDefinition이 등록된 후, **빈 인스턴스를 만들기 전에** 실행됩니다.
- **무엇을?**  
  BeanDefinition을 **조회/수정**할 수 있습니다. (아직 객체가 없으므로 인스턴스에 손댈 수는 없음)
- **왜 중요?**  
  “어떤 빈이 만들어질지” 자체를 바꿀 수 있어 파워가 큼.

대표 예:
- `PropertySourcesPlaceholderConfigurer`: `${...}` 플레이스홀더를 프로퍼티 값으로 치환
- `ConfigurationClassPostProcessor`: `@Configuration` 클래스 파싱 → `@Bean` 메서드들을 BeanDefinition으로 등록 (스프링 자바 설정의 핵심)

### 2.2 BeanDefinitionRegistryPostProcessor (정의 “등록”까지 건드리는 상위 타입)

- `BeanFactoryPostProcessor` 보다 더 이른 단계에서  
  **BeanDefinitionRegistry에 빈 정의를 추가 등록/변형**할 수 있습니다.
- 대표 예:
  - `ConfigurationClassPostProcessor`는 보통 이 역할도 수행 (구현체 관점)

### 2.3 BeanPostProcessor (인스턴스 수준 후처리기)

- **언제?**  
  빈이 “생성되고(객체 생김) → 의존성 주입되고 → 초기화되는 과정” 중간중간에 호출.
- **무엇을?**  
  실제 객체 인스턴스에 대해:
  - 초기화 전/후에 커스터마이징
  - 프록시로 교체(가장 핵심!)
  - 어노테이션 기반 주입/라이프사이클(@Autowired, @PostConstruct 등) 처리

대표 예:
- `AutowiredAnnotationBeanPostProcessor`: `@Autowired`, `@Value` 등을 실제 주입으로 연결
- `CommonAnnotationBeanPostProcessor`: `@PostConstruct`, `@PreDestroy` 처리
- `AbstractAutoProxyCreator` 계열: AOP 프록시 생성(예: `AnnotationAwareAspectJAutoProxyCreator`)

---

## 3. “빈 1개”가 만들어질 때 실제 생명주기(가장 중요)

아래는 **싱글톤/프로토타입/스코프를 막론하고** “컨테이너가 어떤 빈을 실제로 만들 때”의 전형적인 흐름입니다  
(단, 스코프별로 마지막 소멸 시점이 다릅니다).

### 3.1 ASCII 타임라인(확장 포인트 포함)

```
[getBean("myBean")] 또는 preInstantiateSingletons()
        |
        v
(0) BeanDefinition 조회/결정
        |
        v
(1) InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
        |   - "인스턴스 만들기 전에" 가로채서 프록시를 먼저 반환할 수도 있음
        |   - 반환값 != null 이면: (2~4 건너뛰고) 초기화 후 단계로 이동 가능
        v
(2) 실제 인스턴스 생성 (생성자 호출)
        |
        v
(3) InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation
        |   - 프로퍼티 주입을 계속할지 여부(boolean) 결정
        v
(4) 의존성 주입(프로퍼티/필드/세터)
        |   - @Autowired 처리, @Value 처리 등이 여기서 이루어짐
        |   - InstantiationAwareBeanPostProcessor#postProcessProperties 등이 관여
        v
(5) Aware 콜백들
        |   - BeanNameAware / BeanFactoryAware / ApplicationContextAware 등
        v
(6) BeanPostProcessor#postProcessBeforeInitialization
        |   - 여기서 @PostConstruct 호출이 일어나는 경우가 매우 많음
        v
(7) 초기화 메서드 호출
        |   7-1) @PostConstruct (BPP에서 호출되는 케이스가 일반적)
        |   7-2) InitializingBean#afterPropertiesSet()
        |   7-3) custom init-method (@Bean(initMethod=...), XML init-method)
        v
(8) BeanPostProcessor#postProcessAfterInitialization
        |   - AOP 프록시 생성/적용이 대개 여기서 일어남
        |   - 반환 객체가 원본 빈이 아닌 "프록시"일 수 있음
        v
(9) Bean ready (컨테이너에 캐싱/노출, 주입 대상에게 전달)
```

> 매우 중요한 사실  
> - **AOP 프록시는 보통 (8) “초기화 이후” 단계에서 적용**됩니다.  
>   따라서 **@PostConstruct 시점에는 아직 프록시가 아닐 수 있어** `@Transactional` 같은 AOP 기반 기능이 기대대로 동작하지 않을 수 있습니다.  
>   (대표적으로 “PostConstruct에서 트랜잭션 시작이 안 된다” 류의 이슈)

---

## 4. 각 단계에서 “정확히 무슨 일을 하냐”를 더 깊게

### 4.1 (1) postProcessBeforeInstantiation: “인스턴스 만들기 전” 가로채기

- 인터페이스: `InstantiationAwareBeanPostProcessor`
- 목적:
  - “이 빈은 원본 인스턴스를 만들지 말고 **프록시/대체 인스턴스**를 먼저 내보내자”
  - 예: 특정 조건에서 mock/대체 구현체 반환, AOP 프록시를 조기 생성 등

실무에서 가장 흔한 케이스:
- 스프링 AOP의 AutoProxyCreator가 여기 또는 이후 단계에서 프록시를 만들 수 있음  
  (스프링 내부 최적화/전략에 따라 시점이 달라질 수 있지만 “인스턴스 생성 이전/이후에 프록시를 만들 수 있다”가 핵심)

### 4.2 (2) 실제 인스턴스 생성: 생성자/팩토리 메서드

- 생성 방식은 다양합니다.
  - 기본 생성자
  - 생성자 주입(생성자 인자 해결)
  - 정적 팩토리 메서드
  - 인스턴스 팩토리 메서드(@Bean 메서드 등)
- 이 시점에는 아직 필드/세터 주입은 안 되었을 수 있습니다.

### 4.3 (4) 의존성 주입: @Autowired, @Value, @Qualifier, @Resource 등

- 필드 주입/세터 주입/메서드 주입이 반영됩니다.
- 핵심 주역:
  - `AutowiredAnnotationBeanPostProcessor` (대개 `InstantiationAwareBeanPostProcessor`로 동작)
    - `@Autowired`, `@Value` 등의 메타정보를 읽고 실제 의존성을 해결해서 주입
  - 타입 매칭, @Qualifier, @Primary, 이름 매칭 등의 규칙이 이때 적용됩니다.

> 주입 시점의 함정  
> - 순환 참조(circular dependency)는 “생성자 주입”에서 특히 치명적입니다.  
>   필드/세터 주입은 프록시/조기참조(early reference) 전략으로 풀리는 경우도 있지만, 항상 안전하진 않습니다.

### 4.4 (5) Aware 계열: “컨테이너 인프라를 빈에게 알려주기”

- 대표 인터페이스:
  - `BeanNameAware`: 컨테이너에 등록된 빈 이름 알려줌
  - `BeanFactoryAware`: BeanFactory 접근 권한 제공
  - `ApplicationContextAware`: ApplicationContext 접근 제공
  - `EnvironmentAware`, `ResourceLoaderAware`, `ApplicationEventPublisherAware` 등
- 언제 쓰나?
  - “스프링 인프라에 직접 접근해야 하는” 프레임워크성 컴포넌트에서 사용
- 주의:
  - 남발하면 테스트/설계가 어려워져서 일반 비즈니스 로직 빈에는 보통 권장되지 않습니다.

### 4.5 (6) postProcessBeforeInitialization: 초기화 “직전” 훅

- 인터페이스: `BeanPostProcessor`
- 하는 일:
  - 초기화 메서드 실행 전에 빈을 검사/수정
  - 대표적으로 여기서 `@PostConstruct` 호출이 트리거됨  
    (`CommonAnnotationBeanPostProcessor`가 이 시점에서 @PostConstruct 메서드를 호출하는 식)

### 4.6 (7) 초기화 메서드들: “주입 끝났으니 준비 완료” 단계

초기화 트리거는 여러 개가 **순서대로** 올 수 있습니다.

- `@PostConstruct`
- `InitializingBean#afterPropertiesSet()`
- custom init-method (`@Bean(initMethod="...")` / XML `init-method`)

권장:
- 가능하면 **@PostConstruct** 또는 `@Bean(initMethod=...)` 같은 선언적 방식 선호  
- `InitializingBean`은 스프링에 대한 결합도가 올라갑니다.

### 4.7 (8) postProcessAfterInitialization: 초기화 “직후” 훅 (프록시의 본진)

- 인터페이스: `BeanPostProcessor`
- 가장 중요한 능력:
  - **반환값으로 다른 객체(프록시)를 돌려줄 수 있음**
  - 결과적으로 컨테이너에 저장되는 것은 원본이 아니라 프록시일 수 있음
- 대표 예:
  - `AbstractAutoProxyCreator` 계열이 여기서 AOP 프록시를 만들고 원본을 감싸 반환

---

## 5. 소멸(Destruction) 단계: 언제 무엇이 호출되나?

### 5.1 싱글톤 vs 프로토타입 vs 웹 스코프

- **singleton(기본)**  
  - 컨테이너가 시작될 때(대부분) 만들고  
  - 컨테이너가 종료될 때 소멸 콜백 실행
- **prototype**  
  - 요청할 때마다 새로 생성  
  - **컨테이너가 소멸을 관리하지 않음** (즉, destroy 콜백이 자동으로 호출되지 않는 경우가 일반적)  
  - 필요하면 사용자가 직접 정리하거나, 별도 전략 필요
- **request/session/application(web scopes)**  
  - 웹 요청/세션/서블릿 컨텍스트 생명주기와 연동되어 소멸 콜백이 호출됨

### 5.2 싱글톤 소멸 콜백 흐름(대표)

```
Context close / shutdown hook
        |
        v
DestructionAwareBeanPostProcessor#postProcessBeforeDestruction
        |
        v
@PreDestroy (보통 BPP가 등록한 destroy 콜백으로 실행)
        |
        v
DisposableBean#destroy()
        |
        v
custom destroy-method (@Bean(destroyMethod=...), XML destroy-method)
```

> 주의  
> - 실제 내부 호출 순서는 스프링 버전/설정에 따라 세부가 달라질 수 있지만,  
>   “BPP의 파괴 콜백 → 어노테이션 기반 파괴 → DisposableBean → 커스텀 destroy”의 큰 흐름으로 이해하면 대부분의 실무 상황에서 정확합니다.

---

## 6. “후처리기란 무엇인가?”를 진짜 실감나게: 대표 후처리기 3종

### 6.1 AutowiredAnnotationBeanPostProcessor

- 역할: `@Autowired`, `@Value`, `@Inject` 등을 해석해서 실제 주입 수행
- 포인트:
  - **객체가 만들어진 후** “의존성 주입(populateBean)” 단계에서 일함
  - 따라서 “생성자 주입”은 스프링 코어가 먼저 해결하고, 그 외 필드/세터 주입이 여기에 의해 완성되는 그림이 많음

### 6.2 CommonAnnotationBeanPostProcessor

- 역할: `@PostConstruct`, `@PreDestroy`, `@Resource` 등 JSR-250 기반 어노테이션 처리
- 포인트:
  - `@PostConstruct`는 보통 `postProcessBeforeInitialization`에서 실행
  - `@PreDestroy`는 소멸 단계에 맞춰 실행되도록 destroy 콜백 등록

### 6.3 (AOP) AnnotationAwareAspectJAutoProxyCreator 등 AutoProxyCreator

- 역할: 어떤 빈을 프록시로 감쌀지 판단하고, 필요하면 프록시 생성
- 포인트:
  - 프록시 적용 시점이 보통 초기화 이후(`postProcessAfterInitialization`)
  - `@Transactional`, `@Async`, `@Cacheable` 등은 대부분 이런 프록시 기반으로 동작

---

## 7. “빈 요청(getBean) 흐름”을 아스키아트로 보기

아래는 “컨테이너가 어떤 빈을 달라고 요청받았을 때” (예: 의존성 주입 중, 혹은 직접 getBean 호출)  
어떤 순서로 만들어지고 캐싱되는지의 큰 흐름입니다.

```
Caller (다른 빈 생성 중 / 또는 직접 getBean)
        |
        v
BeanFactory#getBean(name)
        |
        +--> (A) singleton cache에 이미 존재?
        |         |
        |         +-- yes --> 반환 (이미 프록시일 수도 있음)
        |         |
        |         +-- no ----+
        |                    |
        v                    v
    (B) BeanDefinition 조회/결정   (C) createBean(name)
                               |
                               +-- resolveBeforeInstantiation()
                               |     (InstantiationAwareBPP beforeInstantiation)
                               |        |
                               |        +-- proxy returned? -> applyAfterInitBPP -> cache -> return
                               |
                               +-- doCreateBean()
                                     |
                                     +-- instantiate (constructor)
                                     |
                                     +-- populate (DI)
                                     |
                                     +-- initialize
                                     |     - aware callbacks
                                     |     - BPP before init (@PostConstruct 등)
                                     |     - init methods
                                     |     - BPP after init (AOP proxy 등)
                                     |
                                     +-- register disposable (singleton이면)
                                     |
                                     +-- singleton cache에 저장
                                     |
                                     +-- return (proxy일 수도!)
```

---

## 8. “HTTP 요청이 들어왔을 때” 스프링 MVC 요청 흐름(아스키아트)

사용자가 말한 “요청 흐름”이 웹 요청이라면, 전형적인 Spring MVC 흐름은 아래와 같습니다.  
(빈 생명주기와의 연결점도 함께 표시합니다)

```
[Client]  ----HTTP---->  [Servlet Container(Tomcat 등)]
                               |
                               v
                        (1) Filter Chain
                               |
                               |  예: CharacterEncodingFilter, Spring Security Filter 등
                               v
                        (2) DispatcherServlet (Front Controller)
                               |
                               +--> (3) HandlerMapping: 어떤 Controller가 처리?
                               |
                               +--> (4) HandlerAdapter: 그 Controller를 어떻게 호출?
                               |
                               +--> (5) HandlerInterceptor (preHandle)
                               |
                               v
                        (6) Controller 호출
                               |
                               |  - Controller는 보통 "singleton bean"
                               |  - 만약 @Lazy이면 첫 요청에서 생성될 수 있음
                               |  - request scope bean은 여기서 매 요청 생성될 수 있음
                               v
                        (7) 반환 처리
                               |
                               +--> @ResponseBody / HttpMessageConverter (JSON 직렬화)
                               |
                               +--> ViewResolver -> View 렌더링 (템플릿 엔진)
                               |
                               v
                        (8) HandlerInterceptor (postHandle / afterCompletion)
                               |
                               v
                        (9) Filter Chain 반환
                               |
                               v
[Client] <---HTTP Response--- [Servlet Container]
```

### 8.1 요청 스코프(request scope) 빈이 끼어드는 지점

request scope 빈은 “컨테이너 시작 때”가 아니라 **요청이 들어올 때마다** 만들어집니다.

```
요청 시작
   |
   +-- request scope 빈 필요함? (컨트롤러/서비스에서 참조)
         |
         +-- scoped proxy가 있다면:
                - 실제 객체는 "이번 요청"의 RequestAttributes에서 조회/생성
   |
요청 끝
   |
   +-- request scope 빈 destroy 콜백 호출(가능)
```

---

## 9. 자주 헷갈리는 포인트 TOP 10

1) **@PostConstruct는 “프록시 적용 전”에 실행되는 경우가 흔함**  
   → @Transactional을 PostConstruct에서 기대하면 안 맞을 수 있음

2) **BeanFactoryPostProcessor는 객체가 없을 때 일한다(정의만 있음)**  
   → DI를 기대하면 안 됨

3) **BeanPostProcessor는 객체를 바꿀 수도 있다(프록시 교체 가능)**  
   → “주입받은 타입이 실제 구현체가 아닐 수 있음”

4) **prototype 스코프는 소멸을 컨테이너가 책임지지 않는다**  
   → 리소스 정리 주의

5) **순환참조는 생성자 주입에서 특히 위험**  
   → 설계 개선을 강하게 권장

6) **@Configuration의 @Bean 메서드는 그냥 메서드 호출이 아니다**  
   → ConfigurationClassPostProcessor가 프록시화해서 싱글톤 보장 등 처리

7) **BPP는 등록 타이밍이 중요**  
   → 어떤 빈이 BPP로 등록되느냐에 따라 다른 빈 생성 과정에 영향

8) **빈 이름/타입 선택 규칙(@Primary, @Qualifier 등)은 DI 단계에서 적용된다**

9) **AOP 프록시의 종류**  
   - 인터페이스 기반: JDK Dynamic Proxy  
   - 클래스 기반: CGLIB  
   → 캐스팅/프록시 체인에서 차이가 날 수 있음

10) **SmartLifecycle / ApplicationListener는 “시작/종료 시점”에 추가 훅을 제공**  
   → 빈 생성/소멸과는 별개의 실행 단계 관리에 유용

---

## 10. 예제 코드: 라이프사이클 훅을 눈으로 확인하기

### 10.1 단일 빈에서 실행 순서 확인

```java
@Component
public class LifeCycleDemo implements BeanNameAware, ApplicationContextAware, InitializingBean, DisposableBean {

    public LifeCycleDemo() {
        System.out.println("1) constructor");
    }

    @Override
    public void setBeanName(String name) {
        System.out.println("5) BeanNameAware: " + name);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        System.out.println("5) ApplicationContextAware");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("7-1) @PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("7-2) afterPropertiesSet");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("destroy) @PreDestroy");
    }

    @Override
    public void destroy() {
        System.out.println("destroy) DisposableBean.destroy");
    }
}
```

### 10.2 간단한 BeanPostProcessor 예시

```java
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.equals("lifeCycleDemo")) {
            System.out.println("6) BPP before init: " + beanName);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.equals("lifeCycleDemo")) {
            System.out.println("8) BPP after init: " + beanName);
        }
        return bean;
    }
}
```

---

## 11. 한 문장 요약(기억하기)

- **BeanFactoryPostProcessor**: “빈을 만들기 전에, 설계도(BeanDefinition)를 손본다.”  
- **BeanPostProcessor**: “빈이 만들어지는 과정에서, 실제 객체를 바꾸거나(프록시), 어노테이션을 처리한다.”  
- **초기화 전후 훅**에서 많은 마법이 일어난다. 특히 AOP 프록시.

---

## 12. 다음 단계 학습 로드맵(원하면 더 깊게)

원하면 아래 주제도 “원리+예제+디버깅 포인트”까지 더 깊게 확장해 드릴 수 있습니다.

- `AbstractAutowireCapableBeanFactory#createBean` 내부 흐름 디버깅 가이드
- `AutowiredAnnotationBeanPostProcessor`가 실제로 필드/메서드에 주입하는 방식
- `AnnotationAwareAspectJAutoProxyCreator`가 Advisor/Pointcut을 찾고 프록시 만드는 과정
- `@Configuration` 클래스가 왜 프록시화되는지, 그리고 @Bean 메서드 호출이 왜 싱글톤을 보장하는지
- request scope / session scope + scoped proxy 내부 동작(RequestContextHolder 등)
- 순환참조 해결 전략(설계/프록시/지연 로딩/Provider/ObjectFactory)

