# Kotlin 테스트에서 Kotest vs JUnit 5(Jupiter) 실무 가이드

> 대상 독자  
> - Kotlin + Spring Boot 프로젝트에서 테스트 전략을 정하려는 개발자  
> - "JUnit 5로 충분한가, Kotest로 가는 것이 좋은가?"를 실무 관점에서 판단하려는 팀  
> - 점진적으로 도입하고 싶은 팀 리드/시니어 개발자

> 버전 메모  
> - 이 문서는 공식 문서를 기준으로 정리했다.  
> - Spring Boot의 `spring-boot-starter-test`는 JUnit Jupiter를 기본으로 가져오며, Spring Boot 테스트는 `@SpringBootTest`와 슬라이스 테스트(`@WebMvcTest`, `@DataJpaTest` 등)를 제공한다. [1][2]  
> - Kotest는 JVM에서 JUnit Platform 위에서 동작하며, assertions/property testing을 프레임워크와 독립적으로 사용할 수 있다. [3][4][5]  
> - Kotest Spring 확장은 `io.kotest:kotest-extensions-spring` 모듈이며, Kotest 6.0부터 data-driven testing이 core에 포함되었다. [6][7]

---

## 1. 먼저 결론

### 한 줄 결론

- **Kotlin-first 팀**이고 테스트를 "더 읽기 쉽게", "더 Kotlin답게", "data/property testing까지 적극적으로" 쓰고 싶다면 **Kotest가 꽤 강력한 선택**이다.
- **Spring Boot 기본 흐름, Java 혼합 코드베이스, 사내 표준화, 온보딩 비용 최소화**가 더 중요하면 **JUnit Jupiter를 기본으로 두는 것이 여전히 좋은 선택**이다.
- 가장 현실적인 전략은 보통 다음 순서다.

1. **1단계: JUnit 유지 + Kotest assertions만 도입**
2. **2단계: 순수 Kotlin unit test부터 Kotest spec으로 전환**
3. **3단계: Spring 통합 테스트까지 Kotest로 확장할지 팀 기준으로 결정**

즉, 실무에서는 "JUnit vs Kotest"를 이분법으로 보기보다 **도입 범위를 어떻게 나눌지**가 더 중요하다.

---

## 2. 두 프레임워크를 어떻게 이해하면 좋은가

### JUnit 5(Jupiter)

JUnit 5는 정확히 말하면 **JUnit Platform + JUnit Jupiter + (필요 시) JUnit Vintage**로 구성된다.  
JUnit Platform은 테스트 엔진을 실행하는 기반이고, JUnit Jupiter는 우리가 흔히 말하는 JUnit 5 테스트 작성 모델과 확장 모델이다. [8]

실무적으로 기억할 포인트:

- Spring Boot 기본 테스트 생태계와 가장 자연스럽게 붙는다. [1]
- IDE/CI/빌드 도구/플러그인 호환성이 매우 넓다. [8]
- `@Test`, `@Nested`, `@ParameterizedTest`, `@BeforeEach`, `@ExtendWith` 같은 **표준 어노테이션 중심 모델**이다. [9][10]

### Kotest

Kotest는 Kotlin용 테스트 프로젝트이며, 크게 다음 3개를 독립적으로 제공한다. [3]

- Test framework
- Assertions library
- Property testing

중요한 점은:

- **Kotest 전체 프레임워크로 갈 수도 있고**
- **JUnit은 그대로 두고 Kotest assertions/property만 가져올 수도 있다**는 점이다. [3][5]

즉, Kotest는 "JUnit을 버리고 갈아타는 것"만 의미하지 않는다.  
실무에서는 오히려 **"Kotest assertions부터 도입"**이 가장 안전하다.

---

## 3. 런타임 관점에서 보면 둘은 경쟁만 하는 것이 아니다

Kotest는 JVM에서 **JUnit Platform 위에서 동작**한다. [4]  
즉, Gradle에서 `useJUnitPlatform()`을 쓰는 구조는 JUnit Jupiter와 Kotest 모두에게 공통 기반이 될 수 있다. [4][11]

이 말은 실무적으로 다음을 뜻한다.

- 같은 프로젝트에서 **JUnit 테스트와 Kotest 테스트를 공존**시킬 수 있다.
- 한 번에 전체 전환하지 않고 **파일 단위, 모듈 단위, 레이어 단위**로 천천히 옮길 수 있다.
- Spring Boot 기본 테스트 설정을 유지하면서도 Kotest를 추가할 수 있다.

이 점 때문에, Kotest 도입은 생각보다 "파괴적"이지 않다.

---

## 4. 언제 JUnit 5가 더 낫고, 언제 Kotest가 더 나은가

## 4.1 JUnit 5가 더 잘 맞는 경우

다음 조건이 많다면 JUnit 5 기본 유지가 좋다.

- Java + Kotlin 혼합 프로젝트다.
- 팀 대부분이 JUnit에 익숙하고, Kotest DSL 학습 비용이 부담된다.
- 외부 라이브러리/사내 공통 확장들이 JUnit Extension 모델을 전제로 한다.
- 테스트 코드의 80%가 전형적인 CRUD/service/controller/repository 테스트다.
- 파라미터 테스트와 `assertAll` 정도면 충분하다.
- 테스트 코드를 누구나 바로 읽을 수 있는 "가장 보편적인 형태"가 중요하다.

### 실무적인 판단

JUnit 5는 "나쁜 선택"이 아니라 **가장 무난하고 표준적인 선택**이다.  
특히 Spring Boot에서는 기본이 JUnit Jupiter라서 별도 의사결정 비용이 적다. [1][2]

---

## 4.2 Kotest가 더 잘 맞는 경우

다음 조건이 많다면 Kotest가 확실한 장점을 준다.

- Kotlin-only 혹은 Kotlin 비중이 매우 높다.
- 테스트 문장을 **도메인 언어처럼 읽히게** 만들고 싶다.
- assertions 가독성과 표현력이 중요하다.
- 같은 로직을 많은 입력 조합으로 테스트하는 일이 많다.
- property testing으로 입력 공간을 넓게 검증하고 싶다.
- collection/assertion DSL, hooks, isolation mode, coroutine 친화성이 필요하다.
- 테스트가 단순 검증이 아니라 **명세(specification)**에 가까운 문서 역할을 하길 원한다.

