package com.booster.coinservice.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinSseService {

    private final StringRedisTemplate redisTemplate;

    // 접속한 클라이언트 관리 (Thread-Safe한 Map 필수)
    // Key: Client ID, Value: SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 관리할 코인 목록
    private static final List<String> TARGET_CODES = List.of("KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE");

    // 1. 클라이언트 접속 처리 (SSE 연결 생성)
    public SseEmitter subscribe(String clientId) {
        // 타임아웃 설정: 0 = 무제한 (실무에선 적절히 30분~1시간 설정 추천)
        // Nginx 같은 프록시가 있으면 더 짧게 잡고 재접속 유도해야 함
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 콜백 설정: 만료되거나 완료되면 리스트에서 제거
        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        emitter.onError((e) -> emitters.remove(clientId));

        emitters.put(clientId, emitter);

        // 접속 확인용 더미 데이터 전송 (503 에러 방지용)
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected!"));

            // [핵심] Redis에서 각 코인의 최신 데이터를 조회해서 즉시 전송
            sendInitialData(emitter);

        } catch (IOException e) {
            emitters.remove(clientId);
        }

        log.info("### 클라이언트 접속: {}, 현재 접속자 수: {}", clientId, emitters.size());
        return emitter;
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

    // 2. 데이터 브로드캐스팅 (Redis -> 이 메서드 호출)
    // 여기가 핵심: Virtual Thread를 사용하여 병렬 전송
    public void broadcast(String coinDataJson) {
        if (emitters.isEmpty()) return;
        // Java 21+ Virtual Thread Executor
        // try-with-resources 구문을 사용하여 구조적 동시성(Structured Concurrency) 적용
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            emitters.forEach((id, emitter) -> {
                executor.submit(() -> {
                    try {
                        // 클라이언트에게 데이터 전송 (단방향)
                        emitter.send(SseEmitter.event()
                                .name("trade")   // 이벤트 이름
                                .data(coinDataJson)); // 데이터 (JSON String 그대로)
                    } catch (IOException | IllegalStateException e) {
                        // 전송 실패 시 조용히 제거 (클라이언트가 이미 떠남)
                        emitters.remove(id);
                    }
                });
            });
        } // 여기서 모든 전송 태스크가 끝날 때까지 기다림 (블로킹되지만 VT라 상관없음)
    }
}
