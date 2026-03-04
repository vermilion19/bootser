# Spring Bean Lifecycle Deep Dive (면접 대비: “깊게” 답변하기 위한 정리)

> 목표: **“스프링 빈 생명주기”를 면접에서 10분~30분 동안 끊김 없이 깊게 설명**할 수 있을 정도로,
> 단계별로 **누가(컴포넌트/확장 포인트), 언제(시점), 무엇을(정확히 하는 일)**을 정리한다.  
> 또한 “요청이 들어왔을 때” (1) **컨테이너 관점의 getBean 흐름**과 (2) **웹 요청(Spring MVC) 흐름**을 **ASCII 아트로 도식화**한다.

---

## 버전/전제

- 본 문서는 Spring Framework **5.x ~ 6.x** 공통 개념을 중심으로 설명한다.  
  (세부 클래스명/내부 구현은 버전마다 조금씩 달라질 수 있지만, **핵심 단계/확장 포인트는 매우 안정적**이다.)
- Spring Boot를 쓰는 경우, Boot가 “스프링을 대신 설정해주는 층”이 추가되지만, **BeanFactory/BeanPostProcessor 기반 생명주기 자체는 동일**하다.
- 설명 단위:
  - **BeanDefinition 단계** (설계도/메타데이터 단계)
  - **Bean 인스턴스 단계** (객체 생성/주입/초기화/프록시/파괴)
  - **ApplicationContext refresh 단계** (컨테이너 전체 생명주기)

---

## 목차

