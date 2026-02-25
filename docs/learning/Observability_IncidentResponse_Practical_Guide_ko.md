# 운영/장애 대응(Observability) 능력: 주니어 백엔드 실무 생존서
로그/메트릭/트레이싱부터 알람/SLO/장애대응/회고까지 “끝까지 책임지는” 가이드

- 버전: v1.6 (비즈니스/성능/합성 모니터링 포함)
- 작성일: 2026-02-25 (Asia/Seoul)
- 대상: 주니어 백엔드 개발자(실무 0~3년)
- 목표:
  - “서비스가 느리다/에러난다”를 **추측이 아니라 근거로** 말할 수 있게 된다
  - 장애 때 **5~10분 안에 방향을 잡는** 트리아지 루틴을 갖는다
  - 알람/대시보드/런북/회고까지 포함해 **운영 가능한 기능**을 만든다

> 이 문서는 특정 벤더(Datadog/New Relic/ELK/Grafana 등)에 종속되지 않고,  
> 어느 스택에서도 통하는 **관측성의 원리 + 실무 패턴 + 예시**를 중심으로 구성했습니다.  
> 예시는 Prometheus/Grafana/OpenTelemetry 기반을 많이 사용하지만, 개념은 동일합니다.

---

## 목차

### Part 0. 시작하기
0. [읽는 방법](#0-읽는-방법)  
0.1 [관측성 3대 기둥과 “운영”의 정의](#01-관측성-3대-기둥과-운영의-정의)  
0.2 [이 문서가 지향하는 운영 수준](#02-이-문서가-지향하는-운영-수준)

### Part 1. 관측성의 기본 프레임: 무엇을, 왜, 어떻게 볼 것인가
1. [관측성 vs 모니터링: 차이와 현실](#1-관측성-vs-모니터링-차이와-현실)  
2. [Golden Signals, RED, USE: 실무에서 가장 많이 쓰는 3가지 관점](#2-golden-signals-red-use-실무에서-가장-많이-쓰는-3가지-관점)  
3. [SLI/SLO/Error Budget: “알람 난리”를 끝내는 공식](#3-slisloerror-budget-알람-난리를-끝내는-공식)  

### Part 2. 로그(Logging): “나중에 볼 수 있게”가 아니라 “바로 원인을 찾게”
4. [구조화 로그(Structured Logging) 원칙](#4-구조화-로그structured-logging-원칙)  
5. [Correlation ID / Trace ID: 로그를 한 줄로 연결하는 법](#5-correlation-id--trace-id-로그를-한-줄로-연결하는-법)  
6. [로그 레벨/샘플링/비용: 로그는 무한히 쌓지 않는다](#6-로그-레벨샘플링비용-로그는-무한히-쌓지-않는다)  
7. [보안/개인정보: 로그에 절대 남기면 안 되는 것](#7-보안개인정보-로그에-절대-남기면-안-되는-것)  
8. [실무 로그 템플릿과 예시(JSON)](#8-실무-로그-템플릿과-예시json)

### Part 3. 메트릭(Metrics): 지표는 “알람”이 아니라 “의사결정”이다
9. [지표 설계: Counter/Gauge/Histogram 요령](#9-지표-설계-countergaugehistogram-요령)  
10. [p95/p99 지연시간을 ‘제대로’ 보는 법](#10-p95p99-지연시간을-제대로-보는-법)  
11. [Prometheus/StatsD 관점의 수집 설계](#11-prometheusstatsd-관점의-수집-설계)  
12. [대시보드(서비스/의존성/리소스) 표준 레이아웃](#12-대시보드서비스의존성리소스-표준-레이아웃)

### Part 4. 트레이싱(Tracing): “어느 구간이 느린가?”를 1분 안에
13. [분산 트레이싱이 필요한 이유](#13-분산-트레이싱이-필요한-이유)  
14. [OpenTelemetry 기본 개념(Trace/Span/Context)](#14-opentelemetry-기본-개념tracespancontext)  
15. [HTTP/DB/Cache/Queue 인스트루먼트 포인트](#15-httpdbcachequeue-인스트루먼트-포인트)  
16. [샘플링 전략: 전부 남기면 망한다](#16-샘플링-전략-전부-남기면-망한다)  
17. [트레이스 기반 알람/디버깅 루틴](#17-트레이스-기반-알람디버깅-루틴)

### Part 5. 알람(Alerting): “사람을 깨우는 시스템” 만들기
18. [알람 설계 원칙: 알람은 적을수록 좋다(진짜로)](#18-알람-설계-원칙-알람은-적을수록-좋다진짜로)  
19. [증상 기반 vs 원인 기반 알람: 무엇을 먼저?](#19-증상-기반-vs-원인-기반-알람-무엇을-먼저)  
20. [알람 룰 예시(PromQL) + 튜닝 포인트](#20-알람-룰-예시promql--튜닝-포인트)  
21. [알람 → 런북 → 조치: 클릭 3번 이내로](#21-알람--런북--조치-클릭-3번-이내로)

### Part 6. 장애 대응(Incident Response): 5분 트리아지부터 1시간 안정화까지
22. [장애 대응의 목표: “원인 규명”보다 “사용자 영향 최소화”](#22-장애-대응의-목표-원인-규명보다-사용자-영향-최소화)  
23. [5분 트리아지 루틴: 지금 뭘 먼저 볼까](#23-5분-트리아지-루틴-지금-뭘-먼저-볼까)  
24. [롤(Incident Commander/Scribe/Comms)과 커뮤니케이션](#24-롤incident-commanderscribecomms과-커뮤니케이션)  
25. [장애 중 의사결정: 롤백/기능 플래그/레이트 리밋/격리](#25-장애-중-의사결정-롤백기능-플래그레이트-리밋격리)  
26. [장애 후 회고(Postmortem): 비난 없는 개선 시스템](#26-장애-후-회고postmortem-비난-없는-개선-시스템)

### Part 7. 운영 가능성(Operability): 관측성이 잘 되는 코드는 “운영이 쉽다”
27. [Timeout/Retry/Circuit Breaker: 관측 가능한 방식으로](#27-timeoutretrycircuit-breaker-관측-가능한-방식으로)  
28. [헬스체크( readiness / liveness )와 그레이스풀 셧다운](#28-헬스체크-readiness--liveness--와-그레이스풀-셧다운)  
29. [배포 관측: 배포가 원인인 장애를 2분 만에 찾기](#29-배포-관측-배포가-원인인-장애를-2분-만에-찾기)  
30. [용량 계획(capacity) 기본: CPU/메모리/DB/큐](#30-용량-계획capacity-기본-cpu메모리db큐)

### Part 8. 케이스 스터디(실전 시나리오)
31. [케이스 1: p99 지연이 갑자기 튄다(슬로우/락/풀 고갈)](#31-케이스-1-p99-지연이-갑자기-튄다슬로우락풀-고갈)  
32. [케이스 2: 에러율 스파이크(배포/의존성/데이터)](#32-케이스-2-에러율-스파이크배포의존성데이터)  
33. [케이스 3: DB 커넥션 고갈](#33-케이스-3-db-커넥션-고갈)  
34. [케이스 4: 큐 적체(consumer lag)](#34-케이스-4-큐-적체consumer-lag)  
35. [케이스 5: 메모리 릭/GC 폭주](#35-케이스-5-메모리-릭gc-폭주)  

### Appendices
A. [운영 체크리스트](#a-운영-체크리스트)  
B. [자주 하는 실수 50](#b-자주-하는-실수-50)  
C. [면접/과제 대비 질문 40](#c-면접과제-대비-질문-40)  
D. [로그/지표/트레이스 필드 표준(권장 스키마)](#d-로그지표트레이스-필드-표준권장-스키마)  
E. [PromQL 치트시트](#e-promql-치트시트)  
F. [“오늘부터” 적용하는 2주 플랜](#f-오늘부터-적용하는-2주-플랜)  

---

# Part 0. 시작하기

## 0. 읽는 방법

관측성은 “지식”이 아니라 **습관 + 체계**입니다.  
이 문서는 아래 순서로 읽으면 가장 효과가 큽니다.

1) Part 1로 프레임 잡기(RED/USE/Golden + SLO)  
2) Part 2~4에서 3대 기둥을 **표준화**(로그/메트릭/트레이스)  
3) Part 5에서 알람을 “사람을 깨우는 시스템”으로 만들기  
4) Part 6에서 트리아지/롤/회고 템플릿을 몸에 익히기  
5) Part 8 케이스 스터디를 실제 서비스에 대입하기

추천 루틴:
- 평일 30분: 한 장 읽고 “내 서비스에 적용하면?” 메모
- 주말 2시간: 대시보드 1개 만들고 알람 1개 튜닝하기

---

## 0.1 관측성 3대 기둥과 “운영”의 정의

관측성(Observability)은 보통 3요소로 얘기합니다.

- **Logs(로그)**: “무슨 일이 일어났나?” (이벤트의 서술)
- **Metrics(메트릭)**: “얼마나 자주/얼마나 오래/얼마나 많이?” (숫자)
- **Traces(트레이스)**: “어디서 시간이 쓰였나?” (분산 호출의 시간 지도)

운영(Operations)은 단순히 서버를 켜두는 게 아니라, 아래를 포함합니다.

- 장애를 빠르게 감지하고(Detect)
- 사용자 영향을 줄이며 완화하고(Mitigate)
- 원인을 파악하고(Remedy)
- 재발 방지를 시스템으로 남기는 것(Prevent)

---

## 0.2 이 문서가 지향하는 운영 수준

이 문서의 도달 목표는 다음 “레벨 3”입니다.

- 레벨 0: 장애는 사용자가 먼저 알려줌
- 레벨 1: 기본 알람/로그는 있지만 원인 파악이 느림
- 레벨 2: 대시보드/알람은 있는데 알람이 너무 많음(피로)
- **레벨 3(목표)**: SLO 기반 증상 알람 + 런북 + 5분 트리아지 루틴이 있음
- 레벨 4: 자동 완화(오토스케일/서킷/격리) + 카나리/실험 기반 운영
- 레벨 5: SRE 체계(에러 버짓 정책, 대규모 신뢰성 엔지니어링)

주니어가 레벨 3까지 올리면 팀에서 “운영을 맡길 수 있는 사람”으로 보입니다.

---

# Part 1. 관측성의 기본 프레임

## 1. 관측성 vs 모니터링: 차이와 현실

- 모니터링은 “이미 알고 있는 질문”에 답합니다.  
  예: CPU가 80% 넘었나? 에러율이 5% 넘었나?

- 관측성은 “처음 보는 문제”도 탐색 가능하게 합니다.  
  예: 특정 사용자/특정 경로에서만 느린데 왜지?

실무에서는:
- 모니터링(알람)으로 **감지**
- 관측성(로그/트레이스)으로 **원인 탐색**
- SLO로 **알람 피로를 통제**
이 조합이 중요합니다.

---

## 2. Golden Signals, RED, USE: 실무에서 가장 많이 쓰는 3가지 관점

### 2.1 Golden Signals(구글 SRE에서 널리 쓰는 관점)
- Latency(지연시간)
- Traffic(트래픽)
- Errors(에러)
- Saturation(포화: CPU/메모리/큐/커넥션)

### 2.2 RED(요청 중심 서비스에 최적)
- Rate: 요청량(RPS)
- Errors: 에러율(5xx/실패율)
- Duration: 지연시간(p50/p95/p99)

API 서버(백엔드)는 RED가 가장 강력합니다.

### 2.3 USE(인프라/리소스 중심)
- Utilization: 사용률(CPU 등)
- Saturation: 포화(큐 길이, 락 대기, 스레드/커넥션)
- Errors: 오류(디스크 오류 등)

“CPU가 90%인데 왜 괜찮지?” 같은 질문은 USE로 답이 나옵니다.

---

## 3. SLI/SLO/Error Budget: “알람 난리”를 끝내는 공식

알람이 너무 많으면 결국 아무도 보지 않습니다.  
해결책은 **SLO**입니다.

### 3.1 용어
- SLI(Service Level Indicator): 측정 지표  
  예: “성공 응답 비율”, “p95 지연시간”
- SLO(Objective): 목표  
  예: “성공률 99.9%”, “p95 < 300ms”
- Error Budget: 허용 실패량(= 1 - SLO)

### 3.2 현실적인 SLO 예시(웹/API)
- 가용성(성공률): 99.9% / 99.5%  
- 지연시간: p95 < 300ms, p99 < 1s  
(정답은 없고 사용자 기대/비즈니스 중요도에 따라 결정)

### 3.3 SLO 기반 알람의 장점
- 단기 스파이크(1~2분)를 무조건 깨우지 않음
- “사용자 영향” 기준으로 알람을 울림
- 팀이 ‘품질’을 수치로 합의할 수 있음

### 3.4 SLO를 설계할 때 꼭 해야 하는 질문
- 무엇이 “성공”인가? (2xx만? 4xx도 성공인가?)
- 사용자에게 중요한 엔드포인트는 무엇인가?
- 배포/외부 의존성 실패를 SLI에 포함할 것인가?

---

# Part 2. 로그(Logging)

## 4. 구조화 로그(Structured Logging) 원칙

텍스트 로그는 검색/필터링이 어렵습니다.  
실무에서는 **JSON 구조화 로그**가 사실상 표준입니다.

### 4.1 좋은 로그의 기준
- 검색 가능(필드 기반)
- 상관관계 가능(trace_id / request_id)
- 개인정보/비밀이 없다
- 비용을 통제한다(샘플링/레벨)

### 4.2 로그는 “이벤트”를 남긴다
로그에 남기면 좋은 이벤트:
- 요청 시작/종료(요약)
- 외부 의존성 호출(결제/DB/캐시/큐) 실패/타임아웃
- 상태 전이(주문 CREATED->PAID 등)
- 재시도/서킷 열림/레이트 리밋 발동
- 예외 스택트레이스(중요: 중복/폭주 방지)

---

## 5. Correlation ID / Trace ID: 로그를 한 줄로 연결하는 법

장애 때 가장 힘든 건 이겁니다.
- “이 에러 로그가 어떤 요청에서 나온 거지?”
- “같은 사용자 요청의 로그가 어디 있지?”

### 5.1 필드 2개만 제대로 넣어도 반은 끝
- `trace_id` (분산 트레이스와 동일)
- `request_id` (게이트웨이/서버 내부 단위)

추천:
- 가능하면 `trace_id`를 표준으로 삼고,
- 로그/메트릭/트레이스 모두에 동일하게 묶습니다.

### 5.2 전파(Propagation)가 핵심
- API Gateway → Service A → Service B로 갈 때
- 헤더로 trace context가 전달되어야 합니다.

대표 표준:
- W3C Trace Context (`traceparent`, `tracestate`)

실무 팁:
- “한 서비스만 트레이싱”하면 효과가 반쪽입니다.
- 최소한 **Edge(게이트웨이)~핵심 서비스~DB**까지는 연결되어야 합니다.

---

## 6. 로그 레벨/샘플링/비용: 로그는 무한히 쌓지 않는다

로그는 비용(저장/검색)이고, 너무 많으면 “신호 대 잡음”이 무너집니다.

### 6.1 로그 레벨 가이드(현실 버전)
- ERROR: 반드시 행동이 필요한 오류(알람/티켓 연결 가능)
- WARN: 잠재적 문제(재시도, 서킷, 약한 실패)
- INFO: 요청 요약/상태 전이(너무 과하면 비용 폭발)
- DEBUG: 개발/재현용(운영 기본 비활성 권장)

### 6.2 샘플링 전략
- 정상 요청 로그는 1~5%만 남기고,
- 에러/느린 요청은 100% 남기는 방식이 흔합니다.

예:
- `duration_ms > 1000` 인 요청은 INFO로 남김
- 그 외는 샘플링

---

## 7. 보안/개인정보: 로그에 절대 남기면 안 되는 것

**절대 금지(원칙)**:
- 비밀번호/토큰/세션키/서명 키/Access Key
- 주민번호/계좌번호/카드번호(전체)
- 민감한 개인정보(법/정책에 따라)

현실적인 안전 장치:
- 로깅 전 레드액션(redaction) 함수 적용
- 필드 화이트리스트(허용한 필드만 기록)
- “요청 바디 그대로 로깅” 금지(특히 결제/인증)

---

## 8. 실무 로그 템플릿과 예시(JSON)

권장 최소 필드:

```json
{
  "ts": "2026-02-25T12:34:56.789Z",
  "level": "INFO",
  "service": "orders-api",
  "env": "prod",
  "version": "1.2.3",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "request_id": "req_01HT...",
  "method": "POST",
  "path": "/v1/orders",
  "status": 201,
  "duration_ms": 32,
  "client_ip": "1.2.3.4",
  "user_id": 123,
  "msg": "request completed"
}
```

에러 로그 예시:

```json
{
  "ts": "2026-02-25T12:35:12.012Z",
  "level": "ERROR",
  "service": "payments-api",
  "env": "prod",
  "trace_id": "....",
  "request_id": "....",
  "error_type": "TimeoutError",
  "error_message": "payment provider timeout",
  "dependency": "payment_provider",
  "timeout_ms": 2000,
  "msg": "dependency call failed"
}
```

---

# Part 3. 메트릭(Metrics)

## 9. 지표 설계: Counter/Gauge/Histogram 요령

메트릭 타입 감각이 중요합니다.

### 9.1 Counter
- 단조 증가(리셋 제외)
- 예: 요청 수, 에러 수, 재시도 수
- Prometheus에서는 rate/increase로 속도를 봅니다.

### 9.2 Gauge
- 오르내림 가능
- 예: 현재 큐 길이, 현재 커넥션 수, 메모리 사용량

### 9.3 Histogram(권장)
- 지연시간/사이즈 분포
- p95/p99 계산에 유리
- “평균”은 항상 속입니다(꼬리가 중요)

---

## 10. p95/p99 지연시간을 ‘제대로’ 보는 법

### 10.1 왜 평균이 위험한가
- 1%의 느린 요청이 사용자 경험을 망칩니다.
- 평균은 “괜찮아 보이는 착시”를 줍니다.

### 10.2 p95/p99의 의미
- p95: 95%의 요청이 이 시간 이하
- p99: 99%의 요청이 이 시간 이하(꼬리)

실무 팁:
- p99가 튀면 보통
  - GC/스톱더월드
  - DB 락 대기
  - 커넥션 풀 대기
  - 외부 의존성 타임아웃
같은 “대기”가 원인인 경우가 많습니다.

---

## 11. Prometheus/StatsD 관점의 수집 설계

### 11.1 레이블(cardinality) 폭발 주의
Prometheus에서 흔한 사고:
- user_id, order_id 같은 고유값을 label로 넣음
- 시계열이 폭발 -> 저장/쿼리 비용 폭발

원칙:
- label은 “낮은 카디널리티”로
- 고유 id는 로그/트레이스에만

좋은 label 예:
- http_method, route(템플릿), status_class(2xx/5xx), dependency_name

나쁜 label 예:
- user_id, path_full, query_string, order_id

---

## 12. 대시보드 표준 레이아웃

대시보드는 “보기 좋게”가 아니라 “장애 때 빠르게”가 목표입니다.

### 12.1 서비스 대시보드(RED + SLO)
1) 트래픽(RPS)
2) 에러율(5xx, 실패율)
3) 지연시간(p50/p95/p99)
4) SLO burn rate(가능하면)
5) 상위 엔드포인트 TOP(느림/에러)

### 12.2 의존성 대시보드
- DB: 쿼리 지연, 커넥션/풀 대기, 락 대기
- 캐시: hit rate, latency, evictions
- 외부 API: latency, error rate, timeout, retry

### 12.3 리소스 대시보드(USE)
- CPU/Load
- 메모리/GC
- 디스크 IO
- 네트워크
- 스레드/이벤트루프 지연(Node 등)

---

# Part 4. 트레이싱(Tracing)

## 13. 분산 트레이싱이 필요한 이유

마이크로서비스/외부 의존성이 있는 순간, 이런 질문이 생깁니다.

- “우리 서버가 느린 거야, DB가 느린 거야, 결제사가 느린 거야?”
- “A 요청이 B를 3번 호출한 건가?”
- “어느 구간에서 2초가 쓰였지?”

트레이싱은 “요청의 타임라인”을 제공합니다.

---

## 14. OpenTelemetry 기본 개념(Trace/Span/Context)

- Trace: 하나의 요청 흐름 전체
- Span: 그 안의 한 구간(HTTP 핸들러, DB 쿼리, 외부 호출 등)
- Context: trace/span 정보를 다음 호출로 전달하는 메커니즘

### 14.1 기본 원칙
- 모든 inbound 요청에 trace를 시작(또는 이어받기)
- 중요한 outbound 호출에 span 생성
- 에러/타임아웃은 span에 이벤트로 기록
- trace_id를 로그에도 포함(로그-트레이스 연결)

---

## 15. HTTP/DB/Cache/Queue 인스트루먼트 포인트

실무에서 “최소로 해도 효과 큰” 포인트:

1) HTTP 서버(inbound)  
   - route(템플릿), status, duration
2) HTTP 클라이언트(outbound)  
   - dependency name, retry count, timeout
3) DB 쿼리  
   - operation(SELECT/UPDATE), table(가능하면), duration  
   - 주의: 쿼리 전문은 PII/비용 이슈가 있어 샘플/마스킹 필요
4) Cache(Redis 등)  
   - command, hit/miss, duration
5) Queue/Kafka  
   - produce/consume span, lag 관련 메트릭

---

## 16. 샘플링 전략: 전부 남기면 망한다

트레이스는 매우 비쌉니다(저장/전송/검색).  
그래서 샘플링은 필수입니다.

현실적인 샘플링:
- 정상 요청: 1~5%
- 에러 요청: 100%
- 느린 요청: 100%(tail-based sampling)

용어:
- head sampling: 시작 시점에 결정(간단, 하지만 “느린 것”을 놓칠 수 있음)
- tail sampling: 끝난 뒤 결과를 보고 선택(좋지만 collector/파이프라인 필요)

---

## 17. 트레이스 기반 디버깅 루틴

느림이 왔을 때 순서:

1) 느린 trace 하나를 잡는다(대표 p99)  
2) 가장 긴 span을 찾는다  
3) 그 span이
   - DB 쿼리냐?
   - 외부 API냐?
   - 내부 lock/queue 대기냐?
4) 해당 의존성 메트릭/로그로 내려간다

이게 “추측”을 끝내는 루틴입니다.

---

# Part 5. 알람(Alerting)

## 18. 알람 설계 원칙: 알람은 적을수록 좋다(진짜로)

알람은 사람의 주의력을 소모합니다.  
좋은 알람의 조건:

- **사용자 영향**이 있다
- **즉시 행동**할 수 있다
- **중복**이 없다(같은 사건에 알람 10개 금지)
- **런북**이 있다(“뭘 보면 되는지” 링크)

### 18.1 알람 피로의 전형적 원인
- CPU 80% 같은 “증상이 아닌 원인도 아닌” 알람 남발
- thresholds가 너무 빡빡함
- 배포/피크 타임 변동 고려 없음
- 알람이 났는데 할 수 있는 조치가 없음(=잡음)

---

## 19. 증상 기반 vs 원인 기반 알람: 무엇을 먼저?

추천 우선순위:
1) **증상 기반(SLO, 에러율, 지연)**: 먼저  
2) 원인 기반(DB 커넥션, 큐 적체, 디스크) : 보조

왜냐하면:
- 원인 알람은 “고장 나기 전에” 좋지만, 자주 거짓 양성
- 증상 알람은 “사용자 영향” 중심이라 진짜 중요

---

## 20. 알람 룰 예시(PromQL) + 튜닝 포인트

아래 예시는 Prometheus 스타일의 개념 예시입니다.

### 20.1 에러율(5xx) 알람
```promql
sum(rate(http_server_requests_total{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_total[5m]))
> 0.01
```

튜닝:
- route 별로 나누면 너무 많은 알람이 생길 수 있음
- 먼저 전체 서비스 에러율 알람 → 필요하면 핵심 route만 추가

### 20.2 p95 지연 알람(히스토그램 기반)
```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_request_duration_seconds_bucket[5m])) by (le)
) > 0.5
```

### 20.3 SLO burn rate(개념)
- 5분, 1시간 창을 동시에 보고 “빠르게 태우는지” 감지
- 이건 조직의 SLO 체계에 따라 구현이 달라질 수 있음

---

## 21. 알람 → 런북 → 조치: 클릭 3번 이내로

알람은 메시지에 반드시 포함해야 합니다.

- 어떤 서비스/엔드포인트?
- 언제부터?
- 사용자 영향 추정(에러율/지연)
- 관련 대시보드 링크
- 런북 링크(트리아지 단계/조치)

런북 없는 알람은 **사람을 깨우는 잡음**이 될 확률이 큽니다.

---

# Part 6. 장애 대응(Incident Response)

## 22. 장애 대응의 목표

장애 대응에서 가장 위험한 행동:
- 원인을 모르는 상태에서 “여기저기 건드리기”
- 로그를 더 찍기 위해 무작정 DEBUG 켜기(비용/부하 폭발)
- 커뮤니케이션 없이 혼자 해결하려다 더 늦어짐

목표 순서:
1) 사용자 영향 파악
2) 확산 방지/완화(rollback, feature flag off, rate limit)
3) 서비스 안정화
4) 원인 규명/재발 방지(회고)

---

## 23. 5분 트리아지 루틴: 지금 뭘 먼저 볼까

장애 알람이 뜨면, 아래 5분 루틴을 추천합니다.

### 23.1 1분: 증상 확인(RED)
- 트래픽 변화? (급증/급감)
- 에러율? (5xx/timeout)
- 지연? (p95/p99)

### 23.2 2분: 범위 확인
- 특정 region/cluster만?
- 특정 버전 배포 이후?
- 특정 endpoint만?
- 특정 고객/테넌트만?

### 23.3 2분: 의존성 확인
- DB: 커넥션 풀, 락 대기, 슬로우쿼리
- 캐시: hit rate 급락? latency 급증?
- 외부 API: timeout/error 급증?
- 큐: lag/적체?

### 23.4 결론: “지금 가장 유력한 1개 가설”을 만든다
- 예: “배포 이후 특정 endpoint에서 DB 락 대기 증가로 p99 튐”
- 그리고 그 가설을 검증하는 액션을 1~2개만 한다.

---

## 24. 롤과 커뮤니케이션

### 24.1 최소 롤 3개
- Incident Commander(IC): 의사결정/조율 담당(키보드보다 머리)
- Scribe: 타임라인 기록(나중에 회고/재발방지에 결정적)
- Comms: 내부/외부 커뮤니케이션(상황 공유)

작은 팀이면 1~2명이 겸임해도 되지만,
“기록”은 절대 빠지면 안 됩니다.

### 24.2 상황 공유 템플릿(짧게)
- 현재 영향: (에러율 %, 지연, 영향 endpoint)
- 시작 시각: (KST)
- 가설: (현재 가장 유력한 원인)
- 조치: (롤백/스케일/레이트리밋 등)
- 다음 업데이트 시간: (예: 10분 후)

---

## 25. 장애 중 의사결정: 롤백/기능 플래그/레이트 리밋/격리

장애 때 가장 강한 도구는 “정교한 수정”이 아니라 “큰 스위치”인 경우가 많습니다.

### 25.1 롤백
- 배포 이후 바로 문제면 1순위
- 카나리/블루그린이면 더 빠름

### 25.2 기능 플래그 OFF
- 특정 기능(검색/추천/리포트)이 원인인 경우, 기능 OFF가 가장 빠른 완화일 수 있음

### 25.3 레이트 리밋/부하 격리
- 특정 클라이언트가 폭주하면 전체가 죽습니다.
- 레이트 리밋으로 “전체 사용자 보호”가 우선입니다.

### 25.4 디그레이드(Degrade)
- “완전 실패” 대신 “기능 일부만 제한”
- 예: 추천/랭킹은 캐시값/기본값으로

---

## 26. 장애 후 회고(Postmortem)

좋은 회고의 핵심:
- 개인 비난이 아니라 시스템 개선
- “재발 방지 액션”이 구체적(기한/오너)

### 26.1 회고 템플릿(권장)
- 요약(1~2줄)
- 사용자 영향(에러율, 지연, 기간)
- 타임라인(분 단위)
- 탐지(어떤 알람/누가/언제)
- 원인(기술적, 프로세스적)
- 무엇이 잘 됐나/안 됐나
- 재발 방지 액션(오너, due date)
- 관련 링크(대시보드, PR, 런북)

---

# Part 7. 운영 가능성(Operability)

## 27. Timeout/Retry/Circuit Breaker: 관측 가능한 방식으로

타임아웃/재시도는 “신뢰성 향상” 도구이지만,
관측 없이 쓰면 폭탄이 됩니다.

### 27.1 타임아웃 원칙
- 외부 호출은 타임아웃 필수
- 타임아웃이 없으면 커넥션/스레드가 묶여 연쇄 장애

### 27.2 재시도 원칙
- 무작정 재시도 = 폭주
- 지수 백오프 + jitter
- 멱등성 없는 쓰기는 위험(결제/주문은 특히)

### 27.3 재시도를 메트릭으로 남겨라
- retry_count
- timeout_count
- circuit_open_count

“재시도 때문에 살아났다”는 착시를 막으려면 관측이 필요합니다.

---

## 28. 헬스체크(readiness/liveness)와 그레이스풀 셧다운

### 28.1 readiness는 “트래픽 받아도 되나?”
- DB 연결 불가, 마이그레이션 중, 의존성 장애면 ready=false
- 로드밸런서가 트래픽을 빼게 함

### 28.2 liveness는 “프로세스 살아있나?”
- 데드락/스레드 정지 등 프로세스가 망가졌을 때 재시작 유도

### 28.3 그레이스풀 셧다운
- 종료 신호를 받으면 새 요청을 받지 않고
- 진행 중 요청을 마무리하고
- 커넥션을 정리하고 종료

이게 안 되면 배포 중 에러율 스파이크가 생깁니다.

---

## 29. 배포 관측: 배포가 원인인 장애를 2분 만에 찾기

배포가 원인인지 확인하는 가장 빠른 방법:
- 메트릭에 `version`/`deployment` 라벨을 넣고 비교
- 배포 타임라인을 대시보드에 표시(annotations)

실무 팁:
- “배포 직후 에러율 상승” 패턴은 매우 흔합니다.
- 롤백이 빠른 구조(블루그린/카나리)가 곧 신뢰성입니다.

---

## 30. 용량 계획(capacity) 기본

주니어라도 다음 4개는 감각이 있어야 합니다.

- CPU: 처리량과 직결(스케일 아웃)
- 메모리: OOM/GC/캐시 영향
- DB: 커넥션/락/IO가 병목이 되는 경우 많음
- 큐: lag가 증가하면 “미처리 작업”이 쌓인다

---

# Part 8. 케이스 스터디

## 31. 케이스 1: p99 지연이 갑자기 튄다

### 증상
- p50은 정상인데 p99만 튄다
- 에러율은 낮거나 없음

### 흔한 원인 후보
- DB 락 대기/슬로우쿼리
- 커넥션 풀 대기
- 외부 API 타임아웃 직전 지연
- GC/Stop-the-world
- 큐/스레드풀 포화

### 트리아지 순서(현실 루틴)
1) 지연이 특정 endpoint인가?
2) 트레이스에서 가장 긴 span은 어디인가?
3) DB라면:
   - active connections/lock waits/slow query
4) 풀 대기라면:
   - 풀 대기 시간/active vs idle
5) GC라면:
   - GC time, heap usage, pause time

### 즉시 완화 옵션
- 문제 endpoint 레이트 리밋
- 캐시 사용/디그레이드
- 롤백/기능 플래그 off
- (DB) 장기 트랜잭션/배치 중단

---

## 32. 케이스 2: 에러율 스파이크

### 증상
- 5xx 증가
- 특정 코드(예: 500/503/504) 집중

### 원인 후보
- 배포 버그(NullPointer, 설정 누락)
- 외부 의존성 장애(결제/인증)
- DB 에러(커넥션 고갈, deadlock)
- 입력 트래픽 급증(DDoS/크롤러)

### 트리아지
1) 배포 타임라인과 에러 시작 시각 비교
2) 에러 타입/스택트레이스 상위 1~3개 확인
3) 외부 의존성 latency/error 확인
4) 레이트 리밋/서킷 브레이커 동작 여부 확인

---

## 33. 케이스 3: DB 커넥션 고갈

### 증상
- DB timeout, “too many connections”, pool exhausted
- API 지연/에러 동시 발생

### 원인 후보
- 슬로우 쿼리 증가로 커넥션이 오래 잡힘
- 트랜잭션이 길다 / idle in transaction
- 앱 인스턴스 수 증가 + 풀 크기 과대
- 누수(leak): 커넥션 반환 안 함

### 트리아지
1) 앱 풀 대기 시간 확인
2) DB의 active sessions/long running queries 확인
3) 락 대기/블로킹 확인
4) 최근 배포로 특정 쿼리가 늘었는지 확인

### 완화
- 풀 크기 줄이거나 인스턴스 줄이기(상황에 따라)
- 슬로우 쿼리/블로킹 트랜잭션 중단(최후)
- timeout 설정 강화

---

## 34. 케이스 4: 큐 적체(consumer lag)

### 증상
- lag 증가
- 처리량이 소비량을 못 따라감

### 원인 후보
- 소비자 에러/재시도 폭주
- 다운스트림(DB) 병목
- 메시지 크기/처리 로직 증가
- 파티션 불균형(특정 키 핫스팟)

### 트리아지
1) consumer 처리량/에러율
2) DB/외부 의존성 지연
3) 특정 파티션/키 집중 여부
4) 최근 배포로 처리 시간이 늘었는지

---

## 35. 케이스 5: 메모리 릭/GC 폭주

### 증상
- 메모리 계속 증가
- GC time 증가, pause 증가
- 결국 OOM kill

### 원인 후보
- 캐시 무한 성장
- 요청 바디/응답 바디를 메모리에 쌓음
- 버퍼/스트림 누수
- 스레드/코루틴 누수

### 트리아지
- 메모리/GC 대시보드
- 트레이스에서 큰 payload 구간
- 최근 배포/특정 요청 패턴과 상관관계

---

# Appendices

## A. 운영 체크리스트

### 최소 관측성(서비스별)
- [ ] RED 대시보드(RPS/에러율/지연 p95/p99)
- [ ] 주요 의존성(DB/Cache/외부 API) 대시보드
- [ ] 배포 버전/환경 태그(버전별 비교 가능)
- [ ] 구조화 로그 + trace_id 포함
- [ ] 트레이싱(최소 inbound + DB + 외부 호출)
- [ ] SLO(성공률/지연) + 증상 기반 알람
- [ ] 알람에 런북 링크

### 장애 대응 체계
- [ ] 5분 트리아지 루틴이 문서화됨
- [ ] 역할(IC/Scribe/Comms) 최소 정의
- [ ] 회고 템플릿과 액션 트래킹

---

## B. 자주 하는 실수 50

1. 로그에 요청 바디 전체를 남김(PII/비용 폭발)  
2. Authorization/Cookie/토큰/비밀번호 같은 비밀 값을 로그에 남김  
3. user_id, order_id 같은 고유값을 메트릭 label로 넣어 카디널리티 폭발  
4. 라우트 템플릿이 아니라 실제 path(`/users/123`)를 label로 사용  
5. query string/user agent 전체를 label로 넣음(폭발)  
6. 평균 지연만 보고 p95/p99를 무시  
7. 지연시간을 histogram 없이 gauge/average로만 기록  
8. 에러율을 전체로만 보고 route/의존성 분해가 안 됨  
9. 알람 임계치를 고정값으로만 두고 트래픽 변동을 무시  
10. 알람이 너무 많아져서 결국 알람을 꺼버림(알람 피로)  
11. 알람 메시지에 대시보드 링크가 없음(바로 못 본다)  
12. 알람 메시지에 런북 링크가 없음(뭘 해야 하는지 모른다)  
13. 배포 버전/커밋 태그가 메트릭에 없어 배포 원인 판단이 느림  
14. trace_id/span_id가 로그에 없어 로그-트레이스 연결이 불가능  
15. 구조화 로그 없이 텍스트 로그만 남겨 필드 검색이 어렵다  
16. 에러 로그에 stacktrace만 있고 error_type/dependency 분류가 없다  
17. INFO 로그를 샘플링 없이 무한히 쌓아 비용과 노이즈가 폭발  
18. 같은 에러를 요청마다 스택트레이스로 찍어 로그가 ‘폭주’한다  
19. 외부 호출에 타임아웃이 없다(연쇄 장애 시작점)  
20. 재시도에 백오프/jitter가 없어 retry storm를 만든다  
21. 재시도/타임아웃 횟수를 메트릭으로 남기지 않아 “폭주”를 못 본다  
22. 서킷 브레이커 상태(open/half-open)를 관측하지 않는다  
23. readiness/liveness를 혼동해 배포 시 트래픽이 이상하게 튄다  
24. readiness 체크가 외부 호출을 포함해 느리거나 불안정하다  
25. graceful shutdown이 없어 배포/스케일 인 때 5xx 스파이크가 생긴다  
26. “증상 알람” 없이 원인 알람(CPU 80% 등)만 잔뜩 만든다  
27. 저트래픽 환경에서 에러율 알람이 노이즈가 되는데 traffic guard가 없다  
28. 스테이징/프리프로덕션에 관측성이 없어 배포 전 문제를 못 잡는다  
29. 런북이 오래되어 링크가 죽거나 절차가 틀리다  
30. 장애 중 변경(롤백/플래그) 기록이 없어 타임라인이 엉킨다  
31. 인시던트 채널 없이 개인 DM/여러 채널에 흩어져 커뮤니케이션이 망가진다  
32. 장애 선언이 늦어 중복 작업/혼선이 커진다  
33. 회고가 “누가 잘못했나”로 끝나고 시스템 개선이 없다  
34. 회고 액션에 오너/기한이 없어 실행되지 않는다  
35. 외부 의존성 장애를 내부 코드 버그로 착각해 삽질한다  
36. 캐시 hit rate만 보고 latency/eviction/스탬피드를 안 본다  
37. 캐시 TTL 동시 만료(스탬피드) 대비가 없다(TTL jitter/singleflight 미사용)  
38. DB statement_timeout/lock_timeout이 없어 무한 대기가 생긴다  
39. DB 커넥션 풀 크기를 무작정 키워 DB max_connections를 초과한다  
40. 슬로우 쿼리를 EXPLAIN 없이 “감”으로 인덱스를 추가/삭제한다  
41. 로그 마스킹/레드액션이 없어 개인정보가 그대로 남는다  
42. 메트릭에 service/env 태그가 없어 여러 환경이 섞여 해석이 어렵다  
43. 알람 라우팅이 잘못되어 책임 팀이 아닌 곳으로 페이지가 간다  
44. 알람 중복(동일 사건에 10개)이 발생하는데 디듀프/그룹핑이 없다  
45. 배포 마커/변경 로그(annotations)가 없어 “언제부터”가 늦다  
46. SLO 없이 임계치 알람만 있어 “사용자 영향”이 기준이 아니다  
47. SLI 정의가 애매(4xx 포함 여부, 성공 정의)해서 지표가 흔들린다  
48. 트레이싱을 100% 수집해 비용/성능이 폭발한다  
49. tail sampling 없이 느린 요청/에러 요청이 샘플링에서 누락된다  
50. 로그/메트릭/트레이스 리텐션 정책이 없어 저장 비용이 통제되지 않는다

---

## C. 면접/과제 대비 질문 40

1. Observability(관측성)와 Monitoring(모니터링)의 차이는 무엇인가요?  
2. Logs/Metrics/Traces는 각각 어떤 질문에 답하나요?  
3. Golden Signals 4가지는 무엇이고, 각각 왜 중요한가요?  
4. RED 방법과 USE 방법은 어떤 상황에서 각각 유용한가요?  
5. 평균 지연시간이 위험한 이유를 p99 관점에서 설명해보세요.  
6. Histogram 없이 p95/p99를 제대로 보기 어려운 이유는 무엇인가요?  
7. SLI/SLO/Error Budget을 실제 예시로 설명해보세요.  
8. “성공률” SLI에서 4xx를 포함할지 제외할지 어떤 기준으로 결정하나요?  
9. Burn rate는 무엇이고, 왜 multi-window 알람을 쓰나요?  
10. 알람 피로(alert fatigue)가 생기는 이유 3가지와 해결책 3가지를 말해보세요.  
11. 좋은 알람 메시지에 반드시 포함되어야 할 요소는 무엇인가요?  
12. 런북(runbook)에는 어떤 항목이 있어야 하나요?  
13. 구조화 로그(Structured logging)의 장점은 무엇인가요?  
14. 로그에 trace_id/request_id를 넣는 이유와 구현 시 주의점은?  
15. 메트릭 label cardinality 폭발이 무엇이고 왜 위험한가요?  
16. route 템플릿과 실제 path를 구분해야 하는 이유는?  
17. Head sampling과 Tail sampling의 차이와 트레이드오프는?  
18. “p99 지연 상승” 장애가 났을 때 5분 트리아지 순서를 말해보세요.  
19. “5xx 에러율 상승” 장애가 났을 때 가장 먼저 확인할 3가지는?  
20. DB 문제를 슬로우 쿼리 vs 락 대기 vs 커넥션 고갈로 나누는 기준은?  
21. 커넥션 풀 고갈이 생기는 원인 3가지는 무엇인가요?  
22. readiness와 liveness의 차이를 설명하고, 잘못 설정하면 어떤 일이 생기나요?  
23. graceful shutdown이 없는 서비스에서 배포 시 어떤 문제가 생기나요?  
24. 외부 의존성 장애가 있을 때 재시도는 왜 위험할 수 있나요?  
25. 재시도 폭주(retry storm)를 막는 3가지 방법은 무엇인가요?  
26. 서킷 브레이커가 해결하는 문제는 무엇이고, 관측해야 할 지표는?  
27. 캐시 스탬피드(thundering herd)의 원인과 완화 전략 3가지를 말해보세요.  
28. 큐 consumer lag가 증가할 때 원인 후보와 대응 방법은?  
29. 쿠버네티스에서 CrashLoopBackOff가 났을 때 확인 순서는?  
30. OOMKilled와 memory leak의 차이를 어떻게 구분하나요?  
31. CPU throttling이 지연을 만드는 이유를 설명해보세요.  
32. 배포가 원인인 장애를 빠르게 확인하기 위한 관측 장치는 무엇인가요?  
33. 카나리 배포에서 반드시 비교해야 할 지표는 무엇인가요?  
34. 인시던트 역할(IC/Scribe/Comms)은 왜 필요한가요?  
35. 회고(Postmortem)에 반드시 포함되어야 할 내용은 무엇인가요?  
36. MTTD/MTTR은 무엇이고, 각각을 줄이기 위한 실무 방법은?  
37. 비즈니스 SLI(주문/결제 성공률 등)가 기술 지표만으로는 안 되는 이유는?  
38. 데이터 품질 모니터링이 필요한 사례를 하나 들어보세요.  
39. Synthetic monitoring(합성 모니터링)은 무엇이고 언제 유용한가요?  
40. “2주 안에 관측성 수준을 끌어올리는 계획”을 당신의 서비스에 맞게 제안해보세요.

---

## D. 로그/지표/트레이스 필드 표준(권장 스키마)

### 로그 필드(추천)
- ts, level, service, env, version
- trace_id, span_id, request_id
- method, route, status, duration_ms
- user_id(가능하면), tenant_id(멀티테넌트면)
- error_type, error_message, stacktrace(에러 시)

### 메트릭 레이블(추천)
- service, env, route(template), method, status_class
- dependency(name)
- version(배포 비교용)

### 트레이스 속성(추천)
- http.route, http.method, http.status_code
- db.system, db.operation, db.name(가능하면)
- net.peer.name, peer.service(의존성)

---

## E. PromQL 치트시트

- RPS:
```promql
sum(rate(http_server_requests_total[5m]))
```

- 에러율(5xx):
```promql
sum(rate(http_server_requests_total{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_total[5m]))
```

- p95 지연(히스토그램):
```promql
histogram_quantile(0.95,
  sum(rate(http_server_request_duration_seconds_bucket[5m])) by (le)
)
```

- CPU 사용률(예시):
```promql
avg(rate(process_cpu_seconds_total[5m]))
```

> 실제 메트릭 이름은 라이브러리/환경마다 다릅니다. 치트시트는 “패턴”을 기억하는 용도입니다.

---

## F. “오늘부터” 적용하는 2주 플랜

### 1~3일차: 로그 표준화
- 구조화 로그(JSON) 도입
- trace_id/request_id 포함
- 에러 로그에 dependency/timeout 정보 포함

### 4~7일차: RED 대시보드 만들기
- RPS/에러율/p95/p99
- 배포 버전별 비교 가능하게 태깅
- 상위 느린 endpoint TOP

### 8~10일차: 트레이싱 최소 셋업
- inbound + outbound HTTP + DB span
- 에러/느린 요청은 100% 샘플링(가능하면)

### 11~14일차: 알람 정리 + 런북 작성
- SLO 기반 증상 알람 1~2개로 시작
- 알람 메시지에 대시보드/런북 링크
- 5분 트리아지 런북을 문서화

---

## 끝맺음

관측성은 “세팅”이 아니라 “운영 문화/습관”입니다.  
주니어가 가장 빠르게 성장하는 방법은:

- 내가 맡은 기능에 대해
  - 로그/메트릭/트레이스를 심고
  - 알람과 런북을 만들고
  - 장애/이슈가 났을 때 스스로 1차 트리아지를 해보고
  - 회고로 재발 방지를 남기는 것

이 한 사이클을 2~3번만 돌려도, 팀에서의 신뢰도가 확 달라집니다.


---

# Part 9. “운영이 잘 되는 코드”를 만드는 구현 디테일(언어/프레임워크 공통)

이 파트는 관측성의 원리를 “실제 코드”로 옮길 때 주니어가 가장 많이 막히는 지점들을 정리합니다.  
목표는 **‘어디에, 무엇을, 어떤 형태로 심을지’**를 감각적으로 잡는 것입니다.

## 36. 요청 단위 표준 이벤트: “요약 1줄 + 예외 1줄”로 충분하다

### 36.1 요청 요약 로그(권장)
요청마다 아래 한 줄만 있어도 장애 때 훨씬 빨라집니다.

- route(템플릿), method
- status_code
- duration_ms
- trace_id / request_id
- user_id/tenant_id(가능할 때만)

예(JSON):
```json
{
  "level":"INFO",
  "msg":"request completed",
  "method":"POST",
  "route":"/v1/orders",
  "status":201,
  "duration_ms":32,
  "trace_id":"...",
  "request_id":"...",
  "user_id":123
}
```

### 36.2 예외 로그(권장)
예외는 “스택트레이스 전체”를 남기기 전에 먼저:
- error_type
- error_message
- error_classification(예: dependency_timeout, validation_error, db_deadlock)
- dependency(어디가 실패했나)
를 필드로 남기는 게 중요합니다.

이유:
- 장애 때는 “무슨 예외인지”를 집계하고 싶지, 스택을 다 읽고 싶지 않습니다.

---

## 37. 라우트(경로) 라벨링: path를 그대로 쓰면 망한다

관측에서 가장 흔한 실수:
- 메트릭/로그에 `/users/123/orders/999` 같이 “실제 path”를 넣음
- 고유값이 포함되어 시계열/로그가 폭발

### 37.1 원칙
- 항상 라우트는 템플릿으로 남긴다
  - `/users/{userId}/orders/{orderId}`
  - `/users/:userId/orders/:orderId`

### 37.2 실무 팁
- 프레임워크가 라우트 템플릿을 제공하는지 확인
- 제공하지 않으면 “라우팅 미들웨어”에서 템플릿을 태그로 넣는 것이 유리

---

## 38. 에러 분류(Error taxonomy): “500만 보면 끝”이 아니다

운영에서 중요한 건 “실패를 나누는 것”입니다.

### 38.1 추천 분류(현실 버전)
- validation_error: 4xx 입력 오류(알람 대상 아님)
- auth_error: 인증/인가(보안 이벤트로 분리)
- dependency_timeout: 외부/DB 타임아웃(중요)
- dependency_error: 외부 5xx/네트워크(중요)
- db_deadlock / serialization_failure: 재시도 가능 오류(중요)
- rate_limited: 자체 보호 장치 발동(중요/경보 수준 조절)
- bug: 코드 버그(배포/롤백 연결)

### 38.2 왜 분류가 중요한가
- 알람/대응책이 달라집니다.
  - validation_error가 늘었다 -> 프론트 버그/봇/공격
  - dependency_timeout이 늘었다 -> 타임아웃/서킷/격리
  - bug가 늘었다 -> 롤백

---

## 39. “에러율” 정의: 무엇을 성공으로 볼 것인가

SLI/알람 설계에서 반드시 합의해야 합니다.

### 39.1 흔한 기준
- 2xx/3xx 성공, 5xx 실패
- 4xx는 보통 “사용자 오류”로 분리(단, 429/401/403은 별도 집계)

### 39.2 엔드포인트별로 다를 수 있음
예:
- 검색 API는 429(레이트 리밋)이 많으면 사용자 영향이 큼 -> SLI에 포함 고려
- 인증 API는 401이 정상일 수도 있음(로그인 실패)

실무 팁:
- 서비스 전체 SLO는 단순하게 시작하고(예: 5xx만),
- 핵심 경로에 대해 세분화 SLO를 추가하는 방식이 성공 확률이 높습니다.

---

## 40. 로그 질의 예시: “장애 때 실제로 쓰는” 쿼리 패턴

아래는 툴과 무관하게 통하는 패턴입니다.

### 40.1 특정 trace_id로 전체 흐름 보기
- trace_id = X인 로그만 필터링
- 시간순 정렬

### 40.2 특정 에러 타입 top
- 지난 10분 error_type별 count
- top 3를 보고 가장 큰 것부터 대응

### 40.3 느린 요청만 보기
- duration_ms > 1000
- route 별로 집계(어느 endpoint가 느린지)

### 40.4 (Loki 예시) JSON 로그라면
```text
{service="orders-api", env="prod"} | json | level="ERROR"
```

느린 요청:
```text
{service="orders-api", env="prod"} | json | duration_ms > 1000
```

> 실제 문법은 도구마다 다르지만, 핵심은 “필드 기반 검색”이 가능해야 한다는 점입니다.

---

# Part 10. OpenTelemetry “최소 셋업” 가이드(개념 + 예시)

OpenTelemetry(OTel)는 트레이싱/메트릭/로그를 표준 형태로 수집하는 생태계입니다.  
여기서는 벤더에 상관없이 통하는 “최소 구성”을 설명합니다.

## 41. 최소 구성 아키텍처

1) 애플리케이션(서비스)  
   - OTel SDK/에이전트로 trace/metric을 생성
2) OTel Collector(선택이지만 강력 추천)  
   - 수집(OTLP) → 처리(batch, sampling) → 내보내기(export)
