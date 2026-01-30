# 서브에이전트 자동 위임 규칙

아래 조건에 해당하는 작업이 감지되면, 직접 처리하지 말고 반드시 해당 서브에이전트에 Task 도구로 위임하라.

## 위임 규칙

### architect
다음 키워드나 맥락이 포함된 경우 `architect` 서브에이전트에 위임:
- 시스템 아키텍처 설계 또는 검토 요청
- 분산 시스템 구조 (Kafka 토폴로지, Redis Cluster 구성)
- 서비스 간 통신 패턴 선택 (동기/비동기, 이벤트 기반)
- MSA 분리/통합 전략
- 대규모 트래픽 대응 아키텍처 질문
- `[ARCHITECT]` 키워드로 시작하는 요청

### db-tuner
다음 키워드나 맥락이 포함된 경우 `db-tuner` 서브에이전트에 위임:
- SQL 쿼리 성능 분석 또는 튜닝 요청
- EXPLAIN ANALYZE 결과 해석
- 인덱스 설계/추가/제거 전략
- 테이블 파티셔닝 설계
- JPA N+1 문제의 DB 관점 해결
- PostgreSQL 설정 최적화
- `[DBA]` 키워드로 시작하는 요청

### concurrency-expert
다음 키워드나 맥락이 포함된 경우 `concurrency-expert` 서브에이전트에 위임:
- Deadlock, Race Condition, Livelock 분석
- Virtual Threads 사용 패턴 질문
- 분산 락 설계 (Redisson, ShedLock)
- synchronized, Lock, Atomic 관련 설계
- 동시성 테스트 전략
- 스레드 안전성 검토

### infra-planner
다음 키워드나 맥락이 포함된 경우 `infra-planner` 서브에이전트에 위임:
- Dockerfile, Docker Compose 설정 작성/검토
- Kubernetes 리소스 설계 (Deployment, HPA, PDB)
- 배포 전략 (Rolling, Blue-Green, Canary)
- 오토스케일링 정책 수립
- Observability 스택 구성 (Prometheus, Grafana)
- CI/CD 파이프라인 설계

## 위임하지 않는 경우

다음은 메인 에이전트가 직접 처리한다:
- 단순 코드 작성, 버그 수정, 리팩토링
- 테스트 코드 생성
- Git 커밋, PR 생성
- 파일 탐색, 코드 설명
- 스킬(`/commit`, `/review` 등) 실행

## 위임 형식

서브에이전트에 위임할 때는 Task 도구를 사용하며, 다음을 포함한다:
1. 사용자의 원래 질문/요청 전문
2. 관련 파일 경로 (이미 파악된 경우)
3. 현재 프로젝트 컨텍스트에서 필요한 배경 정보
