# Load Testing Interpretation Guide

## 문서 목적

이 문서는 부하테스트를 했을 때 **숫자를 어떻게 읽고**, **어떤 지점에서 병목이라고 판단하며**, **무엇을 먼저 바꿔야 하는지**를 실무적으로 정리한 가이드다. 목표는 단순히 그래프를 보는 법이 아니라, 테스트 결과를 바탕으로 **성능 개선 우선순위를 스스로 정할 수 있게 만드는 것**이다.

---

## 1. 부하테스트를 보는 관점부터 바꾸기

부하테스트는 보통 이렇게 오해하기 쉽다.

- 평균 응답시간이 낮으면 좋은 테스트다
- CPU가 낮으면 성능이 좋은 것이다
- RPS가 많이 나오면 합격이다
- 에러만 없으면 괜찮다

실제로는 다 불완전한 판단이다.

부하테스트의 본질은 아래 두 질문에 답하는 것이다.

1. **어느 부하 수준에서 사용자 경험 기준이 깨지는가**
2. **그 순간 어떤 내부 자원이 먼저 포화되는가**

즉 성능 테스트 결과는 “빠르다/느리다”가 아니라 다음처럼 읽어야 한다.

- 300 RPS까지는 p95 200ms 이하로 안정적
- 500 RPS부터 p99 급등
- 700 RPS부터 에러율 증가
- 같은 시점에 DB 커넥션 풀 pending이 증가
- 따라서 최초 병목은 애플리케이션 CPU가 아니라 DB access path 혹은 connection pool 구간

이 문서 전체는 이 해석 흐름을 몸에 익히는 데 초점을 둔다.

---

## 2. 부하테스트 전에 반드시 정해야 하는 것

### 2.1 테스트 합격 기준(SLO)

부하테스트는 기준 없이 하면 숫자 놀이가 된다. 먼저 아래를 정해야 한다.

- 어떤 API/시나리오를 테스트하는가
- 목표 부하는 얼마인가
- 합격 기준 latency는 얼마인가
- 허용 가능한 에러율은 얼마인가
- 얼마나 오래 버텨야 하는가

예시:

- 주문 조회 API: 300 RPS에서 p95 150ms 이하, 에러율 0.5% 미만
- 주문 생성 API: 100 RPS에서 p95 400ms 이하, 에러율 1% 미만
- 피크 20분 동안 처리량 급락 없이 유지

테스트는 항상 위처럼 **질문이 먼저**고 **그래프는 나중**이다.

### 2.2 테스트 시나리오

시나리오가 섞이면 해석이 어려워진다. 보통 다음처럼 나눈다.

- 읽기 위주 시나리오: 목록 조회, 상세 조회
- 쓰기 위주 시나리오: 생성, 수정, 삭제
- 혼합 시나리오: 실제 서비스 사용 패턴 반영
- 피크 시나리오: 특정 이벤트/프로모션 시간대
- 장시간 시나리오: 누수, 적체, fragmentation 확인

### 2.3 테스트 단계

부하를 한 번에 세게 주지 말고 단계적으로 올리는 편이 해석이 쉽다.

1. Smoke test
2. Baseline test
3. Step load test
4. Stress / breakpoint test
5. Spike test
6. Soak test

특히 **step load test**가 실무적으로 가장 유용하다.

예:

- 100 RPS 5분
- 200 RPS 5분
- 400 RPS 5분
- 600 RPS 5분
- 800 RPS 5분

이렇게 해야 “언제부터 꺾였는가”를 읽기 쉽다.

---

## 3. 핵심 지표는 이 네 가지부터 본다

Google SRE에서 강조하는 Golden Signals 관점으로 보면, 처음엔 이 네 개만 보면 된다.

### 3.1 Latency

- 평균보다 **p95, p99**가 중요하다
- 성공 요청과 실패 요청 latency를 분리해서 봐야 한다
- 평균 latency가 좋은데 p99만 나쁘면 tail latency 문제다

### 3.2 Traffic

- 요청 수(RPS)
- 동시 사용자 수(VU)
- 처리량(TPS)
- 큐 consumer 처리 속도