1. [큰 그림: BeanFactory / ApplicationContext / BeanDefinition](#1-큰-그림-beanfactory--applicationcontext--beandefinition)
2. [컨테이너 전체 생명주기: `refresh()` 단계 (가장 중요)](#2-컨테이너-전체-생명주기-refresh-단계-가장-중요)
3. [빈 생성 전체 타임라인(확장 포인트 한눈에)](#3-빈-생성-전체-타임라인확장-포인트-한눈에)
4. [BeanDefinition 단계 후처리: BFPP / BDRPP](#4-beandefinition-단계-후처리-bfpp--bdrpp)
5. [getBean() 내부 흐름: 캐시, 생성, 초기화, 조기 노출](#5-getbean-내부-흐름-캐시-생성-초기화-조기-노출)
6. [BeanPostProcessor 완전정복: 종류별로 “정확히” 무슨 일을 하나](#6-beanpostprocessor-완전정복-종류별로-정확히-무슨-일을-하나)
7. [의존성 주입(Autowired)의 실제 동작 위치](#7-의존성-주입autowired의-실제-동작-위치)
8. [초기화 콜백: @PostConstruct / InitializingBean / initMethod](#8-초기화-콜백-postconstruct--initializingbean--initmethod)
9. [AOP 프록시가 끼어드는 지점: @Transactional이 “언제” 붙나](#9-aop-프록시가-끼어드는-지점-transactional이-언제-붙나)
10. [순환 참조(circular dependency)와 3단 캐시(면접 단골)](#10-순환-참조circular-dependency와-3단-캐시면접-단골)
11. [스코프: singleton/prototype/request… + scoped proxy](#11-스코프-singletonprototyperequest--scoped-proxy)
12. [FactoryBean / @Configuration 프록시 / @Bean 호출의 비밀](#12-factorybean--configuration-프록시--bean-호출의-비밀)
13. [웹 요청 흐름(Spring MVC): Filter → DispatcherServlet → Controller](#13-웹-요청-흐름spring-mvc-filter--dispatcherservlet--controller)
14. [이벤트와 생명주기: ContextRefreshedEvent, SmartLifecycle](#14-이벤트와-생명주기-contextrefreshedevent-smartlifecycle)
15. [디버깅/검증 로드맵: 로그/브레이크포인트/관찰법](#15-디버깅검증-로드맵-로그브레이크포인트관찰법)
16. [면접 Q&A: “깊게” 나오는 단골 질문 35선](#16-면접-qa-깊게-나오는-단골-질문-35선)
17. [부록: 치트시트/표/ASCII 도식 모음](#17-부록-치트시트표ascii-도식-모음)

---

## 1) 큰 그림: BeanFactory / ApplicationContext / BeanDefinition

스프링 컨테이너 이야기를 “빈 생명주기”로 들어가려면, **세 가지 층**을 정확히 분리해야 한다.

### 1.1 BeanDefinition = 빈의 “설계도”

- BeanDefinition은 “이 빈을 어떤 클래스로 만들지”, “스코프는 뭔지”, “주입할 프로퍼티는 뭔지”, “초기화/파괴 메서드는 뭔지” 같은 **메타데이터**다.
- 핵심: **BeanDefinition은 객체가 아니다.**
  - BeanDefinition이 등록돼도, 빈 인스턴스는 아직 생성되지 않았을 수 있다. (`lazy` 또는 singleton이라도 아직 안 만들었을 수 있음)

### 1.2 BeanFactory = 빈 생성/조회 엔진

- BeanFactory는 `getBean()`을 통해 빈을 생성하고, 캐시하고, 반환하는 **핵심 엔진**이다.
- “빈 생명주기”의 대부분은 BeanFactory 구현(대표적으로 `DefaultListableBeanFactory`)에서 일어난다.

### 1.3 ApplicationContext = BeanFactory + α (컨테이너 운영체제)

ApplicationContext는 BeanFactory 위에 더 얹는다:

- 메시지 소스(MessageSource), 국제화
- 이벤트(ApplicationEvent), 리스너
- 리소스 로딩(ResourceLoader)
- 환경(Environment), 프로파일
- 웹 환경이면 WebApplicationContext 구성

즉, 면접에서 “BeanFactory vs ApplicationContext”를 묻는다면:
- BeanFactory: **빈 생성/관리 최소 기능**
- ApplicationContext: **프레임워크 운영에 필요한 부가기능까지 포함**

---

## 2) 컨테이너 전체 생명주기: `refresh()` 단계 (가장 중요)

스프링 컨테이너가 “시작할 때” 가장 큰 진입점은 보통 `AbstractApplicationContext#refresh()`다.  
면접에서 깊게 답하려면 “빈 하나”가 아니라 “컨테이너가 부팅되는 전체 단계”를 알아야 한다.

아래 ASCII는 refresh의 대표 흐름(세부 단계는 버전마다 조금씩 다르지만 핵은 동일):

```
ApplicationContext#refresh()
 |
 +--> prepareRefresh()
 |
 +--> obtainFreshBeanFactory()
 |      - BeanFactory 생성/초기화
 |
 +--> prepareBeanFactory(beanFactory)
 |      - ClassLoader, resolver, Aware processor 등 기본 세팅
 |
 +--> postProcessBeanFactory(beanFactory)
 |      - 서브클래스가 커스터마이즈할 훅
 |
 +--> invokeBeanFactoryPostProcessors(beanFactory)
 |      - ★ BeanDefinition 단계 후처리(BFPP/BDRPP)
 |      - @Configuration 파싱, 컴포넌트 스캔 결과 등록 등도 여기 영향
 |
 +--> registerBeanPostProcessors(beanFactory)
 |      - ★ Bean 인스턴스 단계 후처리(BPP) 등록
 |      - Autowired, @PostConstruct, AOP 프록시 등 대부분의 매직이 여기 등록된 BPP로 동작
 |
 +--> initMessageSource()
 +--> initApplicationEventMulticaster()
 +--> onRefresh()
 |
 +--> registerListeners()
 |
 +--> finishBeanFactoryInitialization(beanFactory)
 |      - ★ (대부분) singleton 빈 인스턴스 생성(preInstantiateSingletons)
 |
 +--> finishRefresh()
        - 이벤트 발행(ContextRefreshedEvent), Lifecycle start 등
```

면접에서 **“후처리기(Processor)들이 언제 등록되고 실행되냐”**를 물으면,
위 흐름에서 정확히 집어줘야 한다:

- BFPP/BDRPP 실행: `invokeBeanFactoryPostProcessors`
- BPP 등록: `registerBeanPostProcessors`
- 실제 singleton 생성: `finishBeanFactoryInitialization`

즉, **BPP는 빈 생성 전에 미리 “등록”되어 있어야** 빈 생성 과정에 끼어들 수 있다.

---

## 3) 빈 생성 전체 타임라인(확장 포인트 한눈에)

아래는 “빈 하나”가 만들어질 때, 확장 포인트까지 포함한 전형적인 흐름이다.

> 주의: 실제로는 “스코프/프록시/순환참조/FactoryBean/생성 방식”에 따라 분기가 많다.  
> 하지만 면접에서는 아래를 “기본 골격”으로 제시하고, 분기를 추가 설명하는 방식이 좋다.

```
getBean("myBean")
 |
 +--> (0) BeanDefinition 조회/결정
 |
 +--> (1) resolveBeforeInstantiation
 |       InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
 |       - 인스턴스를 만들기 전, 프록시를 "미리" 반환할 수 있음
 |       - 반환 != null 이면: (2~4를 건너뛰고) 초기화 후 단계로 갈 수도 있음
 |
 +--> (2) instantiateBean (인스턴스 생성)
 |       - 생성자 호출 / 팩토리 메서드 / Supplier 등
 |
 +--> (3) postProcessAfterInstantiation
 |       InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation
 |       - 프로퍼티 주입을 계속할지 여부(boolean) 결정
 |
 +--> (4) populateBean (의존성 주입)
 |       - @Autowired / @Value / setter/field injection 처리
 |       - postProcessProperties 훅이 핵심
 |
 +--> (5) Aware 콜백
 |       - BeanNameAware, BeanFactoryAware, ApplicationContextAware ...
 |
 +--> (6) postProcessBeforeInitialization (BPP)
 |       - @PostConstruct가 "보통" 여기서 실행되도록 트리거됨
 |
 +--> (7) init methods
 |       7-1) @PostConstruct
 |       7-2) InitializingBean#afterPropertiesSet
 |       7-3) custom init-method
 |
 +--> (8) postProcessAfterInitialization (BPP)
 |       - ★ AOP 프록시 생성/적용이 가장 흔히 여기서 발생
 |       - 반환 객체가 원본이 아니라 "프록시"일 수 있음
 |
 +--> (9) Singleton cache 등록 / Bean ready
 |
 +--> (10) (나중에) destroy 단계
         - DestructionAwareBeanPostProcessor
         - @PreDestroy, DisposableBean, destroyMethod
```

**면접에서 딱 한 줄로 요약하면:**
- BFPP는 **설계도(BeanDefinition)**를 건드리고
- BPP는 **객체(Bean instance)**를 건드린다
- AOP는 대부분 **BPP의 afterInitialization**에서 프록시로 교체한다

---

## 4) BeanDefinition 단계 후처리: BFPP / BDRPP

### 4.1 BeanFactoryPostProcessor(BFPP)란?

- 정의: **BeanFactory(정확히는 BeanDefinition들)**를 후처리하는 훅
- 실행 시점: **빈 인스턴스 생성 이전**
- 하는 일: 주로
  - BeanDefinition을 추가/수정/정리
  - 프로퍼티 값 치환 (`${}`), 프로파일 조건 적용
  - 애노테이션 기반 설정을 해석해서 BeanDefinition을 등록

즉, BFPP는 “객체를 바꾸는 게 아니라 설계도를 바꾸는 것”이다.

### 4.2 BeanDefinitionRegistryPostProcessor(BDRPP)란?

- BDRPP는 BFPP의 하위(확장) 개념으로,
- “BeanDefinitionRegistry(=BeanDefinition을 등록하는 레지스트리)”에 직접 접근하여
  - **새 BeanDefinition을 등록**할 수 있는 더 강력한 후처리기

면접 팁:
- BFPP: “기존 설계도를 바꾸는 느낌”
- BDRPP: “설계도 자체를 새로 등록/추가할 수 있음”

### 4.3 가장 중요한 BFPP: ConfigurationClassPostProcessor

스프링의 애노테이션 기반 설정에서 매우 핵심:

- `@Configuration` / `@ComponentScan` / `@Import` / `@Bean` 등을 파싱한다.
- 파싱 결과로 **BeanDefinition들을 등록**한다.
- `@Configuration` 클래스는 “full mode”일 경우 CGLIB로 강화(enhancement)되어,
  - @Bean 메서드 간 호출에서도 싱글톤이 깨지지 않게 한다 (자세한 내용은 12장)

면접에서 “스프링이 애노테이션 설정을 어떻게 이해하나요?”라고 물으면,
**ConfigurationClassPostProcessor를 BFPP/BDRPP로 묶어 설명**하면 매우 강력하다.

### 4.4 BFPP 실행 순서/정렬 (PriorityOrdered / Ordered)

BFPP/BDRPP는 여러 개가 있을 수 있고, 실행 순서가 중요할 때가 많다.

- `PriorityOrdered` 구현 → 최우선
- 그 다음 `Ordered`
- 그 다음 “순서 없음”

또한 애노테이션 `@Order`도 Ordered 계열에 반영될 수 있다.

면접에서 순서 질문이 나오면:
- **“등록” 순서와 “실행” 순서를 구분**하자.
- 스프링은 보통 Ordered/priority를 반영하여 정렬 후 실행한다.

---

## 5) getBean() 내부 흐름: 캐시, 생성, 초기화, 조기 노출

### 5.1 `getBean()`이 하는 일을 한 문장으로

> “이름/타입을 키로 하여, 이미 만든 싱글톤이 있으면 캐시에서 꺼내고, 없으면 BeanDefinition을 기반으로 생성(createBean)해서 반환한다.”

하지만 “생성”에는 엄청난 로직이 들어있다.

### 5.2 싱글톤 캐시 구조(면접 단골): 1단/2단/3단 캐시

스프링은 순환참조를 다루기 위해 대표적으로 “3단 캐시” 구조를 쓴다.

- 1단: `singletonObjects`  
  - 완성된 싱글톤(초기화/프록시까지 끝난 최종 객체)
- 2단: `earlySingletonObjects`  
  - 조기 노출된 싱글톤(아직 완전 초기화 전일 수 있음)
- 3단: `singletonFactories`  
  - ObjectFactory(팩토리) 형태로 “필요할 때 early reference를 만들 수 있게” 저장

ASCII로 보면:

```
            (완성)                (조기)                 (조기 참조 생성용)
singletonObjects  <--- promote --- earlySingletonObjects <--- create --- singletonFactories
     [L1]                 [L2]                             [L3]
```

왜 3단이냐?
- “조기 참조를 실제로 만들 필요가 없는 경우”가 많아서
- 필요할 때만 factory를 통해 만들고 2단으로 올리는 구조가 효율적이고
- 특히 AOP 프록시 조기 참조(getEarlyBeanReference)와 결합된다.

### 5.3 getBean 흐름 ASCII (핵심 분기 포함)

```
Caller
 |
 v
DefaultListableBeanFactory#getBean(name)
 |
 +--> (A) singletonObjects에 있나?
 |        |
 |        +-- yes -> return (이미 프록시일 수도)
 |        |
 |        +-- no
 |
 +--> (B) "현재 생성 중"인데 early 참조 가능?
 |        |
 |        +-- yes -> earlySingletonObjects / singletonFactories 활용 -> return early ref
 |        |
 |        +-- no
 |
 +--> (C) createBean(name)
          |
          +--> resolveBeforeInstantiation()
          |      - SmartInstantiationAwareBPP#postProcessBeforeInstantiation
          |      - 반환 객체 != null이면 그걸로 대체(대개 프록시)
          |
          +--> doCreateBean()
                 |
                 +--> instantiateBean()  (생성자/팩토리메서드)
                 |
                 +--> addSingletonFactory(...)  ★ (순환참조 대비 조기 노출 준비)
                 |
                 +--> populateBean()     (DI)
                 |
                 +--> initializeBean()   (Aware, BPP, init)
                 |
                 +--> registerDisposableBeanIfNecessary()
                 |
                 +--> singletonObjects에 최종 등록
                 |
                 +--> return (최종, 프록시일 수 있음)
```

여기서 면접 포인트:
- “조기 노출 준비(addSingletonFactory)”가 instantiate 직후에 일어난다.
- “프록시가 필요한 경우”
  - 초기화 후(afterInitialization)에도 프록시로 교체될 수 있고
  - 순환참조 상황에서는 “early reference 단계”에서 프록시가 필요할 수 있다.

---

## 6) BeanPostProcessor 완전정복: 종류별로 “정확히” 무슨 일을 하나

면접에서 “빈 후처리기란?”을 물으면,
그냥 “빈 생성 전후에 개입하는 객체”라고만 답하면 얕게 보일 수 있다.

### 6.1 BeanPostProcessor(BPP)의 정체

**BeanPostProcessor는 빈 인스턴스 생성 과정에 끼어들어**
- 빈을 검사/수정/대체하는 훅이다.

핵심은 단순히 “콜백”이 아니라, **반환값으로 원본 빈 대신 다른 객체(프록시 등)를 반환할 수 있다는 점**이다.

대표 메서드:
- `postProcessBeforeInitialization(bean, beanName)`
- `postProcessAfterInitialization(bean, beanName)`

### 6.2 InstantiationAwareBeanPostProcessor (IABPP)

BPP보다 더 “앞 단계”까지 관여한다.
대표적인 훅:

- `postProcessBeforeInstantiation(Class<?> beanClass, String beanName)`
  - 인스턴스 만들기 전에 실행
  - **여기서 프록시를 미리 만들어 반환하면** 뒤의 instantiate/populate/init을 상당부분 스킵할 수 있음(케이스에 따라)

- `postProcessAfterInstantiation(Object bean, String beanName)`
  - 인스턴스 직후 호출
  - boolean 반환: `false`면 프로퍼티 주입(populateBean)을 건너뛸 수 있음

- `postProcessProperties(PropertyValues pvs, Object bean, String beanName)`
  - 실무에서 매우 중요: **@Autowired 처리 지점**이 보통 여기

### 6.3 SmartInstantiationAwareBeanPostProcessor (SIABPP)

IABPP보다 더 똑똑한 확장.
면접에서 AOP/순환참조까지 깊게 가려면 여기까지 설명하면 좋다.

주요 메서드:

- `predictBeanType(Class<?> beanClass, String beanName)`
  - “이 빈 최종 타입이 뭘 것 같은지” 예측(프록시 고려)

- `determineCandidateConstructors(Class<?> beanClass, String beanName)`
  - 어떤 생성자를 사용할지 결정하는데 개입
  - 생성자 주입 선택 로직과 관련

- `getEarlyBeanReference(Object bean, String beanName)`
  - ★ 순환참조 상황에서 “조기 참조(early reference)”가 필요할 때 호출
  - AOP 프록시가 필요하면 이 단계에서 프록시를 만들 수 있음

즉, **afterInitialization에서 프록시 만드는 것과 별개로**
순환참조를 해결하려면 **early 단계에서 프록시를 제공할 수도 있다.**

### 6.4 MergedBeanDefinitionPostProcessor (MBDPP)

이건 “메타데이터 캐싱/병합” 레벨의 최적화/전처리 성격이 강하다.

- BeanDefinition은 상속/합성/공장 메서드 등을 거치며 “병합된(Merged) 정의”로 사용되는데,
- MBDPP는 빈 생성 전에 병합된 정의를 보고 필요한 메타데이터를 캐시하는 역할을 한다.
- 예: @Autowired, @PostConstruct 관련 메타데이터를 미리 분석/캐시

### 6.5 DestructionAwareBeanPostProcessor

빈 파괴 단계에도 개입한다.
대표 메서드:

- `postProcessBeforeDestruction(Object bean, String beanName)`
  - @PreDestroy 같은 파괴 콜백을 트리거하기도 함(구현체에 따라)

- `requiresDestruction(Object bean)`
  - 이 빈이 파괴 콜백이 필요한지 판정

### 6.6 “대표적인 BPP들”이 실제로 하는 일

아래는 면접에서 “예를 들어 어떤 후처리기가 어떤 기능을 제공하나요?”에 답할 수 있도록 정리한 것.

- `AutowiredAnnotationBeanPostProcessor`
  - @Autowired, @Value, @Inject 등을 해석
  - 필드/세터/메서드/생성자 주입 메타데이터를 준비하고
  - populateBean 과정에서 실제 주입 수행

- `CommonAnnotationBeanPostProcessor` (또는 Init/Destroy 애노테이션 처리 BPP)
  - @PostConstruct, @PreDestroy 처리

- `AnnotationAwareAspectJAutoProxyCreator` (AutoProxyCreator 계열)
  - @Aspect / Advisor 기반 AOP 적용
  - 후보 빈에 대해 pointcut 매칭 후 ProxyFactory를 통해 프록시 생성
  - 보통 postProcessAfterInitialization에서 프록시로 교체

- `AsyncAnnotationBeanPostProcessor`
  - @Async 적용을 위해 프록시 생성(또는 기존 프록시에 Advisor 추가)

- `ScheduledAnnotationBeanPostProcessor`
  - @Scheduled 스케줄 작업 등록
  - (프록시 교체라기보다, 초기화 시점에 TaskScheduler에 등록하는 느낌)

- `PersistenceAnnotationBeanPostProcessor`(JPA 환경)
  - @PersistenceContext, @PersistenceUnit 주입

면접 답변 구조 추천:
1) BPP의 “정의”와 “핵심 능력(프록시로 교체 가능)”을 말하고
2) IABPP/SIABPP로 확장되는 “더 앞 단계 관여”를 설명하고
3) 대표 구현체 2~3개(Autowired, PostConstruct, AOP)를 예로 들어주면 깊이가 확 올라간다.

---

## 7) 의존성 주입(Autowired)의 실제 동작 위치

면접에서 “@Autowired는 언제 처리되나요?”라고 물으면,
정답은 대개 다음을 포함해야 한다:

- **BeanPostProcessor(정확히는 InstantiationAwareBeanPostProcessor)의 populate 단계에서 처리된다.**
- 즉, “스프링이 리플렉션으로 어노테이션 읽고 주입”하는 매직은 **후처리기의 역할**이다.

### 7.1 populateBean의 개념적 단계

```
populateBean(beanName, mbd, instance)
 |
 +--> InstantiationAwareBPP#postProcessProperties
 |       - @Autowired / @Value 주입 처리
 |
 +--> applyPropertyValues
         - BeanWrapper를 통해 실제 필드/세터에 값 반영
```

### 7.2 주입 후보 결정(autowire candidate) 로직

스프링은 의존성 주입 대상(DependencyDescriptor)을 만들고
다음 기준을 고려해 후보를 고른다(요약):

- 타입 매칭
- `@Primary`
- `@Qualifier`
- 이름 매칭(필요 시)
- 컬렉션 주입: 타입에 맞는 여러 빈을 모아 List/Map으로 주입
- `ObjectProvider<T>` / `Provider<T>`: 지연 조회 가능

면접 포인트:
- `@Order`는 “주입 후보 선택”보다는 “컬렉션 주입 시 정렬”에서 더 많이 체감된다.
- 주입 후보가 0개면: 필수 여부(required)면 예외, optional이면 null/Optional.empty
- 후보가 N개면: Primary/Qualifier로 좁히지 못하면 예외(모호성)

### 7.3 생성자 주입 vs 필드/세터 주입과 생명주기 영향

- 생성자 주입: 객체 생성 시점(instantiateBean)에서 해결되어야 한다.
  - 즉, 생성자 파라미터를 만들기 위해 **다른 빈을 먼저 getBean**해야 함
  - 순환참조에 약함(특히 A ↔ B가 서로 생성자 주입이면 거의 해결 불가)
- 필드/세터 주입: instantiate 이후 populateBean에서 해결된다.
  - 순환참조를 3단 캐시로 우회 가능할 때가 많다.

면접에서는 “왜 생성자 주입이 권장되나?”도 자주 묻는데,
- 불변성, 테스트 용이성, required 의존성 명확화 등의 장점
- 단, 순환참조 탐지/해결 측면에서 더 엄격해질 수 있음(=빨리 실패)

---

## 8) 초기화 콜백: @PostConstruct / InitializingBean / initMethod

### 8.1 초기화 단계의 정확한 순서

일반적으로 초기화는 아래 순서로 이해하면 좋다(대표 케이스):

1) (주입 완료 후) `Aware` 콜백
2) `BeanPostProcessor#postProcessBeforeInitialization`
3) `@PostConstruct`
4) `InitializingBean#afterPropertiesSet`
5) `@Bean(initMethod="...")` 또는 XML init-method
6) `BeanPostProcessor#postProcessAfterInitialization`

실무적으로 중요한 점:
- **@PostConstruct는 “BPP가 트리거”한다.**  
  스프링이 자바 표준 애노테이션을 자동으로 아는 게 아니라, 이를 처리하는 BPP가 등록되어 있기 때문.

### 8.2 @PostConstruct에서 흔히 하는 실수

- AOP 프록시가 아직 붙기 전일 수 있다.
  - 많은 경우 프록시는 afterInitialization에서 붙는다.
  - 그러면 @PostConstruct 안에서 `this`는 “프록시”가 아닌 “타깃 객체”일 수 있다.
- 따라서 @PostConstruct 안에서 트랜잭션 경계(@Transactional)가 적용되기를 기대하는 코드는 위험하다.

권장:
- 초기화에서 트랜잭션이 필요하면, `ApplicationListener<ContextRefreshedEvent>`나
  `SmartInitializingSingleton` 또는 별도의 “startup runner”에서 처리하는 패턴을 고려.
  (환경에 따라 다르지만 면접에서 이런 논점을 말하면 깊게 보임)

### 8.3 InitializingBean vs initMethod

- InitializingBean: 스프링 의존(인터페이스 구현 필요)
- initMethod: POJO 유지 가능 (더 선호됨)
- 하지만 “프레임워크 내부에서는” InitializingBean도 많이 사용한다.

면접에서는 다음 식으로 답변하면 좋다:
- “둘 다 가능하지만, 애플리케이션 코드 관점에서는 스프링 의존성을 줄이려 initMethod나 @PostConstruct를 선호합니다.”

---

## 9) AOP 프록시가 끼어드는 지점: @Transactional이 “언제” 붙나

면접 단골 질문: “@Transactional은 어떻게 동작하나요?”

깊게 답하려면 “트랜잭션 AOP가 적용되는 시점 = 빈 생명주기에서 프록시가 만들어지는 시점”을 연결해야 한다.

### 9.1 핵심 흐름(개념)

1) `@EnableTransactionManagement` 또는 Boot 자동설정이
   - 트랜잭션 관련 인프라 빈들(Advisor, Interceptor 등)을 등록한다.
2) AutoProxyCreator(대표적으로 AnnotationAwareAspectJAutoProxyCreator)가
   - 각 빈이 생성될 때 “이 빈에 적용할 Advisor가 있는지” 검사한다.
3) 적용 대상이면 `postProcessAfterInitialization` 단계에서 프록시를 생성해 반환한다.
4) 이후 컨테이너에 등록되는 것은 원본이 아니라 “프록시”다.
5) 프록시가 메서드 호출을 가로채서 트랜잭션 시작/커밋/롤백을 수행한다.

### 9.2 왜 self-invocation(자기 호출)은 트랜잭션이 안 걸리나?

프록시 기반 AOP에서 메서드 호출이 트랜잭션을 타려면:

- 호출이 **프록시를 통해** 들어와야 한다.

하지만 같은 클래스 내부에서 `this.someTransactionalMethod()`로 호출하면:
- 프록시를 거치지 않고 타깃 메서드로 직행 → Advice가 실행되지 않는다.

면접에서 이걸 말할 때는 꼭 “프록시 기반 AOP의 한계”로 설명하고,
해결 방법(대표적인 것들)을 덧붙이면 좋다:

- 구조를 바꿔서 다른 빈으로 분리(가장 권장)
- `AopContext.currentProxy()` 사용(설정 필요, 남용 주의)
- AspectJ weaving(런타임 프록시가 아닌 바이트코드 위빙) (현업에서는 상대적으로 드묾)

### 9.3 JDK 동적 프록시 vs CGLIB

프록시 방식은 주로 2가지:

- JDK dynamic proxy
  - 인터페이스 기반
  - 타깃이 인터페이스를 구현하고 있으면 가능
- CGLIB proxy
  - 클래스 상속 기반
  - 인터페이스 없어도 가능
  - 단, `final class`, `final method`는 프록시 어려움

면접 팁:
- “Spring은 기본적으로 인터페이스가 있으면 JDK 프록시, 없으면 CGLIB” 같은 식으로 설명하되,
  실제 설정 옵션에 따라 달라질 수 있다고 덧붙이면 정확하다.

### 9.4 프록시가 생명주기에 미치는 영향

- 컨테이너에 등록되는 실제 객체가 “원본이 아니라 프록시”일 수 있다.
- 그래서 다른 빈에 주입되는 것도 프록시가 된다.
- @PostConstruct 시점과 프록시 적용 시점의 미묘함이 버그를 만든다.

면접에서 “@PostConstruct에서 트랜잭션” 논점을 꺼내면
스프링 내부 흐름을 아는 인상을 주기 좋다.

---

## 10) 순환 참조(circular dependency)와 3단 캐시(면접 단골)

### 10.1 문제 정의

A가 B를 필요로 하고, B가 A를 필요로 하면:

- 둘 중 하나가 먼저 만들어지는 과정에서 상대방 빈이 필요해져서 `getBean()`이 재진입한다.
- 생성자 주입이면 매우 어렵고(거의 실패),
- 필드/세터 주입이면 “인스턴스만 먼저 만들고 나중에 주입”이 가능해서 해결 여지가 생긴다.

### 10.2 스프링의 전략: “조기 노출”

스프링은 싱글톤 생성 중에 다음을 한다:

1) A 인스턴스 생성(생성자 호출)
2) A를 완성하기 전에, **“A의 early reference를 제공할 수 있는 팩토리”**를 3단 캐시에 등록
3) A의 의존성 주입 중에 B를 생성하다가
4) B가 다시 A를 요청하면 → 3단 캐시를 통해 **A의 early reference**를 꺼내 주입

