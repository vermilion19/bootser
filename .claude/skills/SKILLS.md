# Skills 가이드

프로젝트에서 사용 가능한 슬래시 커맨드 스킬 목록입니다.

---

## `/api`

**Controller의 REST API 문서를 마크다운으로 생성**

지정된 Controller 클래스를 분석하여 API 개요, Base URL, 엔드포인트 상세, Request/Response 예시, 에러 코드를 포함한 API 문서를 생성합니다.

```
/api WaitingController            # WaitingController API 문서 생성
/api src/main/.../controller/     # 폴더 내 모든 Controller 문서화
```

---

## `/commit`

**변경사항 분석 후 Conventional Commit 형식으로 커밋**

`git diff`를 분석하여 `feat`, `fix`, `refactor` 등 Conventional Commit 형식의 커밋 메시지를 자동 생성하고 커밋합니다.

```
/commit              # 스테이징된 변경사항 커밋
/commit all          # 모든 변경사항 add 후 커밋
```

---

## `/explain`

**코드의 동작 방식을 쉽게 설명**

지정된 코드의 목적(Why), 동작 흐름(How), 핵심 로직(What), 의존성, 다이어그램을 포함하여 설명합니다. `simple`/`deep` 키워드로 설명 수준을 조절할 수 있습니다.

```
/explain WaitingService           # WaitingService 설명
/explain registerWaiting simple   # 쉬운 설명
/explain OutboxPattern deep       # 상세 설명
```

---

## `/fix`

**에러 메시지를 분석하고 코드 수정**

에러 메시지나 스택트레이스를 분석하여 원인을 파악하고, 수정 코드를 작성합니다. 빌드 에러, 테스트 실패, 런타임 에러를 처리합니다.

```
/fix                              # 최근 빌드/테스트 에러 수정
/fix NullPointerException at...   # 특정 에러 메시지로 수정
/fix build                        # 빌드 에러 수정
/fix test                         # 테스트 실패 수정
```

---

## `/make-md`

**직전 응답 내용을 마크다운 파일로 저장**

가장 최근 응답 내용을 정리하여 `.md` 파일로 저장합니다. 대화체를 제거하고 문서 형식으로 변환합니다.

```
/make-md                     # 자동 파일명으로 저장
/make-md api-guide           # api-guide.md로 저장
/make-md docs/architecture   # docs/architecture.md로 저장
```

---

## `/review`

**코드 변경사항에 대한 코드 리뷰**

버그 가능성, 성능, 보안, 설계/가독성, 테스트 관점에서 코드를 리뷰합니다. Critical / Warning / Suggestion / Good으로 분류하여 결과를 출력합니다.

```
/review                           # 현재 git diff 리뷰
/review src/main/java/.../*.java  # 특정 파일 리뷰
/review 123                       # PR #123 리뷰
```

---

## `/test`

**지정된 클래스의 단위 테스트 코드 생성**

JUnit 5 + AssertJ + Mockito 기반으로 Given-When-Then 구조의 테스트 코드를 생성합니다. Happy Path, Edge Case, Exception, State 변경 테스트를 포함합니다.

```
/test WaitingService              # WaitingService 테스트 생성
/test src/main/.../Waiting.java   # 파일 경로로 지정
```

---

## `/spec-writer`

**요구사항을 개발 스펙 문서로 정리**

요구사항을 분석하여 기능/비기능 요구사항, 영향 범위, API 설계, 데이터 모델, 테스트 시나리오를 포함한 스펙 문서를 생성합니다.

```
/spec-writer 웨이팅 호출 시 SMS 알림 기능
/spec-writer 식당 영업시간 관리 기능 추가
```

---

## `/spec-review`

**스펙 문서를 리뷰하고 실행 가능한 형태로 정제**

작성된 스펙 문서를 실현 가능성, 범위 적정성, 명확성, 일관성 관점에서 리뷰하고, YAGNI/MVP 원칙에 따라 간소화합니다.

```
/spec-review docs/waiting-sms-spec.md    # 특정 스펙 파일 리뷰
/spec-review                              # 직전 스펙 리뷰
```