### 3.3 Errors

- HTTP 5xx
- timeout
- rejected request
- lock timeout
- DB connection acquire timeout
- circuit breaker open

### 3.4 Saturation

- CPU 사용률
- 메모리/heap 사용률
- GC pause
- thread pool active/rejected
- DB connection pool active/pending
- DB CPU / IOPS / lock wait
- queue depth / lag
- external API timeout

부하테스트를 해석할 때는 항상 이렇게 묻는다.

> 트래픽을 올렸더니 latency가 언제 깨졌고, errors가 언제 늘었으며, 같은 순간 어떤 saturation 지표가 먼저 찼는가?

---

## 4. 평균보다 p95/p99가 중요한 이유

평균은 tail 문제를 숨긴다.

예를 들어 아래 수치를 보자.

- 평균 120ms
- p50 90ms
- p95 280ms
- p99 2.4s

이건 “서비스가 대체로 빠르다”가 아니라, **대부분은 빠르지만 일부는 매우 느리다**는 뜻이다. 실제 사용자 경험은 p99 같은 tail에서 크게 망가질 수 있다.

이런 경우 자주 나오는 원인:

- 특정 느린 SQL 몇 개
- lock 경합
- 특정 shard/hot partition
- GC pause
- external API tail latency
- queue backlog

따라서 성능 개선은 평균을 더 낮추는 것보다 **tail을 만든 원인을 제거하는 것**이 더 가치가 큰 경우가 많다.

---

## 5. 결과를 읽는 패턴별 해석법

### 패턴 A. 처리량 증가 + latency 안정 + 에러율 낮음

해석:

- 아직 여유가 있다
- 부하를 더 올려도 된다
- 현재 구간은 시스템이 선형적으로 확장되는 중이다

액션:

- 다음 step으로 부하를 더 올린다
- baseline으로 저장한다

### 패턴 B. 트래픽을 더 넣는데 throughput은 거의 안 늘고 latency만 급격히 증가

해석:

- 전형적인 saturation 구간
- 대기열이 생기고 있다
- 병목 자원이 이미 꽉 찼다

원인 후보:

- CPU 바운드
- DB connection pool 바운드
- DB slow query / lock wait
- thread pool 바운드
- external dependency saturation
- queue consumer 처리 한계

액션:

- 같은 시간축에서 saturation 지표를 대조한다
- 최초로 꺾인 지표를 찾는다

### 패턴 C. 평균은 괜찮은데 p99만 급등

해석:

- tail latency 문제
- 전체가 느린 게 아니라 일부 요청만 크게 느리다

원인 후보:

- 특정 API path
- 특정 SQL
- 락 경쟁
- 특정 노드만 느림
- 메모리 pressure / GC
- 재시도 폭증

액션:

- tracing으로 느린 span 상위 3개를 본다
- URI / endpoint / query별 분포를 분리해서 본다

### 패턴 D. 자원은 낮은데 에러가 먼저 난다

해석:

- 순수 성능 병목보다는 구성/정책 문제일 수 있다

원인 후보:

- rate limit
- 너무 작은 pool size
- timeout 설정 불일치
- deadlock / lock timeout
- 브로커 quota
- external API quota
- 애플리케이션 버그

액션:

- 에러 타입별 집계부터 본다
- 500 한 종류로 묶지 말고 cause를 나눈다

### 패턴 E. 앱/DB는 여유 있어 보이는데 기대 RPS가 안 나온다

해석:

- 테스트 도구 또는 네트워크가 병목일 수 있다

원인 후보:

- 부하 생성기 CPU 부족
- 부하 생성기 네트워크 부족
- 테스트 스크립트 자체가 무거움
- 한 머신에서 너무 많은 VU 실행

액션:

- load generator CPU, network, memory 확인
- 필요하면 분산 부하 생성 사용

---

## 6. 병목별로 어떻게 판단할까

### 6.1 CPU 바운드

관찰 패턴:

- CPU가 높게 유지됨
- 처리량 증가가 둔화됨
- latency 급증
- DB는 상대적으로 여유 있음

의심 구간:

