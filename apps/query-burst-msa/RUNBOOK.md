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

## Scale-out

각 서비스를 독립적으로 수평 확장할 수 있습니다.  
외부 요청은 Nginx 로드밸런서를 통해 분배되며, 서비스 간 통신(예: order → catalog)은 Docker 내장 DNS round-robin으로 처리됩니다.

### 특정 서비스 스케일 아웃

```bash
# catalog-service를 3대로 확장
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml up --scale catalog-service=3 -d

# 여러 서비스 동시에 확장
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml \
  up --scale catalog-service=3 --scale order-service=2 -d
```

### 스케일 인 (축소)

```bash
# catalog-service를 다시 1대로
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml up --scale catalog-service=1 -d
```

### 현재 인스턴스 수 확인

```bash
docker compose --env-file apps/query-burst-msa/.env \
  -f apps/query-burst-msa/docker-compose.yml ps
```

### 주의사항

- `--scale` 적용 시 `--build` 플래그는 생략해도 됩니다 (이미 빌드된 이미지 재사용).
- Nginx는 `resolver valid=5s` 설정으로 스케일 변경 후 최대 5초 이내에 새 컨테이너를 자동 감지합니다.
- `analytics-service`와 `ranking-service`는 Kafka Consumer이므로, 스케일 시 `consumer group`이 파티션을 재분배(rebalance)합니다.

## 참고

- [docker-compose.yml](./docker-compose.yml)
- [nginx.conf](./docker/nginx.conf)
- [.env.example](./.env.example)