ASCII 시나리오:

```
create A
  |
  +-- instantiate A (A는 아직 미완성)
  |
  +-- addSingletonFactory(A)   [L3에 factory 등록]
  |
  +-- populate A -> needs B -> create B
         |
         +-- instantiate B
         |
         +-- addSingletonFactory(B)
         |
         +-- populate B -> needs A -> getBean(A)
                |
                +-- A는 현재 생성 중
                +-- L1 없음, L2 없음, L3 factory 있음 -> earlyRef(A) 생성 -> B에 주입
         |
         +-- initialize B -> B 완성 -> L1 등록
  |
  +-- populate A 계속 -> B 주입
  +-- initialize A -> A 완성 -> L1 등록
```

### 10.3 “AOP 프록시 + 순환참조”가 왜 복잡해지나?

핵심은 “B가 A를 주입받을 때, 받아야 하는 것은 원본 A냐, 프록시 A냐?”다.

- 트랜잭션/보안 같은 AOP가 걸리는 빈이면,
  최종적으로는 프록시가 컨테이너에 등록된다.
- 그런데 B가 A를 주입받는 시점은 “A가 완성되기 전(early reference)”일 수 있다.
- 그래서 스프링은 `getEarlyBeanReference()` 훅을 제공하여,
  필요하다면 early 단계에서도 프록시를 반환하도록 한다.