- JSON 직렬화/역직렬화
- 암호화/서명
- 압축
- 정규식
- 비효율적인 루프
- DTO 변환
- 로그 포맷팅

우선 액션:

- hot path profiling
- 샘플링 프로파일링
- 로그/직렬화 비용 점검
- 캐시 가능한 계산 분리
- unnecessary object allocation 감소

### 6.2 DB 바운드

관찰 패턴:

- 앱 CPU는 높지 않음
- API latency는 높음
- tracing에서 DB span 비중 큼
- slow query / lock wait 증가
- connection pool pending 증가

의심 구간:

- 인덱스 미흡
- N+1
- 과도한 join
- pagination 비효율
- 락 경합
- 불필요한 flush
- transaction 범위 과다

우선 액션:

- 상위 느린 SQL 추출
- explain analyze 확인
- 인덱스 점검
- N+1 / fetching 전략 점검
- batch 처리 여부 확인
- transaction 범위 축소

### 6.3 Connection Pool / Thread Pool 바운드

관찰 패턴:

- CPU는 낮은데 latency 급증
- pending acquire 증가
- thread rejected / queue length 증가
- timeout 증가

의심 구간:

- pool size 부족
- 긴 트랜잭션
- 외부 호출을 트랜잭션 안에서 수행
- blocking call 과다
- thread pool 공유 사용

우선 액션:

- pool pending 지표 확인
- 트랜잭션 시간 측정
- 외부 IO를 트랜잭션 밖으로 분리
- thread pool 별도 분리
- queue 및 concurrency 제한 재설계

### 6.4 Queue / Async 적체

관찰 패턴:

- 초반엔 괜찮다가 시간이 갈수록 latency 악화
- lag 증가
- backlog 누적
- consumer throughput < producer throughput

의심 구간:

- consumer 처리 속도 부족
- batch size 부적절
- retry storm
- dead letter 처리 미흡
- downstream dependency 병목

우선 액션:

- lag / backlog 그래프 확인
- consumer concurrency 조정
- batch 크기 조정
- retry 정책 점검
- downstream 처리 능력 확인

### 6.5 External Dependency 병목

관찰 패턴:

- 내부 자원 여유
- 특정 외부 호출 span만 느림
- timeout / circuit breaker 증가

우선 액션:

- external API latency 분리 계측
- timeout / retry / bulkhead 재점검
- stub/mock으로 내부 한계와 분리 측정

---

## 7. Grafana / Prometheus 데이터를 읽을 때 주의할 점

### 7.1 summary 대신 histogram을 이해하기

서비스 전체 p95, p99를 인스턴스 단위로 합산하려면 histogram 기반이 더 적합하다.

대표 예시:

```promql
histogram_quantile(
  0.95,
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket[5m])
  )
)
```

실무 포인트:

- 여러 pod의 summary quantile 평균은 서비스 전체 p95가 아니다
- 가능하면 histogram bucket을 기준으로 집계한다
- URI, method, status를 적절히 나누되 cardinality 폭발에 주의한다

### 7.2 평균만 보지 말 것

반드시 같이 봐야 하는 것:

- avg
- p50
- p95
- p99
- max
- error rate
- throughput

### 7.3 분리해서 봐야 하는 것

- 성공 요청 vs 실패 요청 latency
- 읽기 API vs 쓰기 API
- cache hit path vs miss path
- internal API vs external dependency 포함 API

---

## 8. 부하테스트 결과를 보고 실제로 무엇을 바꿔야 하나

이건 많은 개발자가 가장 헷갈려하는 지점이다. 아래 순서로 판단하면 된다.

### Step 1. SLO가 깨졌는가

먼저 묻는다.

- p95/p99가 목표를 넘었는가
- 에러율이 목표를 넘었는가
- throughput이 목표에 못 미치는가

SLO를 안 넘었다면 섣불리 최적화하지 않는다. 성능 개선은 비용이다.

### Step 2. 최초로 무너진 지점은 어디인가

- 200 RPS는 괜찮음
- 400 RPS부터 p99 증가
- 500 RPS부터 에러 증가

