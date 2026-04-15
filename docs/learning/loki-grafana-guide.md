# Loki and Grafana Guide

> 대상 독자: Grafana와 Loki를 이미 연결해 두었지만, **Loki를 실제로 어떻게 써야 하는지** 감이 부족한 백엔드/플랫폼 엔지니어  
> 문서 목표: Loki의 이론, 핵심 개념, Grafana 연동 방식, LogQL 사용법, 운영 포인트를 한 번에 정리

---

## 1. Loki를 한 문장으로 설명하면

Loki는 **로그 본문 전체를 인덱싱하지 않고, 로그 스트림을 구분하는 라벨(label)만 인덱싱하는 로그 저장소**다.  
그래서 Elasticsearch 계열처럼 로그 내용 전체를 색인하는 시스템과 비교하면, 저장 비용과 운영 복잡도를 줄이기 쉽다. 대신 Loki를 잘 쓰려면 **라벨 설계**와 **쿼리 습관**이 매우 중요하다.

공식 문서 기준으로 Loki는 다음 특징을 가진다.

- 수평 확장이 가능하다.
- 다중 테넌시를 지원한다.
- 로그 본문이 아니라 **라벨 집합**을 인덱싱한다.
- 로그 데이터는 압축된 **chunks**로 저장된다.
- object storage를 중심으로 저비용 저장 구조를 가진다.

이 구조 때문에 Loki는 “로그를 전부 색인해서 검색하는 엔진”이라기보다, **라벨로 범위를 먼저 좁히고, 그 범위 안에서 로그를 읽고 필터링하는 시스템**으로 이해하는 게 맞다.

---

## 2. Loki를 왜 쓰는가

### 2.1 Prometheus와 비슷한 철학

Loki는 Prometheus에서 영감을 받은 시스템이다. Prometheus가 metric을 라벨 기반으로 다루듯, Loki도 로그를 **label set** 기준으로 다룬다.  
즉 “어느 서비스에서 나왔는지”, “어느 namespace/pod/container인지”, “어느 환경(prod/staging)인지” 같은 **저카디널리티 메타데이터**를 기준으로 로그를 찾는다.

### 2.2 저장 비용 절감

로그 본문 전체를 인덱싱하지 않으므로 인덱스 크기가 상대적으로 작다. 공식 문서도 Loki의 작은 인덱스와 압축 chunk 구조가 운영 단순화와 비용 절감에 유리하다고 설명한다.

### 2.3 Grafana와의 결합이 좋다

Grafana에서 Loki는 단순 로그 뷰어가 아니다. 다음을 같이 할 수 있다.

- Explore에서 ad-hoc 로그 분석
- Dashboard 패널에 로그/로그 기반 메트릭 시각화
- Alerting 규칙 생성
- Trace 시스템(Jaeger, Tempo 등)과 링크 연결
- Metrics/Logs/Traces를 한 화면에서 연결

즉 Loki는 **Grafana 안에서 운영 관측의 로그 축**을 담당한다고 보면 된다.

---

## 3. Loki의 핵심 개념

### 3.1 Label

Loki에서 가장 중요한 개념이다.  
라벨은 `key=value` 형태의 메타데이터다.

예:

- `service=payment`
- `env=prod`
- `namespace=backend`
- `cluster=kr-prod-1`

Loki는 이 라벨 집합으로 로그를 **log stream** 단위로 묶는다.

핵심 원칙:

- **라벨은 저카디널리티 값**이어야 한다.
- 고유값이 너무 많은 값은 라벨로 두면 안 된다.
- 예: `trace_id`, `user_id`, `order_id`, `request_id`, `timestamp`, `uuid` 같은 값은 보통 라벨로 부적절하다.

왜냐하면 라벨 조합이 많아질수록 stream 수가 폭증하고, 이는 곧 메모리/인덱스/쿼리 비용 증가로 이어지기 때문이다.

### 3.2 Log Stream

같은 라벨 집합을 공유하는 로그들의 묶음이다.

예를 들어:

```logql
{service="payment", env="prod", namespace="backend"}
```

이 라벨 집합을 가진 로그는 하나의 stream으로 취급된다.

실무 감각으로는:

- stream이 너무 굵으면 쿼리 범위가 넓어진다.
- stream이 너무 많으면 cardinality 폭발이 일어난다.

