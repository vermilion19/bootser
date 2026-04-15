# 부하 테스트 가이드

> 목적 없이 부하 테스트를 돌리면 숫자만 쌓이고 결론이 없다.
> 테스트 전에 **무엇을 검증할지**, **어떤 숫자가 나오면 성공인지** 먼저 정한다.

---

## 테스트 전에 반드시 정할 것

### 1. 무엇을 검증하고 싶은가?

| 유형 | 질문 예시 |
|---|---|
| **처리량 한계** | 이 서비스는 최대 몇 RPS까지 버티는가? |
| **응답시간 SLO** | 500 VU에서 p95가 500ms 안에 들어오는가? |
| **병목 위치** | DB가 먼저 터지는가, 앱이 먼저 터지는가? |
| **장애 전파** | catalog-service 다운 시 order-service가 정상 응답하는가? |

### 2. 합격 기준 (threshold)을 먼저 정한다

테스트 결과를 보고 나서 기준을 정하면 후기 합리화가 된다.
**반드시 실행 전에 작성한다.**

```
예시:
- p95 < 500ms, 에러율 < 1%  → 합격
- p95 >= 500ms OR 에러율 >= 1% → 개선 필요
```

### 3. 결과가 나오면 무엇을 바꿀 것인가?

"그래서?" 가 있어야 테스트가 의미 있다.

| 결과 | 다음 액션 |
|---|---|
| DB 커넥션 포화 | HikariCP `maximum-pool-size` 조정 |
| 쿼리 느림 | 인덱스 추가, N+1 제거 |
| 앱 CPU 포화 | 스케일 아웃 |
| 외부 서비스 지연 | 캐시 도입, 서킷브레이커 튜닝 |

---

## 이 프로젝트에서 검증할 수 있는 항목

### A. Redis vs DB 응답시간 비교

**검증하려는 것**
> ranking-service(Redis Sorted Set)가 catalog-service(DB)보다 얼마나 빠른가?

**방법**
- 동일 VU(예: 300)에서 `ranking-read.js`, `catalog-read.js` 각각 실행
- Grafana에서 p95 응답시간 비교

**합격 기준 (예시)**
```
ranking-service p95 < 100ms
catalog-service p95 < 500ms
```

**스크립트**: `scripts/ranking-read.js`, `scripts/catalog-read.js`

---

### B. 서킷브레이커 효과 검증

**검증하려는 것**
> catalog-service 장애 시, CB가 없을 때와 있을 때 order-service 응답이 어떻게 다른가?

**방법**
1. `order-write.js` 실행 중 catalog-service 컨테이너 강제 중단
2. Grafana에서 order-service 에러율, 응답시간 변화 확인
3. CB OPEN 로그(`[CB] catalog-service reserve 차단됨`) 확인

**합격 기준 (예시)**
```
catalog-service 다운 후 3초 이내 CB OPEN 전환
CB OPEN 상태에서 order-service 에러율 < 10% (즉시 fallback)
CB OPEN 상태에서 p95 < 500ms (느린 타임아웃 없음)
```

**확인할 메트릭**
```promql
resilience4j_circuitbreaker_state{name="catalog-reserve"}
resilience4j_circuitbreaker_calls_total{name="catalog-reserve", kind="not_permitted"}
```

---

### C. Scale-out 효과 검증

**검증하려는 것**
> catalog-service를 2대로 늘리면 처리량이 실제로 증가하는가?

**방법**
```bash
# 1대 기준 TPS 측정
docker compose up --scale catalog-service=1

# 2대로 늘린 뒤 동일 조건 재측정
docker compose up --scale catalog-service=2
```

**합격 기준 (예시)**
```
1대 대비 2대 TPS 1.5배 이상 증가 → scale-out 효과 있음
1.5배 미만 → 병목이 catalog-service 외부(DB, Nginx 등)에 있음
```

---

### D. Kafka Consumer Lag 관찰

**검증하려는 것**
> 주문이 폭발적으로 들어올 때, ranking/analytics Consumer가 얼마나 뒤처지는가?

