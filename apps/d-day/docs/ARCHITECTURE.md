# d-day-service 리뉴얼 — 아키텍처

> **상태: 설계 단계.** 요구사항(§1–8) · 도메인 모델(§9) · API 목록(§10) · 원천 실측(§11 · §12) 이
> 닫힌 뒤의 문서다. 2층 검산점은 2026-09-14 에 확보됐다 (`docs/ASTRO-CHECKPOINTS.md`).
>
> **스키마와 인덱스는 이 문서의 범위가 아니다** — `db-tuner` 몫이다 (SPEC §8-3 · §10.9-5).
> 여기서는 **무엇이 어느 모듈에 살고, 무엇이 무엇을 부르고, 어디에 캐시와 락과 회로를 거는가**만 정한다.
> 테이블 이름이 나오는 자리가 있으나 그것은 **경계를 그리기 위한 자리표시**이지 스키마 결정이 아니다.
>
> 작성 2026-09-14

---

## 0. 이 문서가 정하는 것

| | 물음 | 어디 |
| --- | --- | --- |
| 1 | 7개 컨텍스트를 어떻게 배치하나 | §2 |
| 2 | C-7 (음력 기념일) 의 도메인 경계 | §3 |
| 3 | D-4 (일정 변경 알림) 의 이벤트 흐름 | §4 |
| 4 | 캐시 — 특히 B-6 의 열린 키 공간 | §5 |
| 5 | 외부 동기화 — 락 · 회로 · 부분 실패 | §6 |
| 6 | 천문 계산의 자리와 IMO 함정의 대응 | §7 |
| 7 | 게이트웨이 변경 넷의 순서와 위험 | §8 |
| 8 | 못 닫은 것 | §11 |

**결정 요약.** 근거는 각 절에 있다.

| | 정한 것 |
| --- | --- |
| 배치 | **한 서비스 · 패키지 경계.** 단 천문 계산만 `apps/d-day/astro-core` 로 뺀다 (`libs/` 아니다) |
| C-7 | `anniversary → sky` **동기 in-process 호출** + `sky` 가 공표하는 포트 하나. 트랜잭션 **밖**에서 계산 |
| C-7 의 대가 | 알림 스케줄러를 위해 **`AnniversaryOccurrence` 투영**을 둔다. 저장하는 것은 발생일이지 D-day 가 아니다 |
| D-4 | Outbox **구조는 그대로, 구현은 `apps/query-burst` 세대를 베낀다.** `query-burst-msa` 세대는 AOP 자기호출 버그가 있다 |
| D-4 흐름 | **2단 발행** — 사실(`dday.release.changed`) 발행 → 자기 컨슈머가 `Watch` 로 펼쳐 알림 요청 발행 |
| 캐시 | **캐시 키를 만드는 값은 전부 유한 집합에서 와야 한다.** 아니면 캐시 앞에서 400/404 로 잘린다 |
| B-6 | 정의역 `[1583, 2999]` + **핫 윈도우(올해±2)만 캐시** + 콜드는 무캐시 + Bulkhead |
| 무효화 | 개별 삭제 금지. **버전 플립** (새 버전에 먼저 심고 나서 버전을 올린다) |
| 동기화 | 트랜잭션 경계 = **(국가, 연도) 하나.** 전부 아니면 전무 금지 |
| CB | 원천마다 하나. **4xx 는 실패로 세지 않는다** |
| 유성우 | **(나) 공표 시각 보유**를 뼈대로, (가) 역산은 「공표값이 없는 해」의 대체값 |
| 함정 방어 | 황경을 **기준계별 타입**으로 나눠 컴파일 에러로 만든다. 테스트로는 못 잡는 종류다 |
| 게이트웨이 | **4번을 제일 먼저.** 경로가 열리기 전에 막아 두면 구멍이 존재한 적이 없게 된다 |

---

# 1. 배치 — 모듈과 패키지

## 1.1 갈래 셋

| | 모양 | 배포 단위 |
| --- | --- | --- |
| **가** | `d-day-service` 하나, 7개 컨텍스트를 패키지로 | 1 |
| 나 | 컨텍스트마다 Gradle 모듈, 부트 앱은 하나 | 1 |
| 다 | `holiday-service` · `sky-service` · … 로 서비스 분리 | 7 |

**「가」로 간다.** 「나」는 §1.3 의 한 군데에서만 쓴다.

**「다」를 버린 까닭 — 자를 선이 없다.** MSA 로 쪼개는 값어치는 *따로 스케일하고 따로 배포할 수 있다*
는 데 있는데, 이 서비스의 부하는 **전부 한 갈래(읽기)** 이고 그 읽기가 §9.1 의 컨텍스트를 가로질러
조립된다. `country` 는 다른 여섯 전부가 읽고, `axis` 는 `holiday` 없이는 질의 자체가 성립하지 않는다.
쪼개면 **한 응답을 만들려고 서비스 셋을 부르는** 모양이 되고, 그것은 §F-5 의 p99 100ms 를 네트워크
왕복으로 까먹는 짓이다. 게다가 저장소에는 이미 MSA 분해 실험이 둘(`query-burst-msa` · `telemetryhub`)
있다. 세 번째를 여기서 반복해도 배울 것이 없다 (§2 의 「비계」 칸이 빈다).

**「나」를 뼈대로 쓰지 않는 까닭 — 지금 강제할 경계가 아니다.** 모듈로 나누면 의존 방향이 컴파일러로
강제되어 좋지만, **일곱 컨텍스트 중 실제로 가로지르는 것은 둘뿐이다** (§6 — C-7 · D-4).
둘을 지키려고 일곱 모듈의 빌드 그래프를 짊어지면 비용이 이득을 넘는다.
방향 강제는 §1.5 의 규칙과 ArchUnit 테스트로 대신한다 — 값이 훨씬 싸다.

## 1.2 그런데 천문 계산만은 모듈로 뺀다

**`apps/d-day/astro-core` 를 신설한다.** Gradle 모듈이고 **Spring 을 의존하지 않는 순수 자바**다.

`libs/` 가 아니라 `apps/d-day/` 아래인 까닭 셋.

1. **`libs/*` 는 저장소 전체가 공유하는 인프라 축이다.** 천문은 d-day 만 쓴다. `libs/` 에 두면
   `waiting-service` 빌드에 VSOP87 이 딸려온다.
2. **`libs/*` 가 도메인을 알게 되는 것을 이 저장소는 이미 한 번 후회하고 있다.**
   `libs/storage-redis/.../domain/WaitingUser.java` 가 그 자국이다 — 인프라 모듈 안에 특정
   서비스의 도메인 타입이 앉아 있다. **그 전례를 따라 하지 않는다.**
3. **집합 모듈 아래 형제 모듈을 두는 것은 이 저장소의 관례다.**
   `apps/query-burst-msa/order-service/build.gradle` 이 `implementation project(':apps:query-burst-msa:contracts')`
   를 쓴다. CLAUDE.md 의 `apps/* → apps/* (FORBIDDEN)` 이 막는 것은 **서비스 사이**(waiting → restaurant)이지
   집합 모듈 내부가 아니다.

**그러면 왜 서비스 안의 패키지로는 안 두나.**

| | |
| --- | --- |
| 검산점 테스트가 **수백 건**이다 | 절기 72 · 삭망 74 · 유성우 11. 이것들이 `@SpringBootTest` 를 타면 CI 가 못 버틴다 |
| **Spring 을 모르게 만드는 것이 목적**이다 | 같은 컴파일 단위에 있으면 언젠가 누가 `@Cacheable` 을 계산 커널에 붙인다. 모듈이 Spring 을 의존하지 않으면 **그것이 컴파일 에러**가 된다 |
| 캐시는 `sky` 의 일이지 계산의 일이 아니다 | 이 분리가 §9.5 의 「DB 없음 · 계산 + 캐시」를 구조로 굳힌다 |

**따라오는 이사 하나.** `checkpoints.json` 이 지금 `astro-core/src/test/resources/astro/` 에 있다.
**`astro-core/src/test/resources/astro/` 로 옮기고** `apps/d-day/tools/fetch-astro-checkpoints.mjs` 의
출력 경로를 같이 고친다. ASTRO-CHECKPOINTS §7 이 "출력 파일을 손으로 고치지 않는다" 고 못 박았으므로
**경로 변경도 스크립트 쪽을 고쳐야 한다.**

## 1.3 루트 패키지를 지금 옮긴다

현재 `com.booster.ddayservice`. **`com.booster.dday` 로 옮긴다.**

| 까닭 | |
| --- | --- |
| 집합 모듈 관례 | `com.booster.queryburstmsa.{contracts,order}` · `com.booster.telemetryhub.*` 와 같은 모양이 된다 |
| `astro-core` 가 들어갈 자리가 필요하다 | `com.booster.dday.astro` 는 자연스럽고 `com.booster.ddayservice.astro` 는 거짓말이다 (서비스 모듈이 아니다) |
| **지금이 아니면 못 옮긴다** | 자바 파일이 `DDayServiceApplication` 하나뿐이다. 비용이 오늘 최저다 |

## 1.4 나무

```
apps/d-day/
├── build.gradle                       (집합 · bootJar 꺼짐 — 지금 그대로)
├── docs/{SPEC,ASTRO-CHECKPOINTS,ARCHITECTURE}.md
├── tools/fetch-astro-checkpoints.mjs  (출력 경로만 고침)
│
├── astro-core/                        ★ 신설 · 순수 자바 · Spring 없음
│   ├── build.gradle                   (plugins { id 'java-library' } · 부트 플러그인 없음)
│   └── src/main/java/com/booster/dday/astro/
│       ├── time/      JulianDay · DeltaTModel · TimeScale(TT↔UT1↔UTC)
│       ├── frame/     ApparentEclipticLongitude · J2000EclipticLongitude · PrecessionModel
│       ├── solar/     SolarPosition(VSOP87 절단) · SolarTermSolver
│       ├── lunar/     MoonPosition · MoonPhaseSolver · LunisolarCalendar
│       └── meteor/    MeteorShowerCatalog · PublishedMaximum · EstimatedMaximum
│   └── src/main/resources/meteor/imo-2026.json      (§7.4)
│   └── src/test/resources/astro/checkpoints.json    (이사해 온 것)
│
└── d-day-service/
    └── src/main/java/com/booster/dday/
        ├── DDayServiceApplication.java
        ├── config/        CacheConfig · SchedulerConfig(LockProvider) · ExternalClientConfig
        │                  SyncProperties · LeagueProperties · CoverageProperties
        ├── shared/        ← 얇게 유지한다. 여기가 두꺼워지면 경계가 녹고 있다는 신호다
        │   ├── dday/      DDayCalculator        (§9.9(2) 의 「조립」이 사는 유일한 자리)
        │   ├── cache/     CacheNamespace · CacheKey · VersionedCache
        │   ├── locale/    Lang · LangResolver   (Accept-Language / ?lang=)
        │   ├── outbox/    OutboxEvent · OutboxStatus · DomainOutbox · OutboxRelay
        │   └── web/       CurrentUser(X-User-Id) · AdminOnly · ErrorCode(DDay*)
        │
        ├── country/       domain · application · web · infrastructure(CldrSeedLoader)
        ├── holiday/       domain · application · web · infrastructure(NagerClient)
        ├── axis/          application · web                (domain 없음 — §9.4)
        ├── sky/           api · application · web          (계산 없음 — astro-core 가 한다)
        ├── anniversary/   domain · application · web · event
        ├── release/       domain · application · web · event · infrastructure
        └── sync/          domain(SyncRun · SyncRunItem) · application · web(admin) · event(스케줄러)
```