3) 백엔드 스토리지/분석(벤더/오픈소스)
   - Trace: Tempo/Jaeger/Datadog/NewRelic…
   - Metrics: Prometheus/Datadog…
   - Logs: Loki/ELK/Datadog…

Collector를 두는 이유:
- 샘플링/필터링을 중앙에서 통제 가능
- 앱 부담 감소
- 벤더 교체/멀티 백엔드가 쉬움

---

## 42. Trace/Log 연동: “trace_id를 로그에 자동 주입”이 목표

가장 중요한 운영 UX:
- 트레이스에서 버튼 한 번으로 관련 로그 보기
- 로그에서 trace_id 클릭하면 트레이스로 이동

그래서:
- 로그에 trace_id/span_id가 반드시 있어야 합니다.
- 많은 프레임워크에서 MDC(자바), context(노드), middleware(파이썬)로 구현합니다.

---

## 43. 샘플링 추천 전략(현실 버전)

### 43.1 기본
- head sampling 1~5% (트래픽이 큰 경우)
- 에러/느린 요청은 100% 남기고 싶다면 tail sampling 필요

### 43.2 “느린 요청 100%”가 필요한 이유
p99 문제는 “희귀한 느림”이 원인인 경우가 많습니다.  
그런데 head sampling은 느린 요청을 놓칠 수 있습니다.

