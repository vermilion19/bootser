---
name: commit
description: 변경사항 분석 후 Conventional Commit 형식으로 커밋 메시지 생성 및 커밋
user-invocable: true
---

## 작업

Git 변경사항을 분석하여 적절한 커밋 메시지를 생성하고 커밋합니다.

### 단계

1. `git status`로 변경된 파일 확인
2. `git diff --staged`로 스테이징된 변경사항 확인 (없으면 `git diff`로 전체 확인)
3. 변경 내용을 분석하여 커밋 메시지 작성
4. 사용자에게 메시지 확인 후 커밋 실행

### 커밋 메시지 형식 (Conventional Commit)

```
<type>(<scope>): <subject>

<body>
```

### Type 종류

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 설정 파일 등 기타 변경 |
| `style` | 코드 포맷팅, 세미콜론 등 |
| `perf` | 성능 개선 |

### 규칙

- subject는 50자 이내, 명령형으로 작성
- body는 왜(why) 변경했는지 설명
- 한글 커밋 메시지 허용
- 스테이징 안 된 파일이 있으면 먼저 확인 요청

### 사용 예시

```
/commit              # 스테이징된 변경사항 커밋
/commit all          # 모든 변경사항 add 후 커밋
```