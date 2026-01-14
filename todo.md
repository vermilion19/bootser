# Booster 프로젝트 - 포트폴리오 강화 TODO

> 현재 포트폴리오 점수: **6.5/10**
---

## 현재 프로젝트 강점

- ✅ DDD 기반 설계
- ✅ 분산 락 (Redisson)
- ✅ Outbox 패턴
- ✅ Kafka 이벤트 기반 아키텍처
- ✅ Redis 캐시 및 실시간 순위
- ✅ Resilience4j (CircuitBreaker, Bulkhead)
- ✅ 커서 기반 페이지네이션
- ✅ Snowflake ID 생성
- ✅ 동시성 테스트 (100 스레드)

---

## 우선순위 가이드

| 등급 | 의미 | 면접 영향도 |
|------|------|-------------|
| 🔴 CRITICAL | 반드시 추가 | 없으면 탈락 가능 |
| 🟠 HIGH | 강력 권장 | 경쟁력 크게 상승 |
| 🟡 MEDIUM | 권장 | 기술 깊이 어필 |
| 🟢 LOW | 선택 | 차별화 요소 |

---

## 🔴 CRITICAL - 반드시 해야 함 (예상 20시간)

### 1. README.md 작성 (1시간)
> 면접관/리쿠르터가 가장 먼저 보는 파일

**해야 할 것:**
```markdown
# Booster - 대용량 트래픽 웨이팅 시스템

## 🎯 프로젝트 소개
- 한 줄 설명
- 주요 기능 (3~5개)

## 🏗️ 아키텍처
- 시스템 다이어그램 (ASCII 또는 이미지)
- 기술 스택 표

## 🚀 Quick Start
- 사전 요구사항
- 로컬 실행 방법
- API 테스트 방법

## 📊 기술적 특징
- 분산 락, Outbox 패턴 등 핵심 기술 요약
- 성능 테스트 결과 (TPS, 응답시간)

## 📁 프로젝트 구조
- 모듈별 역할 설명

## 📚 문서
- 상세 문서 링크
```

**위치:** `README.md` (루트)

---

### 2. GitHub Actions CI/CD 파이프라인 (3시간)
> 현대 개발에서 필수. 없으면 "실무 경험 부족"으로 보임

**생성할 파일:**
```
.github/
└── workflows/
    ├── ci.yml           # PR/Push 시 빌드 & 테스트
    ├── cd.yml           # main 브랜치 배포
    └── codeql.yml       # 보안 스캔
```

**ci.yml 예시:**
```yaml
name: CI Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build with Gradle
        run: ./gradlew build

      - name: Run tests
        run: ./gradlew test

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: '**/build/test-results/test/*.xml'
```

---

### 3. Docker 컨테이너화 (4시간)
> K8s 배포의 전제 조건. 없으면 "클라우드 네이티브 경험 없음"

**생성할 파일:**
```
docker/
├── waiting-service/Dockerfile
├── restaurant-service/Dockerfile
├── notification-service/Dockerfile
├── auth-service/Dockerfile
├── gateway-service/Dockerfile
├── discovery-service/Dockerfile
└── docker-compose.yml   # 전체 스택 실행
```

**Dockerfile 템플릿:**
```dockerfile
# Multi-stage build
FROM eclipse-temurin:25 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :apps:waiting-service:bootJar -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/apps/waiting-service/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**docker-compose.yml:**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: booster
      POSTGRES_USER: booster
      POSTGRES_PASSWORD: booster
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: bitnami/kafka:3.6
    environment:
      KAFKA_CFG_NODE_ID: 0
      KAFKA_CFG_PROCESS_ROLES: broker,controller
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
    ports:
      - "9092:9092"

  discovery-service:
    build:
      context: .
      dockerfile: docker/discovery-service/Dockerfile
    ports:
      - "8761:8761"

  waiting-service:
    build:
      context: .
      dockerfile: docker/waiting-service/Dockerfile
    ports:
      - "8081:8080"
    depends_on:
      - postgres
      - redis
      - kafka
      - discovery-service

volumes:
  postgres_data:
```

---

### 4. Kubernetes 배포 매니페스트 (5시간)
> 빅테크에서 K8s는 기본. Scale-out 주장의 증거

