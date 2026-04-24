# Performance Test SLO and RPS Goal Guide

## 0. 이 문서의 목적

이 문서는 소프트웨어 성능 테스트를 진행할 때 **목표 RPS**, **응답시간 SLO**, **에러율 기준**, **자원 포화 기준**을 어떤 기준으로 정해야 하는지 정리한 실무형 가이드다.

성능 테스트에서 가장 흔한 실수는 “우리 서버가 몇 RPS까지 버티는가?”만 보는 것이다. 하지만 실제 목적은 조금 다르다.

> **성능 테스트의 핵심은 목표 부하에서 사용자 경험 기준을 만족하는지 확인하고, 그 이상에서 어떤 자원이 먼저 포화되는지 찾는 것이다.**

즉 성능 테스트는 단순히 최대 처리량을 자랑하기 위한 것이 아니라, 다음 질문에 답하기 위한 과정이다.

- 우리 서비스가 예상 피크 트래픽을 감당할 수 있는가?
- 그 트래픽에서 사용자가 받아들이기 어려운 응답 지연이 생기지 않는가?
- 에러율은 허용 가능한 수준인가?
- CPU, DB, 커넥션 풀, 스레드 풀, 큐 중 무엇이 먼저 한계에 도달하는가?
- 성능 개선이 필요한가, 스케일아웃이 필요한가, 아니면 SLO 자체를 조정해야 하는가?

---

## 1. 목표 RPS와 최대 처리 가능 RPS는 다르다

성능 테스트를 시작하기 전에 먼저 이 둘을 구분해야 한다.

### 1.1 목표 RPS

**목표 RPS**는 서비스가 비즈니스상 안정적으로 처리해야 하는 요청량이다.

예를 들어:

```text
주문 생성 API는 피크 시간에 100 RPS를 안정적으로 처리해야 한다.
상품 목록 API는 피크 시간에 800 RPS를 안정적으로 처리해야 한다.
```

이것은 요구사항이다.

목표 RPS는 시스템이 현재 얼마나 버티는지와 무관하게, 비즈니스 예상 사용량과 사용자 수요에서 계산된다.

### 1.2 최대 처리 가능 RPS

**최대 처리 가능 RPS**는 부하테스트를 통해 실제로 측정하는 현재 시스템의 한계다.

예를 들어:

```text
800 RPS까지는 p95 latency 200ms 이하를 유지한다.
1000 RPS부터 p95 latency가 700ms로 증가한다.
1200 RPS부터 에러율이 3%를 넘는다.
```

이것은 측정 결과다.

### 1.3 둘을 섞으면 안 된다

목표 RPS와 최대 처리 가능 RPS를 섞으면 판단이 흐려진다.

- 목표 RPS: 비즈니스가 요구하는 처리량
- 최대 처리 가능 RPS: 현재 시스템이 실제로 감당하는 처리량

성능 테스트의 목적은 다음 두 가지다.

```text
1. 목표 RPS에서 SLO를 만족하는지 확인한다.
2. 목표 RPS 이상에서 어느 지점부터 성능이 깨지는지 확인한다.
```

예를 들어 비즈니스 목표가 500 RPS인데, 테스트 결과 900 RPS까지 버틴다면 현재 구조는 충분히 여유가 있다.  
반대로 목표가 500 RPS인데 400 RPS부터 p95가 깨진다면 성능 개선이 필요하다.

---

## 2. 목표 RPS는 어떤 기준으로 정하나

목표 RPS는 보통 아래 순서로 정한다.

```text
비즈니스 예상 사용량
→ 피크 시간대 트래픽
→ 순간 피크 배수
→ 성장 여유분
→ 안전 여유분
→ 목표 RPS
```

중요한 점은 **일평균 트래픽을 그대로 목표 RPS로 쓰면 대부분 과소추정된다**는 것이다.