**방법**
- `order-write.js` spike 구간 실행
- Grafana에서 `kafka_consumer_fetch_manager_records_lag` 메트릭 확인
- ranking-service의 실시간 랭킹 반영 지연 시간 측정

**합격 기준 (예시)**
```
Consumer lag < 1000 건 유지
spike 종료 후 30초 이내 lag 정상화
```

---

## 테스트 실행 순서 (권장)

```
1. 목적 선택 (A/B/C/D 중)
2. 합격 기준 이 문서에 작성
3. 테스트 실행
4. Grafana에서 메트릭 확인
5. 결과 기록 → 다음 액션 결정
```

---

## [현재 진행 중] 처리량 한계 분석

> 각 서비스가 SLO를 지키면서 최대 몇 RPS까지 처리할 수 있는지 측정한다.
> `ramping-arrival-rate` 사용 — RPS를 직접 제어하여 한계 지점을 정확히 찾는다.

### 측정 방식

RPS를 단계적으로 올리다가 **threshold 초과 시 테스트 자동 중단** (`abortOnFail: true`).
중단 직전 단계의 RPS = 처리량 한계.

### Step 1. ranking-service

| 항목 | 값 |
|---|---|
| 스크립트 | `throughput-ranking.js` |
| SLO | p95 < 200ms, 에러율 < 1% |
| RPS 범위 | 10 → 1500 RPS |
| 결과 | _(테스트 후 기록)_ |

**Grafana에서 볼 것**
- `http_server_requests_seconds{application="query-burst-ranking-service"}` p95
- JVM 메모리, CPU 사용량

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-ranking.js
```

---

### Step 2. catalog-service

| 항목 | 값 |
|---|---|
| 스크립트 | `throughput-catalog.js` |
| SLO | p95 < 500ms, 에러율 < 1% |
| RPS 범위 | 10 → 300 RPS |
| 결과 | _(테스트 후 기록)_ |

**Grafana에서 볼 것**
- `http_server_requests_seconds{application="query-burst-catalog-service"}` p95
- `hikaricp_connections_active` — DB 커넥션 포화 여부
- `hikaricp_connections_pending` — > 0 이면 커넥션 풀이 병목

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-catalog.js
```

---

### Step 3. order-service

| 항목 | 값 |
|---|---|
| 스크립트 | `throughput-order.js` |
| SLO | p95 < 3000ms, 에러율 < 5% |
| RPS 범위 | 5 → 70 RPS |
| 결과 | _(테스트 후 기록)_ |

**Grafana에서 볼 것**
- `http_server_requests_seconds{application="query-burst-order-service"}` p95
- `http_server_requests_seconds{application="query-burst-catalog-service"}` — 연쇄 지연 여부
- `resilience4j_circuitbreaker_state{name="catalog-reserve"}` — CB 동작 여부
- Tempo: 어느 span이 오래 걸리는지

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/throughput-order.js
```

---

### 결과 해석 가이드

```
한계 지점에서 무엇을 봐야 하는가?

CPU 100% 도달
  → 처리 로직이 무거움. 스케일 아웃이 효과적.

CPU 낮은데 응답시간 급등
  → I/O 대기 (DB or 외부 호출)
    ├─ hikaricp_connections_pending > 0  → 커넥션 풀 부족
    └─ 커넥션 충분한데 느림             → 쿼리 자체가 느림 (인덱스 확인)

order-service만 느리고 catalog는 정상
  → catalog 호출 오버헤드 또는 order DB 문제

모든 서비스가 동시에 느려짐
  → DB 서버 자체가 병목
```

---

## [데이터 정합성] 동시성 테스트

> 부하 테스트와 달리 "이 숫자가 0이어야 한다"는 pass/fail 기준이 명확한 테스트.
> 각 테스트는 독립적으로 실행하며, 실행 후 DB 상태를 수동으로 확인한다.

### 테스트 1: 재고 초과 예약 (Oversell)

| 항목 | 내용 |
|---|---|
| 스크립트 | `concurrency-oversell.js` |
| 검증 대상 | `CatalogService.createReservation()` — SELECT FOR UPDATE |
| 시나리오 | 재고 50개 상품에 200 VU가 동시 주문 |
| 합격 기준 | STOCK_RESERVED 건수 ≤ 초기 재고 수량 |
| 수동 확인 | `SELECT stock FROM product WHERE id=?;` — 음수이면 버그 |

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/concurrency-oversell.js
```