해결:
- collector에서 tail sampling(조건 기반)을 적용
  - status_code >= 500
  - duration_ms > 1000
같은 조건으로 남깁니다.

---

## 44. (예시) OTel Collector 설정 스니펫(개념용)

환경마다 다르니 “패턴”만 참고하세요.

```yaml
receivers:
  otlp:
    protocols:
      http:
      grpc:

processors:
  batch:
  # tail sampling은 환경에 따라 플러그인이 다를 수 있으니 개념만 참고
  # tail_sampling:
  #   policies:
  #     - type: latency
  #       threshold_ms: 1000
  #     - type: status_code
  #       status_codes: [ERROR]

exporters:
  logging:
  # otlp/jaeger/tempo/prometheus 등으로 export 가능

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [logging]
```

---

# Part 11. SLO/알람을 “실제로” 설계하는 워크북

이 파트는 SLO를 말로만 아는 상태에서,
실제로 수치/창/알람으로 옮기는 과정을 보여줍니다.

## 45. SLI 만들기: “좋은 이벤트 / 전체 이벤트”

예: HTTP 성공률 SLI

- 전체 이벤트: 모든 요청 수
- 좋은 이벤트: 성공 요청 수(예: 2xx/3xx)

```promql
good = sum(rate(http_requests_total{status_class=~"2xx|3xx"}[5m]))
total = sum(rate(http_requests_total[5m]))
sli = good / total
```

