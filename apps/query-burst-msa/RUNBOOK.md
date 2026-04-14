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
docker compose --env-file .env -f docker-compose.yml up --build -d      

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

## 부하 테스트 (k6)

### 사전 조건

1. query-burst-msa 서비스가 모두 실행 중이어야 한다
2. 각 서비스의 데이터 초기화 API를 먼저 호출해야 한다 (상품 재고 필요)
3. Observability 스택이 실행 중이어야 Grafana에서 결과를 확인할 수 있다

```bash
# 데이터 초기화 (서비스 기동 후 1회 실행)
curl -X POST http://localhost:18113/init  # catalog
curl -X POST http://localhost:18114/init  # member
curl -X POST http://localhost:18115/init  # order
curl -X POST http://localhost:18111/init  # analytics
curl -X POST http://localhost:18112/init  # ranking
```

### 시나리오별 실행

```bash
# 카탈로그 읽기 부하 (scale-out 효과 검증)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/catalog-read.js

# 주문 쓰기 부하 (Outbox + Kafka 처리량 측정)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/order-write.js

# 랭킹 읽기 부하 (Redis 성능 측정)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/ranking-read.js

# 복합 시나리오 (browse 70% + order 30%)
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run /scripts/complex-flow.js
```

### Grafana 실시간 모니터링과 함께 실행

Prometheus remote write를 활성화하여 테스트 중 Grafana에서 k6 메트릭을 실시간으로 확인할 수 있다.

```bash
docker compose -f apps/query-burst-msa/load-test/docker-compose.yml \
  run --rm k6 run --out experimental-prometheus-rw /scripts/complex-flow.js
```

Grafana 대시보드 → **Dashboards → Import → ID `18030`** 입력 후 Prometheus 데이터소스 선택.

### 주요 관찰 지표

| 지표 | 위치 | 의미 |
|------|------|------|
| `http_req_duration` p95 | k6 대시보드 | 서비스별 응답시간 |
| `http_req_failed` rate | k6 대시보드 | 에러율 |
| `http_server_requests_seconds` | Spring 대시보드 | 인스턴스별 RPS |
| Kafka consumer lag | Kafka UI | Outbox 처리 지연 |

## 참고

- [docker-compose.yml](./docker-compose.yml)
- [nginx.conf](./docker/nginx.conf)
- [load-test/](./load-test/)
- [.env.example](./.env.example)