**`search/` 패키지를 만들지 않는다.** 1차에서 빠졌다 (§7). 빈 패키지는 "여기 뭔가 있다" 는
거짓 신호를 준다. 자리는 이 문서와 §9.1 의 점선이 기억한다.

**`axis` 에 `domain` 이 없는 것은 실수가 아니다** (§9.4 — 집합체도 테이블도 없다).
`application` 에 읽기 모델과 QueryDSL 질의만 산다. 이 빈칸이 CLAUDE.md 의 DDD 규약에서
의도적으로 벗어난 유일한 자리이고, **벗어났다는 사실이 `axis` 의 성격**이다.

## 1.5 컨텍스트 사이의 의존 방향

```
                 country
                    │  (읽는다)
        ┌───────────┼────────────┬───────────┐
        ▼           ▼            ▼           ▼
     holiday     release      sky.api    anniversary
        │                        ▲            │
        ▼                        └────────────┘
      axis                         C-7 (동기)
```

| 규칙 | |
| --- | --- |
| R1 | `shared` 는 누구도 의존하지 않는다. `shared` 는 **모두가** 의존한다 |
| R2 | 컨텍스트 사이 호출은 **`{context}.api` 패키지에 공표된 타입**으로만 한다. `domain` · `application` 을 건너다보지 않는다 |
| R3 | `sky` 는 누구도 모른다. 부름을 받기만 한다 |
| R4 | `astro-core` 를 의존하는 것은 **`sky` 뿐**이다. `anniversary` 가 직접 의존하면 캐시 정책과 ΔT 정책이 두 곳에 생긴다 |
| R5 | 순환 금지. ArchUnit 으로 건다 |

**R2 가 「나」(모듈 분리)를 안 해도 되게 만드는 장치다.** 지금 공표 대상은 하나뿐이다 — `sky.api`.

---

# 2. 가로지르는 것 (1) — C-7 음력 기념일

> §6: "개인 기념일이 하늘(음력 계산)을 소비한다 → 도메인 경계를 흔든다."

## 2.1 갈래 넷

| | 방법 | 버린 까닭 |
| --- | --- | --- |
| 가 | `anniversary` 가 `astro-core` 를 직접 의존 | 캐시·ΔT·기준 자오선 정책이 두 곳에 생긴다. 언젠가 둘이 갈라지고, 그때 **음력이 두 개**가 된다 |
| 나 | HTTP 로 자기 자신의 `/sky/lunar` 호출 | 자기 프로세스를 네트워크로 부른다. p99 를 버리고 장애 모드를 산다 |
| 다 | Kafka 로 비동기 요청/응답 | 기념일 하나 등록하는 데 왕복 이벤트. 응답을 기다려야 하므로 비동기의 값어치가 없다 |
| **라** | **`sky` 가 포트 하나를 공표하고 `anniversary` 가 동기 호출** | — |

**「라」로 간다.**

## 2.2 「라」가 값싼 까닭 — `sky` 에는 DB 가 없다

도메인 간 동기 호출이 보통 위험한 이유는 **남의 트랜잭션·커넥션을 내 트랜잭션이 붙들기** 때문이다.
그런데 §9.5 가 `sky` 에 DB 를 주지 않았다. 그러므로 **C-7 은 트랜잭션 경계를 뚫지 않는다.**
붙드는 것은 CPU 시간뿐이다.

**그래도 트랜잭션 안에서 부르지 않는다.** 음력 변환이 수 ms 라도, 그것이 DB 트랜잭션 안에 있으면
커넥션을 그만큼 더 붙든다. 가상 스레드가 켜져 있어 **동시 요청 수의 상한이 사실상 없으므로**
(§6.7) 커넥션 보유 시간이 곧 처리량이다.

```
AnniversaryFacade (트랜잭션 없음)
  1. sky.api.LunarCalendarPort 로 향후 발생일 N개를 계산   ← 트랜잭션 밖
  2. AnniversaryService.register(cmd, occurrences)         ← 여기서부터 @Transactional
       - Anniversary 저장
       - AnniversaryOccurrence N건 저장
       - (C-5 알림 대상이면) Outbox 예약은 스케줄러가 한다 (§2.3)
```

CLAUDE.md 의 「복합 트랜잭션 조율은 Facade」 규약이 여기 정확히 들어맞는다.

## 2.3 경계를 다시 뚫는 것 — 알림 스케줄러

C-7 의 진짜 어려움은 등록이 아니라 **C-10(알림 시점)** 에 있다.

> "D-7 인 기념일을 전부 찾아라" 를 **SQL 로** 못 쓴다. 음력 기념일의 다음 양력 발생일은
> 컬럼에 없고 계산에 있기 때문이다.

| | 방법 | |
| --- | --- | --- |
| 가 | 매일 전 회원의 전 기념일을 읽어 `sky` 로 계산하고 거른다 | **회원 수에 비례해 매일 전수 계산.** Scale-out 이 안 되고 (ShedLock 이 한 인스턴스에만 일을 주므로) 그 인스턴스가 혼자 탄다 |
| **나** | **`AnniversaryOccurrence` 투영을 둔다** — 기념일마다 향후 양력 발생일 N개를 미리 전개해 저장 | — |

**「나」로 간다.** 스케줄러가 `WHERE occurrence_date = ? AND notify_offset = ?` 로 후보를 좁힌다.
음력이든 양력이든 **스케줄러에게는 같은 모양**이라 알림 경로에서 C-7 이 사라진다 — 결합이 등록 시점
한 곳으로 모인다.

### §9.9(2) 와 부딪히지 않는 까닭

> "D-day 는 캐시에 넣지 않는다. 조립 단계에서 만든다."

**투영이 저장하는 것은 발생일(절대 날짜)이지 D-day(오늘 의존값)가 아니다.**
2027-03-15 는 내일도 2027-03-15 다. §9.9(2) 가 막는 것은 **오늘이 바뀌면 틀려지는 값**을 굳히는 짓이고,
발생일은 그런 값이 아니다. 규칙을 정확히 다시 읽으면 이렇다.

| 저장·캐시해도 되는 것 | 안 되는 것 |
| --- | --- |
| 발생일 2027-03-15 | "D-182" |
| 절기 시각 2026-03-20T09:01Z | "다음 절기는 춘분" |

**전개 폭과 롤포워드.** 등록·수정 시 **향후 3년치**를 전개하고, **연 1회 롤포워드 스케줄러**가
꼬리를 늘린다. 3년인 까닭: 공휴일 코퍼스의 최소 보유 기간(§A-1)과 같은 눈금을 쓰면
"어디까지 답할 수 있나" 를 한 값으로 말할 수 있다 (§5.1 의 정의역과도 같은 값).

## 2.4 인터페이스 스케치

```java
// com.booster.dday.sky.api — 다른 컨텍스트가 보는 유일한 표면
public interface LunarCalendarPort {
    LunarDate toLunar(LocalDate solar, ZoneId meridian);
    Optional<LocalDate> toSolar(LunarDate lunar, ZoneId meridian);   // 없는 윤달이면 empty
    List<LocalDate> nextOccurrences(LunarDate anchor, LeapPolicy leap,
                                    LocalDate from, int count, ZoneId meridian);
}

public enum LeapPolicy { LEAP_ONLY, PLAIN_ONLY, EITHER }   // 윤달이 없는 해를 어떻게 할 것인가
```

`meridian` 을 인자로 받는 것이 중요하다. **음력은 기준 자오선이 다르면 날짜가 하루 달라진다** —
한국 음력(KST 135°E)과 중국 음력(UTC+8)이 같은 해에 갈라지는 일이 실제로 있다.
1차는 KST 고정이되 **서명에는 남긴다.** 조용히 하나를 고르는 것이 §9.9(4) 가 금지한 바로 그 고장이다.
(→ 열린 결정 §11-8)

```java
// com.booster.dday.shared.dday — 「조립」이 사는 유일한 자리
public interface DDayCalculator {
    int daysUntil(LocalDate target, ZoneId zone, Instant now);
    int daysSince(LocalDate origin, ZoneId zone, Instant now);       // C-8 D+N
    LongWeekendPhase phaseOf(LocalDate start, LocalDate end, ZoneId zone, Instant now);
    <T extends HasDate> Optional<T> pickNext(List<T> candidates, ZoneId zone, Instant now);
}
```

**`pickNext` 가 §9.9(2) 의 "「다음」이 무엇인가도 오늘 의존" 을 구현이 아니라 타입으로 못 박는다.**
`sky` · `holiday` · `release` 가 전부 이 하나를 쓴다. 「다음」을 고르는 코드가 세 군데 생기면
세 군데가 각자 다른 시간대 해석을 갖게 된다.

---

# 3. 가로지르는 것 (2) — D-4 외부 자료 변경 → 알림

## 3.1 기존 Outbox 패턴을 그대로 쓸 수 있나

**구조는 그대로 쓴다. 그런데 「기존」이 셋이고, 셋이 서로 다르다.**

| 세대 | 파일 | 갖춘 것 | 문제 |
| --- | --- | --- | --- |
| 1 | `apps/waiting-service/.../event/OutboxMessageRelay.java` | `published` 불리언, 폴링 | **분산 락 없음** → 인스턴스 2대면 같은 이벤트를 둘 다 발행. **배치 상한 없음** → 밀린 이벤트가 많으면 한 트랜잭션이 무한정 길어진다 |
| 2 | `apps/query-burst-msa/.../event/OrderOutboxRelay.java` | 상태기계 + claim | **깨져 있다.** ↓ |
| **3** | **`apps/query-burst/.../event/OutboxMessageRelay.java`** | 분산 락 · `TransactionTemplate` · stale 회수 · 배치 상한 100 · `get(5, SECONDS)` | — |

### 2세대가 깨져 있다 — AOP 자기호출

```java
// apps/query-burst-msa/order-service/.../OrderOutboxRelay.java
@Scheduled(fixedDelay = 3000)
public void relay() {
    List<OutboxEventEntity> candidates = claimPendingEvents();   // ← 같은 클래스 내 호출
    ...
}

@Transactional
protected List<OutboxEventEntity> claimPendingEvents() { ... }   // ← 프록시를 안 거친다
```

