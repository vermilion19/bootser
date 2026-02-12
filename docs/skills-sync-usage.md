# Codex 스킬 동기화 사용법

이 문서는 여러 PC(Windows/macOS)에서 동일한 Codex 스킬 상태를 유지하는 방법을 설명합니다.

## 전제

- 스킬 원본: `./.claude/skills`
- 로컬 설치 위치: `~/.codex/skills`
- Windows 래퍼: `./scripts/sync-skills.ps1`
- macOS 래퍼: `./scripts/sync-skills.sh`

## 기본 사용

### Windows (PowerShell)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-skills.ps1
```

### macOS (bash/zsh)

```bash
cd /path/to/bootser
chmod +x ./scripts/sync-skills.sh
./scripts/sync-skills.sh
```

## 옵션

### 1) 정리 동기화 (`Prune`)

소스(`.claude/skills`)에 없는 스킬을 로컬(`.codex/skills`)에서 제거합니다.

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-skills.ps1 -Prune
```

macOS:

```bash
./scripts/sync-skills.sh --prune
```

주의:
- 로컬에만 있던 스킬은 삭제될 수 있습니다.
- `.system` 폴더는 삭제 대상에서 제외됩니다.

### 2) Pull 생략 (`SkipPull`)

`git pull` 없이 동기화만 실행합니다.

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-skills.ps1 -SkipPull
```

macOS:

```bash
./scripts/sync-skills.sh --skip-pull
```

## 권장 운영 방식 (여러 PC)

1. 스킬 변경 후 저장소에 커밋/푸시
2. 다른 PC에서 저장소 최신화
3. OS에 맞는 `sync-skills` 스크립트 실행

## 문제 해결

- `git pull` 실패: 충돌 또는 인증 문제를 먼저 해결
- 동기화 실패: `.claude/skills` 경로와 파일 권한 확인
- macOS 실행 권한 오류: `chmod +x ./scripts/sync-skills.sh` 실행