일평균 트래픽은 평온해 보이지만, 실제 서비스는 특정 시간대와 특정 이벤트에 요청이 몰린다.

---

## 3. 목표 RPS 계산 예시

쇼핑몰 주문 API를 예로 들어보자.

```text
하루 예상 주문 수: 100,000건
피크 2시간에 전체 주문의 40% 발생
피크 2시간 주문 수: 40,000건
```

초당 평균 주문 요청은 다음과 같다.

```text
40,000건 / 7,200초 = 약 5.5 RPS
```

하지만 여기서 끝내면 안 된다.  
피크 2시간 안에서도 요청이 고르게 들어오지 않는다. 특정 5분, 1분, 10초 구간에 더 몰릴 수 있다.

그래서 보통 보정 계수를 곱한다.

```text
피크 평균 RPS: 5.5
순간 피크 배수: x5
성장 여유분: x2
안전 여유분: x1.5

목표 RPS = 5.5 × 5 × 2 × 1.5 = 약 82.5 RPS
```

따라서 주문 생성 API의 목표를 다음처럼 잡을 수 있다.

```text
주문 생성 API는 100 RPS에서 p95 500ms 이하, 에러율 1% 미만을 만족해야 한다.
```

---

## 4. 운영 데이터가 있으면 추정보다 운영 데이터를 우선한다

이미 운영 중인 서비스라면 예상치보다 실제 운영 데이터를 우선해야 한다.

볼 수 있는 지표는 다음과 같다.

```text
최근 7일 평균 RPS
최근 30일 평균 RPS
최근 7일 p95 시간대 RPS
최근 30일 p99 분당 RPS
최근 이벤트 당시 최대 RPS
API별 RPS 분포
시간대별 RPS 분포
```

예를 들어 전체 서버가 1000 RPS라고 해도 API별 분포는 다를 수 있다.

```text
GET /products          600 RPS
GET /products/{id}     250 RPS
POST /orders            50 RPS
POST /payments          10 RPS
기타                    90 RPS
```

이 경우 모든 API에 동일하게 “1000 RPS를 버텨야 한다”고 설정하는 것은 부정확하다.

API별 역할과 중요도에 따라 목표를 따로 잡아야 한다.

---

## 5. API별로 목표를 다르게 잡아야 한다

전체 서버 기준 목표도 필요하지만, 실무에서는 **핵심 API별 SLO**가 더 중요하다.

예시:

| API | 목표 RPS | Latency SLO | Error SLO |
|---|---:|---:|---:|
| 상품 목록 조회 | 800 RPS | p95 200ms 이하 | 0.5% 미만 |
| 상품 상세 조회 | 500 RPS | p95 250ms 이하 | 0.5% 미만 |
| 주문 생성 | 100 RPS | p95 500ms 이하 | 1% 미만 |
| 결제 요청 | 50 RPS | p95 800ms 이하 | 0.5% 미만 |
| 관리자 검색 | 20 RPS | p95 1.5s 이하 | 1% 미만 |

이렇게 다르게 잡는 이유는 API마다 사용자 기대와 비즈니스 중요도가 다르기 때문이다.

예를 들어:

- 상품 목록 API는 사용자가 즉시 체감하므로 빠르게 응답해야 한다.
- 주문 생성 API는 조금 더 느려도 괜찮지만 실패하면 치명적이다.
- 결제 API는 외부 PG 연동 때문에 latency 목표가 더 길 수 있지만, 에러율은 낮아야 한다.
- 관리자 검색 API는 일반 사용자 API보다 느려도 허용될 수 있다.

즉 SLO는 기술자가 보기 좋은 숫자가 아니라 **사용자 경험과 비즈니스 중요도에 맞춘 품질 기준**이다.

---

## 6. 응답시간 SLO는 어떤 기준으로 정하나

응답시간 SLO는 보통 다음 기준을 섞어서 정한다.