`relay()` 가 같은 인스턴스의 `claimPendingEvents()` 를 직접 부른다. **프록시를 거치지 않으므로
`@Transactional` 이 무시된다.** `candidates.forEach(markSending)` 의 더티 체킹이 flush 될 트랜잭션이
없고, `markPublished` · `markFailed` 도 마찬가지다. **결과적으로 상태기계가 DB 에 한 글자도 안 쓰인다.**
`protected` 라 CGLIB 프록시가 있었더라도 자기호출에서는 소용이 없다.

이것은 CLAUDE.md `.claude/rules/behavior.md` 의 **AOP 체크리스트가 정확히 경고한 고장**이다.
**d-day 는 2세대를 베끼지 않는다.**

### CLAUDE.md 의 예시도 1세대다

CLAUDE.md §「Transactional Outbox + Polling Publisher」의 코드 조각은 락도 배치 상한도 없는 1세대다.
**규약 문서의 예시가 최신 세대가 아니라는 것을 알고 시작한다.**

## 3.2 그래서 d-day 가 쓰는 것

3세대를 베끼되 **분산 락만 바꾼다.**

| | 3세대 | d-day |
| --- | --- | --- |
| 락 | `apps/query-burst` 안의 `DistributedLock`(fencing token) | **ShedLock** |
| 까닭 | | 3세대의 락은 `apps/query-burst` **안에** 산다. `apps/* → apps/*` 금지라 가져올 수 없고, `libs/` 로 승격시키는 것은 이 문서의 범위가 아니다. ShedLock 은 `waiting-service` 가 이미 쓰고(`SchedulerConfig` · `RedisLockProvider`) gradle 두 줄이면 된다 |
| 트랜잭션 | `TransactionTemplate` | **그대로** — 자기호출 함정을 구조적으로 없앤다 |
| 배치 상한 | 100 | 그대로 |
| stale 회수 | `SENDING` 이 1분 넘으면 재수거 | 그대로 |
| 전송 대기 | `get(5, TimeUnit.SECONDS)` | 그대로 |

```java
// com.booster.dday.shared.outbox
public interface DomainOutbox {
    void append(AggregateType type, String aggregateId,
                String eventType, Object payload, String idempotencyKey);
}
```

## 3.3 Outbox 테이블은 하나인가 도메인마다인가

저장소 전례는 **도메인마다**다 (`waiting` · `restaurant` · `auth` 가 각자 테이블과 각자 릴레이).

**여기서는 하나로 간다** — `shared/outbox`, `aggregateType` 으로 가른다.

| | 얻는 것 | 잃는 것 |
| --- | --- | --- |
| 하나 | 릴레이가 하나 → **ShedLock 락도 하나**, 배치·백프레셔·메트릭이 한 벌. 스케줄러 풀 점유가 1 (§6.7 이 실제 제약이다) | 두 컨텍스트가 한 테이블을 공유 → 경계가 약해진다 |
| 도메인마다 | 경계가 선명 | 릴레이 2개 · 락 2개 · 스케줄 슬롯 2개. **`anniversary` 의 알림이 `release` 동기화 폭주에 밀릴 때 그것을 격리할 방법이 오히려 없다** (둘 다 각자 배치 상한만 갖는다) |

경계 약화의 대가는 **`aggregateType` → 토픽 매핑을 `shared` 에 두는 것 하나**로 끝난다.
그 매핑 표가 커지면 그때 쪼갠다.

## 3.4 사슬 — 2단 발행

**한 번에 수신자별 이벤트를 쏟지 않는다.**

```
[1단 · 사실]  경기가 연기됐다                 → dday.release.changed
[2단 · 의도]  A 씨에게 알려야 한다             → dday.notification.requested
```

| | 갈래 | 버린 까닭 |
| --- | --- | --- |
| 가 | 동기화 트랜잭션 안에서 `Watch` 를 조회해 수신자별 Outbox N건을 쓴다 | 관심자 수에 비례해 **동기화 트랜잭션이 길어진다.** 인기 팀 하나가 동기화 전체를 붙든다. 재시도 단위도 통째다 |
| 나 | `notification-service` 가 `Watch` 를 조회해 펼친다 | **남의 DB 를 본다.** `Watch` 는 `release` 소유다 (§9.7). 금지 |
| **다** | **1단은 수신자 없는 사실, 2단은 우리 컨슈머가 `Watch` 로 펼친다** | — |

「다」의 이득 셋.

1. **동기화 트랜잭션이 `Watch` 크기와 무관해진다.** 쓰는 것은 `SportEvent` 갱신 + `DateChange` + Outbox 1건.
2. **펼치기가 독립적으로 재시도된다.** Kafka 컨슈머라 실패하면 재시도되고, 끝내 실패하면 DLT 로 간다
   (`notification-service/config/KafkaRetryConfig.java` 와 같은 모양).
3. **사실 이벤트에 소비자를 더 붙일 수 있다.** 1차 이후의 `search` 색인 갱신(§E-2)이 바로 이 이벤트를 탄다.
   **검색을 뺐어도 `release` 의 모양을 지킨다는 §7 의 약속이 여기서 한 번 더 지켜진다.**

### 흐름

```
┌───────────┐   ┌──────────────┐  ┌────────┐  ┌──────┐  ┌────────────┐  ┌──────────────┐
│ Scheduler │   │ SyncService  │  │  DB    │  │Outbox│  │  Relay     │  │ notification │
│(ShedLock) │   │              │  │        │  │Table │  │ (ShedLock) │  │  -service    │
└─────┬─────┘   └──────┬───────┘  └───┬────┘  └──┬───┘  └──────┬─────┘  └──────┬───────┘
      │ 10분마다        │              │          │             │               │
      ├───────────────▶│              │          │             │               │
      │                │ TheSportsDB (CircuitBreaker · RateLimiter)             │
      │                ├──────────────────────────────▶ (외부)  │               │
      │                │              │          │             │               │
      │                │ ── 한 트랜잭션 ──────────────┐          │               │
      │                │ 이전 값과 대조 │           │          │               │
      │                │ SportEvent 갱신▶│           │          │               │
      │                │ DateChange 저장▶│           │          │               │
      │                │ Outbox append ──────────────▶│          │               │
      │                │ ─────────────────────────────┘          │               │
      │                │              │          │             │               │
      │                │              │          │◀── claim ────┤ 3초마다        │
      │                │              │          │             │ dday.release.changed
      │                │              │          │             ├──────────▶(Kafka)
      │                │              │          │             │               │
   ┌──┴────────────────────────────────────────────────────────┴──┐            │
   │ DateChangeFanoutConsumer (d-day 자기 자신)                    │            │
   │   Watch 조회 → 수신자별 dday.notification.requested 발행      ├───────────▶│
   └───────────────────────────────────────────────────────────────┘            │
                                                                           소비 · 발송
```

## 3.5 토픽 · 키 · 멱등

`libs/storage-kafka/.../KafkaTopic.java` 에 더한다. **이것이 `libs` 를 건드리는 유일한 변경이다.**

| 토픽 | 발행 | 소비 | 파티션 키 | 까닭 |
| --- | --- | --- | --- | --- |
| `dday.release.changed` | d-day (Outbox 릴레이) | d-day fanout · (1차 이후) search | 외부 경기/작품 id | 같은 경기의 변경이 순서대로 |
| `dday.notification.requested` | d-day (fanout · 기념일 스케줄러) | notification-service | **memberId** | 한 회원의 알림이 순서대로. 회원 단위 분산 |
| `dday.release.changed.DLT` · `dday.notification.requested.DLT` | | | | 기존 관례 (`member-events.DLT`) |
| `member-events` (기존) | auth-service | **d-day 가 새로 소비** | memberId | `MemberReference` (§9.6) |

**멱등.** Outbox 는 at-least-once 다. 방어를 두 겹으로 둔다.

| 겹 | 어디 | 무엇 |
| --- | --- | --- |
| 1 | **발행 쪽** | `DomainOutbox.append` 의 `idempotencyKey` 에 유일 제약. 경기 변경 = `(externalId, fromTs, toTs)`, 기념일 알림 = `(anniversaryId, occurrenceDate, notifyOffset)`. **창 누적으로 같은 경기를 여러 번 보므로**(§12.2) 여기서 걸러야 한다 |
| 2 | 소비 쪽 | 이벤트의 Snowflake `eventId` 로 dedup |

**`DateChange` 와 Outbox 가 같은 트랜잭션에 있는 것이 D-4 의 전부다.** 둘이 갈라지면
"이력에는 있는데 알림은 안 갔다" 또는 그 반대가 생기고, 그것은 §9.7 의 `DateChange` 가
존재하는 이유를 무너뜨린다.

## 3.6 `notification-service` 를 건드려야 한다

지금 그 서비스에는 `WaitingEvent` 배치 리스너 하나뿐이다
(`NotificationEventListener.handleWaitingEvents`). `dday.notification.requested` 를 받으려면
리스너를 하나 더 넣어야 하고, **그것은 다른 서비스의 변경**이다. → 열린 결정 §11-2.

---

# 4. 캐시

## 4.1 원칙 — 하나로 셋을 막는다

§9.5 는 `sky` 의 B-6 만 걱정했다. **읽다 보니 같은 고장이 세 자리에 있다.**

| 자리 | 키를 만드는 값 | 어디서 오나 |
| --- | --- | --- |
| `sky` B-6 | `year` | **사용자가 아무 숫자나** |
| `holiday` A-4 | `date` (`/holidays/on/{date}`) | **사용자가 아무 날짜나** |
| `axis` A-5 | `slug` (`/holiday-names/{slug}`) | **사용자가 아무 문자열이나** |

셋 다 "캐시 미스 → 계산/질의 → 캐시 적재" 를 그대로 태우면, 공격자가 아니라 **크롤러 한 마리**로
Redis 가 쓰레기로 찬다. 그러므로 원칙은 하나다.

> ### 캐시 키를 만드는 값은 전부 **우리가 아는 유한 집합**에서 와야 한다.
> 그 집합 밖의 값은 **캐시에 닿기 전에** 400 또는 404 로 잘린다.

| 자리 | 유한 집합 | 밖이면 |
| --- | --- | --- |
| `sky` | `[1583, 2999]` — 그레고리력 시행 이후, 절단 급수가 믿을 만한 범위 | **400** `SKY_YEAR_OUT_OF_RANGE`, 본문에 허용 범위 |
| `holiday` | 동기화된 연도 범위 (설정 `coverage.holiday.years`) | **400** `DATE_OUT_OF_COVERAGE`, 본문에 보유 범위 |
| `axis` | 코퍼스에 실재하는 slug 집합 (Redis Set 으로 유지, 동기화가 갱신) | **404**. 없는 slug 는 **캐시를 건너뛴다** |
| `country` | 204개 코드 | **404** |