### 실무적인 판단

Kotest는 특히 다음에서 체감 차이가 크다.

- domain/service unit test
- validation logic
- parser/formatter
- policy/discount/pricing/rule engine
- 문자열/날짜/수치 변환
- 여러 edge case가 많은 순수 함수 또는 준순수 함수
- 비즈니스 시나리오를 BDD 스타일로 읽히게 해야 하는 테스트

---

## 5. 항목별 비교표

| 항목 | JUnit 5(Jupiter) | Kotest | 실무 코멘트 |
|---|---|---|---|
| 기본 철학 | 표준 어노테이션 중심 | Kotlin DSL/spec 중심 | 팀 취향과 온보딩 비용 차이 |
| 생태계 | 가장 넓음 | Kotlin 테스트 쪽 강함 | Java 혼합이면 JUnit 우세 |
| Spring Boot 기본 | 기본 제공 [1] | 추가 의존성/확장 필요 [6] | Boot는 JUnit 쪽 진입이 쉬움 |
| 테스트 구조 | `@Test`, `@Nested` | `FunSpec`, `BehaviorSpec`, `ShouldSpec` 등 여러 스타일 [12] | Kotest는 표현력 높지만 팀 룰 필요 |
| assertions | 기본 assertions, `assertAll`, `assertThrows` [13] | `shouldBe`, 풍부한 matcher, inspectors, soft assertions [5][14] | Kotlin에서는 Kotest가 더 읽기 좋음 |
| 파라미터 테스트 | `@ParameterizedTest` + source 필요 [10] | data-driven testing 내장 [7] | 많은 조합 테스트에서 Kotest 편함 |
| 동적 테스트 | `@TestFactory` 가능 [15] | spec/data DSL이 더 자연스러움 | JUnit dynamic test는 lifecycle 주의 필요 [15] |
| property testing | 별도 도구가 더 필요 | 공식 지원 [16] | Kotest 강점 |
| lifecycle | `@BeforeEach`, `@AfterEach`, `@BeforeAll` | DSL hooks + reusable extension [17][18] | Kotlin에서는 Kotest DSL이 자연스러울 때 많음 |
| extension | `Extension` API [19] | reusable extension + project config [18][20] | 둘 다 강하지만 방식이 다름 |
| test instance lifecycle | 기본 `PER_METHOD` [21] | 기본 `SingleInstance` [22] | 상태 공유 실수에 주의 |
| Kotlin 친화성 | 충분히 좋음, Kotlin assertion 지원 있음 [23] | Kotlin 중심으로 설계됨 [3][24] | Kotlin-only면 Kotest 만족도 높음 |
| 점진 도입 | 매우 쉬움 | assertions만 먼저 도입 가능 [3][5] | 실무에서는 hybrid가 좋음 |

---

## 6. 실무 추천 전략

### 전략 A - 보수적이고 안전한 전략
**JUnit 5는 유지하고 Kotest assertions만 도입**

추천 상황:
- 기존 JUnit 테스트가 많다.
- 팀 전체 전환은 부담스럽다.
- 그래도 Kotlin답고 읽기 좋은 assertion DSL은 원한다.

장점:
- 테스트 러너/IDE/CI 흐름 변화가 거의 없다.
- 기존 `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest` 관성 유지 가능
- assertions 가독성만 빠르게 개선 가능

### 전략 B - 현실적인 균형 전략
**Spring 통합 테스트는 JUnit 위주, 순수 unit/domain test는 Kotest**

추천 상황:
- Spring Boot 프로젝트
- service/domain/validation 테스트가 많음
- BDD/data/property testing 혜택을 보고 싶음

장점:
- 컨텍스트 테스트는 익숙한 JUnit로 유지
- 순수 Kotlin 로직은 Kotest로 생산성/가독성 향상
- 팀 간 타협이 쉽다

### 전략 C - Kotlin-first 전략
**대부분의 테스트를 Kotest로 통일**

추천 상황:
- Kotlin-only
- 팀이 DSL에 익숙해질 의지가 있음
- 테스트를 문서처럼 읽히게 하는 문화가 중요
- property/data testing 활용도가 높음

주의:
- spec style을 팀 규칙으로 제한하는 것이 좋다.
- `FunSpec` 또는 `BehaviorSpec` 1-2개 정도로 좁히는 편이 운영상 좋다.

---

## 7. Gradle 설정 예제

## 7.1 JUnit 5 기본 설정(Spring Boot 기본 흐름)

```kotlin
plugins {
    kotlin("jvm") version "YOUR_KOTLIN_VERSION"
    kotlin("plugin.spring") version "YOUR_KOTLIN_VERSION"
    id("org.springframework.boot") version "YOUR_BOOT_VERSION"
    id("io.spring.dependency-management") version "YOUR_DM_VERSION"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

설명:

- `spring-boot-starter-test`가 JUnit Jupiter를 기본으로 포함한다. [1]
- Spring Boot 테스트 annotation과 slice test를 바로 쓸 수 있다. [2]

---

## 7.2 JUnit 유지 + Kotest assertions만 추가

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // assertion만 추가
    testImplementation("io.kotest:kotest-assertions-core:YOUR_KOTEST_VERSION")
}
```

이 경우 테스트 클래스는 계속 JUnit으로 쓰고 assertion만 이렇게 바꿀 수 있다.

```kotlin
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UserIdsTest {

    @Test
    fun `유저 ID 목록을 반환한다`() {
        val ids = listOf(10L, 20L, 30L)

        ids shouldHaveSize 3
        ids.first() shouldBe 10L
    }
}
```

이 방식은 **도입 리스크가 가장 낮다**.

---

## 7.3 Kotest 전체 프레임워크 도입

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("io.kotest:kotest-runner-junit5:YOUR_KOTEST_VERSION")
    testImplementation("io.kotest:kotest-assertions-core:YOUR_KOTEST_VERSION")
    testImplementation("io.kotest:kotest-property:YOUR_KOTEST_VERSION")

    // Spring Boot / spring-test 연동용
    testImplementation("io.kotest:kotest-extensions-spring:YOUR_KOTEST_VERSION")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