```text
사용자 체감
비즈니스 중요도
외부 시스템 timeout
프론트엔드 timeout
기존 운영 latency
경쟁 서비스 또는 내부 기대 수준
성능 개선 비용과 난이도
```

예를 들어 프론트엔드가 3초 후 timeout을 건다면 백엔드 SLO를 p95 2.8초로 잡으면 위험하다.  
네트워크, 프론트 렌더링, 브라우저 처리, 로깅, API Gateway 지연까지 고려해야 하기 때문이다.

### 6.1 사용자 체감 기준으로 역산하기

예를 들어 사용자가 1초 안에 응답을 받게 하고 싶다고 하자.

```text
전체 사용자 체감 목표: 1초
프론트 렌더링/네트워크 여유: 300ms
백엔드 전체 허용 시간: 700ms
백엔드 p95 목표: 500~700ms
```

조회 API라면 더 짧게 잡을 수 있고, 결제나 파일 처리처럼 외부 의존성이 있는 API는 더 길게 잡을 수 있다.

---

## 7. 평균 응답시간이 아니라 p95, p99로 잡아야 한다

성능 목표를 다음처럼 잡으면 부족하다.

```text
평균 응답시간 200ms 이하
```

평균은 tail latency를 숨긴다.

예를 들어:

```text
요청 95%는 100ms
요청 5%는 5초
```

이 경우 평균은 생각보다 괜찮아 보일 수 있다.  
하지만 실제 사용자 일부는 매우 나쁜 경험을 한다.

그래서 보통 SLO는 다음처럼 잡는 것이 좋다.

```text
p95 latency 300ms 이하
p99 latency 1s 이하
error rate 1% 미만
```

### 7.1 p95와 p99의 의미

- p50: 절반의 요청이 이 시간 이하로 응답
- p95: 95%의 요청이 이 시간 이하로 응답
- p99: 99%의 요청이 이 시간 이하로 응답

예시:

```text
p50 = 80ms
p95 = 300ms
p99 = 2s
```

이 수치는 “대부분은 빠르지만, 일부 요청은 매우 느리다”는 뜻이다.

p99가 나쁜 경우는 다음 원인을 의심할 수 있다.

- 특정 SQL만 느림
- 락 경합
- GC pause
- 외부 API의 간헐적 지연
- DB 커넥션 풀 대기
- 특정 인스턴스만 느림
- 캐시 미스 구간이 느림

---

## 8. 목표 RPS에는 헤드룸이 있어야 한다

목표가 500 RPS라고 해서 서버가 500 RPS까지만 버티면 위험하다.

예를 들어 테스트 결과가 다음과 같다고 하자.

```text
500 RPS: p95 250ms, error 0%
600 RPS: p95 400ms, error 0.2%
700 RPS: p95 900ms, error 1.5%
800 RPS: p95 3s, error 8%
```

비즈니스 목표가 600 RPS라면, 600 RPS에서 간신히 통과하는 구조는 위험하다.

운영에서는 다음 변수가 발생한다.

- 배포 중 일부 인스턴스 제외
- 캐시 미스 증가
- DB 통계 변경
- GC pause
- 인기 상품 쏠림
- 외부 API 지연
- 특정 테넌트 트래픽 폭증
- 장애 복구 중 트래픽 재분배
- 오토스케일링 지연

따라서 보통은 목표 RPS보다 여유를 둔다.

```text
비즈니스 목표: 600 RPS
성능 테스트 합격 기준: 600 RPS에서 SLO 만족
권장 여유: 900~1200 RPS 근처까지 급격한 붕괴가 없어야 함
```

---

## 9. 목표 RPS와 동시성은 같이 봐야 한다

RPS만 보면 안 된다. 동시성도 같이 봐야 한다.

간단한 공식은 다음과 같다.

```text
동시 처리 중인 요청 수 ≈ RPS × 평균 응답시간(초)
```

예를 들어:

