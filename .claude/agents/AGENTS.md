# 서브에이전트 가이드

프로젝트에서 사용 가능한 서브에이전트 목록입니다. 메인 에이전트가 상황에 따라 자동으로 위임하며, `[ARCHITECT]`, `[DBA]` 등 키워드로 직접 호출할 수도 있습니다.

---

## `architect`

**대규모 트래픽 처리를 위한 고가용성 시스템 설계 전문가**

분산 시스템 아키텍처(Kafka, Redis Cluster) 설계 검토, Java 25 가상 스레드 활용 최적화, 데이터 정합성을 위한 분산 트랜잭션 패턴을 가이드합니다. 시퀀스 다이어그램과 구조도를 적극 활용합니다.

- **모델**: Opus
- **도구**: Read, Grep, Glob, WebSearch, WebFetch
- **자동 위임 조건**: 시스템 아키텍처 설계/검토, MSA 전략, 서비스 간 통신 패턴, `[ARCHITECT]` 키워드

---

## `db-tuner`

**PostgreSQL 실행 계획 분석 및 인덱스 최적화 전문가**

EXPLAIN ANALYZE 해석, 단일/복합/부분/커버링 인덱스 설계, JPA 생성 쿼리 성능 검토, 대용량 테이블 파티셔닝, 커넥션 풀 및 트랜잭션 격리 수준을 가이드합니다.

- **모델**: Opus
- **도구**: Read, Grep, Glob, WebSearch, WebFetch
- **자동 위임 조건**: 쿼리 튜닝, 인덱스 설계, EXPLAIN 결과 해석, PostgreSQL 설정, `[DBA]` 키워드

---

## `concurrency-expert`

**Java Virtual Threads와 동기화 이슈 전문가**

Deadlock, Race Condition, Livelock 발생 조건 분석, Virtual Threads 환경에서의 pinning 경고, 분산 락(Redisson, ShedLock) 설계 및 범위 최적화를 담당합니다.

- **모델**: Opus
- **도구**: Read, Grep, Glob, WebSearch, WebFetch
- **자동 위임 조건**: 동시성 버그 분석, Virtual Threads 패턴, 분산 락 설계, 스레드 안전성 검토

---

## `infra-planner`

**Docker, Kubernetes 설정 및 서버 스케일 아웃 전략 전문가**

Dockerfile/Docker Compose 최적화, Kubernetes 리소스 설계(HPA, PDB), 무중단 배포 전략(Rolling, Blue-Green, Canary), Observability 스택 구성을 담당합니다.

- **모델**: Opus
- **도구**: Read, Grep, Glob, Bash, WebSearch, WebFetch
- **자동 위임 조건**: 컨테이너 설정, K8s 리소스 설계, 배포 전략, 오토스케일링, CI/CD

---

## 자동 위임 규칙

위임 규칙은 `.claude/rules/agents.md`에 정의되어 있으며, 메인 에이전트가 다음 기준으로 판단합니다:

| 요청 유형 | 위임 대상 |
|----------|----------|
| 시스템 설계, MSA 전략 | `architect` |
| SQL 튜닝, 인덱스, EXPLAIN | `db-tuner` |
| Deadlock, Race Condition, 분산 락 | `concurrency-expert` |
| Docker, K8s, 배포 전략 | `infra-planner` |
| 코드 작성, 버그 수정, 스킬 실행 | 메인 에이전트 직접 처리 |

## 참고

- 모든 서브에이전트는 **읽기 전용**(설계/분석만 제시, 코드 직접 작성 안함)으로 동작합니다. (`infra-planner`만 Bash 포함)
- 세션 재시작 후 적용됩니다.
- `/agents` 명령으로 현재 등록된 에이전트를 확인할 수 있습니다.