**그리고 이 정의역을 API 로 공표한다** — `GET /api/v1/dday/meta/coverage`.
§9.9(4) 의 정신이다: 서버가 조용히 범위를 정해 놓고 안 알리면 그것이 고장이다.
(→ SPEC §10 에 없는 엔드포인트다. 추가를 제안한다.)

**`[1583, 2999]` 로 자르는 것이 B-6 을 배신하지 않는가.** 안 한다. B-6 이 이기려던 상대는
**정적 사이트의 3년**이다 (§B-6). 1417년이면 충분히 이겼고, 그 이상은 요구가 아니라 사고다.

## 4.2 캐시 레지스트리

**모든 값은 「오늘에 독립」이고 「언어 중립」이다.** D-day 와 라벨은 조립 단계에서 붙는다.

| 이름 | 키 | 값 | TTL | 무효화 |
| --- | --- | --- | --- | --- |
| `country:all` | `c:all:v{V}` | 204개 메타 | 없음 | 시드 교체 시 수동 bump |
| `holiday:year` | `h:{cc}:{year}:v{V}` | 그 해 `Public` 포함 공휴일 (언어 중립: 코드 + 영어 이름 + 지역) | 7d | `V` 플립 |
| `holiday:long-weekend` | `lw:{cc}:{year}:v{V}` | 황금연휴 목록 (3분기 **없이**) | 7d | `V` 플립 |
| `holiday:on-date` | `hd:{date}:v{V}` | 그 날 쉬는 국가 코드 목록 | 24h | `V` 플립 |
| `axis:name-hub` | `ax:names:{year}:v{V}` | 이름 허브 | 24h | `V` 플립 |
| `axis:name` | `ax:name:{slug}:{year}:v{V}` | 낱장 | 24h | `V` 플립 |
| `axis:rank` | `ax:rank:{year}:v{V}` | 순위 표 넷 | 24h | `V` 플립 |
| `axis:weekday` | `ax:weekday:{year}:v{V}` | 요일 축 | 24h | `V` 플립 |
| `sky:{kind}:{year}` | `s:{kind}:{year}:v{A}` | UTC 시각 목록 | **핫만 30d** | `A`(알고리즘 버전) bump |
| `label:holiday-name` | `lbl:{lang}:v{V}` | 영어→한국어 라벨 전체 | 1h | `V` 플립 |

**언어를 키에 넣지 않는다.** `HolidayNameLabel` 표(§9.9(1))를 통째로 따로 캐시하고 **조립에서 합친다.**
D-day 를 조립으로 뺀 것과 **정확히 같은 논리**다 — 키를 2배로 가르지 않고, 라벨 하나가 추가될 때
204×5 개 키가 아니라 라벨 캐시 하나만 식는다.

**`sky` 만 `V` 가 아니라 `A` 를 쓴다.** `sky` 는 원천 자료가 없어서 동기화로 변하지 않는다.
값이 바뀌는 유일한 계기는 **우리 알고리즘이 바뀌는 것**이고, 그건 배포 사건이다.
`A` 를 설정값으로 두고 배포와 함께 올린다. 여기가 §9.5 의 "다른 도메인의 대조군" 이 드러나는 자리다.

## 4.3 무효화 — 개별 삭제를 금지한다

동기화 한 번이 어느 `axis` 키를 더럽히는지 **계산할 수 없다.** 스위스 공휴일 하나가 바뀌면
`ax:name:epiphany:2026` 과 `ax:rank:2026` 과 `ax:weekday:2026` 이 같이 틀려진다.
`SCAN` + 삭제로 쫓아가면 수천 키를 훑게 되고, 그건 운영에서 하면 안 되는 짓이다.

> **버전 플립.** 버전 `V` 를 Redis 에 한 키로 두고 모든 캐시 키에 섞는다.
> 무효화 = `INCR`. 옛 키는 TTL 로 자연 소멸한다.

```java
public interface VersionedCache {
    <T> T getOrLoad(CacheNamespace ns, String suffix, Class<T> type, Supplier<T> loader);
    long version(CacheNamespace ns);
    long bump(CacheNamespace ns);           // 동기화 성공 후에만
}
public enum CacheNamespace { COUNTRY, HOLIDAY, AXIS, LABEL, SKY }
```

### 그런데 플립은 스탬피드를 부른다

`INCR` 하는 순간 `axis` 전체가 미스다. `axis` 는 이 서비스에서 제일 무거운 질의다 (§9.4).
**플립 직후가 가장 위험한 순간**이 된다.

> **먼저 심고 나서 뒤집는다 (write-new-then-flip).**
>
> 1. 동기화가 끝난다 → 새 버전 `V+1` 을 **정한다** (아직 공표 안 함)
> 2. 워밍 작업이 무거운 축(A-5~A-7 · `holiday:year` 상위 국가)을 계산해 **`:v{V+1}` 키에 심는다**
> 3. 다 심고 나서 **버전 키를 `V+1` 로 바꾼다**
>
> 사용자 요청은 플립 전에는 `V` 를 보고 플립 후에는 이미 채워진 `V+1` 을 본다. **미스가 0이다.**

대가: 워밍이 실패하면 플립이 안 되고 **낡은 자료가 계속 나간다.** 그래서 워밍 실패는 `SyncRun` 에
남기고 메트릭으로 띄운다. "조용히 옛것이 나가는" 것이 이 구조의 유일한 고장 모드이므로
**그 자리에 알람을 건다.**

## 4.4 B-6 — 오염 방어

정의역을 잘라도 `[1583, 2999]` = **1417년 × 4갈래 = 5,668키**다. 유한하지만 전부 채워질
이유가 없다 (사람은 올해 앞뒤를 본다). 갈래 넷.

| | 방법 | 평가 |
| --- | --- | --- |
| 가 | 전부 캐시, TTL 짧게 | 콜드 키가 핫 키를 LRU 로 밀어낸다. Redis 는 접두사별 축출 정책을 못 준다 |
| 나 | 전부 캐시, 핫만 TTL 길게 | 나아지지만 **축출은 TTL 이 아니라 메모리 압력이 정한다.** 여전히 밀린다 |
| **다** | **핫 윈도우(올해±2)만 캐시. 콜드는 캐시하지 않고 매번 계산** | **오염이 원천적으로 불가능하다** |
| 라 | 캐시 없이 전부 계산 | 핫 경로가 p99 100ms 를 못 지킬 위험 |

**「다」로 간다.** 캐시에 들어갈 수 있는 키가 **5년 × 4갈래 = 20개**로 고정된다.
오염을 「막는」 것이 아니라 **오염될 자리를 없앤다.**

### 「다」가 성립하는 전제 — 콜드 계산이 300ms 안에 끝나야 한다

절기 한 해 = 뉴턴법 반복 24회, 회당 VSOP87 절단 급수 평가 몇 번이다. ~~수 ms 로
예상하나 측정하지 않았다.~~ **쟀다 (2026-09-15).**

| | p50 | p99 | 게이트 |
| --- | --- | --- | --- |
| **절기 한 해** (`SolarTermSolver.termsOf`) | 1.70 ms | **2.07 ms** | 20 ms — **통과** |
| 음력 변환 1건 (`LunisolarCalendar.toSolar`) | 24.9 ms | **31.8 ms** | — (아래) |

> **「다」를 확정한다.** 게이트의 10분의 1이다. 콜드 연도는 캐시하지 않고 매번 계산하며,
> 캐시에 들어갈 수 있는 키는 5년 × 4갈래 = **20개로 고정**된다. 오염될 자리가 없다.
> 별도 Redis 논리 DB 도 필요 없다. (→ §10-1 닫힘)

`apps/d-day/astro-core` 의 `benchmark` 태스크가 그 수를 다시 잰다 (JMH · `@Tag("bench")`).
평소 테스트에는 안 들어간다.

### ⚠ 재다가 딸려 나온 것 — 음력이 절기의 **12배**다

재라고 한 적 없는 값인데 같이 쟀고, 그래서 알았다. 음력 변환 한 건이 **25~32ms** 다.
한 해의 달 구조를 세우려면 삭과 중기를 다 풀어야 하므로 절기 한 해보다 무겁다.

**캐시 정책은 안 바뀐다** — 음력도 §4.2 의 `s:{kind}:{year}` 네 갈래 중 하나라 같은
핫 윈도우에 들어간다. 바뀌는 것은 **다른 자리**다.

| 어디 | 무엇이 걸리나 |
| --- | --- |
| B-4 `GET /sky/lunar?date=` | 콜드 연도면 한 요청이 **25ms**. p99 100ms 예산의 4분의 1을 CPU 로 쓴다 |
| **C-7 기념일 등록** | 향후 **3년치**를 전개하므로 등록 한 번이 **75ms+**. 쓰기 경로라 캐시가 안 받아 준다 |
| §4.4 의 Bulkhead(8) | 콜드 음력이 8개만 동시에 돌아도 **CPU 코어를 다 쓴다.** 절기 기준으로 8을 정했으므로 음력에는 다시 봐야 한다 |

**지금 고치지 않는다.** 1차에서 음력을 부르는 표면은 B-4 하나이고 그것은 읽기라
캐시가 받는다. C-7 이 서는 **착수 8 전에** 정한다 (→ 열린 결정 §10-14).

### 콜드 경로에 Bulkhead 를 건다

캐시가 못 막아 주는 자리이므로 **격리로 막는다.**

```java
@Bulkhead(name = "sky-cold", type = Bulkhead.Type.SEMAPHORE)   // maxConcurrentCalls: 8
```

가상 스레드가 켜져 있어 **동시 요청 수에 상한이 없다**(§6.7). 콜드 연도로 동시에 500개가
들어오면 CPU 를 500개가 나눠 갖고 **핫 경로의 p99 까지 무너진다.** 세마포어 8개가
그것을 막는다. 넘치면 `429` 로 즉시 거절 — §F-5 의 "에러율 < 1%" 안에서 관리한다.

**비계로서도 값어치가 있다.** §D-1·D-2 의 CircuitBreaker 는 *남의 서버* 를 지키는 것이고,
여기 Bulkhead 는 *우리 CPU* 를 지키는 것이다. **같은 라이브러리의 두 도구가 서로 다른 자원을
지키는 대비**가 한 서비스 안에 생긴다.

## 4.5 조립 — 「다음」은 두 해를 읽는다

§9.9(2) 가 `next` 를 조립으로 밀었다. 구현에 함정이 하나 있다.

> **12월 20일에 "다음 절기" 를 물으면 답이 내년 1월에 있다.**
> 올해 캐시만 읽으면 `empty` 가 나오고, 그것은 조용한 고장이다.

그러므로 조립 규칙을 못 박는다.

| 자원 | 읽는 캐시 | 고르는 법 |
| --- | --- | --- |
| A-2 다음 공휴일 | `h:{cc}:{Y}` · `h:{cc}:{Y+1}` | 두 목록을 이어 `pickNext` |
| B-1 다음 절기 | `s:terms:{Y}` · `s:terms:{Y+1}` | 〃 |
| B-3 다음 유성우 | `s:meteors:{Y}` · `s:meteors:{Y+1}` | 〃 |
| A-3 연휴 3분기 | `lw:{cc}:{Y}` | 시작·끝과 그 나라 「오늘」을 견준다 |