```text
500 RPS × 0.2초 = 약 100개 동시 요청
500 RPS × 2초 = 약 1000개 동시 요청
```

RPS가 같아도 응답시간이 길어지면 동시에 붙잡고 있는 요청 수가 증가한다.

그 결과 다음 자원에 압력이 생긴다.

- Tomcat worker thread
- Netty event loop
- DB connection pool
- Hikari pending queue
- JVM heap
- 애플리케이션 큐
- 외부 API connection pool

따라서 성능 목표를 잡을 때는 다음을 함께 봐야 한다.

```text
목표 RPS
p95/p99 latency
in-flight request 수
DB connection pool active/pending
thread pool active/queue/rejected
queue backlog
```

---

## 10. SLO는 의사결정 기준이어야 한다

SLO를 정하는 이유는 대시보드를 예쁘게 만들기 위해서가 아니다.  
SLO는 의사결정 기준이다.

예를 들어 테스트 목표와 결과가 다음과 같다고 하자.

```text
목표:
500 RPS에서 p95 300ms 이하, error 1% 미만

결과:
500 RPS에서 p95 420ms, error 0.1%
```

이 경우 에러율은 괜찮지만 latency SLO가 깨졌으므로 실패다.

이때 이어지는 질문은 다음과 같다.

```text
1. 병목이 어디인가?
2. DB query가 느린가?
3. connection pool 대기가 있는가?
4. CPU가 포화됐는가?
5. thread pool queue가 쌓이는가?
6. 캐시로 해결 가능한가?
7. scale-out이 맞는가?
8. SLO 자체가 비즈니스상 과한가?
```

반대로:

```text
목표:
500 RPS에서 p95 300ms 이하

결과:
500 RPS에서 p95 80ms
```

이 경우 성능은 충분하다.  
추가 최적화보다 비용 절감, 인스턴스 축소, 안정성 테스트, 장애 대응 테스트가 더 중요할 수 있다.

---

## 11. 목표를 정하는 실무 순서

### 11.1 핵심 사용자 시나리오를 정한다

예:

```text
상품 목록 조회
상품 상세 조회
장바구니 추가
주문 생성
결제 요청
주문 내역 조회
로그인
검색
```

모든 API를 한 번에 하지 말고, 비즈니스 핵심 흐름부터 잡는다.

### 11.2 각 시나리오의 예상 트래픽을 계산한다

예를 들어:

```text
DAU: 100,000
피크 1시간 사용자: 20,000
사용자당 상품 목록 조회: 10회
사용자당 상품 상세 조회: 5회
사용자당 주문 생성: 0.2회
```

피크 1시간 기준:

```text
상품 목록 조회: 20,000 × 10 / 3600 = 약 55 RPS
상품 상세 조회: 20,000 × 5 / 3600 = 약 28 RPS
주문 생성: 20,000 × 0.2 / 3600 = 약 1.1 RPS
```

여기에 순간 피크, 성장률, 안전 여유를 곱한다.

```text
상품 목록 목표: 55 × 5 × 2 = 550 RPS
상품 상세 목표: 28 × 5 × 2 = 280 RPS
주문 생성 목표: 1.1 × 10 × 2 = 22 RPS
```

주문 생성은 평균 계산상 낮아 보여도 이벤트나 선착순 쿠폰에서는 훨씬 몰릴 수 있으므로 별도 피크 배수를 크게 잡을 수 있다.

### 11.3 API별 latency SLO를 정한다

예:

```text
상품 목록 조회: p95 200ms 이하
상품 상세 조회: p95 300ms 이하
주문 생성: p95 500ms 이하
결제 요청: p95 1000ms 이하
```

기준은 다음과 같다.

```text
사용자가 기다릴 수 있는가?
프론트 timeout 안에 충분히 들어오는가?
외부 API timeout과 충돌하지 않는가?
비즈니스상 실패 비용이 큰가?
```

