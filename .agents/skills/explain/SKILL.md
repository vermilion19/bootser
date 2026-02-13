---
name: explain
description: 코드의 동작 방식을 쉽게 설명
---

## 작업

$ARGUMENTS로 지정된 코드를 분석하여 이해하기 쉽게 설명합니다.

### 설명 포함 내용

#### 1. 목적 (Why)
- 이 코드가 왜 존재하는지
- 어떤 문제를 해결하는지

#### 2. 동작 흐름 (How)
- 실행 순서를 단계별로 설명
- 주요 분기점과 조건

#### 3. 핵심 로직 (What)
- 중요한 알고리즘이나 패턴
- 비즈니스 규칙

#### 4. 의존성 (Dependencies)
- 어떤 클래스/서비스와 연결되어 있는지
- 외부 시스템 연동

#### 5. 시각화 (Diagram)
- 필요시 ASCII 다이어그램으로 흐름 표현

### 설명 형식

```markdown
## [클래스/메서드명] 설명

### 목적
한 줄 요약

### 동작 흐름
1. 첫 번째 단계
2. 두 번째 단계
   └─ 조건 분기 설명
3. 세 번째 단계

### 핵심 로직
- 주요 포인트 설명

### 다이어그램
┌─────────┐    ┌─────────┐
│ Client  │───▶│ Service │
└─────────┘    └─────────┘

### 관련 코드
- `ClassName.java:123` - 연관 설명
```

### 설명 수준 조절

- 기본: 중급 개발자가 이해할 수 있는 수준
- `$ARGUMENTS`에 `simple` 포함: 주니어/비개발자용 쉬운 설명
- `$ARGUMENTS`에 `deep` 포함: 내부 구현까지 상세 설명

### 사용 예시

```
/explain WaitingService           # WaitingService 설명
/explain registerWaiting simple   # 쉬운 설명
/explain OutboxPattern deep       # 상세 설명
/explain                          # 현재 열린 파일 설명
```