실무 팁:
- status_class 라벨이 없다면 2xx/3xx regex로 계산할 수 있습니다.
- 4xx를 제외할지 포함할지는 “정책”입니다.

---

## 46. SLO 만들기: 목표 수치와 창

예:
- SLO: 99.9% (30일)
- 에러 버짓: 0.1% = 0.001

### 46.1 직관
- 30일 동안 전체 요청 중 실패가 0.1%까지는 허용
- 이 허용치를 “얼마나 빨리 태우는지”가 burn rate

---

## 47. Burn Rate 알람(개념): “빠르게 망가지는지” 감지

burn_rate = (관측된 에러율) / (허용 에러율)

예: SLO 99.9%이면 허용 에러율 = 0.001  
관측 에러율이 0.01(1%)이면 burn_rate = 10 (버짓을 10배 속도로 소모)

### 47.1 왜 burn rate가 유용한가
- 트래픽이 늘어도 기준이 “버짓”이라 상대적으로 안정적
- 단기/장기 두 창을 같이 보면 빠르고 안전한 감지가 가능

### 47.2 현실적인 2-윈도우 알람 패턴
- 빠른 창(예: 5m): 급격한 악화 탐지
- 느린 창(예: 1h): 지속 문제 탐지

알람은 보통 “둘 다 만족할 때” 페이지합니다.
- 5m burn_rate > A AND 1h burn_rate > B

