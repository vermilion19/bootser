package com.booster.queryburstmsa.ranking.application;

import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import com.booster.queryburstmsa.contracts.event.OrderEventType;
import com.booster.queryburstmsa.ranking.web.dto.ProductRankingView;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class RankingService {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Duration HOURLY_KEY_TTL = Duration.ofHours(25);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    public RankingService(RedissonClient redissonClient, StringRedisTemplate stringRedisTemplate) {
        this.redissonClient = redissonClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void apply(OrderEventPayload payload) {
        if (payload.eventType() != OrderEventType.ORDER_CREATED && payload.eventType() != OrderEventType.ORDER_CANCELED) {
            return;
        }
        String eventKey = "ranking:event:" + payload.eventType() + ":" + payload.orderId() + ":" + payload.occurredAt();
        Boolean firstSeen = stringRedisTemplate.opsForValue().setIfAbsent(eventKey, "1", IDEMPOTENCY_TTL);
        if (!Boolean.TRUE.equals(firstSeen)) {
            return;
        }

        String key = hourlyKey(payload.occurredAt());
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        double direction = payload.eventType() == OrderEventType.ORDER_CREATED ? 1.0d : -1.0d;
        payload.items().forEach(item -> set.addScore(String.valueOf(item.productId()), item.quantity() * direction));
        set.expire(HOURLY_KEY_TTL);
    }

    public List<ProductRankingView> getTopProducts(int windowHours, int size) {
        RScoredSortedSet<String> merged = redissonClient.getScoredSortedSet("ranking:window:" + windowHours + ":" + System.currentTimeMillis());
        try {
            for (int offset = 0; offset < windowHours; offset++) {
                RScoredSortedSet<String> bucket = redissonClient.getScoredSortedSet(hourlyKey(LocalDateTime.now().minusHours(offset)));
                Collection<org.redisson.client.protocol.ScoredEntry<String>> entries = bucket.entryRangeReversed(0, -1);
                for (org.redisson.client.protocol.ScoredEntry<String> entry : entries) {
                    merged.addScore(entry.getValue(), entry.getScore());
                }
            }

            Collection<org.redisson.client.protocol.ScoredEntry<String>> topEntries = merged.entryRangeReversed(0, size - 1);
            List<ProductRankingView> result = new ArrayList<>();
            int rank = 1;
            for (org.redisson.client.protocol.ScoredEntry<String> entry : topEntries) {
                if (entry.getScore() <= 0) {
                    continue;
                }
                result.add(new ProductRankingView(Long.parseLong(entry.getValue()), entry.getScore(), rank++));
            }
            return result;
        } finally {
            merged.delete();
        }
    }

    private String hourlyKey(LocalDateTime dateTime) {
        return "RANK:hourly:" + dateTime.format(HOUR_FMT);
    }
}
