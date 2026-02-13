# Damagochi Service 리뷰 (2026-02-13)

## 범위
- `apps/kotlin/damagochi-service/src/main/kotlin` 전체 검토
- `apps/kotlin/damagochi-service/src/test/kotlin` 테스트 코드 검토
- 초점: 정확성, DDD 적합성, 변경 용이성, 운영 리스크

## 주요 발견사항 (심각도 순)

### 1. 치명적(Critical) - 사용자 식별을 클라이언트가 위조 가능 (`X-User-Id`)
- 근거: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/common/web/CurrentUserIdResolver.kt:25`
- 현재는 요청 헤더 `X-User-Id` 값을 그대로 신뢰해서 인증 사용자로 사용합니다.
- 영향: 어떤 클라이언트든 임의 사용자로 가장하여 타인의 크리처/배틀 데이터에 접근 및 변경할 수 있습니다.
- 권장:
  1. JWT/세션 등 실제 인증 컨텍스트를 도입하고, 검증된 principal에서 userId를 추출하세요.
  2. `X-User-Id` 방식은 로컬 개발 프로파일에서만 허용하세요.

### 2. 높음(High) - 회원가입 중복 체크에 경쟁 조건이 있어 동시 요청 시 500 가능
- 근거: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/auth/application/AuthApplicationService.kt:17`
- `existsByLoginId` 확인 후 `save` 하는 구조가 원자적이지 않습니다. 동시 가입 시 둘 다 체크를 통과하고, 하나는 DB unique 제약에서 실패할 수 있습니다.
- 영향: 비결정적 동작, 500 응답 가능성.
- 권장:
  1. DB unique 위반을 비즈니스 예외로 처리해 409(Conflict)로 매핑하세요.
  2. 사전 중복 체크는 UX 보조로만 두고, 최종 무결성은 DB 제약을 기준으로 하세요.

### 3. 높음(High) - 크리처 불변식(최대 슬롯/활성 1개)이 동시성에 안전하지 않음
- 근거:
  - 슬롯 수 체크: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:37`
  - 활성 전환 로직: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:90`
  - 엔티티에 사용자별 활성 1개 DB 강제 장치 부재: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/domain/Creature.kt:19`
- 영향: 동시 요청 시 슬롯 초과 생성, 활성 크리처 2개 이상 같은 규칙 위반 가능.
- 권장:
  1. 트랜잭션 잠금 전략(`PESSIMISTIC_WRITE` 또는 사용자 단위 락) 도입.
  2. 가능한 범위에서 DB 제약 추가(또는 활성 관계를 별도 테이블로 분리해 강제).

### 4. 중간(Medium) - 읽기 전용 트랜잭션 경로에서 실제 쓰기 발생
- 근거:
  - 클래스 readOnly: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:19`
  - `findCreatures`에서 상태 보정 후 저장: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:28`
  - 실제 save 호출: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:200`
- 영향: JPA flush/provider 설정에 따라 동작이 달라질 수 있고, 추후 튜닝 시 잠재 버그가 발생할 수 있습니다.
- 권장:
  1. 쓰기가 포함된 경로는 명시적으로 `@Transactional(readOnly = false)` 처리.
  2. 순수 조회와 상태 보정(쓰기)을 분리.

### 5. 중간(Medium) - Redis 랜덤 매칭 큐에 stale 엔트리 누적 가능
- 근거: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/battle/application/match/RedisBattleMatchRegistry.kt:45`
- `removeFromRandomQueue`가 해시(`KEY_RANDOM_ENTRIES`)만 삭제하고 리스트(`KEY_RANDOM_ORDER`)에서는 제거하지 않습니다.
- 영향: 오래된 userId가 리스트에 쌓여 성능 저하(불필요 pop 반복, 메모리 증가) 위험.
- 권장:
  1. 제거 시 리스트에서도 정리(`LREM`)하거나, 단일 구조(sorted set 등)로 재설계.
  2. 현재 구조 유지 시 정기 정리(compaction) 전략 추가.

### 6. 낮음(Low) - JSON payload를 문자열 보간으로 직접 생성
- 근거:
  - `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureApplicationService.kt:59`
  - `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/battle/application/BattleApplicationService.kt:234`
- 영향: 사용자 입력에 따라 JSON 형식이 깨지거나 로그 일관성이 떨어질 수 있습니다.
- 권장: `ObjectMapper` + 전용 DTO로 직렬화.

### 7. 낮음(Low) - 시간 모델 일관성 부족 (UTC Clock + LocalDateTime + KST 하드코딩)
- 근거:
  - UTC clock: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/common/config/ClockConfig.kt:9`
  - KST 고정 야간 계산: `apps/kotlin/damagochi-service/src/main/kotlin/com/booster/kotlin/damagochiservice/creature/application/CreatureStateCalculator.kt:9`
- 영향: 인프라 타임존/정책 변경 시 해석 불일치 가능.
- 권장: 내부 시간은 `Instant`/`OffsetDateTime` 기준으로 통일하고, 정책 타임존은 외부 설정화.

## DDD/구조 평가
- 장점:
  - `auth`, `creature`, `battle`, `activity`, `common` 컨텍스트 분리가 명확해졌습니다.
  - 도메인 엔티티/리포지토리와 웹 어댑터 분리가 되어 있습니다.
- 보완점:
  - 일부 Application Service가 너무 커서(오케스트레이션 + DTO 다수) 변경 영향 범위가 큽니다.
  - 핵심 불변식이 애플리케이션 레벨에 치우쳐 있고 DB 강제 장치가 약합니다.

## 테스트 커버리지 공백
- 동시성 테스트 부재:
  - 회원가입 중복 race
  - 크리처 활성 전환 race
  - 크리처 슬롯 초과 race
- Redis 레지스트리 모드(`damagochi.battle.registry-type=redis`) 통합 테스트 부재.
- 보안 회귀 테스트 부재(운영에서 header 위조 불가 보장 확인 필요).

## 우선순위 실행 계획
1. 인증 경로 보강(`X-User-Id` 신뢰 제거, 운영 인증 컨텍스트 적용)
2. 회원가입/크리처 불변식의 동시성 안전성 확보
3. Redis 큐 stale 리스트 문제 개선(실서비스 Redis 전환 전)
4. 트랜잭션 경계 정리(읽기/쓰기 경로 분리)
5. 로그 payload 생성 방식 DTO 직렬화로 전환