> 수치(A/B)는 조직/서비스 특성에 따라 달라집니다.  
> 중요한 건 “짧은 노이즈”를 줄이고 “진짜 영향”을 잡는 패턴입니다.

---

## 48. 알람 튜닝 6단계

1) 알람이 무엇을 보호하는가(사용자 영향)  
2) false positive의 원인이 무엇인가(피크/배포/저트래픽)  
3) 조건에 traffic guard 추가(요청량이 적으면 알람 억제)  
4) 요약 라벨을 줄여 알람 수를 줄임(route별 알람 남발 금지)  
5) runbook 링크/조치가 있는지 확인  
6) 장애 후 회고에서 알람 개선 액션을 남김(정기적인 개선 루프)

---

# Part 12. 런북(Runbook) & 플레이북(Playbook) 템플릿

런북은 “문서”가 아니라 “장애 때 쓰는 조작 설명서”입니다.  
형식이 중요합니다.

## 49. 런북 템플릿(추천)

- 알람 이름:
- 의미(사용자 영향):
- 확인 대시보드:
- 5분 트리아지(순서대로):
  1) ...
  2) ...
- 즉시 완화 옵션:
  - 롤백/플래그/레이트리밋/스케일
- 에스컬레이션:
  - 누구/어느 채널/어떤 정보 포함
- 후속 조치:
  - 회고 링크/티켓 링크

---

## 50. 플레이북 예시 1: “에러율 5xx 급증” 런북

### 50.1 5분 트리아지
1) 최근 배포 여부 확인(배포 타임라인)
2) error_type/top stacktrace 확인(로그 집계)
3) 특정 endpoint인지 확인(route별 5xx)
4) 외부 의존성 오류인지 확인(외부 API/DB 메트릭)
5) 특정 버전만 문제인지 확인(version 라벨)

### 50.2 즉시 완화
- 배포 직후면 롤백(가장 빠름)
- 특정 기능 원인이면 플래그 off
- 외부 의존성이 원인이면:
  - 타임아웃 단축
  - 서킷 활성화
  - 디그레이드 응답(캐시/기본값)

### 50.3 회고 액션 후보
- 배포 전 canary/테스트 강화
- 의존성 타임아웃/서킷/재시도 정책 개선
- 알람 메시지/런북 개선

---

## 51. 플레이북 예시 2: “p99 지연 상승” 런북

### 51.1 5분 트리아지
1) p99 상승이 전체인가, 특정 route인가
2) 트레이스에서 가장 긴 span 확인(원인 후보)
3) DB 락/커넥션/슬로우쿼리 확인
4) 풀 대기(스레드/커넥션) 확인
5) GC/메모리/CPU 확인

### 51.2 즉시 완화
- 레이트 리밋(부하 감소)
- 캐시 적용/TTL 증가(임시)
- 배치 중단(락/IO 줄이기)
- 문제 기능 플래그 off

---

## 52. 플레이북 예시 3: “DB 커넥션 고갈” 런북

### 52.1 5분 트리아지
1) 앱 풀 대기 시간 확인
2) DB active sessions/long queries 확인
3) 락 대기/blocked vs blocking 확인
4) 최근 배포/쿼리 증가 여부 확인
5) 커넥션 누수 의심: 커넥션 반환/세션 수 추적

### 52.2 즉시 완화
- 풀 크기/인스턴스 조정(상황에 따라)
- 슬로우쿼리 최악 1개를 임시 차단(가능하면)
- blocking 트랜잭션 중단(최후)

---

# Part 13. “장애 대응 커뮤니케이션” 실전

기술 해결만큼 중요한 게 커뮤니케이션입니다.  
특히 주니어가 신뢰를 얻는 포인트이기도 합니다.

## 53. 장애 선언(Incident declaration) 기준

다음 중 하나면 “인시던트 채널”을 만들 가치가 있습니다.

- SLO 영향(에러율/지연)이 분 단위로 커지고 있다
- 사용자 CS가 증가한다
- 결제/회원가입 등 핵심 플로우가 깨졌다
- 복구 시간이 15분 이상 걸릴 것 같다

선언이 늦으면:
- 각자 흩어져 대응
- 중복 작업/커뮤니케이션 혼선
- 더 오래 걸립니다

---

## 54. 슬랙/회의 운영(권장 패턴)

- #incident-YYYYMMDD-short-title 채널 생성
- 상단 고정(pinned):
  - 대시보드 링크
  - 런북 링크
  - 타임라인 문서(구글 문서 등)
- 10분마다 상황 업데이트(Comms)

---

## 55. 외부 커뮤니케이션(상태 페이지/공지) 최소 템플릿

- 영향: 어떤 기능이 어떤 사용자에게 영향을 받는지
- 시작 시각: (KST)
- 현재 상태: 조사 중/완화 중/복구 완료
- 우회 방법: 있으면 제공
- 다음 업데이트 시각: (예: 30분 후)

---

# Part 14. 실전 연습 과제(혼자 해도 실력이 는다)

관측성은 실제로 “손으로 해봐야” 늘어납니다.

## 56. 과제 1: 내 서비스(또는 토이 프로젝트)에 RED 대시보드 만들기
- RPS, 에러율, p95/p99
- 배포 버전별 비교
- 상위 느린 route TOP

완료 기준:
- “느린 요청이 발생했을 때 어느 route인지 1분 안에 말할 수 있다”

## 57. 과제 2: 알람 2개만 제대로 만들기
- 증상 알람: 에러율(또는 SLO burn rate)
- 원인 보조 알람: DB 커넥션 풀 대기(또는 큐 lag)

완료 기준:
- 알람 메시지에 대시보드/런북 링크가 들어있다
- 5분 트리아지 절차가 문서에 있다

## 58. 과제 3: 장애 시뮬레이션(카오스 미니)
- DB를 잠깐 느리게 만들기(의도적으로 슬로우 쿼리)
- 외부 호출을 일부 타임아웃 나게 만들기
- 트래픽을 2배로 올리기(로컬/스테이징)

완료 기준:
- 트레이스/로그/메트릭으로 원인을 “근거”로 말할 수 있다
- 완화 전략(레이트리밋/롤백/플래그)을 1개 이상 실행해봤다

---

# Appendix G. 알람 메시지 템플릿(복붙용)

```text
[SEV-2] orders-api 5xx error rate high (10m)

Impact:
- 5xx rate: 2.3% (baseline 0.1%)
- Affected routes: /v1/orders (70% of errors)
- Started at: 2026-02-25 14:12 KST

Dashboards:
- Service RED: <link>
- Dependency(DB): <link>
- Traces: <link>

Runbook:
- <link>

Current hypothesis:
- Suspected DB lock wait after deployment v1.2.3

Actions taken:
- Rolled back to v1.2.2 at 14:18
Next update:
- 14:25 KST
```

---

# Appendix H. “이건 꼭 넣자” 최소 관측 필드/지표 목록

## H.1 서비스 공통 메트릭
- http_requests_total (method, route, status_class)
- http_request_duration_seconds (histogram)
- process_cpu_seconds_total
- process_resident_memory_bytes
- event_loop_lag_seconds(Node라면)
- thread_pool_queue_size(있다면)

## H.2 DB 관련(가능한 범위)
- db_query_duration_seconds (operation, table)
- db_pool_active, db_pool_idle, db_pool_pending
- db_errors_total (timeout, deadlock 등)

## H.3 캐시/큐
- cache_hit_total / cache_miss_total
- cache_latency_seconds
- queue_lag, queue_depth
- consumer_errors_total

---

# 마무리(현실 조언)

관측성 능력은 “대형사 SRE만 하는 것”이 아니라,
주니어가 빠르게 성장하는 가장 현실적인 레버리지입니다.

- 장애 때 근거를 가지고 말하는 사람
- 알람을 줄이고, 런북을 만들고, 회고로 시스템을 개선하는 사람
- “운영 가능한 기능”을 만드는 사람

이 사람은 AI 시대에도 대체되기 어렵습니다.


---

# Part 15. Kubernetes/클라우드 환경에서의 관측성과 장애 대응(현실 실전)

요즘 백엔드 운영은 대부분 컨테이너/쿠버네티스 위에서 이뤄집니다.  
쿠버 환경의 장애는 “앱 코드”가 아니라 **인프라 계층(스케줄링/네트워크/DNS/리소스 제한)**에서 시작하는 경우도 많습니다.

> 이 파트는 특정 클라우드(AWS/GCP/Azure)에 종속되지 않게,  
> 쿠버네티스에서 공통으로 겪는 장애 패턴과 관측 포인트를 정리합니다.

## 59. 쿠버네티스에서 “이상하다”의 80%는 6개 신호로 보인다

### 59.1 Pod/Deployment 상태
- CrashLoopBackOff
- OOMKilled
- ImagePullBackOff
- Pending(스케줄링 실패)
- Ready false( readiness 실패 )

### 59.2 리소스 포화
- CPU throttling(제한으로 인한 성능 저하)
- 메모리 제한 근접/초과(OOM)
- 노드 디스크 압력(DiskPressure)
- 네트워크 대역/패킷 드롭

### 59.3 네트워크/서비스 디스커버리
- DNS 문제(CoreDNS)
- 서비스 엔드포인트 없음(Selector/Label mismatch)
- Ingress/LB 설정 문제

---

## 60. 쿠버 트리아지 “명령 7개” (장애 때 가장 많이 쓰는 것)

> 아래 명령은 “패턴”을 익히는 게 목적입니다. 환경에 맞게 namespace/label을 바꾸세요.

1) 전체 상태 보기
```bash
kubectl get pods -n <ns>
kubectl get deploy -n <ns>
```

2) 특정 pod 상세(이벤트가 중요)
```bash
kubectl describe pod <pod> -n <ns>
```

3) 최근 이벤트 보기
```bash
kubectl get events -n <ns> --sort-by=.metadata.creationTimestamp | tail -n 50
```

4) 로그 확인(최근)
```bash
kubectl logs <pod> -n <ns> --since=10m
```

5) 이전 로그(재시작 직전)
```bash
kubectl logs <pod> -n <ns> --previous
```

6) 리소스 확인(메트릭 서버 필요)
```bash
kubectl top pod -n <ns>
kubectl top node
```

7) 서비스/엔드포인트 확인
```bash
kubectl get svc -n <ns>
kubectl get endpoints -n <ns>
```

---

## 61. 흔한 장애 1: CrashLoopBackOff

### 증상
- Pod가 계속 재시작
- 트래픽이 줄거나(Ready false), 에러율 증가

### 원인 후보
- 앱 시작 시 예외(설정 누락, secret/env 문제)
- 외부 의존성 연결 실패(DB 주소/자격증명)
- OOM(메모리 부족으로 강제 종료)

### 트리아지
1) `kubectl describe pod`에서 restart reason 확인
2) `kubectl logs --previous`로 직전 종료 원인 확인
3) 배포 직후면 “롤백”이 가장 빠른 완화

### 예방(운영 가능성)
- 시작 시 설정 검증(필수 env 없으면 즉시 fail)
- readiness를 “준비된 상태”에만 true로(의존성 준비 포함 여부는 정책)
- 메모리 request/limit 적절히 설정

---

## 62. 흔한 장애 2: OOMKilled vs Memory leak 구분

### OOMKilled
- 쿠버 이벤트/describe에 OOMKilled 표시
- 메모리 limit 초과