그래서 적절한 라벨 설계가 Loki 성능의 핵심이다.

### 3.3 Index와 Chunk

Loki 저장 구조의 핵심은 이 둘이다.

- **Index**: 어떤 라벨 집합의 로그가 어디에 있는지 가리키는 목차
- **Chunk**: 실제 로그 라인이 압축되어 저장되는 단위

즉 Loki는 먼저 인덱스를 보고 대상 chunk를 찾고, 그다음 chunk를 열어서 로그를 읽는다.

이 구조 때문에 쿼리를 잘 쓰려면:

1. 라벨 셀렉터로 stream 범위를 충분히 좁히고
2. 그 다음 로그 본문 필터를 적용해야 한다.

### 3.4 Structured Metadata

Loki는 고카디널리티 값을 라벨로 넣지 말라고 권장한다. 그 대안이 **structured metadata**다.

이건 로그 라인에 붙는 메타데이터이지만, 라벨처럼 인덱싱되지는 않는다.  
즉 다음 같은 값에 잘 맞는다.

- `trace_id`
- `pod_name`
- `process_id`
- `thread_id`
- `transaction_id`

이 값들은 검색에 자주 쓰일 수 있지만 라벨로 두기엔 cardinality가 너무 높다. 그래서 Loki 최신 문서는 이런 정보는 structured metadata 쪽을 검토하라고 안내한다.

### 3.5 Tenant

Loki는 multi-tenant 시스템이다.  
즉 논리적으로 여러 사용자/팀/환경이 하나의 Loki를 공유할 수 있다. 실제 운영에서는 Grafana 데이터소스, gateway, 인증 계층과 함께 tenant 분리가 설계된다.

---

## 4. Loki 아키텍처를 이해하는 법

Loki를 정확히 쓰려면 최소한 **쓰기 경로(write path)** 와 **읽기 경로(read path)** 를 이해해야 한다.

### 4.1 쓰기 경로

대략 이런 흐름이다.

1. 로그 수집기(Alloy, OTel Collector 등)가 로그를 보낸다.
2. **Distributor** 가 요청을 받아 적절한 ingester로 분배한다.
3. **Ingester** 가 메모리/로컬 WAL 등에 로그를 쌓고 chunk를 만든다.
4. chunk와 index가 저장소(object store 등)로 반영된다.

### 4.2 읽기 경로

1. Grafana나 logcli가 쿼리를 보낸다.
2. **Query Frontend / Query Scheduler / Querier** 계층이 쿼리를 분산/병렬 처리한다.
3. 인덱스를 보고 필요한 chunk를 찾는다.
4. chunk를 읽어서 필터링하고 결과를 반환한다.

### 4.3 주요 컴포넌트

Loki 문서를 이해할 때 최소한 아래 컴포넌트 이름은 알아두는 게 좋다.

- **Distributor**: 로그 쓰기 요청 수신/분배
- **Ingester**: 로그를 수용하고 chunk 생성
- **Querier**: 쿼리 실행
- **Query Frontend**: 쿼리 병렬화, 캐싱, 분할
- **Query Scheduler**: 쿼리 스케줄링
- **Compactor**: 저장 데이터 정리/compaction/retention 관련 작업
- **Ruler**: 규칙/알림 계산
- **Index Gateway**: 인덱스 접근 최적화

백엔드 엔지니어 관점에서 중요한 건 “Loki는 단일 바이너리처럼 보여도 내부적으로는 **쓰기 계층과 읽기 계층이 분리된 분산 로그 시스템**”이라는 점이다.

---

## 5. 배포 모드 이해하기

최신 공식 문서를 기준으로 Loki는 대표적으로 다음 배포 모드를 가진다.

### 5.1 Monolithic / Single Binary

모든 컴포넌트를 하나의 프로세스에서 돌리는 가장 단순한 형태다.

언제 적합한가:

- 로컬 개발
- 소규모 테스트
- 개념 학습
- 아주 작은 운영 환경

장점:

- 단순하다.
- 빠르게 띄울 수 있다.

단점:

- 대규모 확장성은 제한적이다.

### 5.2 Simple Scalable Deployment (SSD)

