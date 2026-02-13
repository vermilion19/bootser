# IntelliJ Terminal + Codex CLI 효율 사용 가이드 (Skills / MCP 중심)

> 전제: IntelliJ IDEA Terminal에서 `npm i -g @openai/codex`로 **Codex CLI 설치 완료**.  
> 목적: **코딩 생산성 극대화**(계획→수정→검증→리뷰 루프를 안정적으로).

---

## 0) 한 줄 요약
- **프로파일(권한/승인) 3종** + **Skills로 반복작업 표준화** + **MCP(특히 JetBrains MCP)로 IDE 실행/검사 연결**  
→ Codex를 “코드 생성기”가 아니라 **작업 루프 자동화 에이전트**로 쓰게 됨.

---

## 1) Codex CLI 기본 루프 (매일 쓰는 습관)

### 1.1 세션 이어가기(컨텍스트 비용 절감)
- `codex resume` : 최근 세션에서 선택
- `codex resume --last` : 마지막 세션 즉시 재개

### 1.2 TUI에서 빠른 조작(핵심 8개)
- `/plan` : 구현 전에 계획부터
- `/permissions` : 세션 중 권한(자동/읽기 등) 전환
- `/diff` : 변경사항 즉시 확인
- `/review` : diff 기반 2차 리뷰(작업 트리 최소 개입)
- `/mention` : 특정 파일/폴더를 대상으로 지정
- `/compact` : 대화가 길어지면 요약해서 컨텍스트 절약
- `/model` : 모델/추론 레벨 전환
- `/mcp` : 연결된 MCP 도구 확인

### 1.3 “검증 루프”를 빠르게 만드는 TUI 팁
- 메시지에 `@`로 파일 경로 퍼지 검색해 컨텍스트 정확히 지정
- 한 줄을 `!`로 시작하면 로컬 쉘 커맨드 실행 → 출력이 대화에 붙음  
  예) `! ./gradlew test`

---

## 2) 안전 vs 속도를 “프로파일”로 고정하기 (config.toml)

### 2.1 추천: 프로파일 3종
- **readonly**: 읽기 전용(리뷰/분석용)
- **auto_safe**: 워크스페이스 쓰기 + 필요 시 승인(기본)
- **auto_strict**: 워크스페이스 쓰기 + 신뢰 낮은 동작은 승인

### 2.2 예시: `~/.codex/config.toml`
> 필요하면 프로젝트별 `.codex/config.toml`로도 관리 가능.

```toml
# ~/.codex/config.toml
model = "gpt-5.3-codex"
sandbox_mode    = "workspace-write"
approval_policy = "on-request"

[sandbox_workspace_write]
network_access = false

[profiles.readonly]
sandbox_mode    = "read-only"
approval_policy = "on-request"

[profiles.auto_safe]
sandbox_mode    = "workspace-write"
approval_policy = "on-request"

[profiles.auto_strict]
sandbox_mode    = "workspace-write"
approval_policy = "untrusted"
```

### 2.3 운영 팁
- 기본은 **network_access=false** 유지 → 필요할 때만 켜기
- 큰 리팩터링/광범위 수정은 **auto_strict**로, 단순 리뷰는 **readonly**로

---

## 3) Skills: 반복 작업을 “표준 운영 절차(SOP)”로 패키징

### 3.1 Skills 핵심 개념
- Skill = Codex가 특정 작업을 **항상 같은 방식**으로 수행하게 만드는 지침 패키지
- 구성: 폴더 + `SKILL.md`(필수) + (선택) 스크립트/템플릿/레퍼런스

### 3.2 배치 위치
- 팀 공유(추천): `YOUR_REPO/.agents/skills/`
- 개인 공통: `~/.agents/skills/`

### 3.3 만들기(가장 쉬운 방법)
- `codex` TUI에서 다음을 입력:
  - `$skill-creator`

### 3.4 바로 쓰는 Spring/Java 스킬 템플릿 3개

#### (1) spring-bugfix
`YOUR_REPO/.agents/skills/spring-bugfix/SKILL.md`
```md
---
name: spring-bugfix
description: Spring Boot/Java 버그 수정 작업일 때만 사용. 재현 커맨드/테스트가 있으면 failing test를 먼저 만들고 최소 수정으로 통과시킨다. 단순 설명/리뷰만 필요할 때는 사용하지 않는다.
---

## Goal
- 재현 → 원인 파악 → 최소 수정 → 회귀 테스트 추가 → 테스트 통과 → diff 요약

## Workflow
1) 재현 방법 확보(있으면 그대로 사용, 없으면 최소 재현 테스트 작성)
2) 실패하는 테스트/스텝 먼저 만들기
3) 원인 후보 2~3개로 좁혀 작은 수정으로 검증
4) 수정 후 unit test 실행(필요 시 통합 테스트)
5) /diff로 변경 요약 + 리스크 포인트 목록화
```