대응:
- 일시적 증가(트래픽/배치/큰 payload)인지 확인
- 리밋 조정(즉시 완화)
- 근본: payload 제한, 스트리밍, 캐시 제한, 누수 확인

### Memory leak
- 메모리 사용이 “계속 상승”
- GC가 잦아지고 pause 증가(언어에 따라)
- 결국 OOM

대응:
- 특정 요청/경로/배포와 상관관계 확인
- heap dump/프로파일(가능한 환경에서)
- 큰 컬렉션/캐시 성장 제한(최대 크기, TTL)

---

## 63. 흔한 장애 3: CPU throttling(제한이 만드는 숨은 지연)

### 증상
- CPU 사용률은 낮게 보이는데 지연이 증가
- 특히 p99가 튀는 현상

원인:
- CPU limit이 낮아서 커널이 스케줄링을 제한(throttle)
- “CPU 100%”가 아니라 “CPU를 못 씀”이 문제

관측 포인트:
- throttling 관련 메트릭(환경마다 제공 방식 다름)
- pod request/limit 설정

대응:
- CPU limit 상향/제거(정책에 따라)
- 워크로드 특성에 맞게 request/limit 튜닝
- 스케일 아웃(HPA)

---

## 64. 흔한 장애 4: readiness 실패로 트래픽이 사라진다

### 증상
- 에러율보다 “트래픽 급감”이 먼저 보일 수 있음
- 라우터/게이트웨이가 트래픽을 빼버림

원인:
- readiness 체크가 너무 엄격(외부 의존성까지 포함)
- readiness endpoint가 느림/타임아웃
- 앱 내부 상태 관리 버그

권장:
- readiness는 “이 인스턴스가 트래픽을 받아도 되는지”를 정확히 표현
- 외부 의존성까지 포함할지 팀 정책 필요(정답 없음)
  - 포함하면: 의존성 장애 시 자동으로 트래픽 차단(안전하지만 과민할 수 있음)
  - 제외하면: 서비스는 트래픽을 받지만 내부에서 5xx가 늘 수 있음(관측 필요)

---

# Part 16. 의존성(Dependency) 장애를 다루는 관측/운영 패턴

대부분의 실서비스는 DB/캐시/외부 API/메시지 큐에 의존합니다.  
장애의 상당수는 “우리 코드”가 아니라 “의존성”에서 옵니다.

## 65. 의존성 관측의 핵심: 4가지 수치만 잡아도 된다

의존성별로 아래 4개는 반드시 봐야 합니다.

- 호출량(rate)
- 성공/실패(error)
- 지연(latency)
- 타임아웃/재시도(retry/timeout)

그리고 서비스 지표(RED)와 함께 보면:
- “우리 서비스가 느린 게 아니라 결제사가 느리다”
- “재시도 때문에 DB가 죽고 있다”
같은 결론이 빨리 나옵니다.

---

## 66. 타임아웃은 관측 가능한 값이어야 한다

나쁜 상황:
- 타임아웃이 코드 여기저기 흩어져 있고 값이 제각각
- 타임아웃이 없거나 너무 김(수십 초)

좋은 상황:
- 의존성별 timeout 값이 한 곳에서 관리
- 타임아웃 발생 횟수가 메트릭으로 있음
- 타임아웃이 늘면 알람/대시보드로 확인 가능

---

## 67. 재시도는 “성공률”을 올리지만 “포화”를 만든다

재시도는 성공률을 올릴 수 있지만,
의존성이 느릴 때 재시도가 몰리면 더 느려지고 더 실패합니다(양의 피드백).

관측해야 할 것:
- retry_count
- overall attempts(원 요청 대비 몇 배 호출이 나갔는지)
- 대기열/스레드/커넥션 포화

운영 대응:
- 재시도 제한(최대 2회 등)
- 백오프 + jitter
- 서킷 브레이커로 “의존성 회복 시간”을 벌기
- 레이트 리밋/부하차단(load shedding)

---

## 68. 서킷 브레이커는 “관측 가능한 스위치”여야 한다

서킷이 열리고 닫히는 것을 모르면,
장애 때 “왜 갑자기 빠르게 실패하지?”만 남습니다.

필수 관측:
- circuit_open_total
- circuit_state(open/half-open/closed)
- fallback_used_total

---

# Part 17. DB/캐시 관측 심화: 백엔드가 제일 자주 막히는 곳

## 69. DB 관측: 최소 세 가지(슬로우/락/커넥션)

DB에서 흔히 보는 3대 병목:
- 슬로우 쿼리(실행이 느림)
- 락 대기(기다림이 느림)
- 커넥션 풀 고갈(대기열)

### 69.1 슬로우 쿼리 관측(원칙)
- 쿼리 지연 히스토그램(가능하면)
- 상위 쿼리 TOP(누가 시간을 쓰는지)

### 69.2 락 대기 관측(원칙)
- lock wait time
- blocked vs blocking 트랜잭션 확인(앞의 DB 문서 참고 가능)

### 69.3 커넥션 풀 관측(원칙)
- active/idle/pending
- pool wait time

---

## 70. 캐시 관측: hit rate 하나로 끝나지 않는다

캐시에서 자주 터지는 문제:
- hit rate 급락 -> DB 부하 폭발
- latency 증가 -> 전체 지연 증가
- eviction 폭증 -> 캐시 크기 부족
- stampede(thundering herd): 캐시 만료 순간에 동시에 DB로 몰림

관측 포인트:
- hit/miss
- latency
- evictions
- keyspace size(가능하면)

완화 패턴:
- TTL jitter(만료 분산)
- singleflight(같은 키 동시 요청 합치기)
- stale-while-revalidate(만료 후에도 잠깐 오래된 값 제공)

---

# Part 18. 비용/거버넌스: 관측성은 “무제한 저장”이 아니다

관측은 비용입니다. 비용을 통제하지 못하면 결국 꺼집니다.

## 71. 로그 예산(Log budget) 개념

- 서비스별 로그 용량/일을 정합니다.
- 정상 요청 로그는 샘플링, 에러/느린 요청은 보존
- 리텐션(보관 기간)을 데이터의 가치와 맞춥니다.

예(감각):
- 상세 로그: 7~14일
- 요약 로그/메트릭: 30~90일
- 감사/보안 로그: 규정에 따라 더 길게

---

## 72. 메트릭 카디널리티 예산

메트릭 폭발은 “나중에 고치기 어렵습니다.”  
처음부터 규칙이 필요합니다.

- label에 고유값 금지
- route는 템플릿만
- dependency name은 제한된 목록

---

## 73. 트레이스 예산(샘플링 정책)

- 기본 샘플링률(예: 1%)
- 에러/느린 요청 100%
- 리텐션(예: 3~7일)

운영 팁:
- “모든 요청 트레이싱”은 대부분 비용/성능상 불가능
- 중요한 건 “문제 요청을 잡아내는 정책”입니다

---

# Appendix I. 용어집(Glossary)

- Observability(관측성): 내부 상태를 외부 출력(로그/메트릭/트레이스)으로 추론할 수 있는 능력  
- Monitoring(모니터링): 미리 정의한 지표/임계치를 감시하는 활동  
- SLI/SLO: 서비스 품질 지표/목표  
- Error Budget: SLO를 달성하면서 허용되는 실패량  
- RED/USE/Golden signals: 지표를 보는 대표 프레임  
- Correlation ID / Trace ID: 요청 흐름을 연결하는 식별자  
- Tail latency(p99): 느린 꼬리 지연. 사용자 경험을 좌우  
- Circuit breaker: 의존성이 망가졌을 때 빠르게 실패/폴백하여 연쇄 장애를 막는 장치  
- Load shedding: 서비스가 죽기 전에 일부 요청을 의도적으로 거절하여 전체를 보호하는 전략  


---

# Part 19. 언어/프레임워크별 “미니 레시피”(Spring / Node / Python)

아래는 “관측성 필드/지표/트레이스”를 실제 코드에 넣을 때 자주 쓰는 형태를 **개념적으로** 보여줍니다.  
(정확한 라이브러리/버전/설정은 팀 환경에 따라 다를 수 있지만, 구조는 거의 같습니다.)

> 원칙: **프레임워크 자동 인스트루먼트(오토)**를 최대한 활용하고,  
> 도메인 이벤트(주문 상태 전이, 결제 승인, 재고 차감)는 **수동 스팬/로그**로 보강합니다.

## 74. Java/Spring 계열: “Micrometer + OTel + MDC”의 조합

### 74.1 해야 하는 일(목표)
- 요청마다 trace_id를 만들고(또는 전달받고)
- 로그에 trace_id를 자동으로 넣고
- HTTP 지표(RPS/에러/지연)를 자동 수집하고
- DB/HTTP client span을 자동 수집

### 74.2 로그에 trace_id 넣기(MDC)
- 많은 트레이싱 구현은 MDC에 trace_id/span_id를 넣을 수 있습니다.
- Logback 패턴에서 이를 출력합니다(개념 예시):

```text
%date %-5level [%X{{trace_id}} %X{{span_id}}] %logger - %msg%n
```

이렇게 하면 구조화 로그가 아니더라도 연결이 가능해집니다.  
(가능하면 JSON 로그 + trace_id 필드 방식이 더 좋습니다.)

### 74.3 도메인 스팬(수동) 예시(의사코드)
```java
span = tracer.spanBuilder("Order.Pay").setAttribute("order.id", orderId).startSpan();
try (Scope scope = span.makeCurrent()) {{
  // 결제 확정 트랜잭션
  // ...
  span.setStatus(OK);
}} catch (Exception e) {{
  span.recordException(e);
  span.setStatus(ERROR);
  throw e;
}} finally {{
  span.end();
}}
```

핵심:
- “기능 단위”로 스팬을 하나 잡으면,
  장애 때 트레이스에서 바로 그 기능을 기준으로 볼 수 있습니다.

---

## 75. Node.js(Express/Nest) 계열: “pino(JSON) + middleware + OTel”

### 75.1 구조화 로그(pino)에서 필드 표준화
예(개념):

```js
logger.info({{
  trace_id,
  request_id,
  route,
  method,
  status,
  duration_ms
}}, "request completed");
```

### 75.2 Express middleware로 요청 요약/에러 기록
핵심은:
- 요청 시작 시각 기록
- 응답 종료 시 duration 계산
- trace_id를 컨텍스트에서 꺼내 로그/메트릭에 포함

### 75.3 “이벤트 루프 지연” 지표(Node 특화)
Node에서 p99 지연이 튈 때 원인 중 하나가 event loop lag입니다.
- CPU가 꽉 차거나
- 동기 블로킹 작업이 있거나
- GC가 길어질 때
event loop가 늦어질 수 있습니다.

관측 포인트:
- event loop lag(p95/p99)
- heap 사용량
- 외부 호출 대기

---

## 76. Python(FastAPI/Django) 계열: “structlog + OTel instrumentation”

### 76.1 구조화 로그(structlog) 기본 형태(개념)
```python
log.info("request completed",
         trace_id=trace_id,
         request_id=request_id,
         route=route,
         status=status,
         duration_ms=duration_ms)
```

### 76.2 FastAPI에서 middleware로 표준 로그 찍기
- request.state에 start time 저장
- response 시 duration 계산
- trace_id를 OTel context에서 가져오기(가능한 경우)

---

# Part 20. 배포/릴리즈 관측: “배포가 원인인지”를 2분 안에 판단하는 체계

장애 원인의 상당수는 배포입니다.  
그래서 배포 관측이 잘 되어 있으면, 장애 대응이 빨라집니다.

## 77. 릴리즈 마커(Release marker)를 대시보드에 넣기

- 배포 시각을 그래프에 표시(annotations)
- 버전/커밋/이미지 태그를 메트릭 라벨로 포함
- 버전별 에러율/지연 비교

실무 패턴:
- “배포 직후 에러율↑, 지연↑”이면 롤백이 빠른 완화

---

## 78. 카나리/블루그린에서의 관측 포인트

카나리(일부 트래픽만 새 버전)에서 성공하려면:
- 새 버전 vs 구 버전 지표를 비교할 수 있어야 합니다.

필수 비교:
- 에러율(특히 5xx)
- p95/p99 지연
- 특정 도메인 에러(결제/주문 실패율)
- 리소스(CPU/메모리)

“카나리 자동 중단”은 이상적인 목표지만,
주니어 단계에서는 수동 관측이라도 일관된 체크리스트가 중요합니다.

---

## 79. 배포 실패의 흔한 원인과 트리아지

### 79.1 Config/Secret 누락
- CrashLoopBackOff
- 로그에 “필수 환경 변수 없음”

대응:
- readiness 전에 설정 검증(빠르게 fail)
- 배포 파이프라인에서 config lint

### 79.2 마이그레이션 순서 오류
- 새 코드가 새 컬럼을 읽는데 DB에 없음
- 또는 DB 제약 추가로 기존 코드가 실패

대응:
- Expand -> Migrate -> Contract 플레이북(DB 문서 참고)
- feature flag로 단계적 전환

---

# Part 21. “장애 때 실제로 하는” 디버깅 도구상자

## 80. 확인 우선순위: 원칙은 “기다림 vs 실행” 구분

느리면:
- 실행이 느린가? (슬로우 쿼리, CPU)
- 기다림이 느린가? (락, 풀, 큐)

이걸 구분하는 순간, 해결 방향이 잡힙니다.

---

## 81. 자주 쓰는 ‘정리 질문’ 12개

장애 대응 중, 본인/팀에게 다음 질문을 던져보세요.