이렇게 **breakpoint**를 특정한다.

### Step 3. 같은 시간축의 saturation을 본다

- CPU가 먼저 올랐는가
- DB pending이 먼저 올랐는가
- queue lag가 먼저 쌓였는가
- 외부 API latency가 먼저 올랐는가

### Step 4. 한 번에 하나만 바꾼다

좋지 않은 방식:

- 인덱스 추가
- 캐시 추가
- pool size 조정
- timeout 조정
- GC 옵션 변경
- 스레드 수 변경

이걸 한 번에 다 바꿈

좋은 방식:

- 가장 유력한 병목 하나를 골라 변경
- 같은 테스트 프로파일로 재측정
- baseline 대비 차이를 기록

### Step 5. 개선 폭을 정량화한다

예:

- before: 400 RPS에서 p95 420ms, DB pending 60
- after: 400 RPS에서 p95 180ms, DB pending 8

“조금 좋아진 것 같다”가 아니라 수치로 남겨야 한다.

---

## 9. 바로 써먹는 실무형 해석 체크리스트

아래 체크리스트는 테스트가 끝난 직후 그대로 따라가면 된다.

### 9.1 테스트 전 체크리스트

- [ ] 어떤 API/시나리오를 테스트하는지 명확하다
- [ ] 목표 부하(RPS/VU)가 정해져 있다
- [ ] 합격 기준(p95/p99, 에러율)이 정해져 있다
- [ ] 테스트 환경이 운영과 얼마나 다른지 기록했다
- [ ] 외부 의존성(mock/stub 여부)을 기록했다
- [ ] baseline 수치가 있다
- [ ] 앱, DB, 큐, 외부 호출 지표가 동시에 수집된다
- [ ] tracing/log/metrics를 서로 연결할 correlation 방법이 있다

### 9.2 테스트 직후 1차 확인 체크리스트

- [ ] 처리량이 목표에 도달했는가
- [ ] p95, p99가 목표를 만족하는가
- [ ] 에러율이 목표 이내인가
- [ ] 실패 요청과 성공 요청 latency를 분리해서 봤는가
- [ ] 부하가 올라갈수록 latency가 선형/비선형 어느 패턴인지 확인했는가
- [ ] throughput이 더 이상 늘지 않는 구간을 찾았는가

### 9.3 병목 식별 체크리스트

- [ ] CPU가 먼저 포화됐는가
- [ ] 메모리/GC가 먼저 문제를 보였는가
- [ ] DB connection pool pending이 증가했는가
- [ ] slow query, lock wait, deadlock이 있었는가
- [ ] thread pool active/rejected가 증가했는가
- [ ] queue backlog/lag가 누적됐는가
- [ ] external dependency latency가 증가했는가
- [ ] load generator 자체가 병목은 아닌가

### 9.4 해석 품질 체크리스트

- [ ] 평균만 보고 판단하지 않았는가
- [ ] p95/p99를 포함했는가
- [ ] 서비스 전체 latency와 endpoint별 latency를 분리했는가
- [ ] 인스턴스 평균이 아니라 전체 집계 방식이 맞는가
- [ ] summary quantile을 잘못 평균내지 않았는가
- [ ] 테스트 시간축과 모니터링 시간축이 정확히 맞는가

### 9.5 개선 액션 체크리스트

- [ ] 병목 후보를 1개로 좁혔는가
- [ ] 가장 작은 변경으로 검증 가능한가
- [ ] 변경 전/후 비교 실험 계획이 있는가
- [ ] 결과를 before/after 수치로 기록하는가
- [ ] 개선 후에도 같은 시나리오로 재검증하는가
- [ ] 부하 증가 시 다음 병목이 어디로 이동했는지 확인하는가

---

## 10. 증상 → 원인 후보 → 다음 액션 표