읽기/쓰기/백엔드 타겟으로 나누는 배포 방식이다.  
다만 최신 문서에서는 **SSD가 deprecated 방향**으로 안내되고 있다.

### 5.3 Microservices Mode

대규모 운영에서 권장되는 형태다. 각 컴포넌트를 독립적으로 확장/운영할 수 있다.

실무 판단 기준:

- 작게 시작 → single binary
- 운영 트래픽 증가 → microservices mode 검토
- Helm chart를 쓴다면 최신 공식 문서의 권장사항과 deprecated 상태를 확인해야 한다.

---

## 6. 로그는 어떻게 Loki로 들어오는가

Loki 자체는 “로그 수집기”가 아니라 **로그 백엔드**다.  
그래서 보통 다음 중 하나가 Loki 앞단에 온다.

- **Grafana Alloy**
- **OpenTelemetry Collector**
- Promtail
- Docker driver
- Fluent Bit / Fluentd / Logstash 등

### 6.1 Grafana Alloy

최신 공식 문서 기준으로 Loki에 로그를 보내는 **주요 권장 경로**는 Grafana Alloy다.  
Alloy는 Grafana Labs가 배포하는 observability collector이고, 로그 수집/변환/전송 파이프라인을 구성할 수 있다.

로그 파이프라인의 기본 구조:

- **Collector**: 파일, HTTP, gRPC, queue 등에서 로그 수집
- **Transformer**: 라벨 추가, 필터링, 파싱, 배치
- **Writer**: Loki로 전송

즉 Alloy는 로그용 ETL 파이프라인이라고 생각하면 된다.

### 6.2 Promtail

예전에는 Loki 앞단으로 Promtail을 많이 썼다.  
하지만 최신 릴리스 노트 기준으로 **Promtail은 deprecated** 되었고, 코드가 Alloy로 합쳐지는 방향으로 안내된다. 새로 구성한다면 Promtail보다 Alloy를 우선 검토하는 게 맞다.

### 6.3 OpenTelemetry Collector

OTLP 기반 로그 수집을 하고 싶다면 OTel Collector로 Loki에 보낼 수 있다.  
최신 문서에서는 Loki의 native OTLP endpoint를 활용하는 경로도 설명한다.

언제 좋나:

- 이미 OTel 생태계를 쓰는 경우
- 로그/메트릭/트레이스를 같은 Collector 계열로 통합하고 싶은 경우
- 애플리케이션 instrumentation과 pipeline을 통일하고 싶은 경우

---

## 7. 이미 Grafana와 연동돼 있다면, Loki는 어떻게 쓰면 되나

이 문서의 실전 핵심이다.

Loki를 “잘 쓴다”는 건 결국 아래 순서를 습관화하는 거다.

1. **Explore에서 라벨 기반으로 범위를 좁힌다**
2. 필요한 문자열 필터를 앞에 둔다
3. JSON/logfmt/pattern parser로 필드를 꺼낸다
4. 그 필드로 추가 필터링하거나 metric query로 바꾼다
5. 반복적으로 보는 쿼리는 dashboard/alert로 승격한다

즉 Loki는 단순 grep가 아니라:

- stream selector
- line filter
- parser
- label filter
- metric aggregation

이 흐름으로 쓰는 게 핵심이다.

---

## 8. Grafana에서 Loki 데이터소스 사용하기

Grafana에서 Loki를 데이터소스로 추가하면:

- Explore에서 직접 로그 검색 가능
- Dashboard 패널에서 로그 패널/시계열 패널 생성 가능
- LogQL로 로그 쿼리와 메트릭 쿼리 작성 가능
- Derived fields로 trace ID 같은 값을 외부 링크와 연결 가능

### 8.1 기본 사용 흐름

1. Grafana에서 Loki data source 추가
2. Explore 열기
3. 쿼리 입력
4. 결과 로그를 확인
5. 유용한 쿼리는 panel로 저장
6. 메트릭 쿼리로 바꾸어 알람/시계열 시각화

### 8.2 Derived Fields

Grafana Loki 데이터소스는 로그에서 특정 필드를 뽑아 외부 링크로 연결할 수 있다.  
예를 들어 로그에 `traceID=...`가 들어 있다면, 그 값을 Tempo/Jaeger 링크로 연결해서 **로그 → 트레이스**로 점프할 수 있다.

