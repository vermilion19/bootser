---
name: quiz
description: 현재 코드 기반의 시니어 면접 질문 생성
user-invocable: true
---

## 작업

$ARGUMENTS로 지정된 코드를 분석하여 시니어 개발자 수준의 기술 면접 질문을 생성합니다. 대규모 트래픽 환경에서의 장애 시나리오를 중심으로 학습을 유도합니다.

### 분석 대상

- `$ARGUMENTS`가 클래스명이면: 해당 클래스의 설계/로직 기반 질문 생성
- `$ARGUMENTS`가 파일 경로면: 해당 파일 분석 후 질문 생성
- `$ARGUMENTS`가 없으면: 현재 변경된 파일 기반 질문 생성

### 질문 생성 관점

#### 1. 동시성 / 트래픽
- 동시 요청 시 Race Condition 발생 가능성
- 분산 락 없이 동시 접근 시 데이터 정합성 문제
- 트래픽 급증 시 병목 지점과 스케일링 전략

#### 2. 장애 시나리오
- 외부 서비스 장애 시 Cascading Failure 방지
- DB 커넥션 풀 고갈 시 대응 전략
- Redis 장애 시 캐시/세션 복구 방안
- Kafka 브로커 다운 시 메시지 손실 방지

#### 3. 데이터 정합성
- 분산 트랜잭션에서의 Eventual Consistency
- Outbox Pattern의 At-least-once 보장과 멱등성
- DB와 캐시 간 정합성 유지 전략

#### 4. 설계 / 아키텍처
- DDD 관점에서의 Aggregate 경계 적절성
- 이벤트 기반 아키텍처의 트레이드오프
- MSA 환경에서의 서비스 간 의존성 관리

#### 5. DB / 쿼리
- 인덱스 설계와 쿼리 실행 계획
- 대용량 데이터 페이징 전략
- 데드락 발생 조건과 회피 방법

### 출력 형식

```markdown
## 면접 질문 - [분석 대상]

### 난이도: ★★★☆☆ (Mid-Senior)

---

### Q1. [질문 제목]

> 현재 코드에서 `WaitingService.register()`는 단일 트랜잭션 내에서 DB 저장과 Redis 업데이트를 수행합니다.
> 동시에 1,000명이 같은 식당에 웨이팅 등록을 시도하면 어떤 문제가 발생할 수 있나요?

**관련 코드**: `WaitingService.java:45`

**힌트**:
- Race Condition 관점에서 생각해보세요
- 분산 환경에서 단일 DB 트랜잭션만으로 충분한지 고민해보세요

<details>
<summary>모범 답안</summary>

#### 예상 문제
1. 대기번호 중복 발급 (Race Condition)
2. Redis와 DB 간 데이터 불일치
3. DB 커넥션 풀 고갈

#### 해결 방안
1. **분산 락**: Redisson을 활용한 락으로 순차 처리
2. **Optimistic Lock**: `@Version` 필드로 충돌 감지
3. **Redis Atomic Operation**: `INCR` 명령으로 원자적 번호 발급

#### 코드 예시
```java
RLock lock = redissonClient.getLock("waiting:" + restaurantId);
try {
    lock.lock(5, TimeUnit.SECONDS);
    // 등록 로직
} finally {
    lock.unlock();
}
```

</details>

---

### Q2. [다음 질문]
...

---

### 추가 학습 키워드
- [키워드1]: 관련 개념 간단 설명
- [키워드2]: 관련 개념 간단 설명
```

### 질문 난이도

| 난이도 | 대상 | 설명 |
|--------|------|------|
| ★★☆☆☆ | Junior | 기본 개념 확인 (트랜잭션, 인덱스 등) |
| ★★★☆☆ | Mid-Senior | 장애 시나리오 대응, 설계 트레이드오프 |
| ★★★★☆ | Senior | 대규모 트래픽, 분산 시스템 심화 |
| ★★★★★ | Architect | 시스템 전체 설계, MSA 전환 전략 |

### 사용 예시

```
/quiz WaitingService               # 이 로직에서 트래픽 급증 시 발생할 문제점은?
/quiz database-schema.sql          # 인덱스 설계와 쿼리 성능 관련 질문 생성
/quiz KafkaConfig                  # 브로커 장애 시 메시지 손실 방지 방안은?
/quiz OutboxMessageRelay           # Outbox 패턴의 한계와 개선 방안 질문
/quiz                              # 현재 변경 파일 기반 질문 생성
```