**생성할 파일:**
```
k8s/
├── base/
│   ├── namespace.yaml
│   ├── waiting-service/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── configmap.yaml
│   │   └── hpa.yaml           # Auto Scaling
│   ├── restaurant-service/
│   ├── notification-service/
│   └── infrastructure/
│       ├── postgres/
│       ├── redis/
│       └── kafka/
└── overlays/
    ├── dev/
    ├── staging/
    └── prod/
```

**deployment.yaml 예시:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: waiting-service
  labels:
    app: waiting-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: waiting-service
  template:
    metadata:
      labels:
        app: waiting-service
    spec:
      containers:
        - name: waiting-service
          image: booster/waiting-service:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "200m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
```

**hpa.yaml (Auto Scaling):**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: waiting-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: waiting-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

### 5. 부하 테스트 & 결과 문서화 (5시간)
> "대용량 트래픽" 주장의 객관적 증거

**생성할 파일:**
```
tests/
├── load/
│   ├── k6/
│   │   ├── waiting-register.js
│   │   ├── waiting-list.js
│   │   └── concurrent-register.js
│   └── results/
│       ├── report-1000tps.md
│       └── graphs/
```

**k6 테스트 스크립트:**
```javascript
// waiting-register.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },   // Ramp-up
    { duration: '1m', target: 1000 },   // Peak load
    { duration: '30s', target: 0 },     // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95% 응답시간 500ms 미만
    http_req_failed: ['rate<0.01'],     // 에러율 1% 미만
  },
};

export default function () {
  const payload = JSON.stringify({
    restaurantId: Math.floor(Math.random() * 100) + 1,
    guestPhone: `010-${Math.floor(Math.random() * 10000).toString().padStart(4, '0')}-${Math.floor(Math.random() * 10000).toString().padStart(4, '0')}`,
    partySize: Math.floor(Math.random() * 4) + 1,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post('http://localhost:8080/api/v1/waitings', payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(0.1);
}
```

**결과 문서 (report-1000tps.md):**
```markdown
# 부하 테스트 결과

## 테스트 환경
- 인스턴스: 3개 (2 vCPU, 4GB RAM)
- DB: PostgreSQL (4 vCPU, 8GB RAM)
- Redis: Single node (2GB RAM)
- Kafka: 3 brokers

## 결과 요약
| 지표 | 결과 |
|------|------|
| 최대 TPS | 1,247 |
| 평균 응답시간 | 45ms |
| P95 응답시간 | 120ms |
| P99 응답시간 | 350ms |
| 에러율 | 0.02% |

## 병목 분석
1. DB 커넥션 풀 (10 → 20으로 증가 시 15% 개선)
2. Redis 분산 락 대기 시간
```

---

## 🟠 HIGH - 경쟁력 크게 상승 (예상 15시간)

### 6. 테스트 커버리지 리포팅 (JaCoCo) (1시간)

**build.gradle 수정:**
```gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% 커버리지 필수
            }
        }
    }
}

tasks.named('check') {
    dependsOn jacocoTestCoverageVerification
}
```

---

### 7. API 문서화 (Swagger/OpenAPI) (2시간)

**의존성 추가:**
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

**설정 클래스:**
```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Booster Waiting System API")
                .version("1.0.0")
                .description("대용량 트래픽 대응 MSA 웨이팅 시스템")
                .contact(new Contact()
                    .name("Your Name")
                    .email("your@email.com")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local"),
                new Server().url("https://api.booster.com").description("Production")
            ));
    }
}
```

**Controller 어노테이션:**
```java
@Tag(name = "Waiting", description = "웨이팅 관리 API")
@RestController
@RequestMapping("/api/v1/waitings")
public class WaitingController {

    @Operation(
        summary = "웨이팅 등록",
        description = "새로운 웨이팅을 등록합니다. 분산 락으로 동시성 제어됩니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "등록 성공"),
        @ApiResponse(responseCode = "409", description = "중복 등록"),
        @ApiResponse(responseCode = "503", description = "서버 과부하")
    })
    @PostMapping
    public ApiResponse<RegisterWaitingResponse> register(
        @RequestBody @Valid RegisterWaitingRequest request) { ... }
}
```

---

### 8. ADR (Architecture Decision Records) (3시간)

**생성할 파일:**
```
docs/adr/
├── 000-template.md
├── 001-outbox-pattern-for-event-delivery.md
├── 002-redis-distributed-lock.md
├── 003-kafka-over-rabbitmq.md
├── 004-cursor-pagination.md
├── 005-snowflake-id-generation.md
├── 006-resilience4j-patterns.md
└── 007-ddd-package-structure.md
```

**ADR 템플릿:**
```markdown
# ADR-001: Outbox 패턴을 통한 이벤트 전달 보장