이 기능은 분산 추적과 함께 쓸 때 체감이 매우 크다.

---

## 9. LogQL: Loki를 실제로 쓰는 언어

LogQL은 Loki의 쿼리 언어다. 개념적으로는 PromQL과 닮았지만 로그용 파이프라인이 추가되어 있다.

LogQL 쿼리는 크게 두 종류다.

- **Log queries**: 로그 라인을 반환
- **Metric queries**: 로그로부터 메트릭을 계산

---

## 10. LogQL 기본 구조

가장 기본 형태는 이렇게 시작한다.

```logql
{service="payment", env="prod"}
```

이건 **stream selector** 다.  
즉 라벨 조건으로 어떤 로그 스트림을 볼지 정한다.

그 다음 파이프라인을 붙인다.

```logql
{service="payment", env="prod"} |= "error"
```

이건:

1. `service=payment, env=prod` 로그 스트림을 고른 뒤
2. 그 안에서 `error` 문자열이 들어간 로그만 남긴다.

---

## 11. Stream Selector를 잘 쓰는 법

라벨 셀렉터는 Loki 쿼리의 첫 단계다. 성능적으로 가장 중요하다.

예:

```logql
{namespace="backend", service="payment", env="prod"}
```

지원되는 대표 연산자:

- `=` 정확히 일치
- `!=` 불일치
- `=~` 정규식 일치
- `!~` 정규식 불일치

예:

```logql
{service=~"payment|order|checkout", env="prod"}
```

주의점:

- 범위가 너무 넓은 셀렉터는 느리다.
- `{env="prod"}` 만으로 검색하면 prod 전체 로그를 뒤져야 할 수 있다.
- 서비스/클러스터/네임스페이스 단위까지 충분히 좁히는 게 좋다.

---

## 12. Line Filter를 잘 쓰는 법

대표적인 line filter:

- `|= "text"` 포함
- `!= "text"` 미포함
- `|~ "regex"` 정규식 포함
- `!~ "regex"` 정규식 미포함

예:

```logql
{service="payment"} |= "error" != "timeout"
```

이건 `error`는 포함하고 `timeout`은 제외한다.

실무 팁:

- line filter는 가능한 한 **앞쪽에 두는 게 좋다**.
- 공식 문서도 파싱보다 먼저 line filter를 적용하는 것이 보통 더 빠르다고 설명한다.

예:

```logql
{service="payment"} |= "error" | json | status >= 500
```

이 쿼리는 파서를 태우기 전에 우선 `error`를 포함한 로그만 좁히므로 더 효율적이다.

---

## 13. Parser를 잘 쓰는 법

Loki는 로그 라인에서 필드를 뽑아 쿼리 중간에 활용할 수 있다.

대표 parser:

- `| json`
- `| logfmt`
- `| pattern "..."`
- `| regexp "..."`
- `| unpack`

### 13.1 JSON

JSON 로그라면 가장 먼저 익혀야 한다.

```logql
{service="payment"} | json
```

이후 추출된 필드를 조건에 쓸 수 있다.

```logql
{service="payment"} | json | status >= 500
```

예시 로그:

```json
{"level":"error","status":503,"trace_id":"abc","latency":"120ms"}
```

위 로그라면 `status`, `trace_id`, `latency` 같은 필드를 쿼리 중간에 활용할 수 있다.

### 13.2 logfmt

로그가 `key=value` 스타일이면 좋다.

```logql
{job="nginx"} | logfmt | status >= 500
```

### 13.3 pattern

정형 로그인데 JSON/logfmt가 아니라면 pattern이 regexp보다 쓰기 쉽고 빠른 편이다.

예:

```logql
{job="nginx"} | pattern "<_> - - [<_>] \"<method> <path> <_>\" <status> <size> <_>"
```

### 13.4 regexp

구조가 복잡하거나 pattern으로 부족할 때 쓴다. 하지만 일반적으로는 pattern이 더 간결하고 성능상 유리한 경우가 많다.

---

## 14. Label Filter Expression

파싱으로 뽑은 필드나 원래 라벨을 비교해 조건을 더 걸 수 있다.

예:

```logql
{service="payment"} | json | duration > 200ms and status >= 500
```

지원 타입 개념:

