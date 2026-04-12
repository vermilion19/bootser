# Query Burst MSA Runbook

## 전제 조건

`query-burst-msa` 앱은 기존 인프라 컨테이너를 사용합니다.

- `booster-postgres`
- `booster-redis`
- `booster-kafka`

확인:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | rg 'booster-(postgres|redis|kafka)'
```

## 환경 파일

```bash
cp apps/query-burst-msa/.env.example apps/query-burst-msa/.env
```

기본값은 기존 인프라 기준으로 맞춰져 있습니다.

## 실행

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml up --build -d
```

## 중지

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml down
```

## 로그

전체 로그:

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml logs -f
```

특정 서비스 로그:

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml logs -f order-service
```

## 포트

- `analytics-service`: `18111`
- `ranking-service`: `18112`
- `catalog-service`: `18113`
- `member-service`: `18114`
- `order-service`: `18115`

## 참고

- [docker-compose.yml](/Users/juhongkim/Desktop/Dev/projects/booster/apps/query-burst-msa/docker-compose.yml)
- [.env.example](/Users/juhongkim/Desktop/Dev/projects/booster/apps/query-burst-msa/.env.example)