### 11.4 error SLO를 정한다

예:

```text
조회 API: error rate 0.5% 미만
주문 API: error rate 0.1% 미만
결제 API: error rate 0.05% 미만
```

결제나 주문은 단순 조회보다 실패 허용치가 낮아야 한다.

### 11.5 saturation 기준도 정한다

성능 테스트에서는 결과 지표만 보면 부족하다.  
내부 자원 기준도 같이 잡아야 한다.

예:

```text
CPU 평균 70% 이하
CPU p95 85% 이하
Hikari pending connection 거의 0
DB CPU 70% 이하
GC pause p99 100ms 이하
Tomcat busy thread 80% 이하
queue lag 지속 증가 없음
```

---

## 12. 운영 데이터가 없을 때 시작하는 방법

초기 서비스라서 예상 트래픽이 애매하면, 완벽한 숫자를 만들려고 하지 말고 **가정 기반 SLO**로 시작한다.

예:

```text
MVP 1차 목표:
- 상품 조회 100 RPS
- 주문 생성 20 RPS
- p95 latency 500ms 이하
- error rate 1% 미만

출시 후 2주간 실제 트래픽 수집 후 재조정
```

처음부터 정확할 수 없다.  
SLO와 목표 RPS는 한 번 정하고 끝나는 값이 아니라, 실제 트래픽과 비즈니스 성장에 맞춰 계속 조정하는 값이다.

---

## 13. 목표를 너무 높게 잡는 것도 문제다

다음 같은 목표는 멋져 보일 수 있다.

```text
모든 API p99 50ms 이하
error rate 0.001% 이하
```

하지만 이 목표를 달성하기 위해서는 많은 비용이 든다.

- 대규모 캐시
- 샤딩
- 비동기 처리
- 고성능 인프라
- 복잡한 튜닝
- 더 많은 운영 비용
- 더 복잡한 장애 대응 구조

SLO는 무조건 낮을수록 좋은 것이 아니다.  
사용자가 만족하고 비즈니스가 감당할 수 있는 수준이어야 한다.

실무에서는 다음 질문을 해야 한다.

```text
p95 300ms와 p95 100ms의 비즈니스 차이가 큰가?
이 API는 사용자가 직접 기다리는 API인가?
느려져도 비동기로 처리 가능한가?
성능을 올리는 비용이 매출/UX 개선보다 큰가?
```

---

## 14. 목표를 너무 낮게 잡는 것도 문제다

반대로 다음처럼 잡으면 의미가 없다.

```text
p95 5초 이하
error rate 10% 미만
```

이런 기준은 테스트를 통과해도 사용자는 불편할 수 있다.  
특히 다음 API는 사용자가 직접 기다리는 구간이므로 너무 느슨한 목표를 잡으면 운영에서 바로 불만으로 이어질 수 있다.

- 로그인
- 상품 조회
- 검색
- 장바구니
- 주문
- 결제
- 내 주문 내역

---

## 15. 부하테스트 합격 기준 예시

### 15.1 주문 생성 API

```text
Scenario: Create Order

Target load:
- 100 RPS for 10 minutes
- 200 RPS spike for 1 minute
- 150 RPS sustained for 1 hour

SLO:
- p95 latency <= 500ms
- p99 latency <= 1500ms
- error rate <= 0.5%
- no sustained DB connection pool pending
- no continuously increasing queue backlog
```

### 15.2 상품 목록 API

```text
Scenario: Product List

Target load:
- 800 RPS for 10 minutes
- 1500 RPS spike for 1 minute

SLO:
- p95 latency <= 200ms
- p99 latency <= 800ms
- error rate <= 0.5%
- cache hit ratio >= 80%
```

### 15.3 결제 요청 API

```text
Scenario: Request Payment

Target load:
- 50 RPS for 10 minutes
- 100 RPS spike for 1 minute

SLO:
- p95 latency <= 1000ms
- p99 latency <= 3000ms
- error rate <= 0.1%
- external payment timeout <= configured limit
- retry storm must not occur
```

