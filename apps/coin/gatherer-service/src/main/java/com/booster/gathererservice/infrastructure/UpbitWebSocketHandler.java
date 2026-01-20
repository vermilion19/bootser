package com.booster.gathererservice.infrastructure;

import com.booster.gathererservice.application.CoinPriceService;
import com.booster.gathererservice.dto.TradeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebSocketHandler extends TextWebSocketHandler {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic coinTopic;
    private final ObjectMapper objectMapper;
    private final CoinPriceService coinPriceService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("### [Upbit] 서버 연결 성공! Session ID: {}", session.getId());

        // 요청은 Text로 보내도 업비트가 알아듣습니다.
        String payload = """
                [
                  {"ticket": "%s"},
                  {"type": "trade", "codes": ["KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-DOGE"]}
                ]
                """.formatted(UUID.randomUUID().toString());

        session.sendMessage(new TextMessage(payload));
        log.info("### [Upbit] 요청 전송 완료");
    }

    /**
     * [핵심 수정] 업비트는 데이터를 BinaryMessage로 보냅니다!
     * 이걸 처리하지 않으면 Code 1003 에러가 납니다.
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            // 1. 바이너리 -> 문자열 변환
            String jsonPayload = StandardCharsets.UTF_8.decode(message.getPayload()).toString();

            // 2. [추가된 로직] JSON 파싱 및 현재가 Redis 저장 (Key-Value)
            TradeDto tradeDto = objectMapper.readValue(jsonPayload, TradeDto.class);
            if ("trade".equals(tradeDto.getType())) {
                // 여기서 Redis에 가격을 저장합니다! (coin-api가 조회할 수 있게)
                coinPriceService.saveCurrentPrice(tradeDto.getCode(), tradeDto.getTradePrice());
            }

            // 3. Pub/Sub으로 스트리밍 전송 (기존 로직)
            redisTemplate.convertAndSend(coinTopic.getTopic(), jsonPayload);

        } catch (Exception e) {
            log.error("메시지 처리 중 오류", e);
        }
    }

    // 혹시라도 Text로 올 경우를 대비해 남겨둡니다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        redisTemplate.convertAndSend(coinTopic.getTopic(), payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("### [Upbit] 에러 발생", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.warn("### [Upbit] 연결 끊김: {} (Code: {})", status.getReason(), status.getCode());
    }
}
