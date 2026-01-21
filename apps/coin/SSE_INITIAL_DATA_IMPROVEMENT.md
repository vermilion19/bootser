# SSE 초기 데이터 전송 개선

## 개요

SSE 연결 시 클라이언트가 즉시 최신 코인 데이터를 받을 수 있도록 개선했습니다.

## 문제점

### 기존 동작 방식
```
클라이언트 SSE 연결 → "connect" 이벤트만 전송 → 새 trade 이벤트 대기 (수 초 소요)
```

- 페이지 접속 시 코인 가격이 0으로 표시됨
- 새로운 체결 데이터가 올 때까지 대기 필요 (업비트 체결 빈도에 의존)
- 상세 페이지 진입 시 그래프가 비어있음

## 해결 방안

### 개선된 동작 방식
```
클라이언트 SSE 연결 → "connect" 이벤트 → Redis에서 캐싱된 최신 데이터 조회 → 즉시 4개 코인 데이터 전송
```

## 수정 파일

### 1. gatherer-service

#### `CoinPriceService.java`
```java
// SSE 초기 데이터 TTL (gatherer-service 장애 시 오래된 데이터 자동 만료)
private static final Duration TRADE_DATA_TTL = Duration.ofMinutes(1);

/**
 * 전체 거래 데이터를 Redis에 저장 (SSE 초기 데이터 전송용)
 * TTL 1분: gatherer-service가 정상 동작하면 계속 갱신되고, 장애 시 자동 만료
 */
public void saveLatestTradeData(String code, String jsonPayload) {
    if (code == null || jsonPayload == null) return;

    // Key: coin:data:KRW-BTC
    String key = "coin:data:" + code;
    redisTemplate.opsForValue().set(key, jsonPayload, TRADE_DATA_TTL);
}
```

#### `UpbitWebSocketHandler.java`
```java
if ("trade".equals(tradeDto.getType())) {
    // 기존: 가격만 저장
    coinPriceService.saveCurrentPrice(tradeDto.getCode(), tradeDto.getTradePrice());
    // 추가: 전체 JSON도 저장 (SSE 초기 데이터 전송용)
    coinPriceService.saveLatestTradeData(tradeDto.getCode(), jsonPayload);
}
```

### 2. coin-service

#### `CoinSseService.java`
```java
private static final List<String> TARGET_CODES = List.of("KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE");

public SseEmitter subscribe(String clientId) {
    // ... 기존 코드 ...

    try {
        emitter.send(SseEmitter.event().name("connect").data("connected!"));

        // [핵심] Redis에서 각 코인의 최신 데이터를 조회해서 즉시 전송
        sendInitialData(emitter);

    } catch (IOException e) {
        emitters.remove(clientId);
    }
    // ...
}

/**
 * SSE 연결 직후 Redis에 캐싱된 최신 데이터를 전송
 */
private void sendInitialData(SseEmitter emitter) {
    for (String code : TARGET_CODES) {
        try {
            String key = "coin:data:" + code;
            String cachedData = redisTemplate.opsForValue().get(key);

            if (cachedData != null) {
                emitter.send(SseEmitter.event()
                        .name("trade")
                        .data(cachedData));
            }
        } catch (IOException e) {
            log.warn("초기 데이터 전송 실패: {}", code, e);
        }
    }
}
```

## Redis 키 구조

| 키 패턴 | 용도 | TTL | 예시 값 |
|---------|------|-----|---------|
| `coin:price:{CODE}` | 현재가 (기존) | 없음 | `"100000000"` |
| `coin:data:{CODE}` | 전체 거래 JSON (신규) | **1분** | `{"type":"trade",...}` |

## 데이터 흐름도

```
┌─────────────────────────────────────────────────────────────┐
│ Upbit WebSocket (실시간 체결 데이터)                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ UpbitWebSocketHandler (gatherer-service)                    │
│                                                             │
│ 1. coinPriceService.saveCurrentPrice()                      │
│    → Redis: coin:price:KRW-BTC = "100000000"               │
│                                                             │
│ 2. coinPriceService.saveLatestTradeData()  ← [신규]        │
│    → Redis: coin:data:KRW-BTC = "{전체 JSON}"              │
│                                                             │
│ 3. redisTemplate.convertAndSend("coin-trade-topic", json)   │
└─────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ CoinSseService.subscribe() (coin-service)                   │
│                                                             │
│ 1. SseEmitter 생성                                          │
│ 2. "connect" 이벤트 전송                                    │
│ 3. sendInitialData() 호출  ← [신규]                        │
│    → Redis에서 coin:data:* 조회                            │
│    → 4개 코인 데이터 즉시 전송                              │
│ 4. 이후 pub/sub으로 실시간 데이터 수신                      │
└─────────────────────────────────────────────────────────────┘
```

## 테스트 방법

1. **서비스 재시작**
   ```bash
   # gatherer-service 재시작 (Redis에 coin:data:* 키 생성)
   ./gradlew :apps:coin:gatherer-service:bootRun

   # coin-service 재시작
   ./gradlew :apps:coin:coin-service:bootRun
   ```

2. **Redis 데이터 확인**
   ```bash
   redis-cli
   > KEYS coin:data:*
   > GET coin:data:KRW-BTC
   ```

3. **프론트엔드 테스트**
   - `/coin` 페이지 접속 → 테이블에 즉시 데이터 표시 확인
   - 코인 row 클릭 → 상세 페이지에서 그래프 즉시 시작점 확인

## 기대 효과

- 페이지 접속 즉시 코인 가격 표시 (0 → 실제 가격)
- 상세 페이지 진입 시 그래프에 최소 1개 데이터 포인트 존재
- 사용자 경험 향상 (체감 로딩 시간 단축)

## 주의사항

- gatherer-service가 한 번이라도 실행되어야 Redis에 초기 데이터가 캐싱됨
- Redis 재시작 시 캐시 데이터 유실 (gatherer-service가 다시 채움)
- `coin:data:*` 키는 TTL 1분 설정됨
  - gatherer-service 정상: 체결마다 갱신되어 만료되지 않음
  - gatherer-service 장애: 1분 후 자동 만료되어 오래된 데이터 방지