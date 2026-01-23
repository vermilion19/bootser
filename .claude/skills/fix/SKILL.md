---
name: fix
description: 에러 메시지를 분석하고 코드 수정
user-invocable: true
---

## 작업

에러 메시지를 분석하여 원인을 파악하고 수정합니다.

### 에러 소스

- `$ARGUMENTS`로 전달된 에러 메시지
- 최근 빌드 에러 (`./gradlew build` 결과)
- 최근 테스트 실패 (`./gradlew test` 결과)
- 런타임 에러 로그

### 수정 단계

1. **에러 분석**: 에러 메시지, 스택트레이스 파싱
2. **원인 파악**: 관련 코드 위치 찾기
3. **컨텍스트 확인**: 주변 코드, 의존성 확인
4. **수정안 제시**: 수정 코드 작성
5. **검증 방법**: 수정 후 확인 방법 안내

### 일반적인 에러 유형별 대응

| 에러 유형 | 확인 사항 |
|----------|----------|
| `NullPointerException` | null 체크, Optional 사용 |
| `CompilationError` | import, 타입 불일치, 문법 |
| `BeanCreationException` | 의존성 주입, 빈 설정 |
| `SQLException` | 쿼리 문법, 컬럼명, 제약조건 |
| `TestFailure` | assertion, mock 설정, 테스트 데이터 |

### 출력 형식

```markdown
## 에러 분석

### 에러 요약
- 에러 타입: XXXException
- 발생 위치: ClassName.java:123
- 원인: 간단한 원인 설명

### 상세 분석
원인에 대한 상세 설명...

### 수정 방법
파일: `src/main/.../ClassName.java`

수정 전:
\`\`\`java
// 문제 코드
\`\`\`

수정 후:
\`\`\`java
// 수정된 코드
\`\`\`

### 검증
- [ ] 빌드 확인: `./gradlew build`
- [ ] 테스트 확인: `./gradlew test`
```

### 사용 예시

```
/fix                              # 최근 빌드/테스트 에러 수정
/fix NullPointerException at...   # 특정 에러 메시지로 수정
/fix build                        # 빌드 에러 수정
/fix test                         # 테스트 실패 수정
```