---
name: test
description: 지정된 클래스의 단위 테스트 코드 생성
---

## 작업

$ARGUMENTS로 지정된 클래스의 테스트 코드를 생성합니다.

### 단계

1. 대상 클래스 분석 (메서드, 의존성, 비즈니스 로직)
2. 테스트 시나리오 도출
3. 테스트 코드 작성
4. 테스트 파일 생성

### 테스트 프레임워크

- JUnit 5 (`org.junit.jupiter`)
- AssertJ (`org.assertj.core.api.Assertions`)
- Mockito (`org.mockito`)

### 테스트 코드 규칙

#### 네이밍
```java
@DisplayName("한글로 테스트 설명")
void should_기대결과_when_조건() { }
```

#### 구조 (Given-When-Then)
```java
@Test
@DisplayName("웨이팅 등록 시 대기번호가 발급된다")
void should_issueWaitingNumber_when_register() {
    // given
    var command = new RegisterCommand(...);

    // when
    var result = service.register(command);

    // then
    assertThat(result.getWaitingNumber()).isPositive();
}
```

### 테스트 케이스 종류

| 종류 | 설명 | 예시 |
|------|------|------|
| Happy Path | 정상 동작 | 유효한 입력으로 성공 |
| Edge Case | 경계값 | 빈 리스트, 0, 최대값 |
| Exception | 예외 상황 | null 입력, 중복 등록 |
| State | 상태 변경 | 상태 전이 검증 |

### 필수 테스트 항목

1. **정상 케이스**: 모든 public 메서드
2. **예외 케이스**: 예상되는 예외 상황
3. **경계값**: null, 빈 값, 최대/최소값
4. **상태 검증**: 도메인 엔티티 상태 변경

### 사용 예시

```
/test WaitingService              # WaitingService 테스트 생성
/test src/main/.../Waiting.java   # 파일 경로로 지정
/test                             # 현재 열린 파일 테스트 생성
```