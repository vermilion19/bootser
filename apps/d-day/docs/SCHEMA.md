# d-day-service 리뉴얼 — 스키마 · 인덱스 전략

> **상태: 설계 단계.** SPEC §9(도메인 모델) · §11(Nager 실측) · §12(TheSportsDB 실측) 과
> ARCHITECTURE(배치 · 캐시 · 이벤트 · 동기화) 가 닫힌 뒤의 문서다.
> SPEC §8-3 · §10.9-5 가 `db-tuner` 몫으로 남긴 항목이고, SPEC §11.3 이 **"이 문단을 같이 넘긴다"**
> 고 적은 그 문단이 이 문서 §2 의 입력이다.
>
> **아키텍처 결정을 다시 열지 않는다.** 배치 · 캐시 · 이벤트 흐름은 ARCHITECTURE 가 닫았다.
> 여기서 정하는 것은 **테이블 · 제약 · 인덱스 · 보존 정책 · DDL 의 소재지**뿐이다.
> **E-2 검색 색인은 설계하지 않는다** (1차 밖). 다만 `release` 가 나중에 색인 대상이 된다는 것은
> §6.6 한 줄로 기억해 둔다.
>
> 작성 2026-09-14

---

## 0. 결정 요약

근거는 각 절에 있다.

| | 물음 | 정한 것 |
| --- | --- | --- |
| 1 | `holiday` 의 「지역 집합」 | **정렬한 정규화 문자열 한 칼럼**(`subdivision_key`). 별도 테이블도 배열도 아니다. `NULL` 이 아니라 `''` 를 센티넬로 쓴다 |
| 2 | 유일 제약 | `UNIQUE (country_code, holiday_date, name_en, subdivision_key)` — **부분 인덱스가 아니다** |
| 3 | 소프트 삭제 | `deleted_at` + `last_seen_run_id`. **유일 인덱스는 삭제된 행까지 덮는다** — 그래야 upsert 가 되살린다 |
| 4 | 소프트 삭제 보존 | **동기화 3회차**(공휴일 기준 3주) 지나면 물리 삭제. ARCHITECTURE §10-12 를 이 값으로 닫는다 |
| 5 | 파생 컬럼 | **앱이 쓰고 DB 가 `CHECK` 로 검사한다.** 생성 컬럼(`GENERATED`)은 쓰지 않는다 |
| 6 | 축 질의의 정체성 | 이름 축은 `name_en` 이 아니라 **`name_slug` 로 묶는다.** 허브와 낱장이 같은 키를 써야 수가 맞는다 |
| 7 | `axis` · `sky` 의 테이블 수 | **0.** SPEC §9.4 · §9.5 가 스키마에서 그대로 보인다 |
| 8 | `anniversary_occurrence` PK | **자연키 3열 복합 PK.** 이 표에서만 Snowflake 규약을 벗어난다 (근거 §5.3) |
| 9 | 롤포워드 | 연 1회 일괄이 아니라 **매일 1/365 씩.** 쓰기 부하가 읽기 부하와 같아진다 |
| 10 | `sport_event` 재-upsert | `source_hash` 한 칼럼. **안 바뀌었으면 `UPDATE` 를 안 한다** (창 누적의 핵심) |
| 11 | `date_change` | **유일 제약을 걸지 않는다.** 중복 제거는 Outbox 멱등키의 일이다 — 이력과 알림은 요구가 다르다 |
| 12 | Outbox 폴링 | `(created_at) WHERE status IN ('PENDING','SENDING')` **부분 인덱스.** 적체돼도 Top-N 이다 |
| 13 | 파티셔닝 | **1차에는 없다.** 방아쇠는 `anniversary_occurrence` 3,000만 행 |
| 14 | DDL 의 자리 | **Flyway 를 들인다.** `d-day-service/src/main/resources/db/migration/`. 저장소 최초다 |
| 15 | 제약과 인덱스의 이중 관리 | **정합성 제약은 엔티티에도 적고, 인덱스는 Flyway 에만 적는다** (근거 §1.5) |

---

# 1. 이 스키마가 지키는 다섯 규칙

개별 테이블을 보기 전에 규칙부터 정한다. **규칙이 없으면 18개 테이블이 18가지 관례를 갖는다.**

## 1.1 ID · 이름 · 시각 타입

| | 정한 것 | 까닭 |
| --- | --- | --- |
| PK | `bigint`, 값은 `SnowflakeGenerator.nextId()` | 저장소 관례다 (`User` · `Waiting` · `OutboxEvent` 전부). **예외 둘** — `country`(자연키 `code`) · `anniversary_occurrence`(§5.3) · `holiday_coverage`(자연키) |
| 시퀀스 | **쓰지 않는다** | Snowflake 를 쓰는 이상 `BIGSERIAL` 은 죽은 객체다 |
| 컬럼 이름 | `snake_case` | Spring Boot 기본 물리 명명 전략이 `CamelCaseToUnderscoresNamingStrategy` 다. DDL 이 이것과 어긋나면 `validate` 가 부팅을 막는다 |
| 국가 코드 | **`varchar(2)`. `char(2)` 가 아니다** | Hibernate 는 `String` 을 `varchar` 로 매핑한다. `char(2)` 로 적으면 `validate` 가 타입 불일치로 터지고, 게다가 `char` 는 공백 채움 비교라 `'KR ' = 'KR'` 이 참이 된다 |
| 날짜 | `date` | 공휴일은 시각이 없다. D-day 는 조립 단계에서 나라 시간대로 계산한다 (SPEC §9.9(2) · ARCHITECTURE §4.5) |
| 순간 | `timestamptz` | `starts_at` · `deleted_at` · `detected_at` · `published_at`. 엔티티는 `Instant` |
| 감사 | `created_at` · `updated_at` 은 `timestamp(6)` | `BaseEntity` 가 `LocalDateTime` 이라 `timestamp without time zone` 이 된다 |

**마지막 줄은 흠이고, 흠인 채로 둔다.** 한 테이블 안에 `timestamptz` 와 `timestamp` 가 섞인다.
`BaseEntity` 를 고치면 `waiting` · `auth` · `query-burst` 의 기존 테이블이 전부 마이그레이션 대상이
되는데, **그것은 d-day 의 범위가 아니다.** 대신 규칙을 하나 둔다 — *`BaseEntity` 가 주는 두 칼럼만
`timestamp` 다. 우리가 새로 만드는 순간 칼럼은 전부 `timestamptz` 다.* 섞인 것이 실수가 아니라
경계라는 것이 보이게.

## 1.2 파생 컬럼은 앱이 쓰고 DB 가 검사한다

SPEC §9.3 이 `dayCount` 를 거부한 근거는 **"시작과 끝에서 나오는 값을 따로 담으면 둘이 갈라질 수
있고, 그러면 그것까지 검증해야 한다"** 였다. 그런데 인덱스를 만들려면 파생값이 칼럼이어야 하는
자리가 셋 있다 — `holiday_year` · `is_public` · `name_slug`.

갈래 셋을 재 보았다.

| | 방법 | 버린 까닭 |
| --- | --- | --- |
| 가 | 식 인덱스 (`ON holiday (EXTRACT(YEAR FROM holiday_date))`) | PostgreSQL 에서는 된다(`date_part(text,date)` 는 IMMUTABLE). **그런데 QueryDSL 이 그 식을 그대로 써야 인덱스를 탄다.** 질의 쪽 실수가 조용히 Seq Scan 이 되고, 아무 에러도 안 난다 |
| 나 | 생성 칼럼 (`GENERATED ALWAYS AS ... STORED`) | 두 군데서 막힌다. **(1)** `array_to_string` 은 `STABLE` 이라 생성 칼럼에 못 쓴다. **(2)** H2 의 `create-drop` 은 Hibernate 가 만들므로 생성 칼럼이 **테스트 DB 에는 존재하지 않는다.** 앱이 값을 안 쓰므로 H2 에서는 그 칼럼이 통째로 비고, **운영에서만 맞는 테스트**가 된다 |
| **다** | **앱이 쓰고 `CHECK` 가 검사한다** | — |

> ### 규칙 — 파생 칼럼은 앱이 채우고, 그 파생 관계를 `CHECK` 로 DB 에 새긴다.

```sql
CONSTRAINT ck_holiday_year   CHECK (holiday_year = EXTRACT(YEAR FROM holiday_date)::smallint),
CONSTRAINT ck_holiday_public CHECK (is_public = (strpos(',' || types_raw || ',', ',Public,') > 0))
```

얻는 것 셋.

1. **SPEC §9.3 의 우려가 사라진다.** "둘이 갈라질 수 있다" 가 아니라 **갈라지면 `INSERT` 가 실패한다.**
   그리고 §5.2 의 부분 실패 단위가 (국가, 연도) 라 그 실패는 `SyncRunItem.FAILED` 한 건으로 격리된다.
2. **H2 에서도 같은 코드가 돈다.** 앱이 값을 채우므로 CHECK 가 없는 H2 에서도 칼럼이 비지 않는다.
3. **`is_public` 의 구분자 앵커가 실측 방어다.** SPEC §11.2 가 `Public+Bank` 10건을 발견했다.
   `types_raw LIKE '%Public%'` 로 짜면 원천이 언젠가 `PublicSector` 를 내보낼 때 조용히 오염된다.
   `',Public,'` 는 그럴 수 없다.

**`CHECK` 가 못 지키는 것도 적어 둔다.** `name_slug` 와 `subdivision_key` 의 **정렬 여부**는 SQL 식으로
검사할 수 없다. 그 둘은 §1.3 의 정규화 함수 하나에 몰고, **속성 기반 테스트**(임의 순서로 섞어 넣어도
같은 키가 나오는가)로 지킨다. DB 가 못 지키는 자리를 안 적으면 지켜지는 줄 안다.

## 1.3 집합값은 정규화 문자열 한 칼럼으로 굳힌다

`subdivision_key`(지역 집합) · `types_raw`(타입 배열) · `bridge_days`(징검다리 날짜) ·
`notify_offsets`(알림 시점) 넷이 전부 집합이다. **넷 다 같은 모양으로 굳힌다.**

> **정규화 = (1) 원소를 정렬하고 (2) 쉼표로 잇고 (3) 빈 집합은 `NULL` 이 아니라 `''`.**

`text[]` 와 `jsonb` 를 버린 까닭은 §2.2 에 자세히 적는다. 여기서는 **세 번째 항목이 제일 중요하다**는
것만 못 박는다 — **`NULL` 이면 유일 제약이 무력해진다.** PostgreSQL 의 btree 유일 인덱스에서
`NULL` 은 서로 같지 않으므로, 전국 공휴일(`counties: null`)을 `NULL` 로 저장하면
**대한민국 설날이 매주 한 줄씩 늘어난다.** 조용히.

> PG 15+ 의 `UNIQUE NULLS NOT DISTINCT` 로도 풀린다. **쓰지 않는다** — H2 의 동작이 다르고,
> PG 14 이하로 내려가는 순간 조용히 뜻이 바뀐다. `''` 센티넬은 어디서나 같은 뜻이다.

## 1.4 FK 는 컨텍스트 안에서만

| | 규칙 | 근거 |
| --- | --- | --- |
| 컨텍스트 **안** | FK 를 건다 (`holiday → country`, `anniversary → occurrence`, `sync_run → item`, `movie → movie_release`) | 값이 싸고 버그를 잡는다. 우리 규모에서 부모 검사 비용은 측정 한계 밑이다 |
| 컨텍스트 **사이** | **FK 를 걸지 않는다.** 논리 참조만 둔다 | `anniversary.member_id` 는 auth-service 소유다 (SPEC §9.6). 저장소 전례도 그렇다 — `Waiting.restaurantId` 가 "식당 ID (논리적 참조)" 라고 주석까지 달려 있다 |
| `ON DELETE CASCADE` | **두 곳만** — `anniversary → occurrence`, `sync_run → item` | 둘 다 부모 없이는 뜻이 없는 투영·명세다 |

**`sport_event → team` 만 예외적으로 조심한다.** FK 를 걸면 원천이 모르는 팀 id 를 실은 경기를
줄 때 그 회차가 통째로 막힌다. 그래서 **동기화가 모르는 팀을 만나면 `team` 에 스텁 행을
먼저 넣는다**(`is_stub = true`). FK 는 살리고 창 누적은 안 끊는다. 스텁 건수가 0 이 아니면
SPEC §12.3 이 발견한 것과 같은 종류의 신호다.

## 1.5 `ddl-auto: validate` 가 못 보는 것 — 이 문서의 존재 이유

운영 프로필이 `validate` 다. **그것이 무엇을 지켜 주는지 정확히 알아야 한다.**

| Hibernate `validate` 가 보는 것 | **안 보는 것** |
| --- | --- |
| 테이블이 있는가 | **인덱스** |
| 칼럼이 있는가 | **유일 제약** |
| 칼럼 타입이 맞는가 | **`CHECK` 제약** |
| 시퀀스가 있는가 | **NULL 허용 여부 · 기본값 · FK** |

> **즉 이 문서가 정하는 것의 거의 전부를 `validate` 가 못 지킨다.**

따라 나오는 것 셋이고, 셋이 §11(DDL 의 자리)의 근거가 된다.

1. **DDL 은 사람이 들고 있어야 하고, 그것을 실제로 적용하는 도구가 있어야 한다.** 없으면 첫 사람이
   "테이블이 없다" 를 만나고, 그 자리에서 가장 자연스러운 반사가 **`ddl-auto: update` 로 바꾸는 것**이다.
   그 순간 부분 인덱스도 `CHECK` 도 `INCLUDE` 도 전부 사라지고, **그것을 `validate` 가 못 잡는다.**
2. **정합성 제약은 엔티티에도 적는다.** `@Table(uniqueConstraints = ...)` 로 적어야 H2 `create-drop`
   에서도 유일 제약이 생기고, "중복 공휴일이 거부되는가" 테스트가 테스트 DB 에서 실제로 의미를 갖는다.
3. **인덱스는 엔티티에 적지 않는다.** `@Table(indexes = ...)` 에 적으면 이름과 정의가 두 군데
   살고, 부분 인덱스·`INCLUDE` 는 애초에 표현할 수 없어 **두 정의가 처음부터 다르다.**
   인덱스는 Flyway 한 곳에만 산다.

---

# 2. `holiday` — 자연키와 「지역 집합」 (요구 1)

SPEC §11.3 의 실측이 입력이다.

| 키 후보 | 45개국 · 2026 에서의 충돌 |
| --- | --- |
| `(국가, 날짜, 영어 이름)` | **11조** (스위스 10 · 호주 1) |
| `(국가, 날짜, 영어 이름, 지역 집합)` | **0조** |