`{Y}` 는 **그 나라 대표 시간대의 오늘**이 속한 해다 (E-1). UTC 의 해가 아니다.
이 한 줄이 E-1 을 조립 단계에 박는다.

## 4.6 L1 로컬 캐시는 1차에 넣지 않는다

핫 키가 20개뿐이므로 Caffeine 을 얹으면 Redis 왕복(0.3~1ms)까지 없앨 수 있다.
**1차에서는 넣지 않는다** — 저장소에 Caffeine 전례가 없고, p99 100ms 예산에서 1ms 는
최적화할 자리가 아니다. **부하 테스트에서 Redis 왕복이 p99 의 10% 를 넘게 나오면 그때 넣는다.**
근거 없이 층을 늘리면 무효화가 두 겹이 되고, 두 겹째는 인스턴스마다 따로 식는다. (→ §11-6)

---

# 5. 외부 동기화

## 5.1 두 원천은 성격이 다르다

| | Nager.Date (공휴일) | TheSportsDB (경기) |
| --- | --- | --- |
| 호출 단위 | (국가, 연도) | (리그) — 다음 1건 · 지난 1건 |
| 한 회차 호출 수 | **204국 × 5년 ≈ 1,020** + 황금연휴 1,020 | 리그당 2~3 |
| 주기 | **주 1회** | **10분** |
| 자료 성질 | 거의 안 변한다 | **창 누적** (§12.2) — 같은 것을 계속 다시 본다 |
| 실패 비용 | 낮다 (다음 주) | 중간 (창이 끊기면 경기를 놓친다) |
| 키 | 없음 | 있음 (무료/유료 설정) |

**두 개를 한 스케줄러로 묶지 않는다.** 주기가 다르고 실패 비용이 다르다.
`SyncTarget` enum 으로 가르고, 오케스트레이터만 공유한다.

```java
public interface SyncTask {
    SyncTarget target();
    SyncRunResult run(SyncContext ctx);      // 부분 실패를 담아 돌려준다. 던지지 않는다
}
public interface SyncOrchestrator {
    SyncRunId trigger(SyncTarget target, TriggerSource source);   // SCHEDULE | ADMIN
    Optional<SyncRunView> status(SyncRunId id);
}
```

## 5.2 트랜잭션 경계 = (국가, 연도) 하나

**1,020 호출을 한 트랜잭션에 담지 않는다.** 30개가 실패해도 나머지 990을 반영한다.

| 까닭 | |
| --- | --- |
| 공휴일은 국가별로 독립이다 | 파라과이가 실패한 것이 일본 갱신을 막을 이유가 없다 |
| 한 트랜잭션이 수십 분 | 커넥션 하나를 그동안 붙들고, 롤백 비용도 그만큼이다 |
| 재시도 단위가 통째가 된다 | 999개가 성공해도 하나 때문에 전부 다시 |

`SyncRun`(회차 1건) + `SyncRunItem`(국가·연도 N건, `OK / FAILED / SKIPPED / ABORTED` + 버린 건수).
**실패 항목은 다음 회차에 우선 재시도**하고, 3회 연속 실패하면 메트릭으로 띄운다.
§9.3 의 "버린 건수를 `SyncRun` 에 남긴다" 가 여기 `SyncRunItem` 으로 내려온다 —
**§11.4 가 「평소 0이어야 정상」이라 했으므로 0이 아닌 것 자체가 알람 조건**이다.

## 5.3 급감 가드 — 소프트 삭제의 안전핀

원천에서 사라진 공휴일은 우리도 빼야 한다(소프트 삭제). **그런데 원천이 일시적으로 빈 배열을
주면 그 나라 공휴일이 전멸한다.** 조용히, HTTP 200 으로.

> **응답 건수가 직전 성공 회차 대비 절반 미만이면 반영하지 않는다.**
> `SyncRunItem` 을 `ABORTED` 로 남기고 사람이 본다.

§12.3 이 발견한 것과 같은 종류의 방어다 — *"에러가 아니라 그럴듯한 데이터라서 위험하다."*
원천이 200 으로 헛소리를 하는 일이 이 두 API 에서 **이미 한 번 관측됐다.**

## 5.4 가상 스레드가 제 자리를 찾는 곳

1,020번의 HTTP 대기는 **이 서비스에서 가상 스레드가 가장 정직하게 쓰이는 자리**다.
플랫폼 스레드 풀 20개로 순차 처리하면 회차 하나가 수십 분이다.

```java
// 인터페이스만 — 구현은 CODER 몫
interface HolidaySyncRunner {
    SyncRunResult runYear(int year, List<CountryCode> targets, SyncContext ctx);
}
```

~~Java 25 의 `StructuredTaskScope` 로 국가별 작업을 묶는다~~ —
**재 보니 못 쓴다 (2026-09-15).**

> `jdk-25.0.4` 에서 `java.util.concurrent.StructuredTaskScope` 는 **아직 프리뷰다**
> (JEP 505 — 다섯 번째 프리뷰). 쓰려면 컴파일과 **런타임 양쪽에 `--enable-preview`** 가
> 필요하고, 프리뷰로 컴파일한 클래스는 **다른 JDK 마이너 버전에서 아예 안 뜬다.**
> 서비스 모듈에 그 값을 치를 이유가 없다.

**그리고 치를 이유가 애초에 약했다.** 위에서 든 근거는 *"부분 실패를 다루기 쉽다"* 인데,
§5.2 가 이미 **작업이 던지지 않게** 만들어 두었다 — 단위마다 `SyncRunItem` 을 돌려준다.
그러면 `Executors.newVirtualThreadPerTaskExecutor()` + `invokeAll` 로 충분하고,
**가상 스레드라는 알맹이는 그대로다.** 스코프가 주는 것은 취소 전파와 타임아웃인데
둘 다 여기서 안 쓴다 (속도는 RateLimiter 가, 시간은 회로가 잡는다).

### 그런데 가상 스레드가 병목을 옮길 뿐인 자리가 둘 있다

| | 함정 | 대응 |
| --- | --- | --- |
| 1 | **남의 서버를 때린다.** 1,020 요청이 동시에 나가면 무료 API 를 우리가 DoS 하는 셈 | **Resilience4j `RateLimiter`** 가 진짜 제한자다 (예: 5 rps). 동시성은 가상 스레드가 열고, 속도는 RateLimiter 가 잡는다 |
| 2 | **DB 커넥션 풀이 진짜 상한이다.** 가상 스레드 1,020개가 HikariCP 10개에 몰리면 큐만 길어진다 | **HTTP 취득과 DB 쓰기를 나눈다.** 취득은 넓게, 쓰기는 (국가, 연도) 단위로 짧게. 커넥션 보유 구간에 외부 호출이 **한 번도** 들어가지 않게 한다 |

**2번이 원칙이다: 외부 호출은 절대 트랜잭션 안에 없다.** §2.2 에서 `sky` 에 적용한 규칙의
같은 얼굴이다.

**핀 고정(pinning)** 은 Java 24+ 에서 대부분 해소됐으나(JEP 491), `astro-core` 와 Redisson 경로에
`synchronized` + 블로킹 IO 조합이 들어가지 않도록 코드 리뷰 항목으로 둔다.
`SnowflakeGenerator.nextIdInternal()` 이 `synchronized` 인데 **안에 IO 가 없으므로 안전하다** —
이 판단을 여기 적어 둔다. 나중에 누가 로깅 IO 를 넣으면 그때부터 위험해진다.

## 5.5 CircuitBreaker 배치 — 무엇을 실패로 세나

| 결정 | 값 | 까닭 |
| --- | --- | --- |
| 회로 단위 | **원천마다 하나** (`nager` · `thesportsdb` · `tmdb`) | 국가별로 만들면 204개 회로가 각자 표본 부족이라 **영영 안 열린다** |
| 실패로 세는 것 | 5xx · 커넥션 실패 · 타임아웃 | |
| **실패로 안 세는 것** | **4xx** (`ignoreExceptions`) | 한 나라가 404 를 주는 것은 **원천의 장애가 아니라 그 나라의 사정**이다. 그걸로 회로를 열면 나머지 203국이 막힌다 |
| 타임아웃 | RestClient 읽기 타임아웃 **<** TimeLimiter | 소켓에서 먼저 실패해야 CB 가 그것을 실패로 기록한다. `CatalogServiceClient` 가 이미 이렇게 해 두었다 (connect 1s / read 3s vs TimeLimiter 4s) — 그 값을 그대로 따른다 |
| 폴백 | 예외를 던지지 않고 **`SyncRunItem.FAILED` 를 돌려준다** | §5.2 의 부분 실패와 맞물린다 |

`libs/core-resilience/ResilienceConfig` 의 기본값(실패율 50% · OPEN 1초 · 창 100)은
**사용자 요청용이지 배치용이 아니다.** 배치는 호출 수가 적고(리그 2건) 회복 대기가 길어야 한다.
`CatalogCircuitBreakerConfig` 처럼 **이름 붙은 인스턴스**를 d-day 쪽 `config/` 에 따로 둔다.

### F-5 표의 한 줄이 1차에서 비어 있다는 것을 적어 둔다

§F-5 는 "외부 연동이 읽기 경로에 남은 것 < 1,000ms" 와 "CircuitBreaker 가 열린 상태에서도
p99 를 지켜야 한다" 를 요구한다. **1차에서 읽기 경로에 외부 호출이 하나도 없다** —
§E-2 가 `release` 를 「당겨와 저장」으로 바꿨기 때문이다 (§7 · §12.2).

> **그러므로 그 줄의 통과 조건은 「해당 없음」이고, 「해당 없음이라는 사실」이 통과 조건이다.**
> 부하 테스트에서 외부 호스트로 나가는 커넥션이 **0** 임을 확인한다. 0이 아니면 설계가 샌 것이다.

## 5.6 락 — 스케줄러와 수동 트리거가 **같은 락**을 잡아야 한다

`@SchedulerLock` 은 스케줄 메서드에만 붙는다. **`POST /admin/sync/{target}`(§10.7)은 스케줄
메서드가 아니다.** 애노테이션만 믿으면 스케줄러가 도는 중에 운영자가 눌러 **이중 실행**이 된다.

> `LockProvider` 를 오케스트레이터에 **직접 주입**해 명령형으로 잡는다.
> 락 이름은 `dday-sync-{target}` 하나. 스케줄 경로와 수동 경로가 **같은 이름**을 쓴다.