근거:

- Kotest는 JVM에서 JUnit Platform 위에서 동작한다. [4]
- Spring 확장은 `kotest-extensions-spring` 모듈로 제공된다. [6]
- property testing은 별도 `kotest-property` 모듈이다. [16]

---

## 7.4 Kotest 프로젝트 전역 설정(ProjectConfig)

Kotest는 프로젝트 전역 설정을 `AbstractProjectConfig`로 둘 수 있다.  
Kotest 6.x에서는 project config 인식 방식에 변화가 있으므로 공식 문서 구조를 따라 두는 편이 안전하다. [20]

예시:

`src/test/kotlin/io/kotest/provided/ProjectConfig.kt`

```kotlin
package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension())
}
```

이렇게 하면 모든 spec에 `SpringExtension`을 전역 등록할 수 있다. [6][20]

---

## 8. 같은 테스트를 JUnit 5와 Kotest로 비교해보기

예시 대상 코드:

```kotlin
class PriceCalculator {
    fun total(unitPrice: Int, quantity: Int, discountPercent: Int = 0): Int {
        require(unitPrice >= 0)
        require(quantity >= 1)
        require(discountPercent in 0..100)

        val gross = unitPrice * quantity
        val discount = gross * discountPercent / 100
        return gross - discount
    }
}
```

---

## 8.1 JUnit 5 버전

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PriceCalculatorJUnitTest {

    private val sut = PriceCalculator()

    @Nested
    inner class Total {

        @ParameterizedTest(name = "[{index}] unitPrice={0}, quantity={1}, discount={2}, expected={3}")
        @CsvSource(
            "1000, 2, 0, 2000",
            "1000, 2, 10, 1800",
            "1500, 3, 20, 3600"
        )
        fun `총액을 계산한다`(
            unitPrice: Int,
            quantity: Int,
            discountPercent: Int,
            expected: Int
        ) {
            val actual = sut.total(unitPrice, quantity, discountPercent)
            assertEquals(expected, actual)
        }

        @Test
        fun `수량이 1보다 작으면 예외가 발생한다`() {
            assertThrows(IllegalArgumentException::class.java) {
                sut.total(unitPrice = 1000, quantity = 0)
            }
        }
    }
}
```

### 장점

- 누구나 익숙하다.
- `@ParameterizedTest`와 `@CsvSource`가 직관적이다. [10]
- Spring Boot 문서/예제와 가장 자연스럽게 이어진다. [1][2]

### 단점

- Kotlin 코드로 보면 annotation이 많고 의도가 분산될 수 있다.
- nested/parameterized가 늘어나면 "구조를 읽는 맛"은 Kotest보다 약하다고 느끼는 팀이 많다.
- assertions 표현력은 Kotest보다 다소 평범하다.

---

## 8.2 Kotest FunSpec 버전

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.assertions.throwables.shouldThrow

data class PriceCase(
    val unitPrice: Int,
    val quantity: Int,
    val discountPercent: Int,
    val expected: Int
)

class PriceCalculatorFunSpecTest : FunSpec({

    val sut = PriceCalculator()

    context("total") {
        withData(
            PriceCase(1000, 2, 0, 2000),
            PriceCase(1000, 2, 10, 1800),
            PriceCase(1500, 3, 20, 3600),
        ) { case ->
            sut.total(case.unitPrice, case.quantity, case.discountPercent) shouldBeExactly case.expected
        }
    }

    test("수량이 1보다 작으면 예외가 발생한다") {
        shouldThrow<IllegalArgumentException> {
            sut.total(unitPrice = 1000, quantity = 0)
        }
    }
})
```

### 장점

- 테스트가 Kotlin DSL처럼 읽힌다.
- data class + `withData` 조합이 매우 자연스럽다.  
  Kotest는 data-driven testing을 프레임워크 차원에서 제공하고, 각 row를 별도 테스트 케이스처럼 표시한다. [7]
- `shouldThrow`, `shouldBeExactly` 같은 matcher가 의도 전달이 좋다. [5]

### 단점

- JUnit보다 초기 학습 비용이 있다.
- 팀마다 spec style을 제각각 쓰면 오히려 읽기 어려워질 수 있다.
- data-driven는 container/leaf scope 제약을 알아야 한다. data test는 leaf scope 안에 둘 수 없다. [7]

---

## 8.3 Kotest BehaviorSpec 버전

BDD 문맥이 필요한 테스트라면 다음처럼 쓸 수 있다.

```kotlin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PriceCalculatorBehaviorSpecTest : BehaviorSpec({

    given("정상 가격과 수량이 주어졌을 때") {

        val sut = PriceCalculator()

        `when`("할인이 없으면") {
            then("총액은 단가 * 수량이다") {
                sut.total(1000, 2, 0) shouldBe 2000
            }
        }

        `when`("10% 할인이 있으면") {
            then("할인된 총액을 반환한다") {
                sut.total(1000, 2, 10) shouldBe 1800
            }
        }
    }
})
```

### 언제 좋나

- 비즈니스 룰이 많을 때
- 기획서/정책 문서를 테스트로 옮기고 싶을 때
- given-when-then 흐름이 설득력을 줄 때

### 언제 과할 수 있나

- 단순 CRUD/service/repository 테스트에 남발하면 오히려 장황해진다.
- 그래서 실무에서는 보통
  - `FunSpec`을 기본
  - 정말 BDD가 필요한 도메인만 `BehaviorSpec`
  으로 제한하는 편이 낫다.

---

## 9. assertions 비교 - Kotlin에서는 왜 Kotest가 체감이 큰가

JUnit 기본 assertions도 충분히 강력하다. `assertEquals`, `assertTrue`, `assertThrows`, `assertAll`이 있다. [13]

하지만 Kotlin에서 Kotest assertions는 다음 장점이 있다.