**그래서 넷이다.** 남은 물음은 하나 — *지역 집합을 어떻게 저장하고 어떻게 비교하나.*

## 2.1 갈래 넷

| | 방법 | 유일 제약을 DB 가 강제하나 | 버린 까닭 |
| --- | --- | --- | --- |
| 가 | `holiday_subdivision(holiday_id, code)` 자식 테이블 | **못 한다** | 이것이 결정적이다. 자식 테이블은 *"이 부모의 지역 집합이 저 부모의 것과 같다"* 를 제약으로 표현할 방법이 없다. 동기화가 upsert 를 하므로 **`ON CONFLICT` 가 겨냥할 인덱스가 있어야 하는데 겨냥할 것이 없다.** 앱이 매번 집합 비교를 하게 되고, 그 비교가 곧 유일성의 유일한 수문장이 된다 |
| 나 | `subdivision_codes text[]` + `UNIQUE (..., subdivision_codes)` | 한다 | **배열 동등성은 순서를 본다.** `{CH-ZH,CH-BE}` 와 `{CH-BE,CH-ZH}` 가 다른 값이다. 결국 앱이 정렬해 넣어야 하고, 그러면 「다」와 같은 일을 하면서 **JPA 매핑만 까다로워진다**(Hibernate 의 `String[]` → `varchar(255)[]` 매핑과 H2 배열 지원 차이) |
| 다 | 정렬 집합의 **해시**(`md5`) 한 칼럼 | 한다 | 인덱스가 32바이트로 고정되는 것이 장점인데, **우리 인덱스는 애초에 작다**(§2.3 의 계산). 대신 잃는 것이 크다 — `psql` 로 그 행을 봐도 **무엇이 키인지 안 보인다.** 충돌 조사가 불가능해진다 |
| **라** | **정렬해 쉼표로 이은 문자열 한 칼럼** | **한다** | — |

## 2.2 「라」로 간다

```sql
subdivision_key varchar(1000) NOT NULL DEFAULT ''
```

> 정규화 규칙: **원소를 오름차순 정렬 → `,` 로 이음 → 전국이면 `''`.**
> `counties: null` 도 `counties: []` 도 전부 `''` 로 접힌다.

근거 넷.

1. **DB 가 유일 제약을 그대로 강제한다.** `ON CONFLICT (country_code, holiday_date, name_en,
   subdivision_key)` 가 겨냥할 인덱스가 실재한다. 「가」가 못 하는 바로 그것이다.
2. **키가 사람이 읽을 수 있다.** 스위스 `Epiphany` 두 건이 `CH-GR,CH-SZ,CH-TI,CH-UR` 와
   `CH-AI,CH-LU,...` 로 보인다. SPEC §11.3 을 조사할 때 실제로 필요했던 정보다.
3. **`text[]` · `jsonb` 를 안 쓰므로 H2 와 PostgreSQL 이 같은 타입이다.** §1.2 의 이유와 같다 —
   테스트 DB 와 운영 DB 가 다른 모양이면 테스트가 지키는 것이 줄어든다.
4. **1차에 지역으로 거르는 질의가 없다.** SPEC §10.2 의 엔드포인트 어디에도 지역 필터가 없다.
   A-9 는 *"넣고 **표시**한다"* 이지 *"지역으로 찾는다"* 가 아니다. **없는 질의를 위해 GIN 인덱스와
   자식 테이블을 지금 만들지 않는다.**

### 지역 축이 나중에 열리면

**「가」를 그때 더한다. 지금 것을 안 버린다.** `holiday_subdivision(holiday_id, code)` 를 추가하고
GIN 이든 btree 든 걸면 되며, `subdivision_key` 는 그대로 유일 제약의 수문장으로 남는다.
**더하기만 있고 고치기가 없다** — 이 순서라서 「라」가 안전하다.

### `varchar(1000)` 인 까닭 — 인덱스 키 상한을 손으로 재 둔다

PostgreSQL btree 의 항목 상한은 약 **2,704바이트**다. 자연키 넷의 최악을 더해 본다.

| 칼럼 | 최악 |
| --- | --- |
| `country_code varchar(2)` | 2 + 1 |
| `holiday_date date` | 4 |
| `name_en varchar(200)` | 200 + 4 |
| `subdivision_key varchar(1000)` | 1,000 + 4 |
| **합** | **≈ 1,215 바이트** |

상한의 절반 밑이다. **`text` 로 열어 두지 않는 까닭이 여기 있다** — `text` 면 원천이 언젠가
아주 긴 집합을 줄 때 `index row size exceeds maximum` 이라는, **어느 칼럼 때문인지 안 보이는
런타임 에러**가 난다. `varchar(1000)` 이면 칼럼 이름이 찍힌 채로 그 (국가, 연도) 하나만
`SyncRunItem.FAILED` 가 된다. §5.2 의 부분 실패가 제 역할을 한다.

### 형식 `CHECK` 는 **느슨하게** 건다

처음에는 ISO 3166-2 정규식(`^[A-Z]{2}-[A-Z0-9]{1,4}(,...)*$`)을 걸려고 했다. **버렸다.**
원천이 새 형식의 코드를 하나 내보내는 순간 **그 나라가 통째로 동기화에서 빠지는데, 그것은
과한 대가**다. `subdivision_key` 가 실제로 지켜야 하는 성질은 하나뿐이다 — **키로서 모호하지
않을 것.**

```sql
CONSTRAINT ck_holiday_subdiv CHECK (
    subdivision_key !~ '[[:space:]]'    -- 공백이 섞이면 정렬·비교가 흔들린다
AND subdivision_key !~ ',,'             -- 빈 원소
AND subdivision_key !~ '^,' AND subdivision_key !~ ',$'
)
```

코드 **모양** 검사는 제약이 아니라 동기화의 검증 단계에서 하고, 어긋나면 `SyncRunItem` 의
경고로 남긴다. **제약은 못 고칠 것만 막고, 신호는 신호가 보내야 한다.**

## 2.3 `holiday` 의 최종 자연키

```sql
CREATE UNIQUE INDEX uk_holiday_natural
    ON holiday (country_code, holiday_date, name_en, subdivision_key);
```

**`is_global` 을 키에 넣지 않는다.** SPEC §11.3 이 넷으로 0조를 실측했고, 다섯째를 더하면
*"전국 → 지역 한정으로 바뀐 공휴일"* 이 갱신 대신 **새 행으로 들어온다.** 대신 못 일어날
조합만 막는다.

```sql
CONSTRAINT ck_holiday_global CHECK (NOT (is_global AND subdivision_key <> ''))
```

---

# 3. 소프트 삭제 (요구 2)

## 3.1 모양

```sql
last_seen_run_id bigint      NOT NULL,     -- 이 행을 마지막으로 본 동기화 회차
deleted_at       timestamptz              -- NULL 이면 살아 있다
```

동기화 한 단위 = (국가, 연도) 한 트랜잭션 (ARCHITECTURE §5.2) 안에서 둘을 한다.

```sql
-- 1) 원천이 준 것을 전부 upsert. 이때 되살아난다.
INSERT INTO holiday (...) VALUES (...)
ON CONFLICT (country_code, holiday_date, name_en, subdivision_key)
DO UPDATE SET name_local = EXCLUDED.name_local,
              name_slug  = EXCLUDED.name_slug,
              is_global  = EXCLUDED.is_global,
              types_raw  = EXCLUDED.types_raw,
              is_public  = EXCLUDED.is_public,
              launch_year = EXCLUDED.launch_year,
              last_seen_run_id = EXCLUDED.last_seen_run_id,
              deleted_at = NULL,                    -- ← 되살리기가 여기 한 줄이다
              updated_at = now();

-- 2) 이번 회차가 못 본 것을 죽인다.
UPDATE holiday
   SET deleted_at = now(), updated_at = now()
 WHERE country_code = :cc AND holiday_year = :year
   AND deleted_at IS NULL
   AND last_seen_run_id <> :runId;
```

## 3.2 유일 인덱스를 **부분으로 만들지 않는 것**이 이 절의 전부다

솔깃한 최적화가 하나 있다 — `uk_holiday_natural ... WHERE deleted_at IS NULL`.
살아 있는 행만 인덱스에 두면 작아진다.

> **그렇게 하면 되살리기가 깨진다.** `ON CONFLICT` 는 **술어가 맞는 부분 인덱스만** 겨냥할 수
> 있으므로, 소프트 삭제된 행은 충돌로 잡히지 않고 **같은 자연키의 행이 하나 더 들어온다.**
> 그 다음부터 그 공휴일은 영원히 두 줄이다.

**되살릴 수 있어야 한다는 요구(§5.3 급감 가드가 있으므로)와 부분 유일 인덱스는 양립하지
않는다.** 유일 인덱스는 무덤까지 덮는다. 성능 손해는 §3.3 의 보존 기간으로 상계한다.

**대신 읽기 인덱스는 전부 `WHERE deleted_at IS NULL` 부분 인덱스다** (§4). 유일성은 전체를,
읽기는 산 자만 본다. 역할이 다르므로 인덱스도 다르다.

## 3.3 보존 기간 — **동기화 3회차** (ARCHITECTURE §10-12 를 닫는다)

| 갈래 | 값 | 평가 |
| --- | --- | --- |
| 가 | 영구 보존 | 부활 창이 무한이라는 뜻인데, **2년 전에 사라진 공휴일이 돌아올 일은 없다.** 유일 인덱스만 계속 자란다 |
| 나 | 30일 고정 | 달력 기반이라 **회차와 어긋난다.** 공휴일 동기화는 주 1회라 30일은 4~5회차인데, 원천 장애로 2회를 건너뛰면 뜻이 달라진다 |
| **다** | **마지막으로 살아 있던 회차로부터 성공 회차 3번** | — |

> **`deleted_at` 이 있고, 그 (국가, 연도) 의 `holiday_coverage.last_ok_run_id` 가 그 뒤로
> 3번 더 바뀌었으면 물리 삭제한다.**

근거 셋.

1. **부활의 실제 창이 3주다.** 원천이 한 회차 헛소리를 하면 §5.3 의 급감 가드가 `ABORTED` 로
   막아 애초에 삭제가 안 된다. 가드를 통과해 삭제된 것이 되살아나는 경우는
   *"원천이 한 주 빼먹었다가 도로 넣었다"* 뿐이고, 그것은 다음 주에 드러난다.
2. **달력이 아니라 회차를 세면 원천 장애에 안 휘둘린다.** 3주 동안 동기화가 한 번도 성공을
   못 했으면 아무것도 안 지운다 — 그게 옳다.
3. **인덱스 부담이 상수로 묶인다.** 아래 §3.4 참고.

`long_weekend` 도 같은 규칙을 쓴다. **`sport_event` 는 소프트 삭제를 하지 않는다** — 창 누적은
"사라짐" 을 관측할 수 없기 때문이다(원천이 다음 1건만 준다). 지난 경기는 그냥 남는다.

## 3.4 인덱스에 주는 영향 — 재 보면 무시할 수 있다

| | 수치 |
| --- | --- |
| 살아 있는 행 | 204국 × ≈14건 × 5년 ≈ **14,000** |
| 주당 새로 죽는 행 | 실측 기준 **0에 가깝다** (SPEC §11.4 가 "평소 0이어야 정상") |
| 3회차 보존이 더하는 행 | 많아야 수백 |
| `uk_holiday_natural` 크기 | ≈ **1.5 MB** |

> **즉 소프트 삭제가 유일 인덱스를 부풀리는 양은 1% 미만이다.** 부분 유일 인덱스로 얻을 것이
> 애초에 없었고, 그 대신 잃을 뻔한 것(§3.2)은 정합성이었다.

**읽기 인덱스 쪽이 오히려 이득이다.** 읽기 인덱스가 전부 `WHERE deleted_at IS NULL` 이므로
죽은 행은 다섯 개 인덱스 중 넷에서 **아예 빠진다.** 소프트 삭제가 읽기를 느리게 만들지 않는다.

**쓰기 대가는 여기서 딱 한 번 발생한다** — `deleted_at` 을 `NULL` → `now()` 로 바꾸는 `UPDATE`
한 번이 네 개 부분 인덱스에서 그 항목을 **삭제**하게 만든다(술어 칼럼이 바뀌므로 HOT 불가).
주당 0~수십 건이므로 비용이라 부를 수 없다.

---

# 4. 읽기 질의별 인덱스 (요구 3)

## 4.0 먼저 — 이 코퍼스는 작다. 그것이 인덱스 설계를 바꾼다

| | 행 수 |
| --- | --- |
| `holiday` (5년 보유) | **≈ 14,000** |
| 한 해치 | ≈ 2,800 (SPEC §5 의 "2026년 (국가,날짜) 2,500쌍" 과 맞다) |
| `long_weekend` (5년) | ≈ 7,500 (실측 330건/45국/1년 → 7.3건/국·년) |
| `country` | **204** |

> **14,000행 테이블의 Seq Scan 은 8ms 안팎이다.** 그러므로 A-5~A-7 에서 플래너가 Seq Scan 을
> 고르더라도 그것 자체가 고장이 아니다.

**그래서 통과 조건을 「Index Scan 이 나오는가」로 두지 않는다.** 그것은 측정 가능한 목표가 아니라
취향이다. 진짜 조건은 둘이다.

> | 통과 조건 | 왜 이것인가 |
> | --- | --- |
> | **계획에 `Sort` 노드가 없다** | 집계 질의가 느려지는 진짜 원인이 정렬이다. 2,800행 정렬은 한 번은 싸지만 **캐시 워밍(§4.3)이 A-5~A-7 을 연도마다 한꺼번에 돌린다** |
> | **`shared read` ≈ 0 (Index Only Scan 또는 전부 캐시 히트)** | 디스크를 안 친다는 뜻. 14,000행은 통째로 `shared_buffers` 에 있어야 정상이다 |

이 문서의 인덱스는 **그 두 조건을 만드는 것**이 목적이고, Index Scan 은 수단이다.

## 4.1 `holiday` 인덱스 다섯