### 15.4 관리자 검색 API

```text
Scenario: Admin Search

Target load:
- 20 RPS for 10 minutes

SLO:
- p95 latency <= 1500ms
- p99 latency <= 3000ms
- error rate <= 1%
```

---

## 16. 목표 부하 공식

실무적으로는 다음 공식이 좋다.

```text
목표 부하 = 예상 피크 트래픽 × 성장 여유 × 안전 여유
응답시간 SLO = 사용자 체감 기준 × API 중요도 × 외부 timeout 제약
에러 SLO = 비즈니스 실패 허용도
자원 기준 = 병목을 조기에 감지하기 위한 saturation 한계
```

예:

```text
예상 피크: 400 RPS
성장 여유: 2배
안전 여유: 1.5배

목표 부하: 400 × 2 × 1.5 = 1200 RPS
```

테스트 해석:

```text
1200 RPS에서 SLO 만족? → 합격
1500~2000 RPS에서 어디가 먼저 깨짐? → 한계 분석
깨지는 자원이 DB? CPU? pool? lock? → 개선 방향 결정
```

---

## 17. 성능 테스트 결과를 해석하는 기본 프레임

성능 테스트 결과는 다음 네 가지 관점으로 읽는다.

```text
Traffic
Latency
Errors
Saturation
```

### 17.1 Traffic

서비스에 들어오는 요청량이다.

예:

```text
RPS
동시 사용자 수
초당 트랜잭션 수
API별 호출 수
```

### 17.2 Latency

응답 시간이다.

중요한 것은 평균이 아니라 p95, p99다.

```text
p50
p95
p99
max
```

성공 요청과 실패 요청의 latency도 구분해야 한다.  
빠른 500 에러가 평균 latency를 좋게 보이게 만들 수 있기 때문이다.

### 17.3 Errors

실패율이다.

다음은 모두 에러로 볼 수 있다.

```text
HTTP 5xx
HTTP 4xx 중 시스템 문제로 인한 오류
timeout
connection refused
rejected request
rate limit 초과
business failure
SLO를 초과한 매우 느린 요청
```

### 17.4 Saturation

시스템 자원이 얼마나 가득 찼는지 나타낸다.

예:

```text
CPU
memory
GC pause
DB connection pool
thread pool
queue depth
DB lock wait
disk I/O
network bandwidth
external API pool
```

---

## 18. 결과 해석 패턴

### 18.1 처리량도 늘고 latency도 안정적인 경우

```text
RPS 증가
p95/p99 안정적
에러율 낮음
자원 여유 있음
```

아직 시스템에 여유가 있는 상태다.  
부하를 더 올려도 된다.

### 18.2 처리량은 안 늘고 latency만 증가하는 경우

```text
RPS를 더 넣었지만 처리량이 정체
p95/p99 급증
에러율 증가 가능
```

전형적인 포화 신호다.

해야 할 질문:

```text
무엇이 먼저 찼는가?
CPU인가?
DB인가?
connection pool인가?
thread pool인가?
queue인가?
외부 API인가?
```

### 18.3 평균은 괜찮은데 p99만 튀는 경우

Tail latency 문제다.

의심 원인:

```text
락 경합
느린 SQL 일부
GC pause
외부 API 간헐적 지연
캐시 미스
특정 인스턴스 문제
특정 데이터 skew
```

### 18.4 자원은 안 찼는데 에러가 먼저 나는 경우

설정 또는 정책 문제일 수 있다.

의심 원인:

```text
rate limit
timeout 설정
connection timeout
pool max size
retry 폭증
circuit breaker 설정
deadlock
lock timeout
외부 API quota
```

### 18.5 서버는 멀쩡한데 RPS가 기대보다 안 나오는 경우

부하 생성기가 병목일 수 있다.

