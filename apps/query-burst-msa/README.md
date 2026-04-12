# query-burst-msa

`query-burst` 모놀리스를 유지한 채, 서비스 경계를 실제 코드 구조로 분리하기 위한 신규 멀티모듈 앱이다.

## 모듈

- `contracts`: 서비스 간 공용 이벤트/내부 API 계약
- `analytics-service`: `order-events` 기반 집계 read model
- `ranking-service`: `order-events` 기반 실시간 랭킹 read model
- `catalog-service`: 상품/카테고리/재고 예약 API
- `member-service`: 회원 마스터 API
- `order-service`: 주문 쓰기 오케스트레이션 골격

## 원칙

- 기존 `apps/query-burst`는 유지한다
- 서비스 간 DB 공유를 전제로 하지 않는다
- 먼저 계약과 API 경계를 코드로 고정한다
- 외부 인프라 없이도 최소 실행 가능한 기본 구현을 둔다

## 다음 단계

1. `analytics-service`와 `ranking-service`에 실제 Kafka/Redis/DB read model 이관
2. `catalog-service`에 영속 저장소와 재고 예약 멱등성 적용
3. `order-service`에 catalog 연동과 outbox 발행 이관
4. 게이트웨이 라우팅 전환 후 기존 모놀리스 API 축소

## Docker Compose 실행

1. `booster-postgres`, `booster-redis`, `booster-kafka`가 먼저 실행 중이어야 한다
2. `apps/query-burst-msa/.env.example`을 복사해 `apps/query-burst-msa/.env`를 만든다
3. 계정 정보와 접속 정보를 `.env`에 맞게 수정한다
4. 아래 명령으로 실행한다

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml up --build
```

중지는 아래 명령을 사용한다.

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml down
```

상세 실행 방법은 [RUNBOOK.md](/Users/juhongkim/Desktop/Dev/projects/booster/apps/query-burst-msa/RUNBOOK.md:1)를 참고한다.
