package com.booster.gathererservice.config.stress;

import com.booster.gathererservice.config.application.CoinPriceService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("stress") // 'stress' 프로파일일 때만 실행
@RequiredArgsConstructor
public class StressTestRunner implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic coinTopic;
    private final ObjectMapper objectMapper;
    private final CoinPriceService coinPriceService;

    @Override
    public void run(String... args) {
        log.info("### STRESS TEST: Jackson 직렬화 부하 테스트 시작 ###");

        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    // 1. 자바 객체 생성 (Heap 메모리 사용)
                    TradeDto trade = new TradeDto(
                            "trade",           // type
                            "KRW-BTC",         // code (이제 String임)
                            100000000.0,       // tradePrice (필드명은 tradePrice지만 JSON은 trade_price로 나감)
                            System.currentTimeMillis() // timestamp
                    );

                    // 2. 가격 저장 서비스 호출 (모의투자용)
                    // (주의: DTO의 필드명이 tradePrice로 바뀌었으니 getter도 getTradePrice()로 바뀜)
                    coinPriceService.saveCurrentPrice(trade.getCode(), trade.getTradePrice());

                    // 3. JSON 변환 및 전송
                    String payload = objectMapper.writeValueAsString(trade);
                    redisTemplate.convertAndSend(coinTopic.getTopic(), payload);


                    // 속도가 너무 빠르면 조절 (선택)
                    // Thread.sleep(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 메인 스레드 종료 방지용
        try { Thread.currentThread().join(); } catch (InterruptedException e) {}
    }

    // 테스트용 DTO (내부 클래스)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor // 기본 생성자 추가 (습관적으로 넣는 게 좋습니다)
    static class TradeDto {
        private String type;     // "trade"
        private String code;     // "KRW-BTC" (List가 아니라 String이어야 함)

        // [중요] Jackson이 JSON으로 만들 때 "trade_price"로 변환하도록 설정
        @JsonProperty("trade_price")
        private Double tradePrice;

        private Long timestamp;
    }

}