- infix 스타일이라 문장이 자연스럽다.
- collection/string/exception/nullable 등용 matcher가 풍부하다. [5]
- inspectors로 collection 검증을 매우 간결하게 할 수 있다. [14]
- assertions만 따로 도입 가능하다. [5]

### JUnit 스타일

```kotlin
assertEquals(3, ids.size)
assertTrue(ids.all { it > 0 })
```

### Kotest 스타일

```kotlin
ids shouldHaveSize 3
ids.forAll { it shouldBeGreaterThan 0 }
```

### inspectors 예시

Kotest inspectors는 collection에 특히 강하다. [14]

```kotlin
import io.kotest.assertions.withClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.longs.shouldBeGreaterThan

data class User(val id: Long, val age: Int)

class UserAssertionsTest : FunSpec({
    test("모든 사용자는 양수 ID를 가진다") {
        val users = listOf(
            User(1, 20),
            User(2, 30),
            User(3, 40)
        )

        users.forAll {
            withClue("user=${it.id}") {
                it.id shouldBeGreaterThan 0L
            }
        }
    }
})
```

---

## 10. data-driven testing 비교

## 10.1 JUnit 5

JUnit은 `@ParameterizedTest`로 잘 지원한다. [10]

장점:
- 표준적이고 강력하다.
- `@CsvSource`, `@MethodSource`, `@EnumSource` 등 소스가 다양하다. [10]

단점:
- Kotlin data class와 DSL 결합 느낌은 Kotest보다 덜 자연스럽다.
- 테스트가 많아질수록 annotation이 늘어난다.

예시:

```kotlin
@ParameterizedTest
@CsvSource(
    "A, true",
    "b, true",
    "1, false"
)
fun `알파벳만 허용한다`(input: String, expected: Boolean) {
    assertEquals(expected, input.all { it.isLetter() })
}
```

---

## 10.2 Kotest

Kotest는 data-driven testing을 프레임워크 차원에서 제공한다.  
Kotest 6.0부터는 data-driven testing이 core에 포함되어 별도 모듈이 필요 없다. [7]

예시:

```kotlin
data class UsernameCase(
    val input: String,
    val expected: Boolean
)

class UsernameValidationTest : FunSpec({

    context("username validation") {
        withData(
            UsernameCase("alpha", true),
            UsernameCase("beta123", false),
            UsernameCase("zeta", true),
        ) { case ->
            case.input.all { it.isLetter() } shouldBe case.expected
        }
    }
})
```

실무 장점:

- data class row를 바로 쓰기 좋다.
- 실패 시 어떤 row가 실패했는지 읽기 쉽다. [7]
- nested context와 잘 결합된다. [7]

---

## 10.3 JUnit dynamic test와 Kotest data test의 차이

JUnit에는 `@TestFactory` 기반 dynamic test가 있다. [15]  
하지만 공식 문서상 **dynamic test 개별 항목에는 일반 `@Test`와 같은 lifecycle callback이 적용되지 않는다**는 점을 주의해야 한다. `@BeforeEach`/`@AfterEach`는 `@TestFactory` 메서드에는 적용되지만 개별 dynamic test에는 적용되지 않는다. [15]

반면 Kotest의 data-driven test는 프레임워크가 생성한 개별 테스트 케이스처럼 다뤄지며, 일반 lifecycle hook과 함께 동작한다. [7][17]

이 차이는 실무에서 꽤 중요하다.

- "각 케이스마다 setup/teardown이 자연스럽게 먹어야 한다"
- "CI 리포트에서 row 단위로 보이길 원한다"
- "테스트 실패 메시지가 입력 row와 바로 연결되길 원한다"

이런 요구가 많으면 Kotest가 더 편하다.

---

## 11. property testing - Kotest가 확실히 강한 영역

property testing은 "예시 몇 개"가 아니라 **많은 입력에 대해 성질(property, invariant)이 항상 성립하는지**를 검사하는 방식이다.  
Kotest는 property test를 공식 모듈로 제공한다. [16]

### 언제 유용한가

- 할인율 계산
- 정렬/파싱/인코딩/디코딩
- 문자열 정규화
- 날짜 계산
- validation logic
- 경계값이 많은 수치 계산
- edge case가 많은 함수

### 예시 - 할인 결과는 항상 원가 이하

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class PriceCalculatorPropertyTest : FunSpec({

    val sut = PriceCalculator()

    test("할인된 금액은 항상 0 이상이며 원가 이하이다") {
        checkAll(
            Arb.int(0..1_000_000),
            Arb.int(1..100),
            Arb.int(0..100)
        ) { unitPrice, quantity, discountPercent ->
            val result = sut.total(unitPrice, quantity, discountPercent)
            val gross = unitPrice * quantity

            result shouldBeGreaterThanOrEqual 0
            result shouldBeLessThanOrEqual gross
        }
    }
})
```

JUnit에서도 별도 라이브러리 조합으로 property testing을 할 수는 있지만,  
Kotest는 **이 기능이 자기 정체성 중 하나**라서 도입과 사용이 훨씬 자연스럽다. [3][16]

### 실무 조언

property test는 모든 테스트를 대체하지 않는다.  
보통은 다음 조합이 가장 좋다.

- 예시 기반 테스트(example-based): 대표 시나리오 5-10개
- property test: 경계/조합/랜덤 입력 전체
- 회귀 테스트(regression): 과거 장애 케이스 고정

---

## 12. lifecycle과 test instance lifecycle 차이

이 부분은 실무에서 꽤 중요하다.

## 12.1 JUnit 5 기본값

JUnit Jupiter는 기본적으로 **테스트 메서드마다 새 인스턴스**를 만든다(`PER_METHOD`). [21]

즉:

- 테스트 간 필드 상태 공유가 기본적으로 덜 일어난다.
- 상태 오염 버그를 줄이기 쉽다.
- 대신 Kotlin에서 `@BeforeAll`/`@AfterAll`를 인스턴스 메서드로 쓰려면 `@TestInstance(PER_CLASS)`를 고려해야 한다. [21][23]

예시:

```kotlin
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleJUnitTest {

    private lateinit var state: MutableList<String>

    @BeforeAll
    fun beforeAll() {
        println("before all")
    }

    @BeforeEach
    fun setUp() {
        state = mutableListOf()
    }

    @Test
    fun `첫 번째 테스트`() {
        state.add("A")
        assertEquals(1, state.size)
    }

    @Test
    fun `두 번째 테스트`() {
        state.add("B")
        assertEquals(1, state.size)
    }
}
```

---

## 12.2 Kotest 기본값

Kotest 기본 isolation mode는 **SingleInstance**다. 즉, spec 인스턴스 하나를 만들고 그 안의 테스트를 실행한다. [22]

이 점은 JUnit과 매우 다르다.

### 실무에서 생기는 문제

JUnit 감각으로 field mutable state를 써버리면 Kotest에서 테스트 간 상태가 섞일 수 있다.

예:

```kotlin
class BadKotestExample : FunSpec({

    val bucket = mutableListOf<String>()

    test("첫 번째 테스트") {
        bucket.add("A")
        bucket.size shouldBe 1
    }

    test("두 번째 테스트") {
        bucket.add("B")
        // 여기서 2가 되어 실패할 수 있다
        bucket.size shouldBe 1
    }
})
```

### 해결책

1. 가능하면 테스트 상태를 로컬 변수로 둔다.
2. `beforeTest`에서 매번 초기화한다.
3. 필요하면 `IsolationMode.InstancePerRoot`로 바꾼다. Kotest 공식 문서도 `InstancePerRoot`를 권장한다. [22]

예시:

```kotlin
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec

