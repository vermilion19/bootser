package com.booster.queryburstmsa.ranking.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class RankingDataInitializer {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Getter
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private final AtomicReference<String> status = new AtomicReference<>("대기 중");

    private final StringRedisTemplate redisTemplate;

    @Value("${data.init.window-hours:24}")
    private int windowHours;

    @Value("${data.init.products-per-hour:5000}")
    private int productsPerHour;

    public void initializeAsync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 적재가 진행 중입니다.");
        }
        CompletableFuture.runAsync(() -> {
            try {
                initialize();
            } finally {
                running.set(false);
            }
        });
    }

    private void initialize() {
        status.set("ranking 데이터 초기화 중");
        Set<String> keys = new HashSet<>();
        keys.addAll(redisTemplate.keys("RANK:hourly:*"));
        keys.addAll(redisTemplate.keys("ranking:event:*"));
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        LocalDateTime now = LocalDateTime.now();
        for (int hour = 0; hour < windowHours; hour++) {
            String key = "RANK:hourly:" + now.minusHours(hour).format(HOUR_FMT);
            for (int i = 1; i <= productsPerHour; i++) {
                long productId = ((long) hour * productsPerHour) + i;
                double score = (productsPerHour - i + 1) + (hour * 0.1d);
                redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
            }
            redisTemplate.expire(key, Duration.ofHours(25));
            status.set("ranking 적재 중: " + (hour + 1) + "/" + windowHours);
        }

        status.set("완료");
    }
}
