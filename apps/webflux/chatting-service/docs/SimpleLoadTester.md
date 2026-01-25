# SimpleLoadTester

WebSocket 서버에 대량의 동시 연결을 생성하여 부하 테스트를 수행하는 도구입니다.

## 환경변수 설정

| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| `TARGET_URL` | `ws://localhost:8080/ws/chat` | 대상 WebSocket 엔드포인트 |
| `CONN_COUNT` | `300` | 생성할 연결 수 |
| `CLIENT_ID` | `tester` | 클라이언트 식별자 |

## 동작 흐름

### 1. 연결 생성

```java
Flux.range(1, count)
    .flatMap(i -> {
        String userId = clientId + "-" + i;
        return HttpClient.create()
            .headers(h -> h.add("userId", userId))
            .websocket()
            .uri(url)
            .handle((in, out) -> { ... });
    }, 50)
```

- `Flux.range(1, count)`로 1부터 count까지 순차적으로 연결 시도
- 각 연결마다 `{clientId}-{번호}` 형태의 userId 헤더 설정

### 2. 동시성 제어

```java
.flatMap(..., 50) // 동시성 제어
```

- 최대 **50개**의 연결을 동시에 시도
- 한 번에 모든 연결을 열지 않고 점진적으로 증가

### 3. 연결 유지

```java
.handle((in, out) -> {
    int c = connected.incrementAndGet();
    if (c % 100 == 0) {
        System.out.println(clientId + " Connected: " + c);
    }
    return in.receive().then();
})
```

- 연결 성공 시 카운터 증가
- 100개 단위로 진행 상황 출력
- `in.receive().then()`으로 수신 루프 유지 (연결 끊기지 않음)

### 4. 재시도 전략

```java
.retryWhen(
    Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(5))
)
```

- **Exponential Backoff**: 실패 시 지수적으로 대기 시간 증가
- 초기 대기: 1초, 최대 대기: 5초
- 무제한 재시도 (`Long.MAX_VALUE`)

## 실행 방법

### 기본 실행 (300개 연결)

```bash
java SimpleLoadTester
```

### 환경변수로 커스텀 실행

```bash
TARGET_URL=ws://prod-server:8080/ws/chat \
CONN_COUNT=1000 \
CLIENT_ID=load-test-1 \
java SimpleLoadTester
```

## 특징

- **Reactor Netty** 기반으로 논블로킹 방식
- **자동 재연결**로 서버 장애 복구 테스트 가능
- 분산 테스트 시 `CLIENT_ID`로 각 테스터 구분 가능