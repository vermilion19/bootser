package com.booster.queryburst.ranking.application;

import com.booster.queryburst.ranking.application.dto.ProductRankingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final String HOURLY_KEY_PREFIX = "RANK:hourly:";
    private static final String WINDOW_KEY_PREFIX = "RANK:window:";
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Duration HOURLY_KEY_TTL = Duration.ofHours(25);
    private static final Duration WINDOW_KEY_TTL = Duration.ofMinutes(5);

    private final RedissonClient redissonClient;

    public void incrementSales(Long productId, int quantity) {
        String key = hourlyKey(LocalDateTime.now());
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        set.addScore(String.valueOf(productId), quantity);
        set.expire(HOURLY_KEY_TTL);
        log.debug("[Ranking] sales incremented. productId={}, qty={}, key={}", productId, quantity, key);
    }

    public void decrementSales(Long productId, int quantity) {
        String key = hourlyKey(LocalDateTime.now());
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        double nextScore = set.addScore(String.valueOf(productId), -quantity);
        if (nextScore < 0) {
            set.add(0.0, String.valueOf(productId));
        }
        set.expire(HOURLY_KEY_TTL);
    }

    public List<ProductRankingResult> getTopProducts(int windowHours, int size) {
        List<String> keys = getHourlyKeys(windowHours);

        String destKey = WINDOW_KEY_PREFIX + windowHours + "h:" + System.currentTimeMillis();
        RScoredSortedSet<String> dest = redissonClient.getScoredSortedSet(destKey);

        for (String key : keys) {
            RScoredSortedSet<String> hourSet = redissonClient.getScoredSortedSet(key);
            Collection<org.redisson.client.protocol.ScoredEntry<String>> entries = hourSet.entryRangeReversed(0, -1);
            for (org.redisson.client.protocol.ScoredEntry<String> entry : entries) {
                dest.addScore(entry.getValue(), entry.getScore());
            }
        }

        dest.expire(WINDOW_KEY_TTL);

        Collection<org.redisson.client.protocol.ScoredEntry<String>> topEntries =
                dest.entryRangeReversed(0, size - 1);

        List<ProductRankingResult> results = new ArrayList<>();
        int rank = 1;
        for (org.redisson.client.protocol.ScoredEntry<String> entry : topEntries) {
            results.add(new ProductRankingResult(
                    Long.parseLong(entry.getValue()),
                    entry.getScore(),
                    rank++
            ));
        }

        dest.delete();
        return results;
    }

    private List<String> getHourlyKeys(int windowHours) {
        List<String> keys = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < windowHours; i++) {
            keys.add(hourlyKey(now.minusHours(i)));
        }
        return keys;
    }

    private String hourlyKey(LocalDateTime dateTime) {
        return HOURLY_KEY_PREFIX + dateTime.format(HOUR_FMT);
    }
}