```sql
-- (1) 자연키 — 유일성. 부분이 아니다 (§3.2)
CREATE UNIQUE INDEX uk_holiday_natural
    ON holiday (country_code, holiday_date, name_en, subdivision_key);

-- (2) A-1 · A-2 · 동기화 스윕
CREATE INDEX ix_holiday_country_year
    ON holiday (country_code, holiday_year)
    INCLUDE (holiday_date, name_en, name_local, name_slug,
             subdivision_key, is_global, is_public, launch_year)
    WHERE deleted_at IS NULL;

-- (3) A-4 — 날짜 → 국가 역방향
CREATE INDEX ix_holiday_on_date
    ON holiday (holiday_date)
    INCLUDE (country_code, name_slug, is_global)
    WHERE is_public AND deleted_at IS NULL;

-- (4) A-5 — 이름 축 (허브 · 낱장)
CREATE INDEX ix_holiday_axis_name
    ON holiday (holiday_year, name_slug, country_code)
    WHERE is_public AND deleted_at IS NULL;

-- (5) A-6 · A-7 — 순위 축 · 요일 축
CREATE INDEX ix_holiday_axis_country
    ON holiday (holiday_year, is_global, country_code, holiday_date)
    WHERE is_public AND deleted_at IS NULL;
```

### 하나씩

| | 질의 | 이 인덱스가 하는 일 |
| --- | --- | --- |
| (2) | `WHERE country_code=? AND holiday_year IN (?,?) AND deleted_at IS NULL` | **Index Only Scan.** 캐시 `h:{cc}:{year}` 한 건을 채우는 데 필요한 칼럼이 전부 `INCLUDE` 에 있어 힙을 안 친다. A-2(다음 공휴일)는 ARCHITECTURE §4.5 대로 `Y` 와 `Y+1` 두 건을 읽으므로 **같은 인덱스에 탐색 두 번**이다 |
| (2) | 동기화 스윕 (§3.1 의 2번 `UPDATE`) | 같은 선두 키 `(country_code, holiday_year)`. 읽기용 인덱스가 쓰기 경로를 그대로 덮는다 — 스윕 전용 인덱스를 따로 안 만든다 |
| (3) | `WHERE holiday_date = ? AND is_public AND deleted_at IS NULL` | **Index Only Scan.** 최악이 1월 1일 · 12월 25일로 **180~200행**(SPEC: 크리스마스 178개국). 204개국 횡단이라지만 실제로 읽는 것은 그 날짜의 항목뿐이다 |
| (4) | 허브: `WHERE holiday_year=? ... GROUP BY name_slug` | 선두가 `holiday_year` 라 한 해가 **연속 구간**이고, 그 안에서 `name_slug` 가 **이미 정렬돼 있다.** 그래서 `GroupAggregate` 가 `Sort` 없이 돈다 — §4.0 의 통과 조건이 이 칼럼 순서에서 나온다 |
| (4) | 낱장: `WHERE holiday_year=? AND name_slug=?` | 두 칼럼 동등 탐색. `country_code` 가 세 번째라 결과가 정렬돼 나온다 |
| (5) | A-6: `WHERE holiday_year=? AND is_global ... GROUP BY country_code` | `(연도, is_global)` 이 연속 구간이고 그 안에서 `country_code` 가 정렬돼 있다. 역시 `Sort` 없음 |
| (5) | A-7: 같은 구간에서 `holiday_date` 를 꺼내 요일을 센다 | `holiday_date` 가 네 번째 키라 **Index Only Scan** 이 된다. 요일은 저장하지 않는다 — §4.4 |

### `is_public` 을 (2) 의 술어에 안 넣은 까닭

A-1 은 `Public` 만 내보내지만 **A-10 이 "나중에 축을 열 때 재동기화가 필요 없게" 를 요구한다.**
(2) 를 `WHERE is_public` 으로 좁히면 그 날 **인덱스를 새로 만들어야 한다.** 대신 `is_public` 을
`INCLUDE` 에 넣어 **Index Only Scan 안에서 걸러지게** 했다. 걸러지는 양은 14,000 중 500 남짓이라
좁힐 값어치가 없다.

### 쓰기 트레이드오프 — 재 보고 넘어간다

| | |
| --- | --- |
| 인덱스 수 | **5** (PK 포함 6) |
| 쓰기 시점 | **주 1회 배치뿐.** 사용자 요청 경로에 `holiday` 쓰기가 하나도 없다 |
| 한 회차 쓰기량 | 204국 × 5년 × ≈14건 = **14,000 upsert** |
| 그중 실제로 값이 바뀌는 것 | 거의 0 (SPEC §5.1 "거의 안 변한다") |

> **그러므로 인덱스 다섯의 쓰기 대가는 이 테이블에서 사실상 0이다.** 다만 이것은
> **`holiday` 에 한정된 판단**이고, 같은 셈을 `sport_event`(10분마다) 와 `outbox_event`(상시)
> 에서 다시 한다 — §6.2 · §7.3. **인덱스가 공짜인 테이블과 아닌 테이블을 구별하지 않는 것이
> 이 직업의 흔한 실수다.**

**그리고 배치마다 `VACUUM (ANALYZE) holiday;` 를 돌린다.** Index Only Scan 은 가시성 맵이
최신이어야 성립하고, 한 회차에 14,000행을 갱신하고 나면 맵이 낡는다. `SyncRun` 이 끝나는
자리에서 명시적으로 부른다 — **자동 청소를 기다리면 §4.3 의 워밍이 그 전에 돈다.**

## 4.2 A-5 의 정체성은 `name_en` 이 아니라 `name_slug` 다

SPEC §10.2 가 `GET /holiday-names` (허브) 와 `GET /holiday-names/{slug}` (낱장) 를 둘 다 둔다.
**둘이 다른 키로 묶이면 허브의 숫자와 낱장의 목록이 안 맞는다.**

| | 묶는 키 | 문제 |
| --- | --- | --- |
| 가 | `name_en` | slug 는 `name_en` 에서 나오는데 **`"New Year's Day"` 와 `"New Years Day"` 가 같은 slug** 가 된다. 허브는 둘을 따로 세고 낱장은 둘을 합쳐 보여 준다. **숫자가 안 맞고, 안 맞는 이유가 안 보인다** |
| **나** | **`name_slug`** | — |

> **허브도 낱장도 `name_slug` 로 묶는다. 표시용 영어 이름은 그 묶음에서 가장 흔한 `name_en`
> 을 고른다.**

이래야 SPEC §5 의 *"60개 이름 × 176개국"* · *"크리스마스에 쉬는 나라 178개국"* 이 허브와 낱장
양쪽에서 같은 수로 재현된다. 그리고 **`HolidayNameLabel` 도 `name_slug` 를 키로 잡는다** —
라벨이 `name_en` 을 키로 잡으면 세 번째 키가 생기고, 셋이 갈라지는 것은 시간 문제다.

`name_en` 의 유일 제약(§2.3)은 그대로 둔다. **원천 충실도는 `name_en` 이, 축의 정체성은
`name_slug` 가 맡는다.** 둘은 다른 일이다.

## 4.3 A-7 — 나라별 주말을 비트마스크로 둔다

SPEC §5 가 검산점을 줬다. **2026년 주말에 겹쳐 날아간 공휴일이 544 이고, 토·일 고정으로 세면
540 이다. 그 네 건의 차이가 스키마가 맞는지를 가른다.**

```sql
weekend_mask smallint NOT NULL   -- ISO-8601: bit0=월 … bit6=일
```

| 갈래 | 값 | 나라 수 (SPEC §5) |
| --- | --- | --- |
| 토 · 일 | `96` (bit5+bit6) | 195 |
| 금 · 토 | `48` (bit4+bit5) | 8 |
| 일요일만 | `64` (bit6) | 1 (인도) |

A-7 질의가 정수 연산 하나로 끝난다.

```sql
SELECT count(*) FROM (
  SELECT DISTINCT h.country_code, h.holiday_date
    FROM holiday h JOIN country c ON c.code = h.country_code
   WHERE h.holiday_year = 2026
     AND h.is_public AND h.is_global AND h.deleted_at IS NULL
     AND ((c.weekend_mask::int >> (EXTRACT(ISODOW FROM h.holiday_date)::int - 1)) & 1) = 1
) t;
-- 기대 544. 540 이 나오면 weekend_mask 가 전부 96 으로 굳어 있다는 뜻이다.
```

**`weekday` 칼럼을 저장하지 않는다.** `EXTRACT(ISODOW FROM date)` 는 IMMUTABLE 이고 대상이
한 해 2,800행이라 계산 비용이 측정 한계 밑이다. §1.2 의 규칙을 다시 읽으면 *"인덱스를 만들려면
칼럼이어야 하는 자리"* 에만 파생을 허용했고, **요일은 인덱스를 안 만든다.**

> **`is_global` 을 켜고 끄는 두 벌을 다 돌려서 544 가 나오는 쪽을 채택한다.** 정적 사이트의
> 코퍼스가 칸톤 한정 공휴일을 셌는지 안 셌는지 우리는 모른다. **추측하지 말고 검산점에게
> 물어본다** — 이것이 A-7 인덱스에 `is_global` 이 키로 들어가 있는 실제 이유다.

## 4.4 예상 계획과 재는 법

DB 가 아직 없으므로 **아래는 예측이다.** 착수 5(공휴일 동기화)가 코퍼스를 채운 직후 여섯 개를
`EXPLAIN (ANALYZE, BUFFERS)` 로 실측하고 이 표를 실제 값으로 갈아 끼운다.

| 질의 | 예상 계획 | 예상 반환 행 | 통과 조건 |
| --- | --- | --- | --- |
| A-1 | Index Only Scan `ix_holiday_country_year` | 14 | `Heap Fetches: 0` |
| A-4 | Index Only Scan `ix_holiday_on_date` | ≤ 200 | `Heap Fetches: 0` |
| A-5 허브 | Index Only Scan `ix_holiday_axis_name` → GroupAggregate | 입력 2,800 / 출력 ~800 | **`Sort` 노드 없음** |
| A-5 낱장 | Index Only Scan, 2열 동등 | ≤ 204 | — |
| A-6 | Index Only Scan `ix_holiday_axis_country` → GroupAggregate | 입력 2,800 / 출력 204 | **`Sort` 노드 없음** |
| A-7 | 위 + Hash Join (`country` 204행) | 출력 204 | **`Sort` 노드 없음** · Hash Join |

**셋 다 캐시 뒤에 있지만**(ARCHITECTURE §4.2) **워밍(§4.3)이 플립마다 연도별로 한 번씩 돌린다.**
그때는 A-5·A-6·A-7 × 보유 연도 수만큼이 **연달아** 실행되므로, 여기서 `Sort` 가 하나 있으면
워밍 전체가 그만큼 길어지고 **워밍이 늦으면 버전 플립이 늦는다.** 인덱스의 칼럼 순서가
캐시 무효화 지연으로 이어지는 경로가 이것이다.

---

# 5. `anniversary_occurrence` 투영 (요구 4)

## 5.1 규모 — 먼저 잰다

가정: 회원당 기념일 5개, 그중 80% 가 매년 반복, 알림 시점 평균 2.5개, 향후 3년치 전개
(ARCHITECTURE §2.3).

회원 1명당 = 5 × (0.8 × 3년 × 2.5 + 0.2 × 1 × 2.5) ≈ **32.5행**

| 회원 수 | 행 수 | 힙 + 인덱스 | 하루에 「오늘 알릴 것」 | 롤포워드 연간 쓰기 |
| --- | --- | --- | --- | --- |
| 1만 | 32만 | ~50 MB | ~300 | ~11만 |
| **10만** | **325만** | **~500 MB** | **~3,000** | **~108만** |
| 100만 | 3,250만 | ~5 GB | ~30,000 | ~1,080만 |

**읽기는 어느 규모에서도 문제가 아니다.** 하루에 3,000행을 날짜 동등 조건으로 꺼내는 일이다.
**문제는 쓰기다** — 연 1회 롤포워드가 10만 회원에서 **108만 행을 하루에** 쓴다.

## 5.2 롤포워드를 매일로 쪼갠다 — 연 1회 주기는 그대로

ARCHITECTURE §2.3 이 정한 것은 **「향후 3년치를 유지한다」**이지 「1년에 하루만 일한다」가
아니다. 그 유지 규칙을 지키는 방식만 고른다.

| | 방법 | 평가 |
| --- | --- | --- |
| 가 | 정한 날 하루에 전체를 훑어 꼬리를 늘린다 | 10만 회원에서 **108만 행 삽입이 한 스케줄 창에** 몰린다. ARCHITECTURE §5.2 가 "한 트랜잭션이 수십 분" 을 금지한 것과 같은 고장이고, §5.7 의 스케줄러 풀 하나를 그동안 붙든다 |
| **나** | **기념일마다 `expanded_until` 을 들고, 매일 「3년 밑으로 떨어진 것」만 늘린다** | — |

```sql
-- anniversary
expanded_until date,
CREATE INDEX ix_anniversary_expand ON anniversary (expanded_until) WHERE recurrence = 'YEARLY';

-- 매일 도는 스케줄러
SELECT id FROM anniversary
 WHERE recurrence = 'YEARLY' AND expanded_until < (CURRENT_DATE + INTERVAL '3 years')
 ORDER BY expanded_until LIMIT 2000;
```

> **기념일의 꼬리는 그 기념일의 날짜에 자연스럽게 짧아진다.** 그러므로 매일 걸리는 것은
> 전체의 **1/365** — 10만 회원에서 **하루 ~3,000행**이다.

**읽기 부하와 정확히 같은 수가 된다.** 하루 3,000행을 읽고 3,000행을 쓴다. 이 대칭이
「나」가 옳다는 제일 좋은 증거다 — **투영을 유지하는 비용이 투영을 쓰는 비용과 같은 눈금에
있으면 그 투영은 감당되는 것이다.**

`LIMIT 2000` 이 백프레셔다. 밀리면 다음 날 따라잡고, 계속 밀리면 `expanded_until` 의 최소값이
당겨지는 것으로 메트릭에 보인다.

## 5.3 이 표에서만 Snowflake 규약을 벗어난다

```sql
CREATE TABLE anniversary_occurrence (
    anniversary_id  bigint   NOT NULL REFERENCES anniversary(id) ON DELETE CASCADE,
    occurrence_date date     NOT NULL,
    notify_offset   smallint NOT NULL,
    member_id       bigint   NOT NULL,
    notified_at     timestamptz,
    created_at      timestamp(6) NOT NULL,
    PRIMARY KEY (anniversary_id, occurrence_date, notify_offset),
    CONSTRAINT ck_occ_offset CHECK (notify_offset BETWEEN 0 AND 365)
);
```

| 벗어난 것 | 까닭 |
| --- | --- |
| **PK 가 자연키 3열** | 이 표는 **집합체가 아니라 투영**이다. 바깥에서 이 행 하나를 id 로 가리킬 일이 없다. 그리고 **ARCHITECTURE §3.5 의 멱등키가 정확히 이 셋**(`anniversaryId, occurrenceDate, notifyOffset`)이다 — 대리키를 두면 **같은 뜻의 키가 둘**이 된다 |
| **`updated_at` 이 없다** | `BaseEntity` 를 상속하지 않는다. 이 행은 만들어지고 한 번 `notified_at` 이 찍히고 끝난다. **3,000만 행에서 안 쓰는 8바이트 칼럼은 240MB** 다 |
| | JPA 대가: `@EmbeddedId` 또는 `@IdClass`. **한 표에 한 번 치르는 값** |