## 상태
Accepted

## 컨텍스트
- MSA 환경에서 DB 트랜잭션과 Kafka 이벤트 발행의 원자성이 보장되지 않음
- 서비스 크래시 시 이벤트 유실 가능

## 결정
Outbox 패턴 + Polling Publisher 방식 채택

## 근거
1. **단순성**: CDC(Debezium) 대비 추가 인프라 불필요
2. **신뢰성**: DB 트랜잭션으로 이벤트 저장 보장
3. **복구 가능**: 미발행 이벤트는 재시작 시 자동 재발행

## 대안 검토
| 방식 | 장점 | 단점 |
|------|------|------|
| 직접 발행 | 단순 | 이벤트 유실 가능 |
| **Outbox + Polling** | 신뢰성 | 지연 발생 (3초) |
| CDC (Debezium) | 실시간 | 인프라 복잡도 |

## 결과
- At-Least-Once 전달 보장
- Consumer 멱등성 구현 필요
- 폴링 주기(3초)만큼 지연 허용
```

---

### 9. 커스텀 비즈니스 메트릭 (2시간)

**추가할 메트릭:**
```java
@Component
@RequiredArgsConstructor
public class WaitingMetrics {
    private final MeterRegistry registry;

    // 카운터: 등록/취소/입장 수
    private Counter registerCounter;
    private Counter cancelCounter;
    private Counter enterCounter;

    // 게이지: 현재 대기 수
    private AtomicInteger currentWaitingCount = new AtomicInteger(0);

    // 히스토그램: 실제 대기 시간
    private Timer waitingDurationTimer;

