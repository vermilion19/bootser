# Mini-Zuul 면접 가이드

Netty 기반 경량 리버스 프록시 프로젝트 면접 대비 자료

---

## 프로젝트 어필 포인트

### 강점
| 항목 | 설명 |
|------|------|
| 저수준 네트워크 이해 | Spring Cloud Gateway 대신 Netty로 직접 구현 |
| 비동기/이벤트 루프 | EventLoop, Channel, Pipeline 개념 실습 |
| 장애 대응 설계 | Failover, Health Check, Backpressure 직접 구현 |
| 메모리 관리 | Reference counting, ByteBuf 누수 방지 |

### 약점 (보완 필요)
| 항목 | 상태 |
|------|------|
| 테스트 코드 | TODO |
| 성능 벤치마크 | TODO |
| 모니터링 연동 | TODO |

---

## "왜 만들었나요?" 답변

### Bad
> "Netty 공부하려고요" / "포트폴리오용이요"

### Good
> "Spring Cloud Gateway 내부가 Netty 기반인데, 블랙박스로 쓰다 보니 장애 상황에서 디버깅이 어려웠습니다.
>
> 프록시가 실제로 어떻게 동작하는지, Connection은 어떻게 관리되는지, Backpressure는 어떻게 처리하는지 직접 구현해보면서 이해하고 싶었습니다.
>
> 구현하면서 EventLoop 모델의 장점, Reference counting의 중요성, 그리고 Failover 설계 시 고려해야 할 점들을 체감할 수 있었습니다."

---

## 핵심 기술 설명

### 1. Netty EventLoop 모델
```
┌─────────────────────────────────────────┐
│ WorkerGroup (N개 스레드)                  │
│  ├─ EventLoop 1: Channel A, B, C        │
│  ├─ EventLoop 2: Channel D, E           │
│  └─ EventLoop 3: Channel F              │
└─────────────────────────────────────────┘

- 하나의 EventLoop가 여러 Channel 담당
- Channel 내 작업은 단일 스레드 보장 → Lock-free
- Context switching 최소화 → 고성능
```

### 2. 프록시 데이터 흐름
```
Client → HttpServerCodec → HttpObjectAggregator → ProxyFrontendHandler
                                                        ↓
                                                  LoadBalancer (Round Robin)
                                                        ↓
                                          Backend (Failover 지원)
                                                        ↓
                                                ProxyBackendHandler
                                                        ↓
                                                 Client Response
```

### 3. Failover 로직
```java
// 연결 실패 시
loadBalancer.recordFailure(server);  // Health Check 기록
if (currentRetry < maxRetries) {
    attemptConnection(nextServer);   // 다음 서버 시도
} else {
    sendErrorResponse(502);          // 모두 실패 시 Bad Gateway
}
```

### 4. Backpressure 처리
```java
// 백엔드 연결 전 요청이 들어오면 큐에 버퍼링
if (outboundChannel == null || !outboundChannel.isActive()) {
    if (pendingMessages.size() >= MAX_PENDING_MESSAGES) {
        sendErrorResponse(503);  // 큐 초과 시 Service Unavailable
        return;
    }
    pendingMessages.add(msg);
}
```

### 5. Reference Counting
```java
// ByteBuf는 Direct Memory 사용 → GC 대상 아님
request.retain();   // 참조 카운트 증가 (다른 곳에서 사용)
// ...
ReferenceCountUtil.release(msg);  // 참조 카운트 감소 (해제)
```

---

## 예상 질문 & 답변

### Q1. "Spring Cloud Gateway 쓰면 되는데 왜 직접 만들었나요?"

> **A:** Spring Cloud Gateway는 프로덕션에서 검증된 좋은 도구입니다. 하지만 내부 동작을 이해하지 못하면 장애 상황에서 대응이 어렵습니다.
>
> 예를 들어, "왜 Connection이 끊어졌는지", "메모리 누수가 발생하는 이유" 같은 문제를 디버깅하려면 Netty의 EventLoop, ByteBuf 관리를 이해해야 합니다.
>
> 직접 구현해보면서 이런 저수준 개념들을 체득했고, 이제 SCG를 쓰더라도 문제 상황에서 원인을 추론할 수 있게 되었습니다.

---

### Q2. "Netty vs Tomcat 차이가 뭔가요?"