**보너스 — 이 PK 가 파티셔닝 준비를 공짜로 해 준다.** 파티션 테이블의 모든 유일 인덱스는
파티션 키를 포함해야 하는데, 이 PK 는 `occurrence_date` 를 이미 갖고 있다. §9 의 방아쇠가
당겨지는 날 **`RANGE (occurrence_date)` 로 쪼개는 데 제약을 안 고쳐도 된다.**

**그리고 CASCADE 의 FK 인덱스도 공짜다.** `anniversary_id` 가 PK 선두라 C-4(수정·삭제)의
`DELETE WHERE anniversary_id = ?` 와 부모 삭제 연쇄가 둘 다 이 PK 를 탄다. *FK 칼럼에
인덱스를 안 만들어 연쇄 삭제가 전체 스캔이 되는 것*이 흔한 고장인데, 여기서는 구조가 막는다.

## 5.4 스케줄러 인덱스 — `notified_at IS NULL` 부분 인덱스

ARCHITECTURE §2.3 이 정한 질의가 `WHERE occurrence_date = ? AND notify_offset = ?` 다.

```sql
CREATE INDEX ix_occurrence_due
    ON anniversary_occurrence (occurrence_date, notify_offset)
    INCLUDE (anniversary_id, member_id)
    WHERE notified_at IS NULL;
```

| | |
| --- | --- |
| **부분인 까닭** | 발사된 행은 인덱스에서 빠진다. 3년 뒤를 늘 들고 있으므로 **가동 3년째부터 인덱스가 전체의 절반, 5년째면 3/8** 이 된다. 지난 것이 미래 질의 비용에 안 섞인다 |
| **`member_id` 를 비정규화한 까닭** | 2단 발행(§3.4)의 Kafka 파티션 키가 memberId 다. `anniversary` 로 조인하지 않고 **Index Only Scan 으로 끝난다.** **비정규화가 안전한 것은 원본이 불변일 때뿐이고, `member_id` 는 불변이다** |
| **`title` 은 비정규화하지 않는다** | C-4 가 제목을 바꾼다. 바뀌는 것을 복제하면 §9.3 의 `dayCount` 와 같은 실수다. 페이로드를 만들 때 PK 로 3,000번 조회한다 — 한 건 수 µs |
| **`notified_at` 이 멱등의 1겹** | ARCHITECTURE §3.5 의 두 겹 앞에 붙는 0겹이다. 스케줄러가 중복 실행돼도 이 칼럼이 막는다 |

**자동 청소를 손본다.** 매일 3,000행 삽입 + 3,000행 갱신 + 3,000행 삭제인데 표가 325만 행이면
기본 임계(20%)로는 **자동 청소가 몇 달에 한 번 돈다.** 그러면 가시성 맵이 낡아 위의
Index Only Scan 이 조용히 힙 조회로 떨어진다.

```sql
ALTER TABLE anniversary_occurrence
  SET (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.01);
```

> **Index Only Scan 을 설계했으면 자동 청소도 같이 설계해야 한다.** 인덱스만 만들어 놓고
> `Heap Fetches` 가 0이 아닌 것을 나중에 발견하는 것이 이 자리의 전형적인 고장이다.

## 5.5 보존 — 파티셔닝 압력을 여기서 없앤다

> **`occurrence_date < CURRENT_DATE - 90` 인 행을 매일 지운다.**

```sql
CREATE INDEX ix_occurrence_purge
    ON anniversary_occurrence (occurrence_date)
    WHERE notified_at IS NOT NULL;
```

지난 발생일은 **알림이 갔다는 사실** 말고는 값어치가 없고, 그 사실은 90일이면 충분하다
(C-8 의 D+N 은 `anniversary.anchor_date` 를 보지 투영을 보지 않는다).

**이 한 줄이 §9 의 결론을 만든다.** 보존을 걸면 표가 **3.25년치에서 멈춘다.** 무한히 자라는
표에는 파티셔닝이 필요하지만, **상한이 있는 표에는 필요 없다.**

## 5.6 `anniversary` 는 **소프트 삭제를 하지 않는다**

`holiday` 와 정반대로 간다. 규칙을 하나로 적으면 이렇다.

> **소프트 삭제는 「원천이 되살릴 수 있는 자료」에만 쓴다.**

| | `holiday` | `anniversary` |
| --- | --- | --- |
| 삭제의 뜻 | 원천이 이번 주에 안 줬다 | **사람이 지웠다** |
| 되살아날 수 있나 | 있다 (§5.3 급감 가드가 그래서 있다) | 없다 |
| 안 지우고 들고 있으면 | 인덱스가 조금 큰다 | **남의 개인 자료를 허락 없이 보관한다** |

`ON DELETE CASCADE` 로 투영까지 같이 사라진다. `member-events` 로 탈퇴가 오면 그 회원의
`anniversary` 를 지우고, 연쇄로 투영이 따라간다.

---

# 6. `release` — 창 누적 upsert 와 `DateChange` (요구 5)

## 6.1 창 누적이 스키마에 요구하는 것

SPEC §12.2: 무료 키는 **다음 1건 · 지난 1건**만 준다. ARCHITECTURE §5.1: **10분마다.**

> 즉 **같은 경기를 하루에 144번 다시 본다.** 대부분 아무것도 안 바뀐 채로.

이것이 세 가지를 요구한다.

| | 요구 | 답 |
| --- | --- | --- |
| 1 | 원천의 id 로 같은 것을 알아본다 | `UNIQUE (source, external_id)` |
| 2 | **안 바뀌었으면 쓰지 않는다** | `source_hash` (§6.2) |
| 3 | 바뀐 것만 이력과 알림으로 흘린다 | `date_change` + Outbox 멱등키 (§6.3) |

## 6.2 `source_hash` — 「다시 쓰지 않기」를 한 칼럼으로 만든다

```sql
source_hash  char(32) NOT NULL,      -- 가변 필드들의 md5
last_seen_at timestamptz NOT NULL
```

`starts_at` · `status` · `postponed` · `venue` · `name` 을 정규화해 이은 문자열의 md5 다.

1차(창 누적, 회당 2~3건)에서는 **읽고-비교하고-쓴다**로 충분하다. 2~3행을 SELECT 해서 메모리에서
비교하는 것이 가장 읽기 쉽고, 예전 값이 손에 있어야 `date_change` 를 만들 수 있다.

**유료 키가 켜져 벌크가 열리면**(SPEC §12.2) 회당 수백~수천 건이 되고, 그때는 이렇게 바뀐다.

```sql
INSERT INTO sport_event (...) VALUES (...)
ON CONFLICT (source, external_id) DO UPDATE
   SET starts_at = EXCLUDED.starts_at, status = EXCLUDED.status,
       postponed = EXCLUDED.postponed, venue = EXCLUDED.venue,
       source_hash = EXCLUDED.source_hash, last_seen_at = EXCLUDED.last_seen_at,
       updated_at = now()
 WHERE sport_event.source_hash <> EXCLUDED.source_hash   -- ← 이 줄이 전부다
RETURNING id, external_id;
```

> **`WHERE` 절이 없으면 10분마다 모든 행이 죽은 튜플을 하나씩 남긴다.** 값이 안 바뀌었어도
> `UPDATE` 는 새 튜플을 쓰고, 인덱스 네 개에 항목을 넣고, 자동 청소가 뒤따라온다.
> 하루 144회 × 시즌 720경기면 **10만 개의 무의미한 죽은 튜플**이다.

`RETURNING` 이 **실제로 바뀐 것만** 돌려주므로 그 목록이 곧 `date_change` 와 Outbox 의 입력이 된다.
1차에서는 안 쓰지만 **`source_hash` 칼럼은 지금 넣어 둔다** — 나중에 더하면 그 시점의 전체 행에
해시를 채우는 마이그레이션이 붙는다.

**`last_seen_at` 은 해시가 같아도 갱신하고 싶은데, 그러면 위의 `WHERE` 와 부딪힌다.** 그래서
`last_seen_at` 은 `sport_event` 가 아니라 `sync_run_item` 이 들고 있는다 — **"언제 봤나" 는
동기화의 사실이지 경기의 사실이 아니다.** (위 DDL 의 `last_seen_at` 은 값이 바뀐 마지막 시각이다.)

## 6.3 `date_change` 에는 유일 제약을 걸지 않는다

ARCHITECTURE §3.5 가 경기 변경의 멱등키를 **`(externalId, fromTs, toTs)`** 로 못 박았다.
이 키를 `date_change` 에도 유일 제약으로 걸고 싶어진다. **걸면 안 된다.**

> 우천 순연이 흔한 리그다 (SPEC §12.1 이 KBO 를 고른 이유가 그것이다).
> `A → B → A → B` 가 실제로 일어나면, 세 번째 변경의 키가 첫 번째와 같다.
> **유일 제약을 걸면 그 변경이 이력에서 사라진다.**

| | 무엇을 요구하나 |
| --- | --- |
| **이력** (`date_change`) | **전부 남을 것.** "이 경기가 몇 번 밀렸나" 가 답이 돼야 한다 |
| **알림** (Outbox) | **중복은 안 보낼 것.** 같은 내용을 두 번 알리면 스팸이다 |

**요구가 다르므로 층을 나눈다.**

```sql
-- date_change: 유일 제약 없음. 순수 append-only
CREATE INDEX ix_date_change_subject
    ON date_change (subject_type, subject_id, detected_at DESC);

-- outbox_event: 여기에 §3.5 의 멱등키 유일 제약이 산다
CREATE UNIQUE INDEX uk_outbox_idem ON outbox_event (aggregate_type, idempotency_key);
```

**그 결과 `A→B→A→B` 는 이력에 4줄로 남고 알림은 2번 간다.** 세 번째 `A→B` 는 첫 번째와
같은 내용이라 안 간다. 이것은 **의도한 동작**이다 — 같은 말을 두 번 안 한다.
1차 뒤에 "되돌아온 것도 알려야 한다" 가 되면 멱등키에 `date_change.id` 를 더하면 되고,
그것은 **이력이 남아 있어야 가능한 변경**이다. 유일 제약을 걸었다면 그 길이 막혀 있었다.

## 6.4 `/teams/{id}/events/next` — `OR` 가 인덱스 정렬을 깨는 자리

경기는 홈팀과 원정팀을 갖는다. "이 팀의 다음 경기" 는 두 칼럼 중 아무 쪽이나 맞으면 된다.

| | 방법 | 계획 |
| --- | --- | --- |
| 가 | `WHERE home_team_id=? OR away_team_id=?` + `ORDER BY starts_at LIMIT 1` | **BitmapOr → Sort → Limit.** 비트맵을 거치면 인덱스의 정렬이 **사라지므로**, 그 팀의 시즌 경기 전부(144건)를 모아 정렬한 뒤 1건을 버린다 |
| **나** | **두 인덱스 + `UNION ALL` 로 각각 `LIMIT 1` 을 뽑고 둘 중 이른 것** | **Index Scan × 2 + Limit 1 씩.** 읽는 행이 정확히 2 |

```sql
CREATE INDEX ix_sport_event_home ON sport_event (home_team_id, starts_at);
CREATE INDEX ix_sport_event_away ON sport_event (away_team_id, starts_at);
```

```sql
(SELECT * FROM sport_event WHERE home_team_id = :t AND starts_at >= :now
  ORDER BY starts_at LIMIT 1)
UNION ALL
(SELECT * FROM sport_event WHERE away_team_id = :t AND starts_at >= :now
  ORDER BY starts_at LIMIT 1)
ORDER BY starts_at LIMIT 1;
```

144행 정렬이 느려서가 아니다. **`OR` 가 인덱스 정렬을 못 쓰게 만든다는 사실이 시즌 전수가
열리는 날(유료 키) 그대로 커지기 때문**이고, 그때 고치려면 질의를 다시 쓰게 된다.

**참가자 모델의 한계를 적어 둔다.** `home_team_id`/`away_team_id` 두 칼럼은 원천의 모양이고
KBO 에 맞는다. **테니스 토너먼트나 모터스포츠처럼 참가자가 N 인 종목을 켜는 날 이 두 칼럼이
깨진다.** 그때 `sport_event_participant(event_id, team_id, role)` 가 필요하고, 그것은
**스키마 변경이지 설정 변경이 아니다.** SPEC §D-5 의 "범용" 이 저장 구조까지 범용이라는
뜻은 아니라는 것을 여기 적어 둔다.

## 6.5 `watch` — 양방향 인덱스

```sql
CREATE UNIQUE INDEX uk_watch        ON watch (member_id, subject_type, subject_id);
CREATE INDEX        ix_watch_fanout ON watch (subject_type, subject_id) INCLUDE (member_id);
```

`uk_watch` 가 `GET /me/watches`(D-3) 와 중복 등록 방지를 동시에 한다.
`ix_watch_fanout` 이 ARCHITECTURE §3.4 의 **2단 발행에서 펼치는 쪽**이다 — 사실 이벤트 하나를
받아 `WHERE subject_type=? AND subject_id=?` 로 수신자를 꺼내고, `member_id` 가 `INCLUDE` 에
있어 Index Only Scan 이다. **ARCHITECTURE §10-11 (fan-out 상한) 이 걸리는 표가 이것**이고,
상한이 필요해지면 이 인덱스에 커서 페이지네이션(`... AND member_id > :last ORDER BY member_id`)
을 붙일 수 있게 `member_id` 가 이미 인덱스 안에 있다.

## 6.6 1차 이후 — 검색 (한 줄만)

`sport_event.name` · `movie.title_ko` · `movie.title_en` 을 **소유 테이블의 평범한 텍스트
칼럼**으로 둔다. 색인이 필요해지는 날 `pg_trgm` GIN 이든 별도 엔진이든 **더하기만** 하면 된다.
**색인 스키마는 설계하지 않는다** (SPEC §7 — 1차 밖).

---

# 7. Outbox 한 테이블 (요구 6)

ARCHITECTURE §3.3 이 정했다 — 도메인마다가 아니라 하나.

## 7.1 한 테이블이 스키마에 남기는 자국 둘

```sql
aggregate_type  varchar(30) NOT NULL,     -- HOLIDAY | ANNIVERSARY | SPORT_EVENT | MOVIE
aggregate_id    varchar(64) NOT NULL,     -- ← bigint 가 아니다
partition_key   varchar(64) NOT NULL,     -- ← 새 칼럼
idempotency_key varchar(200) NOT NULL,
```

