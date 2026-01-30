# Claude Code 컨텍스트 관리 가이드

## 개요

Claude Code는 대화가 길어지거나 프로젝트 규모가 클수록 컨텍스트 윈도우가 빠르게 차올라 답변 품질이 저하되고 토큰 효율이 떨어진다. 자동 compact와 수동 제어를 활용하여 이를 관리할 수 있다.

---

## 자동 Compact

- 기본적으로 **컨텍스트 윈도우의 95%가 차면 자동으로 compact** 수행
- 이전 툴 출력을 먼저 제거하고, 필요시 대화를 요약
- 사용자의 요청과 핵심 코드 스니펫은 보존

### 임계값 조정

환경 변수 `CLAUDE_AUTOCOMPACT_PCT_OVERRIDE`로 compact가 트리거되는 시점을 조절한다.

**PowerShell (현재 세션만)**

```powershell
$env:CLAUDE_AUTOCOMPACT_PCT_OVERRIDE = "50"
```

**PowerShell (영구 설정)**

```powershell
[System.Environment]::SetEnvironmentVariable("CLAUDE_AUTOCOMPACT_PCT_OVERRIDE", "50", "User")
```

**Linux / Mac**

```bash
export CLAUDE_AUTOCOMPACT_PCT_OVERRIDE=50
```

| 값 | 동작 |
|----|------|
| 50 | 50% 차면 compact (더 일찍, 더 자주 압축) |
| 70 | 70% 차면 compact (균형적) |
| 95 | 기본값 (거의 가득 찰 때 압축) |

> 설정 후 Claude Code를 **새로 시작**해야 적용된다.

---

## 수동 제어 명령어

| 명령 | 용도 |
|------|------|
| `/compact` | 즉시 수동 압축 |
| `/compact API 변경사항 중심으로` | 특정 내용을 보존하며 압축 |
| `/context` | 현재 컨텍스트 사용량 확인 |
| `/clear` | 컨텍스트 완전 초기화 |

---

## 컨텍스트 절약 팁

### 1. 서브에이전트 활용

탐색/검색 작업을 서브에이전트에 위임하면 메인 대화의 컨텍스트를 아낄 수 있다. 서브에이전트는 독립적인 컨텍스트 윈도우를 가지며 자체적으로 auto-compact를 수행한다.

### 2. 작업 전환 시 초기화

관련 없는 작업으로 전환할 때 `/clear`로 컨텍스트를 초기화하면 불필요한 토큰 낭비를 방지할 수 있다.

### 3. 낮은 임계값 설정

50~70%로 설정하면 답변 품질이 저하되기 전에 미리 압축되어 안정적인 품질을 유지할 수 있다.

### 4. CLAUDE.md에 compact 지시사항 추가

```markdown
# Compact Instructions
When compacting, always preserve:
- The full list of modified files
- Any test commands
- API endpoint definitions
```

---

## 관련 설정

### 출력 토큰 제한

```bash
# 기본값: 32000, 최대: 64000
export CLAUDE_CODE_MAX_OUTPUT_TOKENS=32000
```

이 값을 늘리면 auto-compact 전 사용 가능한 컨텍스트가 줄어드는 점에 유의한다.
