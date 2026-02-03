# Claude Code 토큰 절약 가이드

## 1. 컨텍스트 관리

| 명령 | 용도 |
|------|------|
| `/clear` | 대화 초기화 (새 토픽 시작 시) |
| `/compact` | 컨텍스트 수동 압축 |
| `/context` | 현재 컨텍스트 사용량 확인 |
| `/cost` | 비용 확인 |

> **팁**: 오래된 대화는 계속 토큰을 소비하므로 토픽이 바뀌면 `/clear` 사용

---

## 2. 모델 선택

| 모델 | 비용 (입력/출력 MTok당) | 용도 |
|------|------------------------|------|
| **Haiku** | $1 / $5 | 간단한 작업, 빠른 탐색 |
| **Sonnet** | $3 / $15 | 일반 개발 (기본값) |
| **Opus** | $15 / $75 | 복잡한 아키텍처 설계 |

```bash
/model haiku   # 간단한 작업
/model sonnet  # 일반 작업
/model opus    # 복잡한 분석
```

---

## 3. 효율적인 프롬프트

### 나쁜 예
```
코드 개선해줘
```

### 좋은 예
```
src/payments/validator.ts의 expiry date 검증 버그 수정해줘.
에러: TypeError: Cannot read property 'email' of undefined
```

> **핵심**: 파일 경로 + 에러 메시지 명시 = 탐색 비용 절감

---

## 4. 파일 읽기 최적화

```bash
# 나쁨: 전체 로그 읽기
cat large-log.log

# 좋음: 필터링된 출력
./gradlew test 2>&1 | grep -A 5 "FAIL"
```

---

## 5. Plan Mode 활용

복잡한 작업은 `Shift+Tab`으로 Plan Mode 진입:

- 실행 전 미리보기로 잘못된 접근 방지
- 재작업 비용 절감
- 읽기 전용 도구만 사용

---

## 6. 작업 분할

```bash
# 나쁨: 한 번에 큰 작업
> 전체 결제 서비스 리팩토링해줘

# 좋음: 단계별 진행
> PaymentService에 입력 검증 추가
[완료 후]
> 에러 핸들링 추가
[완료 후]
> 별도 클래스로 추출
```

---

## 7. CLAUDE.md 최적화

- **500줄 이하 유지**
- 자세한 내용은 Skills(`.claude/skills/`)로 분리
- 반복되는 컨텍스트는 문서화하여 탐색 비용 절감

---

## 8. 설정 최적화

### 기본 모델 설정

```json
// ~/.claude/settings.json
{
  "model": "sonnet"
}
```

### Extended Thinking 토큰 제한

```json
{
  "max-thinking-tokens": 8000
}
```

---

## 실전 팁 요약

| # | 팁 |
|---|-----|
| 1 | 토픽 바뀌면 `/clear` |
| 2 | 간단한 작업은 Haiku |
| 3 | 파일 경로 명시 |
| 4 | 에러 메시지 복붙 |
| 5 | 큰 작업은 나눠서 |
| 6 | `/cost`로 비용 모니터링 |

---

## 참고

- 일일 정상 범위: 약 $6~12
- Plan Mode: 복잡한 작업 전 검토용
- Subagent: 장시간 작업 격리 (메인 세션 토큰 절약)