1) 사용자 영향은 무엇인가? (에러율/지연/영향 기능)  
2) 언제 시작했나? (절대 시각)  
3) 무엇이 바뀌었나? (배포/설정/트래픽/외부 장애)  
4) 전체인가, 일부인가? (특정 route/테넌트/리전)  
5) 에러가 늘었나, 느려졌나, 둘 다인가?  
6) 트래픽은 늘었나/줄었나?  
7) 의존성(DB/캐시/외부)의 지표는?  
8) 자원(CPU/메모리/큐/커넥션)은 포화인가?  
9) 로그의 top 에러 타입은?  
10) 트레이스에서 가장 긴 span은?  
11) 즉시 완화(롤백/플래그/레이트리밋) 옵션은?  
12) 다음 업데이트는 언제/무엇을 공유할까?

이 질문들이 ‘루틴’이 되면, 장애 대응 속도가 올라갑니다.

---

# Part 22. 예시로 보는 “좋은 회고” (샘플)

아래는 가상의 예시입니다. 형태를 참고하세요.

## 82. Postmortem 예시(요약형)

- 사건: 주문 생성 API p99 지연 급증(2026-02-25)
- 영향:
  - p99: 4.2s까지 증가(평소 0.8s)
  - 5xx: 0.2% -> 1.5%
  - 기간: 14:12~14:40 (28분)
  - 영향 기능: 주문 생성/결제 시작
- 탐지:
  - SLO burn rate 알람(14:13)
- 원인:
  - 배포 v1.2.3에서 주문 생성 시 추가된 쿼리의 인덱스 누락
  - 쿼리 실행 시간이 길어져 커넥션 풀 대기 증가
  - 재시도 정책이 즉시 재시도여서 DB 부하를 더 키움
- 완화:
  - 14:18 롤백 v1.2.2
  - 14:25 에러율 감소 확인
  - 14:40 정상화 확인
- 잘 된 점:
  - 알람이 빨랐고(1분 내), 롤백 결정이 빨랐다
  - 트레이스로 병목 쿼리를 빠르게 특정
- 아쉬운 점:
  - 배포 전 EXPLAIN/인덱스 체크가 없었다
  - 재시도 정책이 백오프 없이 즉시 재시도였다
- 재발 방지 액션:
  - (오너 A, 3/5) 배포 전 쿼리 플랜 체크리스트 추가
  - (오너 B, 3/10) 주문 생성 쿼리 인덱스 추가 + 모니터링 대시보드 보강
  - (오너 C, 3/12) 재시도 정책에 지수 백오프+jitter 적용, retry_count 메트릭 추가

---

# Appendix J. 운영 성숙도 셀프 체크(10분)

각 항목에 “예/아니오”로 체크해보세요.

- [ ] 서비스 RED 대시보드가 있다  
- [ ] 알람은 증상 기반(SLO/에러율/지연) 중심이다  
- [ ] 알람마다 런북이 있다  
- [ ] 로그는 구조화(JSON)이고 trace_id로 연결된다  
- [ ] 트레이싱으로 DB/외부 호출 병목을 볼 수 있다  
- [ ] 배포 마커/버전 비교가 가능하다  
- [ ] 커넥션 풀/락 대기/큐 lag를 관측한다  
- [ ] 타임아웃/재시도/서킷이 관측 가능한 지표로 남는다  
- [ ] 인시던트 채널/타임라인 기록 습관이 있다  
- [ ] 회고가 액션으로 이어지고 추적된다

5개 미만이면:
- “일단 보이게 만드는 것”부터 시작해야 합니다.

7개 이상이면:
- 이미 운영 레벨이 꽤 높은 편입니다.


---

# Part 23. 로그 심화: “운영 로그”의 표준 패턴과 파이프라인

## 83. 로그 파이프라인(수집 → 가공 → 저장 → 검색) 감각

프로덕션에서 로그는 보통 이런 흐름을 거칩니다.

1) 애플리케이션 stdout/stderr 또는 파일
2) 로그 수집기(Fluent Bit, Fluentd, Vector 등)
3) 중앙 저장소(Elasticsearch, Loki, Cloud Logging, Datadog Logs 등)
4) 검색/대시보드/알람

### 83.1 앱에서 지켜야 할 최소 원칙
- 로그는 stdout으로(컨테이너 표준)
- 한 줄(JSON)로(멀티라인은 파이프라인에서 다루기 어렵고 비싸짐)
- trace_id/route/status/duration 같은 공통 필드를 포함

---

## 84. 멀티라인 스택트레이스 문제(왜 골칫거리인가)

스택트레이스는 보통 여러 줄입니다.
- 수집기/파서가 줄 단위로 쪼개면
  한 예외가 수십 개 이벤트로 분리될 수 있습니다.

권장:
- 가능하면 “스택트레이스를 하나의 문자열 필드”로 넣는 방식(JSON)
- 또는 수집기에서 멀티라인 조합 규칙을 설정(운영 난이도 ↑)

---

## 85. PII/비밀 마스킹(레드액션) 실전 팁

### 85.1 ‘필드 화이트리스트’ 전략(권장)
- 허용 필드 목록을 정하고, 그 외는 기록하지 않습니다.
- 특히 요청 바디/헤더 전체 기록은 금지.

### 85.2 대표 마스킹 예시
- email: `a***@domain.com`
- phone: `010-****-1234`
- token: 앞 4글자만 남기고 마스킹

### 85.3 “나중에 디버깅을 위해”라는 이유로 남기지 말 것
보안 사고가 나면:
- 로그가 가장 위험한 데이터 저장소가 됩니다.

---

## 86. 운영 로그 이벤트 카탈로그(권장 12종)

운영에 도움이 되는 “이벤트 타입”을 정해두면,
로그를 집계/검색/알람하기 쉬워집니다.

- request_summary
- exception
- dependency_call_failed
- db_slow_query
- db_deadlock_or_retryable_error
- circuit_opened
- rate_limited
- cache_miss_spike(샘플)
- queue_lag_detected(샘플)
- config_loaded
- feature_flag_changed(가능하면)
- deploy_marker(가능하면)

실무 팁:
- 이벤트 타입은 `event` 필드로 남기고,
- 관련 필드들을 표준화하면 검색이 매우 쉬워집니다.

---

# Part 24. 메트릭 심화: 히스토그램/버킷/카디널리티의 함정

## 87. 히스토그램 버킷 설계: 너무 적어도, 너무 많아도 문제

지연시간 히스토그램은 버킷으로 구성됩니다.
- 버킷이 너무 적으면 p95/p99가 부정확
- 버킷이 너무 많으면 시계열 증가(비용)

현실적 가이드(요청 지연):
- 5ms, 10ms, 25ms, 50ms, 100ms, 250ms, 500ms, 1s, 2.5s, 5s, 10s
(서비스 특성에 맞게 조정)

---

## 88. “평균은 거짓말”을 넘어: tail latency의 구조적 원인

p99가 튀는 구조적 원인(자주):
- 큐잉(대기열): 풀 고갈(스레드/커넥션), 이벤트루프 지연
- 락: DB 락/뮤텍스/분산락
- IO: 디스크/네트워크/외부 의존성
- GC/메모리: 스톱더월드/페이지 폴트

관측 포인트:
- 대기 시간(풀 pending, lock wait)
- 외부 호출 latency
- GC pause time
- 큐 길이/lag

---

## 89. Recording rules: “대시보드 쿼리”를 미리 계산해두는 이유

대시보드가 느리면 운영이 느려집니다.
Prometheus에서는 자주 쓰는 계산을 recording rule로 저장해:
- 쿼리 비용 감소
- 알람/대시보드 일관성 확보
를 할 수 있습니다.

예(개념):
- `http_error_rate_5m`
- `http_p95_latency_5m`

---

## 90. 카디널리티 폭발 디버깅(실무에서 한 번은 꼭 겪음)

징후:
- Prometheus 메모리 증가
- 쿼리 느려짐
- 저장소 비용 폭발

원인:
- label에 고유값
- path full
- query string
- user agent 전체

대응:
- 라벨 제한
- route 템플릿화
- 필요한 경우 로그/트레이스로 대체

---

# Part 25. 트레이싱 심화: “span 이름/속성/에러 처리”의 표준

## 91. span 이름 규칙(가독성이 운영을 살린다)

좋은 span 이름:
- `HTTP GET /v1/orders/{id}`
- `DB SELECT orders`
- `Redis GET cart:{user}` (주의: 고유키 전체는 피하거나 마스킹)
- `PaymentProvider POST /charge`

나쁜 span 이름:
- `doWork`
- `serviceMethod`
- `query`

장애 때는 “읽는 속도”가 생존입니다.

---

## 92. span 속성(Attributes) 최소 표준

권장:
- http.method, http.route, http.status_code
- db.system, db.operation, db.name(가능하면)
- peer.service(dependency name)
- error.type, error.message(에러 시)

주의:
- 개인정보/토큰/쿼리 전문을 속성으로 넣지 마세요.
- 비용이 커지고 보안 문제가 됩니다.

---

## 93. 비동기 흐름(큐/이벤트) 트레이싱

HTTP 요청은 시작과 끝이 분명하지만,
큐/이벤트는 “시간이 분리”됩니다.

권장 패턴:
- produce span(메시지 발행)
- consume span(메시지 처리)
- 메시지 헤더에 trace context 전파

이렇게 하면:
- “이 이벤트가 어떤 요청에서 시작됐는지”
- “어느 소비자가 느린지”
를 볼 수 있습니다.

---

# Part 26. 알람 심화: Severity, Routing, Suppression(억제)

## 94. 알람 등급(Severity) 정의가 있어야 한다

알람을 모두 “긴급”으로 만들면 결국 무시됩니다.

현실적인 등급 예:
- SEV-1: 결제/로그인 등 핵심 기능 전면 장애(즉시 페이지)
- SEV-2: 사용자 영향 큼(지연/에러 급증, 즉시 대응)
- SEV-3: 부분 영향/단기 대응 가능(업무시간 티켓)
- SEV-4: 정보성(페이지 금지)

---

## 95. 알람 라우팅: “누가” 받는가

- 서비스 오너 팀
- 플랫폼/DB 오너(공유 컴포넌트)
- 외부 의존성 담당자(결제/인증)

알람 메시지에 반드시 포함:
- 서비스/환경/클러스터
- 최근 배포 여부
- 대시보드/런북 링크

---

## 96. 억제(Suppression)와 유지보수 창(Maintenance window)

대규모 마이그레이션/배포 중에는
알람을 “완전히 끄는 것”이 아니라, 적절히 억제해야 합니다.

원칙:
- 억제는 시간/범위를 제한
- 사후에 반드시 원복
- 억제 중에도 “진짜 장애”는 감지 가능한 알람을 남김(예: 전체 다운)

---

# Part 27. 추가 케이스 스터디(현업에서 매우 자주 만나는 것들)

## 97. 케이스 6: DNS 장애(CoreDNS)로 외부 호출이 전부 실패

### 증상
- 외부 API/DB 연결이 갑자기 모두 timeout
- 서비스 내부는 멀쩡한데 의존성이 전부 죽음
- 여러 서비스에서 동시에 발생(동시다발)

### 트리아지
- 특정 노드/클러스터에서만?
- DNS resolve 실패 로그(예: ENOTFOUND)
- CoreDNS 지표/로그 확인(플랫폼 팀 협업)

### 완화
- DNS 캐시/리졸버 설정(가능하면)
- 트래픽 우회(다른 클러스터/리전)
- 외부 의존성에 IP 고정 같은 임시 처방은 매우 위험(최후)

---

## 98. 케이스 7: 캐시 스탬피드로 DB가 죽는다

### 증상
- cache miss 급증
- DB QPS/지연 급증
- p99 상승, 에러율 증가

### 원인
- 인기 키 TTL이 동시에 만료
- 모든 요청이 동시에 DB로 몰림

### 완화
- TTL jitter로 만료 분산
- singleflight로 같은 키 동시 요청 합치기
- stale-while-revalidate로 만료 후에도 임시 값 제공

관측:
- hit rate, miss rate, DB QPS, DB latency, key별 핫스팟(가능하면 로그)

---

## 99. 케이스 8: 재시도 폭주(retry storm)로 의존성이 더 죽는다

### 증상
- 외부 API가 느려짐 → 타임아웃 → 재시도 → 호출량 폭증
- 결국 외부도, 내부도 다 망가짐

### 해결
- 재시도에 백오프+jitter
- 최대 재시도 횟수 제한
- 서킷 브레이커로 빠른 실패/폴백
- 레이트 리밋으로 폭주 억제

관측:
- attempts per request(원 요청 대비 호출 배수)
- retry_count
- circuit_open_count

---

# Appendix K. MTTD/MTTR: 운영 성과 지표(개념)

- MTTD(Mean Time To Detect): 평균 탐지 시간  
- MTTR(Mean Time To Recover): 평균 복구 시간

주니어가 할 수 있는 현실적 개선:
- 알람/대시보드로 탐지 시간을 줄인다(MTTD↓)
- 런북/롤백/플래그로 복구 시간을 줄인다(MTTR↓)

이 두 지표는 “운영 실력”이 수치로 보이는 대표 지표입니다.


---

# Part 28. 런북 모음(실무에서 바로 복붙해서 쓰는 수준)

이 파트는 “장애 때 실제로 필요한 명령/쿼리”를 조금 더 구체적으로 제공합니다.  
(특히 DB/큐/쿠버 쪽은 실제로 손이 가는 부분이라, 템플릿이 있으면 큰 도움이 됩니다.)