| 증상 | 가장 유력한 원인 후보 | 먼저 볼 지표 | 우선 액션 |
|---|---|---|---|
| p95/p99 급등, CPU 높음 | CPU 바운드 | CPU, profiler, hot path | 프로파일링, 객체 생성/직렬화 최적화 |
| p95 급등, 앱 CPU 낮음, DB pending 증가 | DB 또는 connection pool 병목 | Hikari pending, DB latency, slow query | 느린 SQL 분석, pool pending 원인 확인 |
| 평균 괜찮음, p99만 나쁨 | tail latency 문제 | trace, slow span, GC, lock wait | 느린 일부 요청 분리 분석 |
| 에러가 먼저 발생 | 설정/정책/락/쿼터 문제 | error breakdown, timeout, deadlock | 에러 타입별 분석 |
| 시간이 갈수록 느려짐 | queue 적체, leak, pool leak | lag, queue depth, heap, active connection | soak test, 누수/적체 추적 |
| RPS 안 오름, 서버 여유 | load generator 병목 | generator CPU/network | 부하 생성기 분산 또는 증설 |
| 쓰기 API만 느림 | 트랜잭션, 락, flush, 인덱스 | DB lock wait, tx time, SQL | transaction 범위/인덱스/flush 점검 |
| 특정 endpoint만 느림 | 쿼리/캐시 miss/외부 의존성 | endpoint별 latency, trace | URI 단위로 원인 분리 |

---

## 11. 병목별 개선 액션 가이드

### 11.1 DB가 병목일 때

우선순위:

1. 상위 느린 SQL 찾기
2. execution plan 확인
3. 인덱스 보강
4. N+1 제거
5. projection/fetch 전략 수정
6. 트랜잭션 범위 줄이기
7. batch 처리 적용
8. connection pool은 마지막에 조정

잘못된 접근:

- 쿼리는 안 보고 pool size만 키움
- 락 경합은 무시하고 read replica만 붙임

### 11.2 애플리케이션 CPU가 병목일 때

우선순위:

1. profiler로 핫패스 확인
2. 로그 포맷팅/JSON 직렬화 비용 확인
3. 불필요한 객체 생성 감소
4. 캐시로 반복 계산 제거
5. sync/blocking 코드를 분리
6. 필요 시 scale out

### 11.3 Connection pool이 병목일 때

우선순위:

1. 트랜잭션이 너무 긴지 확인
2. 트랜잭션 안에서 외부 API 호출하는지 확인
3. 쿼리 자체가 느린지 확인
4. leak 여부 확인
5. 그 다음에 pool size 재조정

### 11.4 Queue 적체가 병목일 때

우선순위:

1. producer rate와 consumer rate 비교
2. lag 누적 시작 시점 확인
3. batch/concurrency 조정
4. retry storm 있는지 확인
5. downstream 서비스 한계 확인

---

## 12. 부하테스트 결과 보고서 템플릿

아래 템플릿을 그대로 복붙해서 매번 작성하면 해석 품질이 좋아진다.

### 테스트 개요

- 테스트명:
- 날짜:
- 대상 서비스:
- 대상 시나리오:
- 코드 버전/브랜치:
- 환경:
- 테스트 도구:

### 목표

- 목표 부하:
- 목표 p95/p99:
- 목표 에러율:
- 목표 지속 시간:

### 결과 요약

- 최대 안정 부하:
- breakpoint:
- 에러 시작 구간:
- 가장 먼저 포화된 자원:
- 가장 느린 컴포넌트:

### 주요 수치

- throughput:
- p50:
- p95:
- p99:
- error rate:
- CPU:
- heap / GC:
- DB connection pending:
- DB slow query count:
- queue lag:

### 해석

- 사용자 기준으로 어디서 깨졌는가:
- 내부적으로 무엇이 먼저 찼는가:
- 병목 후보 1순위:
- 근거:

### 액션 아이템

1. 
2. 
3. 

### 재검증 계획

- 어떤 변경을 할 것인가:
- 같은 시나리오로 재측정할 것인가:
- 비교 기준은 무엇인가:

---

## 13. 테스트할 때 흔한 실수

### 실수 1. 평균 응답시간만 본다

평균은 tail을 숨긴다. 반드시 p95/p99를 같이 본다.

### 실수 2. 성공/실패 latency를 섞는다

빠른 실패가 평균을 좋아 보이게 만들 수 있다.