면접에서 이 수준까지 설명하면 “진짜 아는 사람” 느낌이 난다.

### 10.4 왜 생성자 순환참조는 보통 실패하나?

- 생성자 주입은 instantiate 시점에 의존성이 필요하다.
- 인스턴스를 만들기 전에 의존성을 먼저 만들어야 하므로,
  “조기 노출로 인스턴스만 먼저 만들어 둔다” 전략이 통하지 않는다.

---

## 11) 스코프: singleton/prototype/request… + scoped proxy

### 11.1 singleton vs prototype

- singleton (기본)
  - 컨테이너당 1개 인스턴스
  - 컨테이너가 생성/파괴를 책임진다
- prototype
  - `getBean()` 호출마다 새 인스턴스
  - 컨테이너는 “생성/주입/초기화”까지만 책임지고
  - 파괴(destroy)는 사용자 책임(일반적으로)

면접 단골:
- “prototype 빈은 @PreDestroy가 자동으로 호출되나요?”  
  → 보통 **아니다**. 컨테이너가 prototype의 전체 생명주기(파괴)를 관리하지 않는다고 답변.

### 11.2 request/session/application scope (웹)

- request: HTTP 요청마다 새 인스턴스
- session: 세션마다
- application: ServletContext(웹 앱) 단위

### 11.3 scoped proxy가 필요한 이유

문제:
- singleton 빈이 request 스코프 빈을 직접 주입받으면?
  - singleton은 앱 시작 시 만들어지고, request 빈은 요청이 있을 때 만들어지므로 시점이 안 맞는다.

해결:
- request 빈을 “프록시”로 주입하고,
- 실제 객체는 요청 시점에 RequestContext에서 찾아 delegating 한다.

ASCII:

```
singletonService  --(주입)-->  requestScopedBeanProxy
                                   |
                                   +--> 요청 시점에 실제 requestScopedBean을 lookup
                                   +--> method call을 위임(delegate)
```

면접에서 scoped proxy를 설명할 때 포인트:
- 프록시는 “생명주기/스코프 격차”를 메운다.
- 즉 “요청마다 다른 실제 객체로 라우팅하는 대리자”다.

### 11.4 ObjectProvider/Provider도 같은 문제의 다른 해법

- `ObjectProvider<T>`를 주입받아
- 필요한 순간에 `getObject()`로 실제 request 빈을 가져오는 방식
- 프록시 대신 “지연 조회”로 해결

---

## 12) FactoryBean / @Configuration 프록시 / @Bean 호출의 비밀

### 12.1 FactoryBean이란?

FactoryBean은 “이 빈 자체가 실제 빈이 아니라, 빈을 만드는 공장 역할”을 하는 특수한 계약이다.

- 일반 빈: 컨테이너에 등록된 객체가 곧 사용자에게 주입되는 객체
- FactoryBean: 컨테이너에 등록된 것은 공장이고,
  사용자에게 주입되는 것은 `FactoryBean#getObject()`가 만든 결과물

면접에서 자주 묻는 포인트:
- `"myFactoryBean"`을 getBean하면 → “공장이 만든 객체”가 나온다.
- `"&myFactoryBean"`을 getBean하면 → “FactoryBean(공장) 자체”를 얻는다.

### 12.2 factory-method(@Bean)와 FactoryBean은 다르다

- `@Bean`의 factory method: “컨테이너가 특정 메서드를 호출해서 객체를 생성하는 방식”
- FactoryBean: “컨테이너가 특별 취급하는 타입 자체”

둘을 혼동하면 안 된다(면접에서 종종 함정처럼 나옴).

### 12.3 @Configuration이 왜 특별한가? (proxyBeanMethods)

`@Configuration` 클래스는 흔히 CGLIB로 프록시/서브클래싱되어,
@Beean 메서드를 직접 호출해도 “항상 컨테이너의 싱글톤”을 반환하도록 만든다.

예를 들어:

```java
@Configuration
class AppConfig {
  @Bean
  A a() { return new A(b()); } // b() 직접 호출!
  @Bean
  B b() { return new B(); }
}
```

위 코드에서 `a()`가 `b()`를 직접 호출하면, 일반 자바라면 매번 new B()가 될 것 같지만,
@Configuration 프록시가 b() 호출을 가로채서 컨테이너에서 B를 꺼내 준다(싱글톤 보장).

반대로:
- `@Configuration`이 아닌 `@Component`에 @Bean을 붙인 “lite mode”에서는
  이런 보장이 약해질 수 있다.

Spring의 `proxyBeanMethods=false` 옵션(버전/환경에 따라 지원)은
이 프록시 기능을 끄고 성능을 올리는 대신, @Bean 메서드 간 직접 호출 시 싱글톤 보장을 포기할 수 있다.

면접에서 이걸 언급하면 매우 강력한 포인트가 된다.

---

## 13) 웹 요청 흐름(Spring MVC): Filter → DispatcherServlet → Controller

사용자가 말한 “요청이 들어왔을 때 요청이 어떻게 흘러가는지”는
웹 맥락에서는 아래 흐름이 가장 대표적이다.

### 13.1 Spring MVC 요청 흐름 ASCII

