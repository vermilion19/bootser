package com.booster.gathererservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebSocketHandler extends TextWebSocketHandler {

    // 핵심: 공통 RedisTemplate<String, Object>가 아닌 StringRedisTemplate 주입
    // 이유: 업비트 JSON을 파싱하지 않고 문자열 그대로 토스하기 위함 (CPU 절약)
    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic coinTopic;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("### [Upbit] 서버 연결 성공! Session ID: {}", session.getId());

        // 1. 구독 요청 메시지 생성 (Raw JSON)
        // Java Text Blocks (""") 사용 (Java 15+)
        // 실제 운영 시 codes 부분은 설정 파일이나 DB에서 가져오도록 변경 필요
        String payload = """
                [
                  {"ticket": "%s"},
                  {"type": "trade", "codes": ["KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE"]}
                ]
                """.formatted(UUID.randomUUID().toString());

        // 2. 요청 전송
        session.sendMessage(new TextMessage(payload));
        log.info("### [Upbit] 데이터 요청 전송 완료: \n{}", payload);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 3. 데이터 수신 (업비트 -> 내 서버)
        String payload = message.getPayload();

        // 4. Redis로 바이패스 (내 서버 -> Redis)
        // 별도의 DTO 변환 과정(Jackson Parsing) 없이 바로 쏘기 때문에 매우 빠름
        redisTemplate.convertAndSend(coinTopic.getTopic(), payload);

        // 디버깅용 로그 (데이터가 너무 많이 오면 주석 처리하세요)
        // log.debug("### [Redis Pub] Data: {}", payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("### [Upbit] 데이터 전송 중 에러 발생", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.warn("### [Upbit] 연결 끊김: {} (Code: {})", status.getReason(), status.getCode());
        // 추후 여기에 재접속(Retry) 로직 구현 필요
    }
}