| 락 이름 | 누가 |
| --- | --- |
| `dday-sync-HOLIDAY` | 주 1회 스케줄 · `POST /admin/sync/holiday` |
| `dday-sync-SPORT_EVENT` | 10분 스케줄 · `POST /admin/sync/sport-event` |
| `dday-sync-MOVIE` | 일 1회 스케줄 · 수동 |
| `dday-outbox-relay` | 3초 릴레이 |
| `dday-cache-warm` | 플립 전 워밍 (§4.3) |
| `dday-anniversary-notify` | 알림 스캔 (C-10) |
| `dday-occurrence-rollforward` | 연 1회 (§2.3) — **매일 1/365 로 바뀌었다**, SCHEMA §5.2 |
| `dday-retention` | 보존 청소 (SCHEMA §10 이 더했다) |

**수동 트리거는 동기 실행하지 않는다.** 1,020 호출을 HTTP 응답 안에서 기다릴 수 없다.

```
POST /api/v1/dday/admin/sync/{target}
  → 202 Accepted { "runId": ... }       락을 잡았다
  → 409 Conflict { "runningRunId": ... } 이미 돌고 있다
GET /api/v1/dday/admin/sync/runs/{runId}
```

`runs/{runId}` 는 SPEC §10.7 에 없다 (`GET /admin/sync/runs` 만 있다). **추가를 제안한다** —
202 를 주고 조회할 곳이 없으면 202 가 거짓말이다.

## 5.7 스케줄러 풀 크기 — 조용한 함정

§5.6 의 표에 스케줄 작업이 **일곱**이다. Spring 의 기본 `TaskScheduler` 풀 크기는 **1** 이다.

> 공휴일 동기화(수십 분)가 그 하나를 붙들면 **Outbox 릴레이가 수십 분 멈춘다.**
> D-4 알림이 수십 분 늦고, 로그에는 아무 에러도 안 찍힌다.

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 10         # 스케줄 작업 수 이상 — SCHEMA §10 이 dday-retention 을 더해 여덟이 됐다
```

**`waiting-service` 도 같은 위험을 안고 있다** (릴레이 3초 + 노쇼 1분 + 자정 정리).
이 저장소에서 아직 안 터진 고장이고, d-day 는 스케줄이 일곱이라 **훨씬 빨리 터진다.**

> **확인했다 (2026-09-15) — 덮는다.** Spring Boot 4.0.1 의
> `TaskSchedulingConfigurations.TaskSchedulerConfiguration` 안에 `@ConditionalOnThreading(VIRTUAL)`
> 인 `taskScheduler` 빈이 있고, `spring.threads.virtual.enabled: true` 면 그쪽이 떠서
> `SimpleAsyncTaskScheduler` 가 된다. **스케줄마다 가상 스레드를 새로 뽑으므로 풀 크기라는
> 개념 자체가 없다.** 위의 함정은 우리 설정에서는 이미 없는 셈이다. (→ §10-4 닫힘)
>
> **그래도 `pool.size: 10` 을 지우지 않는다.** 누가 가상 스레드를 끄는 순간 기본값이
> **1** 로 돌아오고 함정이 그대로 복귀한다. 설정 한 줄이 그 복귀를 막는다.
> `SchedulerThreadingTest` 가 세 경우를 다 문다 — 가상 스레드 켬 · 끔+설정 · 끔+무설정(풀이 1).

---

# 6. 천문 계산의 자리

## 6.1 어디에 두나 — §1.2 에서 정했다

`apps/d-day/astro-core`. Spring 없는 순수 자바 모듈. `sky` 컨텍스트만 이것을 의존한다 (R4).

| 층 | 무엇 | 어디 |
| --- | --- | --- |
| 계산 | VSOP87 절단 · Meeus · ΔT · 황경 역산 | `astro-core` |
| 정책 | 캐시 · 정의역 검사 · 핫/콜드 · Bulkhead | `sky/application` |
| 표현 | 라벨 · 시간대 · D-day 조립 | `sky/web` + `shared/dday` |

## 6.2 IMO 함정 — (가) 와 (나)

ASTRO-CHECKPOINTS §4 가 남긴 물음이다. 요약하면:
**IMO 의 황경은 `equinox 2000.0` 기준이고 절기의 황경은 그때의 겉보기값이라, 2026년에 약 9시간
어긋난다. 하나의 「황경 → 시각」 함수에 그대로 먹이면 모든 극대가 9시간 이르게 나오는데
날짜는 대개 그대로라 1층도 절기 테스트도 조용하다.**

| | 무엇 | 얻는 것 | 잃는 것 |
| --- | --- | --- | --- |
| **가** | IMO 황경을 그 해의 겉보기 황경으로 세차 보정하고 역산 | **임의 연도(B-6)가 유성우에도 열린다** | **공표값과 어긋난다** ↓ |
| **나** | 공표된 시각을 자료로 보유 | 공표값과 정확히 일치. 함정이 존재할 수 없다 | **IMO 가 낸 해만 답할 수 있다.** 지금 2026 한 해뿐 (§6-3 빈 자리) |

## 6.3 정한 것 — **(나) 를 뼈대로, (가) 를 대체값으로**

> 1. **공표값이 있는 해** → **그대로 낸다.** `source: IMO`, `precision` 을 그대로 싣는다
> 2. **공표값이 없는 해** → 세차 보정한 역산으로 근사값을 내고 **`source: COMPUTED`,
>    `precision: "date"`** 로 낮춰 표시한다

근거 셋.

### (1) 정확도의 상한이 원천에 있다

유성우 극대는 궤도 요소에서 나오는 값이 아니라 **관측 통계(ZHR 프로파일)** 에서 나온다.
IMO 스스로 이렇게 적었다 (ASTRO-CHECKPOINTS §5 인용).

> in many cases, such maxima are not known more precisely than
> **to the nearest degree of solar longitude**

**계산이 원천보다 나을 수 없는 자리**다. 여기서 계산을 1급으로 두면, 우리는 IMO 보다 **덜 맞는 값을
더 정밀한 척** 내게 된다. 절기는 정반대다 — 절기는 정의가 황경이므로 계산이 곧 정답이다.
**두 갈래는 인식론이 다르고, 그래서 구조도 달라야 한다.**

### (2) 검산점이 살아남는다

(가)만 쓰면 IMO 픽스처 11건은 *"우리 계산의 검산"* 이 아니라 *"우리가 IMO 를 얼마나 못 맞히나"* 의
기록이 된다. 세차 보정을 완벽히 해도 관측 통계와는 몇 시간 다를 수 있으니, 그 테스트는
**언제 깨져야 하는지 아무도 모르는 테스트**가 된다.

(나)로 가면 둘 다 산다.

| 픽스처 | (나)에서 무엇을 검산하나 |
| --- | --- |
| 유성우 11건 | **자료 적재 · UT 해석 · 정렬 · `precision` 전달.** 한 건이라도 어긋나면 파이프라인 고장 |
| 절기 72 · 삭망 74 | 계산 커널 그 자체 (허용오차 1분) |

그리고 ASTRO-CHECKPOINTS §5 가 말한 유성우 픽스처의 **진짜 값어치** —
*"15°의 배수가 아닌 황경에서 시각 변환을 검산하는 것"* — 는 **(가) 경로의 단위 테스트**로 따로 산다.
IMO 값을 정답으로 쓰는 대신, **(가) 로 보정한 황경을 (가) 로 역산하면 제자리로 돌아오는가**
(왕복 항등성)를 본다. 이건 원천 없이도 검산되는 성질이다.

### (3) 함정이 구조적으로 못 들어온다 — 타입으로 막는다

§4 의 함정은 **테스트로 못 잡는 종류**다. 문서 자신이 그렇게 적었다 — *"날짜는 대개 그대로라
1층도 절기 테스트도 조용하다."* 테스트가 못 잡는 것은 **컴파일러가 잡아야 한다.**

```java
// com.booster.dday.astro.frame — 두 기준계를 다른 타입으로 나눈다
public record ApparentEclipticLongitude(double degrees) { }
public record J2000EclipticLongitude(double degrees)   { }

public interface PrecessionModel {
    ApparentEclipticLongitude toApparent(J2000EclipticLongitude l, Instant at);
    J2000EclipticLongitude    toJ2000  (ApparentEclipticLongitude l, Instant at);
}

// com.booster.dday.astro.solar
public interface SolarTermSolver {
    /** 겉보기 황경만 받는다. J2000 값은 여기 들어올 수 없다. */
    Instant timeOf(ApparentEclipticLongitude longitude, int aroundYear);
    List<SolarTerm> termsOf(int year);
}
```

**IMO 의 `140.0°` 를 `timeOf` 에 그대로 먹이는 길이 컴파일 에러가 된다.**
`PrecessionModel.toApparent` 를 지나야만 들어갈 수 있고, 그 호출이 코드에 보이면
**리뷰어가 세차 보정을 보게 된다.** `double` 하나로 두면 보이지 않는다.

ASTRO-CHECKPOINTS §4 는 *"이 차이는 해마다 약 8.5분씩 자란다"* 고 적었다.
즉 **이것은 오늘의 버그가 아니라 미래의 버그**이고, 미래의 버그는 타입으로 막는 것이 맞다.

## 6.4 자료를 들고 있되 DB 는 쓰지 않는다 — §9.5 유지

(나)는 "공표 시각을 자료로 보유" 이므로 §9.5 의 「`sky` 는 DB 를 쓰지 않는다」와 부딪혀 보인다.
**부딪히지 않는다. 자원 파일로 들고 있는다.**

```
astro-core/src/main/resources/meteor/imo-2026.json
astro-core/src/main/resources/meteor/imo-2027.json   (IMO 가 내면 더한다)
```

| 왜 DB 가 아닌가 | |
| --- | --- |
| 갱신 주기가 **연 1회**이고 손으로 확인해야 한다 | §9.2 의 `country` CLDR 시드와 같은 성격이다 — "바뀌면 그것 자체가 사람이 확인할 사건" |
| DB 에 넣으면 동기화·마이그레이션·`SyncRun` 이 따라온다 | §9.5 의 "다른 도메인의 대조군" 이라는 `sky` 의 존재 이유가 사라진다 |
| 배포와 함께 버전이 맞는다 | 계산 코드와 자료가 한 아티팩트 안에 있다. 둘이 갈라질 수 없다 |

**갱신 절차는 ASTRO-CHECKPOINTS §7 과 같은 길을 쓴다** — `tools/` 의 스크립트가 자원 파일도
같이 낸다. 검산점 픽스처와 운영 자료가 **같은 취득 경로**를 갖는다는 것이 중요하다.
둘이 다른 경로로 들어오면 둘을 견주는 테스트가 무의미해진다.

```java
// com.booster.dday.astro.meteor
public sealed interface MeteorMaximum permits PublishedMaximum, EstimatedMaximum { }
public record PublishedMaximum(String imoCode, Instant utc, Precision precision, String source)
        implements MeteorMaximum { }
public record EstimatedMaximum(String imoCode, Instant utc, ApparentEclipticLongitude at)
        implements MeteorMaximum { }