| | 왜 |
| --- | --- |
| `aggregate_id` 가 **문자열** | 저장소 전례(`MemberOutboxEvent` · 3세대 `OutboxEvent`)는 `Long` 이다. 그런데 `release` 의 집합체 식별자는 **원천의 문자열 id**(`idEvent: "2400325"`) 이고 `anniversary` 는 Snowflake 다. 한 표에 둘이 살면 **넓은 쪽을 택하는 것 말고 선택지가 없다** |
| `partition_key` 가 **따로 있다** | 3세대 릴레이는 `aggregateId` 를 Kafka 키로 쓴다. 그런데 §3.5 는 토픽마다 키가 다르다 — `dday.release.changed` 는 외부 경기 id, `dday.notification.requested` 는 **memberId** 다. 집합체 id 와 파티션 키가 **다른 값**이므로 칼럼을 나눈다. 안 나누면 릴레이가 페이로드를 파싱해 키를 뽑게 되고, **릴레이가 페이로드의 모양을 알게 되는 순간 도메인마다 분기가 생긴다** |

**한 테이블의 대가는 ARCHITECTURE 가 말한 대로 `aggregate_type → 토픽` 매핑 하나로 끝난다.**
스키마 쪽 대가는 위의 두 칼럼이 조금 넓어지는 것뿐이다.

## 7.2 폴링 인덱스 — 적체돼도 Top-N 이 되게

3세대 릴레이의 claim 질의는 이렇다.

```sql
SELECT * FROM outbox_event
 WHERE status = 'PENDING'
    OR (status = 'SENDING' AND updated_at < :staleThreshold)
 ORDER BY created_at
 LIMIT 100
 FOR UPDATE SKIP LOCKED;
```

| | 후보 | 평가 |
| --- | --- | --- |
| 가 | `(status, created_at)` 전체 인덱스 | 3세대가 쓰는 모양(`idx_outbox_status_created`). **`PUBLISHED` 수백만 행이 인덱스에 그대로 산다.** `OR` 때문에 BitmapOr 로 가면 정렬이 깨진다 |
| 나 | `(created_at)` 전체 인덱스 | 발행된 것까지 훑는다 |
| **다** | **`(created_at) WHERE status IN ('PENDING','SENDING')` 부분 인덱스** | — |

```sql
CREATE INDEX ix_outbox_claim ON outbox_event (created_at)
    WHERE status IN ('PENDING','SENDING');
```

질의는 **부분 인덱스의 술어를 그대로 한 줄 더 적어 주어야** 플래너가 그것을 고른다.

```sql
 WHERE status IN ('PENDING','SENDING')                       -- ← 인덱스 술어와 같은 모양
   AND (status = 'PENDING' OR updated_at < :staleThreshold)  -- ← 나머지는 필터
 ORDER BY created_at LIMIT 100
```

**적체 시 동작이 이 설계의 전부다.**

> `PENDING` 이 100만 건 쌓여도 인덱스가 `created_at` 순이므로 계획은
> **`Limit → Index Scan`** 이다. **오래된 100건만 읽고 멈춘다.**
> 「가」·「나」였다면 100만 건을 모아 정렬한 뒤 100건을 취한다 — **적체될수록 릴레이가
> 느려지고, 느려질수록 더 적체된다.**

그리고 `PUBLISHED` 수백만 행이 이 인덱스에 **아예 안 들어온다.** 보존 정책(§10)이 늦어도
폴링 비용이 안 오른다.

### `FOR UPDATE SKIP LOCKED` 를 더한다

ARCHITECTURE §3.2 가 ShedLock 을 걸었으므로 원칙적으로 claim 자는 하나다.
**그런데 ShedLock 은 `lockAtMostFor` 가 지나면 락을 놓는다.** 릴레이가 GC 로 멈추거나
Kafka `get(5, SECONDS)` 가 연달아 걸리면 **두 인스턴스가 같은 100건을 집는 창**이 생긴다.
`SKIP LOCKED` 는 그 창을 무해하게 만든다. 비용이 없다.

### 그리고 HOT 갱신은 안 된다 — 알고 받아들인다

`status` 가 **부분 인덱스의 술어 칼럼**이므로, `PENDING → SENDING → PUBLISHED` 갱신은
HOT 최적화를 못 받는다(PostgreSQL 은 술어·식에 쓰인 칼럼도 HOT 차단 대상으로 센다).
즉 갱신마다 인덱스 항목이 새로 생긴다.

> **그래도 「다」가 유리하다.** 얻는 것은 *`PUBLISHED` 수백만 행이 폴링 인덱스에 없다* 이고,
> 잃는 것은 *하루 수천 건의 비-HOT 갱신* 이다. 자릿수가 다르다.
> `fillfactor` 를 낮추는 것은 **이 표에서 의미가 없다** (HOT 이 애초에 안 되므로).
> 대신 자동 청소를 공격적으로 건다.

```sql
ALTER TABLE outbox_event
  SET (autovacuum_vacuum_scale_factor = 0.01, autovacuum_vacuum_cost_delay = 0);
```

**Outbox 의 진짜 위험은 인덱스 선택이 아니라 팽창(bloat)이다.** 행 하나가 사는 동안 최소
세 번 갱신되고 곧 지워지는 표라서 그렇다.

## 7.3 멱등키 유일 제약이 만드는 함정 하나

```sql
CREATE UNIQUE INDEX uk_outbox_idem ON outbox_event (aggregate_type, idempotency_key);
```

`aggregate_type` 을 같이 넣는 까닭: 두 도메인이 같은 모양의 키를 만들 수 있다
(`"123:2027-03-15"`). 한 표를 공유하는 대가다.

> ### ⚠ `append` 는 반드시 `ON CONFLICT DO NOTHING` 으로 넣어야 한다.
>
> 창 누적은 **같은 경기를 계속 다시 보므로**(SPEC §12.2) 중복 `append` 가 **정상 경로**다.
> 그런데 JPA 로 그냥 `save()` 하면 유일 제약 위반이 `ConstraintViolationException` 으로
> 튀고, **그 순간 그 트랜잭션이 rollback-only 로 표시된다.** 그러면 같은 트랜잭션 안에 있던
> `SportEvent` 갱신과 `DateChange` 저장까지 통째로 날아간다.
>
> **ARCHITECTURE §3.5 가 "`DateChange` 와 Outbox 가 같은 트랜잭션에 있는 것이 D-4 의 전부"
> 라고 못 박은 그 트랜잭션이, 멱등 방어 때문에 깨지는 것이다.** 방어가 지키려던 것을
> 방어가 부순다.

`DomainOutbox.append` 의 구현은 **네이티브 `INSERT ... ON CONFLICT DO NOTHING`** 이어야 한다.
`@Modifying` 네이티브 질의 한 줄이고, 반환값 `0` 이 돌아오면 "이미 있다" 는 뜻이므로 그것을
정상으로 센다.

---

# 8. `sync_run` · `sync_run_item` · `holiday_coverage` (요구 7)

## 8.1 규모

| | 주기 | 회차/년 | 회차당 item | item/년 |
| --- | --- | --- | --- | --- |
| `HOLIDAY` | 주 1회 | 52 | **1,020** (204국 × 5년) | 53,000 |
| `SPORT_EVENT` | 10분 | **52,560** | 2~3 | 130,000 |
| `MOVIE` | 일 1회 | 365 | 국가 수만큼 | ~2,000 |
| **합** | | **≈ 53,000 run** | | **≈ 185,000 item** |

**`sync_run` 이 `sync_run_item` 보다 적지만 자릿수가 같다.** 10분 스케줄이 "아무것도 안
바뀌었다" 를 하루 144번 기록하기 때문이다. 이것이 보존 정책을 갈래로 나누는 이유다.

## 8.2 보존 — 대상마다 다르게

> | 대상 | `sync_run` | 까닭 |
> | --- | --- | --- |
> | `HOLIDAY` | **90일** | 회차가 주 1회라 90일이 **13회차**다. §5.3 급감 가드와 §3.3 의 3회차 규칙을 사람이 눈으로 따라갈 수 있는 길이 |
> | `SPORT_EVENT` | **14일** | 회차가 심박이지 사건이 아니다. 14일이면 상주 행이 **2,000** 으로 묶인다 |
> | `MOVIE` | 90일 | |
>
> `sync_run_item` 은 **`ON DELETE CASCADE` 로 부모를 따라간다.** 자체 보존 규칙을 두지 않는다.

**자체 규칙을 안 두는 까닭:** 두면 "부모는 있는데 자식이 없는 회차" 가 생기고, `GET /admin/sync/runs/{runId}`
(ARCHITECTURE §11-2 가 추가를 제안한 그것)가 **빈 목록과 "원래 0건이었다" 를 구별 못 한다.**
회차는 통째로 있거나 통째로 없다.

**청소 질의는 Seq Scan 을 받아들인다.**

```sql
DELETE FROM sync_run
 WHERE (target = 'SPORT_EVENT' AND started_at < now() - INTERVAL '14 days')
    OR (target <> 'SPORT_EVENT' AND started_at < now() - INTERVAL '90 days');
```

`started_at` 단독 인덱스를 만들지 **않는다.** 만들면 **연 53,000번의 삽입마다 항목이 하나 더
생기는데**, 그것을 쓰는 것은 **하루 한 번의 청소뿐**이다. 5만 행 Seq Scan 은 수 ms 다.

> **인덱스를 안 만드는 결정도 결정이고, 근거는 읽기 횟수 대 쓰기 횟수다.**
> 여기서는 쓰기 53,000 : 읽기 365 로 **145 대 1** 이다.

## 8.3 재시도 인덱스 — 부분으로

ARCHITECTURE §5.2: *"실패 항목은 다음 회차에 우선 재시도"*.

```sql
CREATE INDEX ix_sync_run_item_run ON sync_run_item (sync_run_id);   -- CASCADE 용 + 회차 조회
CREATE INDEX ix_sync_run_item_bad
    ON sync_run_item (target, country_code, holiday_year)
    WHERE status IN ('FAILED','ABORTED');
```

`ix_sync_run_item_bad` 가 **부분인 것이 핵심**이다. 185,000행 중 실패는 **정상이면 0에 가깝다**
(SPEC §11.4). 즉 **이 인덱스는 거의 비어 있고, 그래서 거의 공짜다.** 삽입 185,000번 중
항목이 실제로 생기는 것은 실패한 건뿐이다.

**`ix_sync_run_item_run` 은 지울 수 없다** — `ON DELETE CASCADE` 가 부모 삭제마다 이 인덱스를
쓴다. 없으면 하루 한 번의 청소가 **185,000행 전체 스캔 × 삭제되는 회차 수**가 된다.
§5.3 에서 PK 순서가 공짜로 해결한 문제를 여기서는 인덱스로 사 온다.

## 8.4 `holiday_coverage` — 새로 더하는 표 하나

```sql
CREATE TABLE holiday_coverage (
    country_code         varchar(2) NOT NULL REFERENCES country(code),
    holiday_year         smallint   NOT NULL,
    last_ok_run_id       bigint,
    last_ok_at           timestamptz,
    source_count         integer    NOT NULL DEFAULT 0,   -- 원천이 준 건수
    stored_count         integer    NOT NULL DEFAULT 0,   -- 우리가 담은 건수
    dropped_count        integer    NOT NULL DEFAULT 0,
    consecutive_failures smallint   NOT NULL DEFAULT 0,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    PRIMARY KEY (country_code, holiday_year)
);
```

**1,020행. 인덱스는 PK 하나.** 이 표가 세 가지를 한꺼번에 푼다.

| 쓰이는 곳 | 이 표가 없으면 |
| --- | --- |
| **§5.3 급감 가드** — "직전 성공 회차 대비 절반 미만인가" | `sync_run_item` 이력을 **(국가, 연도)마다** 뒤져야 한다. 한 회차에 **1,020번**의 `ORDER BY created_at DESC LIMIT 1` 이다. 여기서는 **PK 단건 조회 1,020번** |
| **§4.1 정의역** (`GET /meta/coverage`) | `holiday` 를 `GROUP BY` 해야 하는데, 그러면 **"동기화했는데 공휴일이 0건인 나라"** 와 **"한 번도 동기화 안 한 나라"** 를 **구별할 수 없다.** 전자는 200 이고 후자는 400 이어야 한다 (ARCHITECTURE §4.1) |
| **§5.2 "3회 연속 실패하면 알람"** | 이력을 세야 한다. 여기서는 칼럼 하나 |

> **정의역을 `holiday` 에서 유도하려 들면 반드시 틀린다.** 없는 것과 비어 있는 것은
> 자료로 구별해야 하고, 그것이 이 표가 존재하는 이유다.

`source_count` 와 `stored_count` 를 **둘 다** 둔다. 차이가 곧 A-10 이 거른 비-`Public` 건수이고,
`dropped_count` 는 §9.3 의 "황금연휴가 우리 코퍼스에 안 걸려 버린 건수" 다.
**SPEC §11.4 가 「평소 0이어야 정상」이라 했으므로 0이 아닌 것 자체가 알람 조건**이다.

---

# 9. 파티셔닝 (요구 8)

## 9.1 표별로 재 본다

| 표 | 1차 상주 행 | 상한이 있나 | 파티셔닝 |
| --- | --- | --- | --- |
| `holiday` | 14,000 | 있다 (연도 보유 범위 × 204국) | **없다** |
| `long_weekend` | 7,500 | 있다 | **없다** |
| `country` · `holiday_coverage` · `holiday_name_label` | 204 · 1,020 · 수백 | 있다 | **없다** |
| `anniversary` | 회원 × 5 | 없다 | **없다** — 행이 작고 질의가 `member_id` 단건이다 |
| **`anniversary_occurrence`** | 회원 × 32.5 | **있다 (§5.5 의 90일 보존이 3.25년으로 묶는다)** | **없다. 방아쇠는 §9.3** |
| `outbox_event` | 7일 × ~3,000 = **21,000** | 있다 (보존) | **없다** |
| `sync_run` · `sync_run_item` | ~7,000 · ~20,000 | 있다 (보존) | **없다** |
| `sport_event` | KBO 720/시즌 × 시즌 수 | 사실상 있다 | **없다** |
| `date_change` · `movie` · `movie_release` · `watch` | 수천~수만 | | **없다** |

## 9.2 결론 — **1차에는 파티셔닝이 필요한 표가 하나도 없다**

파티셔닝이 값어치를 내는 조건은 셋이고, **1차에는 셋 다 성립하지 않는다.**