class BetterKotestExample : FunSpec({

    isolationMode = IsolationMode.InstancePerRoot

    val bucket = mutableListOf<String>()

    test("첫 번째 테스트") {
        bucket.add("A")
        bucket.size shouldBe 1
    }

    test("두 번째 테스트") {
        bucket.add("B")
        bucket.size shouldBe 1
    }
})
```

### 핵심 요약

- JUnit default: 상태 공유에 상대적으로 안전
- Kotest default: DSL 구조는 좋지만 상태 관리 규칙을 팀이 알아야 함

이건 Kotest의 단점이라기보다 **운영 규칙이 필요한 차이**다.

---

## 13. lifecycle hooks 비교

### JUnit 5

- `@BeforeEach`
- `@AfterEach`
- `@BeforeAll`
- `@AfterAll`

가장 표준적이다.

### Kotest

Kotest는 spec 내부에서 hook DSL을 직접 쓸 수 있다. [17]

- `beforeTest`
- `afterTest`
- `beforeSpec`
- `afterSpec`
- 그 외 여러 lifecycle callback [17][18]

예시:

```kotlin
class UserServiceHookTest : FunSpec({

    lateinit var service: UserService

    beforeTest {
        service = UserService()
    }

    afterTest { (_, result) ->
        println("result=$result")
    }

    test("유저를 생성한다") {
        service.create("kim").name shouldBe "kim"
    }
})
```

### 실무 관점

Kotest hook은 Kotlin 함수처럼 자연스럽고 재사용 가능한 lambda나 extension으로 빼기 좋다. [17][18]  
반면 JUnit은 annotation 기반이라 보편적이고 익숙하다.

---

## 14. extension 모델 비교

## 14.1 JUnit 5 Extension

JUnit Jupiter는 `Extension` API를 중심으로 확장한다.  
공식 문서 기준으로 확장은 `@ExtendWith`, `@RegisterExtension`, ServiceLoader 등을 통해 등록할 수 있다. [19]

장점:
- 가장 표준적이다.
- 사내 공통 확장, 외부 라이브러리 통합에 유리하다.
- Spring도 이 모델과 자연스럽게 붙는다. Spring Boot 테스트 annotation은 필요한 확장을 이미 메타 annotation으로 포함한다. [2]

개념 예시:

```kotlin
class FixedClockExtension : BeforeEachCallback, ParameterResolver {
    override fun beforeEach(context: ExtensionContext) {
        // setup
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == Clock::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
```

```kotlin
@ExtendWith(FixedClockExtension::class)
class ExpiryPolicyTest {

    @Test
    fun `만료일을 계산한다`(clock: Clock) {
        // ...
    }
}
```

---

## 14.2 Kotest Extension

Kotest는 lifecycle hook을 재사용 가능한 extension으로 만들 수 있고, project-wide config도 지원한다. [18][20]  
공식 문서에서는 hooks 자체도 extension 개념과 가깝게 설명한다. [18]

장점:
- Kotlin DSL스럽다.
- spec/test lifecycle에 매우 자연스럽게 녹아든다.
- project config, package-level config 등 운영 포인트가 있다. [20]

실무 판단:

- **커스텀 인프라 확장**이 많으면 JUnit Extension이 더 익숙할 수 있다.
- **테스트 작성 경험 자체를 Kotlin답게 만들고 싶으면** Kotest 쪽이 손에 잘 맞는다.

---

## 15. Spring Boot에서 어떻게 쓰면 좋은가

이 부분이 실무에서 가장 중요하다.

## 15.1 대원칙

### 1) 순수 unit test는 Spring context를 띄우지 않는다
- JUnit이든 Kotest든 동일
- fake/mock/stub로 해결
- 가장 빠르고 유지보수성이 좋음

### 2) slice test를 적극적으로 쓴다
- `@WebMvcTest`
- `@DataJpaTest`
- `@JsonTest`
- `@RestClientTest`
등  
Spring Boot는 애플리케이션 전체 컨텍스트 대신 필요한 슬라이스만 올리는 테스트를 제공한다. [2]

### 3) 정말 필요한 경우에만 `@SpringBootTest`
Spring Boot 문서상 `@SpringBootTest`는 전체 애플리케이션 컨텍스트를 만들고, `webEnvironment` 설정에 따라 mock 또는 real server 환경을 띄운다. [2]

즉, 느리고 무겁다.  
만능이지만 **기본값으로 남발하면 안 된다**.

---

## 15.2 JUnit 5로 Spring Boot 테스트

Spring Boot에서는 JUnit이 가장 자연스럽다.

```kotlin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(UserController::class)
class UserControllerJUnitTest(
    @Autowired private val mockMvc: MockMvc
) {

    @Test
    fun `GET users는 200을 반환한다`() {
        mockMvc.get("/users")
            .andExpect {
                status { isOk() }
            }
    }
}
```

### 장점

- Spring Boot 공식 예제, 문서, 블로그와 가장 호환성이 좋다.
- 사내에서 처음 보는 사람도 적응이 쉽다.
- 문제 생겼을 때 검색 결과가 가장 많다.

---

## 15.3 Kotest로 Spring Boot 테스트

Kotest는 Spring 확장을 제공한다.  
Spring 확장이 활성화되면 test class primary constructor에 대한 constructor injection도 지원한다. [6]

예시:

```kotlin
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@ApplyExtension(SpringExtension::class)
class UserServiceSpringKotest(
    private val userService: UserService
) : FunSpec({

    test("유저를 저장하고 조회한다") {
        val saved = userService.create("kim")
        val found = userService.find(saved.id)

        found.name shouldBe "kim"
    }
})
```

근거:
- Kotest Spring extension은 Spring DI를 사용하는 테스트를 지원한다. [6]
- Spring extension이 활성화되면 primary constructor를 통한 autowiring을 처리한다. [6]

### 장점

- Kotlin constructor injection이 매우 자연스럽다.
- DSL + Spring DI 조합이 깔끔하다.
- 도메인 시나리오 테스트를 더 읽기 좋게 작성할 수 있다.

### 주의

- Kotest SpringExtension 등록을 빼먹으면 당황하기 쉽다.
- JUnit에 비해 팀원들의 익숙함이 적을 수 있다.
- Boot 기본 예제와 완전히 같지는 않아서 팀 공통 템플릿이 필요하다.

---

## 15.4 어떤 레이어에 무엇을 권하나

### A. domain/service unit test
**Kotest 추천 강함**

이유:
- Spring 없이도 충분
- assertion/data/property testing 이점 큼
- 가독성 좋아짐

### B. controller slice test (`@WebMvcTest`)
**둘 다 가능하지만 JUnit 유지도 매우 합리적**

이유:
- Spring Boot 문서/예제가 거의 JUnit 기준
- 팀 전체 공통 이해도가 중요
- 테스트 구조가 표준화되어 있어 DSL 이점이 상대적으로 작음

### C. repository test (`@DataJpaTest`)
**둘 다 가능**

- SQL/엔티티 검증이 많다면 assertions DSL이 좋아서 Kotest 만족도가 괜찮다.
- 다만 JUnit으로도 충분히 잘 된다.

### D. full integration test (`@SpringBootTest`)
**팀 선호에 따라**
- 테스트 수가 적고 표준성이 중요하면 JUnit
- 시나리오 중심이고 constructor injection + DSL을 선호하면 Kotest

---

## 16. 코루틴/비동기 코드가 많다면

Kotest 공식 소개는 built-in coroutine support를 강조한다. [24]  
즉, coroutine-heavy Kotlin 코드에서는 Kotest가 전반적으로 더 자연스럽게 느껴질 수 있다.

예를 들어, suspend 함수가 많은 서비스나 비동기 polling/assertion이 많은 경우
Kotest의 DSL, hooks, timeout/config, coroutine 친화성이 장점이 된다. [24][25]

다만 실무 조언은 이렇다.

- **코루틴 테스트 자체는 `kotlinx-coroutines-test`가 핵심**이다.
- Kotest가 있다고 coroutine test가 자동으로 좋아지는 것은 아니다.
- 하지만 Kotlin 스타일과 더 잘 맞는 것은 분명하다.

즉:

- coroutine test infrastructure: `kotlinx-coroutines-test`
- test DSL/structure/ergonomics: Kotest가 유리한 경우가 많음

---

## 17. "Kotest를 쓰면 JUnit을 완전히 버려야 하나?" - 아니다

이 질문이 실무에서 매우 중요하다.

정답은 **아니다**.

가능한 운영 방식:

### 방식 1 - JUnit + Kotest assertions
가장 무난하고 강력한 혼합

### 방식 2 - JUnit과 Kotest spec 공존
- 기존 테스트는 JUnit
- 신규 Kotlin domain test는 Kotest

### 방식 3 - 모듈 단위 분리
- `core-domain`: Kotest
- `spring-api`: JUnit + 일부 Kotest assertions

Kotest 공식 문서도 assertions/property를 독립적으로 사용할 수 있다고 말한다. [3][5][16]

그래서 나는 실무적으로 **"전부 바꾸기"보다 "좋은 부분부터 가져오기"**를 더 추천한다.

---

## 18. 팀 규칙을 어떻게 잡아야 하나

Kotest를 도입할 때 가장 흔한 실패는 "자유도가 너무 높아서"다.

Kotest는 여러 testing style을 제공한다. 공식 문서에 따르면 8가지 스타일이 있고 기능 차이는 거의 없고 구조 선호 차이이다. [12]

이 말은 곧:
- 아무 규칙 없이 쓰면 팀마다 코드 스타일이 갈린다
- 온보딩 비용이 올라간다
- 테스트 검색성과 일관성이 떨어질 수 있다

### 추천 팀 규칙

1. 기본 style은 `FunSpec` 하나로 통일
2. 정말 필요한 경우에만 `BehaviorSpec` 허용
3. assertions는 Kotest matcher 사용
4. data-driven는 `withData` 또는 팀이 정한 `withXXX` 규칙 사용
5. mutable state를 spec field에 두지 말 것
6. 필요 시 `IsolationMode.InstancePerRoot`를 기본값으로 고려 [22]
7. SpringExtension 등록 방법을 팀 템플릿에 고정 [6][20]

이 정도만 정해도 운영 품질이 크게 올라간다.

---

## 19. JUnit 5를 잘 쓰는 방법

Kotest를 선택하지 않더라도 JUnit을 "잘" 쓰면 충분히 훌륭하다.

### 추천 포인트

1. `@ParameterizedTest` 적극 활용 [10]
2. `@Nested`로 문맥 구조화 [9]
3. `assertAll`로 여러 필드 검증 묶기 [13]
4. Kotlin에서는 backtick 함수명 적극 활용
5. `@TestInstance(PER_CLASS)`를 무분별하게 전역 기본값으로 바꾸지 말기  
   공식 문서도 전역 lifecycle 변경은 일관되게 적용하지 않으면 예측 불가능성을 만들 수 있다고 경고한다. [21]
6. Spring Boot에서는 slice test 우선, `@SpringBootTest` 최소화 [2]

### JUnit으로도 충분한 경우

- 전형적인 web/service/repository 테스트
- Java 혼합
- 사내 공통 확장 재사용
- 신규 인원 온보딩이 매우 빈번

---

## 20. Kotest를 잘 쓰는 방법

Kotest는 "도입하면 자동으로 좋아지는 도구"가 아니다.  
잘 써야 강점이 나온다.

### 추천 포인트

1. **assertions만 먼저 도입**해도 가치가 크다. [5]
2. spec style을 1-2개로 제한한다. [12]
3. state 공유를 피한다. Kotest 기본값은 `SingleInstance`다. [22]
4. data test는 row를 data class로 표현해 도메인 의미를 살린다. [7]
5. property test를 validation/rule logic에 적극 사용한다. [16]
6. Spring integration에서는 SpringExtension 등록을 템플릿화한다. [6]
7. project config 위치/설정을 팀에서 고정한다. [20]

---

## 21. 도메인 테스트에서 Kotest가 특히 좋은 이유

테스트가 "if this then that" 수준을 넘어 비즈니스 룰을 설명해야 한다면 Kotest 장점이 커진다.

예: 배송비 정책

```kotlin
enum class Grade { BASIC, VIP }

class ShippingPolicy {
    fun fee(totalAmount: Int, grade: Grade): Int =
        when {
            totalAmount >= 50_000 -> 0
            grade == Grade.VIP -> 0
            else -> 3_000
        }
}
```

Kotest 예시:

```kotlin
class ShippingPolicyTest : BehaviorSpec({

    val policy = ShippingPolicy()

    given("기본 회원의 주문 금액이 5만원 미만이면") {
        `when`("배송비를 계산하면") {
            then("3000원을 반환한다") {
                policy.fee(49_000, Grade.BASIC) shouldBe 3_000
            }
        }
    }

    given("VIP 회원이면") {
        `when`("주문 금액이 5만원 미만이어도") {
            then("배송비는 무료다") {
                policy.fee(10_000, Grade.VIP) shouldBe 0
            }
        }
    }

    given("주문 금액이 5만원 이상이면") {
        `when`("회원 등급과 상관없이") {
            then("배송비는 무료다") {
                policy.fee(50_000, Grade.BASIC) shouldBe 0
                policy.fee(50_000, Grade.VIP) shouldBe 0
            }
        }
    }
})
```

이건 단순히 예쁘다는 문제가 아니라, **테스트를 정책 문서처럼 읽을 수 있다**는 장점이다.

---

## 22. custom matcher를 만들면 Kotest 강점이 더 커진다

Kotest를 정말 잘 쓰는 팀은 custom matcher를 만든다.  
이때 테스트가 도메인 언어를 말하게 된다.

예:

```kotlin
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

data class Order(val status: String, val amount: Int)

fun haveStatus(expected: String) = Matcher<Order> { actual ->
    MatcherResult(
        actual.status == expected,
        { "expected status=$expected but was ${actual.status}" },
        { "expected status not to be $expected" }
    )
}

class OrderMatcherTest : FunSpec({
    test("주문 상태를 검증한다") {
        val order = Order(status = "PAID", amount = 3000)
        order should haveStatus("PAID")
    }
})
```

이런 matcher는 규모가 커질수록 가치가 올라간다.

- `haveStatus("PAID")`
- `bePublishedArticle()`
- `haveAmount(1000)`
- `beRetryableFailure()`

JUnit에서도 helper를 만들 수 있지만, Kotest matcher DSL은 이 용도에 훨씬 잘 맞는다.

---

## 23. 자주 하는 실수

## 23.1 Kotest에서 너무 많은 style을 섞는다
- `FunSpec`, `ShouldSpec`, `BehaviorSpec`, `WordSpec`를 팀원마다 다르게 쓰면 유지보수성이 떨어진다.

## 23.2 Kotest에서 mutable field state를 공유한다
- 기본 `SingleInstance`를 이해하지 못하면 flaky test가 나오기 쉽다. [22]

## 23.3 Spring context 테스트를 너무 많이 `@SpringBootTest`로 쓴다
- 프레임워크 선택과 무관하게 안 좋은 습관
- slice test를 우선 사용 [2]

## 23.4 JUnit의 dynamic test를 parameterized test처럼 생각한다
- lifecycle이 다르다. 개별 dynamic test마다 `@BeforeEach`/`@AfterEach`가 적용되지 않는다. [15]

## 23.5 Kotest SpringExtension 등록을 잊는다
- constructor injection 기반 Kotest Spring test에서 자주 당황하는 포인트 [6]

## 23.6 "Kotest면 모든 테스트가 좋아진다"고 생각한다
- controller slice test처럼 표준성이 더 중요한 테스트는 JUnit이 더 나을 수 있다.

---

## 24. 추천 조합

### 추천 조합 1 - 가장 현실적
- Spring Boot context/slice test: **JUnit 5**
- domain/service/validation unit test: **Kotest**
- assertions는 전체 프로젝트에서 **Kotest matcher 허용**

이 조합이 가장 무난하다.

### 추천 조합 2 - 점진 도입
- 1개월: JUnit + Kotest assertions
- 2-3개월: 신규 순수 unit test는 Kotest
- 그 다음: 팀 만족도 보고 Spring integration으로 확대 여부 결정

### 추천 조합 3 - Kotlin-first 풀 도입
- 모든 신규 테스트 Kotest
- spec style은 `FunSpec` 기본 + `BehaviorSpec` 제한 허용
- property testing 적극 사용
- team template 제공

---

## 25. 내 추천

질문이 "코틀린이면 JUnit 대신 Kotest가 더 좋냐?"라면, 내 실무 답변은 이렇다.

### 답변 1 - 무조건 갈아타라? 아니다
Spring Boot 프로젝트에서 JUnit Jupiter는 여전히 아주 좋은 기본값이다.  
특히 사내 표준화, Java 혼합, 온보딩, 외부 예제 호환성 면에서는 여전히 강하다. [1][8][19]

### 답변 2 - 그래도 Kotest를 진지하게 볼 이유는 충분하다
특히 Kotlin codebase에서 다음은 Kotest가 확실히 좋다.

- assertions 가독성 [5]
- data-driven testing [7]
- property testing [16]
- DSL 기반 spec 구조 [12]
- Kotlin constructor injection을 활용한 Spring test 작성 경험 [6]

### 답변 3 - 가장 좋은 도입 순서
나는 보통 다음을 추천한다.

1. **Kotest assertions 먼저**
2. **순수 Kotlin unit test는 Kotest**
3. **Spring 통합 테스트는 팀이 원하면 Kotest, 아니면 JUnit 유지**

이렇게 가면 장점은 얻고 리스크는 줄일 수 있다.

---

## 26. 바로 복붙해서 시작할 수 있는 템플릿

## 26.1 JUnit + Kotest assertions 템플릿

```kotlin
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SimpleJUnitTemplateTest {

    @Test
    fun `간단한 검증`() {
        val result = 1 + 2
        result shouldBe 3
    }
}
```

---

## 26.2 Kotest FunSpec 템플릿

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SimpleKotestTemplateTest : FunSpec({

    test("간단한 검증") {
        val result = 1 + 2
        result shouldBe 3
    }
})
```

---

## 26.3 Kotest SpringBootTest 템플릿

```kotlin
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@ApplyExtension(SpringExtension::class)
class UserServiceIntegrationTest(
    private val userService: UserService
) : FunSpec({

    test("서비스가 정상 동작한다") {
        userService.create("kim").name shouldBe "kim"
    }
})
```

---

## 27. 최종 요약

### JUnit 5를 선택해도 좋은 경우
- Spring Boot 기본 흐름을 최대한 그대로 가져가고 싶다
- Java 혼합 프로젝트다
- 사내 표준화와 온보딩이 중요하다
- 표준 어노테이션 기반 모델이 더 익숙하다

### Kotest를 선택하면 특히 좋은 경우
- Kotlin-first 팀이다
- 테스트를 더 읽기 좋게 만들고 싶다
- data-driven/property testing을 적극 활용하고 싶다
- assertions DSL 생산성이 중요하다
- domain/service/validation 로직 테스트 비중이 높다

### 실무 최적 해법
- **JUnit vs Kotest를 "하나만 선택" 문제로 보지 말고**
- **JUnit + Kotest assertions -> 일부 Kotest spec -> 필요 시 확대**로 가져가는 것이 가장 현실적이다.

---

## 참고 자료

[1] Spring Boot Testing Reference, "Testing", official Spring Boot reference  
https://docs.spring.io/spring-boot/reference/testing/index.html

[2] Spring Boot Testing Reference, "Testing Spring Boot Applications", official Spring Boot reference  
https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html

[3] Kotest Quick Start, official Kotest docs  
https://kotest.io/docs/quickstart/

[4] Kotest Framework Setup / Quick Start, JVM uses JUnit Platform  
https://kotest.io/docs/quickstart/

[5] Kotest Assertions, official Kotest docs  
https://kotest.io/docs/assertions/assertions.html

[6] Kotest Spring Extension, official Kotest docs  
https://kotest.io/docs/extensions/spring.html

[7] Kotest Data Driven Testing, official Kotest docs  
https://kotest.io/docs/framework/datatesting/data-driven-testing.html

[8] JUnit User Guide Overview, official JUnit docs  
https://docs.junit.org/5.10.2/user-guide/index.html

[9] JUnit User Guide - Nested Tests  
https://docs.junit.org/5.10.2/user-guide/index.html#writing-tests-nested

[10] JUnit User Guide - Parameterized Tests  
https://docs.junit.org/5.10.2/user-guide/index.html#writing-tests-parameterized-tests

[11] Kotest JVM setup on JUnit Platform  
https://kotest.io/docs/next/framework/project-setup.html

[12] Kotest Testing Styles, official Kotest docs  
https://kotest.io/docs/framework/testing-styles.html

[13] JUnit Jupiter Assertions, official JUnit docs  
https://docs.junit.org/5.10.2/user-guide/index.html#writing-tests-assertions

[14] Kotest Inspectors, official Kotest docs  
https://kotest.io/docs/assertions/inspectors.html

[15] JUnit User Guide - Dynamic Tests  
https://docs.junit.org/5.10.2/user-guide/index.html#writing-tests-dynamic-tests

[16] Kotest Property Testing, official Kotest docs  
https://kotest.io/docs/proptest/property-based-testing.html

[17] Kotest Lifecycle Hooks, official Kotest docs  
https://kotest.io/docs/framework/lifecycle-hooks.html

[18] Kotest Simple Extensions / Project Config, official Kotest docs  
https://kotest.io/docs/framework/extensions/simple-extensions.html  
https://kotest.io/docs/framework/project-config.html

[19] JUnit Jupiter Extension Model, official JUnit docs  
https://docs.junit.org/5.10.2/user-guide/index.html#extensions

[20] Kotest Project Level Config, official Kotest docs  
https://kotest.io/docs/framework/project-config.html

[21] JUnit User Guide - Test Instance Lifecycle  
https://docs.junit.org/5.10.2/user-guide/index.html#writing-tests-test-instance-lifecycle

[22] Kotest Isolation Modes, official Kotest docs  
https://kotest.io/docs/framework/isolation-mode.html

[23] Spring Boot Kotlin guide, "Testing with JUnit" and JUnit Kotlin support  
https://spring.io/guides/tutorials/spring-boot-kotlin/

[24] Kotest Overview / homepage, coroutine support and Kotlin-first positioning  
https://kotest.io/

[25] Kotest Timeouts / Concurrency docs  
https://kotest.io/docs/framework/timeouts.html  
https://kotest.io/docs/framework/concurrency6.html
