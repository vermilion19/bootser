# Claude Code 스킬(Skill) 작성 가이드

Claude Code에서 `/commit`, `/review` 같은 슬래시 커맨드를 직접 만드는 방법을 정리한 문서입니다.
이 프로젝트의 `.claude/skills/` 에 있는 실제 스킬들을 기반으로 작성했습니다.

---

## 목차

1. [스킬이란?](#1-스킬이란)
2. [파일 위치와 구조](#2-파일-위치와-구조)
3. [SKILL.md 작성법](#3-skillmd-작성법)
4. [Frontmatter 옵션 레퍼런스](#4-frontmatter-옵션-레퍼런스)
5. [인수($ARGUMENTS) 사용법](#5-인수arguments-사용법)
6. [스킬 호출 제어 패턴](#6-스킬-호출-제어-패턴)
7. [여러 스킬을 순서대로 실행하기](#7-여러-스킬을-순서대로-실행하기)
8. [보조 파일 활용](#8-보조-파일-활용)
9. [실전 스킬 예시 모음](#9-실전-스킬-예시-모음)
10. [이 프로젝트의 스킬 목록](#10-이-프로젝트의-스킬-목록)
11. [자주 하는 실수](#11-자주-하는-실수)

---

## 1. 스킬이란?

스킬은 Claude Code에서 `/skill-name` 형태로 호출하는 **커스텀 슬래시 커맨드**입니다.
마크다운 파일(SKILL.md)에 지시사항을 작성하면, 호출 시 Claude가 그 지시사항을 따릅니다.

```
/commit          → git 변경사항 분석 후 Conventional Commit 형식으로 커밋
/review          → 코드 리뷰 수행
/test MyService  → MyService 테스트 코드 생성
```

---

## 2. 파일 위치와 구조

### 위치에 따른 적용 범위

| 위치 | 경로 | 적용 범위 |
|------|------|---------|
| 프로젝트 전용 | `.claude/skills/<skill-name>/SKILL.md` | 현재 프로젝트만 |
| 전역 | `~/.claude/skills/<skill-name>/SKILL.md` | 모든 프로젝트 |

> 같은 이름이면 프로젝트 스킬이 전역 스킬을 덮어씁니다.

### 디렉토리 구조

```
.claude/skills/
├── SKILLS.md                  # 스킬 목록 문서 (선택, 하지만 권장)
├── commit/
│   └── SKILL.md
├── review/
│   └── SKILL.md
└── my-skill/
    ├── SKILL.md               # 필수
    ├── reference.md           # 선택: 참고 자료
    └── examples/
        └── sample.md          # 선택: 예시 파일
```

---

## 3. SKILL.md 작성법

```markdown
---
name: skill-name
description: 이 스킬이 무엇을 하는지, 언제 쓰는지 설명
user-invocable: true
---

## 작업

여기에 Claude에게 줄 지시사항을 작성합니다.

인수는 $ARGUMENTS로 받습니다.

### 단계

1. 첫 번째 할 일
2. 두 번째 할 일
3. 세 번째 할 일
```

### 작성 원칙

- **지시사항은 명령형**으로 작성 ("분석하세요", "생성하세요")
- **단계를 번호로 나열**하면 순서가 보장됨
- **출력 형식을 코드블록으로 예시**로 보여주면 결과물 품질이 올라감
- **사용 예시**를 포함하면 사용자가 인수 형식을 이해하기 쉬움

---

## 4. Frontmatter 옵션 레퍼런스

```yaml
---
name: skill-name                    # 스킬 이름 (생략 시 디렉토리명 사용)
description: 설명                   # Claude가 자동 호출 여부 판단에 사용
user-invocable: true                # false면 사용자가 /skill-name으로 호출 불가
disable-model-invocation: false     # true면 /skill-name 으로만 호출, Claude 자동 실행 안 됨
allowed-tools: Read, Grep, Bash     # 허용할 도구 목록 (생략 시 전체 허용)
context: fork                       # fork = 서브에이전트에서 격리 실행
agent: Explore                      # context: fork 와 함께 사용할 에이전트 타입
model: claude-opus-4-6              # 이 스킬에 사용할 모델 지정
argument-hint: [파일경로] [옵션]    # 자동완성 시 보여줄 힌트
---
```

### 핵심 옵션 비교

| 옵션 조합 | 사용자 `/skill` 호출 | Claude 자동 호출 | 언제 쓰나 |
|---|---|---|---|
| 기본값 | 가능 | 가능 | 일반 스킬 |
| `disable-model-invocation: true` | 가능 | 불가 | `/commit`, `/deploy` 처럼 타이밍 제어 필요할 때 |
| `user-invocable: false` | 불가 | 가능 | 다른 스킬에서 내부적으로만 쓰는 보조 스킬 |
| `context: fork` | 가능 | 가능 | 긴 탐색 작업을 메인 컨텍스트와 격리할 때 |

---

## 5. 인수($ARGUMENTS) 사용법

사용자가 `/my-skill 인수1 인수2` 처럼 호출하면 스킬 내에서 변수로 받을 수 있습니다.

```markdown
# SKILL.md 내에서

$ARGUMENTS          → 전달된 모든 인수 ("인수1 인수2")
$ARGUMENTS[0]       → 첫 번째 인수 ("인수1")
$ARGUMENTS[1]       → 두 번째 인수 ("인수2")
$0                  → 첫 번째 인수 (축약형)
$1                  → 두 번째 인수 (축약형)
```

### 예시

```markdown
---
name: migrate
description: 컴포넌트를 한 프레임워크에서 다른 프레임워크로 마이그레이션
argument-hint: [컴포넌트명] [from] [to]
---

$0 컴포넌트를 $1에서 $2로 마이그레이션하세요.
```

```
/migrate SearchBar React Vue
→ SearchBar 컴포넌트를 React에서 Vue로 마이그레이션하세요.
```

### 인수가 없을 때 기본 동작 정의

```markdown
### 분석 대상

- `$ARGUMENTS`가 없으면: 현재 열려있는 파일을 대상으로 실행
- `$ARGUMENTS`가 클래스명이면: 해당 클래스를 찾아서 분석
- `$ARGUMENTS`가 파일 경로면: 해당 파일을 직접 읽어서 분석
```

---

## 6. 스킬 호출 제어 패턴

### 패턴 1: 수동 전용 스킬 (커밋, 배포 등)

```yaml
---
name: commit
description: 변경사항 분석 후 커밋
disable-model-invocation: true   # 반드시 /commit 으로만 실행, Claude가 알아서 실행하면 안 됨
---
```

> 커밋이나 배포처럼 **사람이 명시적으로 승인해야 하는 작업**에 사용합니다.

### 패턴 2: 내부용 보조 스킬

```yaml
---
name: load-conventions
description: 프로젝트 코딩 컨벤션 로드
user-invocable: false            # 사용자는 /load-conventions 로 직접 호출 불가
---

이 프로젝트의 코딩 컨벤션은 다음과 같습니다:
- 들여쓰기: 4칸 스페이스
- 네이밍: camelCase
...
```

> `/` 메뉴에 노출하지 않고, 다른 스킬에서 내부적으로만 참조하게 할 때 사용합니다.

### 패턴 3: 격리 실행 (긴 탐색 작업)

```yaml
---
name: deep-search
description: 코드베이스 전체를 깊이 탐색하여 분석
context: fork
agent: Explore
---
```

> 수백 개 파일을 탐색하는 작업처럼 메인 대화 컨텍스트를 오염시키지 않아야 할 때 사용합니다.

### 패턴 4: 도구 제한 (읽기 전용)

```yaml
---
name: safe-audit
description: 파일을 읽기만 하고 수정하지 않는 감사
allowed-tools: Read, Grep, Glob
---
```

---

## 7. 여러 스킬을 순서대로 실행하기

Claude Code는 스킬이 다른 스킬을 직접 호출하는 문법은 없습니다.
대신 **최상위 스킬의 지시사항에 순서를 명시**하면 Claude가 각 스킬을 순서대로 실행합니다.

### 예시: release 스킬 (review → test → commit → push)

```markdown
---
name: release
description: 코드 검증 후 커밋하고 원격에 푸시까지 수행
disable-model-invocation: true
---

다음을 순서대로 실행하세요. 각 단계가 실패하면 즉시 중단하세요.

## 1단계: 코드 리뷰
/review 스킬을 실행하여 Critical 이슈가 없는지 확인하세요.
Critical 이슈가 있으면 즉시 중단하고 사용자에게 보고하세요.

## 2단계: 테스트 실행
다음 명령어로 테스트를 실행하세요:
./gradlew test

테스트가 실패하면 중단하세요.

## 3단계: 커밋
/commit 스킬을 실행하세요.

## 4단계: 푸시
사용자에게 푸시 여부를 확인한 후 git push origin main 을 실행하세요.
```

### 포인트

- 각 단계에 **중단 조건**을 명시하면 실패 시 자동으로 멈춤
- **사용자 확인이 필요한 단계**는 명시적으로 "사용자에게 확인"을 넣을 것
- 하위 스킬은 `/skill-name` 으로 명시하거나 "~~ 스킬을 실행하세요"로 지시

---

## 8. 보조 파일 활용

SKILL.md에서 같은 디렉토리의 파일을 참조할 수 있습니다.

```
.claude/skills/review/
├── SKILL.md
├── checklist.md     ← 리뷰 체크리스트 상세 내용
└── examples/
    ├── good.md      ← 좋은 코드 예시
    └── bad.md       ← 나쁜 코드 예시
```

```markdown
# SKILL.md 내에서 참조

자세한 체크리스트는 [checklist.md](checklist.md)를 참고하세요.
좋은 코드 예시는 [examples/good.md](examples/good.md)를 참고하세요.
```

> 스킬 지시사항이 길어질 때 파일로 분리하면 유지보수가 편합니다.

---

## 9. 실전 스킬 예시 모음

### 예시 1: 가장 간단한 스킬

```markdown
---
name: hello
description: 간단한 인사 스킬
---

$ARGUMENTS 에게 프로젝트 Booster에 오신 것을 환영하는 메시지를 출력하세요.
```

```
/hello 철수
→ 철수님, Booster 프로젝트에 오신 것을 환영합니다!
```

---

### 예시 2: 인수를 받아 파일 분석

```markdown
---
name: domain-check
description: 도메인 엔티티가 DDD 규칙을 따르는지 점검
argument-hint: [클래스명 또는 파일경로]
---

## 작업

$ARGUMENTS 로 지정된 도메인 엔티티를 분석하고 아래 규칙 준수 여부를 점검하세요.

### 점검 항목

- [ ] `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 선언 여부
- [ ] 정적 팩토리 메서드(`create()`) 존재 여부
- [ ] Setter 메서드 존재 여부 (있으면 위반)
- [ ] 비즈니스 메서드로 상태 변경하는지 여부
- [ ] 도메인 유효성 검증이 엔티티 내부에 있는지 여부

### 출력 형식

```
## DDD 엔티티 점검 결과: {클래스명}

### 준수 항목
- [x] ...

### 위반 항목
- [ ] ... → 권장 수정 방법: ...
```
```

---

### 예시 3: 다중 인수 + 조건 분기

```markdown
---
name: gen-event
description: 도메인 이벤트 클래스와 핸들러를 생성
argument-hint: [이벤트명] [도메인명]
---

## 작업

$0 이벤트를 $1 도메인에 생성합니다.

### 생성할 파일

1. `apps/{$1}-service/src/.../event/{$0}Event.java`
2. `apps/{$1}-service/src/.../event/{$0}EventListener.java`

### 생성 규칙

- 이벤트 클래스는 `record`로 작성
- 핸들러는 `@EventListener` 어노테이션 사용
- Outbox 패턴 적용 (OutboxEvent 저장 포함)

$ARGUMENTS[2]가 `kafka`이면 Kafka Producer 코드도 함께 생성하세요.
```

```
/gen-event WaitingCanceled waiting kafka
```

---

### 예시 4: 결과물을 파일로 저장

```markdown
---
name: erd-doc
description: 현재 엔티티 클래스를 분석하여 ERD 문서 생성
---

## 작업

프로젝트의 모든 `@Entity` 클래스를 찾아 ERD 문서를 생성하고
`docs/ERD.md` 파일로 저장하세요.

### 포함 내용

1. 엔티티 목록과 설명
2. 필드 정보 (이름, 타입, 제약조건)
3. 연관관계 (1:N, N:M 등)
4. Mermaid ERD 다이어그램

### Mermaid 형식 예시

\`\`\`mermaid
erDiagram
    USER ||--o{ ORDER : places
    ORDER ||--|{ ORDER_ITEM : contains
\`\`\`
```

---

## 10. 이 프로젝트의 스킬 목록

현재 `.claude/skills/` 에 등록된 스킬 목록입니다.

| 스킬 | 설명 | 인수 예시 |
|------|------|---------|
| `/api` | Controller REST API 문서 생성 | `WaitingController` |
| `/commit` | Conventional Commit 형식 커밋 | `all` |
| `/event-flow` | 분산 시스템 이벤트 흐름 문서화 | `WaitingService` |
| `/explain` | 코드 동작 방식 설명 | `WaitingService simple` |
| `/fix` | 에러 메시지 분석 및 코드 수정 | `build` / `test` |
| `/make-md` | 직전 응답을 마크다운 파일로 저장 | `docs/my-doc` |
| `/perf-audit` | 성능 병목 분석 및 최적화 제안 | `WaitingService deep` |
| `/push-after-commit` | 커밋 후 원격 push까지 수행 | - |
| `/query-plan` | DB 쿼리 실행 계획 분석 | `WaitingRepository` |
| `/quiz` | 시니어 면접 질문 생성 | `WaitingService` |
| `/resilience` | 고가용성 및 장애 복구 전략 점검 | `WaitingFacade` |
| `/review` | 코드 리뷰 수행 | 파일경로 / PR번호 |
| `/roadmap` | 기술 습득 학습 경로 생성 | `Kafka 심화` |
| `/spec-review` | 스펙 문서 리뷰 및 정제 | `docs/spec.md` |
| `/spec-writer` | 요구사항을 스펙 문서로 정리 | `웨이팅 SMS 알림 기능` |
| `/test` | 단위 테스트 코드 생성 | `WaitingService` |
| `/anti-pattern` | 안티패턴 탐지 및 개선 | `service/` |
| `/responsive` | 반응형 레이아웃 검토 | `NavigationBar.tsx` |
| `/ui-gen` | 현대적인 UI 컴포넌트 생성 | `사용자 목록 테이블` |

---

## 11. 자주 하는 실수

### ❌ description을 비워두기

```yaml
# 나쁜 예
---
name: my-skill
---
```

`description`이 없으면 Claude가 언제 이 스킬을 써야 할지 판단하지 못합니다.
**자동 호출이 전혀 안 될 수 있습니다.**

---

### ❌ 커밋/배포 스킬에 disable-model-invocation 빠뜨리기

```yaml
# 나쁜 예: Claude가 알아서 커밋해버릴 수 있음
---
name: commit
description: 커밋 수행
---
```

```yaml
# 좋은 예
---
name: commit
description: 커밋 수행
disable-model-invocation: true
---
```

---

### ❌ 지시사항이 너무 모호함

```markdown
# 나쁜 예
코드를 리뷰하세요.
```

```markdown
# 좋은 예
다음 관점에서 코드를 리뷰하고, Critical / Warning / Suggestion 으로 분류하여 출력하세요:
1. NullPointerException 위험
2. N+1 쿼리 문제
3. 보안 취약점
```

---

### ❌ 출력 형식을 지정하지 않음

출력 형식 예시를 코드블록으로 보여주면 결과물의 일관성이 크게 올라갑니다.

```markdown
### 출력 형식

\`\`\`markdown
## 결과

### Critical (반드시 수정)
- [ ] 파일:라인 - 내용

### Warning (수정 권장)
- [ ] 파일:라인 - 내용
\`\`\`
```

---

## 참고

- 이 프로젝트의 실제 스킬 파일: `.claude/skills/`
- 스킬 목록 문서: `.claude/skills/SKILLS.md`
- Claude Code 공식 문서: https://docs.anthropic.com/claude-code