| 조건 | 1차에서 |
| --- | --- |
| (가) **대량 삭제를 `DROP PARTITION` 으로 바꿀 수 있다** | 제일 큰 표의 보존이 이미 §5.5 의 일일 범위 삭제로 풀렸다. 하루 3,000행 삭제에 파티션 관리를 붙일 이유가 없다 |
| (나) **인덱스가 메모리에 안 들어간다** | 전체 인덱스를 다 더해도 10만 회원 기준 **1GB 미만**이다 |
| (다) **질의가 늘 파티션 키로 좁혀진다** | `anniversary_occurrence` 만 그렇고(`occurrence_date` 동등), 그 질의는 **파티셔닝 없이도 이미 인덱스 단건 탐색**이다 |

> **파티셔닝은 성능 도구이기 전에 운영 부담이다.** 파티션 생성 스케줄러 · 마이그레이션 ·
> Hibernate 매핑 · `validate` 동작 확인이 전부 따라온다. **그것을 지불할 이유를 아직 못 찾았고,
> 못 찾았다는 사실을 여기 적어 두는 것이 이 절의 내용이다.**

## 9.3 언제 다시 열 것인가 — 방아쇠를 지금 적어 둔다

> ### `anniversary_occurrence` 가 **3,000만 행**을 넘거나,
> ### 일일 청소가 **자동 청소를 못 따라가서 팽창이 보이면** 그때 연다.
>
> 모양: `PARTITION BY RANGE (occurrence_date)`, 연 단위. 일일 `DELETE` 를 **연 1회
> `DETACH` + `DROP`** 으로 바꾼다.
>
> **치를 비용:** PK 가 이미 `occurrence_date` 를 포함하므로(§5.3) **유일 제약은 안 고친다.**
> 고칠 것은 `ix_occurrence_due` 를 파티션마다 다시 만드는 것과, 파티션 생성 스케줄러 하나다.

**`outbox_event` 는 파티션 후보처럼 보이지만 아니다.** 고회전 표라 솔깃하지만, 해법이
**보존(§10) + 부분 인덱스(§7.2)** 로 이미 나와 있다. 상주 21,000행짜리 표를 쪼개는 것은
문제를 못 풀고 운영만 늘린다.

---

# 10. 보존 정책 한 장

한 곳에 모아 둔다. **보존이 §9 의 결론을 만들었으므로 이 표가 지켜지지 않으면 §9 도 무효다.**

| 표 | 무엇을 | 언제 | 어떻게 | 스케줄 락 이름 |
| --- | --- | --- | --- | --- |
| `holiday` · `long_weekend` | `deleted_at` 행 | **성공 회차 3번 뒤** | 물리 삭제 | `dday-sync-HOLIDAY` 안에서 |
| `anniversary_occurrence` | `occurrence_date < today-90` | 매일 | `DELETE` (`ix_occurrence_purge`) | `dday-retention` |
| `outbox_event` | `status='PUBLISHED' AND published_at < now()-7d` | 매일 | `DELETE` (`ix_outbox_purge`) | `dday-retention` |
| `outbox_event` | `status='FAILED'` | **안 지운다** | 사람이 본다. 0이 아닌 것이 알람 | — |
| `sync_run` (+ item CASCADE) | `SPORT_EVENT` 14일 / 나머지 90일 | 매일 | `DELETE` (Seq Scan 허용 — §8.2) | `dday-retention` |
| `date_change` | 1년 | 매일 | `DELETE` (`ix_date_change_subject` 는 못 쓴다 → `detected_at` 인덱스 추가) | `dday-retention` |

**청소 스케줄을 하나로 묶는다** (`dday-retention`). ARCHITECTURE §5.6 의 락 목록에 여덟 번째로
더해지고, §5.7 의 풀 크기 8 이 아홉이 되므로 **`pool.size` 를 10 으로 올린다.**
— 이 문서가 ARCHITECTURE 에 되돌리는 유일한 요구다.

`date_change` 청소를 위해 인덱스가 하나 더 필요하다.

```sql
CREATE INDEX ix_date_change_detected ON date_change (detected_at);
```

수천 행짜리 표라 지금은 없어도 되지만, **보존 규칙을 적어 놓고 그것을 실행할 인덱스를 안
만드는 것**이 나중에 "청소가 왜 이렇게 오래 걸리지" 가 된다. 삽입이 드문 표라 대가가 없다.

**청소는 전부 `LIMIT` 을 건 반복 삭제로 한다** (`DELETE ... WHERE ctid IN (SELECT ctid ... LIMIT 5000)`).
한 번에 백만 행을 지우면 트랜잭션이 길어지고 복제 지연이 생긴다 — ARCHITECTURE §5.2 가
동기화에 적용한 원칙과 같다.

---

# 11. DDL 을 어디에 어떤 형태로 두나 (요구 9)

## 11.1 지금의 공백

확인한 사실 그대로다.

- 마이그레이션 도구가 **저장소 어디에도 없다.** Flyway · Liquibase 둘 다 없다.
- 운영 프로필은 `ddl-auto: validate`, 테스트는 `create-drop`.
- **즉 아무도 테이블을 만들지 않는다.** `validate` 는 "이미 있는 것" 을 검사만 한다.
- 저장소에 DDL 이 있는 곳은 `apps/promotion-service/src/main/resources/scripts/table.sql`
  **하나뿐**인데, 그것은 **MySQL 문법**이다 (`DATETIME`, `ON UPDATE CURRENT_TIMESTAMP`,
  `INDEX` 를 테이블 정의 안에). PostgreSQL 에서 돌지 않는다. **본보기로 쓸 수 없다.**
- `d-day-service/application.yml` 에 **datasource 설정 자체가 없다.**

## 11.2 갈래 넷

| | 방법 | 버린 까닭 |
| --- | --- | --- |
| 가 | `docs/` 에 DDL 을 적어 두고 사람이 `psql` 로 돌린다 | **아무도 안 돌린다.** 첫 사람이 "테이블이 없다" 를 만나면 가장 자연스러운 반사가 `ddl-auto: update` 로 바꾸는 것이고, **그 순간 부분 인덱스·`CHECK`·`INCLUDE` 가 전부 사라진다. 그리고 `validate` 는 그것을 못 잡는다** (§1.5) |
| 나 | `schema.sql` (`spring.sql.init`) | 매 부팅마다 돌리려면 멱등해야 하고, 그러면 **`IF NOT EXISTS` 범벅**이 된다. 무엇보다 **변경 이력이 없다.** 2차 스키마 변경에서 바로 막힌다 |
| 다 | Liquibase | XML/YAML 로 DDL 을 다시 쓰는 의식이 붙는다. 우리 DDL 은 **PostgreSQL 전용 기능(부분 인덱스·`INCLUDE`·`CHECK` 식)이 요점**이라 추상화 계층이 전부 `<sql>` 태그로 빠져나간다. **얻는 것 없이 층만 는다** |
| **라** | **Flyway** | — |

## 11.3 Flyway 로 간다 — 저장소 최초다

```
apps/d-day/d-day-service/src/main/resources/db/migration/
└── V1__baseline.sql          ← §12 의 전문
```

```gradle
// apps/d-day/d-day-service/build.gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

```yaml
# application-home.yml / application-docker.yml (PostgreSQL)
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/dday_service_db}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: false        # 빈 DB 에서 시작한다. 기존 스키마가 없다
    validate-on-migrate: true

# application-local.yml (H2 · 테스트)
spring:
  datasource:
    url: jdbc:h2:mem:dday;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false                    # ← H2 에서는 끈다
```

### 왜 저장소 최초라도 지금 들이나 — 근거 넷

1. **`validate` 는 이미 누군가 스키마를 만들었다고 가정한다.** 그 "누군가" 가 저장소에 없다.
   **공백을 메우는 것이지 새 층을 얹는 것이 아니다.**
2. **이 문서가 정한 것의 대부분을 Hibernate 가 생성할 수 없다.** 부분 인덱스 · `INCLUDE` ·
   식 `CHECK` · `autovacuum` 파라미터. **둘 자리가 없으면 존재할 수 없다.**
3. **`flyway:validate` 가 §1.5 의 빈칸을 메운다.** Hibernate `validate` 가 못 보는 것을
   Flyway 가 체크섬으로 본다. 둘이 겹치지 않고 보완한다.
4. **d-day 가 저장소에서 스키마가 제일 복잡한 서비스다.** 18개 표 · 제약 12개 · 인덱스 25개.
   여기서 안 하면 어디서도 안 한다.

### H2 에서 끄는 대가와 그 대가를 막는 테스트

Flyway 를 H2 에서 끄면 **테스트 DB 는 엔티티에서, 운영 DB 는 Flyway 에서** 나온다. 둘이 갈라질
수 있다. **이미 있는 픽스처로 막는다** — `libs/storage-db/src/testFixtures/.../PostgresTestConfig.java`
가 Testcontainers PostgreSQL 을 `@ServiceConnection` 으로 띄운다.

> **드리프트 테스트 하나.** Testcontainers PostgreSQL 을 띄우고 → Flyway 로 마이그레이션하고 →
> `hibernate.hbm2ddl.auto=validate` 로 컨텍스트를 올린다. **뜨면 통과.**
> 엔티티에 칼럼을 더하고 마이그레이션을 안 적으면 **CI 가 거기서 빨개진다.**

**인덱스는 그 테스트도 못 본다** (§1.5). 그래서 하나 더.

> **인덱스 존재 테스트.** 같은 컨테이너에서 `pg_indexes` 를 읽어 **이 문서가 정한 인덱스
> 이름 25개가 전부 있는지** 단언한다. 이름 목록을 테스트가 들고 있게 하면, 누가 인덱스를
> 지우거나 이름을 바꿀 때 **그 이유를 적게 강제된다.**

테스트 둘 다 `@SpringBootTest` 라 느리므로 **태그를 붙여 별도 Gradle 태스크로 뺀다** —
ARCHITECTURE §1.2 가 `astro-core` 검산점에서 한 걱정과 같은 종류다.

## 11.4 규칙 셋 — 앞으로 이렇게 굴린다

| | 규칙 |
| --- | --- |
| R1 | **`V{n}__{설명}.sql` 은 한 번 머지되면 못 고친다.** 고치면 체크섬이 깨져 부팅이 막힌다. 잘못은 다음 번호로 고친다 |
| R2 | **정합성 제약(`UNIQUE`·`CHECK`·`NOT NULL`)은 마이그레이션과 엔티티 양쪽에 적는다.** H2 에서도 살아야 한다 (§1.5-2) |
| R3 | **인덱스는 마이그레이션에만 적는다.** `@Table(indexes=...)` 는 쓰지 않는다 (§1.5-3) |

**운영에 자료가 찬 뒤의 인덱스 추가는 `CREATE INDEX CONCURRENTLY` 로 한다.** 다만
`CONCURRENTLY` 는 트랜잭션 안에서 못 돌므로 그 마이그레이션 파일 머리에
`-- flyway:executeInTransaction=false` 를 적는다. **`V1__baseline.sql` 은 빈 DB 에 도는
것이므로 `CONCURRENTLY` 를 쓰지 않는다** — 빈 표에 락을 피할 이유가 없고,
`CONCURRENTLY` 는 실패 시 무효 인덱스를 남긴다.

---

# 12. `V1__baseline.sql` — 전문

> PostgreSQL 15+ 문법. 순서는 FK 의존을 따른다.

```sql
-- =============================================================
-- d-day-service baseline schema
-- 근거: docs/SCHEMA.md · SPEC.md §9/§11/§12 · ARCHITECTURE.md
-- =============================================================

-- -------------------------------------------------------------
-- country  (SPEC §9.2) — CLDR 시드. 동기화 대상이 아니다.
-- -------------------------------------------------------------
CREATE TABLE country (
    code              varchar(2)   PRIMARY KEY,
    name_en           varchar(100) NOT NULL,
    name_ko           varchar(100) NOT NULL,
    primary_zone_id   varchar(64)  NOT NULL,
    zone_is_ambiguous boolean      NOT NULL DEFAULT false,
    weekend_mask      smallint     NOT NULL,
    created_at        timestamp(6) NOT NULL,
    updated_at        timestamp(6) NOT NULL,
    CONSTRAINT ck_country_code    CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_country_weekend CHECK (weekend_mask BETWEEN 1 AND 127)
);
COMMENT ON COLUMN country.weekend_mask IS
  'ISO-8601 요일 비트마스크. bit0=월 … bit6=일. 토일=96, 금토=48, 일요일만=64';
COMMENT ON COLUMN country.zone_is_ambiguous IS
  'true 면 시간대 여럿 중 대표를 고른 것. 응답에 표시한다 (SPEC §9.9(4))';

-- -------------------------------------------------------------
-- holiday  (SPEC §9.3 · §11.3)
-- -------------------------------------------------------------
CREATE TABLE holiday (
    id               bigint        PRIMARY KEY,
    country_code     varchar(2)    NOT NULL REFERENCES country(code),
    holiday_date     date          NOT NULL,
    holiday_year     smallint      NOT NULL,
    name_en          varchar(200)  NOT NULL,
    name_local       varchar(200),
    name_slug        varchar(120)  NOT NULL,
    subdivision_key  varchar(1000) NOT NULL DEFAULT '',
    is_global        boolean       NOT NULL,
    is_fixed         boolean       NOT NULL,
    types_raw        varchar(200)  NOT NULL,
    is_public        boolean       NOT NULL,
    launch_year      smallint,
    last_seen_run_id bigint        NOT NULL,
    deleted_at       timestamptz,
    created_at       timestamp(6)  NOT NULL,
    updated_at       timestamp(6)  NOT NULL,

    CONSTRAINT ck_holiday_year   CHECK (holiday_year = EXTRACT(YEAR FROM holiday_date)::smallint),
    CONSTRAINT ck_holiday_types  CHECK (types_raw ~ '^[A-Za-z]+(,[A-Za-z]+)*$'),
    CONSTRAINT ck_holiday_public CHECK (is_public = (strpos(',' || types_raw || ',', ',Public,') > 0)),
    CONSTRAINT ck_holiday_global CHECK (NOT (is_global AND subdivision_key <> '')),
    CONSTRAINT ck_holiday_subdiv CHECK (
            subdivision_key !~ '[[:space:]]'
        AND subdivision_key !~ ',,'
        AND subdivision_key !~ '^,'
        AND subdivision_key !~ ',$'
    )
);
COMMENT ON COLUMN holiday.subdivision_key IS
  '지역 코드를 정렬해 쉼표로 이은 것. 전국이면 빈 문자열(NULL 아님 — 유일 제약이 무력해진다)';
