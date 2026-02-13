# Damagochi Service 아키텍처/코드 가이드

이 문서는 `apps/kotlin/damagochi-service`를 처음 보는 개발자가 빠르게 구조를 파악하고, 기능 변경 시 어디를 수정해야 하는지 판단할 수 있도록 정리한 안내서입니다.

## 1. 프로젝트 개요
- 서비스 성격: 다마고치(Tamagotchi) 스타일 백엔드 API
- 핵심 기능:
  - 사용자 가입/로그인
  - 크리처 생성/활성화/상태 액션(먹이, 수면, 치료)
  - 배틀 매칭(랜덤 큐, 방 생성/참가)
  - 활동 로그 기록
- 런타임 기본 구성:
  - Spring Boot + Kotlin + JPA
  - 기본 DB: H2(in-memory)
  - 선택 매칭 저장소: InMemory 또는 Redis (`damagochi.battle.registry-type`)

## 2. 기술 스택 및 빌드
- Language: Kotlin (JVM 21)
- Framework: Spring Boot (web, validation, data-jpa, data-redis)
- Security 유틸: `spring-security-crypto` (비밀번호 해시)
- Build: Gradle (`apps/kotlin/damagochi-service/build.gradle.kts`)

주요 의존성:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-validation`
- `com.h2database:h2`, `org.postgresql:postgresql`

## 3. 패키지 구조 (DDD 컨텍스트 중심)
루트 패키지: `com.booster.kotlin.damagochiservice`

- `auth`
  - `web`: 인증 API 컨트롤러
  - `application`: 인증 유스케이스 서비스
  - `domain`: 사용자 엔티티/리포지토리
- `creature`
  - `web`: 크리처 API 컨트롤러
  - `application`: 크리처 유스케이스/상태 계산
  - `domain`: 크리처/상태/이상상태 엔티티 및 리포지토리
- `battle`
  - `web`: 배틀 API 컨트롤러
  - `application`: 배틀 유스케이스/결과 계산
  - `application.match`: 매칭 레지스트리 추상화 및 구현(memory/redis)
  - `domain`: 배틀 엔티티/리포지토리
- `activity`
  - `domain`: 활동 로그 엔티티/리포지토리/타입
- `common`
  - `config`: 공통 설정 Bean/정책 프로퍼티
  - `web`: 공통 웹 인프라(예외 처리, 사용자 ID 리졸버, 헬스체크)

## 4. 전체 아키텍처 관점
요청 흐름은 전형적인 `Controller -> Application Service -> Domain/Repository` 구조입니다.

```text
HTTP Request
  -> Web Controller (DTO 검증/매핑)
    -> Application Service (유스케이스 트랜잭션)
      -> Domain Entity/Policy 계산
      -> Repository(JPA/Redis) 저장/조회
    <- View DTO 응답
  <- HTTP Response