확인할 것:

```text
load generator CPU
load generator network
k6/JMeter worker 수
client-side timeout
테스트 스크립트 sleep
테스트 데이터 부족
DNS/TLS overhead
```

---

## 19. 병목 판단 체크리스트

### 19.1 CPU 병목

징후:

```text
CPU 사용률 높음
RPS 증가가 멈춤
latency 증가
GC는 정상
DB는 여유 있음
```

의심 원인:

```text
비효율적인 비즈니스 로직
JSON 직렬화/역직렬화 비용
압축
암호화
정규식
대량 loop
동기 blocking 작업
```

개선 방향:

```text
핫패스 최적화
불필요한 객체 생성 줄이기
캐시
비동기 처리
스케일아웃
```

### 19.2 DB 병목

징후:

```text
앱 CPU는 낮음
DB query latency 증가
DB CPU 또는 I/O 증가
Hikari active 증가
Hikari pending 발생
slow query 증가
lock wait 증가
```

의심 원인:

```text
인덱스 없음
잘못된 인덱스
N+1
불필요한 join
과도한 fetch
large result set
락 경합
트랜잭션 범위 과도
```

개선 방향:

```text
인덱스 추가/수정
쿼리 개선
DTO projection
fetch 전략 개선
batch 처리
트랜잭션 범위 축소
캐시
read replica
```

### 19.3 커넥션 풀 병목

징후:

```text
Hikari active가 max에 가까움
pending connection 증가
DB CPU는 낮거나 중간
요청 latency 증가
timeout 발생
```

의심 원인:

```text
트랜잭션이 너무 김
DB 쿼리가 느림
외부 API 호출을 트랜잭션 안에서 수행
pool size가 너무 작음
connection leak
```

개선 방향:

```text
트랜잭션 범위 축소
느린 쿼리 개선
외부 API 호출을 트랜잭션 밖으로 이동
connection leak 탐지
pool size 조정
```

### 19.4 스레드 풀 병목

징후:

```text
Tomcat busy thread 증가
thread pool queue 증가
rejected request 발생
CPU는 낮은데 요청 대기 증가
```

의심 원인:

```text
blocking I/O
외부 API 대기
DB connection 대기
thread pool size 부적절
동기 처리 과다
```

개선 방향:

```text
blocking 구간 줄이기
timeout 설정
bulkhead
비동기 처리
thread pool 조정
```

### 19.5 GC 병목

징후:

```text
GC pause 증가
p99 latency 증가
CPU spike
heap usage 톱니 모양 이상
full GC 발생
```

의심 원인:

```text
대량 객체 생성
큰 응답 객체
대량 조회
캐시 과다
메모리 leak
```

개선 방향:

```text
조회 결과 줄이기
pagination
streaming
DTO 최소화
캐시 크기 제한
JVM heap/GC 튜닝
```

### 19.6 외부 API 병목

징후:

```text
trace에서 외부 API span이 김
우리 CPU/DB는 여유 있음
timeout/retry 증가
p99가 튐
```

의심 원인:

```text
외부 API 지연
retry storm
timeout이 너무 김
circuit breaker 없음
rate limit
```

개선 방향:

```text
timeout 줄이기
retry with backoff
circuit breaker
비동기화
캐시
fallback
```

---

## 20. 성능 테스트 목표 설계 템플릿

아래 템플릿을 그대로 복사해서 사용할 수 있다.

