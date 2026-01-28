# Chatting Service 성능 비교

## 서비스 개요

세 가지 다른 스레드 모델을 사용한 WebSocket 채팅 서버 구현체입니다.

| 서비스 | 설명 |
|--------|------|
| `chatting-service` | WebFlux 기반 Reactive 스트림 처리 |
| `chatting-service-vt` | Spring MVC + Virtual Thread (Java 21+) |
| `chatting-service-t` | Spring MVC + 전통적 Platform Thread |

## 상세 비교

| 항목 | chatting-service | chatting-service-vt | chatting-service-t |
|------|------------------|---------------------|-------------------|
| **포트** | 8080 | 8081 | 8082 |
| **프레임워크** | Spring WebFlux | Spring MVC | Spring MVC |
| **서버** | Netty | Tomcat | Tomcat |
| **스레드 모델** | EventLoop (Non-blocking) | Virtual Thread | Platform Thread |
| **I/O 방식** | Non-blocking | Blocking (VT에서 효율적) | Blocking |
| **Redis 클라이언트** | Reactive (Lettuce Reactive) | Blocking (Lettuce) | Blocking (Lettuce) |
| **spring.threads.virtual.enabled** | N/A | `true` | `false` |
| **최대 스레드 수** | EventLoop 수 (CPU 코어) | 사실상 무제한 | 200 (설정값) |
| **스레드당 메모리** | ~1KB (태스크 큐) | ~1KB | ~1MB |

## 실행 명령

```bash
# Redis 먼저 실행 (필수)
docker run -d -p 6379:6379 redis:alpine

# 1. WebFlux Reactive 기반
./gradlew :apps:webflux:chatting-service:bootRun

# 2. Virtual Thread 기반
./gradlew :apps:webflux:chatting-service-vt:bootRun

# 3. 전통적 Platform Thread 기반
./gradlew :apps:webflux:chatting-service-t:bootRun
```

## WebSocket 엔드포인트

모든 서비스가 동일한 엔드포인트 구조를 사용합니다:

```
ws://localhost:{PORT}/ws/chat?userId={USER_ID}
```

### 메시지 포맷

```json
{
  "type": "TALK",
  "roomId": "room1",
  "userId": "user1",
  "message": "Hello!"
}
```

### 메시지 타입
- `ENTER`: 입장
- `TALK`: 대화
- `PING`: 연결 유지 (서버에서 무시)

## 성능 테스트 시 비교 포인트

### 1. 동시 연결 수 (Concurrency)
- **Platform Thread**: Tomcat 스레드 풀 크기(200)에 제한
- **Virtual Thread**: OS 스레드 제한 없이 수백만 연결 가능
- **WebFlux**: EventLoop 기반으로 적은 스레드로 대량 연결 처리

### 2. 메모리 사용량
- **Platform Thread**: 연결당 ~1MB (스택 메모리)
- **Virtual Thread**: 연결당 ~1KB
- **WebFlux**: 연결당 ~수 KB (버퍼 크기에 따라)

### 3. 응답 지연시간 (Latency)
- **Platform Thread**: Blocking I/O로 인한 스레드 대기
- **Virtual Thread**: Blocking 코드지만 VT가 효율적으로 스케줄링
- **WebFlux**: Non-blocking으로 스레드 대기 없음

### 4. CPU 사용률
- **Platform Thread**: 컨텍스트 스위칭 오버헤드
- **Virtual Thread**: 경량 컨텍스트 스위칭
- **WebFlux**: 최소한의 스레드로 CPU 효율적 사용

## 예상 결과

| 시나리오 | 예상 우위 |
|----------|-----------|
| 적은 연결 + 빠른 응답 | Platform Thread (단순함) |
| 많은 연결 + I/O 대기 | Virtual Thread |
| 대규모 연결 + 복잡한 스트림 | WebFlux |
| 개발 생산성 | Virtual Thread (동기 코드) |

## 부하 테스트 도구

```bash
# k6 (추천)
k6 run --vus 1000 --duration 60s websocket-test.js

# Artillery
artillery run websocket-load-test.yml

# 간단한 테스트 (chatting-service에 포함된 도구)
java -jar chatting-service/build/libs/attacker.jar
```