- 문자열
- 숫자
- duration
- bytes

즉 로그 안에서 `duration="250ms"`, `size="42MB"` 같은 값을 파싱했다면 숫자/시간/바이트 비교가 가능하다.

---

## 15. Formatting과 Label Manipulation

대표적으로 다음을 자주 쓴다.

- `line_format`
- `label_format`
- `keep`
- `drop`

### 15.1 line_format

로그를 더 읽기 좋은 형태로 바꿀 수 있다.

```logql
{service="payment"} | json | line_format "{{.method}} {{.path}} -> {{.status}}"
```

### 15.2 keep / drop

너무 많은 필드가 결과에 섞일 때 필요한 필드만 남기거나 제거할 수 있다.  
특히 structured metadata나 파싱된 필드가 많을 때 결과를 정리하는 데 좋다.

---

## 16. Metric Query: 로그를 메트릭처럼 쓰기

Loki의 강력한 기능 중 하나다.  
로그를 단순히 보는 데서 끝내지 않고, **로그로부터 수치 시계열을 만들 수 있다.**

대표 함수:

- `rate(...)`
- `count_over_time(...)`
- `bytes_rate(...)`
- `bytes_over_time(...)`
- `absent_over_time(...)`

예시:

### 16.1 최근 5분 에러 개수

```logql
count_over_time({service="payment"} |= "error" [5m])
```

### 16.2 초당 에러율

```logql
rate({service="payment"} |= "error" [1m])
```

### 16.3 서비스별 에러율

```logql
sum by (service) (rate({env="prod"} |= "error" [1m]))
```

### 16.4 JSON 로그에서 latency를 추출해 집계

```logql
avg_over_time(
  {service="payment"}
  | json
  | unwrap latency [5m]
)
```

설명:

- `unwrap`은 로그에서 추출한 값을 샘플 값처럼 다루게 해준다.
- 이걸 이용하면 latency, payload size, duration 같은 값을 시계열로 만들 수 있다.

즉 Loki는 단순 로그 저장소가 아니라 **“logs to metrics”** 도구로도 강력하다.

---

## 17. Loki를 실제로 사용하는 대표 시나리오

### 17.1 장애 분석

예시 목표: “payment 서비스에서 최근 15분 동안 5xx 에러만 보고 싶다.”

```logql
{service="payment", env="prod"} | json | status >= 500
```

### 17.2 특정 요청 추적

예시 목표: trace_id 기반으로 관련 로그 찾기

가장 좋은 방식은 다음 중 하나다.

- 로그 라인에 trace_id 포함
- structured metadata로 trace_id 저장
- Grafana derived fields로 trace UI와 연결

예시:

```logql
{service="payment", env="prod"} |= "trace_id=9f0d"
```

또는 structured metadata라면:

```logql
{service="payment", env="prod"} | trace_id="9f0d"
```

### 17.3 NGINX / API access log에서 느린 요청 찾기

```logql
{job="nginx"}
| logfmt
| request_time > 1s
```

### 17.4 시간대별 에러율 대시보드

```logql
sum by (service) (rate({env="prod"} |= "error" [5m]))
```

### 17.5 로그 부재 알림

예시 목표: 특정 서비스 로그가 10분 이상 아예 안 들어오는지 확인

```logql
absent_over_time({service="payment", env="prod"}[10m])
```

이건 서비스가 죽었거나 에이전트 파이프라인이 끊긴 상황을 감지할 때 유용하다.

---

## 18. 라벨 설계가 제일 중요하다

Loki를 잘 못 쓰는 팀의 가장 흔한 문제는 **라벨 설계 실패**다.

### 18.1 좋은 라벨 예시

- `service`
- `app`
- `namespace`
- `cluster`
- `env`
- `region`
- `container`
- `job`

이런 값들은 보통 범위가 작고, 로그 출처를 설명하는 데 유용하다.

### 18.2 나쁜 라벨 예시

- `request_id`
- `trace_id`
- `session_id`
- `user_id`
- `order_id`
- `timestamp`
- `uuid`
- `path` 전체값이 무한히 달라지는 경우

이런 값은 고카디널리티라서 stream explosion을 일으킬 수 있다.

### 18.3 실전 규칙

