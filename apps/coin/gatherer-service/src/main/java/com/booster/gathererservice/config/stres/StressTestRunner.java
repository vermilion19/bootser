package com.booster.gathererservice.config.stres;

import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Profile("stress") // 'stress' 프로파일일 때만 실행
@RequiredArgsConstructor
public class StressTestRunner implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic coinTopic;
    private final ObjectMapper objectMapper; // Jackson 라이브러리 주입!

    @Override
    public void run(String... args) {
        log.info("### STRESS TEST: Jackson 직렬화 부하 테스트 시작 ###");

        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    // 1. 자바 객체 생성 (Heap 메모리 사용)
                    TradeDto trade = new TradeDto(
                            UUID.randomUUID().toString(),
                            "trade",
                            List.of("KRW-BTC"),
                            100000000.0,
                            System.currentTimeMillis()
                    );

                    // 2. [핵심] Jackson에게 일을 시킴 (여기서 CPU가 튈 겁니다)
                    String payload = objectMapper.writeValueAsString(trade);

                    // 3. Redis 전송
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
    static class TradeDto {
        private String ticket;
        private String type;
        private List<String> codes;
        private Double trade_price;
        private Long timestamp;
    }

}