---

## `/perf-audit`

**성능 병목 지점 분석 및 최적화 제안**

대규모 트래픽 환경을 가정하여 JPA N+1 문제, 불필요한 객체 생성, 비효율적인 루프, 인덱스 미활용 쿼리, 캐시 미적용, 동기 외부 호출 등 성능 저하 요소를 점검하고 최적화 방안을 제시합니다.

```
/perf-audit WaitingService          # 서비스 로직 성능 점검
/perf-audit repository/             # 쿼리 및 DB 접근 로직 최적화
/perf-audit WaitingFacade deep      # 외부 호출 포함 심층 분석
```

---

## `/resilience`

**고가용성 및 장애 복구 전략 점검**

서비스 안정성 패턴을 점검합니다. Circuit Breaker 적용 여부, Retry 전략, Bulkhead/Rate Limiter 설정, 예외 처리 핸들러 적절성, Kafka DLQ 처리 로직, 타임아웃 설정 등을 분석합니다.

```
/resilience WaitingFacade            # Facade의 외부 호출 장애 대응 점검
/resilience event/                   # Kafka Consumer/Producer 안정성 점검
/resilience infrastructure/          # Feign Client 장애 전파 방지 점검
```

---

## `/query-plan`

**DB 쿼리 실행 계획 분석 및 인덱스 설계**

SQL이나 JPA 메서드를 분석하여 예상 실행 계획을 추론하고, 인덱스 설계 및 쿼리 튜닝 방안을 제시합니다. PostgreSQL 환경 기준으로 Seq Scan 유발 패턴, 복합 인덱스 설계, 페이징 최적화 등을 가이드합니다.

```
/query-plan findByUserIdAndStatus   # 특정 메서드의 쿼리 효율 분석
/query-plan schema.sql              # 스키마 설계를 통한 인덱스 전략 제안
/query-plan WaitingRepository       # Repository 전체 메서드 분석
```

---

## `/event-flow`

**분산 시스템 간의 이벤트 흐름 문서화**

Kafka 등 메시징 시스템의 이벤트 흐름을 추적합니다. Producer 발행 토픽, Consumer 처리 로직, Outbox Pattern 적용 여부, 데이터 정합성, DLQ 처리 등을 ASCII 다이어그램과 함께 정리합니다.

```
/event-flow WaitingService          # 웨이팅 서비스 이벤트 흐름 추적
/event-flow CouponIssueService      # 쿠폰 발행 이벤트의 시작부터 끝까지 추적
/event-flow topics/order-events     # 특정 토픽 관련 발행/구독 관계 정리
```

---

## `/ui-gen`

**설명을 바탕으로 현대적인 컴포넌트 코드 생성**

Shadcn UI + Tailwind CSS 기반으로 DataTable, Modal, Form, Dashboard, Chart 등의 컴포넌트를 생성합니다. 백엔드 Response DTO에 맞는 타입 정의, 로딩/에러/빈 데이터 상태, 다크모드, 반응형을 기본 지원합니다.

```
/ui-gen 사용자 목록을 보여주는 정렬 가능한 데이터 테이블
/ui-gen 실시간 트래픽을 보여주는 대시보드 차트 컴포넌트
/ui-gen 웨이팅 등록 폼 (인원수, 연락처 입력)
```

---

## `/responsive`

**반응형 레이아웃 및 브레이크포인트 검토**

다양한 화면 크기(모바일, 태블릿, 데스크톱)에서 레이아웃이 깨지지 않는지 검토합니다. Tailwind 반응형 접두사 배치, Mobile-first 구조, 네비게이션 전환, 테이블/그리드 반응형 처리, 터치 영역 등을 점검합니다.

```
/responsive NavigationBar.tsx               # 모바일 햄버거 메뉴 전환 로직 확인
/responsive LandingPage                     # 브레이크포인트별 레이아웃 적절성 검토
/responsive components/dashboard/           # 대시보드 컴포넌트 전체 검토
```
