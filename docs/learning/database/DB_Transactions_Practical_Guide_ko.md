# 데이터베이스 & 트랜잭션 감각: 주니어 백엔드 실무 생존서
실습 중심으로 “데이터가 안 꼬이게” 만드는 법 (PostgreSQL 중심, MySQL 차이 포함)

- 버전: v1.5 (마이그레이션/플랜 심화 포함)
- 작성일: 2026-02-25 (Asia/Seoul)
- 대상: 주니어 백엔드 개발자(실무 0-3년)
- 전제: SQL을 아주 조금이라도 써본 적이 있다(SELECT/INSERT 정도).  
- 목표:
  - 트랜잭션/격리수준/락을 **개념이 아니라 “재현 가능한 기술”**로 만든다
  - 무결성과 동시성을 **DB 레벨에서 먼저** 해결하고, 앱 레벨은 안전망으로 쓴다
  - 느린 쿼리를 **EXPLAIN으로 근거**를 가지고 개선한다
  - 마이그레이션/운영에서 자주 나는 사고를 **체크리스트로 예방**한다

> 이 문서는 “문법책”이 아니라, 실무에서 반복적으로 터지는 문제(중복 처리, oversell, 데드락, 느린 쿼리, 무중단 배포)를 **끝까지 해결하는 감각**을 목표로 합니다.

---

## 목차