#### (2) spring-feature-slice
```md
---
name: spring-feature-slice
description: Spring Boot 기능 추가 시 사용. 요구사항을 API/도메인/DB/테스트로 쪼개 작은 수직 슬라이스로 단계 구현한다. 전면 구조 변경에는 사용하지 않는다.
---

## Workflow
1) /plan 으로 작은 단계 계획 제안
2) API 계약(요청/응답/에러) 명시
3) 가장 작은 수직 슬라이스(Controller→Service→Repo→Test) 먼저 통과
4) 성능/예외/트랜잭션 처리는 2단계로 분리
5) /review로 PR 전 점검
```

#### (3) jpa-performance
```md
---
name: jpa-performance
description: JPA/Hibernate 성능 문제(N+1, fetch 전략, flush, 쿼리 폭증 등) 대응에만 사용. 단순 기능 추가에는 사용하지 않는다.
---

## Workflow
1) 병목 증상 정리(느린 엔드포인트/쿼리 수/락 등)
2) 재현 가능한 측정 기준 만들기(쿼리 횟수, 응답시간, 테스트)
3) 원인 분류: N+1 / eager / pagination+fetch join / batch size / index / tx boundary
4) 최소 변경으로 개선 + 개선 전/후 지표 기록
```

---

## 4) MCP: Codex에 “도구/컨텍스트” 연결하기 (가장 큰 생산성 점프)

### 4.1 MCP가 주는 가치
- Codex가 단순 텍스트가 아니라 **도구 호출**로:
  - IDE 검사 결과(에러/경고 위치)
  - Run Configuration(테스트/린트/포맷) 실행 결과
  - 문서/DB/외부 컨텍스트(연결한 MCP 서버에 따라)
를 직접 가져와 **검증 루프를 자동화**할 수 있음.

### 4.2 MCP 서버 추가(예시)
```bash
codex mcp add context7 -- npx -y @upstash/context7-mcp
```
- Codex TUI에서 `/mcp`로 연결 확인

### 4.3 config.toml로 MCP 관리(공유/재현성 좋음)
```toml
[mcp_servers.context7]
command = "npx"
args = ["-y", "@upstash/context7-mcp"]
```

---

## 5) IntelliJ와 “붙여서” 쓰기: JetBrains MCP Server 연결

> 목표: Codex가 IDE의 **inspections 에러/경고**와 **Run Configuration 실행**을 직접 활용하게 만들기.

### 5.1 IntelliJ에서 MCP Server 활성화
- `Settings | Tools | MCP Server`
- `Enable MCP Server`
- 필요 시 `Copy Stdio Config`로 `command/args/env` 확보

### 5.2 Codex에 JetBrains MCP 등록(개념)
- IntelliJ에서 복사한 **stdio config**의 `command/args/env`를 Codex 쪽 MCP 설정으로 옮김
- 연결 후 Codex에서 `/mcp`로 확인

### 5.3 운영 루프(강추)
- (수정 후) **IDE 검사 → 테스트 실행**을 자동화 루틴으로 고정
  1) get_file_problems(IDE 검사 기반)
  2) execute_run_configuration(테스트/포맷/린트 실행)
- 큰 작업은 “작은 단계”로 쪼개고 매 단계 검증

---

## 6) 성공률 높은 프롬프트 템플릿 3종

### 6.1 버그 수정 템플릿
```text
/plan
현상:
- (증상 1~2줄)

재현:
- (명령어/테스트/HTTP 호출)

완료 조건:
- (테스트 통과 / 특정 에러 제거 / 회귀 테스트 추가)
- /diff 로 변경 요약 + 리스크 3개 정리
```

### 6.2 기능 추가(작은 슬라이스)
```text
/plan
요구사항:
- ...

제약:
- 기존 API 호환
- 새 의존성 추가 금지(필요하면 먼저 제안)

검증:
- 단위 테스트 추가
- (가능하면) 통합 테스트 or Run Configuration 실행
```

### 6.3 마무리 점검(필수 습관)
- `/diff`로 내가 직접 확인
- `/review`로 2차 리뷰 받아 리스크/엣지케이스 체크

---

## 7) 오늘 적용하는 “최소 셋업 체크리스트”
- [ ] `~/.codex/config.toml`에 readonly/auto_safe/auto_strict 프로파일 구성
- [ ] repo에 `.agents/skills/` 생성 후 스킬 1~2개부터 적용
- [ ] MCP 서버 1개라도 먼저 연결해 `/mcp`로 확인
- [ ] IntelliJ MCP Server 켜고 Codex와 연결(가능하면)
- [ ] “수정 → IDE 검사 → 테스트 실행 → diff/review” 루프를 고정

---

## 부록) 추천 작업 순서(초기 30분)
1) config.toml 프로파일 만들기  
2) 스킬 1개(spring-bugfix)부터 적용  
3) JetBrains MCP Server 연결 시도  
4) 버그 1개를 “/plan → 수정 → 테스트 → /diff → /review”로 끝내보기