- 라벨은 **“로그 출처를 설명하는 값”** 위주로 둔다.
- 자주 검색하지만 값 종류가 너무 많은 건 structured metadata 쪽을 검토한다.
- 라벨 값은 가능하면 **bounded** 해야 한다.
- 동적으로 바뀌는 값을 라벨로 붙일 때는 cardinality를 먼저 의심한다.

---

## 19. Grafana Explore에서 Loki를 쓰는 실전 루틴

이미 Loki가 붙어 있다면 실제로는 여기부터 익히면 된다.

### 단계 1. 범위를 최대한 좁힌다

```logql
{cluster="kr-prod", namespace="backend", service="payment"}
```

### 단계 2. line filter를 추가한다

```logql
{cluster="kr-prod", namespace="backend", service="payment"} |= "error"
```

### 단계 3. parser를 붙인다

```logql
{cluster="kr-prod", namespace="backend", service="payment"} |= "error" | json
```

### 단계 4. 필드 조건을 건다

```logql
{cluster="kr-prod", namespace="backend", service="payment"} |= "error" | json | status >= 500
```

### 단계 5. 필요하면 metric query로 승격한다

```logql
sum(rate({cluster="kr-prod", namespace="backend", service="payment"} |= "error" [5m]))
```

이 과정을 반복하면 Loki가 “로그가 쌓인 창고”가 아니라 **운영 분석 도구**로 바뀐다.

---

## 20. Dashboard에서 Loki를 쓸 때의 관점

Loki는 대시보드에서도 두 가지 방식으로 쓴다.

### 20.1 로그 패널

실시간 로그나 최근 장애 로그를 그대로 보여줄 때 사용

예:

- 특정 서비스 에러 로그
- 배치 실패 로그
- 워커 종료 로그

### 20.2 메트릭 패널

로그 기반 수치 시계열을 그릴 때 사용

예:

- 서비스별 초당 error rate
- 최근 5분 로그량
- status 5xx 개수
- 특정 예외 발생 건수

실무에서는 **“로그 패널 + 시계열 패널”** 을 같이 두는 경우가 많다.  
예를 들어 위에는 에러율 그래프, 아래에는 해당 시점의 에러 로그를 같이 두는 식이다.

---

## 21. Alerting도 가능하다

Loki는 로그를 기반으로 alerting rule을 만들 수 있다.

예:

- `rate(... |= "error" ...)` 가 임계값 초과
- `absent_over_time(...)` 로 로그 부재 감지
- 특정 문자열 패턴 증가 감지
- status 5xx 비율 증가 감지

즉 “로그를 보는 도구”에서 끝나지 않고, **로그를 운영 신호로 전환**할 수 있다.

---

## 22. 운영에서 자주 맞는 문제들

### 22.1 로그는 들어오는데 찾기 어렵다

원인 후보:

- 라벨 설계가 너무 빈약함
- 범위가 너무 넓은 셀렉터 사용
- 로그 파서/구조화 부재

해법:

- `service`, `env`, `namespace` 같은 기본 라벨을 정리
- 로그 포맷(JSON/logfmt) 표준화
- search 패턴을 team convention으로 정리

### 22.2 쿼리가 느리다

원인 후보:

- 셀렉터가 너무 넓음
- line filter 전에 parser를 태움
- 고카디널리티 라벨 사용
- 너무 긴 시간 범위를 한번에 조회

해법:

- stream selector를 먼저 좁힌다.
- line filter를 앞에 둔다.
- 고카디널리티 라벨 제거
- 필요한 시간 범위만 조회

### 22.3 라벨 폭발

원인 후보:

- request_id, trace_id, user_id 같은 값을 라벨로 보냄
- pod/container 인스턴스 단위 값이 과도하게 많음
- 동적 path를 그대로 라벨로 승격

해법:

- 라벨 cardinality 점검
- structured metadata로 이동
- 애플리케이션/에이전트 단계에서 라벨 정리

### 22.4 쿼리 결과에 `__error__`가 생긴다

이건 parser가 기대한 형식으로 로그를 해석하지 못한 경우가 많다.

예:

- `| json`을 붙였는데 실제 로그는 plain text
- 숫자/시간/바이트 변환 실패

이때는:

- 실제 로그 원문 확인
- parser 위치/종류 수정
- `keep/drop` 또는 추가 필터 적용

