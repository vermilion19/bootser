# Booster Observability Stack (LGTM)

Grafana LGTM 스택을 사용한 모니터링 환경입니다.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Spring Boot    │     │  Spring Boot    │     │  Spring Boot    │
│  Applications   │     │  Applications   │     │  Applications   │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │ Metrics               │ Logs                  │ Traces
         │ /actuator/prometheus  │ Loki Appender         │ Zipkin
         ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Prometheus    │     │      Loki       │     │     Tempo       │
│   (Metrics)     │     │     (Logs)      │     │   (Traces)      │
│    :9090        │     │     :3100       │     │  :3200/:9411    │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                        ┌─────────────────┐
                        │     Grafana     │
                        │   (Dashboard)   │
                        │     :3000       │
                        └─────────────────┘
```

## Components

| Component | Port | Description |
|-----------|------|-------------|
| **Grafana** | 3000 | 시각화 대시보드 (로그인 필요) |
| **Prometheus** | 9090 | 메트릭 수집 및 저장 |
| **Loki** | 3100 | 로그 수집 및 저장 |
| **Tempo** | 3200, 9411 | 분산 트레이싱 (Zipkin 호환) |

## Quick Start

```bash
# 1. 환경 변수 설정
cp .env.example .env
# .env 파일에서 GRAFANA_PASSWORD 수정

# 2. 실행
docker-compose up -d

# 3. 로그 확인
docker-compose logs -f

# 4. 중지
docker-compose down
```

## Grafana 로그인

- URL: http://localhost:3000
- Username: `.env` 파일의 `GRAFANA_USER` 값
- Password: `.env` 파일의 `GRAFANA_PASSWORD` 값

## Endpoints

- **Grafana Dashboard**: http://localhost:3000
- **Prometheus UI**: http://localhost:9090
- **Loki API**: http://localhost:3100

## Spring Boot Integration

### 1. 의존성 추가 (libs/core-observability)

```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'com.github.loki4j:loki-logback-appender:1.5.1'
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
```

### 2. application.yml 설정

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    prometheus:
      enabled: true

# Zipkin (Tempo로 전송)
spring:
  zipkin:
    base-url: http://localhost:9411
```

### 3. logback-spring.xml (Loki 전송)

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>application=${spring.application.name},level=%level</pattern>
        </label>
        <message>
            <pattern>traceId=${traceId:-} | %msg%n</pattern>
        </message>
    </format>
</appender>
```

## Grafana Datasources

Grafana에 자동으로 설정되는 데이터소스:

| Datasource | UID | 연동 |
|------------|-----|------|
| Prometheus | prometheus | 메트릭 → 트레이스 (trace_id) |
| Loki | loki | 로그 → 트레이스 (traceId 정규식) |
| Tempo | tempo | 트레이스 → 로그 (Loki 연결) |

## Configuration Files

| File | Description |
|------|-------------|
| `docker-compose.yml` | 서비스 정의 |
| `prometheus.yml` | 메트릭 수집 대상 설정 |
| `loki-config.yaml` | Loki 저장소 설정 |
| `tempo-config.yaml` | Tempo Zipkin receiver 설정 |
| `datasources.yaml` | Grafana 데이터소스 자동 설정 |

## Prometheus Targets

`prometheus.yml`에서 수집 대상을 설정합니다:

```yaml
scrape_configs:
  - job_name: 'booster-apps'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
        labels:
          application: 'waiting-service'
      - targets: ['host.docker.internal:8082']
        labels:
          application: 'restaurant-service'
```

> `host.docker.internal`은 Docker 컨테이너에서 호스트 머신에 접근하는 주소입니다.

## Useful Grafana Queries

### Prometheus (Metrics)

```promql
# HTTP 요청 수
http_server_requests_seconds_count{application="waiting-service"}

# 응답 시간 (95th percentile)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# JVM 메모리 사용량
jvm_memory_used_bytes{application="waiting-service"}
```

### Loki (Logs)

```logql
# 특정 서비스 로그
{application="waiting-service"}

# ERROR 레벨만
{application="waiting-service"} |= "ERROR"

# 특정 traceId로 필터
{application=~".+"} |~ "traceId=abc123"
```

### Tempo (Traces)

Grafana에서 Explore → Tempo 선택 후:
- Service Name으로 검색
- Trace ID로 직접 검색
- Duration 기반 필터링

## Troubleshooting

### Prometheus가 메트릭을 수집하지 못할 때

```bash
# 1. 앱이 실행 중인지 확인
curl http://localhost:8081/actuator/prometheus

# 2. prometheus.yml 타겟 확인
# host.docker.internal이 올바른지 확인

# 3. Prometheus UI에서 Targets 상태 확인
# http://localhost:9090/targets
```

### Loki에 로그가 안 보일 때

```bash
# 1. Loki 상태 확인
curl http://localhost:3100/ready

# 2. 앱의 logback 설정 확인
# loki-logback-appender가 설정되어 있는지

# 3. 네트워크 확인 (앱에서 Loki로 접근 가능한지)
```

### Tempo에 트레이스가 안 보일 때

```bash
# 1. 앱에서 Zipkin으로 전송 중인지 확인
# spring.zipkin.base-url 설정 확인

# 2. Tempo가 9411 포트에서 수신 중인지 확인
docker-compose logs tempo
```

## Docker Desktop에서 함께 사용

services 스택과 함께 사용할 때:

```bash
# 1. Observability 스택 먼저 실행
cd dockers/observability
docker-compose up -d

# 2. Services 스택 실행
cd ../services
docker-compose up -d
```