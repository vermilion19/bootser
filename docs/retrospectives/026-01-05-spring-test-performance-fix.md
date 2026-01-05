# [DevLog] 테스트 속도 개선기: 범인은 DB가 아니었다

> **요약** > CI/CD 빌드 속도 저하의 원인을 JPA의 DDL 생성 비용(`create-drop`)으로 추측했으나, 검증 결과 이는 원인이 아니었다.  
> 로그 분석을 통해 스프링 컨텍스트가 반복적으로 재시작되는 현상을 발견했고, **테스트 환경 통합(Context Caching)**을 통해 수행 시간을 획기적으로 단축했다.

---

## 1. 문제 상황 (Problem)

기능이 추가됨에 따라 통합 테스트(Integration Test) 개수가 늘어났고, 전체 테스트 수행 시간이 비정상적으로 길어졌다.
로컬 환경에서도 전체 테스트를 돌리는 데 3분 이상 소요되어 개발 생산성(Feedback Loop)이 급격히 떨어지는 문제가 발생했다.

## 2. 1차 가설 및 검증 (Failed Hypothesis)

### 2.1 가설 설정
JPA의 `hibernate.ddl-auto: create-drop` 설정 때문에 매 테스트마다 테이블을 `DROP` 하고 `CREATE` 하는 과정이 병목일 것이라 판단했다.

### 2.2 검증 과정
* 프로파일링 도구를 통해 DDL 실행 시간을 측정했다.
* 테스트 환경의 DB 설정을 변경하여 DDL 비용을 최소화해 보았다.

### 2.3 결과: "상관없음"
 스키마 생성이 아무리 빨라져도 전체 테스트 속도에는 유의미한 변화가 없었다.
**→ "직관에 의존한 튜닝은 실패한다"는 사실을 확인.**

## 3. 진짜 원인 분석 (Root Cause Analysis)

### 3.1 로그 분석
테스트 실행 로그를 면밀히 살펴보니, 테스트 클래스가 넘어갈 때마다 **Spring Boot 로고가 반복해서 출력**되고 있었다.
즉, **스프링 컨텍스트(ApplicationContext)가 캐싱되지 않고 계속 재시작(Reloading)**되고 있었던 것이다.

### 3.2 Spring Test Context Caching 원리
스프링 부트 테스트는 효율을 위해 컨텍스트를 캐싱하여 재사용한다. 하지만 **설정(Configuration)이 1이라도 달라지면** 캐시를 파기하고 새로 띄운다.

내 프로젝트의 테스트 코드들을 확인해 보니 아래와 같은 문제들이 있었다.
* 테스트 클래스마다 서로 다른 `@MockBean`을 선언하여 사용.
* `@TestPropertySource` 등으로 환경 변수를 제각각 설정.
* 이로 인해 스프링은 매번 "새로운 환경"이라고 인식하여 서버를 껐다 켜고 있었다.

## 4. 해결 방법: 환경 통합 (Environment Unification)

모든 통합 테스트가 **단 하나의 스프링 컨텍스트**만 공유해서 쓰도록 구조를 리팩토링했다.

### 4.1 추상 부모 클래스 도입 (`IntegrationTestSupport`)
모든 통합 테스트가 상속받을 부모 클래스를 만들고, 설정을 이곳으로 몰아넣었다.

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {
    
    // 자주 쓰는 MockBean은 부모 클래스에 한 번만 정의하여 컨텍스트 오염 방지
    @MockBean
    protected ImageService imageService;

    @Autowired
    protected ObjectMapper objectMapper;
    
    // ... 공통 설정
}

// 별도의 설정 어노테이션 없이 상속만으로 해결
class RestaurantServiceTest extends IntegrationTestSupport {

    @Autowired
    private RestaurantService restaurantService;

    @Test
    void registerRestaurant() {
        // 부모 클래스(IntegrationTestSupport)에 정의된 imageService Mock 재사용
        given(imageService.upload(any())).willReturn("url");

        // ... 테스트 로직
    }
}
```