    @PostConstruct
    public void init() {
        registerCounter = Counter.builder("waiting.registered.total")
            .description("Total registered waitings")
            .register(registry);

        cancelCounter = Counter.builder("waiting.canceled.total")
            .description("Total canceled waitings")
            .register(registry);

        enterCounter = Counter.builder("waiting.entered.total")
            .description("Total entered waitings")
            .register(registry);

        Gauge.builder("waiting.current.count", currentWaitingCount, AtomicInteger::get)
            .description("Current waiting count")
            .register(registry);

        waitingDurationTimer = Timer.builder("waiting.duration.seconds")
            .description("Actual waiting duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void recordRegister() {
        registerCounter.increment();
        currentWaitingCount.incrementAndGet();
    }

    public void recordEnter(Duration waitDuration) {
        enterCounter.increment();
        currentWaitingCount.decrementAndGet();
        waitingDurationTimer.record(waitDuration);
    }
}
```

---

### 10. Grafana 대시보드 (2시간)

**생성할 파일:**
```
dockers/observability/grafana/dashboards/
├── waiting-system-overview.json
├── kafka-metrics.json
└── redis-metrics.json
```

**대시보드 패널 구성:**
```
┌─────────────────────────────────────────────────┐
│           Booster Waiting System Dashboard       │
├─────────────────────────────────────────────────┤
│ [등록 TPS]  [취소 TPS]  [입장 TPS]  [에러율]      │
├─────────────────────────────────────────────────┤
│ [현재 대기 수]      │ [평균 대기 시간 (분)]       │
├─────────────────────────────────────────────────┤
│ [P50/P95/P99 응답시간 그래프]                     │
├─────────────────────────────────────────────────┤
│ [식당별 대기 현황]   │ [시간대별 등록 추이]       │
├─────────────────────────────────────────────────┤
│ [Redis 캐시 히트율]  │ [Kafka Consumer Lag]      │
└─────────────────────────────────────────────────┘
```

---

### 11. WebSocket 실시간 대기 순번 (4시간)
> "실시간" 기능으로 차별화

**의존성:**
```gradle
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

**구현:**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/waiting")
            .setAllowedOrigins("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}

@Service
@RequiredArgsConstructor
public class WaitingNotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    // 대기 순번 변경 시 실시간 알림
    public void notifyRankChange(Long restaurantId, Long waitingId, int newRank) {
        messagingTemplate.convertAndSend(
            "/topic/waiting/" + waitingId,
            new RankUpdateMessage(waitingId, newRank)
        );
    }

    // 호출 알림
    public void notifyCall(Long waitingId) {
        messagingTemplate.convertAndSend(
            "/topic/waiting/" + waitingId,
            new CallNotificationMessage(waitingId, "입장해주세요!")
        );
    }
}
```

---

### 12. Rate Limiting (Gateway) (1시간)

**application.yml:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: waiting-service
          uri: lb://waiting-service
          predicates:
            - Path=/api/v1/waitings/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter:
                  replenishRate: 100      # 초당 100개
                  burstCapacity: 200      # 순간 최대 200개
                  requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"
```

**KeyResolver:**
```java
@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
        );
    }
}
```

---

## 🟡 MEDIUM - 기술 깊이 어필 (예상 10시간)

### 13. Contract Testing (Pact) (3시간)
> MSA 환경에서 서비스 간 계약 검증

```gradle
implementation 'au.com.dius.pact.consumer:junit5:4.6.5'
implementation 'au.com.dius.pact.provider:junit5:4.6.5'
```

---

### 14. Saga 패턴 구현 (4시간)
> 분산 트랜잭션 처리 능력 어필

**시나리오: 웨이팅 입장 처리**
```
1. 웨이팅 상태 변경 (CALLED → ENTERED)
2. 식당 점유율 증가
3. 알림 발송

실패 시 보상 트랜잭션:
- 식당 점유율 증가 실패 → 웨이팅 상태 롤백
```

---

### 15. Chaos Engineering 테스트 (2시간)
> 장애 대응 능력 증명

```java
@Test
@DisplayName("Redis 장애 시 Self-Healing 동작 확인")
void whenRedisDown_thenFallbackToDatabase() {
    // Redis 중단
    redisContainer.stop();

    // 순위 조회 → DB 폴백 확인
    WaitingDetailResponse response = waitingService.getWaiting(waitingId);

    assertThat(response.rank()).isNotNull();
    // Self-Healing 로그 확인
}
```

---

### 16. CQRS 패턴 적용 (4시간)
> 읽기/쓰기 분리로 성능 최적화 어필

```
Write Model: PostgreSQL (강한 일관성)
Read Model: Elasticsearch (빠른 검색)

이벤트 흐름:
Waiting 생성 → Outbox → Kafka → ES Indexer → Elasticsearch
```

---

## 🟢 LOW - 차별화 요소 (선택)

### 17. Helm Charts (3시간)
### 18. Service Mesh (Istio) 탐구 (4시간)
### 19. Analytics Service (3시간)
### 20. Push Notification (FCM) (2시간)

---

## 실행 체크리스트

### Phase 1: 기본기 (1주차)
- [ ] README.md 작성
- [ ] GitHub Actions CI 파이프라인
- [ ] Dockerfile 작성 (모든 서비스)
- [ ] docker-compose.yml (전체 스택)

### Phase 2: 운영 준비 (2주차)
- [ ] Kubernetes 매니페스트
- [ ] JaCoCo 커버리지
- [ ] Swagger API 문서
- [ ] 부하 테스트 & 결과 문서화

### Phase 3: 고급 기능 (3주차)
- [ ] ADR 작성
- [ ] 커스텀 메트릭
- [ ] Grafana 대시보드
- [ ] WebSocket 실시간 기능

### Phase 4: 차별화 (4주차)
- [ ] Rate Limiting
- [ ] Contract Testing
- [ ] Saga 패턴
- [ ] Chaos Engineering

---

## 예상 소요 시간

| 우선순위 | 항목 수 | 예상 시간 |
|----------|---------|-----------|
| 🔴 CRITICAL | 5개 | 20시간 |
| 🟠 HIGH | 7개 | 15시간 |
| 🟡 MEDIUM | 4개 | 13시간 |
| 🟢 LOW | 4개 | 12시간 |
| **합계** | **20개** | **~60시간** |

**권장 진행 순서:**
1. CRITICAL 전체 완료 → 포트폴리오 "제출 가능" 수준
2. HIGH 전체 완료 → "경쟁력 있는" 수준
3. MEDIUM 선택적 완료 → "인상적인" 수준

---

## 완료 후 예상 포트폴리오 점수

| 단계 | 점수 | 수준 |
|------|------|------|
| 현재 | 6.5/10 | 주니어~미드 |
| CRITICAL 완료 | 8.0/10 | 미드~시니어 |
| HIGH 완료 | 9.0/10 | 시니어 |
| MEDIUM 완료 | 9.5/10 | 빅테크 레디 |

화이팅! 🚀