---

## 23. Loki를 잘 쓰기 위한 로그 포맷 전략

Loki를 도입하면 애플리케이션 로그 포맷을 같이 점검해야 한다.

### 권장 방향

- 가능하면 JSON 로그
- 최소한 공통 필드 유지
- 서비스 이름, 환경, 레벨, timestamp, trace_id, request_path, status, duration 같은 필드 표준화

예시 JSON 로그:

```json
{
  "timestamp": "2026-04-14T10:15:00Z",
  "level": "ERROR",
  "service": "payment",
  "env": "prod",
  "trace_id": "abc123",
  "status": 503,
  "duration_ms": 912,
  "message": "payment provider timeout"
}
```

이렇게 두면 LogQL에서:

```logql
{service="payment", env="prod"} | json | status >= 500
```

처럼 바로 분석 가능하다.

즉 Loki를 잘 쓰려면 **로그 설계**와 **수집 파이프라인 설계**를 같이 해야 한다.

---

## 24. Alloy / Collector 앞단에서 고려할 것

Loki 앞단 에이전트에서는 보통 다음을 설계한다.

- 어디서 로그를 읽을지
- 어떤 라벨을 붙일지
- 어떤 필드를 structured metadata로 둘지
- 어떤 로그를 버릴지
- 어떤 로그를 마스킹할지
- multiline 처리 필요 여부
- 배치/재시도 정책

실무에서 가장 자주 실수하는 부분은 “아무 생각 없이 라벨을 너무 많이 붙이는 것”과 “로그를 구조화하지 않고 본문 grep에만 의존하는 것”이다.

---

## 25. Loki와 Grafana를 함께 쓸 때 추천 학습 순서

### 1단계: 개념 익히기

- label
- stream
- chunk/index
- LogQL 기본 문법

### 2단계: Explore에서 자주 쓰는 쿼리 익히기

- 라벨 selector
- line filter
- `json`, `logfmt`
- 간단한 `count_over_time`, `rate`

### 3단계: 로그 포맷 정비

- JSON 로그 표준화
- trace_id, status, latency 필드 정리
- 라벨 표준 수립

### 4단계: 대시보드화

- 서비스별 에러율
- 5xx 비율
- 특정 예외 패턴 건수
- 최근 에러 로그 패널

### 5단계: 운영 심화

- retention 정책
- cardinality 관리
- object storage 운영
- alerting
- trace 연동

---

## 26. 바로 써먹는 LogQL 예시 모음

### 모든 payment 서비스 로그

```logql
{service="payment"}
```

### payment 서비스의 error 로그

```logql
{service="payment"} |= "error"
```

### timeout 제외

```logql
{service="payment"} |= "error" != "timeout"
```

### JSON 파싱 후 500 이상만

```logql
{service="payment"} | json | status >= 500
```

### 최근 5분 에러 수

```logql
count_over_time({service="payment"} |= "error" [5m])
```

### 최근 1분 에러율

```logql
rate({service="payment"} |= "error" [1m])
```

### 서비스별 에러율

```logql
sum by (service) (rate({env="prod"} |= "error" [5m]))
```

### NGINX access log에서 느린 요청

```logql
{job="nginx"} | logfmt | request_time > 1s
```

### 로그 부재 감지

```logql
absent_over_time({service="payment", env="prod"}[10m])
```

### 특정 필드만 보기 좋게 출력

```logql
{service="payment"} | json | line_format "{{.status}} {{.path}} {{.message}}"
```

---

## 27. 네가 지금부터 바로 해보면 좋은 것

이미 Loki와 Grafana가 연결되어 있다면, 바로 아래 순서대로 해보는 걸 추천한다.

### 체크리스트

1. Explore에서 현재 붙어 있는 라벨 목록 확인
2. `service`, `env`, `namespace`, `cluster` 기준으로 가장 기본적인 selector 작성
3. 에러 로그 필터 쿼리 3개 만들기
4. JSON/logfmt parser가 잘 동작하는지 확인
5. 최근 5분 에러율 metric query 만들기
6. 서비스 하나 골라 dashboard 패널 2개 만들기
   - 에러율 그래프
   - 최근 에러 로그 패널
7. trace_id가 있으면 derived fields 연결
8. 고카디널리티 라벨이 있는지 점검