COMMENT ON COLUMN holiday.types_raw IS
  'Nager types 배열을 정렬해 쉼표로 이은 것. Public 만 노출하되 전부 저장한다 (SPEC A-10)';

CREATE UNIQUE INDEX uk_holiday_natural
    ON holiday (country_code, holiday_date, name_en, subdivision_key);

CREATE INDEX ix_holiday_country_year
    ON holiday (country_code, holiday_year)
    INCLUDE (holiday_date, name_en, name_local, name_slug,
             subdivision_key, is_global, is_public, launch_year)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_holiday_on_date
    ON holiday (holiday_date)
    INCLUDE (country_code, name_slug, is_global)
    WHERE is_public AND deleted_at IS NULL;

CREATE INDEX ix_holiday_axis_name
    ON holiday (holiday_year, name_slug, country_code)
    WHERE is_public AND deleted_at IS NULL;

CREATE INDEX ix_holiday_axis_country
    ON holiday (holiday_year, is_global, country_code, holiday_date)
    WHERE is_public AND deleted_at IS NULL;

-- -------------------------------------------------------------
-- long_weekend  (SPEC §9.3 · §11.1)
-- -------------------------------------------------------------
CREATE TABLE long_weekend (
    id               bigint       PRIMARY KEY,
    country_code     varchar(2)   NOT NULL REFERENCES country(code),
    holiday_year     smallint     NOT NULL,
    start_date       date         NOT NULL,
    end_date         date         NOT NULL,
    need_bridge      boolean      NOT NULL,
    bridge_days      varchar(200) NOT NULL DEFAULT '',
    last_seen_run_id bigint       NOT NULL,
    deleted_at       timestamptz,
    created_at       timestamp(6) NOT NULL,
    updated_at       timestamp(6) NOT NULL,
    CONSTRAINT ck_lw_range CHECK (end_date >= start_date),
    CONSTRAINT ck_lw_year  CHECK (holiday_year = EXTRACT(YEAR FROM start_date)::smallint)
);
COMMENT ON COLUMN long_weekend.bridge_days IS
  '원천이 주는 값이다. 계산하지 않는다 (SPEC §9.3). dayCount 는 담지 않는다';

CREATE UNIQUE INDEX uk_long_weekend_natural
    ON long_weekend (country_code, start_date, end_date);

CREATE INDEX ix_long_weekend_country_year
    ON long_weekend (country_code, holiday_year)
    INCLUDE (start_date, end_date, need_bridge, bridge_days)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_long_weekend_rank
    ON long_weekend (holiday_year, country_code, start_date, end_date)
    WHERE deleted_at IS NULL;

-- -------------------------------------------------------------
-- holiday_name_label  (SPEC §9.9(1))
-- -------------------------------------------------------------
CREATE TABLE holiday_name_label (
    name_slug      varchar(120) NOT NULL,
    lang           varchar(5)   NOT NULL,
    label          varchar(200) NOT NULL,
    sample_name_en varchar(200) NOT NULL,
    created_at     timestamp(6) NOT NULL,
    updated_at     timestamp(6) NOT NULL,
    PRIMARY KEY (name_slug, lang)
);
COMMENT ON COLUMN holiday_name_label.sample_name_en IS
  '사람이 표를 채울 때 보는 참고값. 응답에 나가지 않는다';

-- -------------------------------------------------------------
-- holiday_coverage  (§8.4) — 정의역과 급감 가드의 근거
-- -------------------------------------------------------------
CREATE TABLE holiday_coverage (
    country_code         varchar(2) NOT NULL REFERENCES country(code),
    holiday_year         smallint   NOT NULL,
    last_ok_run_id       bigint,
    last_ok_at           timestamptz,
    source_count         integer    NOT NULL DEFAULT 0,
    stored_count         integer    NOT NULL DEFAULT 0,
    dropped_count        integer    NOT NULL DEFAULT 0,
    consecutive_failures smallint   NOT NULL DEFAULT 0,
    created_at           timestamp(6) NOT NULL,
    updated_at           timestamp(6) NOT NULL,
    PRIMARY KEY (country_code, holiday_year)
);

-- -------------------------------------------------------------
-- member_reference  (SPEC §9.6) — auth-service 가 원본
-- -------------------------------------------------------------
CREATE TABLE member_reference (
    member_id  bigint       PRIMARY KEY,
    email      varchar(255),
    nickname   varchar(100),
    zone_id    varchar(64),
    status     varchar(20)  NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE','WITHDRAWN'))
);

-- -------------------------------------------------------------
-- anniversary  (SPEC §9.6)
-- -------------------------------------------------------------
CREATE TABLE anniversary (
    id              bigint       PRIMARY KEY,
    member_id       bigint       NOT NULL,
    title           varchar(100) NOT NULL,
    anchor_date     date         NOT NULL,
    calendar        varchar(10)  NOT NULL,
    leap_policy     varchar(12)  NOT NULL,
    recurrence      varchar(10)  NOT NULL,
    count_direction varchar(10)  NOT NULL,
    zone_id         varchar(64)  NOT NULL,
    notify_offsets  varchar(50)  NOT NULL DEFAULT '',
    expanded_until  date,
    created_at      timestamp(6) NOT NULL,
    updated_at      timestamp(6) NOT NULL,
    CONSTRAINT ck_anniv_calendar CHECK (calendar IN ('SOLAR','LUNAR')),
    CONSTRAINT ck_anniv_leap     CHECK (leap_policy IN ('LEAP_ONLY','PLAIN_ONLY','EITHER')),
    CONSTRAINT ck_anniv_recur    CHECK (recurrence IN ('NONE','YEARLY')),
    CONSTRAINT ck_anniv_dir      CHECK (count_direction IN ('D_DAY','D_PLUS')),
    CONSTRAINT ck_anniv_offsets  CHECK (notify_offsets = '' OR notify_offsets ~ '^[0-9]+(,[0-9]+)*$'),
    CONSTRAINT ck_anniv_leap_dom CHECK (calendar = 'LUNAR' OR leap_policy = 'PLAIN_ONLY'),
    CONSTRAINT ck_anniv_expanded CHECK (recurrence = 'NONE' OR expanded_until IS NOT NULL)
);
COMMENT ON COLUMN anniversary.member_id IS
  'auth-service 소유. 논리 참조라 FK 를 걸지 않는다 (Waiting.restaurantId 와 같은 관례)';

CREATE INDEX ix_anniversary_member ON anniversary (member_id, id);
CREATE INDEX ix_anniversary_expand ON anniversary (expanded_until) WHERE recurrence = 'YEARLY';

-- -------------------------------------------------------------
-- anniversary_occurrence  (ARCHITECTURE §2.3) — 투영
-- -------------------------------------------------------------
CREATE TABLE anniversary_occurrence (
    anniversary_id  bigint       NOT NULL REFERENCES anniversary(id) ON DELETE CASCADE,
    occurrence_date date         NOT NULL,
    notify_offset   smallint     NOT NULL,
    member_id       bigint       NOT NULL,
    notified_at     timestamptz,
    created_at      timestamp(6) NOT NULL,
    PRIMARY KEY (anniversary_id, occurrence_date, notify_offset),
    CONSTRAINT ck_occ_offset CHECK (notify_offset BETWEEN 0 AND 365)
);
COMMENT ON TABLE anniversary_occurrence IS
  '저장하는 것은 발생일(절대 날짜)이지 D-day 가 아니다 (SPEC §9.9(2) 와 부딪히지 않는 까닭)';

CREATE INDEX ix_occurrence_due
    ON anniversary_occurrence (occurrence_date, notify_offset)
    INCLUDE (anniversary_id, member_id)
    WHERE notified_at IS NULL;

CREATE INDEX ix_occurrence_purge
    ON anniversary_occurrence (occurrence_date)
    WHERE notified_at IS NOT NULL;

ALTER TABLE anniversary_occurrence
    SET (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.01);

-- -------------------------------------------------------------
-- release — league / team / sport_event  (SPEC §9.7 · §12)
-- -------------------------------------------------------------
CREATE TABLE league (
    id           bigint       PRIMARY KEY,
    source       varchar(20)  NOT NULL,
    external_id  varchar(40)  NOT NULL,
    sport        varchar(40)  NOT NULL,
    name         varchar(120) NOT NULL,
    country_code varchar(2)   REFERENCES country(code),
    zone_id      varchar(64)  NOT NULL,
    enabled      boolean      NOT NULL DEFAULT true,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL
);
CREATE UNIQUE INDEX uk_league_source ON league (source, external_id);

CREATE TABLE team (
    id          bigint       PRIMARY KEY,
    league_id   bigint       NOT NULL REFERENCES league(id),
    source      varchar(20)  NOT NULL,
    external_id varchar(40)  NOT NULL,
    name        varchar(120) NOT NULL,
    name_ko     varchar(120),
    is_stub     boolean      NOT NULL DEFAULT false,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL
);
COMMENT ON COLUMN team.is_stub IS
  '모르는 팀 id 를 만났을 때 FK 를 지키려고 먼저 넣은 행. 0이 아니면 신호다 (§1.4)';
CREATE UNIQUE INDEX uk_team_source ON team (source, external_id);
CREATE INDEX ix_team_league ON team (league_id);

CREATE TABLE sport_event (
    id            bigint       PRIMARY KEY,
    source        varchar(20)  NOT NULL,
    external_id   varchar(40)  NOT NULL,
    league_id     bigint       NOT NULL REFERENCES league(id),
    home_team_id  bigint       REFERENCES team(id),
    away_team_id  bigint       REFERENCES team(id),
    name          varchar(200) NOT NULL,
    starts_at     timestamptz,
    time_is_unknown boolean    NOT NULL DEFAULT false,
    status        varchar(20),
    postponed     boolean      NOT NULL DEFAULT false,
    season        varchar(20),
    venue         varchar(200),
    source_hash   char(32)     NOT NULL,
    last_seen_at  timestamptz  NOT NULL,
    created_at    timestamp(6) NOT NULL,
    updated_at    timestamp(6) NOT NULL
);
COMMENT ON COLUMN sport_event.source_hash IS
  '가변 필드의 md5. 안 바뀌었으면 UPDATE 를 건너뛴다 — 창 누적이 10분마다 같은 행을 다시 보므로';
COMMENT ON COLUMN sport_event.time_is_unknown IS
  'strTime 이 없어 날짜만 확실할 때. 조용히 00:00 으로 퉁치지 않는다 (SPEC §9.9(4))';

CREATE UNIQUE INDEX uk_sport_event_source ON sport_event (source, external_id);
CREATE INDEX ix_sport_event_league_time ON sport_event (league_id, starts_at);
CREATE INDEX ix_sport_event_home        ON sport_event (home_team_id, starts_at);
CREATE INDEX ix_sport_event_away        ON sport_event (away_team_id, starts_at);

-- -------------------------------------------------------------
-- release — movie  (SPEC §9.7)
-- -------------------------------------------------------------
CREATE TABLE movie (
    id          bigint       PRIMARY KEY,
    source      varchar(20)  NOT NULL,
    external_id varchar(40)  NOT NULL,
    title_en    varchar(300),
    title_ko    varchar(300),
    source_hash char(32)     NOT NULL,
    created_at  timestamp(6) NOT NULL,
    updated_at  timestamp(6) NOT NULL
);
CREATE UNIQUE INDEX uk_movie_source ON movie (source, external_id);

CREATE TABLE movie_release (
    movie_id      bigint       NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    country_code  varchar(2)   NOT NULL REFERENCES country(code),
    release_date  date         NOT NULL,
    certification varchar(20),
    created_at    timestamp(6) NOT NULL,
    updated_at    timestamp(6) NOT NULL,
    PRIMARY KEY (movie_id, country_code)
);
COMMENT ON TABLE movie_release IS
  '개봉일은 나라마다 다르다. 어느 나라 기준인지 없이는 D-day 가 성립하지 않는다 (SPEC §9.7)';
CREATE INDEX ix_movie_release_upcoming
    ON movie_release (country_code, release_date) INCLUDE (movie_id);

-- -------------------------------------------------------------
-- release — watch / date_change
-- -------------------------------------------------------------
CREATE TABLE watch (
    id           bigint       PRIMARY KEY,
    member_id    bigint       NOT NULL,
    subject_type varchar(20)  NOT NULL,
    subject_id   bigint       NOT NULL,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL,
    CONSTRAINT ck_watch_subject CHECK (subject_type IN ('SPORT_EVENT','TEAM','MOVIE'))
);
CREATE UNIQUE INDEX uk_watch        ON watch (member_id, subject_type, subject_id);
CREATE INDEX        ix_watch_fanout ON watch (subject_type, subject_id) INCLUDE (member_id);

CREATE TABLE date_change (
    id           bigint       PRIMARY KEY,
    subject_type varchar(20)  NOT NULL,
    subject_id   bigint       NOT NULL,
    field        varchar(30)  NOT NULL,
    old_value    varchar(64),
    new_value    varchar(64),
    detected_at  timestamptz  NOT NULL,
    sync_run_id  bigint,
    created_at   timestamp(6) NOT NULL,
    updated_at   timestamp(6) NOT NULL,
    CONSTRAINT ck_dc_subject CHECK (subject_type IN ('SPORT_EVENT','MOVIE_RELEASE')),
    CONSTRAINT ck_dc_field   CHECK (field IN ('STARTS_AT','RELEASE_DATE','POSTPONED'))
);
COMMENT ON TABLE date_change IS
  '유일 제약을 걸지 않는다. 중복 제거는 outbox_event.idempotency_key 의 일이다 (§6.3)';
CREATE INDEX ix_date_change_subject  ON date_change (subject_type, subject_id, detected_at DESC);
CREATE INDEX ix_date_change_detected ON date_change (detected_at);

-- -------------------------------------------------------------
-- outbox_event  (ARCHITECTURE §3.2 · §3.3 · §3.5) — 한 테이블
-- -------------------------------------------------------------
CREATE TABLE outbox_event (
    id              bigint       PRIMARY KEY,
    aggregate_type  varchar(30)  NOT NULL,
    aggregate_id    varchar(64)  NOT NULL,
    event_type      varchar(50)  NOT NULL,
    partition_key   varchar(64)  NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    payload         text         NOT NULL,
    status          varchar(20)  NOT NULL,
    retry_count     smallint     NOT NULL DEFAULT 0,
    published_at    timestamptz,
    created_at      timestamp(6) NOT NULL,
    updated_at      timestamp(6) NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','SENDING','PUBLISHED','FAILED'))
);
COMMENT ON COLUMN outbox_event.partition_key IS
  'Kafka 파티션 키. aggregate_id 와 다르다 — notification 은 memberId, release 는 경기 id';

