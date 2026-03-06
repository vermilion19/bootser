# Spring 프록시 적용 원리와 self-invocation 문제 정리

> 기준  
> - 설명의 기준은 **Spring Framework 공식 reference + current Javadoc(현재 공개 문서 기준)** 이다.  
> - 핵심 원리는 Spring 5.x / 6.x / 7.x에서 거의 동일하다.  
> - 이 문서는 특히 **“스프링에서 프록시가 어떻게 붙는가”**, **“왜 AOP self-invocation이 문제인가”**, **“실무에서 어떻게 설계/회피할 것인가”**에 초점을 둔다.

---

## 목차

1. [한 줄 요약](#1-한-줄-요약)
2. [왜 스프링은 프록시를 쓰는가](#2-왜-스프링은-프록시를-쓰는가)
3. [Spring AOP가 실제로 할 수 있는 범위](#3-spring-aop가-실제로-할-수-있는-범위)
4. [스프링에서 프록시가 적용되는 큰 흐름](#4-스프링에서-프록시가-적용되는-큰-흐름)
5. [@EnableAspectJAutoProxy가 실제로 하는 일](#5-enableaspectjautoproxy가-실제로-하는-일)
6. [@EnableTransactionManagement가 실제로 하는 일](#6-enabletransactionmanagement가-실제로-하는-일)
7. [빈 생성 생명주기에서 프록시는 “언제” 붙는가](#7-빈-생성-생명주기에서-프록시는-언제-붙는가)
8. [AutoProxyCreator가 내부에서 하는 일](#8-autoproxycreator가-내부에서-하는-일)
9. [Advisor / Advice / Interceptor / ProxyFactory 관계](#9-advisor--advice--interceptor--proxyfactory-관계)
10. [JDK 동적 프록시 vs CGLIB 프록시](#10-jdk-동적-프록시-vs-cglib-프록시)
11. [실제 호출 흐름 ASCII 아트](#11-실제-호출-흐름-ascii-아트)
12. [self-invocation이 왜 문제인가](#12-self-invocation이-왜-문제인가)
13. [@Transactional에서 특히 왜 자주 터지는가](#13-transactional에서-특히-왜-자주-터지는가)
14. [같은 문제가 @Async / @Cacheable에도 생기는 이유](#14-같은-문제가-async--cacheable에도-생기는-이유)
15. [self-invocation 해결 방법들](#15-self-invocation-해결-방법들)
16. [AspectJ mode는 무엇이 다른가](#16-aspectj-mode는-무엇이-다른가)
17. [실무에서 추천하는 설계 원칙](#17-실무에서-추천하는-설계-원칙)
18. [디버깅 포인트](#18-디버깅-포인트)
19. [면접에서 이렇게 설명하면 좋다](#19-면접에서-이렇게-설명하면-좋다)
20. [참고한 공식 문서](#20-참고한-공식-문서)

---

## 1) 한 줄 요약

스프링의 AOP는 기본적으로 **런타임 프록시 기반**이다.  
즉, 타깃 객체를 직접 바꾸는 것이 아니라 **타깃 앞에 프록시 객체를 세워서 메서드 호출을 가로채고**, 그 사이에 트랜잭션/보안/캐시/비동기 같은 부가 기능을 끼워 넣는다.

그래서 중요한 사실이 2개 생긴다.

1. **프록시를 통해 들어온 호출만 가로챌 수 있다.**
2. 그래서 **같은 객체 내부에서 `this.xxx()`로 호출하는 self-invocation은 프록시를 우회한다.**

이 두 줄이 `@Transactional`, `@Async`, `@Cacheable`의 동작과 함정을 거의 다 설명한다.

---

## 2) 왜 스프링은 프록시를 쓰는가

AOP(Aspect-Oriented Programming)를 쓰는 가장 큰 목적은 **핵심 비즈니스 로직과 횡단 관심사(cross-cutting concern)를 분리**하는 것이다.

대표적인 횡단 관심사:
- 트랜잭션 시작 / 커밋 / 롤백
- 권한 검사
- 로깅
- 성능 측정
- 캐시 조회 / 저장
- 비동기 실행

예를 들어 서비스 메서드마다 아래 코드가 반복된다고 생각해보자.

```java
tx.begin();
try {
    businessLogic();
    tx.commit();
} catch (Exception e) {
    tx.rollback();
    throw e;
}
```

이걸 비즈니스 메서드마다 직접 넣으면 코드가 오염된다.  
스프링은 이 문제를 **프록시가 호출을 가로채는 방식**으로 해결한다.

개념적으로는 이런 그림이다.

```text
caller
  |
  v
[proxy] --- before advice --> 트랜잭션 시작
  |
  v
[target] --- 실제 비즈니스 메서드 실행
  |
  v
[proxy] --- after advice --> 커밋/롤백
```

---

## 3) Spring AOP가 실제로 할 수 있는 범위

이 부분을 먼저 정확히 알아야 “왜 self-invocation이 안 되는지”가 이해된다.

Spring AOP는 **스프링 빈에 대한 메서드 실행(method execution join point)** 만 지원한다.  
즉, 프록시 방식으로 **빈의 메서드 호출**을 감쌀 수는 있지만:

- 필드 접근
- 생성자 호출
- static 초기화
- 일반 객체(`new`로 직접 만든 객체)의 메서드 호출

같은 것까지 전부 다루는 방식은 아니다.

실무적으로 기억할 포인트:

- **Spring AOP = 프록시 기반 = 메서드 호출 interception**
- **AspectJ weaving = 바이트코드 weaving = 프록시보다 더 넓은 join point 가능**

즉, Spring AOP는 단순하고 실용적이지만, 그 대신 **프록시를 통과한 호출만 잡는다**는 제약이 있다.

---

## 4) 스프링에서 프록시가 적용되는 큰 흐름

여기서부터가 진짜 핵심이다.

스프링이 프록시를 붙이는 흐름을 아주 크게 보면 이렇다.

```text
@Configuration / @Enable... / @Aspect / @Transactional
        |
        v
(1) 관련 인프라 BeanDefinition 등록
        |
        v
(2) AutoProxyCreator 같은 BeanPostProcessor 등록
        |
        v
(3) 일반 빈 생성
        |
        v
(4) 빈 초기화 후, 이 빈에 적용할 Advisor가 있는지 검사
        |
        +-- 없으면 원본 빈 그대로 사용
        |
        +-- 있으면 프록시 생성 후 원본 대신 프록시를 컨테이너에 등록
```

즉, “스프링이 프록시를 붙인다”는 말은 실제로는:

- 먼저 **프록시를 만들 수 있는 인프라 빈**을 등록하고
- 그 다음 **BeanPostProcessor가 빈 생성 과정에 개입**해서
- 필요한 빈만 골라 **원본 대신 프록시를 반환**하는 것이다.

여기서 제일 중요한 역할을 하는 축이:

- `AutoProxyRegistrar`
- `AbstractAutoProxyCreator`
- `AbstractAdvisorAutoProxyCreator`
- `AnnotationAwareAspectJAutoProxyCreator`
- `ProxyTransactionManagementConfiguration`
- `TransactionInterceptor`

같은 클래스들이다.

---

## 5) `@EnableAspectJAutoProxy`가 실제로 하는 일

`@EnableAspectJAutoProxy`는 “이제부터 @Aspect 기반 AOP를 켜겠다”는 선언이다.

공식 Javadoc 기준으로 이 애노테이션은 `AspectJAutoProxyRegistrar`를 import한다.  
그리고 `AspectJAutoProxyRegistrar`는 현재 `BeanDefinitionRegistry`에 `AnnotationAwareAspectJAutoProxyCreator`를 등록한다.

즉, 요약하면:

```text
@EnableAspectJAutoProxy
   |
   +--> AspectJAutoProxyRegistrar
            |
            +--> AnnotationAwareAspectJAutoProxyCreator 등록
```

### 5.1 `AnnotationAwareAspectJAutoProxyCreator`는 뭔가

이 클래스는 이름 그대로:

- `@Aspect`가 붙은 빈들을 인식하고
- 그 안의 advice / pointcut 정보를 읽어서
- 일반 빈 생성 시점에 “이 빈을 프록시로 감싸야 하는가?”를 판단하는 역할을 한다.

공식 Javadoc 설명도 이 클래스가
- 현재 애플리케이션 컨텍스트의 `@Aspect` 빈과 Spring `Advisor`들을 처리하고
- Spring AOP의 프록시 모델이 적용 가능한 경우 advice를 적용한다고 설명한다.

즉, `@Aspect`를 그냥 읽는 게 아니라,
**“Advisor로 변환해서 AutoProxyCreator가 적용 가능한 빈에 붙인다”**고 이해하면 된다.

### 5.2 `proxyTargetClass`와 `exposeProxy`

`@EnableAspectJAutoProxy`에는 대표적으로 두 옵션이 있다.

```java
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = true
)
```

- `proxyTargetClass = true`
  - 인터페이스 기반 JDK 프록시 대신 **클래스 기반 CGLIB 프록시**를 사용하게 만든다.
- `exposeProxy = true`
  - 현재 프록시를 `AopContext.currentProxy()`로 꺼낼 수 있게 한다.
  - 기본값은 false다.

실무 포인트:
- `exposeProxy = true`는 **self-invocation 우회용 응급처치**에 가깝다.
- 설계적으로는 남용하지 않는 것이 좋다.

---

## 6) `@EnableTransactionManagement`가 실제로 하는 일

`@Transactional`의 내부 원리를 이해하려면 `@EnableTransactionManagement`의 import 구조를 봐야 한다.

공식 current 소스/Javadoc 기준으로 `TransactionManagementConfigurationSelector`는
`EnableTransactionManagement.mode()` 값에 따라 import할 구성을 선택한다.

`PROXY` 모드(기본값)에서는 다음 2개를 import한다.

```text
AutoProxyRegistrar
ProxyTransactionManagementConfiguration
```

즉:

```text
@EnableTransactionManagement
   |
   +--> TransactionManagementConfigurationSelector
            |
            +-- mode == PROXY
            |      +--> AutoProxyRegistrar
            |      +--> ProxyTransactionManagementConfiguration
            |
            +-- mode == ASPECTJ
                   +--> AspectJ transaction configuration
```

### 6.1 `AutoProxyRegistrar`는 무슨 역할인가

이 클래스의 Javadoc 설명은 아주 직설적이다.

- `mode` 와 `proxyTargetClass` 속성을 가진 `@Enable*` 애노테이션을 보고
- 적절한 **auto proxy creator**를 `BeanDefinitionRegistry`에 등록한다.

즉, “프록시를 만들 BeanPostProcessor 계열”을 컨테이너에 심는 역할이다.

### 6.2 `ProxyTransactionManagementConfiguration`은 무슨 역할인가

이 클래스는 Javadoc에 따르면
**proxy-based annotation-driven transaction management에 필요한 Spring 인프라 빈들을 등록**하는 `@Configuration`이다.

그리고 여기에는 대표적으로 다음 빈들이 등록된다.

- `transactionAdvisor(...)`
  - 실제로는 `BeanFactoryTransactionAttributeSourceAdvisor`
- `transactionInterceptor(...)`
  - 실제로는 `TransactionInterceptor`
- `transactionAttributeSource()`
  - 어떤 메서드가 transactional 인지 해석하는 source

즉, 트랜잭션 AOP의 핵심은 다음처럼 생각하면 된다.

```text
@Transactional metadata
       |
       v
TransactionAttributeSource
       |
       v
BeanFactoryTransactionAttributeSourceAdvisor
       |
       v
TransactionInterceptor
       |
       v
AutoProxyCreator가 이 Advisor를 적용해서 프록시 생성
```

### 6.3 `TransactionInterceptor`는 뭘 하나

공식 Javadoc package summary는 `TransactionInterceptor`를
**transactional advice를 제공하는 AOP Alliance `MethodInterceptor`** 라고 설명한다.

즉, 호출을 가로채서 대략 이런 흐름을 수행한다.

```java
beginTransactionIfNecessary();
try {
    Object result = invocation.proceed();
    commit();
    return result;
} catch (Throwable ex) {
    rollbackOn(ex);
    throw ex;
}
```

실제로는 전파/격리/읽기전용/rollback rule 같은 옵션을 더 고려하지만,
구조적으로는 정확히 **“프록시 -> 인터셉터 -> 타깃”** 모델이다.

---

## 7) 빈 생성 생명주기에서 프록시는 “언제” 붙는가

프록시는 보통 빈 생성 과정 중 **BeanPostProcessor 체인**에서 붙는다.

대표적인 큰 흐름은 다음과 같다.

```text
instantiate  ->  dependency injection  ->  initialization  ->  postProcessAfterInitialization
                                                        |
                                                        +--> 여기서 프록시로 교체되는 경우가 많음
```

`BeanPostProcessor` Javadoc도 일반적으로:

- beforeInitialization 에서는 각종 marker / 초기화 관련 처리를 할 수 있고
- afterInitialization 에서는 **프록시로 감싸는(wrapping)** 처리를 보통 수행한다고 설명한다.

즉, 스프링이 빈을 만들고 난 뒤
**최종 반환 직전에 원본 대신 프록시를 돌려주는 것**이 프록시 기반 AOP의 핵심 패턴이다.

---

## 8) AutoProxyCreator가 내부에서 하는 일

이제 한 단계 더 깊게 보자.

스프링 AOP의 핵심 클래스 축은 `AbstractAutoProxyCreator` 계열이다.  
이 클래스는 단순 BeanPostProcessor가 아니라 `SmartInstantiationAwareBeanPostProcessor` 계열로 동작한다.

### 8.1 왜 그냥 BPP가 아니라 SmartInstantiationAwareBPP인가

공식 Javadoc 기준으로 `SmartInstantiationAwareBeanPostProcessor`에는 다음 같은 훅들이 있다.

- 최종 타입 예측
- 생성자 후보 결정
- `getEarlyBeanReference(...)`

그리고 `AbstractAutoProxyCreator`는 이 훅들 중 일부를 활용한다.

특히 중요한 메서드:
- `postProcessBeforeInstantiation(...)`
- `postProcessAfterInitialization(...)`
- `getEarlyBeanReference(...)`
- `wrapIfNecessary(...)`

### 8.2 `postProcessAfterInitialization(...)`

`AbstractAutoProxyCreator` Javadoc은 이 메서드에 대해
**해당 빈이 프록시 대상이면 configured interceptors 로 프록시를 만든다**고 설명한다.

즉, 일반적인 프록시 적용의 가장 핵심 시점은 여기다.

개념적으로는:

```java
Object postProcessAfterInitialization(Object bean, String beanName) {
    if (isInfrastructureClass(bean.getClass())) {
        return bean;
    }
    if (shouldSkip(bean.getClass(), beanName)) {
        return bean;
    }
    return wrapIfNecessary(bean, beanName, cacheKey);
}
```

### 8.3 `wrapIfNecessary(...)`

이 메서드는 이름 그대로:
- 이 빈이 프록시 대상인지 검사하고
- 맞으면 Advisor/Advice를 조합해 프록시를 만들고
- 아니면 원본 빈을 그대로 반환한다.

Javadoc도 “eligible for being proxied” 인지 판단해서 프록시로 감싼다고 설명한다.

### 8.4 `isInfrastructureClass(...)`

이 부분도 실무에서 중요하다.

`AbstractAutoProxyCreator`는 기본적으로
다음 같은 인프라 클래스를 프록시 대상에서 제외한다.

- `Advice`
- `Advisor`
- `AopInfrastructureBean`

왜냐하면 AOP를 구현하는 인프라 자체를 또 AOP로 감싸기 시작하면
무한 루프나 복잡한 부작용이 생길 수 있기 때문이다.

### 8.5 `getEarlyBeanReference(...)`

이건 순환 참조(circular dependency)와 엮일 때 중요하다.

`AbstractAutoProxyCreator`는 `getEarlyBeanReference(...)`도 가진다.  
Javadoc 설명대로, 기본 구현은 raw bean을 그대로 돌려주지만,
AOP 프록시가 필요한 상황에서는 **완전 초기화 전의 “early proxy reference”** 를 노출해야 할 수도 있다.

즉, 순환 참조 상황에서:
- 어떤 빈이 아직 완전히 초기화되기 전에
- 다른 빈이 그 빈을 참조해야 하고
- 그 참조가 최종적으로 프록시여야 한다면

이 early reference 훅이 관여한다.

실무에선 이걸 자주 직접 건드리진 않지만,
“왜 어떤 경우 프록시가 조금 더 이른 시점에 보이느냐”를 설명할 때 아주 중요하다.

---

## 9) Advisor / Advice / Interceptor / ProxyFactory 관계

용어를 분리해서 이해하면 내부 구조가 훨씬 선명해진다.

### 9.1 Advice
실행하고 싶은 부가 로직 그 자체다.

예:
- before 로깅
- after returning 로깅
- around 트랜잭션 처리
- 예외 처리

### 9.2 Pointcut
어떤 메서드에 적용할지 정하는 규칙이다.

예:
```java
execution(* com.example..*Service.*(..))
```

### 9.3 Advisor
**Pointcut + Advice** 의 결합체라고 보면 된다.  
“어디에, 무엇을 적용할지”를 하나의 단위로 묶은 것.

### 9.4 MethodInterceptor
AOP Alliance 스타일에서 호출을 감싸는 around interceptor 형태다.  
`TransactionInterceptor`가 대표 예시다.

### 9.5 ProxyFactory / AopProxyFactory
실제로 프록시 객체를 만들어내는 쪽이다.

공식 문서상 `ProxyFactory`는 programmatic proxy creation의 기본 진입점이다.  
그리고 내부적으로는 `AopProxyFactory` / `DefaultAopProxyFactory`가
JDK proxy 또는 CGLIB proxy를 만든다.

즉 개념도는 대략 이렇게 보면 된다.

```text
Aspect / @Transactional metadata
        |
        v
Advice + Pointcut
        |
        v
Advisor
        |
        v
AutoProxyCreator가 현재 bean에 적용할 Advisor 목록 계산
        |
        v
ProxyFactory / DefaultAopProxyFactory
        |
        +--> JDK dynamic proxy
        \--> CGLIB proxy
```

---

## 10) JDK 동적 프록시 vs CGLIB 프록시

이건 면접에서도 아주 자주 묻는다.

공식 `DefaultAopProxyFactory` Javadoc 기준:

- 다음 중 하나라도 참이면 CGLIB 프록시를 만든다.
  - `optimize` 플래그가 true
  - `proxyTargetClass` 플래그가 true
  - 프록시 인터페이스가 지정되지 않음
- 그 외에는 보통 JDK dynamic proxy를 사용한다.

### 10.1 JDK dynamic proxy

특징:
- **인터페이스 기반**
- 인터페이스 메서드 호출을 프록시한다
- 프록시 클래스는 보통 `$Proxy...` 형태

장점:
- 구조가 단순
- 자바 표준 기능

주의:
- 인터페이스에 정의되지 않은 구체 클래스 메서드는 다루기 불편하다

### 10.2 CGLIB proxy

특징:
- **클래스 상속 기반**
- 타깃 클래스를 상속한 런타임 서브클래스를 만든다
- 프록시 클래스 이름에 `$$SpringCGLIB$$` 같은 흔적이 보일 수 있다

장점:
- 인터페이스가 없어도 프록시 가능
- 클래스 기반 프록시라 타깃 클래스 전체를 더 자연스럽게 감싼다

주의:
- `final class`는 프록시 불가
- `final method`는 advice 불가
- `private method`는 advice 불가
- 다른 패키지의 상위 클래스 package-private 메서드도 사실상 private 취급되어 advice 불가
- Java Module System 제약도 있을 수 있다

공식 문서의 핵심은 아주 단순하다.

> CGLIB는 “상속해서 override” 하는 방식이므로, **상속/override가 불가능한 대상은 못 건드린다.**

### 10.3 실무 팁

실무에서는 “기본이 뭐였지?”를 외우는 것보다
**필요하면 명시적으로 결정**하는 게 낫다.

예:
```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableTransactionManagement(proxyTargetClass = true)
```

또는 XML/설정 속성으로 명시.

그리고 문서가 말하듯 여러 auto-proxy 설정이 동시에 있으면
**하나의 통합 auto-proxy creator로 합쳐지고, strongest proxy settings 가 적용**된다.

즉, 프로젝트 어딘가에서 class-based proxy를 강제하면
다른 AOP 기능에도 영향이 갈 수 있다.

---

## 11) 실제 호출 흐름 ASCII 아트

### 11.1 외부 호출이 프록시를 통과하는 정상 케이스

```text
[Controller / Caller]
        |
        v
   userService  ----->  사실 이 참조는 "target"이 아니라 "proxy"
        |
        v
+---------------------------+
|         Proxy             |
|  - Advisor chain lookup   |
|  - Interceptor invocation |
+---------------------------+
        |
        +--> [TransactionInterceptor]  begin tx
        |
        +--> [LoggingInterceptor]      log before
        |
        v
+---------------------------+
|      Target Object        |
|   UserServiceImpl         |
|   createUser()            |
+---------------------------+
        |
        +--> [LoggingInterceptor]      log after
        |
        +--> [TransactionInterceptor]  commit / rollback
        |
        v
      return
```

### 11.2 self-invocation이 프록시를 우회하는 케이스

```text
외부에서 호출:
Caller --> Proxy --> Target.outer()

Target.outer() 내부:
this.inner()   // 여기서 this 는 "target 자신"
       |
       +--> Proxy 를 다시 거치지 않음
       +--> Interceptor 체인 미실행
       +--> @Transactional / @Async / @Cacheable 무시될 수 있음
```

### 11.3 그림으로 더 직관적으로

```text
정상 호출
========
client
  |
  v
proxy.foo()
  |
  +--> advice 1
  +--> advice 2
  +--> target.foo()

self-invocation
===============
client
  |
  v
proxy.outer()
  |
  +--> advice
  +--> target.outer()
          |
          +--> this.inner()   // target -> target 직통
                  |
                  +--> proxy 미통과
                  +--> advice 없음
```

---

## 12) self-invocation이 왜 문제인가

이제 본질을 아주 정확히 말해보자.

### 12.1 self-invocation의 정확한 정의

**같은 객체 내부에서 자기 자신의 다른 메서드를 직접 호출하는 것**이다.

예:
```java
@Service
public class OrderService {

    public void placeOrder() {
        validate();
        saveOrder();   // self-invocation
    }

    @Transactional
    public void saveOrder() {
        // DB 저장
    }

    private void validate() {
        // 검증
    }
}
```

여기서 `placeOrder()` 안의 `saveOrder()` 호출은 사실상 `this.saveOrder()` 이다.

### 12.2 왜 문제가 되는가

스프링 AOP는 프록시가 메서드 호출을 가로채는 방식이다.  
그런데 `this.saveOrder()`는 **프록시가 아니라 타깃 객체 자신에게 직접 호출**한다.

즉:

- 외부에서 `orderService.placeOrder()` 호출  
  -> 프록시를 통과함
- `placeOrder()` 내부에서 `this.saveOrder()` 호출  
  -> 이미 타깃 안으로 들어온 뒤의 내부 호출  
  -> 프록시를 다시 거치지 않음

따라서 `saveOrder()`에 붙어 있던 `@Transactional` advice는 실행되지 않는다.

공식 transaction docs도 명확히 말한다.  
기본 `proxy` 모드에서는 **프록시를 통해 들어온 external method call만 intercept** 되며,
**self-invocation은 실제 트랜잭션을 만들지 않는다**.

### 12.3 “이미 outer()가 프록시를 탔는데 inner()도 당연히 타는 거 아닌가요?”

많이 헷갈리는 포인트다.

답은 **아니다**.

outer()를 프록시가 감싼 것은 “외부 -> 프록시 -> 타깃.outer()” 까지다.  
그 이후 타깃 내부에서 일어나는 `this.inner()`는 프록시의 제어 밖이다.

즉, 프록시가 outer() 진입만 가로챘다고 해서
타깃 내부의 모든 self-call이 자동으로 다시 프록시를 타는 것은 아니다.

---

## 13) `@Transactional`에서 특히 왜 자주 터지는가

트랜잭션은 가장 자주 쓰이는 AOP 기능이라 self-invocation 이슈도 가장 많이 만난다.

### 13.1 대표적인 오해

```java
@Service
public class PaymentService {

    public void checkout() {
        // 여러 준비 작업
        charge(); // @Transactional 기대
    }

    @Transactional
    public void charge() {
        // 결제 + DB 저장
    }
}
```

개발자는 종종 이렇게 생각한다.

- checkout()는 프록시를 통해 호출됨
- charge()에 @Transactional이 있으니 내부 호출이어도 트랜잭션이 걸릴 것

하지만 실제론:
- checkout() 호출 시점에만 프록시가 개입
- 그 안에서 `this.charge()`는 직통 호출
- `TransactionInterceptor`가 개입하지 못함

결과:
- charge()는 **트랜잭션 없이 실행될 수 있음**

### 13.2 트랜잭션이 실제로 적용되는 구조

```text
client
  |
  v
transactional proxy
  |
  +--> TransactionInterceptor
  |      - TransactionAttributeSource로 @Transactional 메타데이터 조회
  |      - transaction manager 선택
  |      - begin / commit / rollback
  |
  v
target method
```

이 구조를 거쳐야 트랜잭션이 생긴다.  
self-invocation은 이 구조를 건너뛴다.

### 13.3 메서드 가시성(visibility)까지 얽히면 더 헷갈린다

현재 공식 문서는 다음 정도로 정리할 수 있다.

- `@Transactional`은 보통 **public 메서드** 기준으로 설계하는 게 가장 안전하다.
- Spring 6.0 이후에는 **class-based proxy(CGLIB)** 에서 protected / package-visible 메서드도 transactional 처리될 수 있다.
- 하지만 **interface-based proxy(JDK)** 에서는 여전히 **public + 인터페이스에 정의된 메서드**가 기준이다.
- 그리고 두 경우 모두 **external call through proxy** 조건은 여전히 그대로다.

즉:
- visibility 조건이 좀 완화되더라도
- **self-invocation 문제는 그대로 남아 있다**

이걸 따로 구분해서 이해해야 한다.

---

## 14) 같은 문제가 `@Async` / `@Cacheable`에도 생기는 이유

self-invocation 문제가 트랜잭션에만 있는 게 아니다.

공식 문서 기준으로:
- `@Async` 기본 advice mode 역시 `proxy`
- `@Cacheable` 기본 advice mode 역시 `proxy`

따라서 두 기능도 동일하게:
- 프록시를 통해 들어온 외부 호출만 intercept 가능
- 같은 클래스 내부 local call은 intercept 불가

### 14.1 `@Async` 예시

```java
@Service
public class MailService {

    public void registerUser() {
        sendWelcomeMail(); // self-invocation
    }

    @Async
    public void sendWelcomeMail() {
        // 비동기 실행 기대
    }
}
```

실제 결과:
- 비동기 스레드로 안 빠지고 그냥 동기 실행될 수 있다.

### 14.2 `@Cacheable` 예시

```java
@Service
public class BookService {

    public Book getBookAndCheck(String isbn) {
        return findBook(isbn); // self-invocation
    }

    @Cacheable("books")
    public Book findBook(String isbn) {
        return repository.findByIsbn(isbn);
    }
}
```

실제 결과:
- 캐시 interceptor가 안 타서 매번 DB를 칠 수 있다.

즉, **“AOP 기능이 안 먹는다”** 는 말은 대부분
결국 **“프록시를 안 탔다”** 로 번역된다.

---

## 15) self-invocation 해결 방법들

공식 문서와 실무 관점에서 정리하면 우선순위는 대체로 이렇다.

### 15.1 가장 권장: 구조 분리(리팩터링)

공식 docs도 self-invocation을 피하는 것이 best / least-invasive 라고 안내한다.

예:

```java
@Service
public class OrderFacade {

    private final OrderTxService orderTxService;

    public OrderFacade(OrderTxService orderTxService) {
        this.orderTxService = orderTxService;
    }

    public void placeOrder() {
        // 사전 검증, orchestration
        orderTxService.saveOrder();  // 외부 호출 -> 프록시 적용
    }
}

@Service
public class OrderTxService {

    @Transactional
    public void saveOrder() {
        // DB 처리
    }
}
```

장점:
- 의도가 명확하다
- AOP가 자연스럽게 먹는다
- 테스트하기 쉽다
- self-invocation 설명이 필요 없는 구조가 된다

실무적으로 가장 추천하는 방식이다.

---

### 15.2 self injection

공식 문서가 언급하는 두 번째 방법은 **self reference 주입**이다.

즉, 자기 자신을 직접 `this`로 부르는 대신,
컨테이너가 주입해 준 **프록시 자신**을 통해 호출한다.

예:

```java
@Service
public class UserService {

    private final UserService self;

    public UserService(@Lazy UserService self) {
        this.self = self;
    }

    public void signup() {
        self.saveUser(); // 프록시를 통해 호출
    }

    @Transactional
    public void saveUser() {
        // 저장
    }
}
```

주의:
- 자기 자신 주입은 설계상 다소 어색하다
- 순환 참조 감각이 있으므로 `@Lazy` 또는 `ObjectProvider<UserService>`를 함께 쓰는 편이 안전하다
- “왜 자기 자신을 주입하지?”라는 코드 독해 비용이 있다

즉, 가능은 하지만 **리팩터링보다 덜 우아한 차선책**이다.

---

### 15.3 `AopContext.currentProxy()`

공식 문서가 언급하는 세 번째 방법이다.

전제:
- `exposeProxy = true` 로 프록시 노출 허용
- 현재 실행 컨텍스트가 실제 AOP invocation 안이어야 함

예:

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
public class AopConfig {
}
```

```java
@Service
public class BillingService {

    public void checkout() {
        ((BillingService) AopContext.currentProxy()).charge();
    }

    @Transactional
    public void charge() {
        // 결제
    }
}
```

장점:
- 동작은 확실하다

단점:
- 코드가 Spring AOP에 강하게 결합된다
- 현재 프록시가 노출되어 있어야 함
- 공식 Javadoc도 프록시 노출은 기본값이 아니고 비용이 있다고 설명한다

따라서 실무에서는 대체로 **“마지막 수단”** 에 가깝다.

---

### 15.4 AspectJ mode 사용

`@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)` 같은 방식으로
프록시 기반이 아니라 **AspectJ weaving 기반**으로 바꾸는 방법이다.

예:

```java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
public class TxConfig {
}
```

이 방식은 프록시 바깥에 객체를 세워 가로채는 게 아니라,
**대상 클래스 자체의 바이트코드에 aspect를 weaving** 한다.

그래서 공식 문서도 말하듯:
- proxy mode는 local/self call을 intercept 못하지만
- aspectj mode는 compile-time weaving 또는 load-time weaving과 함께 사용하면
  이런 local call 제약을 넘을 수 있다

장점:
- self-invocation 문제를 근본적으로 피할 수 있다
- 프록시 모델의 제약이 줄어든다

단점:
- 빌드/런타임 구성 복잡도가 올라간다
- 팀 전체 이해도가 필요하다
- 운영/디버깅 난이도가 올라갈 수 있다

실무에서는 대부분 proxy mode를 유지하고 구조를 리팩터링한다.  
AspectJ mode는 정말 필요한 경우에만 선택하는 편이 많다.

---

### 15.5 아예 AOP 대신 명시적 API 사용

예를 들어 트랜잭션이라면 `TransactionTemplate` 를 써서
경계를 코드로 명시할 수도 있다.

```java
@Service
public class BatchService {

    private final TransactionTemplate txTemplate;

    public BatchService(TransactionTemplate txTemplate) {
        this.txTemplate = txTemplate;
    }

    public void process() {
        txTemplate.executeWithoutResult(status -> {
            // 명시적 트랜잭션 경계
        });
    }
}
```

이건 self-invocation 문제를 우회한다기보다,
애초에 프록시 semantics에 기대지 않는 방식이다.

장점:
- 동작이 매우 명확하다
- 같은 클래스 내부에서도 자유롭다

단점:
- 선언적 스타일보다 코드가 장황해질 수 있다

---

## 16) AspectJ mode는 무엇이 다른가

이 부분은 프록시 기반 AOP와 대조해서 이해하면 좋다.

### 16.1 proxy mode

```text
caller --> proxy --> target
```

- 외부 호출만 interception
- self call 불가
- 메서드 execution 중심
- 실무에서 가장 흔함

### 16.2 aspectj mode

```text
caller --> woven target
target 내부 local call 도 weaving 결과에 따라 interception 가능
```

- 바이트코드 자체에 aspect가 섞임
- self-invocation 문제가 없음
- 더 넓은 join point 가능
- 대신 셋업이 복잡

공식 문서도 명확히:
- Spring AOP 프록시 모델은 self-invocation 문제가 있고
- **AspectJ는 proxy-based framework가 아니기 때문에 이 문제가 없다**고 설명한다.

즉, self-invocation이 “프록시 방식의 구조적 한계”라는 점이 중요하다.

---

## 17) 실무에서 추천하는 설계 원칙

여기부터는 실무적으로 정말 중요하다.

### 17.1 트랜잭션 경계는 orchestration 메서드에 두지 말고, 경계가 필요한 서비스로 분리하라

나쁜 예:

```java
@Service
public class UserService {

    public void signup() {
        validate();
        saveUser();      // self-invocation
        sendMail();      // self-invocation
    }

    @Transactional
    public void saveUser() {}

    @Async
    public void sendMail() {}
}
```

좋은 예:

```java
@Service
public class UserSignupFacade {

    private final UserTxService userTxService;
    private final MailAsyncService mailAsyncService;

    public UserSignupFacade(UserTxService userTxService, MailAsyncService mailAsyncService) {
        this.userTxService = userTxService;
        this.mailAsyncService = mailAsyncService;
    }

    public void signup() {
        userTxService.saveUser();
        mailAsyncService.sendWelcomeMail();
    }
}
```

이렇게 나누면:
- `@Transactional`
- `@Async`
- `@Cacheable`

전부 외부 호출을 통해 자연스럽게 적용된다.

### 17.2 AOP 애노테이션이 붙은 메서드는 “프록시를 통해 호출될 것”을 전제로 설계하라

즉 다음처럼 생각하는 습관이 좋다.

- `@Transactional` 메서드는 **다른 빈이 호출하는 공개 서비스 경계**
- `@Async` 메서드는 **다른 빈이 호출하는 비동기 작업 경계**
- `@Cacheable` 메서드는 **외부에서 반복 호출될 조회 경계**

### 17.3 public API 중심으로 설계하라

문서가 말하듯 가시성 규칙은 프록시 타입에 따라 다를 수 있지만,
실무에서는 **공개 시그니처(public)** 기준으로 설계하는 것이 가장 단순하고 안전하다.

### 17.4 “왜 이 클래스에 자기 자신이 주입돼 있지?” 라는 코드가 나오면 경고 신호로 봐라

self injection / `AopContext.currentProxy()` 는 가능하지만,
대부분은 설계를 더 잘 나눌 수 있다는 신호인 경우가 많다.

---

## 18) 디버깅 포인트

프록시 문제는 “이론은 알겠는데 내 코드에서 지금 실제로 프록시가 붙었는지”가 중요하다.

### 18.1 런타임 클래스 이름 확인

```java
System.out.println(bean.getClass());
```

보통:
- JDK proxy -> `class jdk.proxy...` 또는 `$Proxy...`
- CGLIB proxy -> `$$SpringCGLIB$$`

### 18.2 AOP 유틸리티 확인

```java
AopUtils.isAopProxy(bean);
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
```

### 18.3 트랜잭션 활성 여부 확인

```java
System.out.println(TransactionSynchronizationManager.isActualTransactionActive());
```

`@Transactional`이 정말 적용됐는지 볼 때 유용하다.

### 18.4 로그 카테고리

문제 추적 시 보통 다음 로그가 도움이 된다.

- `org.springframework.aop`
- `org.springframework.transaction`
- `org.springframework.beans`

### 18.5 “프록시가 안 붙는” 대표 원인 체크리스트

1. 스프링 빈이 아닌 객체를 `new`로 만들었다
2. 메서드가 프록시 모델이 다루는 시그니처가 아니다
3. self-invocation 이다
4. `final` / `private` 제약에 걸렸다
5. pointcut 매칭이 안 된다
6. 인프라 빈이라 auto-proxy 대상에서 제외됐다

---

## 19) 면접에서 이렇게 설명하면 좋다

아래 정도 흐름이면 “프록시를 실제로 이해하고 있다”는 인상을 주기 좋다.

### 19.1 30초 버전

> 스프링 AOP는 기본적으로 런타임 프록시 기반입니다.  
> `@Transactional`, `@Async`, `@Cacheable`, `@Aspect` 같은 기능은 실제 타깃 객체를 직접 바꾸는 게 아니라, 타깃 앞에 프록시를 세워 메서드 호출을 가로채는 방식으로 동작합니다.  
> 그래서 외부에서 프록시를 통해 들어온 호출만 advice가 적용되고, 같은 클래스 내부의 `this.xxx()` 호출은 프록시를 우회하기 때문에 self-invocation 문제가 생깁니다.

### 19.2 2분 버전

> 스프링이 프록시를 붙이는 핵심 메커니즘은 AutoProxyCreator라는 BeanPostProcessor 계열입니다.  
> 예를 들어 `@EnableAspectJAutoProxy`는 `AspectJAutoProxyRegistrar`를 통해 `AnnotationAwareAspectJAutoProxyCreator`를 등록하고, `@EnableTransactionManagement`는 `TransactionManagementConfigurationSelector`를 통해 `AutoProxyRegistrar`와 `ProxyTransactionManagementConfiguration`을 import합니다.  
> 그러면 트랜잭션 쪽에서는 `TransactionInterceptor`, `BeanFactoryTransactionAttributeSourceAdvisor` 같은 인프라 빈이 생기고, 빈 생성 후 `postProcessAfterInitialization()` 시점에 현재 빈에 적용할 Advisor가 있으면 원본 대신 프록시가 반환됩니다.  
> 프록시 종류는 `DefaultAopProxyFactory`가 JDK dynamic proxy 또는 CGLIB 중에서 고르고, `proxyTargetClass`나 인터페이스 유무가 선택에 영향을 줍니다.  
> self-invocation이 문제인 이유는, 프록시는 외부 호출만 감쌀 수 있기 때문입니다. 이미 타깃 객체 안으로 들어간 뒤의 `this.inner()` 호출은 프록시를 다시 거치지 않아서 `TransactionInterceptor` 같은 advice가 실행되지 않습니다.  
> 실무에서는 이걸 리팩터링으로 해결하는 것이 가장 좋고, 필요하면 self injection, `AopContext.currentProxy()`, 또는 AspectJ mode를 검토합니다.

### 19.3 자주 받는 꼬리질문 답변 포인트

**Q. 프록시는 언제 붙나요?**  
- 보통 BeanPostProcessor의 `postProcessAfterInitialization()` 단계에서 붙는다.

**Q. 왜 `@Transactional`이 private 메서드에서 안 먹나요?**  
- 프록시 방식은 override / 인터페이스 dispatch 제약이 있어서 그렇다.  
- 게다가 self-invocation이면 더더욱 안 된다.

**Q. CGLIB 쓰면 self-invocation 해결되나요?**  
- 아니다. 프록시 타입의 문제가 아니라 **프록시를 다시 통과하느냐**의 문제다.

**Q. self-invocation 문제가 없는 방법은?**  
- 구조 분리로 외부 호출로 만들기  
- 또는 AspectJ weaving

---

## 20) 참고한 공식 문서

아래 문서들을 기준으로 정리했다.

1. Spring Framework Reference – Proxying Mechanisms  
   https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

2. Spring Framework Reference – Using `@Transactional`  
   https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html

3. Spring Framework Javadoc – `BeanPostProcessor`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/BeanPostProcessor.html

4. Spring Framework Javadoc – `AbstractAutoProxyCreator`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html

5. Spring Framework Javadoc – `AbstractAdvisorAutoProxyCreator`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAdvisorAutoProxyCreator.html

6. Spring Framework Javadoc – `AnnotationAwareAspectJAutoProxyCreator`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.html

7. Spring Framework Javadoc – `EnableAspectJAutoProxy`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html

8. Spring Framework Javadoc – `EnableTransactionManagement`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/EnableTransactionManagement.html

9. Spring Framework Javadoc – `ProxyTransactionManagementConfiguration`  
   https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/ProxyTransactionManagementConfiguration.html

10. Spring Framework Javadoc – `BeanFactoryTransactionAttributeSourceAdvisor`  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/interceptor/BeanFactoryTransactionAttributeSourceAdvisor.html

11. Spring Framework Javadoc – package `org.springframework.transaction.interceptor`  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/interceptor/package-summary.html

12. Spring Framework Javadoc – `DefaultAopProxyFactory`  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/DefaultAopProxyFactory.html

13. Spring Framework Javadoc – `AopProxy` / `AopContext` / `SmartInstantiationAwareBeanPostProcessor`  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AopProxy.html  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AopContext.html  
    https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/config/SmartInstantiationAwareBeanPostProcessor.html

14. Spring Framework source (official GitHub) – `AspectJAutoProxyRegistrar` / `TransactionManagementConfigurationSelector`  
    https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/context/annotation/AspectJAutoProxyRegistrar.java  
    https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/annotation/TransactionManagementConfigurationSelector.java

---

## 부록 A) self-invocation 예제 모음

### A-1. 실패하는 `@Transactional` 예제

```java
@Service
public class ReportService {

    public void generateAll() {
        // outer 는 프록시를 통해 호출될 수 있음
        generateOne(); // 하지만 이건 self-invocation
    }

    @Transactional
    public void generateOne() {
        // 트랜잭션이 걸릴 거라고 기대하지만 실제로는 아닐 수 있음
    }
}
```

### A-2. 성공하는 구조 분리 예제

```java
@Service
public class ReportFacade {

    private final ReportTxService reportTxService;

    public ReportFacade(ReportTxService reportTxService) {
        this.reportTxService = reportTxService;
    }

    public void generateAll() {
        reportTxService.generateOne(); // 프록시 경유
    }
}

@Service
public class ReportTxService {

    @Transactional
    public void generateOne() {
        // 실제 트랜잭션 적용
    }
}
```

### A-3. self injection 예제

```java
@Service
public class ReportService {

    private final ReportService self;

    public ReportService(@Lazy ReportService self) {
        this.self = self;
    }

    public void generateAll() {
        self.generateOne(); // 프록시 경유
    }

    @Transactional
    public void generateOne() {
    }
}
```

### A-4. `AopContext.currentProxy()` 예제

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class AopConfig {}

@Service
public class ReportService {

    public void generateAll() {
        ((ReportService) AopContext.currentProxy()).generateOne();
    }

    @Transactional
    public void generateOne() {
    }
}
```

---

## 부록 B) “CGLIB면 self-invocation이 될 것 같은데?”에 대한 오해 정리

가끔 이런 오해가 있다.

> “CGLIB는 상속해서 메서드를 override하니까, 같은 클래스 내부 호출도 잡히는 거 아닌가요?”

결론부터 말하면 **일반적인 Spring proxy-based AOP에서는 아니다**.

왜냐하면 핵심은 “서브클래스인가?”가 아니라,
**현재 호출이 프록시 객체를 통과하느냐** 이기 때문이다.

스프링이 관리하는 호출은 결국 이런 형태다.

```text
external reference --> proxy object --> interceptor chain --> target method
```

그런데 타깃 안에서의 local call은
이미 프록시 바깥이 아니라 **타깃 실행 내부 컨텍스트**다.  
그래서 보통 다시 proxy dispatch로 돌아가지 않는다.

즉, self-invocation 문제는
- JDK 프록시냐
- CGLIB 프록시냐

보다 더 근본적으로,
**“프록시를 경유하는 호출 모델인가?”** 의 문제다.

---

## 부록 C) 실무 체크리스트

- `@Transactional` / `@Async` / `@Cacheable` 이 붙은 메서드는 **다른 빈에서 호출**되게 설계했는가?
- 같은 클래스 내부에서 `this.xxx()` 또는 암묵적 self-call 이 있는가?
- 인터페이스 기반 프록시인지, 클래스 기반 프록시인지 알고 있는가?
- `final` / `private` 제약에 걸리는 메서드는 없는가?
- pointcut 이 진짜 매칭되는가?
- 대상 객체가 정말 스프링 빈인가? (`new`로 만든 객체가 아닌가?)
- 필요하다면 프록시 타입을 명시했는가?
- 마지막 응급처치로 `AopContext.currentProxy()`를 쓰고 있다면, 왜 구조 분리가 불가능했는지 팀이 납득 가능한가?

---

## 부록 D) 기억해야 할 핵심 문장 10개

1. 스프링 AOP의 기본 모델은 **런타임 프록시 기반**이다.  
2. 프록시는 **외부 호출**을 가로챈다.  
3. self-invocation은 **프록시를 우회**한다.  
4. `@Transactional` 이 안 먹는 많은 경우는 결국 **프록시를 안 탔기 때문**이다.  
5. `@Async`, `@Cacheable`도 같은 문제를 가진다.  
6. `AbstractAutoProxyCreator` 계열이 빈 생성 후 프록시를 만든다.  
7. 트랜잭션 AOP는 `TransactionInterceptor` 가 핵심이다.  
8. JDK 프록시는 **인터페이스 기반**, CGLIB는 **클래스 상속 기반**이다.  
9. `final` / `private` 는 프록시의 중요한 제약이다.  
10. self-invocation의 가장 좋은 해결책은 대부분 **리팩터링**이다.