```
[Client]  ----HTTP---->  [Servlet Container (Tomcat/Jetty/...)]
                               |
                               v
                        (1) Filter Chain
                               |
                               v
                        (2) DispatcherServlet (Front Controller)
                               |
                               +--> (3) HandlerMapping
                               |       - URL/HTTP Method에 맞는 Handler(Controller) 찾기
                               |
                               +--> (4) HandlerAdapter
                               |       - 해당 Controller를 "어떻게 호출할지" 전략 결정
                               |
                               +--> (5) HandlerInterceptor.preHandle
                               |
                               v
                        (6) Controller 호출
                               |
                               +--> (7) Argument Resolver
                               |       - @RequestParam, @PathVariable, @RequestBody 등 파라미터 해석
                               |
                               +--> (8) Controller 로직 수행
                               |
                               +--> (9) ReturnValue Handler
                               |       - @ResponseBody면 HttpMessageConverter(JSON 등)
                               |       - View 이름이면 ViewResolver로 렌더링
                               |
                               v
                        (10) HandlerInterceptor.postHandle
                               |
                               v
                        (11) ExceptionResolver (예외 발생 시)
                               |
                               v
                        (12) HandlerInterceptor.afterCompletion
                               |
                               v
                        (13) Filter Chain 반환
                               |
                               v
[Client] <---HTTP Response--- [Servlet Container]
```

### 13.2 빈 생명주기와 웹 요청이 만나는 지점

- DispatcherServlet, HandlerMapping, HandlerAdapter, Controller 등은 대부분 싱글톤 빈이다.
- 하지만 request scope 빈(예: RequestContext 관련)은 요청마다 생성될 수 있다.
- request scope 빈을 싱글톤에 주입할 때는 scoped proxy/ObjectProvider가 필요할 수 있다.

면접에서 “스프링 빈 생명주기”를 웹 요청과 연결해서 설명하면:
- “컨테이너는 시작 시 대부분의 싱글톤을 만든다 → 요청은 그 빈들을 사용해 처리한다.”
- “요청 스코프 빈은 요청마다 만들어지고, 필요하면 프록시를 통해 싱글톤과 연결한다.”

---

## 14) 이벤트와 생명주기: ContextRefreshedEvent, SmartLifecycle

### 14.1 ApplicationEvent와 리스너

ApplicationContext는 이벤트를 발행하고, 리스너들이 이를 받아 처리할 수 있다.

- 컨테이너가 refresh를 마치면 `ContextRefreshedEvent`가 발행되는 경우가 많다.
- close 시에는 `ContextClosedEvent` 등.

### 14.2 SmartLifecycle / Lifecycle

면접에서 “스프링이 start/stop을 어떻게 관리하나요?”가 나오면:

- `Lifecycle`: start/stop 계약
- `SmartLifecycle`: phase를 가지고 순서를 제어 가능
  - 낮은 phase부터 start, 높은 phase부터 stop(일반적으로)

예: 메시지 소비자, 스케줄러 등 “컨테이너 시작/종료”에 맞춰 동작해야 하는 컴포넌트에 쓰임.

### 14.3 SmartInitializingSingleton

- 모든 singleton이 만들어진 뒤 한 번 호출되는 훅
- “@PostConstruct보다 더 늦게” 실행되며,
- “다른 싱글톤들이 다 준비된 이후”에 실행해야 하는 초기화 로직에 유용

면접에서 @PostConstruct의 한계를 언급하면서,
대안으로 SmartInitializingSingleton을 말하면 좋다.

---

## 15) 디버깅/검증 로드맵: 로그/브레이크포인트/관찰법

면접은 “암기”가 아니라 “이해”를 보여주는 자리라,
실제로 추적할 수 있는 방법을 말해주면 점수가 오른다.

### 15.1 추천 로그 카테고리(아이디어)

- bean 생성 흐름
  - `org.springframework.beans.factory.support`
  - `org.springframework.beans.factory`
- AOP 프록시
  - `org.springframework.aop`
  - `org.springframework.transaction`

(환경에 따라 로그 레벨/패키지명이 다를 수 있다. 핵심은 “빈 생성/프록시”에 TRACE/DEBUG를 켜는 방향.)

### 15.2 추천 브레이크포인트(핵심 지점)

- `AbstractApplicationContext#refresh`
- `DefaultListableBeanFactory#getBean`
- `AbstractAutowireCapableBeanFactory#doCreateBean`
- `AbstractAutowireCapableBeanFactory#populateBean`
- `AbstractAutowireCapableBeanFactory#initializeBean`
- `AbstractAutoProxyCreator#postProcessAfterInitialization`
- `AutowiredAnnotationBeanPostProcessor#postProcessProperties` (또는 유사)
- `DisposableBeanAdapter#destroy`

면접에서 “실제로는 여기서 디버깅해봤다” 정도의 뉘앙스를 풍기면 설득력이 커진다.

### 15.3 BPP/BFPP 목록 출력 아이디어(실무 팁)

면접에서 말로만 하지 말고 “이렇게 확인할 수 있다”는 식의 언급도 좋다.

- ApplicationContext에서 BeanFactory를 얻어 BPP 목록을 출력
- 또는 Actuator `/beans` 엔드포인트(있다면)로 조회

---

## 16) 면접 Q&A: “깊게” 나오는 단골 질문 35선

아래는 질문 → 답변 포인트(핵심 키워드) 형태로 정리했다.  
면접에서는 “키워드들을 연결해서 이야기하는 능력”이 중요하다.

### Q1. 스프링 빈의 생명주기를 단계별로 설명해보세요.
- BeanDefinition 등록 → (BFPP/BDRPP로 설계도 후처리)
- BPP 등록
- getBean / preInstantiateSingletons
- instantiate → populate → aware → beforeInit → init → afterInit(프록시) → ready
- destroy 시점: preDestroy/DisposableBean/destroyMethod

### Q2. BeanFactoryPostProcessor와 BeanPostProcessor 차이는?
- BFPP: BeanDefinition(설계도) 단계, 인스턴스 생성 전
- BPP: 인스턴스 단계, 초기화 전/후에 개입, 프록시로 교체 가능

### Q3. @Autowired는 언제 처리되나요?
- populateBean 과정에서 InstantiationAwareBPP(postProcessProperties)로 처리

### Q4. @PostConstruct는 누가 호출하나요?
- CommonAnnotation/InitDestroy 계열 BPP가 beforeInit 단계에서 트리거

### Q5. @Transactional은 어떻게 동작하나요?
- 트랜잭션 인프라(Advisor/Interceptor) 등록
- AutoProxyCreator가 afterInit에서 프록시 생성
- 호출이 프록시를 통해 들어오면 advice 실행

### Q6. self-invocation에서 @Transactional이 안 먹는 이유?
- 프록시를 거치지 않기 때문(프록시 기반 AOP 한계)

### Q7. 순환참조는 어떻게 해결되나요?
- 3단 캐시(singletonObjects/earlySingletonObjects/singletonFactories)
- 인스턴스만 먼저 만들고 조기 노출 → 이후 주입
- 생성자 순환참조는 보통 실패

### Q8. prototype 빈의 파괴는 누가 책임지나요?
- 컨테이너는 생성까지만, 파괴는 사용자 책임(일반적으로)

### Q9. request scope 빈을 singleton에 주입하려면?
- scoped proxy 또는 ObjectProvider로 지연 조회

### Q10. FactoryBean이 뭔가요?
- 컨테이너가 특별 취급하는 “공장 빈”, getObject() 결과가 실제 주입 대상
- &beanName으로 FactoryBean 자체 획득

... (더 많은 Q&A는 부록/심화에서 확장)

> 이 문서 후반부 부록에 “35선 전체 버전 + 답변 예시(문장 형태)”를 더 넣어두었다.

---

## 17) 부록: 치트시트/표/ASCII 도식 모음

이 부록은 면접 전날 “빠르게 복습”하기 위한 섹션이다.
하지만 단순 요약이 아니라, **깊은 질문이 들어왔을 때 꺼내 쓸 수 있는 확장 설명**도 같이 둔다.

---

### A. 빈 생성 단계별 “누가/무엇을” 표 (핵심)

| 단계 | 내부적으로 일어나는 일 | 대표 확장 포인트 | 대표 구현체/예 |
|---|---|---|---|
| BeanDefinition 등록 | 설계도(메타데이터) 등록 | ImportSelector, Registrar | @ComponentScan, @Import |
| BeanDefinition 후처리 | 설계도 수정/추가 | BFPP/BDRPP | ConfigurationClassPostProcessor |
| BPP 등록 | 인스턴스 후처리기들을 “미리” 준비 | registerBeanPostProcessors | AutowiredBPP, AutoProxyCreator |
| instantiate | 실제 객체 생성 | InstantiationAwareBPP | 생성자 선택/팩토리메서드 |
| early exposure 준비 | 순환참조 대비 factory 등록 | SmartInstantiationAwareBPP | getEarlyBeanReference |
| populate | 의존성 주입 | postProcessProperties | @Autowired/@Value |
| aware | 컨테이너 관련 정보 주입 | *Aware | BeanFactoryAware 등 |
| before init | 초기화 전 훅 | BPP beforeInit | @PostConstruct 트리거 |
| init | 초기화 메서드들 실행 | init callbacks | afterPropertiesSet, initMethod |
| after init | 초기화 후 훅 | BPP afterInit | AOP 프록시 적용 |
| destroy | 파괴 콜백 | DestructionAwareBPP | @PreDestroy, DisposableBean |

---

### B. “후처리기 실행 순서” 더 정확히 말하기 (면접 포인트)

면접에서 “후처리기들은 언제 등록되고 어떤 순서로 실행되나요?”가 나오면
다음 구조로 답하면 좋다:

1) refresh 과정에서 BFPP/BDRPP가 먼저 실행된다. (BeanDefinition 단계)
2) 그 다음 BPP들이 등록된다.
3) 실제 singleton 생성 과정에서 각 BPP가 체인처럼 실행된다.
4) 여러 BPP가 있을 때는 PriorityOrdered/Ordered 기준으로 정렬된다.

ASCII:

```
refresh()
 |
 +--> invokeBeanFactoryPostProcessors()
 |       - (BDRPP) register BeanDefinitions
 |       - (BFPP)  modify BeanDefinitions
 |
 +--> registerBeanPostProcessors()
 |       - (BPP) register into BeanFactory
 |
 +--> preInstantiateSingletons()
         - createBean() for each non-lazy singleton
             - BPP chain applied per bean
```

---

### C. createBean 내부 “세부 분기” 더 디테일하게

면접에서 더 깊게 들어오면, 아래 같은 분기를 말할 수 있다.

```
createBean(name)
 |
 +--> resolveBeforeInstantiation()
 |       - beforeInstantiation 훅
 |       - 프록시를 "먼저" 반환하는 케이스 가능
 |
 +--> doCreateBean()
         |
         +--> createBeanInstance()
         |       - 생성자, factory-method, supplier 등 다양한 전략
         |
         +--> addSingletonFactory()
         |       - 3단 캐시 준비 (순환참조)
         |
         +--> populateBean()
         |       - autowire by name/type, @Autowired 처리
         |
         +--> initializeBean()
                 |
                 +--> invokeAwareMethods()
                 |
                 +--> applyBeanPostProcessorsBeforeInitialization()
                 |
                 +--> invokeInitMethods()
                 |
                 +--> applyBeanPostProcessorsAfterInitialization()
                         - AOP 프록시 생성/교체 빈번
```

---

### D. “AOP 프록시가 생성되는 순간”을 ASCII로

```
initializeBean(bean)
 |
 +--> BPP beforeInit chain
 |
 +--> init callbacks (@PostConstruct, afterPropertiesSet, initMethod)
 |
 +--> BPP afterInit chain
        |
        +--> AutoProxyCreator checks advisors
        |
        +--> if match -> ProxyFactory -> create proxy
        |
        +--> return proxy (replace original)
```

면접 포인트:
- “컨테이너에 최종 등록되는 객체는 프록시일 수 있다”
- “따라서 주입되는 것도 프록시”
- “하지만 내부 메서드 호출(self-invocation)은 프록시를 거치지 않는다”

---

### E. 순환참조 3단 캐시를 “말로 설명하는 템플릿”

> “스프링은 싱글톤 생성 시점에 바로 singletonObjects에 넣지 않고,
> 조기 참조가 가능하도록 singletonFactories(ObjectFactory)를 등록해두고,
> 순환참조로 재진입하면 그 팩토리를 통해 early reference를 만들고 earlySingletonObjects에 두고,
> 최종 초기화가 끝나면 singletonObjects로 승격합니다.”

여기까지 말하면 면접에서 “아, 내부를 본 적이 있구나” 느낌이 난다.

---

### F. 면접에서 자주 나오는 “왜 이런 버그가 생기나?” 사례 8개

1) **@PostConstruct에서 @Transactional이 안 먹는다**
- 트랜잭션은 프록시 기반이고 afterInitialization에서 프록시가 붙는 경우가 많아 시점이 다를 수 있음

2) **self-invocation 트랜잭션 미적용**
- 프록시를 거치지 않는 호출이라 advice가 실행 안 됨

3) **request scope 빈을 singleton에 직접 주입해서 오류**
- 스코프 생명주기 불일치 → scoped proxy/ObjectProvider 필요

4) **생성자 순환참조로 애플리케이션 부팅 실패**
- 인스턴스 생성 전에 의존성이 필요해서 조기 노출 전략 불가

5) **final class/method에 AOP가 안 붙는다(CGLIB)**
- 상속 기반 프록시 제약

6) **두 개 이상의 후보 빈으로 @Autowired 모호성 예외**
- @Primary/@Qualifier로 해소

7) **prototype 빈 자원 해제가 안 된다**
- 컨테이너가 destroy를 관리하지 않으니 직접 close/destroy 필요

8) **@Configuration proxyBeanMethods=false에서 @Bean 직접 호출로 싱글톤 깨짐**
- 설정 최적화 옵션의 의미를 이해해야 함

---

### G. “35선” 확장 버전 (답변 문장 예시)

아래는 실제 말로 풀어내기 쉬운 형태로 작성했다.
(면접에서 그대로 외우기보다는, 문장 흐름을 참고해서 본인 스타일로 바꾸는 걸 추천)

#### Q1) 스프링 빈이 생성될 때 무슨 일이 일어나나요?
- “스프링은 먼저 BeanDefinition이라는 설계도를 확보합니다. 이 설계도는 @Configuration 파싱이나 컴포넌트 스캔 등으로 등록되고, BeanFactoryPostProcessor가 정의를 수정할 수 있습니다.  
  그 다음 BeanPostProcessor들이 BeanFactory에 등록되고, 실제로 getBean이 호출되거나 컨테이너가 preInstantiateSingletons를 수행할 때 인스턴스 생성이 시작됩니다.  
  인스턴스 생성 후에는 populate 단계에서 AutowiredAnnotationBeanPostProcessor 같은 후처리기가 의존성을 주입하고, Aware 콜백과 @PostConstruct/afterPropertiesSet/initMethod 같은 초기화가 수행됩니다.  
  마지막으로 postProcessAfterInitialization에서 AOP 프록시가 적용되면 원본 빈이 프록시로 대체되고, 그 최종 객체가 싱글톤 캐시에 올라가 주입 대상이 됩니다.”

#### Q2) BeanPostProcessor가 왜 중요한가요?
- “스프링의 많은 기능이 BeanPostProcessor 기반입니다. 예를 들어 @Autowired 주입, @PostConstruct 호출, @Transactional 같은 AOP 프록시 적용이 모두 빈 생성 과정의 후처리기들로 구현됩니다. 특히 postProcessAfterInitialization에서 반환값을 바꿀 수 있어서, 원본 빈을 프록시로 교체하는 게 가능합니다.”

#### Q3) BFPP와 BPP를 구분해서 설명해보세요.
- “BFPP는 빈 인스턴스가 만들어지기 전 BeanDefinition을 수정하는 단계고, BPP는 실제 인스턴스에 개입해 초기화 전/후로 수정하거나 프록시로 교체하는 단계입니다. 즉 BFPP는 설계도, BPP는 객체를 다룹니다.”

#### Q4) 순환참조는 어떻게 해결되나요?
- “스프링은 싱글톤 생성 중에 3단 캐시를 사용합니다. instantiate 직후 singletonFactories에 ObjectFactory를 등록해 조기 참조를 만들 수 있게 해두고, 순환참조로 다시 getBean이 들어오면 early reference를 만들어 earlySingletonObjects에 두고 주입합니다. 초기화가 끝나면 singletonObjects로 승격합니다. 다만 생성자 순환참조는 인스턴스 만들기 전에 의존성이 필요해서 이런 전략이 통하지 않아 실패하는 경우가 많습니다.”

#### Q5) request scope 빈은 언제 만들어지고 어떻게 주입되나요?
- “request scope 빈은 HTTP 요청마다 생성되는데, 싱글톤에 주입하려면 scoped proxy나 ObjectProvider 같은 지연 조회 방식이 필요합니다. 보통 프록시를 주입받고, 호출 시점에 실제 request 빈을 요청 컨텍스트에서 찾아 위임합니다.”

(이하 35개 전체를 더 추가할 수 있지만, 문서가 지나치게 길어질 수 있어
아래 “추가 확장 섹션”에서 더 깊은 질문들을 별도로 정리한다.)

---

# 추가 확장 섹션: 더 깊게 파고들기 (면접에서 “꼬리 질문” 대응)

이 섹션은 면접관이 한 단계 더 파고들 때(꼬리 질문) 대응하기 위한 “심화”다.  
특히 아래 주제는 스프링을 “진짜” 이해하는지 확인하려고 자주 찌르는 포인트다.

- BPP 체인과 “프록시 대체”의 정확한 의미
- @Configuration 클래스가 왜 CGLIB로 강화되는지
- FactoryBean과 일반 빈의 조회 차이
- 순환참조와 AOP가 만날 때 early proxy가 왜 필요한지
- prototype + 프록시 + lifecycle 조합에서 파괴 콜백은 어떻게 되는지

---

## S1) BeanWrapper, PropertyValues, 그리고 populateBean의 실체

스프링이 “필드/세터에 값을 넣는다”를 구현하는 핵심 도구 중 하나가 BeanWrapper다.

- BeanWrapper는 내부적으로 리플렉션을 캡슐화해서
  - 프로퍼티 접근(세터 호출, 필드 접근)
  - 타입 변환(ConversionService/PropertyEditor)
  - 중첩 프로퍼티(user.address.city) 같은 표현을 다룰 수 있다.

populateBean은 개념적으로:

1) 주입할 값(PropertyValues)을 준비
2) 타입 변환이 필요하면 변환
3) 실제 세터/필드에 반영

여기에 InstantiationAwareBPP의 `postProcessProperties`가 끼어들어
“애노테이션 기반 주입”을 구현한다.