CREATE UNIQUE INDEX uk_outbox_idem  ON outbox_event (aggregate_type, idempotency_key);
CREATE INDEX ix_outbox_claim ON outbox_event (created_at) WHERE status IN ('PENDING','SENDING');
CREATE INDEX ix_outbox_purge ON outbox_event (published_at) WHERE status = 'PUBLISHED';

ALTER TABLE outbox_event
    SET (autovacuum_vacuum_scale_factor = 0.01, autovacuum_vacuum_cost_delay = 0);

-- -------------------------------------------------------------
-- sync_run / sync_run_item  (ARCHITECTURE §5.2 · §5.3 · §4.3)
-- -------------------------------------------------------------
CREATE TABLE sync_run (
    id             bigint       PRIMARY KEY,
    target         varchar(20)  NOT NULL,
    trigger_source varchar(10)  NOT NULL,
    status         varchar(12)  NOT NULL,
    started_at     timestamptz  NOT NULL,
    finished_at    timestamptz,
    item_total     integer      NOT NULL DEFAULT 0,
    item_ok        integer      NOT NULL DEFAULT 0,
    item_failed    integer      NOT NULL DEFAULT 0,
    item_aborted   integer      NOT NULL DEFAULT 0,
    dropped_total  integer      NOT NULL DEFAULT 0,
    cache_version  bigint,
    warm_status    varchar(12),
    created_at     timestamp(6) NOT NULL,
    updated_at     timestamp(6) NOT NULL,
    CONSTRAINT ck_run_target  CHECK (target IN ('HOLIDAY','SPORT_EVENT','MOVIE')),
    CONSTRAINT ck_run_trigger CHECK (trigger_source IN ('SCHEDULE','ADMIN')),
    CONSTRAINT ck_run_status  CHECK (status IN ('RUNNING','SUCCEEDED','PARTIAL','FAILED')),
    CONSTRAINT ck_run_warm    CHECK (warm_status IS NULL
                                     OR warm_status IN ('SKIPPED','OK','FAILED'))
);
COMMENT ON COLUMN sync_run.warm_status IS
  '워밍 실패는 플립을 막고 낡은 자료가 계속 나가게 한다 — 이 구조의 유일한 고장 모드라 여기 남긴다 (ARCHITECTURE §4.3)';
CREATE INDEX ix_sync_run_target_time ON sync_run (target, started_at DESC);

CREATE TABLE sync_run_item (
    id            bigint       PRIMARY KEY,
    sync_run_id   bigint       NOT NULL REFERENCES sync_run(id) ON DELETE CASCADE,
    target        varchar(20)  NOT NULL,
    country_code  varchar(2),
    holiday_year  smallint,
    league_id     bigint,
    status        varchar(12)  NOT NULL,
    source_count  integer,
    stored_count  integer,
    dropped_count integer      NOT NULL DEFAULT 0,
    error_code    varchar(60),
    error_message varchar(500),
    duration_ms   integer,
    created_at    timestamp(6) NOT NULL,
    CONSTRAINT ck_item_status CHECK (status IN ('OK','FAILED','SKIPPED','ABORTED'))
);
COMMENT ON COLUMN sync_run_item.dropped_count IS
  '우리 코퍼스에 안 걸린 황금연휴 등. 평소 0이어야 정상이고 0이 아니면 신호다 (SPEC §11.4)';
CREATE INDEX ix_sync_run_item_run ON sync_run_item (sync_run_id);
CREATE INDEX ix_sync_run_item_bad
    ON sync_run_item (target, country_code, holiday_year)
    WHERE status IN ('FAILED','ABORTED');
```

**`sky` 와 `axis` 는 이 스크립트에 한 줄도 없다.** SPEC §9.4 · §9.5 가 그렇게 정했고,
**스키마에서 그것이 보이는 것**이 그 결정이 지켜졌다는 증거다.

---

# 13. 착수 전에 재야 할 것 · 열린 결정

## 13.1 실측 게이트 — ARCHITECTURE §4.4 의 JMH 게이트와 같은 모양

| | 무엇 | 언제 | 통과 조건 |
| --- | --- | --- | --- |
| 1 | §4.4 의 여섯 질의 `EXPLAIN (ANALYZE, BUFFERS)` | 착수 5 직후 (코퍼스가 찬 뒤) | A-5·A-6·A-7 에 **`Sort` 노드 없음** · A-1·A-4 **`Heap Fetches: 0`** |
| 2 | **A-7 검산점** (§4.3 의 SQL) | 〃 | **544.** `is_global` 을 켠 쪽과 끈 쪽 둘 다 돌려서 544 가 나오는 쪽을 채택 |
| 3 | A-5 검산점 | 〃 | 크리스마스 slug 의 국가 수 = **178**, 이름 허브 규모 ≈ **60 × 176** |
| 4 | 자연키 충돌 | 〃 | 204국 · 5년 전수에서 `uk_holiday_natural` 위반 **0건**. 위반이 나오면 SPEC §11.3 의 45개국 표본이 부족했다는 뜻이고, **그때 다섯 번째 칼럼을 논의한다** |
| 5 | 드리프트 테스트 · 인덱스 존재 테스트 (§11.3) | 착수 2 | CI 초록 |

**4번이 제일 중요하다.** SPEC §11.3 의 "0조" 는 **45개국 · 1년**의 실측이고, 우리는
**204개국 · 5년**을 담는다. 표본이 4.5배 · 5배로 늘어난다. **여기서 위반이 나오면 스키마가
틀린 것이고, 그것은 자료가 차기 전에 알아야 한다** — 찬 뒤에 유일 인덱스를 바꾸는 것은
마이그레이션이다.

## 13.2 닫은 것

| ARCHITECTURE §10 | 답 |
| --- | --- |
| **12 — 소프트 삭제 보존 기간** | **성공 회차 3번** (§3.3). `holiday` · `long_weekend` 에만 적용하고, `anniversary` 는 소프트 삭제 자체를 안 한다 (§5.6) |

## 13.3 이 문서가 남기는 열린 결정

| | 물음 | 무엇에 달렸나 | 언제 |
| --- | --- | --- | --- |
| S-1 | **`name_slug` 의 정규화 규칙** | 소문자·하이픈까지는 정해졌으나 아포스트로피·악센트(`Saint Joseph's` · `Día`)를 어떻게 접을지는 코퍼스를 봐야 안다. **슬러그 충돌 건수를 세고 나서 정한다** | 착수 5 |
| S-2 | **`holiday` 보유 연도 범위** | 이 문서는 5년을 가정해 규모를 셌다. 실제 값은 `coverage.holiday.years` 설정이고, 그것이 정해지면 §4.0 의 행 수가 그만큼 곱해진다 (10년이어도 28,000행이라 결론은 안 바뀐다) | 착수 5 |
| S-3 | **`sport_event` 참가자 모델** | 홈/원정 두 칼럼은 KBO 의 모양이다. 참가자 N 인 종목을 켜는 날 자식 테이블이 필요하다 (§6.4) | 리그를 늘릴 때 |
| S-4 | **`anniversary_occurrence` 파티셔닝** | 3,000만 행 또는 청소가 자동 청소를 못 따라갈 때 (§9.3) | 관측 후 |
| S-5 | **유료 키가 켜졌을 때의 벌크 upsert** | §6.2 의 `ON CONFLICT ... WHERE source_hash <> ...` 경로. 칼럼은 지금 넣지만 질의는 그때 쓴다 | 유료 키 도입 시 |

## 13.4 ARCHITECTURE 에 되돌리는 요구 — 하나

> **§5.6 의 락 목록에 `dday-retention` 을 여덟 번째로 더하고, §5.7 의
> `spring.task.scheduling.pool.size` 를 8 에서 10 으로 올린다.**
> 이 문서의 보존 정책(§10)이 스케줄 작업을 하나 늘렸다. §5.7 이 경고한 고장이
> 정확히 **"스케줄 작업 수보다 풀이 작다"** 이므로, 작업을 늘리면서 풀을 안 올리면
> 그 문서가 막으려던 것을 이 문서가 만든다.

---

## 부록 A. 커넥션 풀 · 격리 수준

**격리 수준은 기본값 `READ COMMITTED` 로 둔다.** 더 높은 수준이 필요한 자리를 찾아봤으나 없다 —
경합이 생길 법한 두 곳이 이미 다른 수단으로 막혀 있다.

| 자리 | 왜 `READ COMMITTED` 로 충분한가 |
| --- | --- |
| Outbox claim | ShedLock + `FOR UPDATE SKIP LOCKED` (§7.2) |
| 동기화 upsert | `dday-sync-{target}` 락이 한 회차만 돌게 한다. 게다가 `ON CONFLICT` 가 원자적이다 |

**커넥션 풀은 ARCHITECTURE §5.4-2 가 이미 지적한 곳이다** — *"가상 스레드 1,020개가 HikariCP
10개에 몰리면 큐만 길어진다"*. 스키마 쪽에서 덧붙일 것은 하나다.

> **커넥션 보유 구간 안에 외부 호출이 한 번도 없어야 한다**는 §5.4 의 원칙이 지켜지면,
> (국가, 연도) 한 단위의 트랜잭션은 **`SELECT` 14행 + upsert 14행 + 스윕 1건**으로
> **수 ms** 다. 그러면 풀 크기 10~20으로 1,020 단위를 감당한다.
> **풀을 키워서 푸는 문제가 아니라 트랜잭션을 짧게 해서 푸는 문제**이고, 짧게 만드는 것은
> §5.2 의 트랜잭션 경계 결정이 이미 해 두었다.

## 부록 B. 검산 SQL 모음

`SyncRun` 이 끝날 때 또는 손으로 돌린다. **전부 SPEC 에 적힌 실측값과 견주는 것들이다.**

```sql
-- B-1. 주말 겹침 (SPEC §5) — 기대 544. 540 이면 weekend_mask 가 전부 96 이다
SELECT count(*) FROM (
  SELECT DISTINCT h.country_code, h.holiday_date
    FROM holiday h JOIN country c ON c.code = h.country_code
   WHERE h.holiday_year = 2026 AND h.is_public AND h.is_global AND h.deleted_at IS NULL
     AND ((c.weekend_mask::int >> (EXTRACT(ISODOW FROM h.holiday_date)::int - 1)) & 1) = 1
) t;

-- B-2. 주말 갈래 분포 (SPEC §5) — 기대 96:195, 48:8, 64:1
SELECT weekend_mask, count(*) FROM country GROUP BY 1 ORDER BY 2 DESC;

-- B-3. 크리스마스 (SPEC A-5) — 기대 178
SELECT count(DISTINCT country_code) FROM holiday
 WHERE holiday_year = 2026 AND name_slug = 'christmas-day'
   AND is_public AND deleted_at IS NULL;

-- B-4. 자연키가 정말 유일한가 — 기대 0 (§13.1-4)
SELECT country_code, holiday_date, name_en, subdivision_key, count(*)
  FROM holiday GROUP BY 1,2,3,4 HAVING count(*) > 1;

-- B-5. 세 칼럼만으로는 충돌한다는 것 (SPEC §11.3 재현) — 스위스가 대부분이어야 한다
SELECT country_code, count(*) AS collisions FROM (
  SELECT country_code, holiday_date, name_en FROM holiday
   WHERE deleted_at IS NULL GROUP BY 1,2,3 HAVING count(*) > 1
) t GROUP BY 1 ORDER BY 2 DESC;

-- B-6. 정의역 — 한 번도 동기화 안 한 (국가, 연도)
SELECT country_code, holiday_year FROM holiday_coverage WHERE last_ok_run_id IS NULL;

-- B-7. 버린 건수는 평소 0이어야 정상 (SPEC §11.4)
SELECT sum(dropped_count) FROM sync_run_item
 WHERE created_at > now() - INTERVAL '7 days';

-- B-8. 창 누적이 같은 경기를 두 번 담지 않았나 (SPEC §12.2 검산점)
SELECT external_id, count(*) FROM sport_event GROUP BY 1 HAVING count(*) > 1;

-- B-9. 같은 날 같은 팀이 두 경기에 (SPEC §12.2 검산점)
SELECT t.id, e.starts_at::date, count(*) FROM sport_event e
  JOIN team t ON t.id IN (e.home_team_id, e.away_team_id)
 GROUP BY 1,2 HAVING count(*) > 1;
```

---

**참고 — 이 문서가 근거로 삼은 저장소 안의 실제 코드**

아래 주장들은 **2026-09-14 에 파일을 직접 열어 확인했다** — `BaseEntity` 가 `LocalDateTime` 인 것,
`promotion-service` 의 DDL 이 MySQL 문법인 것, `User` 가 `uniqueConstraints` 를 쓰는 것,
`Waiting.restaurantId` 가 "논리적 참조" 인 것, 3세대 Outbox 의 인덱스가
`idx_outbox_status_created`(`status, created_at`) 이고 claim 질의가 위 §7.2 의 모양인 것,
`aggregateId` 가 두 전례 모두 `Long` 인 것, 그리고 **저장소 어디에도 `SKIP LOCKED` 가 없다**는 것.

| 무엇 | 경로 |
| --- | --- |
| `createdAt`/`updatedAt` 이 `LocalDateTime` (§1.1 의 흠) | `libs/storage-db/src/main/java/com/booster/storage/db/core/BaseEntity.java` |
| Snowflake PK 관례 | `libs/common/src/main/java/com/booster/common/SnowflakeGenerator.java` |
| 엔티티에 `uniqueConstraints` 를 적은 전례 | `apps/auth-service/src/main/java/com/booster/authservice/domain/User.java` |
| 3세대 Outbox 상태기계와 claim 질의 (§7.2 가 이것을 고친다) | `apps/query-burst/src/main/java/com/booster/queryburst/order/domain/outbox/OutboxEvent.java` · `.../OutboxEventRepository.java` |
| 논리 참조 관례 ("식당 ID (논리적 참조)") | `apps/waiting-service/src/main/java/com/booster/waitingservice/waiting/domain/Waiting.java` |
| 저장소의 유일한 DDL — **MySQL 문법이라 본보기가 못 된다** (§11.1) | `apps/promotion-service/src/main/resources/scripts/table.sql` |
| Testcontainers PostgreSQL 픽스처 — §11.3 의 두 테스트가 이것을 쓴다 | `libs/storage-db/src/testFixtures/java/com/booster/storage/db/PostgresTestConfig.java` |
| datasource 설정이 없는 곳 (§11.1) | `apps/d-day/d-day-service/src/main/resources/application.yml` |
| `validate` / `create-drop` 프로필 전례 | `apps/waiting-service/src/main/resources/application-{home,docker,out}.yml` |