### Part 0. 실습 환경과 도메인
0. [읽는 방법](#0-읽는-방법)  
0.1 [실습 환경: Docker + PostgreSQL + psql](#01-실습-환경-docker--postgresql--psql)  
0.2 [예제 도메인: 주문-결제-재고](#02-예제-도메인-주문-결제-재고)  
0.3 [실습 규칙: “두 세션”과 “재현 로그”](#03-실습-규칙-두-세션과-재현-로그)

### Part 1. DB를 “저장소”가 아니라 “동시성 제어 장치”로 보기
1. [DB가 해결하는 두 문제: 저장과 동시성](#1-db가-해결하는-두-문제-저장과-동시성)  
2. [ACID를 실무 언어로 번역하기](#2-acid를-실무-언어로-번역하기)  
3. [트랜잭션 경계: 어디까지 묶고 무엇을 빼야 하나](#3-트랜잭션-경계-어디까지-묶고-무엇을-빼야-하나)  
4. [커밋 이후의 세계: WAL, fsync, “커밋했는데 날아가요?”](#4-커밋-이후의-세계-wal-fsync-커밋했는데-날아가요)  

### Part 2. 격리수준과 이상현상: “재현 -> 해결” 8종 세트
5. [격리수준 빠른 지도](#5-격리수준-빠른-지도)  
6. [이상현상 표: 어떤 문제가 어디서 생기나](#6-이상현상-표-어떤-문제가-어디서-생기나)  
7. [실습 1: Non-repeatable read](#7-실습-1-non-repeatable-read)  
8. [실습 2: Phantom read](#8-실습-2-phantom-read)  
9. [실습 3: Lost update](#9-실습-3-lost-update)  
10. [실습 4: Write skew](#10-실습-4-write-skew)  
11. [실습 5: 데드락](#11-실습-5-데드락)  
12. [SERIALIZABLE과 재시도: 실패를 정상 흐름으로 만드는 법](#12-serializable과-재시도-실패를-정상-흐름으로-만드는-법)  
13. [Savepoint: 부분 롤백으로 트랜잭션을 유연하게](#13-savepoint-부분-롤백으로-트랜잭션을-유연하게)  

### Part 3. 락과 MVCC: “읽기/쓰기”가 실제로 어떻게 충돌하나
14. [MVCC 직관: 업데이트는 새 버전을 만든다](#14-mvcc-직관-업데이트는-새-버전을-만든다)  
15. [락 모드 개요(행 락/테이블 락/의도 락)](#15-락-모드-개요행-락테이블-락의도-락)  
16. [SELECT FOR UPDATE/SHARE: 언제 쓰고 언제 쓰지 말아야 하나](#16-select-for-updateshare-언제-쓰고-언제-쓰지-말아야-하나)  
17. [락 대기 관측: pg_locks, pg_stat_activity로 원인 잡기](#17-락-대기-관측-pg_locks-pg_stat_activity로-원인-잡기)  
18. [긴 트랜잭션이 만드는 2차 재앙: bloat, vacuum, idle in transaction](#18-긴-트랜잭션이-만드는-2차-재앙-bloat-vacuum-idle-in-transaction)  

### Part 4. 무결성(Constraints)으로 문제를 ‘불가능하게’ 만들기
19. [무결성은 기능이다: DB 제약의 역할](#19-무결성은-기능이다-db-제약의-역할)  
20. [UNIQUE/PK/FK/CHECK/NOT NULL: 언제 무엇을 쓰나](#20-uniquepkfkchecknot-null-언제-무엇을-쓰나)  
21. [부분 UNIQUE(Partial Unique)와 “활성 상태는 1개만”](#21-부분-uniquepartial-unique와-활성-상태는-1개만)  
22. [Exclusion constraint: 겹치면 안 되는 예약/스케줄 모델링](#22-exclusion-constraint-겹치면-안-되는-예약스케줄-모델링)  
23. [돈/포인트/잔액: 원장(ledger)로 설계하는 이유](#23-돈포인트잔액-원장ledger로-설계하는-이유)  

### Part 5. 인덱스와 쿼리 플랜: “추측” 대신 “근거”로 튜닝하기
24. [인덱스의 본질과 비용](#24-인덱스의-본질과-비용)  
25. [복합 인덱스와 leftmost prefix](#25-복합-인덱스와-leftmost-prefix)  
26. [부분/표현식/커버링 인덱스](#26-부분표현식커버링-인덱스)  
27. [EXPLAIN(ANALYZE, BUFFERS) 읽기 튜토리얼](#27-explainanalyze-buffers-읽기-튜토리얼)  
28. [조인 전략과 카디널리티(행 수 추정) 함정](#28-조인-전략과-카디널리티행-수-추정-함정)  
29. [페이징: OFFSET이 느린 이유, Keyset pagination](#29-페이징-offset이-느린-이유-keyset-pagination)  
30. [N+1은 DB 문제일까 앱 문제일까](#30-n1은-db-문제일까-앱-문제일까)  

### Part 6. 실무 패턴: 중복 요청, 원자성, 이벤트, 배치
31. [원자적 업데이트(Atomic update) 패턴](#31-원자적-업데이트atomic-update-패턴)  
32. [낙관적 락 vs 비관적 락: 선택 기준과 구현](#32-낙관적-락-vs-비관적-락-선택-기준과-구현)  
33. [UPSERT(ON CONFLICT): 중복 삽입을 안전하게](#33-upserton-conflict-중복-삽입을-안전하게)  
34. [Idempotency key: 결제/주문 중복을 끝내는 법](#34-idempotency-key-결제주문-중복을-끝내는-법)  
35. [Outbox 패턴: DB 커밋과 메시지 발행 연결](#35-outbox-패턴-db-커밋과-메시지-발행-연결)  
36. [배치/잡에서의 트랜잭션: chunking, lock, retry](#36-배치잡에서의-트랜잭션-chunking-lock-retry)  
37. [애드바이저리 락(Advisory lock): 앱 수준 락을 DB로](#37-애드바이저리-락advisory-lock-앱-수준-락을-db로)  

### Part 7. 운영과 마이그레이션: 사고가 가장 많이 나는 곳
38. [커넥션 풀: 병목의 시작점](#38-커넥션-풀-병목의-시작점)  
39. [타임아웃/재시도/서킷브레이커: DB 관점](#39-타임아웃재시도서킷브레이커-db-관점)  
40. [슬로우 쿼리 관측: pg_stat_statements, 로그](#40-슬로우-쿼리-관측-pg_stat_statements-로그)  
41. [무중단 마이그레이션 플레이북](#41-무중단-마이그레이션-플레이북)  
42. [복제/리드 레플리카: 일관성 함정](#42-복제리드-레플리카-일관성-함정)  
43. [장애 대응 시나리오: “락 대기 폭발”과 “커넥션 고갈”](#43-장애-대응-시나리오-락-대기-폭발과-커넥션-고갈)

### Part 8. 케이스 스터디: 실무에서 자주 부딪히는 3대 문제
44. [케이스 1: 재고 oversell을 막는 3가지 설계](#44-케이스-1-재고-oversell을-막는-3가지-설계)  
45. [케이스 2: 결제 중복과 정확히 한 번 처리](#45-케이스-2-결제-중복과-정확히-한-번-처리)  
46. [케이스 3: 좌석/예약 시스템(겹침 금지)](#46-케이스-3-좌석예약-시스템겹침-금지)  

### Appendices
A. [실무 체크리스트](#a-실무-체크리스트)  
B. [자주 하는 실수 50](#b-자주-하는-실수-50)  
C. [면접/과제 대비 질문 40](#c-면접과제-대비-질문-40)  
D. [실습 SQL 전체 스크립트](#d-실습-sql-전체-스크립트)  
E. [PostgreSQL vs MySQL 차이 요약](#e-postgresql-vs-mysql-차이-요약)  
F. [“내가 지금 무엇을 공부해야 하지?” 4주 학습 플랜](#f-내가-지금-무엇을-공부해야-하지-4주-학습-플랜)

---

# Part 0. 실습 환경과 도메인

## 0. 읽는 방법

이 문서는 다음 루프를 반복하도록 설계했습니다.

1) 개념을 **한 문장**으로 정의한다  
2) 이상현상을 **직접 재현**한다(두 세션)  
3) 해결책을 **DB 레벨**에서 적용한다(락/격리수준/원자적 업데이트/제약조건)  
4) 서비스 코드는 “경계(트랜잭션) + 재시도 + 관측성”으로 마무리한다  
5) 마지막으로 “이 선택이 왜 맞는지”를 **설명 가능**하게 만든다

### 추천 공부 루틴(현실적인 버전)
- 평일: 하루 30분(개념 10 + 실습 20)  
- 주말: 2시간(케이스 스터디/쿼리 튜닝/마이그레이션)

---

## 0.1 실습 환경: Docker + PostgreSQL + psql

실습용 `docker-compose.yml`:

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
      POSTGRES_DB: lab
    ports:
      - "5432:5432"
    command: >
      postgres -c max_connections=200
              -c shared_buffers=256MB
              -c log_statement=none
              -c log_min_duration_statement=200ms
              -c log_lock_waits=on
              -c deadlock_timeout=200ms
              -c statement_timeout=0
              -c lock_timeout=0
              -c idle_in_transaction_session_timeout=0
```

실행:

```bash
docker compose up -d
psql "postgresql://postgres:postgres@localhost:5432/lab"
```

psql 편의 설정(권장):

```sql
\timing on
\x auto
```

---

## 0.2 예제 도메인: 주문-결제-재고

실무에서 DB/트랜잭션 사고가 가장 많이 나는 3종 세트입니다.

- 주문(order): 상태 전이(생성 -> 결제 -> 취소)
- 결제(payment): 중복 요청, 재시도, 정확히 한 번 처리(idempotency)
- 재고(stock): 동시성 경쟁, oversell 방지

최소 스키마:

```sql
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS stock_items;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
  id            BIGSERIAL PRIMARY KEY,
  sku           TEXT NOT NULL UNIQUE,
  name          TEXT NOT NULL,
  price_cents   BIGINT NOT NULL CHECK (price_cents >= 0)
);

CREATE TABLE stock_items (
  product_id    BIGINT PRIMARY KEY REFERENCES products(id),
  available_qty BIGINT NOT NULL CHECK (available_qty >= 0),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('CREATED','PAID','CANCELED')),
  total_cents   BIGINT NOT NULL CHECK (total_cents >= 0),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
  order_id      BIGINT NOT NULL REFERENCES orders(id),
  product_id    BIGINT NOT NULL REFERENCES products(id),
  qty           BIGINT NOT NULL CHECK (qty > 0),
  price_cents   BIGINT NOT NULL CHECK (price_cents >= 0),
  PRIMARY KEY (order_id, product_id)
);

CREATE TABLE payments (
  id               BIGSERIAL PRIMARY KEY,
  order_id         BIGINT NOT NULL UNIQUE REFERENCES orders(id),
  provider         TEXT NOT NULL,
  provider_tx_id   TEXT NOT NULL,
  status           TEXT NOT NULL CHECK (status IN ('PENDING','CAPTURED','FAILED')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_tx_id)
);

CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

-- 샘플 데이터
INSERT INTO products(sku, name, price_cents) VALUES
('SKU-001', 'Keyboard', 5000),
('SKU-002', 'Mouse', 3000);

INSERT INTO stock_items(product_id, available_qty)
SELECT id, 10 FROM products;
```

---

## 0.3 실습 규칙: “두 세션”과 “재현 로그”

동시성 이슈는 **세션 2개**로 재현해야 감이 옵니다.

- 터미널 A: 세션 A (psql)
- 터미널 B: 세션 B (psql)

각 실습마다 “재현 로그”를 남기는 습관을 추천합니다.

- 무엇을 재현하려고 했는가(1줄)
- 세션 A/B에서 실행한 SQL
- 기대 결과/실제 결과
- 해결책(락/격리수준/원자적 업데이트/제약조건/재시도)과 트레이드오프

이 로그는 면접/이직/업무 회고 때도 그대로 자산이 됩니다.

---

# Part 1. DB를 “동시성 제어 장치”로 보기

## 1. DB가 해결하는 두 문제: 저장과 동시성

DB를 “엑셀 같은 저장소”로 보면 트랜잭션이 어렵습니다.  
DB는 크게 두 일을 합니다.

1) 데이터를 영구히 저장한다(내구성, durability)  
2) 여러 사용자가 동시에 접근해도 “규칙”을 지킨다(동시성 제어)

실무에서 돈이 많이 나가는 버그는 2)에서 생깁니다.

- “재고가 음수가 됐어요”
- “결제가 두 번 됐어요”
- “취소했는데 다시 살아났어요”
- “주문 상태가 CREATED인데 결제는 CAPTURED예요”

이런 문제는 대개 다음 조합입니다.

- read-modify-write(읽고 판단하고 쓰기) + 동시성
- 제약조건 부족(UNIQUE/CHECK/FK 미사용)
- 재시도/타임아웃 정책이 멱등성과 분리
- 긴 트랜잭션/락 대기/커넥션 풀 병목

---

## 2. ACID를 실무 언어로 번역하기

ACID는 “면접 단어”가 아니라 “어떤 버그를 막나”로 이해해야 합니다.

### A - Atomicity(원자성)
- 트랜잭션 안의 작업은 전부 성공하거나, 전부 실패한다.
- 실무 번역:
  - 주문 생성은 됐는데 결제 상태 업데이트는 실패 같은 “반쪽 상태” 방지
  - (단, 외부 시스템까지 원자적으로 묶어주는 건 아님. 그건 2PC/사가 등 다른 문제)

### C - Consistency(일관성)
- 트랜잭션 전후로 제약조건이 유지된다.
- 실무 번역:
  - 재고는 음수가 아니다(CHECK)
  - 결제는 주문 1개당 1개다(UNIQUE)
  - 주문 아이템은 존재하는 주문만 참조한다(FK)

### I - Isolation(격리)
- 동시에 실행되는 트랜잭션이 서로에게 주는 영향을 제한한다.
- 실무 번역:
  - 동시에 결제해도 “한 번만” 반영된다
  - 동시에 재고 차감해도 oversell이 없다
  - 동시에 상태 전이해도 상태가 꼬이지 않는다

### D - Durability(지속성)
- 커밋된 데이터는 서버가 죽어도 남는다.
- 실무 번역:
  - 결제 성공 기록은 재시작해도 사라지지 않는다(단, 설정과 스토리지 신뢰성이 전제)

---

## 3. 트랜잭션 경계: 어디까지 묶고 무엇을 빼야 하나

### 3.1 “같이 성공해야 하는 것”만 묶는다
결제 확정 처리(예):

트랜잭션 안에 넣을 것(보통):
- orders: status를 PAID로 변경(조건부 업데이트)
- payments: 상태를 CAPTURED로 확정(멱등성 보장)
- stock_items: 재고 차감(원자적 업데이트)
- outbox_events: 이벤트 기록

트랜잭션에서 빼야 할 것(강력 권장):
- 결제사 승인 API 호출
- 문자/이메일 발송
- 느린 파일 업로드
- 다른 DB/다른 서비스 호출

왜냐하면 트랜잭션은 락을 잡고 있기 때문입니다.  
외부 호출이 느리면 락이 오래 잡히고, 락 대기가 늘고, 결국 전체 장애로 번질 수 있습니다.

### 3.2 “트랜잭션이 길어지는 원인” 체크리스트
- 네트워크 호출이 들어갔다
- 대량 루프를 트랜잭션 안에서 돌린다
- 사용자 입력/대기(예: 화면에서 확인)까지 트랜잭션을 유지한다
- 배치가 한번에 너무 많은 row를 업데이트한다

### 3.3 서비스 코드에서의 경계 예시(의사코드)
(언어/프레임워크와 무관한 형태로)

```text
BEGIN TRANSACTION
  1) 주문 상태를 CREATED -> PAID로 조건부 업데이트 (row count 확인)
  2) 결제 레코드 upsert/confirm (idempotent)
  3) 재고를 원자적으로 차감 (row count 확인)
  4) outbox 이벤트 insert
COMMIT
외부 부작용(알림/배송 요청 등)은 outbox 워커가 처리
```

---

## 4. 커밋 이후의 세계: WAL, fsync, “커밋했는데 날아가요?”

주니어가 자주 오해하는 포인트:
- “COMMIT 했으면 100% 디스크에 기록되죠?”  
  -> 대부분의 DB는 **WAL(Write-Ahead Logging)** 기반으로 안정성을 확보하지만,
     스토리지/설정/운영 방식에 따라 위험이 달라질 수 있습니다.

### 4.1 아주 단순화한 DB 쓰기 흐름
1) 변경 내용을 WAL에 기록(순차 쓰기)
2) 데이터 페이지(테이블 파일)는 나중에 반영될 수 있음
3) 체크포인트/백그라운드 작업으로 페이지 반영
4) 장애 시 WAL을 재생(replay)하여 복구

### 4.2 실무에서 알아야 할 것(과하게 파지 말고 감각만)
- WAL은 보통 빠르고 안전한 편(순차 쓰기)
- 그러나 “fsync를 끄거나”, 신뢰할 수 없는 스토리지를 쓰면
  커밋 이후에도 데이터 손실 가능성이 생길 수 있음
- 클라우드/컨테이너에서도 스토리지 계층을 이해하는 게 중요

이 문서의 목적은 스토리지 엔진 개발이 아니라,
**트랜잭션/동시성에서 안전한 설계를 하는 것**이므로,
여기서는 “커밋은 WAL 기반” 정도의 감각까지만 잡고 넘어갑니다.

---

# Part 2. 격리수준과 이상현상: “재현 -> 해결” 8종 세트

## 5. 격리수준 빠른 지도

PostgreSQL 기준(실무 감각 중심 요약):

- READ COMMITTED(기본)
  - 각 statement 시작 시점에 커밋된 것만 본다
  - 같은 트랜잭션 안에서도 SELECT 결과가 바뀔 수 있다
- REPEATABLE READ
  - 트랜잭션 시작 시점 스냅샷을 끝까지 본다
  - 같은 SELECT는 반복해도 동일한 결과(대부분)
  - 하지만 write skew 같은 문제가 가능
- SERIALIZABLE
  - “동시 실행 결과가 어떤 직렬 실행과 동등”하도록 보장하려고 한다
  - 대신 충돌 시 실패(예: serialization failure)가 발생할 수 있음
  - 그래서 “재시도”가 설계의 일부가 된다

MySQL(InnoDB) 주의:
- 기본 격리수준이 REPEATABLE READ인 환경이 많고(설정에 따라 다름),
- gap lock/next-key lock 때문에 “읽기”도 더 많이 막힐 수 있습니다.

---

## 6. 이상현상 표: 어떤 문제가 어디서 생기나

아래 표는 “실무 직관”을 위한 지도입니다(엄밀한 표준 정의는 DB마다 조금씩 다릅니다).

- Dirty read: 커밋되지 않은 값을 읽음
- Non-repeatable read: 같은 row를 두 번 읽었는데 값이 바뀜
- Phantom read: 같은 조건으로 조회했는데 row 개수가 바뀜(새 row 출현)
- Lost update: 동시에 업데이트했는데 한쪽이 덮어씌워짐
- Write skew: 서로 다른 row를 업데이트했는데 전역 규칙이 깨짐

대략적인 경향:

| 격리수준 | Dirty read | Non-repeatable | Phantom | Lost update | Write skew |
|---|---:|---:|---:|---:|---:|
| READ COMMITTED | X | 가능 | 가능 | 가능(패턴에 따라) | 가능 |
| REPEATABLE READ | X | 보통 X | DB마다 다름 | 패턴에 따라 가능 | 가능 |
| SERIALIZABLE | X | X | X(의도상) | X(의도상) | X(의도상) |

중요:
- “격리수준만 올리면 끝”이 아닙니다.
- 성능/충돌/재시도 비용이 올라갑니다.
- 대부분의 실무 문제는 **원자적 업데이트 + 제약조건 + 최소한의 락**으로 해결 가능합니다.

---

## 7. 실습 1: Non-repeatable read

### 준비
```sql
DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
  id      BIGSERIAL PRIMARY KEY,
  owner   TEXT NOT NULL UNIQUE,
  balance BIGINT NOT NULL CHECK (balance >= 0)
);
INSERT INTO accounts(owner, balance) VALUES ('alice', 1000);
```

### 재현(READ COMMITTED에서)
세션 A:
```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice'; -- 1000
-- 여기서 멈춤
```

세션 B:
```sql
BEGIN;
UPDATE accounts SET balance = 1100 WHERE owner='alice';
COMMIT;
```

세션 A:
```sql
SELECT balance FROM accounts WHERE owner='alice'; -- 1100 (같은 트랜잭션인데 바뀜)
COMMIT;
```

### 왜 중요한가(실무)
- “조회 -> 검증 -> 처리” 사이에 값이 바뀌면 검증이 무의미해질 수 있음
- 예: “쿠폰이 ACTIVE인지 확인하고 사용 처리” 같은 흐름

### 해결 1: REPEATABLE READ로 올리기(읽기 일관성)
```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT balance FROM accounts WHERE owner='alice';
-- B가 바꿔도 A는 같은 값(스냅샷)으로 봄
COMMIT;
```

하지만: 읽기 일관성만 확보되고, write skew 같은 다른 문제는 남을 수 있습니다.

### 해결 2: 읽고 곧바로 쓸 거면 FOR UPDATE
```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice' FOR UPDATE;
-- 이제 다른 트랜잭션은 alice를 UPDATE하려면 기다림
UPDATE accounts SET balance = balance - 100 WHERE owner='alice';
COMMIT;
```

---

## 8. 실습 2: Phantom read

### 준비
```sql
DROP TABLE IF EXISTS coupons;
CREATE TABLE coupons (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','USED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO coupons(user_id, status) VALUES (1, 'ACTIVE');
```

### 재현(READ COMMITTED)
세션 A:
```sql
BEGIN;
SELECT count(*) FROM coupons WHERE user_id=1 AND status='ACTIVE'; -- 1
-- 대기
```

세션 B:
```sql
BEGIN;
INSERT INTO coupons(user_id, status) VALUES (1, 'ACTIVE');
COMMIT;
```

세션 A:
```sql
SELECT count(*) FROM coupons WHERE user_id=1 AND status='ACTIVE'; -- 2 (팬텀)
COMMIT;
```

### 실무에서 팬텀이 문제가 되는 경우
- “현재 ACTIVE 쿠폰이 1개 이하인 경우만 발급” 같은 규칙
- “현재 예약 수가 N 이하인 경우만 추가 예약” 같은 규칙

### 해결 전략은 대개 2가지
1) 규칙을 **UNIQUE/제약조건**으로 모델링해서 “위반을 불가능하게” 만들기(Part 4)
2) SERIALIZABLE + 재시도로 “직렬 실행처럼” 보장하기(12장)

쿠폰 예시를 “활성 쿠폰은 1개만”으로 바꾸면:
- 부분 UNIQUE로 해결 가능합니다(21장).

---

## 9. 실습 3: Lost update

Lost update는 “개념”보다 “코딩 습관”입니다.

### 준비
```sql
TRUNCATE accounts;
INSERT INTO accounts(owner, balance) VALUES ('alice', 1000);
```

### 재현(읽고 계산하고 덮어쓰기)
세션 A:
```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice'; -- 1000
-- 앱에서 1000-100 계산했다고 치자
UPDATE accounts SET balance = 900 WHERE owner='alice';
-- 커밋 대기
```

세션 B(거의 동시에):
```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice'; -- 1000
UPDATE accounts SET balance = 900 WHERE owner='alice';
COMMIT;
```

세션 A:
```sql
COMMIT;
```

원래 동시에 두 번 차감이면 800이어야 하는데 900이 됩니다(한 번이 사라짐).

### 해결 1: 원자적 업데이트(가장 추천)
```sql
UPDATE accounts
SET balance = balance - 100
WHERE owner='alice' AND balance >= 100;
```

- row count = 1이면 성공
- row count = 0이면 실패(잔액 부족 등)

이 패턴이 강한 이유:
- “읽기”를 없애고 “한 문장”으로 처리
- 트랜잭션 경쟁에서 더 안전하고 빠름

### 해결 2: SELECT FOR UPDATE로 락 잡고 읽기
```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice' FOR UPDATE;
UPDATE accounts SET balance = balance - 100 WHERE owner='alice';
COMMIT;
```

### 해결 3: 낙관적 락(버전 컬럼)
(Part 6에서 더 깊게)

---

## 10. 실습 4: Write skew

“서로 다른 row를 업데이트하는데 규칙이 깨지는” 케이스입니다.  
대표 예시: 당직자는 최소 1명.

### 준비
```sql
DROP TABLE IF EXISTS oncall;
CREATE TABLE oncall (
  doctor TEXT PRIMARY KEY,
  active BOOLEAN NOT NULL
);
INSERT INTO oncall(doctor, active) VALUES ('kim', true), ('lee', true);
```

### 재현(REPEATABLE READ에서)
세션 A:
```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT count(*) FROM oncall WHERE active=true; -- 2
UPDATE oncall SET active=false WHERE doctor='kim';
-- 커밋 대기
```

세션 B:
```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT count(*) FROM oncall WHERE active=true; -- 2 (같은 스냅샷)
UPDATE oncall SET active=false WHERE doctor='lee';
COMMIT;
```

세션 A:
```sql
COMMIT;
```

둘 다 성공하면 active=true가 0이 될 수 있습니다.

### 해결 1: SERIALIZABLE + 재시도
SERIALIZABLE에서는 이런 충돌을 감지하고 한쪽이 실패할 수 있습니다.

### 해결 2: “규칙을 깨는 변경”에 공동 락을 걸기
예: 별도의 “가드 행”을 만들어 FOR UPDATE로 잠그고 업데이트 순서를 강제:

```sql
CREATE TABLE oncall_guard(id INT PRIMARY KEY);
INSERT INTO oncall_guard(id) VALUES (1);

-- 트랜잭션에서:
SELECT * FROM oncall_guard WHERE id=1 FOR UPDATE;
-- 이제 여기서 oncall 업데이트 수행
```

이 방식은 단순하지만, “모든 변경이 같은 가드 행을 잠가야” 하므로 병목이 될 수 있습니다.

### 해결 3: 모델을 바꾸기
- “최소 1명”이라는 규칙을 다른 데이터 구조로 강제(예: 현재 당직자를 하나의 row로 보관하고 교대 방식으로 업데이트)
- 도메인에 따라 더 좋은 모델이 있을 수 있음

---

## 11. 실습 5: 데드락

### 준비
```sql
DROP TABLE IF EXISTS t_deadlock;
CREATE TABLE t_deadlock (
  id BIGINT PRIMARY KEY,
  v  BIGINT NOT NULL
);
INSERT INTO t_deadlock(id, v) VALUES (1, 10), (2, 20);
```

세션 A:
```sql
BEGIN;
UPDATE t_deadlock SET v = v + 1 WHERE id = 1;
-- 멈춤
UPDATE t_deadlock SET v = v + 1 WHERE id = 2;
COMMIT;
```

세션 B:
```sql
BEGIN;
UPDATE t_deadlock SET v = v + 1 WHERE id = 2;
-- 거의 동시에
UPDATE t_deadlock SET v = v + 1 WHERE id = 1;
COMMIT;
```

둘 중 하나가 `deadlock detected`로 실패합니다.

### 실무 대응 5가지
1) 항상 같은 순서로 락을 잡기  
   - 예: id 오름차순으로 업데이트  
2) 트랜잭션을 짧게 유지하기  
3) lock_timeout/statement_timeout으로 무한 대기 금지  
4) 데드락/직렬화 실패는 “정상 실패”로 보고 재시도(멱등성 전제)  
5) 핫스팟(한 row에 경쟁)이면 설계를 바꾸거나 sharding/분산 전략 고려

---

## 12. SERIALIZABLE과 재시도: 실패를 정상 흐름으로 만드는 법

SERIALIZABLE은 “완벽”이 아니라 “더 강한 보장 + 실패 가능성”입니다.

### 12.1 왜 실패가 생기나
- DB가 동시 실행을 “직렬 실행처럼” 만들기 위해,
  충돌 가능성을 발견하면 트랜잭션을 실패시켜 안전을 확보합니다.
- PostgreSQL에서는 `serialization_failure` 같은 에러가 발생할 수 있습니다.

### 12.2 재시도는 설계다(그냥 try-catch가 아님)
재시도 설계 체크리스트:

- [ ] 재시도 대상 오류를 정확히 구분한다(직렬화 실패/데드락 등)
- [ ] 재시도는 짧은 지수 백오프 + jitter가 좋다(동시 재시도 폭주 방지)
- [ ] 쓰기 재시도는 멱등성이 확보되어야 한다(34장)
- [ ] 재시도 횟수/시간 상한을 둔다(무한 루프 금지)
- [ ] 관측 가능해야 한다(재시도 횟수 메트릭, 로그)

의사코드:

```text
for attempt in 1..max:
  try:
    BEGIN SERIALIZABLE
      do_work()
    COMMIT
    return success
  except serialization_failure or deadlock:
    ROLLBACK
    sleep(backoff_with_jitter(attempt))
    continue
  except other:
    ROLLBACK
    raise
fail
```

---

## 13. Savepoint: 부분 롤백으로 트랜잭션을 유연하게

하나의 트랜잭션 안에서 “일부 작업만 실패하면 그 부분만 되돌리고 계속”할 때 savepoint가 유용합니다.

예:
- 여러 항목을 처리하는데 일부는 유효하지 않다
- 유효하지 않은 것만 건너뛰고 나머지는 커밋하고 싶다

```sql
BEGIN;
SAVEPOINT sp1;

-- 작업 A
-- 실패 가능
-- 실패하면:
ROLLBACK TO SAVEPOINT sp1;

-- 작업 B 계속
COMMIT;
```

주의:
- savepoint 남발은 복잡도를 올립니다.
- “정말 필요한지” 먼저 고민하고, 가능하면 작업을 분리하는 게 더 단순합니다.

---

# Part 3. 락과 MVCC: “읽기/쓰기”가 실제로 어떻게 충돌하나

## 14. MVCC 직관: 업데이트는 새 버전을 만든다

PostgreSQL 같은 MVCC DB에서는:

- UPDATE는 행을 “그 자리에서 바꾸는 것”이 아니라  
  **새 버전의 행을 만든다**
- 트랜잭션은 자신이 볼 수 있는 버전만 본다(스냅샷)

결과:
- 읽기(SELECT)는 쓰기와 덜 충돌한다(많이 막지 않는다)
- 대신 “오래된 버전”이 쌓이면 vacuum이 필요하고, bloat가 생길 수 있음

---

## 15. 락 모드 개요(행 락/테이블 락/의도 락)

이 장은 “모든 락 모드 암기”가 목적이 아닙니다.  
실무에서 자주 만나는 락만 확실히 잡으면 됩니다.

### 15.1 행 수준 락(자주 쓰는 것)
- SELECT FOR UPDATE: 업데이트 목적
- SELECT FOR SHARE: 공유 잠금(버전에 따라 의미 차이)
- UPDATE/DELETE는 내부적으로 행 락을 잡음

### 15.2 테이블 락(마이그레이션에서 자주)
- ALTER TABLE, CREATE INDEX 등은 상황에 따라 테이블 락을 강하게 잡을 수 있음
- 무중단 마이그레이션이 필요한 이유(41장)

### 15.3 “읽기 락”이 생각보다 적게 막히는 이유
MVCC에서는 읽기는 특정 스냅샷을 보기 때문에,
대부분의 경우 읽기-쓰기 충돌이 줄어듭니다.

하지만:
- SELECT FOR UPDATE 같은 “잠그는 읽기”는 예외입니다.
- 그리고 MySQL에서는 gap lock 등으로 더 많이 막힐 수 있습니다(E부록).

---

## 16. SELECT FOR UPDATE/SHARE: 언제 쓰고 언제 쓰지 말아야 하나

### 16.1 언제 쓰나(추천)
- read-modify-write가 필요한데, 원자적 업데이트가 어렵다
- 결제 확정/상태 전이 같은 “정확히 한 번”이 중요한 작업
- 동일 row에 경쟁이 잦고, 낙관적 재시도가 비용이 큰 경우

예: 주문 상태 전이에서 “중복 결제 확정” 방지

```sql
BEGIN;
SELECT status FROM orders WHERE id=:id FOR UPDATE;
-- status 검사
UPDATE orders SET status='PAID' WHERE id=:id;
COMMIT;
```

하지만 더 좋은 경우가 많습니다:
- 가능하면 조건부 UPDATE 한 문장(31장)을 먼저 고려하세요.

### 16.2 언제 피하나(주의)
- 범위가 큰 SELECT에 FOR UPDATE를 걸면 락이 과도하게 잡힙니다.
- “목록 조회 + FOR UPDATE”는 대개 위험 신호입니다.
- 트랜잭션이 길어지면 락 대기 폭발로 장애가 됩니다.

### 16.3 실무 팁: 필요한 row만 잠그기
- 가능한 한 PK로 찍어서 잠그기
- 잠그는 범위를 최소화하기

---

## 17. 락 대기 관측: pg_locks, pg_stat_activity로 원인 잡기

프로덕션에서 “왜 느리지?”는 대부분
- 락 대기
- IO 대기
- 커넥션 풀 대기
중 하나입니다.

### 17.1 지금 어떤 쿼리가 실행 중인가
```sql
SELECT pid, state, wait_event_type, wait_event, query, now() - query_start AS running_for
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY running_for DESC
LIMIT 20;
```

- state: active, idle, idle in transaction 등
- wait_event_type: Lock/IO/Client 등

### 17.2 누가 누구를 막고 있나(간단 버전)
```sql
SELECT
  blocked.pid     AS blocked_pid,
  blocked.query   AS blocked_query,
  blocking.pid    AS blocking_pid,
  blocking.query  AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked ON blocked.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks
  ON blocking_locks.locktype = blocked_locks.locktype
 AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
 AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
 AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
 AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
 AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
 AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
 AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
 AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
 AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
JOIN pg_catalog.pg_stat_activity blocking ON blocking.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted AND blocking_locks.granted;
```

이 쿼리는 길어 보여도, “blocked vs blocking” 관계를 바로 보여줘서 유용합니다.

### 17.3 운영 감각
- “blocking 쿼리”는 보통 길게 잡힌 트랜잭션/배치/마이그레이션입니다.
- blocking을 죽이는 건 최후의 수단입니다.
- 근본 원인은 “트랜잭션 길이/락 범위/인덱스 부재로 인한 락 확장” 같은 구조적 문제인 경우가 많습니다.

---

## 18. 긴 트랜잭션이 만드는 2차 재앙: bloat, vacuum, idle in transaction

긴 트랜잭션은 단순히 “느리다”가 아닙니다.

### 18.1 MVCC 정리가 못 된다
- 오래된 스냅샷이 유지되면,
  DB는 “죽은 row 버전”을 정리(vacuum)하지 못합니다.
- 디스크가 늘고, 인덱스가 비대해지고, 성능이 계속 떨어질 수 있습니다.

### 18.2 idle in transaction은 진짜 위험
- 트랜잭션을 열어놓고 아무 쿼리도 안 하는 상태
- 락을 잡고 있을 수 있고, vacuum을 방해할 수 있음

관측:
```sql
SELECT pid, state, now() - xact_start AS xact_age, query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
ORDER BY xact_age DESC;
```

대응:
- 앱에서 트랜잭션 범위를 짧게
- DB 설정으로 `idle_in_transaction_session_timeout` 적용 고려(주의: 애플리케이션 영향 확인)

---

# Part 4. 무결성(Constraints)으로 문제를 ‘불가능하게’ 만들기

## 19. 무결성은 기능이다: DB 제약의 역할

실무에서 “정합성”은 선택이 아니라 생존입니다.

왜 DB 제약이 강한가?
- 코드 검증은 경로가 여러 개면 뚫립니다(배치, 어드민, 스크립트, 다른 서비스)
- DB 제약은 어떤 경로든 통과해야 하는 “마지막 관문”입니다.

원칙:
- “깨지면 안 되는 규칙”은 가능한 한 DB에 박아라.

---

## 20. UNIQUE/PK/FK/CHECK/NOT NULL: 언제 무엇을 쓰나

### 20.1 PK
- row 정체성
- 참조의 기준
- 보통 BIGINT/UUID

### 20.2 UNIQUE
- 중복 방지
- 멱등성의 핵심(34장)
- 자연키(이메일, provider_tx_id)에 자주 사용

### 20.3 CHECK
- 도메인 제한(상태, 음수 금지)
- 특히 수량/금액은 CHECK로 방어선 구축

### 20.4 NOT NULL
- 의미가 있는 값이면 NOT NULL
- “NULL은 값이 아니다”를 기억하세요
- (마이그레이션에서 NOT NULL 추가는 신중하게: 41장)

### 20.5 FK
- 정합성의 큰 축
- 다만 초대형/샤딩 환경에서는 운영 비용이 커질 수 있음
- 돈/정산 등 핵심 도메인에서는 FK가 대체로 이득

---

## 21. 부분 UNIQUE(Partial Unique)와 “활성 상태는 1개만”

실무에서 정말 자주 나오는 규칙:
- 유저당 활성 구독은 1개만
- 주문당 결제는 1개만(이건 그냥 UNIQUE로도 가능)
- 상품당 대표 이미지 1개만
- ACTIVE 쿠폰은 유저당 1개만

PostgreSQL의 부분 인덱스/부분 UNIQUE가 강력합니다.

예: 유저당 ACTIVE 쿠폰은 1개만

```sql
CREATE UNIQUE INDEX ux_coupon_one_active
ON coupons(user_id)
WHERE status = 'ACTIVE';
```

이제 동시성 경쟁이 있어도, DB가 마지막에 “불가능”하게 막아줍니다.

앱 로직은:
- INSERT 시도
- UNIQUE 위반이면 “이미 ACTIVE가 있다”고 처리

이렇게 하면 팬텀 같은 동시성 문제를 훨씬 단순하게 해결할 수 있습니다.

---

## 22. Exclusion constraint: 겹치면 안 되는 예약/스케줄 모델링

“시간 구간이 겹치면 안 된다”는 규칙은 흔합니다.
- 회의실 예약
- 좌석 예약(시간 단위)
- 렌탈 예약

PostgreSQL에서는 exclusion constraint를 이용할 수 있습니다(확장 필요).

### 준비(확장)
```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

예약 테이블:

```sql
DROP TABLE IF EXISTS reservations;
CREATE TABLE reservations (
  id BIGSERIAL PRIMARY KEY,
  room_id BIGINT NOT NULL,
  period tstzrange NOT NULL,
  EXCLUDE USING gist (
    room_id WITH =,
    period  WITH &&
  )
);
```

- `period WITH &&`는 “겹침(overlap)”을 의미합니다.
- 같은 room_id에 대해 period가 겹치면 삽입 자체가 거부됩니다.

이건 “코드로 겹침 검사”보다 훨씬 강합니다(동시성에서도 안전).

---

## 23. 돈/포인트/잔액: 원장(ledger)로 설계하는 이유

“현재 잔액”만 저장하면 위험합니다.
- 과거 거래를 재구성 불가
- 버그/운영 실수로 balance가 틀어지면 복구가 어려움

### 추천: 원장(append-only)
```sql
DROP TABLE IF EXISTS wallet_ledger;
CREATE TABLE wallet_ledger (
  id           BIGSERIAL PRIMARY KEY,
  wallet_id    BIGINT NOT NULL,
  amount_cents BIGINT NOT NULL, -- +적립, -사용
  reason       TEXT NOT NULL,
  ref_type     TEXT,
  ref_id       TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (wallet_id, ref_type, ref_id) -- 멱등성 장치(도메인에 맞게)
);

CREATE INDEX idx_ledger_wallet_time ON wallet_ledger(wallet_id, created_at DESC);
```

현재 잔액은:
- `SUM(amount_cents)`로 계산 가능하지만 느릴 수 있음
- 그래서 캐시/집계 테이블을 둘 수 있는데,
  그때도 “진실”은 원장입니다.

### 원장 설계에서 중요한 것
- 멱등성(같은 ref_id가 두 번 들어오지 않게 UNIQUE)
- 트랜잭션 안에서 원장 insert와 관련 상태 업데이트를 같이 커밋
- 정합성 검증 배치(원장 합과 집계 테이블 비교)

---

# Part 5. 인덱스와 쿼리 플랜: “추측” 대신 “근거”로 튜닝하기

## 24. 인덱스의 본질과 비용

인덱스는 “검색을 빠르게 하는 자료구조”입니다.  
하지만 공짜가 아닙니다.

- 읽기: 빨라질 수 있음
- 쓰기: 느려질 수 있음(인덱스도 갱신해야 하니까)
- 디스크: 늘어남
- vacuum/정리 비용도 영향

현실적인 질문:
- 이 쿼리는 얼마나 자주 실행되나?
- 데이터는 얼마나 커지나?
- 쓰기 비중은 얼마나 되나?
- 인덱스가 늘어나면 쓰기 TPS가 떨어질 수 있는가?

---

## 25. 복합 인덱스와 leftmost prefix

복합 인덱스 `(a, b, c)`는 대체로:
- a 조건에는 잘 탄다
- a,b 조건에도 잘 탄다
- b 단독 조건에는 못 타는 경우가 많다

### 예: orders(user_id, created_at DESC)
```sql
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
```

아래는 잘 탑니다:
```sql
SELECT * FROM orders
WHERE user_id = 10
ORDER BY created_at DESC
LIMIT 20;
```

하지만 아래는 애매/비효율일 수 있습니다:
```sql
SELECT * FROM orders
WHERE created_at >= now() - interval '1 day';
```

이 경우 created_at 단독 인덱스가 필요할 수 있습니다.

---

## 26. 부분/표현식/커버링 인덱스

### 26.1 부분 인덱스(Partial index)
특정 상태만 자주 조회될 때 효과적입니다.

예: status='CREATED'인 주문만 자주 조회(처리 큐)
```sql
CREATE INDEX idx_orders_created_only
ON orders(created_at)
WHERE status='CREATED';
```

### 26.2 표현식 인덱스(Expression index)
예: 대소문자 무시 검색
```sql
CREATE INDEX idx_products_lower_name ON products (lower(name));
```

쿼리:
```sql
SELECT * FROM products WHERE lower(name) = lower(:name);
```

### 26.3 커버링 인덱스(INCLUDE)
PostgreSQL:
```sql
CREATE INDEX idx_orders_user_created_cover
ON orders(user_id, created_at DESC)
INCLUDE (status, total_cents);
```

인덱스만으로 필요한 컬럼을 반환하면(Index Only Scan),
테이블 접근을 줄여 성능이 좋아질 수 있습니다.

주의:
- Index Only Scan은 visibility map 등 조건이 맞아야 잘 동작합니다.
- vacuum/통계와도 연관됩니다.

---

## 27. EXPLAIN(ANALYZE, BUFFERS) 읽기 튜토리얼

### 27.1 습관: 추측하지 말고 측정
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM orders
WHERE user_id = 10
ORDER BY created_at DESC
LIMIT 20;
```

### 27.2 꼭 보는 포인트
- Actual time: 실제 시간
- Rows: 실제 반환 row 수
- Buffers: shared hit/read 등(캐시 vs 디스크)
- 어떤 scan을 했나(Seq Scan vs Index Scan)
- 어디서 Sort가 발생하나

### 27.3 “느린 이유”의 흔한 패턴
- 인덱스가 없어서 Seq Scan
- 반환량이 너무 커서(필터가 약함)
- 조인 순서가 비효율(통계 문제 포함)
- 정렬/그룹바이가 큰 비용
- 파라미터 값에 따라 플랜이 나빠짐(prepare statement 이슈 등)

---

## 28. 조인 전략과 카디널리티(행 수 추정) 함정

조인 튜닝의 핵심은:
- 조인 키에 인덱스가 있는가
- 필터가 조인 전에 적용되는가
- 카디널리티 추정이 맞는가(통계)

주요 조인 방식:
- Nested loop: 작은 테이블 + 인덱스 있을 때 강함
- Hash join: 큰 테이블 조인에서 흔함(메모리)
- Merge join: 정렬된 입력에서 효율적

카디널리티가 틀리면:
- DB가 엉뚱한 조인 전략을 고를 수 있습니다.

대응:
- ANALYZE로 통계 갱신
- 조건/데이터 분포를 반영한 인덱스/부분 인덱스
- (Postgres) 확장 통계(extended statistics) 고려(심화)

---

## 29. 페이징: OFFSET이 느린 이유, Keyset pagination

OFFSET은 “앞을 버리기 위해서도 읽는다”는 점에서 느립니다.

### 29.1 OFFSET 예시(주의)
```sql
SELECT *
FROM orders
WHERE user_id = 10
ORDER BY created_at DESC
OFFSET 100000
LIMIT 20;
```

데이터가 커지면 선형으로 느려질 수 있습니다.

### 29.2 Keyset pagination(커서 기반)
```sql
SELECT *
FROM orders
WHERE user_id = :user_id
  AND (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

주의:
- 정렬 기준이 안정적이어야 합니다(동률 대비로 id 포함)
- UI에서 “전체 페이지 수” 계산이 어려울 수 있음

---

## 30. N+1은 DB 문제일까 앱 문제일까

N+1은 보통 ORM에서 나타나는 문제지만,
근본은 DB 부하(쿼리 수 폭증)입니다.

해결 전략:
- 조인/프리로드(한 번에 가져오기)
- batch 조회(IN 절)
- 캐시
- 데이터 모델 재구성(반정규화 포함)

중요:
- “쿼리 1개”가 느린 문제와 “쿼리 1000개”가 느린 문제는 해결 방법이 다릅니다.
- 관측(쿼리 수, 평균/상위 퍼센타일, slow query) 없이 감으로 고치면 악화할 수 있습니다.

---

# Part 6. 실무 패턴: 중복 요청, 원자성, 이벤트, 배치

## 31. 원자적 업데이트(Atomic update) 패턴

동시성 이슈의 상당수는 이 패턴 하나로 정리됩니다.

### 31.1 재고 차감(oversell 방지)
```sql
UPDATE stock_items
SET available_qty = available_qty - :qty,
    updated_at = now()
WHERE product_id = :pid
  AND available_qty >= :qty;
```

- 성공: row count = 1
- 실패: row count = 0 (재고 부족 또는 동시 경쟁)

### 31.2 상태 전이(중복 처리 방지)
```sql
UPDATE orders
SET status='PAID'
WHERE id=:order_id AND status='CREATED';
```

- 성공: row count = 1
- 실패: row count = 0 (이미 PAID/CANCELED 등)

### 31.3 조건부 업데이트는 “락보다 싸다”
- 불필요한 락을 오래 잡지 않는다
- 비즈니스 규칙을 SQL 조건으로 표현한다
- 결과를 row count로 해석 가능

---

## 32. 낙관적 락 vs 비관적 락: 선택 기준과 구현

### 32.1 낙관적 락(Optimistic)
전제: 충돌이 드물다. 실패하면 재시도한다.

스키마:
```sql
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
```

업데이트:
```sql
-- 읽기
SELECT balance, version FROM accounts WHERE owner='alice';

-- 업데이트(버전 조건)
UPDATE accounts
SET balance = balance - 100,
    version = version + 1
WHERE owner='alice' AND version = :old_version;
```

row count = 0이면 누군가 먼저 업데이트 -> 재시도.

장점:
- 락 대기 감소
- 읽기 많은 환경에서 유리

단점:
- 재시도 로직 필요
- 충돌이 많으면 재시도 폭주

### 32.2 비관적 락(Pessimistic)
전제: 충돌이 잦다. 확실히 잠그고 처리한다.

```sql
BEGIN;
SELECT balance FROM accounts WHERE owner='alice' FOR UPDATE;
UPDATE accounts SET balance = balance - 100 WHERE owner='alice';
COMMIT;
```

장점:
- 논리가 단순해질 수 있음
- 충돌이 잦을 때 예측 가능

단점:
- 락 대기 증가
- 트랜잭션이 길어지면 장애로 확대

---

## 33. UPSERT(ON CONFLICT): 중복 삽입을 안전하게

### 33.1 단순 upsert
```sql
INSERT INTO payments(order_id, provider, provider_tx_id, status)
VALUES (:order_id, :provider, :tx_id, 'PENDING')
ON CONFLICT (order_id)
DO UPDATE SET status = EXCLUDED.status
RETURNING id, status;
```

- UNIQUE(또는 PK) 충돌을 DB가 처리
- 멱등성 구현에 유용

### 33.2 “중복이면 아무것도 안 함”
```sql
INSERT INTO idempotency_keys(key, request_hash, status)
VALUES (:key, :hash, 'IN_PROGRESS')
ON CONFLICT (key) DO NOTHING;
```

row count를 보고 분기합니다.

---

## 34. Idempotency key: 결제/주문 중복을 끝내는 법

네트워크/클라이언트/서버는 재시도합니다.
- 타임아웃은 실패가 아니라 “모르겠다”입니다.
- 그래서 “같은 요청”이 다시 올 수 있다고 가정해야 합니다.

### 34.1 테이블
```sql
DROP TABLE IF EXISTS idempotency_keys;
CREATE TABLE idempotency_keys (
  key           TEXT PRIMARY KEY,
  request_hash  TEXT NOT NULL,
  response_body JSONB,
  status        TEXT NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 34.2 처리 흐름(권장)
1) 요청이 들어오면 idempotency key로 선점 시도
2) 이미 있으면:
   - COMPLETED: 저장된 응답 그대로 반환
   - IN_PROGRESS: “처리 중” 응답(혹은 폴링 유도)
3) 처음이면:
   - 트랜잭션 수행
   - 결과 저장(COMPLETED)

### 34.3 request_hash는 왜 있나
- 같은 key로 다른 요청을 보내는 공격/버그를 감지하기 위해
- 예: key는 같지만 amount가 다름 -> 위험

---

## 35. Outbox 패턴: DB 커밋과 메시지 발행 연결

### 문제
- DB 커밋은 성공했는데 메시지 발행이 실패 -> 다운스트림이 모름
- 메시지 발행은 성공했는데 DB 롤백 -> 유령 이벤트

### 해결
- 트랜잭션 안에서 outbox 테이블에 이벤트를 기록
- 별도 워커가 outbox를 읽어 발행하고 published_at 표시

스키마:
```sql
DROP TABLE IF EXISTS outbox_events;
CREATE TABLE outbox_events (
  id            BIGSERIAL PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id   TEXT NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  published_at   TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished
ON outbox_events(created_at)
WHERE published_at IS NULL;
```

워커 패턴(중복 처리 대비):
- outbox 가져올 때도 경쟁이 있으니 “작업 예약”을 위한 락이 필요할 수 있습니다.
- 흔한 방식:
  - `SELECT ... FOR UPDATE SKIP LOCKED`로 아직 처리 안 된 이벤트를 가져오기

예:
```sql
BEGIN;
SELECT id, payload
FROM outbox_events
WHERE published_at IS NULL
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 100;
-- 발행 성공한 것만 업데이트
UPDATE outbox_events SET published_at = now() WHERE id = :id;
COMMIT;
```

---

## 36. 배치/잡에서의 트랜잭션: chunking, lock, retry

배치는 “대량 데이터 + 긴 시간” 때문에 트랜잭션 문제를 더 잘 일으킵니다.

### 36.1 chunking 원칙
- 한번에 10만 건을 한 트랜잭션으로 처리하지 말고,
  500-5000건 단위로 쪼개서 커밋합니다(도메인에 따라 조절).

### 36.2 SELECT FOR UPDATE SKIP LOCKED로 분산 처리
작업 큐 테이블이 있다면 여러 워커가 경쟁하면서도 충돌을 줄일 수 있습니다.

```sql
BEGIN;
SELECT id
FROM jobs
WHERE status='READY'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 100;

UPDATE jobs SET status='RUNNING' WHERE id IN (...);
COMMIT;
```

주의:
- “작업 순서 보장”이 중요하면 다른 전략이 필요할 수 있습니다.

### 36.3 배치 실패 시 재시도
- 재시도는 “멱등성 + 관측성”이 있어야 안전합니다.

---

## 37. 애드바이저리 락(Advisory lock): 앱 수준 락을 DB로

“특정 키에 대해 동시에 하나만 수행” 같은 요구가 있습니다.
- 유저별 포인트 정산
- 특정 상품의 가격 계산 배치
- 특정 레포트 생성

DB에 애드바이저리 락을 걸면,
분산 환경에서도 “하나만 실행”을 만들 수 있습니다.

PostgreSQL:
```sql
-- 세션 단위 락 획득(예: user_id 기반)
SELECT pg_advisory_lock(12345);

-- 작업 수행

-- 해제
SELECT pg_advisory_unlock(12345);
```

주의:
- 잘못 쓰면 “영원히 락 잡음”이 생길 수 있으니,
  트랜잭션 락(`pg_advisory_xact_lock`)도 고려합니다(트랜잭션 종료 시 자동 해제).

---

# Part 7. 운영과 마이그레이션: 사고가 가장 많이 나는 곳

## 38. 커넥션 풀: 병목의 시작점

실무에서 흔한 장애:
- DB CPU는 여유가 있는데 서비스가 느림 -> 풀 대기
- DB max_connections 초과 -> 연결 실패 폭발
- 커넥션이 고갈 -> 타임아웃 연쇄

### 38.1 풀 크기 감각
- 앱 인스턴스 수 * 풀 크기 = DB가 감당해야 하는 커넥션 수
- DB 커넥션은 생각보다 비쌉니다(메모리/컨텍스트/캐시)
- 무조건 크게 하면 좋아지지 않습니다.

### 38.2 관측 포인트
- 풀 대기 시간
- active vs idle 커넥션 수
- DB의 `pg_stat_activity`에서 동시에 active 쿼리 수
- lock wait 증가 여부

---

## 39. 타임아웃/재시도/서킷브레이커: DB 관점

### 39.1 타임아웃 계층 정렬
- 앱 타임아웃(HTTP) < DB statement_timeout < DB lock_timeout 같은 식으로
  일관된 정책이 필요합니다.
- 앱이 먼저 타임아웃 나면, DB는 계속 일하고 있을 수 있습니다(유령 작업).

PostgreSQL 예:
```sql
SET statement_timeout = '2s';
SET lock_timeout = '200ms';
```

### 39.2 재시도 함정
- 읽기 재시도는 비교적 안전
- 쓰기 재시도는 멱등성이 없으면 데이터 중복/2번 반영 위험
- 데드락/serialization failure는 재시도 대상이 될 수 있음

---

## 40. 슬로우 쿼리 관측: pg_stat_statements, 로그

슬로우 쿼리를 “추측”으로 고치지 마세요.

### 40.1 pg_stat_statements(가능하면 켜기)
확장:
```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

대표 조회:
```sql
SELECT query, calls, mean_exec_time, total_exec_time, rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

(운영에서는 권한/설정이 필요할 수 있습니다.)

### 40.2 slow query 로그
- log_min_duration_statement 설정으로 특정 시간 이상 쿼리 로깅
- 인덱스 누락/풀스캔을 빠르게 발견

---

## 41. 무중단 마이그레이션 플레이북

마이그레이션은 “기능 개발”보다 더 자주 사고를 냅니다.

### 41.1 기본 원칙: Additive -> Backfill -> Switch -> Cleanup
1) add column/table/index 같은 “추가”부터(기존 코드 깨지지 않게)
2) backfill(데이터 채우기) - 작은 배치로
3) 애플리케이션 전환(dual write/read)
4) 정리(drop column/index 등)

### 41.2 NOT NULL 추가(위험)
대규모 테이블에서 바로 NOT NULL + DEFAULT는 락/리라이트로 위험할 수 있습니다.

권장 패턴(개념):
1) nullable 컬럼 추가(기본값 없이)
2) 코드 배포: 새 컬럼 쓰기 시작
3) 배치로 backfill
4) constraint validation(가능한 경우) 후 NOT NULL 적용

### 41.3 인덱스 생성
PostgreSQL:
- `CREATE INDEX CONCURRENTLY` 고려(긴 락 방지)
- 단, 트랜잭션 안에서 실행 불가
- 실패 시 잔여 인덱스 정리 필요

### 41.4 컬럼 rename(주의)
- 코드/쿼리/리포트/뷰 등 영향 범위가 큼
- 가능하면 새 컬럼 추가 + 이관 후 삭제가 더 안전한 경우가 많음

---

## 42. 복제/리드 레플리카: 일관성 함정

### 문제: read-after-write 깨짐
- 유저가 주문 생성 -> 바로 주문 조회
- 조회가 레플리카로 가면 아직 반영 안 돼서 “주문 없음”이 뜰 수 있음

### 대응 전략
- 중요한 read-after-write는 primary로 라우팅
- 최근 write 후 일정 시간 primary에서 읽기(세션/쿠키 기반)
- 레플리카 lag 지표 관측, 임계치 초과 시 자동 우회

---

## 43. 장애 대응 시나리오: “락 대기 폭발”과 “커넥션 고갈”

### 43.1 락 대기 폭발(전형적 시나리오)
- 긴 트랜잭션이 테이블/행 락을 오래 잡음
- 뒤의 요청들이 줄줄이 대기
- 커넥션이 다 대기 -> 풀 고갈
- 서비스 전체 타임아웃

즉시 대응:
- blocking 쿼리 찾기(17장)
- 정말 필요하면 해당 트랜잭션을 중단(최후)
- 이후 근본 해결:
  - 트랜잭션 범위 축소
  - 인덱스 추가로 락 범위/시간 감소
  - 배치 chunking
  - lock_timeout/statement_timeout

### 43.2 커넥션 고갈
- 앱 인스턴스 증가 + 풀 크기 증가 + 슬로우 쿼리
- DB max_connections 초과

즉시 대응:
- 풀 크기/인스턴스 수 조절
- 슬로우 쿼리/락 대기 원인 제거
- pgbouncer 같은 중간 풀링 고려(환경에 따라)

---

# Part 8. 케이스 스터디: 실무 3대 문제

## 44. 케이스 1: 재고 oversell을 막는 3가지 설계

상황:
- product_id=1 재고 1개
- 동시에 2명이 주문하면 2개 팔리면 안 됨

### 설계 A: 원자적 업데이트(추천)
```sql
UPDATE stock_items
SET available_qty = available_qty - 1
WHERE product_id=1 AND available_qty >= 1;
```

- row count가 1이면 성공
- 0이면 실패(품절)

장점:
- 단순, 빠름
- 락/대기 최소화

단점:
- “재고 예약” 같은 복잡한 흐름에는 추가 모델 필요(예약 테이블 등)

### 설계 B: SELECT FOR UPDATE(비관적)
```sql
BEGIN;
SELECT available_qty FROM stock_items WHERE product_id=1 FOR UPDATE;
-- available_qty 확인
UPDATE stock_items SET available_qty = available_qty - 1 WHERE product_id=1;
COMMIT;
```

장점:
- 로직이 직관적

단점:
- 경쟁이 크면 대기 증가
- 트랜잭션이 길어지면 위험

### 설계 C: SERIALIZABLE + 재시도
- 복잡한 규칙을 직렬 실행처럼 보장하고 싶을 때
- 대신 실패/재시도 설계를 필수로 포함

결론:
- 대부분은 A로 충분합니다.
- 도메인이 복잡하면 A + 예약 모델 + 만료 처리 같은 방식으로 확장합니다.

---

## 45. 케이스 2: 결제 중복과 정확히 한 번 처리

문제:
- 결제 승인 요청이 타임아웃 -> 클라이언트 재시도
- 서버도 재시도할 수 있음
- 결제는 중복으로 반영되면 안 됨

핵심 도구 3개:
1) 조건부 상태 전이(31장)
2) UNIQUE 제약(provider, provider_tx_id) 또는 idempotency key(34장)
3) outbox로 후처리 연결(35장)

추천 흐름(요약):
- 결제 요청마다 idempotency key를 받는다
- idempotency 키로 선점
- 트랜잭션에서:
  - 주문 상태 CREATED -> PAID 조건부 업데이트
  - payments upsert/confirm
  - outbox 기록
- 응답 저장 후 반환

---

## 46. 케이스 3: 좌석/예약 시스템(겹침 금지)

요구:
- 같은 좌석/방/자원에 대해 같은 시간 구간이 겹치면 안 됨
- 동시에 2명이 예약하면 한 명만 성공해야 함

해결:
- 코드로 “겹침 검사”를 하면 동시성에서 깨집니다.
- PostgreSQL의 exclusion constraint(22장)로 “불가능하게” 만들 수 있습니다.

대안(MySQL 등):
- 시간 단위로 slot을 쪼개고 UNIQUE로 막는 방식
- 혹은 SERIALIZABLE/락 전략 등 도메인 맞춤 설계 필요

---

# Appendices

## A. 실무 체크리스트

### 트랜잭션/동시성
- [ ] 트랜잭션 경계가 짧은가?
- [ ] 외부 API 호출이 트랜잭션 안에 들어가 있지 않은가?
- [ ] read-modify-write가 원자적 업데이트 또는 락으로 보호되는가?
- [ ] 조건부 UPDATE(row count)로 중복 처리를 막는가?
- [ ] 데드락/직렬화 실패 재시도 정책이 있는가(멱등성 포함)?
- [ ] 락 획득 순서가 일관적인가?

### 스키마/무결성
- [ ] 중요한 중복은 UNIQUE로 막는가?
- [ ] 음수/도메인 값은 CHECK로 막는가?
- [ ] 핵심 도메인(돈/주문/정산)에 FK/제약이 빠져 있지 않은가?
- [ ] 상태 전이가 문서화/테스트되어 있는가?

### 성능
- [ ] 자주 쓰는 WHERE/ORDER BY 조합에 인덱스가 있는가?
- [ ] OFFSET pagination을 대량 데이터에 쓰지 않는가?
- [ ] 느린 쿼리는 EXPLAIN(ANALYZE)로 확인했는가?
- [ ] 쿼리 수(N+1) 폭증이 없는가?

### 운영
- [ ] 커넥션 풀 대기/락 대기/슬로우쿼리를 관측하는가?
- [ ] 마이그레이션이 무중단 플레이북을 따르는가?
- [ ] 레플리카 lag로 인한 일관성 문제를 고려하는가?

---

## B. 자주 하는 실수 50

1. 돈을 float/double로 저장  
2. 잔액/수량을 “읽고 계산해서 덮어쓰기”(lost update)  
3. UNIQUE 없이 코드로만 중복 방지  
4. 외부 API 호출을 트랜잭션 안에서 수행  
5. 멱등성 없이 재시도  
6. 긴 트랜잭션(배치에서 BEGIN 후 수만 건 처리)  
7. idle in transaction 방치  
8. 인덱스 없이 대량 테이블에서 LIKE '%keyword%'  
9. OFFSET pagination을 무한스크롤에 사용  
10. 타임존 저장/표시 혼동  
11. 레플리카 읽기에서 read-after-write 깨짐을 모름  
12. 마이그레이션에서 NOT NULL + DEFAULT를 한번에 적용  
13. 트랜잭션에서 너무 많은 row를 FOR UPDATE로 잠금  
14. 데드락을 “예외 상황”으로만 보고 재시도 설계를 안 함  
15. lock_timeout/statement_timeout이 없어 무한 대기  
16. 커넥션 풀 크기를 무작정 키움  
17. 인덱스가 많아져서 쓰기 성능이 떨어짐을 모름  
18. EXPLAIN 없이 인덱스를 추가/삭제  
19. 상태 전이를 그냥 문자열로 두고 제약/테스트 없음  
20. NULL 의미를 명확히 정의하지 않음  
21. “soft delete”를 했는데 모든 쿼리에서 조건을 빠뜨림  
22. 대량 UPDATE가 테이블 전체를 오래 잠금  
23. 작업 큐에서 여러 워커가 같은 job을 집는 중복 처리  
24. outbox 없이 “커밋 후 메시지 발행”으로 유령 이벤트  
25. 중복 이벤트 소비에 대비하지 않음  
26. 파티셔닝 없이 시간이 지날수록 테이블이 무한 성장  
27. 오래된 통계로 카디널리티 추정이 계속 틀림  
28. prepared statement로 특정 파라미터에서 플랜이 망가짐을 모름  
29. 큰 JSON 컬럼을 무분별하게 조회  
30. select * 남발로 네트워크/IO 낭비  
31. 트랜잭션에서 순서가 달라 데드락 빈발  
32. FK 삭제/업데이트 cascade를 무심코 사용해 폭발적 락  
33. “읽기만”이라고 생각한 쿼리가 사실은 락을 잡음(FOR UPDATE)  
34. 배치 backfill을 피크타임에 수행  
35. 인덱스 생성/리빌드를 피크타임에 수행  
36. 로그에 개인정보/결제정보를 그대로 남김  
37. 쿼리 타임아웃이 없어서 커넥션이 묶임  
38. 트랜잭션 재시도 시 중복 부작용(외부 API, 이메일)이 발생  
39. 이벤트 처리에서 at-least-once를 exactly-once로 착각  
40. 레플리카 라우팅을 단순히 “읽기=레플리카”로만 처리  
41. monotonic id를 노출해 enumeration 공격 가능  
42. 시퀀스 gap을 “결번”이라고 오해(정상)  
43. autovacuum를 꺼서 성능이 장기적으로 악화  
44. 대량 DELETE로 테이블 bloat 폭발(파티션/아카이빙 고려)  
45. “정확히 한 번”이 필요한데 DB 제약 없이 앱락만 사용  
46. 락 대기/슬로우 쿼리 알람이 없음  
47. 마이그레이션 롤백 전략이 없음  
48. 데이터 수정 스크립트를 리뷰/테스트 없이 실행  
49. 운영자가 직접 UPDATE하다가 제약이 없어 데이터 꼬임  
50. “DB는 알아서 해준다”라고 믿고 설계를 안 함

---

## C. 면접/과제 대비 질문 40

1. ACID를 각각 “어떤 버그를 막는가” 관점으로 설명해보세요.  
2. READ COMMITTED에서 non-repeatable read가 가능한 이유는?  
3. phantom read가 실무에서 문제가 되는 사례 2개를 말해보세요.  
4. lost update는 어떤 코드 패턴에서 발생하나요?  
5. 원자적 업데이트가 lost update를 막는 이유는?  
6. SELECT FOR UPDATE는 무엇을 막고, 무엇을 막지 못하나요?  
7. write skew를 예로 설명하고, 해결책 2가지를 말해보세요.  
8. SERIALIZABLE에서 왜 재시도가 필요할 수 있나요?  
9. 데드락이 왜 생기고, 예방 전략은 무엇인가요?  
10. 트랜잭션을 길게 잡으면 어떤 운영 문제가 생기나요?  
11. partial unique index가 유용한 케이스를 말해보세요.  
12. exclusion constraint를 예약 시스템에 어떻게 쓰나요?  
13. 인덱스가 많으면 왜 쓰기 성능이 떨어질 수 있나요?  
14. 복합 인덱스의 leftmost prefix 규칙을 설명해보세요.  
15. OFFSET pagination이 느린 이유는?  
16. keyset pagination의 장단점은?  
17. EXPLAIN에서 Seq Scan이 나오는 이유를 3가지 말해보세요.  
18. Hash Join과 Nested Loop Join이 선택되는 상황 차이는?  
19. 카디널리티 추정이 틀리면 어떤 문제가 생기나요?  
20. 커넥션 풀 고갈이 생기는 이유 3가지는?  
21. statement_timeout과 lock_timeout은 왜 필요한가요?  
22. 쓰기 재시도에서 멱등성이 왜 중요한가요?  
23. outbox 패턴이 해결하는 문제가 무엇인가요?  
24. 이벤트가 중복 소비될 때 안전하게 처리하는 방법은?  
25. read-after-write 일관성이 레플리카에서 깨지는 이유는?  
26. 마이그레이션에서 NOT NULL을 안전하게 추가하는 순서는?  
27. CREATE INDEX CONCURRENTLY의 목적과 주의점은?  
28. idle in transaction은 왜 위험한가요?  
29. pg_locks/pg_stat_activity로 blocking을 찾는 방법은?  
30. 원장(ledger) 설계가 balance 테이블보다 좋은 점은?  
31. FK를 무조건 켜면 안 되는 경우가 있을까요?  
32. soft delete의 함정은 무엇인가요?  
33. 대량 DELETE가 위험한 이유는?  
34. 트랜잭션 경계를 잘못 잡으면 어떤 장애로 이어지나요?  
35. 상태 전이를 조건부 업데이트로 처리하는 장점은?  
36. 애드바이저리 락을 언제 쓰나요?  
37. 작업 큐에서 SKIP LOCKED를 쓰는 이유는?  
38. 높은 격리수준이 항상 좋은 선택이 아닌 이유는?  
39. 인덱스 튜닝에서 “추측”이 위험한 이유는?  
40. 실무에서 DB 실력을 빠르게 올리는 학습 루틴을 제안해보세요.

---

## D. 실습 SQL 전체 스크립트

이 문서 실습용 테이블/데이터를 한 번에 만들고 싶으면 아래를 실행하세요.

```sql
-- products, stock, orders, payments
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS stock_items;
DROP TABLE IF EXISTS products;

CREATE TABLE products (
  id            BIGSERIAL PRIMARY KEY,
  sku           TEXT NOT NULL UNIQUE,
  name          TEXT NOT NULL,
  price_cents   BIGINT NOT NULL CHECK (price_cents >= 0)
);

CREATE TABLE stock_items (
  product_id    BIGINT PRIMARY KEY REFERENCES products(id),
  available_qty BIGINT NOT NULL CHECK (available_qty >= 0),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('CREATED','PAID','CANCELED')),
  total_cents   BIGINT NOT NULL CHECK (total_cents >= 0),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
  order_id      BIGINT NOT NULL REFERENCES orders(id),
  product_id    BIGINT NOT NULL REFERENCES products(id),
  qty           BIGINT NOT NULL CHECK (qty > 0),
  price_cents   BIGINT NOT NULL CHECK (price_cents >= 0),
  PRIMARY KEY (order_id, product_id)
);

CREATE TABLE payments (
  id               BIGSERIAL PRIMARY KEY,
  order_id         BIGINT NOT NULL UNIQUE REFERENCES orders(id),
  provider         TEXT NOT NULL,
  provider_tx_id   TEXT NOT NULL,
  status           TEXT NOT NULL CHECK (status IN ('PENDING','CAPTURED','FAILED')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_tx_id)
);

CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);

INSERT INTO products(sku, name, price_cents) VALUES
('SKU-001', 'Keyboard', 5000),
('SKU-002', 'Mouse', 3000);

INSERT INTO stock_items(product_id, available_qty)
SELECT id, 10 FROM products;

-- accounts
DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
  id      BIGSERIAL PRIMARY KEY,
  owner   TEXT NOT NULL UNIQUE,
  balance BIGINT NOT NULL CHECK (balance >= 0),
  version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO accounts(owner, balance) VALUES ('alice', 1000), ('bob', 1000);

-- coupons
DROP TABLE IF EXISTS coupons;
CREATE TABLE coupons (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','USED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO coupons(user_id, status) VALUES (1, 'ACTIVE');

-- oncall
DROP TABLE IF EXISTS oncall;
CREATE TABLE oncall (
  doctor TEXT PRIMARY KEY,
  active BOOLEAN NOT NULL
);
INSERT INTO oncall(doctor, active) VALUES ('kim', true), ('lee', true);

-- deadlock
DROP TABLE IF EXISTS t_deadlock;
CREATE TABLE t_deadlock (
  id BIGINT PRIMARY KEY,
  v  BIGINT NOT NULL
);
INSERT INTO t_deadlock(id, v) VALUES (1, 10), (2, 20);

-- idempotency keys
DROP TABLE IF EXISTS idempotency_keys;
CREATE TABLE idempotency_keys (
  key           TEXT PRIMARY KEY,
  request_hash  TEXT NOT NULL,
  response_body JSONB,
  status        TEXT NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- outbox
DROP TABLE IF EXISTS outbox_events;
CREATE TABLE outbox_events (
  id            BIGSERIAL PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id   TEXT NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  published_at   TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_unpublished
ON outbox_events(created_at)
WHERE published_at IS NULL;
```

---

## E. PostgreSQL vs MySQL 차이 요약

이 부록은 “둘 다 써야 하는 사람”을 위한 빠른 감각입니다.

### 격리수준 기본값
- PostgreSQL: 기본 READ COMMITTED
- MySQL(InnoDB): 기본 REPEATABLE READ인 환경이 많음(설정 가능)

### MVCC와 락의 느낌
- PostgreSQL: 읽기-쓰기 충돌이 적은 편, 대신 vacuum/스냅샷 관리 중요
- MySQL(InnoDB): gap lock/next-key lock로 범위 경쟁에서 더 많이 막힐 수 있음

### upsert 문법
- PostgreSQL: INSERT ... ON CONFLICT
- MySQL: INSERT ... ON DUPLICATE KEY UPDATE

### 예약/겹침 금지
- PostgreSQL: exclusion constraint 같은 강력한 도구가 있음
- MySQL: 보통 다른 모델링(슬롯화+UNIQUE 등)로 해결

---

## F. “내가 지금 무엇을 공부해야 하지?” 4주 학습 플랜

### 1주차: 트랜잭션/이상현상 재현
- non-repeatable, phantom, lost update 실습(7-9장)
- 데드락 실습(11장)
- 각 실습에 대해 “왜 발생했는지” 5줄로 정리

### 2주차: 해결 도구 4종 습득
- 원자적 업데이트(31장)
- 조건부 상태 전이(31장)
- UNIQUE/부분 UNIQUE(21장)
- FOR UPDATE의 올바른 사용(16장)

### 3주차: 플랜 읽기 습관
- 자주 쓰는 쿼리 5개를 EXPLAIN(ANALYZE)로 보기(27장)
- 인덱스 2개 추가/삭제를 실험하고, 플랜 변화 기록

### 4주차: 운영/마이그레이션 감각
- pg_stat_activity/pg_locks로 blocking 찾기(17장)
- 무중단 마이그레이션 시나리오 2개를 글로 써보기(41장)
- 커넥션 풀 고갈 시나리오를 “원인 -> 관측 -> 대응”으로 정리(38,43장)

---

## 끝맺음

이 문서의 핵심 메시지는 한 줄입니다.

> **“동시성/무결성 문제는 ‘코드’보다 먼저 ‘DB 설계’로 막아라.”**

원하면, 네가 쓰는 스택(Spring/JPA, Node/Nest, Django/FastAPI, Go 등) 기준으로:
- 위 패턴을 실제 코드(트랜잭션 어노테이션/리포지토리/쿼리)로 옮긴 예제,
- 그리고 네가 겪는 실제 이슈(락 대기, 데드락, 느린 쿼리)를 기준으로
“회사 코드에 바로 적용 가능한 체크리스트/리팩터링 플랜”도 같이 만들어줄게.


---

# Part 9. 심화: 트랜잭션과 락을 “시스템”으로 다루기

이 파트는 “개념은 아는데 현장에서 막힐 때”를 위한 심화입니다.  
특히 **설정(timeout), 관측(뷰/지표), 실패 처리(retry)**를 시스템적으로 다룹니다.

## 47. 트랜잭션/락 관련 설정: timeout 3종 세트와 실무 기준

PostgreSQL에서 실무적으로 자주 만나는 타임아웃은 다음 3개입니다.

- `statement_timeout`: 쿼리(문장) 실행 시간이 너무 길면 중단
- `lock_timeout`: 락을 기다리는 시간이 너무 길면 중단
- `idle_in_transaction_session_timeout`: 트랜잭션을 열어두고 아무것도 안 하면 중단

### 47.1 왜 “락 대기”와 “쿼리 실행”은 분리해야 하나
슬로우는 크게 두 종류입니다.

1) **실행이 느림**: 인덱스 부재, 조인/정렬 비용, IO 등  
2) **기다림이 길다**: 락 대기, 커넥션 풀 대기

여기서 2)를 1)로 착각하면, 인덱스만 계속 추가하다가 더 망가질 수 있습니다.  
그래서 `lock_timeout`과 관측이 중요합니다.

### 47.2 세션 단위로 실험해보기
psql에서:

```sql
SHOW statement_timeout;
SHOW lock_timeout;
SHOW idle_in_transaction_session_timeout;

SET statement_timeout = '2s';
SET lock_timeout = '200ms';
```

- `statement_timeout`은 “쿼리 자체가 오래 걸릴 때” 끊습니다.
- `lock_timeout`은 “락 대기 때문에 대기열이 길어질 때” 끊습니다.

### 47.3 실무 권장값(정답은 없고 기준만)
- API 요청(동기): statement_timeout 1-3초, lock_timeout 100-300ms (도메인/트래픽 따라 조정)
- 배치/리포트: 별도 워크로드로 분리하고 더 긴 timeout 허용(단, 피크타임 분리)
- idle_in_transaction_session_timeout: 수십 초~수분 설정을 고려(프레임워크/트랜잭션 패턴 확인 필수)

핵심:
- 타임아웃은 “장애를 예방”하는 안전장치이지, 성능 튜닝의 대체재가 아닙니다.
- 설정 후 반드시:
  - 에러율 증가 여부
  - 재시도 폭주 여부
  - 특정 기능 실패 빈도
를 관측해야 합니다.

---

## 48. PostgreSQL에서 “어떤 쿼리가 어떤 락을 잡나” 감각 만들기

### 48.1 꼭 알아야 하는 6가지 사실
1) UPDATE/DELETE는 대상 row에 행 락을 잡는다  
2) SELECT는 보통 락을 거의 안 잡는다(MVCC)  
3) SELECT FOR UPDATE는 “잠그는 읽기”라서 다른 UPDATE를 막는다  
4) DDL(ALTER TABLE 등)은 강한 테이블 락을 잡을 수 있다  
5) 락은 “순서”가 중요하다(순서가 다르면 데드락 가능)  
6) 인덱스가 없으면 UPDATE가 “더 많은 row를 만지려다” 락 범위가 커질 수 있다

### 48.2 DDL 락 체감 실습(주의: 실습 DB에서만)
테이블이 크지 않아서 잘 안 느껴질 수 있지만, 패턴을 익히는 게 목적입니다.

세션 A:
```sql
BEGIN;
-- 일부 DDL은 트랜잭션과 함께 락을 잡을 수 있음(종류에 따라 다름)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS memo TEXT;
-- 커밋 대기
```

세션 B:
```sql
-- 같은 테이블에 다른 작업 시도
SELECT count(*) FROM orders;
```

DB/명령 종류에 따라 대기/충돌이 달라질 수 있습니다.  
포인트는 “DDL은 잠깐이라고 생각하면 안 된다”입니다.

### 48.3 락을 “의도”로 설계하기
- 단순 조회: 락이 필요 없는 구조로 만들기(제약/원자적 업데이트)
- 동시성 경쟁이 있는 변경: 락을 최소 범위로, 최소 시간으로

---

## 49. SERIALIZABLE failure를 재현하고 “안전한 재시도” 만들기

SERIALIZABLE을 도입할 때 가장 중요한 건 **재시도 설계**입니다.  
(실무에서 “가끔 실패한다”는 건 정상일 수 있습니다.)

### 49.1 재현: 직렬화 실패(환경에 따라 다를 수 있음)
완벽히 동일하게 재현되지 않을 수 있지만, 감각을 잡는 예시입니다.

준비:
```sql
DROP TABLE IF EXISTS ssi_demo;
CREATE TABLE ssi_demo (
  k INT PRIMARY KEY,
  v INT NOT NULL
);
INSERT INTO ssi_demo(k, v) VALUES (1, 0), (2, 0);
```

아이디어:
- 두 트랜잭션이 각각 다른 row를 업데이트하지만,
  “전체 합은 0이어야 한다” 같은 규칙을 깨는 형태로 충돌을 유도합니다.

세션 A:
```sql
BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT sum(v) FROM ssi_demo; -- 0
UPDATE ssi_demo SET v = v + 1 WHERE k = 1;
-- 커밋 대기
```

세션 B:
```sql
BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT sum(v) FROM ssi_demo; -- 0
UPDATE ssi_demo SET v = v + 1 WHERE k = 2;
COMMIT;
```

세션 A:
```sql
COMMIT; -- 여기서 serialization failure가 발생할 수 있음
```

### 49.2 실무 전략: “재시도 가능한 단위”로 묶기
재시도 단위는 “한 번 더 해도 안전한 작업”이어야 합니다.

- DB 트랜잭션 안에서만 재시도(외부 부작용 제거)
- idempotency key/unique constraint로 중복 반영 방지
- outbox로 외부 부작용 분리

### 49.3 재시도에서 흔한 함정
- 같은 트랜잭션 안에서 외부 API 호출을 했는데, 재시도하면서 외부도 재호출됨
- 재시도 간격 없이 즉시 재시도로 “떼폭주” 발생
- 재시도 대상 오류를 너무 넓게 잡아 실제 버그를 숨김

---

## 50. 트랜잭션 디버깅 워크플로우: “어느 단계에서 막히는가” 5분 컷

현장에서 가장 흔한 상황:
- “요청이 느려요”
- “DB가 병목 같아요”
- “락 때문인가요?”

아래 순서로 보면 보통 5-10분 안에 방향이 잡힙니다.

### 50.1 1단계: 앱이 느린가, DB가 느린가
- 앱의 p95/p99 지연
- DB 쿼리 p95/p99 지연
- 커넥션 풀 대기 시간

풀 대기가 크면 DB 쿼리 튜닝을 해도 체감이 안 날 수 있습니다.

### 50.2 2단계: DB에서 기다리는가(락/IO) 실행하는가
`pg_stat_activity`의 `wait_event_type` 확인:
- Lock이면 락 대기
- IO이면 디스크/스토리지
- Client이면 클라이언트가 데이터를 안 읽는 경우(대량 결과 전송 등)

### 50.3 3단계: 누가 막는가(blocking)
17장의 blocking/blocked 쿼리로 찾습니다.

### 50.4 4단계: 왜 오래 잡고 있나(긴 트랜잭션)
- 트랜잭션이 길게 열린 이유
  - 외부 호출
  - 대량 루프
  - idle in transaction
  - 배치
- 인덱스가 없어 UPDATE가 오래 걸리며 락을 오래 잡는 경우도 흔합니다.

### 50.5 5단계: 구조적으로 해결
- 원자적 업데이트로 경쟁 제거
- 제약조건으로 불가능하게 만들기
- 트랜잭션 범위 축소
- 배치 chunking
- timeout/재시도/관측 강화

---

## 51. 동시성 테스트 전략: “운영에서 터지기 전에” 잡기

동시성 버그는 유닛 테스트로 잘 안 잡힙니다.  
다음 3단계를 권장합니다.

### 51.1 psql 2세션 재현(가장 간단, 가장 강력)
- 이 문서의 실습이 바로 그 형태입니다.

### 51.2 통합 테스트(프로그램으로 경쟁 만들기)
간단한 형태(의사코드):

```text
reset_db()
create_stock(product=1, qty=1)

run 2 threads concurrently:
  attempt_purchase(product=1, qty=1)

assert:
  exactly one succeeds
  stock never negative
```

핵심은 “성공 1, 실패 1”을 강제하고,
실패가 “일관된 에러 코드/메시지”로 나오는지 확인하는 것입니다.

### 51.3 성질(property) 기반 테스트(심화)
인바리언트(invariant)를 정의합니다.
- 재고는 0 미만이 되지 않는다
- 결제는 주문당 최대 1개 CAPTURED다
- 원장 합계와 잔액 캐시는 항상 일치한다(혹은 일정 시간 내 수렴)

그리고 랜덤한 이벤트 시퀀스를 생성해 검증합니다(도구/프레임워크는 스택마다 다름).

---

## 52. 실무 인바리언트 설계법: “규칙을 문장으로, SQL로”

DB 실력이 빠르게 늘어나는 지점은
**비즈니스 규칙을 데이터 규칙으로 바꾸는 능력**입니다.

### 52.1 규칙을 1문장으로 쓴다
예:
- “주문은 CREATED에서만 PAID로 갈 수 있다”
- “결제는 주문당 1개만 CAPTURED가 가능하다”
- “재고는 음수가 될 수 없다”
- “유저당 ACTIVE 쿠폰은 1개만 가능하다”

### 52.2 DB 레벨로 옮긴다
- 상태 전이: 조건부 UPDATE
- 중복 방지: UNIQUE/부분 UNIQUE
- 음수 금지: CHECK
- 겹침 금지: exclusion constraint
- 멱등성: idempotency key 테이블 + UNIQUE

### 52.3 앱 레벨은 “해석기” 역할
- DB가 준 실패를 비즈니스 오류로 매핑한다
- 재시도/백오프/관측을 넣는다

---

# Part 10. 인덱스/플랜 디버깅 워크샵 (실습)

이 파트는 “내가 인덱스를 추가했는데 왜 안 타지?”를 끝내는 실습입니다.  
데이터를 크게 만들고, 플랜을 비교합니다.

> 경고: 실습 DB에서만 하세요. (노트북/로컬 OK)

## 53. 큰 테이블 만들기: generate_series

```sql
DROP TABLE IF EXISTS big_orders;
CREATE TABLE big_orders (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  total_cents BIGINT NOT NULL
);

INSERT INTO big_orders(user_id, status, created_at, total_cents)
SELECT
  (random()*100000)::bigint,
  CASE WHEN random() < 0.8 THEN 'CREATED' ELSE 'PAID' END,
  now() - (random()*365*24*60*60)::int * interval '1 second',
  (random()*100000)::bigint
FROM generate_series(1, 1000000);
```

통계 갱신:
```sql
ANALYZE big_orders;
```

## 54. 인덱스 없이 조회해보기
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM big_orders
WHERE user_id = 42
ORDER BY created_at DESC
LIMIT 20;
```

대개 Seq Scan + Sort가 나올 가능성이 큽니다(환경에 따라 다름).

## 55. 복합 인덱스 추가 후 비교
```sql
CREATE INDEX idx_big_orders_user_created
ON big_orders(user_id, created_at DESC);

ANALYZE big_orders;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM big_orders
WHERE user_id = 42
ORDER BY created_at DESC
LIMIT 20;
```

여기서 기대하는 변화:
- Index Scan 또는 Bitmap Index Scan 등장
- Sort가 줄거나 없어짐(인덱스가 정렬을 제공)

## 56. 부분 인덱스 실험: CREATED만 자주 조회
```sql
CREATE INDEX idx_big_orders_created_queue
ON big_orders(created_at)
WHERE status='CREATED';

ANALYZE big_orders;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, created_at
FROM big_orders
WHERE status='CREATED'
ORDER BY created_at
LIMIT 100;
```

부분 인덱스가 “큐/배치 처리” 류에서 강력한 이유를 체감할 수 있습니다.

## 57. 카디널리티 추정이 틀릴 때(통계의 중요성)
- 통계가 오래되거나
- 데이터 분포가 특이하거나
- 조건이 여러 개 결합될 때
플랜이 나빠질 수 있습니다.

실습 아이디어:
1) 특정 user_id에 데이터가 몰리게 만들어 본다(스큐)
2) ANALYZE 전/후 비교
3) 부분 인덱스/확장 통계(심화)로 개선

---

# Appendix G. “실무에서 바로 쓰는” SQL/명령 치트시트

## G.1 자주 쓰는 관측 쿼리

### 현재 실행 중인 쿼리 TOP
```sql
SELECT pid, usename, state, wait_event_type, wait_event,
       now() - query_start AS running_for,
       left(query, 200) AS query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY running_for DESC
LIMIT 20;
```

### idle in transaction 찾기
```sql
SELECT pid, usename, now() - xact_start AS xact_age, left(query, 200) AS query
FROM pg_stat_activity
WHERE state='idle in transaction'
ORDER BY xact_age DESC;
```

### blocked vs blocking
(문서 본문 17장 참고)

## G.2 동시성 제어 옵션

- NOWAIT: 기다리지 않고 바로 실패
```sql
SELECT * FROM accounts WHERE owner='alice' FOR UPDATE NOWAIT;
```

- SKIP LOCKED: 잠긴 row는 건너뛰고 가능한 것만 처리(잡/큐)
```sql
SELECT id FROM jobs
WHERE status='READY'
FOR UPDATE SKIP LOCKED
LIMIT 100;
```

## G.3 안전한 상태 전이 템플릿
```sql
UPDATE some_table
SET status = :to
WHERE id = :id AND status = :from;
```

row count를 반드시 체크하고, 0이면 “이미 처리됨/경합”으로 해석합니다.

---

# 마무리

이 문서가 길어 보일 수 있지만, 실제 실무에서 “DB 사고”가 나면
이 정도 내용을 어차피 원인 분석하면서 다시 찾게 됩니다.

가장 좋은 학습은:
- 내 서비스의 핵심 흐름(결제/재고/예약 등)을 하나 잡고,
- 이 문서의 도구(원자적 업데이트, 제약조건, 락, 재시도, 관측)를 적용해,
- “동시성 버그를 재현 -> 해결 -> 회고 문서화”를 한 번 해보는 것입니다.


---

# Part 11. 애플리케이션에서 트랜잭션을 “안전하게” 쓰는 법

DB를 이해해도, 앱에서 트랜잭션을 잘못 쓰면 그대로 사고가 납니다.  
이 파트는 “프레임워크 공통 함정”을 중심으로 정리합니다.

## 58. autocommit과 “트랜잭션이 있는 척”의 함정

많은 드라이버/ORM은 기본이 autocommit이거나,
프레임워크가 자동으로 트랜잭션을 열었다 닫습니다.

### 58.1 autocommit이란
- 쿼리 1문장마다 자동 커밋되는 모드
- 즉, 아래 두 문장은 “같은 트랜잭션”이 아닙니다.

```sql
UPDATE orders SET status='PAID' WHERE id=1;
UPDATE payments SET status='CAPTURED' WHERE order_id=1;
```

중간에 장애가 나면 “반쪽 상태”가 생깁니다.

### 58.2 “트랜잭션이 있는 척”이 위험한 이유
- 코드에서는 한 함수 안에서 처리하니 “원자적”이라고 착각
- 실제로는 DB에서는 2개의 커밋으로 분리되어 있음

해결:
- 반드시 DB 트랜잭션으로 묶고, 실패 시 롤백되게 해야 합니다.

---

## 59. 트랜잭션 전파/중첩: “내가 생각한 것과 다르게” 동작하는 순간들

프레임워크마다 용어는 다르지만, 공통 함정은 비슷합니다.

### 59.1 흔한 오해 1: 중첩 트랜잭션이 진짜 “중첩”이라고 생각
많은 환경에서 “중첩 트랜잭션”은 실제로는 savepoint로 구현되거나,
아예 지원하지 않고 “같은 트랜잭션”을 공유합니다.

- “안쪽만 롤백하고 바깥은 커밋하고 싶다” -> savepoint가 필요(13장)
- 프레임워크의 설정을 확인해야 합니다.

### 59.2 흔한 오해 2: 트랜잭션 어노테이션이 항상 적용된다고 생각
예: 프록시 기반(AOP)에서는
- 같은 클래스 내부 메서드 호출은 프록시를 안 거쳐 트랜잭션이 적용되지 않는 경우가 있습니다.

실무 팁:
- “트랜잭션이 걸렸는지”는 로그/드라이버 설정/DB에서 확인하세요.
- 문제가 생기면 DB에서 `SELECT txid_current()` 같은 값으로 확인하는 방식도 도움됩니다.

---

## 60. “읽기-검증-쓰기”를 코드에서 제거하는 3가지 요령

동시성 버그의 80%는 read-modify-write에서 시작합니다.  
가능하면 다음 3가지를 습관화하세요.

### 60.1 원자적 UPDATE로 바꾼다(31장)
- 잔액/수량: `balance = balance - :x` + 조건
- 상태 전이: `WHERE status='CREATED'`

### 60.2 DB 제약으로 막는다(Part 4)
- 중복은 UNIQUE
- 음수는 CHECK

### 60.3 “검증”은 DB 결과(row count/에러 코드)로 한다
예:
- row count=0 -> 재고 부족/이미 처리됨
- unique violation -> 이미 존재

검증을 “조회 결과”로 하지 말고,
**DB가 강제한 결과**로 해석하면 동시성에 더 강해집니다.

---

## 61. 재시도 설계: DB 에러를 “도메인 에러”로 바꾸기

실무에서는 DB 에러가 곧 사용자 에러가 되면 안 됩니다.

### 61.1 재시도 대상(일반적으로)
- 데드락
- serialization failure
- 네트워크 순간 장애(단, 쓰기는 멱등성 필수)

### 61.2 재시도하면 안 되는 것(대개)
- 문법/스키마 오류
- 제약조건 위반(UNIQUE/CHECK) -> 재시도해도 같은 결과(오히려 폭주)
- 권한/인증 오류

### 61.3 “도메인 에러 매핑” 예시
- UNIQUE 위반(활성 쿠폰 1개 제한) -> “이미 활성 쿠폰이 있습니다”
- CHECK 위반(음수 금지) -> “수량이 부족합니다”(더 나은 방식은 row count 기반)
- FK 위반 -> “존재하지 않는 리소스입니다”

실무 팁:
- 가능한 한 CHECK 위반보다 “조건부 UPDATE row count=0” 방식이 더 예측 가능합니다.
- 제약조건은 최후의 방어선으로 두고, 앱에는 친절한 에러를 제공하세요.

---

# Part 12. PostgreSQL 내부 감각: 성능/운영에서 갑자기 튀어나오는 것들

이 파트는 “DB가 왜 점점 느려지지?” 같은 장기 운영 이슈에 대한 감각을 줍니다.  
(너무 깊게 들어가면 DBA 영역이지만, 주니어도 최소 감각은 필요합니다.)

## 62. VACUUM/ANALYZE: MVCC의 대가

MVCC에서는 UPDATE/DELETE가 “죽은 버전”을 남깁니다.  
이걸 정리하지 않으면 디스크/인덱스가 비대해지고(=bloat) 성능이 떨어집니다.

### 62.1 autovacuum를 함부로 끄지 마세요
- 단기적으로는 “리소스 아껴서 빠른 것 같아” 보일 수 있지만,
- 장기적으로는 bloat로 더 큰 비용을 냅니다.

### 62.2 ANALYZE는 플래너의 눈
통계가 낡으면:
- 카디널리티 추정이 틀려서
- 엉뚱한 조인 전략/스캔 전략을 선택할 수 있습니다.

실무에서 흔한 징후:
- 배치로 데이터 분포가 급변했는데 쿼리가 갑자기 느려짐
- 특정 조건에서만 플랜이 망가짐

대응:
- ANALYZE 수행(테이블/컬럼 단위)
- autovacuum/analyze 설정 검토

---

## 63. HOT update와 “인덱스를 많이 만들면 왜 느려지나”

PostgreSQL에는 HOT(Heap-Only Tuple) update라는 최적화가 있습니다.
아주 단순화하면:

- 업데이트가 인덱스 키를 바꾸지 않으면,
  인덱스를 덜 건드리고 더 싸게 업데이트할 수 있습니다.

그런데 인덱스가 많아질수록:
- 인덱스 키를 건드릴 확률이 올라가고
- 업데이트 비용이 커질 수 있습니다.

실무 결론:
- 인덱스는 “필요한 것만”
- 특히 업데이트가 빈번한 테이블은 인덱스 남발을 경계

---

## 64. 시퀀스/ID: “결번”은 버그가 아니다

BIGSERIAL/IDENTITY는 시퀀스를 사용합니다.
- 시퀀스는 트랜잭션과 독립적으로 증가할 수 있습니다.
- 롤백해도 시퀀스 값은 되돌지 않습니다.
- 그래서 중간에 “결번”이 생기는 건 정상입니다.

실무에서 하면 안 되는 것:
- “id가 연속이어야 한다”는 요구를 만들기
- id를 의미 있는 값(주문번호 등)으로 쓰기

주문번호처럼 외부 노출/연속성 요구가 있으면 별도의 정책이 필요합니다(예: 날짜+랜덤, ULID 등).

---

# Part 13. MySQL(InnoDB) 동시성 감각: PostgreSQL과 다른 “체감”

MySQL을 쓰는 팀/회사도 많기 때문에, 차이를 알아두면 좋습니다.  
여기서는 “실무에서 헷갈리는 부분”만 압축합니다.

## 65. REPEATABLE READ 기본값과 “갭락”이 주는 체감

InnoDB는 REPEATABLE READ에서
범위 조건에 대해 gap lock/next-key lock이 걸릴 수 있습니다.

### 65.1 왜 중요한가
- PostgreSQL에서는 “읽기”가 잘 안 막히는 느낌이 강한데,
- MySQL에서는 특정 패턴에서 “왜 이 조회가 막히지?”를 경험할 수 있습니다.

특히:
- 인덱스를 타는 범위 조회 + FOR UPDATE
- 중복/팬텀을 막기 위한 락이 더 적극적으로 걸릴 수 있음

실무 팁:
- MySQL에서 락 관련 장애가 나면 “인덱스 설계”와 “쿼리 패턴”이 더 중요해지는 경우가 많습니다.
- 같은 논리라도 DB가 다르면 락 전략이 달라질 수 있습니다.

---

## 66. INSERT ... ON DUPLICATE KEY UPDATE vs ON CONFLICT

멱등성/중복 처리에서 upsert는 필수인데, 문법과 세부 동작이 다릅니다.

- PostgreSQL:
  - `INSERT ... ON CONFLICT (...) DO UPDATE`
  - `DO NOTHING`
- MySQL:
  - `INSERT ... ON DUPLICATE KEY UPDATE`

실무 결론:
- “UNIQUE로 막고 upsert로 처리한다”라는 전략 자체는 동일합니다.
- 다만 충돌 키/업데이트 컬럼/트리거/락 동작 차이는 문서로 확인하세요(운영에서 중요).

---

# Part 14. 케이스 스터디 추가: 메시지 중복 소비(Inbox 패턴)와 작업 큐

이 파트는 outbox(발행)만큼 중요한 “소비” 측 멱등성을 다룹니다.

## 67. at-least-once는 기본이다: 중복 메시지는 반드시 온다

Kafka/RabbitMQ/SQS 등 대부분의 시스템에서
- 정확히 한 번(Exactly once)은 매우 어렵고 비용이 큽니다.
- 그래서 실무에서는 대부분 at-least-once(중복 가능)를 기본으로 둡니다.

즉, 소비자는 다음을 해야 합니다.
- 같은 이벤트가 2번 와도 결과가 1번만 반영되게(멱등)

## 68. Inbox 테이블로 멱등 소비 만들기

스키마 예:
```sql
DROP TABLE IF EXISTS inbox_events;
CREATE TABLE inbox_events (
  event_id TEXT PRIMARY KEY,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

소비 로직(의사코드):

```text
BEGIN TRANSACTION
  insert into inbox_events(event_id) values (:event_id)
    on conflict do nothing
  if insert did nothing:
     -- 이미 처리됨
     COMMIT; return
  apply_business_change()
COMMIT
```

핵심:
- “처리 여부”를 DB에 기록하고 UNIQUE로 막는다
- 비즈니스 변경과 함께 커밋한다(원자성)

---

## 69. 작업 큐 테이블 설계: 중복 처리/동시 처리 방지

간단한 job 테이블:
```sql
DROP TABLE IF EXISTS jobs;
CREATE TABLE jobs (
  id BIGSERIAL PRIMARY KEY,
  type TEXT NOT NULL,
  payload JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('READY','RUNNING','DONE','FAILED')),
  run_after TIMESTAMPTZ NOT NULL DEFAULT now(),
  attempts INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_ready ON jobs(run_after) WHERE status='READY';
```

워커가 job을 집을 때:
```sql
BEGIN;
WITH cte AS (
  SELECT id
  FROM jobs
  WHERE status='READY' AND run_after <= now()
  ORDER BY id
  FOR UPDATE SKIP LOCKED
  LIMIT 50
)
UPDATE jobs
SET status='RUNNING', attempts = attempts + 1, updated_at = now()
WHERE id IN (SELECT id FROM cte)
RETURNING id, payload;
COMMIT;
```

이 패턴은:
- 여러 워커가 동시에 돌아도 같은 job을 중복 처리하지 않게 해줍니다.

---

# Appendix H. 실무용 “결정 트리”: 지금 어떤 도구를 써야 하지?

## H.1 중복 처리를 막고 싶다
- 동일 리소스 중복 생성: UNIQUE + UPSERT
- 동일 요청 재시도: idempotency key 테이블
- 이벤트 중복 소비: inbox 테이블(UNIQUE)

## H.2 수량/잔액이 꼬인다
- 가능한 한 원자적 UPDATE로
- 그래도 복잡하면 SELECT FOR UPDATE
- 충돌이 드물면 낙관적 락(버전)

## H.3 예약/겹침 규칙이 있다
- PostgreSQL이면 exclusion constraint 우선 고려
- 아니면 슬롯화 + UNIQUE, 혹은 SERIALIZABLE/락 설계

## H.4 느린 쿼리다
- EXPLAIN(ANALYZE, BUFFERS)
- 인덱스/반환량/조인 전략/정렬 확인
- 통계(ANALYZE) 확인

## H.5 갑자기 전체가 느려졌다
- 커넥션 풀 대기?
- 락 대기?
- 슬로우 쿼리 폭증?
- blocking 트랜잭션?
- 레플리카 lag?

관측부터.


---

# Part 15. 무중단 마이그레이션 실전: “Expand -> Migrate -> Contract” 상세

41장에서 원칙을 봤다면, 이 파트는 “그 원칙을 실제로 어떻게 하느냐”를 다룹니다.  
마이그레이션은 실무에서 정말 자주 사고가 나기 때문에, **플레이북을 손에 익히는 것**이 중요합니다.

> 핵심 원칙: 운영 중인 시스템에서 스키마 변경은 “한 번에 끝내는 작업”이 아니라  
> **여러 번의 배포로 나눠서 위험을 줄이는 작업**입니다.

## 70. 마이그레이션 PR 템플릿(강력 추천)

마이그레이션 PR에 아래를 적는 습관을 들이면 사고가 크게 줄어듭니다.

- 변경 목적(무엇을 위해)
- 영향을 받는 테이블/인덱스
- 예상 데이터 규모(행 수, 테이블 크기)
- 락 위험(DDL 종류, CONCURRENTLY 여부)
- 백필(backfill) 계획(배치 단위, 실행 시간, 피크타임 회피)
- 롤백 계획(되돌리는 방법)
- 배포 순서(Expand -> Migrate -> Contract 단계)
- 관측 지표(슬로우 쿼리, 락 대기, 에러율, 레플리카 lag 등)

---

## 71. 시나리오 1: NOT NULL 컬럼을 안전하게 추가하기

목표:
- orders에 `paid_at`(결제 완료 시각)을 추가하고, 결제 완료 주문은 NOT NULL로 보장하고 싶다.

### 71.1 1단계(Expand): nullable로 컬럼 추가
```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ NULL;
```

주의:
- DEFAULT를 바로 주면 대규모 테이블에서 리라이트/락 위험이 커질 수 있습니다(환경/버전에 따라).
- 먼저 nullable로 추가하는 게 안전합니다.

### 71.2 2단계(Migrate): 앱을 배포해서 새 컬럼 채우기 시작
- 결제 성공 처리에서 orders.status='PAID'로 바꿀 때 paid_at도 같이 채움
- 이때 트랜잭션 안에서 같이 업데이트하는 것이 좋습니다.

예:
```sql
UPDATE orders
SET status='PAID',
    paid_at = now()
WHERE id=:order_id AND status='CREATED';
```

### 71.3 3단계(Migrate): 기존 데이터 backfill
이미 PAID인 주문의 paid_at을 채워야 한다면, 배치로 채웁니다.

**배치 기본 원칙**
- 작은 chunk로
- 커밋 자주
- 피크타임 회피
- 관측(락/슬로우/IO)

예(단순):
```sql
-- 예: paid_at이 NULL이고 status='PAID'인 것만
UPDATE orders
SET paid_at = created_at
WHERE paid_at IS NULL AND status='PAID';
```

대규모라면 chunking:
```sql
-- id 범위 기반(예시)
UPDATE orders
SET paid_at = created_at
WHERE paid_at IS NULL
  AND status='PAID'
  AND id BETWEEN :from_id AND :to_id;
```

### 71.4 4단계(Contract): NOT NULL 강제
모든 PAID 행에 paid_at이 채워졌다는 것을 확인한 후:

선택지 A) 애플리케이션에서 논리적으로 보장 + DB는 CHECK로 보강
```sql
ALTER TABLE orders
ADD CONSTRAINT chk_paid_has_paid_at
CHECK (status <> 'PAID' OR paid_at IS NOT NULL);
```

선택지 B) 컬럼 자체를 NOT NULL로
```sql
ALTER TABLE orders ALTER COLUMN paid_at SET NOT NULL;
```

- B는 “모든 행”이 paid_at을 갖는 규칙이 되어야 합니다.
- “PAID일 때만 필수”면 A의 CHECK가 더 자연스럽습니다.

---

## 72. 시나리오 2: UNIQUE 제약을 뒤늦게 추가하기(이미 중복이 있을 수 있음)

목표:
- payments(provider, provider_tx_id)가 중복되면 안 되는데, 과거 데이터가 더럽다.

### 72.1 1단계: 중복 찾기
```sql
SELECT provider, provider_tx_id, count(*)
FROM payments
GROUP BY provider, provider_tx_id
HAVING count(*) > 1
ORDER BY count(*) DESC;
```

### 72.2 2단계: 중복 정리 전략 선택
- “최신 것만 남기고 나머지는 삭제”
- “상태가 CAPTURED인 것을 우선”
- “모두 남겨야 하니 unique를 다른 키로”

예: 최신 1개만 남기기(주의: 실무에서는 신중히)
```sql
WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY provider, provider_tx_id
           ORDER BY created_at DESC, id DESC
         ) AS rn
  FROM payments
)
DELETE FROM payments
WHERE id IN (SELECT id FROM ranked WHERE rn >= 2);
```

### 72.3 3단계: UNIQUE 인덱스 추가
```sql
CREATE UNIQUE INDEX CONCURRENTLY ux_payments_provider_tx
ON payments(provider, provider_tx_id);
```

- CONCURRENTLY는 락을 줄이지만, 시간이 더 걸릴 수 있고 실패/재시도 관리가 필요합니다.

---

## 73. 시나리오 3: 대규모 테이블에 FK 추가하기(검증을 단계적으로)

PostgreSQL에서는 FK를 추가할 때 “검증”이 오래 걸릴 수 있습니다.  
실무에서는 단계적으로 적용하는 방식을 고려합니다(버전/환경에 따라).

개념적 접근:
1) 우선 FK를 “NOT VALID”로 추가(새 데이터는 체크, 기존은 나중에)
2) 백그라운드로 검증(VALIDATE CONSTRAINT)

예:
```sql
ALTER TABLE order_items
ADD CONSTRAINT fk_order_items_order
FOREIGN KEY (order_id) REFERENCES orders(id)
NOT VALID;

ALTER TABLE order_items
VALIDATE CONSTRAINT fk_order_items_order;
```

이 방식은 운영 중 락 시간을 줄이는 데 도움이 될 수 있습니다.

---

## 74. 시나리오 4: 컬럼 rename은 왜 위험한가(호환성 깨짐)

rename은 단순해 보이지만, 영향을 받는 범위가 넓습니다.
- 앱 코드(ORM 매핑, SQL)
- 리포트/BI
- 운영 스크립트
- 뷰/함수/트리거

### 74.1 안전한 대안: 새 컬럼 추가 + dual write + 전환
1) 새 컬럼 추가
2) 일정 기간 dual write
3) 읽기 전환
4) 구 컬럼 제거

비용은 들지만, 사고 확률이 크게 줄어듭니다.

---

# Part 16. 인덱스/플랜 심화 실험: “왜 느린지”를 더 정확히 진단하기

Part 10의 워크샵이 기본이라면, 이 파트는 “실무에서 자주 만나는 추가 병목”을 다룹니다.

## 75. Sort spill: 정렬이 메모리를 넘으면 디스크로 떨어진다

정렬/그룹바이는 메모리를 사용하고,
메모리를 넘으면 디스크 임시 파일을 쓰면서 급격히 느려질 수 있습니다.

### 75.1 실험 아이디어
- 큰 테이블에서 ORDER BY로 많은 row 정렬
- `work_mem`을 작게/크게 바꾸어 비교

예:
```sql
SET work_mem = '4MB';  -- 실험용(운영에서는 신중)
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM big_orders
ORDER BY total_cents DESC
LIMIT 200000;
```

`Sort Method`가 external merge(디스크 사용)로 나오면 spill 가능성이 있습니다.

실무 대응:
- 필요한 컬럼만 정렬(SELECT *)
- 적절한 인덱스로 정렬 비용 제거(ORDER BY 인덱스)
- work_mem 조정(전역 조정은 위험, 세션 단위/쿼리 단위로 신중)

---

## 76. Index Only Scan이 안 나오는 이유(visibility map)

커버링 인덱스를 만들어도 Index Only Scan이 안 뜨는 경우가 있습니다.
- 튜플이 “모두 visible”하지 않으면 테이블 확인이 필요합니다.
- vacuum/analyze 상태에 따라 달라질 수 있습니다.

실무 팁:
- Index Only Scan이 항상 나올 거라고 기대하지 말고,
  EXPLAIN으로 확인하고, 테이블/워크로드 특성을 고려하세요.

---

## 77. JSONB, GIN 인덱스 감각(실무에서 자주 쓰지만 함정도 많음)

JSONB를 남발하면:
- 유연해 보이지만
- 인덱스/쿼리 패턴이 복잡해지고
- 장기 운영에서 성능 문제가 생길 수 있습니다.

### 77.1 GIN 인덱스 예시
```sql
CREATE TABLE events (
  id BIGSERIAL PRIMARY KEY,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_payload_gin ON events USING gin(payload);
```

쿼리:
```sql
SELECT *
FROM events
WHERE payload @> '{"type":"OrderPaid"}';
```

주의:
- JSONB 인덱스는 크고, 쓰기 비용이 큽니다.
- 정말 필요한 키에 대해서는 정규 컬럼으로 빼는 게 더 좋을 수 있습니다.

---

## 78. 시계열/로그 테이블과 BRIN 인덱스(큰 테이블에서 가성비)

시계열 데이터가 “created_at이 증가하는 순서”로 쌓이는 경우,
BRIN 인덱스가 매우 작은 비용으로 효과를 줄 수 있습니다.

개념:
- B-tree처럼 모든 값을 정밀하게 저장하지 않고,
  블록 범위의 요약(min/max)으로 대략적 필터링을 합니다.

(이 파트는 심화라 개념만 소개합니다. 실제 적용은 데이터 분포와 쿼리를 보고 결정하세요.)

---

# Appendix I. 용어집(Glossary)

- 트랜잭션(Transaction): 여러 SQL을 하나로 묶어 원자적으로 처리하는 단위  
- 격리수준(Isolation level): 동시에 실행되는 트랜잭션이 서로를 어떻게 보게 할지 정하는 규칙  
- MVCC: 다중 버전 동시성 제어. 업데이트가 새 버전을 만들고 스냅샷으로 읽음  
- 락(Lock): 동시에 같은 자원을 변경할 때 충돌을 막기 위한 잠금  
- 데드락(Deadlock): 서로가 서로의 락을 기다려 영원히 진행 못 하는 상황  
- 원자적 업데이트(Atomic update): `SET x = x + 1` 같은 방식으로 읽기 없이 한 문장으로 변경  
- 멱등성(Idempotency): 같은 요청을 여러 번 해도 결과가 한 번과 같게 만드는 성질  
- outbox/inbox: DB를 이용해 이벤트 발행/소비를 멱등하게 만드는 패턴  
- 카디널리티(Cardinality): 쿼리 결과 행 수(또는 조인 결과 규모)  
- 선택도(Selectivity): 조건이 데이터를 얼마나 잘 걸러내는지