즉, 면접에서 “Autowired는 어디서 동작하나요?”의 더 깊은 답:
- “populateBean 단계에서 BeanWrapper/PropertyValues 기반 주입이 이루어지고,
  AutowiredAnnotationBeanPostProcessor가 그 직전에 postProcessProperties로 개입해
  InjectionMetadata를 이용하여 필드/메서드에 값을 주입합니다.”

---

## S2) Aware 인터페이스는 왜 있는가? (그리고 누가 호출하나?)

Aware는 “컨테이너가 객체에게 컨테이너 정보를 알려주기 위한” 콜백이다.

예:
- BeanNameAware: 내 빈 이름을 알려달라
- BeanFactoryAware: BeanFactory를 알려달라
- ApplicationContextAware: ApplicationContext를 알려달라
- EnvironmentAware, ResourceLoaderAware 등

중요 포인트:
- Aware 호출은 대개 initializeBean 내부의 특정 단계에서 수행되며,
  ApplicationContext 쪽에서 “ApplicationContextAwareProcessor” 같은 것이 담당하기도 한다.

면접에서 유용한 말:
- “Aware는 DI라기보다는 컨테이너 인프라를 빈에게 주입하는 통로입니다.”

---

## S3) @Configuration “Full mode”의 의미를 더 정확히

@Configuration이 하는 핵심은 “@Bean 메서드 간 호출”이다.

### S3.1 문제: @Bean 메서드가 그냥 메서드라면?

```java
class Config {
  @Bean B b(){ return new B(); }
  @Bean A a(){ return new A(b()); } // b() 직접 호출
}
```

자바 관점에서 b()는 그냥 메서드 → 호출할 때마다 new B() 가능.

### S3.2 해결: CGLIB로 Config를 서브클래싱

- 스프링은 Config 클래스를 CGLIB로 프록시/서브클래스화해서
- @Bean 메서드 호출을 가로채
- 이미 컨테이너에 B가 있으면 그걸 반환한다.

### S3.3 proxyBeanMethods=false는 무엇을 포기하나?

- 이 옵션을 끄면(=프록시 생성 안 하면) 성능은 좋아질 수 있지만,
- @Bean 메서드 간 직접 호출은 일반 자바 호출로 돌아가 싱글톤 보장이 깨질 수 있다.
- 그래서 “@Bean 간 호출이 없는 config”에서만 안전하다.

면접에서 이 얘기를 꺼내면 “아, 설정 클래스 프록시까지 이해하는구나”가 된다.

---

## S4) AutoProxyCreator가 “어떤 빈을 프록시로 만들지” 결정하는 방식

면접에서 “AOP는 어떻게 적용 대상(포인트컷)을 찾나요?”가 나오면:

- 스프링은 Advisor(=Pointcut + Advice)를 기반으로
- 현재 생성 중인 빈에 대해 “이 Advisor가 매칭되는가?”를 검사한다.
- 하나라도 매칭되면 ProxyFactory로 프록시를 만든다.

그리고 이 동작이 **BeanPostProcessor(afterInitialization)**에서 일어난다는 점이 포인트.

추가로 말할 수 있는 깊이 포인트:
- Advisor는 인프라 빈으로 등록되어 있다.
- AutoProxyCreator는 인프라 Advisor 목록을 캐싱하고,
  각 빈 생성 시점에 매칭을 수행한다.
- 프록시 방식(JDK/CGLIB)은 타깃 타입/설정에 의해 결정된다.

---

## S5) “프록시가 주입되면 타입은 뭐가 되나?” 질문 대응

면접에서 이런 질문이 나올 수 있다:
- “프록시가 주입되면 실제 클래스 타입 체크는 어떻게 되나요?”

대답 포인트:
- JDK 프록시라면 인터페이스 타입으로만 프록시가 만들어지는 경우가 많아,
  구체 클래스 타입 캐스팅은 실패할 수 있다.
- CGLIB 프록시는 타깃 클래스를 상속하므로 구체 클래스 타입으로도 동작할 수 있지만,
  final 제약이 있다.
- 그래서 DI는 보통 “인터페이스 기반” 설계가 안정적이다.

---

## S6) destroy 단계의 정확한 흐름

컨테이너 종료 시(또는 scope 종료 시) 파괴는 대략:

1) DestructionAwareBPP#postProcessBeforeDestruction
2) @PreDestroy
3) DisposableBean#destroy
4) custom destroyMethod

면접 포인트:
- singleton은 컨테이너가 destroy까지 관리
- prototype은 destroy를 컨테이너가 자동 호출하지 않는 경우가 많음

---

## S7) “요청이 들어오면 빈이 생성되나요?”에 대한 정교한 답

면접관이 “요청이 들어오면 스프링은 빈을 생성하나요?”라고 물으면,
단순히 “아니요 싱글톤이라 이미 생성돼요”로 끝내면 아쉽다.

정교한 답 구조:

- 대부분의 싱글톤 빈은 컨텍스트 refresh 시점에 이미 생성될 수 있다(preInstantiateSingletons).
- 하지만 `@Lazy`거나 실제로 사용될 때 생성되는 경우도 있다.
- request scope, session scope 같은 웹 스코프 빈은 “요청/세션 단위로” 생성된다.
- 또한 `ObjectProvider`나 `@Lazy`로 지연 주입된 의존성은 실제 접근 시점에 생성될 수 있다.

---

## S8) Spring Boot와 생명주기 연결 포인트 (면접에서 플러스 점수)

Boot를 쓴다면 보통 컨텍스트 생성 후:

- CommandLineRunner / ApplicationRunner가 실행된다.
- 이들은 “컨텍스트가 준비된 뒤” 실행되므로,
  @PostConstruct보다 더 늦은 시점 초기화 용도로 종종 사용된다.

면접에서:
- “@PostConstruct는 프록시 적용 시점과 엮일 수 있으니,
  컨텍스트 준비 이후 실행되는 Runner나 SmartInitializingSingleton로 초기화를 옮기는 경우도 있다”
같은 말을 하면 설득력이 커진다.

---

# 면접 Q&A 35선 (전체 확장판)

> 팁: 아래 답변은 “암기용 스크립트”가 아니라 “흐름”이다.  
> 면접에서는 **키워드 → 근거(생명주기 단계) → 예시(후처리기/프록시/순환참조)** 순으로 말하면 깊게 들린다.

---
### Q1. BeanDefinition이 뭔가요?

- BeanDefinition은 빈 인스턴스가 아니라 ‘빈을 어떻게 만들지’에 대한 설계도(메타데이터)입니다. 클래스 타입, 스코프, 생성 방식(생성자/팩토리메서드), 프로퍼티 값, init/destroy 메서드 같은 정보를 담고 있고, BeanFactory는 이 설계도를 기반으로 실제 빈을 생성합니다.

### Q2. BeanFactory와 ApplicationContext 차이는요?

- BeanFactory는 getBean을 통한 빈 생성/조회/캐싱의 핵심 엔진이고, ApplicationContext는 그 위에 메시지 소스, 이벤트, 리소스 로딩, 환경/프로파일 등 프레임워크 운영 기능을 얹은 상위 컨테이너입니다.

### Q3. 스프링 컨테이너가 시작할 때 무슨 단계들이 있나요?

- 대표적으로 AbstractApplicationContext#refresh가 핵심인데, BeanFactory 준비 → BeanFactoryPostProcessor 실행(BeanDefinition 후처리) → BeanPostProcessor 등록(빈 인스턴스 후처리 준비) → 싱글톤 미리 생성(preInstantiateSingletons) → refresh 완료 이벤트 발행 순서로 진행됩니다.

### Q4. BeanFactoryPostProcessor는 왜 필요하죠?

- 인스턴스를 만들기 전에 BeanDefinition을 수정/추가할 수 있어야 하기 때문입니다. 예를 들어 @Configuration 파싱으로 BeanDefinition을 등록하거나, ${} placeholder를 실제 값으로 치환하는 등은 인스턴스 생성 전에 설계도 차원에서 처리되어야 합니다.

### Q5. BeanPostProcessor는 왜 필요하죠?

- 스프링의 많은 기능이 ‘빈 인스턴스 생성 과정에 개입’해야 구현되기 때문입니다. @Autowired 주입, @PostConstruct, AOP 프록시(@Transactional) 적용 등이 BeanPostProcessor 체인을 통해 이루어집니다. 특히 afterInitialization에서 원본 빈 대신 프록시를 반환할 수 있다는 점이 핵심입니다.

### Q6. @Autowired는 누가 언제 처리하나요?

- AutowiredAnnotationBeanPostProcessor 같은 InstantiationAwareBeanPostProcessor가 populateBean 단계에서 postProcessProperties 훅으로 개입해, 필드/세터/메서드의 InjectionMetadata를 기반으로 실제 의존성을 주입합니다.

### Q7. @PostConstruct는 스프링이 자동으로 아나요?

- 아니요. @PostConstruct는 자바 표준 애노테이션이지만, 스프링이 이를 호출하는 건 CommonAnnotationBeanPostProcessor 같은 후처리기가 등록되어 있기 때문입니다. 즉 ‘후처리기가 트리거’합니다.

### Q8. 초기화 콜백 순서를 말해보세요.

- 일반적으로 Aware 콜백 → BPP beforeInit → @PostConstruct → InitializingBean.afterPropertiesSet → initMethod → BPP afterInit 순서로 이해하면 됩니다. 다만 BPP 구현이나 설정에 따라 일부 훅의 세부 위치는 달라질 수 있습니다.