이 8개만 해도 Loki 사용 감이 확 좋아진다.

---

## 28. 자주 하는 오해

### 오해 1. Loki는 Elasticsearch처럼 로그 전체 검색 엔진이다

아니다. Loki는 **라벨 기반으로 범위를 좁힌 후**, chunk를 읽어서 로그를 검색하는 방식이다.

### 오해 2. 라벨은 많을수록 좋다

아니다. 라벨은 적절하고 안정적이어야 한다. **고카디널리티 라벨은 독**이다.

### 오해 3. Loki만 붙이면 로그 분석이 바로 쉬워진다

그렇지 않다. 로그 포맷과 라벨 설계가 같이 좋아야 한다.

### 오해 4. Explore에서 grep만 하면 충분하다

실제 운영 가치는 parser + metric query + alerting + trace link까지 갈 때 커진다.

---

## 29. 한 번에 정리하는 실무 결론

Loki를 제대로 이해하려면 다음 세 줄만 기억하면 된다.

1. **라벨은 인덱스다.** 그래서 low-cardinality로 설계해야 한다.  
2. **로그 분석은 selector → filter → parse → aggregate 순서로 한다.**  
3. **Grafana와 함께 쓸 때 진짜 가치는 로그를 메트릭/알림/트레이스와 연결할 때 나온다.**

즉 Loki는 단순 로그 저장소가 아니라,  
**“라벨 기반 로그 검색 + 로그 기반 메트릭 + Grafana 관측 흐름의 중심축”** 이라고 이해하면 가장 정확하다.

---

## 30. 공식 문서 기준으로 기억해둘 최신 포인트

- Loki는 라벨만 인덱싱하고 로그 본문은 chunk에 저장한다.
- 최신 문서 기준 로그 수집의 주요 권장 경로는 Grafana Alloy다.
- Promtail은 deprecated 방향이다.
- Structured metadata는 고카디널리티 값 처리에 중요하다.
- 최신 배포 가이드에서는 대규모 운영에 microservices mode를 권장하는 흐름이 강화되고 있다.

---

## References

- Grafana Loki – Get started: https://grafana.com/docs/loki/latest/get-started/  
- Grafana Loki – Overview: https://grafana.com/docs/loki/latest/get-started/overview/  
- Grafana Loki – Architecture: https://grafana.com/docs/loki/latest/get-started/architecture/  
- Grafana Loki – Understand labels: https://grafana.com/docs/loki/latest/get-started/labels/  
- Grafana Loki – Label best practices: https://grafana.com/docs/loki/latest/get-started/labels/bp-labels/  
- Grafana Loki – Cardinality: https://grafana.com/docs/loki/latest/get-started/labels/cardinality/  
- Grafana Loki – Structured metadata: https://grafana.com/docs/loki/latest/get-started/labels/structured-metadata/  
- Grafana Loki – Query: https://grafana.com/docs/loki/latest/query/  
- Grafana Loki – Log queries: https://grafana.com/docs/loki/latest/query/log_queries/  
- Grafana Loki – Metric queries: https://grafana.com/docs/loki/latest/query/metric_queries/  
- Grafana Loki – Query reference: https://grafana.com/docs/loki/latest/query/query_reference/  
- Grafana Loki – Send data: https://grafana.com/docs/loki/latest/send-data/  
- Grafana Loki – Ingest logs using Alloy: https://grafana.com/docs/loki/latest/send-data/alloy/  
- Grafana Loki – OpenTelemetry Collector tutorial: https://grafana.com/docs/loki/latest/send-data/otel/otel-collector-getting-started/  
- Grafana Loki – Configuration: https://grafana.com/docs/loki/latest/configure/  
- Grafana Loki – Deployment modes: https://grafana.com/docs/loki/latest/get-started/deployment-modes/  
- Grafana Loki – Helm install: https://grafana.com/docs/loki/latest/setup/install/helm/  
- Grafana Loki – Release notes v3.5: https://grafana.com/docs/loki/latest/release-notes/v3-5/  
- Grafana – Loki data source: https://grafana.com/docs/grafana/latest/datasources/loki/  
- Grafana – Configure Loki data source: https://grafana.com/docs/grafana/latest/datasources/loki/configure-loki-data-source/
