# D-Day Service AOP 적용 가이드

## 개요

Spring AOP(Aspect-Oriented Programming)를 학습하기 위해 D-Day Service에 세 가지 AOP 기능을 구현했습니다.

---

## 목차

1. [AOP 핵심 개념](#aop-핵심-개념)
2. [실행 시간 로깅](#1-실행-시간-로깅-logexecutiontime)
3. [API 감사 로그](#2-api-감사-로그-auditable)
4. [소유권 검증](#3-소유권-검증-checkownership)
5. [AOP 적용 시 주의사항](#aop-적용-시-주의사항)

---

## AOP 핵심 개념

### Spring AOP 동작 원리

```
클라이언트 → Proxy → Aspect(Advice) → 원본 메서드 → Aspect(Advice) → 결과 반환
```

Spring AOP는 **프록시 패턴**을 사용합니다. `@Aspect`가 적용된 Bean은 원본 객체 대신 프록시 객체가 주입되고, 메서드 호출 시 프록시가 먼저 Advice를 실행합니다.

### 주요 어노테이션

| 어노테이션 | 설명 | 사용 시점 |
|------------|------|----------|
| `@Aspect` | 이 클래스가 Aspect임을 선언 | 클래스 레벨 |
| `@Before` | 메서드 실행 **전**에 실행 | 검증, 로깅 |
| `@After` | 메서드 실행 **후**에 실행 (성공/실패 무관) | 리소스 정리 |
| `@AfterReturning` | 메서드 **정상 완료** 후 실행 | 성공 로깅, 결과 가공 |
| `@AfterThrowing` | 메서드에서 **예외 발생** 시 실행 | 예외 로깅, 알림 |
| `@Around` | 메서드 실행 **전/후** 모두 감싸기 | 실행 시간 측정, 트랜잭션 |

### Pointcut 표현식

```java
// 특정 어노테이션이 붙은 메서드
@Around("@annotation(LogExecutionTime)")

// 특정 패키지의 모든 메서드
@Around("execution(* com.booster.ddayservice..*Service.*(..))")

// 특정 어노테이션이 붙은 클래스의 모든 메서드
@Around("@within(org.springframework.stereotype.Service)")
```

### JoinPoint vs ProceedingJoinPoint

| 타입 | 사용처 | 특징 |
|------|--------|------|
| `JoinPoint` | @Before, @After, @AfterReturning, @AfterThrowing | 메서드 정보만 접근 가능 |
| `ProceedingJoinPoint` | @Around | `proceed()` 호출로 원본 메서드 실행 제어 |

---

## 1. 실행 시간 로깅 (@LogExecutionTime)

메서드 실행 시간을 측정하여 로깅합니다.

### 파일 구조

```
aop/
├── LogExecutionTime.java      # 마커 어노테이션
└── ExecutionTimeAspect.java   # Aspect 구현
```

### 어노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
```

### Aspect 구현

```java
@Slf4j
@Aspect
@Component
public class ExecutionTimeAspect {

    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();  // 원본 메서드 실행
            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}#{}] 실행 완료 - {}ms", className, methodName, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[{}#{}] 예외 발생 - {}ms | {}: {}",
                    className, methodName, duration,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
}
```

### 사용 예시

```java
@LogExecutionTime
public SyncAllResult syncAll(int year) {
    // 실행 시간이 자동으로 로깅됨
}
```

### 로그 출력 예시

```
INFO  [SpecialDaySyncService#syncByYear] 실행 완료 - 1523ms
WARN  [SpecialDaySyncService#syncAll] 예외 발생 - 234ms | TimeoutException: Connection timed out
```

### 적용된 위치

| 클래스 | 메서드 | 용도 |
|--------|--------|------|
| `SpecialDaySyncService` | `syncAll()` | 전체 국가 동기화 시간 측정 |
| `SpecialDaySyncService` | `syncByYear()` | 개별 국가 동기화 시간 측정 |
| `SpecialDayService` | `getToday()` | 조회 성능 측정 |

---

## 2. API 감사 로그 (@Auditable)

누가, 언제, 무엇을 했는지 감사 로그를 기록합니다.

### 파일 구조

```
aop/
├── Auditable.java      # 어노테이션 (action 속성 포함)
└── AuditAspect.java    # Aspect 구현 (Before, AfterReturning, AfterThrowing)
```

### 어노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();  // 수행 작업 설명
}
```

### Aspect 구현 (다중 Advice 패턴)

```java
@Slf4j
@Aspect
@Component
public class AuditAspect {

    // 요청 시작 로깅
    @Before("@annotation(auditable)")
    public void logBefore(JoinPoint joinPoint, Auditable auditable) {
        String action = auditable.action();
        Long memberId = extractMemberId(joinPoint);
        String params = formatParameters(joinPoint);
        log.info("[AUDIT] {} | {} | memberId={} | params={{}} | REQUESTED",
                timestamp, action, memberId, params);
    }

    // 성공 로깅
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void logAfterSuccess(JoinPoint joinPoint, Auditable auditable, Object result) {
        log.info("[AUDIT] {} | {} | memberId={} | SUCCESS",
                timestamp, action, memberId);
    }

    // 실패 로깅
    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void logAfterFailure(JoinPoint joinPoint, Auditable auditable, Throwable ex) {
        log.warn("[AUDIT] {} | {} | memberId={} | FAILED: {} - {}",
                timestamp, action, memberId,
                ex.getClass().getSimpleName(), ex.getMessage());
    }
}
```

### 사용 예시

```java
@Auditable(action = "특별일 삭제")
public void delete(Long id, Long memberId) {
    // 감사 로그가 자동으로 기록됨
}
```

### 로그 출력 예시

```
INFO  [AUDIT] 2025-02-05 12:00:00 | 특별일 삭제 | memberId=123 | params={id=456} | REQUESTED
INFO  [AUDIT] 2025-02-05 12:00:01 | 특별일 삭제 | memberId=123 | SUCCESS

INFO  [AUDIT] 2025-02-05 12:00:02 | 특별일 삭제 | memberId=123 | params={id=999} | REQUESTED
WARN  [AUDIT] 2025-02-05 12:00:02 | 특별일 삭제 | memberId=123 | FAILED: SpecialDayException - 특별일을 찾을 수 없습니다
```

### 적용된 위치

| 클래스 | 메서드 | action |
|--------|--------|--------|
| `SpecialDayService` | `createByMember()` | "특별일 생성" |
| `SpecialDayService` | `delete()` | "특별일 삭제" |
| `SpecialDayService` | `toggleVisibility()` | "공개 설정 변경" |
| `SpecialDayService` | `update()` | "특별일 수정" |

---

## 3. 소유권 검증 (@CheckOwnership)

리소스의 소유자만 접근할 수 있도록 검증합니다.

### 파일 구조

```
aop/
├── CheckOwnership.java      # 마커 어노테이션
└── OwnershipAspect.java     # Aspect 구현 (Repository 주입)
```

### 어노테이션 정의

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckOwnership {
}
```

### Aspect 구현 (Repository 주입 패턴)

```java
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final SpecialDayRepository specialDayRepository;  // DI

    @Before("@annotation(CheckOwnership)")
    public void checkOwnership(JoinPoint joinPoint) {
        Long id = extractId(joinPoint);
        Long memberId = extractMemberId(joinPoint);

        // DB에서 엔티티 조회
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SPECIAL_DAY_NOT_FOUND));

        // 소유권 검증
        if (!specialDay.isOwnedBy(memberId)) {
            throw new SpecialDayException(FORBIDDEN);  // 예외 → 원본 메서드 실행 안 함
        }
    }
}
```

### Before vs After 적용 이유

**@Before를 사용하는 이유:**
- 검증 실패 시 예외를 던져 원본 메서드 실행을 막음
- 불필요한 작업(삭제, 수정)이 실행되기 전에 차단
- 검증 로직은 항상 작업 전에 수행되어야 함

### 사용 예시

**적용 전 (반복 코드):**
```java
public void delete(Long id, Long memberId) {
    SpecialDay specialDay = specialDayRepository.findById(id)
            .orElseThrow(() -> new SpecialDayException(SPECIAL_DAY_NOT_FOUND));

    if (!specialDay.isOwnedBy(memberId)) {  // 반복!
        throw new SpecialDayException(FORBIDDEN);
    }

    specialDayRepository.delete(specialDay);
}
```

**적용 후 (간결):**
```java
@CheckOwnership
public void delete(Long id, Long memberId) {
    // 소유권 검증은 AOP에서 처리
    specialDayRepository.deleteById(id);
}
```

### JPA 1차 캐시 활용

AOP에서 `findById()`로 조회한 엔티티는 JPA 1차 캐시에 저장됩니다.
서비스 메서드에서 같은 ID로 다시 조회해도 DB 쿼리가 발생하지 않습니다.

```java
@CheckOwnership  // 여기서 findById() 실행 → 1차 캐시 저장
public void toggleVisibility(Long id, Long memberId) {
    SpecialDay specialDay = specialDayRepository.findById(id)  // 캐시 hit!
            .orElseThrow(...);
    specialDay.toggleVisibility();
}
```

### 적용된 위치

| 클래스 | 메서드 |
|--------|--------|
| `SpecialDayService` | `delete()` |
| `SpecialDayService` | `toggleVisibility()` |
| `SpecialDayService` | `update()` |

---

## AOP 적용 시 주의사항

### 1. Self-Invocation 문제

같은 클래스 내에서 메서드를 호출하면 AOP가 동작하지 않습니다.

```java
@Service
public class MyService {

    @LogExecutionTime
    public void methodA() {
        methodB();  // AOP 동작 안 함! (Self-Invocation)
    }

    @LogExecutionTime
    public void methodB() { }
}
```

**해결 방법:**
- 다른 Bean에서 호출
- `AopContext.currentProxy()` 사용 (권장하지 않음)
- 클래스 분리

### 2. Aspect 실행 순서

여러 Aspect가 적용된 경우 `@Order`로 순서를 제어합니다.

```java
@Aspect
@Order(1)  // 먼저 실행
public class OwnershipAspect { }

@Aspect
@Order(2)  // 나중에 실행
public class AuditAspect { }
```

실행 흐름:
```
요청 → OwnershipAspect(Before) → AuditAspect(Before) → 원본 메서드
     → AuditAspect(After) → OwnershipAspect(After) → 응답
```

### 3. 트랜잭션과 AOP

`@Transactional`도 AOP로 동작합니다. 순서에 주의하세요.

```java
@Transactional     // 트랜잭션 시작
@CheckOwnership    // 소유권 검증 (트랜잭션 내에서 실행)
public void delete(Long id, Long memberId) { }
```

소유권 검증이 트랜잭션 내에서 실행되므로, 검증 실패 시 트랜잭션도 롤백됩니다.

### 4. 예외 처리

AOP에서 발생한 예외는 원본 메서드의 예외처럼 처리됩니다.

```java
@Before("@annotation(CheckOwnership)")
public void checkOwnership(JoinPoint joinPoint) {
    // 여기서 발생한 예외는 Controller의 @ExceptionHandler에서 처리
    throw new SpecialDayException(FORBIDDEN);
}
```

---

## 전체 적용 현황

| 메서드 | @LogExecutionTime | @Auditable | @CheckOwnership |
|--------|:-----------------:|:----------:|:---------------:|
| `syncAll()` | O | - | - |
| `syncByYear()` | O | - | - |
| `getToday()` | O | - | - |
| `createByMember()` | - | O | - |
| `delete()` | - | O | O |
| `toggleVisibility()` | - | O | O |
| `update()` | - | O | O |

---

## 참고 자료

- [Spring AOP 공식 문서](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [AspectJ Pointcut 표현식](https://www.eclipse.org/aspectj/doc/released/progguide/semantics-pointcuts.html)