### 실수 3. baseline 없이 개선을 시도한다

비교 기준이 없으면 개선인지 착시인지 모른다.

### 실수 4. 여러 변경을 한꺼번에 넣는다

무엇이 효과 있었는지 알 수 없다.

### 실수 5. 테스트 도구가 병목인 걸 놓친다

서비스가 아니라 generator가 먼저 죽을 수 있다.

### 실수 6. 운영과 너무 다른 환경에서 결과를 일반화한다

테스트 환경 차이를 반드시 기록한다.

### 실수 7. read-heavy 테스트만 돌리고 write-heavy를 안 본다

실제 병목은 쓰기와 락에서 먼저 나타날 수 있다.

### 실수 8. 외부 의존성을 분리하지 않는다

내 서비스의 한계와 외부 API의 한계를 섞지 말아야 한다.

---

## 14. 추천 대시보드 구성

### API 대시보드

- RPS
- p50 / p95 / p99
- error rate
- in-flight requests
- endpoint top N latency

### 애플리케이션 대시보드

- CPU
- memory / heap
- GC pause
- thread pool active / queue / rejected
- Hikari active / idle / pending / timeout

### DB 대시보드

- active connections
- slow query count
- average query latency
- p95 query latency
- lock wait
- deadlock
- buffer/cache hit
- IOPS / CPU

### 비동기 / 큐 대시보드

- enqueue rate
- consume rate
- lag
- backlog size
- retry count
- dead-letter count

### 외부 의존성 대시보드

- external API latency
- timeout rate
- circuit breaker open count
- fallback count

---

## 15. 실무에서 가장 유용한 판단 문장들

아래 문장들로 스스로 설명할 수 있으면 해석이 잘 되고 있는 거다.

- “이 테스트의 합격 기준은 p95 300ms 이하와 에러율 1% 미만이다.”
- “400 RPS까지는 선형 증가했지만 500 RPS부터 throughput은 거의 늘지 않고 p99가 급등했다.”
- “같은 시점에 DB connection pending이 증가했기 때문에 최초 병목은 앱 CPU가 아니라 DB access path로 보인다.”
- “평균은 양호하지만 p99가 나쁜 tail latency 문제라서 일부 느린 요청을 tracing으로 분리 분석해야 한다.”
- “이번 개선은 인덱스 추가 한 가지 변경만 적용했고, 같은 시나리오에서 p95가 420ms에서 180ms로 개선됐다.”

이런 식으로 말할 수 있어야 성능 테스트 결과가 숫자에서 판단으로 바뀐다.

---

## 16. 최종 요약

부하테스트를 해석하는 가장 좋은 방법은 아래 한 문장으로 정리된다.

> **부하를 올렸을 때 어느 시점부터 사용자 품질(SLO)이 깨지고, 그 순간 어떤 내부 자원이 먼저 포화되는지 찾는 것**

그래서 반드시 다음 순서로 본다.

1. 목표와 합격 기준을 정한다
2. baseline을 만든다
3. step load로 breakpoint를 찾는다
4. latency, traffic, errors, saturation을 같이 본다
5. 평균보다 p95/p99를 본다
6. 가장 먼저 포화된 자원을 찾는다
7. 한 번에 하나만 바꾸고 재측정한다
8. before/after를 수치로 남긴다

이 과정을 반복하면 부하테스트가 막연한 수치 읽기가 아니라, **재현 가능한 성능 개선 루프**가 된다.

---

## References

- Google SRE Book, *Monitoring Distributed Systems*  
  https://sre.google/sre-book/monitoring-distributed-systems/
- AWS Well-Architected Reliability Pillar, *Test scalability and performance requirements*  
  https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/rel_testing_resiliency_test_non_functional.html
- Grafana k6 Docs, *Automated performance testing*  
  https://grafana.com/docs/k6/latest/testing-guides/automated-performance-testing/
- Grafana k6 Docs, *API load testing*  
  https://grafana.com/docs/k6/latest/testing-guides/api-load-testing/
- Prometheus Docs, *Histograms and summaries*  
  https://prometheus.io/docs/practices/histograms/