---

### 테스트 2: 중복 주문 (Idempotency)

| 항목 | 내용 |
|---|---|
| 스크립트 | `concurrency-idempotency.js` |
| 검증 대상 | `Idempotency-Key` + `request_id UNIQUE` 제약 |
| 시나리오 | 동일 Idempotency-Key로 50 VU가 동시에 주문 |
| 합격 기준 | STOCK_RESERVED 응답이 정확히 1건 |
| 주의 | 5xx가 발생하면 UNIQUE 제약이 방어는 하지만 409로 처리 안 된 것 — 개선 필요 |

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/concurrency-idempotency.js
```

---

### 테스트 3: 결제-취소 경합 (Race Condition)

| 항목 | 내용 |
|---|---|
| 스크립트 | `concurrency-pay-cancel.js` |
| 검증 대상 | `OrderApplicationService` — 낙관적/비관적 락 부재 |
| 시나리오 | 동일 orderId에 pay와 cancel을 `http.batch()`로 동시 발사 |
| 합격 기준 | pay + cancel 동시 성공(204+204) 건수 == 0 |
| 수동 확인 | order.status=PAID + reservation.status=RELEASED 조합 여부 확인 |

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/concurrency-pay-cancel.js
```

---

### [수동 테스트] CB OPEN 상태에서 재고 반환 누락

k6로 자동화 어려움. 수동으로 진행.

**재현 절차:**
```bash
# 1. 주문 N건 생성 (STOCK_RESERVED 상태)
# 2. catalog-service 컨테이너 중단
docker compose -f apps/query-burst-msa/docker-compose.yml stop catalog-service

# 3. order-service에 cancel 요청 폭주 (CB OPEN 유도)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/order-write.js  # 또는 curl 반복

# 4. catalog-service 재시작
docker compose -f apps/query-burst-msa/docker-compose.yml start catalog-service

# 5. DB 정합성 확인
# order.status=CANCELED 건수 vs inventory_reservation.status=RELEASED 건수 비교
# 차이가 있으면 재고 반환 누락 발생
```

**근본 원인:** `OrderApplicationService.cancel()`에서 `catalogServiceClient.release()` 실패(CB fallback)해도 `order.cancel()`은 계속 실행됨.
**개선 방안:** Cancel Saga 패턴 — 취소 요청을 Outbox 이벤트로 발행하고 catalog가 이벤트를 소비하여 release.

---

## 현재 스크립트 목록

| 파일 | 목적 |
|---|---|
| `concurrency-oversell.js` | **[정합성]** 재고 초과 예약 방지 검증 |
| `concurrency-idempotency.js` | **[정합성]** 중복 주문 방지 검증 |
| `concurrency-pay-cancel.js` | **[정합성]** 결제-취소 경합 시 상태 일관성 검증 |
| `throughput-ranking.js` | **[처리량 한계]** ranking-service RPS 한계 탐색 |
| `throughput-catalog.js` | **[처리량 한계]** catalog-service RPS 한계 탐색 |
| `throughput-order.js` | **[처리량 한계]** order-service RPS 한계 탐색 |
| `ranking-read.js` | ranking-service 읽기 부하 (VU 기반) |
| `catalog-read.js` | catalog-service 읽기 부하 (VU 기반) |
| `order-write.js` | order-service 주문 생성 부하 (VU 기반) |
| `complex-flow.js` | 탐색(70%) + 주문(30%) 혼합 시나리오 |
| `realistic-scenario.js` | flash sale, 전체 라이프사이클, 취소, analytics, 페이지네이션 복합 시나리오 |

---

## 실행 명령어

```bash
# Grafana 연동 실행 (권장 — 실시간 메트릭 확인)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/<스크립트명>

# Grafana 없이 터미널 출력만
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/<스크립트명>
```