```

설정/정책(`GameBalanceProperties`)은 Application Service/Engine에서 주입받아 사용합니다.

## 5. 컨텍스트별 핵심 클래스와 역할

### 5.1 Auth 컨텍스트
- `auth.web.AuthController`
  - `/api/auth/signup`, `/api/auth/login` 엔드포인트 제공
  - Request DTO 검증 후 Command로 변환
- `auth.application.AuthApplicationService`
  - `signUp`: loginId 중복 검사, 비밀번호 해시 후 저장
  - `login`: loginId 조회 + 비밀번호 매칭
- `auth.domain.AppUser`
  - 사용자 엔티티(loginId, passwordHash, nickname)
- `auth.domain.AppUserRepository`
  - `findByLoginId`, `existsByLoginId`

### 5.2 Creature 컨텍스트
- `creature.web.CreatureController`
  - `/api/creatures` 및 하위 액션 API
  - 사용자 식별은 `@CurrentUserId`로 주입받음
- `creature.application.CreatureApplicationService`
  - 핵심 유스케이스:
    - `findCreatures`
    - `createCreature`
    - `activateCreature`
    - `feed`
    - `updateSleep`
    - `treat`
  - 각 유스케이스에서 `ActionLog` 기록
  - 상태 갱신은 `CreatureStateCalculator` 사용
- `creature.application.CreatureStateCalculator`
  - 시간 경과에 따른 상태 계산 규칙:
    - 나이 증가
    - 배고픔/체력 감소
    - 야간 컨디션 페널티
    - 수면 상태일 때 별도 규칙
- `creature.domain.Creature`
  - 크리처 기본 엔티티(이름, 종, 단계, 활성 여부)
- `creature.domain.CreatureState`
  - 크리처 상태 엔티티(health/hunger/condition/winRate/sleeping 등)
- `creature.domain.StatusEffect`
  - 질병/부상/피로 등의 상태이상 + 단계적 완화 로직
- `creature.domain.*Repository`
  - `CreatureRepository`, `CreatureStateRepository`, `StatusEffectRepository`

### 5.3 Battle 컨텍스트
- `battle.web.BattleController`
  - 랜덤 큐, 방 생성/조회/취소/참가, 배틀 상세 조회 API
- `battle.application.BattleApplicationService`
  - 매칭 처리 + 배틀 확정 + 참가자 저장 + 승률 반영 + 로그 기록
  - 방 코드 발급/중복 회피, 방 TTL 처리
- `battle.application.BattleEngine`
  - 배틀 점수 계산 엔진(상태값 + 랜덤 시드 + 정책 가중치)
- `battle.application.match.BattleMatchRegistry`
  - 매칭 저장소 인터페이스
  - 구현체:
    - `InMemoryBattleMatchRegistry` (기본)
    - `RedisBattleMatchRegistry` (설정 시 활성화)
- `battle.domain.Battle`, `BattleParticipant`
  - 배틀 메타데이터 및 참가자 스냅샷 저장

### 5.4 Activity 컨텍스트
- `activity.domain.ActionLog`
  - 주요 액션 감사성 로그 저장(사용자, 크리처, 액션 타입, payload)
- `activity.domain.ActionType`
  - 액션 종류 enum (`CREATE_CREATURE`, `BATTLE_RESULT` 등)
- `activity.domain.ActionLogRepository`
  - 최근 로그 조회 메서드 제공

### 5.5 Common 컨텍스트
- `common.config.GameBalanceProperties`
  - 정책 값을 yml에서 바인딩
  - 하위 정책: `CreaturePolicy`, `ActionPolicy`, `StatePolicy`, `BattlePolicy`
- `common.config.ClockConfig`
  - `Clock.systemUTC()` Bean
- `common.config.PasswordConfig`
  - `BCryptPasswordEncoder` Bean
- `common.web.CurrentUserIdResolver`
  - `X-User-Id` 헤더를 `@CurrentUserId Long`으로 변환
- `common.web.GlobalExceptionHandler`
  - 유효성/헤더 누락/쿨다운 등 예외를 표준 에러 응답으로 변환
- `common.web.HealthController`
  - `/api/health`

## 6. 핵심 데이터 모델 관계

```text
AppUser (1) --- (N) Creature
Creature (1) --- (1) CreatureState
Creature (1) --- (N) StatusEffect
Battle (1) --- (N) BattleParticipant
ActionLog: userId, creatureId 기준으로 행위 추적
```

설명:
- 사용자 1명은 여러 크리처를 소유
- 크리처마다 상태(`CreatureState`) 1개를 가짐
- 상태이상(`StatusEffect`)은 크리처에 다수 연결 가능
- 배틀 1건에는 참가자 2명(현재 로직 기준) 정보가 `BattleParticipant`로 저장

## 7. 요청 흐름 예시

### 7.1 크리처 생성
1. `POST /api/creatures` 호출 (`X-User-Id` 필요)
2. `CreatureController`가 `CreateCreatureCommand` 생성
3. `CreatureApplicationService.createCreature` 실행
4. 슬롯 제한 검사 -> `Creature` 저장 -> `CreatureState.initial` 저장
5. `ActionLog(CREATE_CREATURE)` 저장
6. `CreatureSummary` 응답

### 7.2 랜덤 배틀
1. `POST /api/battles/random/queue`
2. `BattleApplicationService.queueRandom`
3. `BattleMatchRegistry.enqueueRandom` 결과가
   - `Waiting`이면 대기 상태 반환
   - `Matched`이면 즉시 배틀 확정
4. 배틀 확정 시:
   - 양쪽 `CreatureState`를 현재 시각 기준으로 보정
   - `BattleEngine.duel`로 승패 계산
   - `Battle`, `BattleParticipant` 저장
   - `winRate` 반영
   - `ActionLog(BATTLE_RESULT)` 저장
5. 배틀 ID 포함 응답

### 7.3 방 기반 배틀
1. 방 생성: `POST /api/battles/rooms`
2. 상대 참가: `POST /api/battles/rooms/{code}/join`
3. 내부적으로 방 소유자/참가자 큐 상태 정리 후 배틀 확정 로직은 랜덤과 동일

## 8. 설정 포인트
기본 설정 파일: `apps/kotlin/damagochi-service/src/main/resources/application.yml`

- `spring.datasource.*`: 기본 H2 메모리 DB
- `spring.jpa.hibernate.ddl-auto=create-drop`: 실행 중 스키마 생성/종료 시 삭제
- `damagochi.battle.registry-type`
  - `memory` (기본)
  - `redis` (Redis 레지스트리 사용)
- `damagochi.balance.*`
  - 크리처 제한, 쿨다운, 상태 감소량, 배틀 가중치/보정치 등 정책값

## 9. API 엔드포인트 요약

### Auth
- `POST /api/auth/signup`
- `POST /api/auth/login`

### Creature
- `GET /api/creatures`
- `POST /api/creatures`
- `POST /api/creatures/{id}/activate`
- `POST /api/creatures/{id}/actions/feed`
- `POST /api/creatures/{id}/actions/sleep`
- `POST /api/creatures/{id}/actions/treat`

### Battle
- `POST /api/battles/random/queue`
- `POST /api/battles/rooms`
- `GET /api/battles/rooms/mine`
- `GET /api/battles/rooms/{code}`
- `DELETE /api/battles/rooms/{code}`
- `POST /api/battles/rooms/{code}/join`
- `GET /api/battles/{id}`

### Health
- `GET /api/health`

## 10. 테스트 구성
테스트 위치: `apps/kotlin/damagochi-service/src/test/kotlin/com/booster/kotlin/damagochiservice`

- `auth/application`: 인증 서비스 테스트
- `creature/application`: 상태 계산/크리처 유스케이스 테스트
- `battle/application`: 배틀 엔진/유스케이스/TTL 테스트
- `common/web`: API 검증/헤더 검증 테스트

실행:
```bash
./gradlew :apps:kotlin:damagochi-service:test
```

(Windows PowerShell)
```powershell
.\gradlew :apps:kotlin:damagochi-service:test
```

## 11. 변경 시 참고 가이드
- 새 비즈니스 규칙 추가:
  - 우선 `common.config.GameBalanceProperties`에 정책값 외부화 고려
  - 유스케이스는 각 컨텍스트의 `application` 서비스에 추가
- 새 API 추가:
  - `web`에 request/response DTO + 컨트롤러 메서드
  - 서비스 메서드 호출만 담당하고 도메인 로직은 application/domain으로 유지
- 저장소 교체/확장:
  - 배틀 매칭은 `BattleMatchRegistry` 인터페이스를 기준으로 구현체 확장

## 12. 현재 구조의 특징 한 줄 요약
- 컨텍스트 분리는 잘 되어 있으며, 각 기능이 `web -> application -> domain` 흐름으로 정리된 상태
- 정책값을 설정으로 분리해 밸런스 튜닝이 쉬움
- 배틀 매칭 저장소를 인터페이스로 추상화해 InMemory/Redis 전환이 가능