> 주의: 아래 쿼리/명령은 **운영 환경에 맞게** 조정해야 합니다.  
> 특히 `KILL`, `terminate`, 대량 UPDATE/DELETE 같은 조치는 매우 위험합니다.

## 100. 런북: PostgreSQL 락 대기(Blocking) 폭발

### 100.1 증상
- p99 지연 급증
- DB 쿼리는 “실행이 느린 게 아니라 기다림이 길다”
- 커넥션 풀이 점점 고갈

### 100.2 5분 트리아지(추천 순서)
1) 현재 실행 중 쿼리 TOP(오래 실행된 순)
```sql
SELECT pid, state, wait_event_type, wait_event,
       now() - query_start AS running_for,
       left(query, 200) AS query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY running_for DESC
LIMIT 20;
```

2) idle in transaction 찾기(가장 위험한 패턴)
```sql
SELECT pid, state, now() - xact_start AS xact_age,
       left(query, 200) AS query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
ORDER BY xact_age DESC;
```

3) 누가 누구를 막는지(blocked vs blocking)
```sql
SELECT
  blocked.pid  AS blocked_pid,
  now() - blocked.query_start AS blocked_for,
  left(blocked.query, 120) AS blocked_query,
  blocking.pid AS blocking_pid,
  now() - blocking.query_start AS blocking_for,
  left(blocking.query, 120) AS blocking_query
FROM pg_catalog.pg_locks blocked_l
JOIN pg_catalog.pg_stat_activity blocked ON blocked.pid = blocked_l.pid
JOIN pg_catalog.pg_locks blocking_l
  ON blocking_l.locktype = blocked_l.locktype
 AND blocking_l.database IS NOT DISTINCT FROM blocked_l.database
 AND blocking_l.relation IS NOT DISTINCT FROM blocked_l.relation
 AND blocking_l.page IS NOT DISTINCT FROM blocked_l.page
 AND blocking_l.tuple IS NOT DISTINCT FROM blocked_l.tuple
 AND blocking_l.transactionid IS NOT DISTINCT FROM blocked_l.transactionid
JOIN pg_catalog.pg_stat_activity blocking ON blocking.pid = blocking_l.pid
WHERE NOT blocked_l.granted AND blocking_l.granted;
```

### 100.3 즉시 완화 옵션(위험도 순)
- (가장 안전) 원인 트랜잭션이 무엇인지 확인하고 앱/배치 중단
- (상황에 따라) 배포 롤백
- (최후) 특정 세션 종료(terminate)  
  - 종료 전 반드시 영향(데이터 정합성/업무 영향)을 확인하세요.

### 100.4 재발 방지
- 트랜잭션 범위 축소
- 인덱스 추가로 UPDATE/SELECT 범위 축소
- 배치 chunking
- lock_timeout/statement_timeout 설정
- idle in transaction 방지(타임아웃)

---

## 101. 런북: PostgreSQL 슬로우 쿼리(실행이 진짜 느림)

### 101.1 증상
- DB CPU/IO 상승
- 특정 쿼리가 오랫동안 active
- 락 대기보다 “실행 시간이 길다”

### 101.2 트리아지
1) 슬로우 쿼리 로그/Top query 확인(환경에 따라)
2) 해당 쿼리 `EXPLAIN (ANALYZE, BUFFERS)`로 확인(스테이징/리플리카에서 권장)
3) 흔한 원인:
   - 인덱스 없음 → Seq Scan
   - 반환량 과다(select * / 필터 약함)
   - 정렬/그룹바이 spill
   - 통계 오래됨(ANALYZE 필요)

### 101.3 즉시 완화
- 문제 쿼리 트래픽 제한/캐싱/디그레이드
- 인덱스 추가(단, 운영에서 즉시 추가는 신중히)

---

## 102. 런북: DB 커넥션 풀 고갈(앱 레벨)

### 102.1 증상
- 앱 로그: “pool timeout”, “acquire timeout”
- DB는 CPU 여유인데 앱이 느림(대기열)

### 102.2 트리아지
- 풀 지표 확인:
  - active, idle, pending
  - wait_time(p95/p99)
- DB 쿼리 지연/락 대기와 동반인지 확인
- 커넥션 누수 의심:
  - 특정 요청에서 커넥션 반환 안 함
  - 트랜잭션 종료 누락

### 102.3 완화
- 트래픽 제한/디그레이드
- 풀 크기를 무작정 키우기 전에 “왜 오래 잡히는지”를 찾기
- statement_timeout/lock_timeout 도입

---

## 103. 런북: Kafka/큐 consumer lag 증가

### 103.1 증상
- lag 상승
- 처리 지연으로 사용자 기능이 늦게 반영(알림, 정산 등)

### 103.2 트리아지
- consumer 에러율/재시도 폭주?
- 처리 시간 증가?
- 다운스트림(DB) 병목?
- 파티션 스큐(특정 키 핫스팟)?

### 103.3 완화
- consumer 스케일 아웃(파티션 수/컨슈머 그룹 고려)
- 실패 메시지 DLQ로 격리(가능하면)
- 처리 로직 디그레이드(비필수 작업 생략)
- DB 병목이면 DB 완화가 먼저(인덱스/쿼리/락)

---

## 104. 런북: 502/503/504(게이트웨이/인그레스) 에러

### 104.1 해석(대략)
- 502: 백엔드 응답 형식 문제/연결 문제
- 503: 백엔드 없음/과부하/리드니스 실패로 라우팅 불가
- 504: 업스트림 타임아웃(백엔드가 너무 느림)

### 104.2 트리아지
- 인그레스/프록시 로그 확인
- 백엔드 pod readiness 상태
- 백엔드 지연(p95/p99)와 상관관계
- 타임아웃 설정(게이트웨이 < 앱 < 의존성 순으로 정렬 필요)

---

## 105. 런북: OOM/메모리 폭증

### 트리아지
- OOMKilled인지(쿠버 이벤트) vs GC 폭주인지(언어별)
- 특정 route/특정 payload에서만 발생하는지(로그/트레이스)
- 최근 배포/기능 플래그 변경 여부

### 완화
- 일시적: 메모리 limit 상향, 스케일 아웃
- 근본: payload 제한, 스트리밍, 캐시 상한/TTL, 누수 수정

---

# Appendix L. 커넥션 풀 크기 감각(초간단)

자주 있는 실수:
- “성능이 느리다” → 풀 크기를 무조건 키움 → DB max_connections 초과 → 더 느려짐

감각적인 접근:
- 앱 인스턴스 수 × 풀 크기 = DB가 받아야 하는 총 커넥션 수
- DB는 커넥션이 매우 비쌈(메모리/컨텍스트)
- 풀 크기를 키우기 전에:
  - 쿼리 시간을 줄이고
  - 타임아웃을 넣고
  - 병목을 제거하는 게 먼저

---

# Appendix M. 실전 운영 레벨업 체크리스트(주니어용)

- [ ] “느리다”를 듣자마자 RED 대시보드를 열 수 있다  
- [ ] trace 하나로 병목 span을 찾을 수 있다  
- [ ] 로그에서 top error_type을 1분 안에 집계할 수 있다  
- [ ] 알람을 1개 추가하면 런북도 같이 만든다  
- [ ] 배포와 장애의 상관관계를 버전 라벨로 확인할 수 있다  
- [ ] DB 락/커넥션/슬로우 쿼리 중 무엇인지 구분할 수 있다  
- [ ] 회고에서 “재발 방지 액션”을 기술/프로세스 둘 다 제안할 수 있다  

이 체크리스트가 절반 이상 “예”면,
운영/장애 대응은 주니어 중에서도 확실히 상위권입니다.


---

# Part 29. 비즈니스 관측(“기술 지표”만 보면 놓치는 것들)

운영에서 진짜 무서운 장애는:
- 서버는 200을 잘 주는데
- 사용자는 결제를 못 하거나
- 주문이 누락되거나
- 데이터가 틀어지는
**비즈니스 장애**입니다.

즉, 기술 지표(RED) + 비즈니스 지표가 함께 있어야 합니다.

## 106. 비즈니스 SLI 예시(주문/결제 도메인)

- 주문 생성 성공률(주문이 실제로 생성되었는가)
- 결제 캡처 성공률(결제가 확정되었는가)
- 결제-주문 정합성(주문 PAID인데 결제 CAPTURED가 없는 케이스 비율)
- 재고 음수 발생 건수(0이면 이상적)
- 환불/취소 성공률

이 지표들은 “HTTP 200”만으로는 알 수 없습니다.  
도메인 이벤트(상태 전이) 기반 메트릭/로그가 필요합니다.

---

## 107. 도메인 이벤트 메트릭 설계(간단하지만 강력)

예: 주문 상태 전이

- `orders_state_transition_total{from="CREATED",to="PAID"}`
- `orders_state_transition_total{from="PAID",to="CANCELED"}`

예: 결제 결과

- `payment_capture_total{result="success"}`
- `payment_capture_total{result="failed", reason="timeout"}`

이런 지표가 있으면:
- “결제 성공률이 떨어졌다”를 기술 알람보다 먼저 잡을 수 있고,
- 어디서 실패하는지(reason)까지 드러납니다.

---

## 108. 데이터 품질(Data Quality) 모니터링의 감각

데이터 품질 이슈는 운영에서 자주 “늦게” 발견됩니다.
예:
- 특정 배치가 잘못 돌아가서 집계가 틀림
- 이벤트 소비가 누락되어 상태가 불일치

최소한의 데이터 품질 체크(현실 버전):
- 일일/시간별 카운트가 급변하면 알람
- NULL/음수/비정상 값 비율 모니터링
- 핵심 테이블의 FK 관계 깨짐(가능하면)

이건 데이터팀/플랫폼팀과 협업 포인트이기도 합니다.

---

# Part 30. 성능 이슈 대응: 관측성 + 프로파일링(Perf)

관측성만으로 원인이 충분히 좁혀지지 않을 때가 있습니다.
- “이 endpoint 내부에서 CPU를 어디가 쓰는지”
- “왜 메모리가 계속 늘어나는지”
같은 질문은 프로파일링이 필요할 수 있습니다.

## 109. 성능 디버깅 3단계(현실)

1) 관측성(메트릭/트레이스)로 문제 범위를 좁힌다  
   - 어떤 route?
   - 어떤 span?
   - 어떤 의존성?
2) 코드 레벨 원인을 가정한다  
   - N+1?
   - JSON 파싱 폭탄?
   - 정렬/압축/암호화 비용?
3) 프로파일링/샘플링으로 확인한다  
   - CPU profile
   - heap profile
   - flame graph

주니어가 할 수 있는 범위:
- 먼저 1)로 범위를 좁히는 능력만 갖춰도 팀에서 가치가 큽니다.

---

# Part 31. Synthetic Monitoring / Uptime Check(합성 모니터링)

“사용자가 알려주기 전에” 감지하려면,
외부에서 주기적으로 핵심 플로우를 호출하는 합성 모니터링이 도움이 됩니다.

## 110. 무엇을 체크할까
- /health(단순)보다 **핵심 사용자 여정**이 더 중요합니다.
  - 로그인 → 주문 조회
  - 결제 시작(샌드박스/모킹)
  - 검색 결과가 빈 값이 아닌지

주의:
- 결제 같은 민감 기능은 샌드박스/테스트 계정/모킹이 필요합니다.

---

# Part 32. 운영 문화: 주니어가 “운영 담당”처럼 보이는 포인트

## 111. 장애 대응에서 신뢰를 주는 행동 8가지

1) “증상(RED)”을 먼저 말한다(지연/에러/트래픽)  
2) 영향 범위를 말한다(특정 route/버전/리전)  
3) 가설을 1개만 세우고 검증한다(난사 금지)  
4) 조치를 할 때는 “되돌릴 수 있게” 한다(롤백/플래그)  
5) 변경을 하면 타임라인에 기록한다  
6) 업데이트 시간을 약속하고 지킨다  
7) 복구 후 회고 액션을 제안한다(문서/알람/런북 개선)  
8) 재발 방지 PR을 실제로 낸다

운영 실력은 결국 “신뢰”로 보입니다.

---

# Appendix N. “나만의 운영 노트” 템플릿(강력 추천)

운영은 경험이 쌓여야 늡니다.  
그 경험을 쌓는 가장 빠른 방법은 “나만의 노트”를 만드는 겁니다.

아래 템플릿을 이슈/장애마다 채워보세요.

- 사건:
- 증상:
- 영향:
- 시작/종료 시각:
- 원인(추정 포함):
- 확인한 근거(대시보드/로그/트레이스):
- 즉시 완화:
- 근본 해결:
- 재발 방지 액션:
- 배운 점(1줄):

이 노트는 6개월 뒤 당신을 “운영 잘하는 사람”으로 만들어줍니다.

---

# 끝맺음(이 문서의 한 줄 요약)

> **관측성은 “장애를 빨리 끝내고, 다시 안 터지게 만드는 능력”이다.**

주니어 입장에서 가장 현실적인 성장 루트는:
- 로그/메트릭/트레이스를 표준화하고,
- SLO 기반 알람과 런북을 만들고,
- 장애 때 5분 트리아지로 방향을 잡고,
- 회고로 시스템을 개선하는 것

이 루틴을 한 번만 제대로 해도,
당신은 팀에서 “운영을 맡길 수 있는 백엔드”가 됩니다.