### Q9. @Transactional은 어떤 시점에 적용되나요?

- 트랜잭션은 프록시 기반 AOP라서, 보통 BeanPostProcessor(afterInitialization)에서 프록시로 교체되며 적용됩니다. 즉 컨테이너에 최종 등록되는 객체가 원본이 아니라 프록시가 되어, 이후 주입되는 것도 프록시가 됩니다.

### Q10. 왜 self-invocation에서는 @Transactional이 안 먹나요?

- 프록시 기반 AOP는 호출이 프록시를 통해 들어와야 advice가 실행됩니다. 같은 클래스 내부에서 this로 호출하면 프록시를 거치지 않고 타깃 메서드로 직행하므로 트랜잭션 인터셉터가 동작하지 않습니다.

### Q11. AOP 프록시는 JDK와 CGLIB 중 언제 무엇을 쓰나요?

- 일반적으로 인터페이스 기반이면 JDK 동적 프록시를, 인터페이스가 없으면 CGLIB(클래스 상속) 기반 프록시를 쓰는 경우가 많습니다. 다만 설정에 따라 강제될 수 있고, CGLIB는 final class/method 제약이 있습니다.

### Q12. 순환참조는 어떻게 해결되나요?

- 스프링은 싱글톤 생성 시 3단 캐시를 사용합니다. instantiate 직후 singletonFactories에 ObjectFactory를 등록해 조기 참조를 만들 수 있게 하고, 순환참조로 다시 getBean이 들어오면 그 팩토리로 early reference를 만들어 earlySingletonObjects에 두고 주입합니다. 최종 초기화 후 singletonObjects로 승격합니다.

### Q13. 생성자 순환참조는 왜 어렵죠?

- 생성자 주입은 인스턴스를 만들기 전에 의존성이 필요하기 때문에 ‘인스턴스만 먼저 만들고 나중에 주입’하는 조기 노출 전략이 통하지 않는 경우가 많아서입니다.

### Q14. prototype 빈은 컨테이너가 destroy도 해주나요?

- 일반적으로는 생성/주입/초기화까지만 해주고, 파괴는 호출자 책임인 경우가 많습니다. 그래서 prototype 빈이 자원을 들고 있으면 사용자가 명시적으로 정리해야 합니다.

### Q15. request scope 빈을 singleton에 주입하려면?

- scoped proxy를 사용해 프록시를 주입받거나, ObjectProvider로 필요 시점에 request 컨텍스트에서 조회하는 지연 조회 방식으로 해결할 수 있습니다.

### Q16. FactoryBean이 뭔가요?

- FactoryBean은 컨테이너가 특별 취급하는 공장 빈입니다. getBean('name')은 FactoryBean#getObject()가 만든 결과물을 반환하고, getBean('&name')은 FactoryBean 자체를 반환합니다.

### Q17. @Configuration이 CGLIB로 프록시되는 이유는?

- @Bean 메서드 간 직접 호출이 발생해도 싱글톤이 깨지지 않게 하기 위해서입니다. 프록시가 @Bean 메서드 호출을 가로채 컨테이너에서 해당 빈을 반환하도록 만듭니다.

### Q18. 빈 후처리기 순서는 어떻게 결정되나요?

- 여러 후처리기가 있으면 PriorityOrdered → Ordered → 나머지 순으로 정렬되는 것이 일반적입니다. 이 순서는 AOP 프록시 적용이나 애노테이션 처리 순서에 영향을 줄 수 있습니다.

### Q19. 어떤 시점에 대부분의 싱글톤이 만들어지나요?

- 컨텍스트 refresh의 finishBeanFactoryInitialization 단계에서 preInstantiateSingletons가 수행되며, non-lazy singleton들이 이때 생성되는 경우가 많습니다. 하지만 @Lazy면 실제 사용 시점까지 미뤄질 수 있습니다.

### Q20. DispatcherServlet이 하는 일은?

- Spring MVC의 프론트 컨트롤러로서, 요청을 받아 HandlerMapping으로 컨트롤러를 찾고, HandlerAdapter로 호출한 뒤, 반환값을 메시지 컨버터나 뷰 리졸버로 처리합니다. 인터셉터와 예외 리졸버 체인도 여기서 관리됩니다.


---

## 마지막 팁: “깊게 아는 사람”처럼 들리게 만드는 문장 패턴 6개

면접에서 아래 문장 패턴을 적절히 섞으면, 단순 지식 나열이 아니라 “이해 기반 설명”으로 들린다.

1) “이 기능은 사실 X 인터페이스/후처리기가 담당합니다.”  
2) “이게 가능한 이유는 빈 생성 과정이 Y 단계로 분리되어 있고, Z 훅이 있기 때문입니다.”  
3) “대부분은 afterInitialization에서 프록시가 붙지만, 순환참조에서는 early reference 단계에서 프록시가 필요할 수 있습니다.”  
4) “설계도 단계(BeanDefinition)와 인스턴스 단계(Bean instance)를 구분하면 이해가 쉽습니다.”  
5) “singleton/prototype/request 스코프는 ‘생성 시점과 파괴 책임’이 다릅니다.”  
6) “이건 프록시 기반 AOP의 제약이라 self-invocation에서 자주 문제가 됩니다.”

---

# 끝

원하면, 다음 확장을 더 추가할 수 있다(특정 회사 면접 스타일에 따라):

- “스프링이 빈을 생성하는 정확한 코드 경로”를 디버깅 스냅샷처럼 단계별로 캡처한 설명
- Spring Security 필터 체인과 MVC 흐름 결합 설명(실무 면접 단골)
- 트랜잭션 전파/격리/롤백 규칙까지 포함한 @Transactional 심화(빈 생명주기 관점 포함)
- Boot 자동설정이 어떤 BFPP/ImportSelector/Registrar로 빈을 등록하는지 연결 설명

# 추가 심화: Ordering, 프로세서 등록 메커니즘, 그리고 “왜 어떤 BPP는 안 도나요?”

이 챕터는 면접에서 “후처리기/설정이 왜 적용이 안 되죠?” 같은 실무형 질문이 나올 때를 대비한다.

---

## O1) 왜 어떤 BeanPostProcessor는 특정 빈에 적용이 안 되나?

대표 원인 5가지:

1) **후처리기 자체가 너무 늦게 등록됨**
- BPP는 “빈 생성 과정에 끼어들어야” 한다.
- 어떤 빈이 BPP 등록 전에 만들어져 버리면, 그 빈에는 그 BPP가 적용되지 않는다.

2) **후처리기가 처리할 타입/조건이 아님**
- AutoProxyCreator는 “Advisor 매칭”이 되는 빈만 프록시로 만든다.
- 매칭 조건이 안 맞으면 그냥 통과.

3) **프레임워크 내부에서 특별 취급되는 빈**
- 인프라 빈들 중 일부는 특정 시점에 먼저 만들어지고,
  그 시점에서는 아직 어떤 BPP가 준비되지 않았을 수 있다.

4) **최종 객체가 프록시로 바뀌어 타입/애노테이션이 다르게 보임**
- 프록시는 애노테이션이 “타깃”에 있고 “프록시 클래스”에는 없을 수 있다.
- 그래서 애노테이션 스캐닝 방식/타깃 클래스 추출 방식이 중요해진다.

5) **@Lazy / ObjectProvider 등으로 생성 시점이 바뀜**
- 어떤 빈은 컨텍스트 시작 시가 아니라, 실제 사용 시점에 만들어져서
  적용되는 후처리기 체인이 달라지는 것처럼 느껴질 수 있다.

면접에서 “어떤 기능이 적용 안 되는 이유”를 이런 식으로 진단하면 실무 감각이 좋아 보인다.

---

## O2) Processor들은 자기 자신도 ‘빈’인데, 그건 어떻게 초기화되나?

BFPP/BPP도 결국은 빈으로 등록되는 경우가 많다.

- refresh 과정에서 스프링은
  - BFPP들을 먼저 “찾아서” 실행하고
  - BPP들도 먼저 “찾아서” 등록한다.

즉, 일반 빈들과 달리, 프로세서 빈들은 “컨테이너 부팅을 위해 특별한 경로로” 먼저 만들어질 수 있다.

면접 포인트:
- “프로세서는 컨테이너를 만드는 도구라서, 부팅 과정에서 우선적으로 취급됩니다.”

---

## O3) Ordered/priority가 실제로 왜 중요한가?

예를 들어:

- 어떤 BPP가 @Autowired를 처리하기 전에
- 다른 BPP가 특정 필드 값을 바꿔야 한다면?

순서가 뒤집히면 원하는 결과가 나오지 않는다.

AOP도 마찬가지:
- 프록시를 만든 다음에 다른 BPP가 빈을 수정하려 하면
  수정 대상이 프록시가 될지 타깃이 될지 복잡해진다.

면접에서는 “순서가 중요할 때 발생 가능한 문제”를 예로 들어주면 좋다.

---

## O4) 한 줄 요약(면접용)

- “BFPP는 설계도를 바꾸고, BPP는 객체를 바꾸며, AOP는 BPP에서 프록시로 객체를 교체한다.”
- “순환참조는 3단 캐시와 early reference로 해결하지만, 생성자 순환참조는 어렵다.”
- “스코프가 다르면 생명주기/파괴 책임이 달라지고, scoped proxy가 그 간극을 메운다.”