```markdown
# Performance Test Target

## Service
- Service name:
- Environment:
- Test date:
- Test owner:

## Scenario
- Scenario name:
- API list:
- Business criticality:
- User-facing: yes/no
- External dependencies:

## Traffic Assumption
- Expected average RPS:
- Expected peak RPS:
- Spike multiplier:
- Growth margin:
- Safety margin:
- Target RPS:

## SLO
- p50 latency:
- p95 latency:
- p99 latency:
- Error rate:
- Timeout:
- Availability target:

## Saturation Guardrail
- App CPU:
- JVM heap:
- GC pause:
- Thread pool active:
- Thread pool rejected:
- DB connection active:
- DB connection pending:
- DB CPU:
- DB lock wait:
- Queue lag:

## Test Types
- Smoke test:
- Average load test:
- Stress test:
- Spike test:
- Soak test:

## Pass Criteria
- Target RPS에서 latency SLO 만족
- Target RPS에서 error SLO 만족
- 지속적인 queue backlog 없음
- 지속적인 connection pool pending 없음
- p99 급격한 폭증 없음
- 테스트 종료 후 리소스 정상 회복

## Failure Criteria
- p95 또는 p99 SLO 초과
- error rate 초과
- timeout 증가
- DB connection pending 지속
- thread rejected 발생
- queue lag 지속 증가
- GC pause SLO 초과
```

---

## 21. 성능 테스트 결과 리뷰 템플릿

```markdown
# Performance Test Result Review

## Summary
- Test scenario:
- Target RPS:
- Actual max stable RPS:
- SLO pass/fail:
- Main bottleneck:

## Traffic
- Achieved RPS:
- Peak RPS:
- Test duration:
- Load pattern:

## Latency
- p50:
- p95:
- p99:
- max:
- Slowest API:

## Errors
- Error rate:
- 4xx:
- 5xx:
- timeout:
- rejected:
- business errors:

## Saturation
- App CPU:
- JVM heap:
- GC pause:
- Thread pool:
- DB connection pool:
- DB CPU:
- DB I/O:
- Lock wait:
- Queue backlog:

## Bottleneck Analysis
- First broken metric:
- Time when degradation started:
- Related resource saturation:
- Trace evidence:
- Slow query evidence:
- Log evidence:

## Decision
- Tune query:
- Add/modify index:
- Reduce response size:
- Add cache:
- Adjust pool:
- Scale out:
- Change SLO:
- Retest required:

## Next Action
1.
2.
3.
```

---

## 22. 결론

성능 테스트에서 목표 RPS와 응답시간 SLO는 다음 기준으로 정한다.

```text
1. 예상 트래픽을 상정한다.
2. 평균이 아니라 피크 트래픽을 본다.
3. 순간 스파이크와 성장 여유, 안전 마진을 곱한다.
4. API별로 목표 RPS를 따로 정한다.
5. 응답시간은 평균이 아니라 p95/p99로 잡는다.
6. error rate SLO를 정한다.
7. CPU, DB, connection pool, thread pool, queue 같은 saturation 기준도 정한다.
8. 목표 부하에서는 SLO를 만족해야 한다.
9. 목표 이상에서는 어느 지점에서 무엇이 먼저 깨지는지 확인한다.
```

최종적으로 좋은 성능 테스트 목표는 이런 형태여야 한다.

```text
상품 목록 API는 800 RPS에서 p95 200ms 이하, p99 800ms 이하,
에러율 0.5% 미만을 만족해야 한다.

또한 1200 RPS까지는 급격한 latency 폭증 없이 graceful degradation 해야 한다.
```

성능 테스트는 “숫자 많이 보기”가 아니라 **트래픽, 사용자 경험, 에러, 자원 포화도를 연결해서 의사결정을 내리는 과정**이다.

---

## 23. References

- Google SRE Book - Service Level Objectives  
  https://sre.google/sre-book/service-level-objectives/

- Google SRE Book - Monitoring Distributed Systems  
  https://sre.google/sre-book/monitoring-distributed-systems/

- AWS Well-Architected Framework - Reliability Pillar: Test reliability  
  https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/rel_testing_resiliency_test_non_functional.html

- AWS Well-Architected Framework - Performance Efficiency Pillar  
  https://docs.aws.amazon.com/wellarchitected/latest/performance-efficiency-pillar/welcome.html