| 항목 | Tomcat (Thread-per-Request) | Netty (Event Loop) |
|------|----------------------------|-------------------|
| 모델 | 요청당 스레드 1개 할당 | 소수 스레드가 다수 연결 처리 |
| 블로킹 | I/O 대기 시 스레드 블로킹 | 논블로킹, 이벤트 기반 |
| 동시 연결 | 스레드 수에 제한 (수백~수천) | 수만 이상 가능 (C10K) |
| 메모리 | 스레드당 스택 메모리 필요 | 적은 메모리로 많은 연결 |
| 적합 대상 | 일반 웹 애플리케이션 | 프록시, 채팅, 게임 서버 |

---

### Q3. "ByteBuf 메모리 누수 어떻게 방지하나요?"

> **A:** Netty의 ByteBuf는 Reference Counting 방식입니다.
>
> 1. `retain()` 호출 시 참조 카운트 +1
> 2. `release()` 호출 시 참조 카운트 -1
> 3. 카운트가 0이 되면 메모리 해제
>
> 프록시에서는 요청을 백엔드로 전달할 때 `retain()`을 호출하고, 전송 완료 후 Netty가 자동으로 `release()` 합니다. Failover 실패 시에는 `ReferenceCountUtil.release()`로 명시적 해제합니다.
>
> 개발 중에는 `-Dio.netty.leakDetection.level=paranoid` 옵션으로 누수를 감지할 수 있습니다.

---

### Q4. "실제 운영하면 어떤 문제가 생길까요?"

| 문제 | 해결 방안 |
|------|----------|
| TLS 미지원 | SslHandler 추가, 인증서 관리 |
| 모니터링 부재 | Micrometer + Prometheus 메트릭 노출 |
| Connection 재사용 없음 | Connection Pool 구현 (Keep-Alive) |
| 단일 인스턴스 | Kubernetes + Service Discovery 연동 |
| 설정 변경 시 재시작 | Config Server + 동적 리로드 |

---

### Q5. "Health Check가 Passive인데, Active로 바꾸면?"

> **A:** 현재는 실제 요청 실패 시에만 서버 상태를 업데이트합니다 (Passive).
>
> Active Health Check는 별도 스케줄러가 주기적으로 `/health` 엔드포인트를 호출해서 상태를 확인합니다.
>
> ```java
> @Scheduled(fixedDelay = 5000)
> public void healthCheck() {
>     for (Server server : servers) {
>         boolean healthy = httpClient.get(server, "/health").isOk();
>         server.setHealthy(healthy);
>     }
> }
> ```
>
> 장점: 첫 요청 실패 없이 미리 감지
> 단점: 추가 네트워크 트래픽, 복잡도 증가

---

### Q6. "확장하려면 어떻게 해야 하나요?"

| 기능 | 구현 방안 |
|------|----------|
| 서비스 디스커버리 | Eureka/Consul 연동, 서버 목록 동적 갱신 |
| Consistent Hashing | 특정 키 기반 라우팅 (세션 Sticky) |
| Rate Limiting | Token Bucket 알고리즘, Redis 연동 |
| Circuit Breaker | Resilience4j 또는 직접 구현 |
| 분산 트레이싱 | Trace ID 전파 (X-Request-Id) |

---

## 보완 우선순위

| 순위 | 항목 | 이유 | 예상 소요 |
|-----|------|------|----------|
| 1 | 테스트 코드 | 없으면 "테스트 안 짜는 사람" 인식 | 2-3시간 |
| 2 | 성능 벤치마크 | 숫자로 말해야 설득력 | 1-2시간 |
| 3 | README 정리 | 아키텍처 다이어그램, 실행 방법 | 1시간 |
| 4 | 모니터링 | 운영 관점 고려했다는 증거 | 2-3시간 |

---

## 벤치마크 예시 (목표)

```
# 테스트 환경
- Tool: k6
- Duration: 60s
- Virtual Users: 100

# 결과
- Requests/sec: 15,000+
- Avg Latency: 5ms
- P99 Latency: 20ms
- Error Rate: 0.01%

# 비교 (Spring Cloud Gateway)
- Requests/sec: 12,000
- Avg Latency: 8ms
```

---

## 기술 키워드 정리

면접에서 자연스럽게 언급할 키워드:

- **Netty**: EventLoop, Channel, Pipeline, ByteBuf, Reference Counting
- **프록시**: Forward Proxy vs Reverse Proxy, Load Balancing, Failover
- **비동기**: Non-blocking I/O, Reactor Pattern, Backpressure
- **장애 대응**: Health Check, Circuit Breaker, Graceful Degradation
- **성능**: C10K Problem, Zero-Copy, Direct Buffer