public interface MeteorShowerCatalog {
    List<MeteorMaximum> of(int year);      // 공표 우선, 없으면 추정
    Set<Integer> publishedYears();         // meta/coverage 가 이것을 공표한다 (§4.1)
}
```

`sealed` 로 두면 **응답을 만드는 쪽이 둘을 구별하는 것을 잊을 수 없다.**
"공표값인지 추정값인지 안 알리고 내보내는 것" 이 §9.9(4) 가 금지한 고장이다.

## 6.5 ΔT 와 검산점의 관계 — 착수 전에 확인할 것

2층 검산점의 허용오차는 **1분**이다 (ASTRO-CHECKPOINTS §5). ΔT 모델의 오차가 그 안에 들어야
테스트가 의미를 갖는다. 2025~2027 구간에서는 ΔT 가 관측으로 잘 정해져 있어 문제가 없을 것이나,
**B-6 이 임의 연도를 받으므로 1583~2999 전 구간의 ΔT 모델이 필요하다.**
모델 선택(Espenak–Meeus 다항식 vs 관측 표 + 외삽)은 구현 결정이다. (→ §11-7)

---

# 7. 게이트웨이 변경 넷 — 순서와 위험

## 7.1 순서 — **4번을 제일 먼저**

SPEC §10.1 은 넷을 1·2·3·4 로 적었다. **그 순서로 하면 안 된다.**

| 순서 | 무엇 | 왜 이 자리인가 | 그때 매칭되는 요청 |
| --- | --- | --- | --- |
| **1** | **4번 — `/api/v1/dday/me/**` 게스트 통과 차단 (새 코드)** | **경로가 아직 안 열려 있어 무해하다.** 열리는 순간부터 막혀 있다. 나중에 넣으면 **그 사이가 구멍**이다 | **0건** |
| 2 | 1번 — `PATH_SERVICE_MAPPING` | `special-days` 와 `dday` 를 **둘 다** `"d-day"` 로. 어차피 4번 때문에 이 파일을 여니 같이 한다 | 0건 |
| 3 | 2번 — 라우팅 predicate | **여기서 새 표면이 열린다.** 이 시점에 1·4 가 이미 지키고 있다 | 열림 |
| 4 | 3번 — `admin-blocked-paths` | ⚠ §7.3 을 읽고 나서 한다 | |

> **순서 자체가 방어다.** 4번의 유일한 실패 모드는 「조용히 안 막힘」이고, 그것은
> 터질 때까지 아무도 모른다. **구멍이 존재한 적이 없게** 만드는 것이 사후에 메우는 것보다 싸다.

## 7.2 4번의 모양 — 매핑을 늘리지 말고 리스트를 신설한다

```yaml
gateway:
  jwt:
    guest-blocked-paths:          # 신설
      - /api/v1/dday/me/**
```

```java
// JwtAuthorizationFilter
if (token == null) {
    if (isGuestBlockedPath(path)) {
        return onError(exchange, "Login required", HttpStatus.UNAUTHORIZED);
    }
    if ("d-day".equals(requiredService)) {
        return handleGuestAccess(exchange, chain);
    }
    return onError(exchange, "No access_token cookie", HttpStatus.UNAUTHORIZED);
}
```

**`PATH_SERVICE_MAPPING` 에 `/api/v1/dday/me/**` 를 더해서 푸는 길을 택하지 않는 까닭 —
그 지도는 순서가 없다.**

```java
private static final Map<String, String> PATH_SERVICE_MAPPING = Map.of(...);   // 불변 해시맵
...
for (Map.Entry<String, String> entry : PATH_SERVICE_MAPPING.entrySet()) {       // 순서 미보장
    if (pathMatcher.match(entry.getKey(), path)) return entry.getValue();
}
```

지금은 패턴이 서로 안 겹쳐 무해하다. **`/api/v1/dday/**` 와 `/api/v1/dday/me/**` 를 같이 넣는 순간
어느 것이 먼저 맞을지 JVM 이 정한다.** 겹치는 패턴을 이 지도에 넣지 않는다.
(SPEC §10.1 의 메모대로 지도를 설정으로 빼고 `List<Entry>` 로 **순서를 갖게** 하는 편이 낫다 —
같이 하기를 권한다. 잔재 `/api/v1/diary/**` 도 이때 지운다.)

### 테스트로 못 박을 것

| 요청 | 기대 |
| --- | --- |
| 토큰 없이 `GET /api/v1/dday/countries` | 200 · `X-User-Id: -1` · `ROLE_GUEST` |
| 토큰 없이 `GET /api/v1/dday/me/anniversaries` | **401** |
| 토큰 없이 `GET /api/v1/dday/me` | **401** (경계값 — `me/**` 가 `me` 를 안 잡는다면 `me` 도 리스트에 넣는다) |
| 유효 토큰 + `d-day` 권한 | 200 · `X-User-Id: {sub}` |
| 유효 토큰 + `d-day` 권한 없음 | 403 (§7.5 를 읽을 것) |
| 만료 토큰으로 `me/**` | 401 |

## 7.3 ⚠ 발견 — 3번을 그대로 하면 **운영자도 막힌다**

```java
if (isAdminBlockedPath(path)) {
    return onError(exchange, "Admin API access is blocked", HttpStatus.FORBIDDEN);
}
```

**토큰을 보기도 전에 무조건 403 이다.** 권한이 있든 없든 막힌다.
그런데 §10.7 은 `POST /admin/sync/{target}` · `GET /admin/sync/runs` 를 **운영자가 쓰는 API** 로
정의했다 (E-4). SPEC §10.1 표의 3번을 그대로 하면 **E-4 가 게이트웨이 밖에서는 영영 못 쓰인다.**

| | 갈래 | |
| --- | --- | --- |
| **가** | 그대로 둔다. **admin 은 게이트웨이를 통과하지 않는다 — 내부 접근 전용** | |
| 나 | `admin-blocked-paths` 대신 **역할 검사**로 바꾼다 (`ROLE_ADMIN` 이면 통과) | 게이트웨이에 역할 기반 인가를 새로 넣는 일이다 |

**1차는 「가」.** 근거 셋.

1. **기본값이 안전하다.** 공개 인터넷에서 동기화를 트리거할 수 있는 표면을 여는 것은
   이 서비스가 감당할 이유가 없는 위험이다.
2. **`admin/sync` 는 사람이 드물게 쓴다.** 내부망·포트포워딩으로 충분하다.
3. **「나」는 d-day 의 범위가 아니라 게이트웨이의 범위다.** 역할 인가 모델을 여기서 발명하면
   다른 서비스들과 어긋난다.

**대신 두 가지를 한다.**

- **SPEC §10.7 에 한 줄을 더한다** — *"admin API 는 게이트웨이를 통과하지 않는다. 내부 접근 전용."*
  안 적으면 나중에 "왜 403 이지" 로 몇 시간이 간다.
- **서비스 쪽에도 `X-User-Role` 검사를 둔다.** 게이트웨이를 안 타는 경로가 생겼으므로
  **서비스가 유일한 방어가 아니라 마지막 방어**여야 한다. `shared/web/AdminOnly`.

## 7.4 2번은 깨는 변경인가 — 저장소 안에는 소비자가 없다

`/api/v1/special-days/**` 를 부르던 컨트롤러(`SpecialDayController` — `history/2026-01-30-dday-application-web-layer.md`)는
**이미 지워졌다.** 지금 `d-day-service` 는 `DDayServiceApplication` 하나뿐이다.
`frontends/dday-static` 은 빌드 타임 정적이라 이 API 를 부르지 않는다 (§3).

> **그러므로 병기 기간 없이 갈아 끼운다.** 저장소 밖에 소비자가 있더라도
> **그 API 는 이미 죽어 있다** — 껍데기 서비스가 404 를 내고 있을 뿐이다.
> 잃을 것이 없다. (→ 한 번은 확인할 것. §11-5)

## 7.5 ⚠ 발견 — 로그인하면 공개 읽기가 막힐 수 있다

게스트 통과는 **토큰이 없을 때만** 탄다. 토큰이 있는데 `access_services` 에 `"d-day"` 가 없으면 **403** 이다.

> **비로그인은 되는데 로그인하면 안 되는 자리가 생긴다.**
> 공개 읽기가 대부분인 서비스(§10.1 의 공개 갈래 전부)에서 이건 뒤집힌 동작이다.

이 서비스만의 문제가 아니라 **필터의 모델 문제**다 — "서비스 접근 권한" 이 서비스 단위라서
**한 서비스 안에 공개 표면과 비공개 표면이 섞이는 경우**를 표현하지 못한다.
경로를 세 갈래로 나눈(§10.1) 지금은 이렇게 말할 수 있다.

> 공개 갈래는 **`requiredService` 검사 자체를 건너뛴다.** 인가는 `me/` 와 `admin/` 에만 건다.

다만 이것은 게이트웨이 필터의 인가 모델 변경이라 §10.1 의 넷을 넘어선다. (→ §11-3)

## 7.6 다섯 번째 변경 — 문서

`docs/AUTH_FLOW.md` §3.2 의 표(`d-day → /api/v1/special-days/**`)와 §「호출 예시」의
`curl .../api/v1/special-days/today` 가 **전부 낡는다.** 문서가 코드보다 오래 살아서
다음 사람을 틀린 경로로 보낸다. **넷이 아니라 다섯이다.**

---

# 8. 관측과 통과 조건

## 8.1 지금 빠져 있는 의존 하나

`apps/d-day/d-day-service/build.gradle` 에 **`libs:core-observability` 가 없다.**
`waiting-service` · `query-burst-msa:order-service` 는 갖고 있다. §F-5 를 측정하려면 필요하다.
착수 시 더할 것 — ShedLock 두 줄과 함께.

## 8.2 봐야 할 것

| 갈래 | 메트릭 | 왜 |
| --- | --- | --- |
| 캐시 | 이름별 hit/miss · 플립 후 미스 곡선 | §4.3 의 "미스 0" 이 지켜지는지 |
| `sky` | 핫/콜드 비율 · 콜드 계산 시간 · Bulkhead 거절 | §4.4 의 전제가 참인지 |
| 동기화 | `SyncRunItem` 상태별 건수 · **버린 건수** · ABORTED | §11.4 — **버린 건수는 평소 0이어야 정상** |
| Outbox | PENDING 적체 · SENDING stale 회수 수 · 릴레이 지연 | D-4 사슬의 건강 |
| CB | `resilience4j_circuitbreaker_state` (원천별) | 4xx 를 실패로 안 세는 설정이 실제로 먹는지 |
| 스케줄 | 작업별 소요 · **락 미획득 횟수** | §5.7 의 풀 고갈이 보인다 |
| 외부 | **읽기 요청 중 외부 호출 수 = 0** | §5.5 의 통과 조건 |

## 8.3 부하 테스트 — p95 를 베끼지 않는다

§F-5 가 못 박았다. `apps/query-burst-msa/load-test/` 의 스크립트와 Grafana 패널은 **p95** 위에 서 있다.
거기서 임계값을 복사해 오면 **조용히 느슨해진다.** d-day 의 k6 임계값은 처음부터 p99 로 쓴다.

| 갈래 | 시나리오 | p99 |
| --- | --- | --- |
| 캐시 히트 읽기 | A-1~A-4 · B (핫 윈도우) | < 100 ms |
| 파생 축 | A-5~A-7 | < 300 ms |
| 개인 쓰기 | C-2 · C-4 (음력 포함) | < 300 ms |
| **콜드 연도** | B-6, 정의역 안 · 캐시 밖 | **§4.4 에서 새로 재는 값** |
| 외부 잔여 | — | **해당 없음** (§5.5) |

콜드 연도 시나리오를 **부하 테스트의 1급 항목**으로 둔다. §9.5 가 지목한 위험이 여기서만 보인다.

---

# 9. 착수 순서

§8 의 원칙 — **가로지르는 것을 초반에 둔다** — 을 그대로 따른다.

| | 무엇 | 왜 이 자리 |
| --- | --- | --- |
| ~~0~~ | ~~게이트웨이 **4번**~~ | **끝남** — `guest-blocked-paths` · 테스트 10 |
| ~~1~~ | ~~모듈 골격~~ | **끝남** — main 클래스패스가 Lombok 하나뿐임을 `verifyNoSpring` 이 지킨다 |
| ~~2~~ | ~~`shared` — `DDayCalculator` · `VersionedCache` · `DomainOutbox` + 3세대 릴레이~~ | **끝남** — 테스트 99. 여기서 §10-4 도 닫혔다 |
| ~~3~~ | ~~`country` 시드 (CLDR)~~ | **끝남** — 204개국. 주말 분포 195·8·1 이 SPEC §5 와 같다 |
| ~~4~~ | ~~`astro-core` 절기 · 삭망~~ | **끝남** — 절기 72건 최대 38초 · 삭망 74건 최대 39초 (허용 1분) |
| ~~5~~ | ~~`holiday` 동기화 + **1층 검산점**~~ | **끝남** — 1층 11해 × 넷이 전부 맞는다. 동기화는 아직 한 번도 안 돌렸다 |
| 6 | `sky` 캐시 정책 + 콜드 계산 측정 (§4.4) | 여기서 §11-1 이 닫힌다 |
| 7 | `axis` + 버전 플립 + 워밍 | 무거운 질의는 캐시 구조가 선 뒤에 |
| 8 | **C-7** — `anniversary` + `Occurrence` 투영 + `sky` 포트 | 가로지르는 것 하나 |
| 9 | **D-4** — `release`(KBO) + `DateChange` + Outbox + 2단 발행 | 가로지르는 것 둘 |
| 10 | 게이트웨이 1·2·3 + `docs/AUTH_FLOW.md` | 표면이 실제로 있을 때 연다 |
| 11 | E-3 인기 · 부하 테스트 | |
| — | E-2 검색 | **1차 밖** (§7) |

**4를 5보다 앞에 두는 것이 ASTRO-CHECKPOINTS §1 의 요구다** — *"2층 없이 시작하면 날짜는 다 맞는데
시각이 전부 틀린 상태로 완성됐다고 믿게 된다."* 그리고 5의 1층 검산점은 4가 있어야 성립한다.

---

# 10. 열린 결정 — 구현이 닫는다

**설계에서 못 닫은 것만 적는다.** 닫을 수 있는데 안 닫은 것은 없다.

| | 물음 | 무엇에 달렸나 | 언제 |
| --- | --- | --- | --- |
| ~~1~~ | ~~**콜드 연도를 캐시할 것인가**~~ | **닫힘 — 안 한다.** p99 **2.07ms** 로 게이트(20ms)의 10분의 1이다. 캐시 키가 20개로 고정되어 오염될 자리가 없다 (§4.4) | 끝남 |
| 2 | **`notification-service` 에 d-day 리스너를 넣을 것인가** (§3.6) | 다른 서비스의 변경이다. 아니면 d-day 가 채널을 직접 치고 알림 서비스는 나중에 | 착수 9 전 |
| 3 | **공개 갈래에서 `requiredService` 검사를 건너뛸 것인가** (§7.5) | 게이트웨이 인가 모델 변경. §10.1 의 넷을 넘어선다 | 착수 10 |
| ~~4~~ | ~~**`@Scheduled` 가 가상 스레드를 타는가**~~ | **닫힘 — 탄다.** `TaskSchedulingConfigurations` 의 `taskScheduler` 빈이 `@ConditionalOnThreading(VIRTUAL)` 이라 `spring.threads.virtual.enabled: true` 면 `SimpleAsyncTaskScheduler` 가 뜨고 **풀 크기라는 개념이 사라진다.** 그래도 `spring.task.scheduling.pool.size: 10` 은 남긴다 — 가상 스레드를 끄면 기본값 **1** 로 돌아가 §5.7 의 함정이 그대로 복귀한다. `SchedulerThreadingTest` 가 셋 다 문다 | 끝남 |
| 5 | **`/api/v1/special-days/**` 의 저장소 밖 소비자** (§7.4) | 있으면 병기 기간, 없으면 갈아 끼움. 저장소 안에는 없다 | 착수 10 |
| 6 | **L1(Caffeine) 도입** (§4.6) | 부하 테스트에서 Redis 왕복이 p99 의 10% 를 넘는가 | 착수 11 이후 |
| ~~7~~ | ~~**ΔT 모델**~~ | **닫힘** — 2005~2050 은 Espenak·Meeus, 그 밖은 장기 포물선. 1600~2005 의 정밀 다항식은 **검산점이 없어 넣지 않았다**(`DeltaT.isRefined` 가 그 사실에 답한다). B-6 이 그 구간을 실제로 요구할 때 원천을 받아 채운다 | 끝남 |
| 8 | **음력 기준 자오선** (§2.4) | 1차는 KST 고정. 포트 서명에는 남긴다. 다른 나라 음력을 열 때 닫는다 | 1차 이후 |
| 9 | **`HolidayNameLabel` 문턱** (§9.9(1)) | "문턱을 넘는 이름만" 의 문턱이 몇인가. 코퍼스를 보고 정한다 | 착수 5 |
| 10 | **급감 가드 임계치** (§5.3) | 50% 는 가정이다. 몇 회차 관측 후 조정 | 착수 5 이후 |
| 11 | **`Watch` fan-out 상한** (§3.4) | 한 경기의 관심자가 수만이면 2단도 길어진다. 1차 KBO 규모에서는 문제가 아니다 | 관측 후 |
| ~~12~~ | ~~**소프트 삭제 보존 기간**~~ | **닫힘** — 성공 회차 **3번** (SCHEMA §3.3). `anniversary` 는 소프트 삭제 자체를 안 한다(§5.6) | 끝남 |
| 13 | **검색 색인 · 검색 캐시 키** | SPEC §7 의 남은 결정 3·4 | **1차 이후** |
| **14** | **음력 변환 25ms 를 어떻게 할 것인가** (§4.4) | 읽기 경로는 캐시가 받는다. **문제는 C-7 등록이 3년치를 전개하는 것**(75ms+)과 콜드 경로 Bulkhead 크기다. 전개 폭을 줄이거나 · 등록을 비동기로 돌리거나 · 계산을 고치거나 | **착수 8 전** |

---

# 11. SPEC 에 반영을 제안하는 것

설계하다 SPEC 과 어긋나거나 SPEC 에 없는 것이 나온 자리다.

| | 무엇 | 어디 |
| --- | --- | --- |
| 1 | **`admin/` 은 게이트웨이를 통과하지 않는다 (내부 전용)** — 3번을 그대로 하면 운영자도 403 | §10.1 · §10.7 |
| 2 | `GET /admin/sync/runs/{runId}` 추가 — 202 를 주고 조회할 곳이 없다 | §10.7 |
| 3 | `GET /meta/coverage` 추가 — 정의역(연도·날짜 범위·유성우 공표 연도)을 공표한다 | §10 |
| 4 | 게이트웨이 변경은 **넷이 아니라 다섯** — `docs/AUTH_FLOW.md` | §10.1 |
| 5 | §F-5 표의 「외부 연동이 읽기 경로에 남은 것」은 **1차에 해당 없음**이고, 그 사실이 통과 조건 | §F-5 |
| 6 | 유성우는 **공표값 보유(나)** 로 닫혔다 — 자료는 DB 가 아니라 자원 파일이므로 §9.5 는 그대로 유지 | §B · ASTRO-CHECKPOINTS §4 |

---

## 참고 — 이 설계가 근거로 삼은 저장소 안의 실제 코드

아래 여섯 가지 주장은 **2026-09-14 에 파일을 직접 열어 확인했다** — 2세대 Outbox 의 자기호출,
`isAdminBlockedPath` 가 토큰 검사보다 앞이라는 것, `PATH_SERVICE_MAPPING` 이 `Map.of` 라는 것,
`WaitingUser` 가 `libs` 에 있다는 것, d-day 에 `core-observability` 가 없다는 것,
그리고 저장소 어디에도 스케줄러 풀 크기 설정이 없다는 것.

| 무엇 | 경로 |
| --- | --- |
| Outbox 1세대 (락·상한 없음) | `apps/waiting-service/src/main/java/com/booster/waitingservice/waiting/event/OutboxMessageRelay.java` |
| Outbox 2세대 (**AOP 자기호출로 트랜잭션이 안 걸린다**) | `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/event/OrderOutboxRelay.java` |
| Outbox 3세대 (**이것을 베낀다**) | `apps/query-burst/src/main/java/com/booster/queryburst/order/event/OutboxMessageRelay.java` |
| ShedLock 설정 전례 | `apps/waiting-service/src/main/java/com/booster/waitingservice/config/SchedulerConfig.java` |
| CB + RestClient 타임아웃 전례 | `apps/query-burst-msa/order-service/src/main/java/com/booster/queryburstmsa/order/infrastructure/CatalogServiceClient.java` |
| **게스트 통과 · admin 무조건 403 · `Map.of` 순서 미보장** | `infrastructure/gateway-service/src/main/java/com/booster/gatewayservice/filter/JwtAuthorizationFilter.java` |
| 라우팅 · `admin-blocked-paths` | `infrastructure/gateway-service/src/main/resources/application.yml` |
| 집합 모듈 내 형제 의존 전례 | `apps/query-burst-msa/order-service/build.gradle` |
| `libs` 에 도메인이 샌 자국 (따라 하지 않는다) | `libs/storage-redis/src/main/java/com/booster/storage/redis/domain/WaitingUser.java` |
| 토픽 enum (여기에 d-day 토픽을 더한다) | `libs/storage-kafka/src/main/java/com/booster/storage/kafka/core/KafkaTopic.java` |
| DLT 재시도 전례 | `apps/notification-service/src/main/java/com/booster/notificationservice/config/KafkaRetryConfig.java` |
| 관측 의존이 빠진 곳 | `apps/d-day/d-day-service/build.gradle` |
