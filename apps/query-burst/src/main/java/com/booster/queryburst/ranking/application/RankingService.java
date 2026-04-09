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
import java.util.concurrent.TimeUnit;

/**
 * 실시간 인기 상품 랭킹 서비스.
 *
 * <h2>자료구조</h2>
 * Redis Sorted Set (Redisson RScoredSortedSet)
 * - Key: "RANK:hourly:{yyyyMMddHH}" (시간대별)
 * - Member: productId (String)
 * - Score: 해당 시간대 누적 판매 수량
 *
 * <h2>슬라이딩 윈도우 집계</h2>
 * ZUNIONSTORE로 여러 시간대 키를 합산하여 N시간 윈도우 랭킹을 계산한다.
 * - 1시간 윈도우: 현재 시간대 키 1개
 * - 6시간 윈도우: 최근 6개 시간대 키 합산
 * - 24시간 윈도우: 최근 24개 시간대 키 합산
 *
 * <h2>TTL 전략</h2>
 * 시간대별 키는 25시간 TTL로 자동 만료.
 * 일별 통계는 ProductDailySales 테이블(DB)에 영속.
 */
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

    /**
     * 상품 판매 수량을 현재 시간대 Sorted Set에 반영.
     *
     * Kafka ORDER_CREATED Consumer에서 호출.
     * ZINCRBY O(log N) — Lock-free.
     */
    public void incrementSales(Long productId, int quantity) {
        String key = hourlyKey(LocalDateTime.now());
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        set.addScore(String.valueOf(productId), quantity);
        set.expire(HOURLY_KEY_TTL);
        log.debug("[Ranking] 판매 반영. productId={}, qty={}, key={}", productId, quantity, key);
    }

    /**
     * ORDER_CANCELED 이벤트 처리: 판매량 감소.
     */
    public void decrementSales(Long productId, int quantity) {
        String key = hourlyKey(LocalDateTime.now());
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        Double current = set.getScore(String.valueOf(productId));
        if (current != null) {
            double next = Math.max(0, current - quantity);
            set.add(next, String.valueOf(productId));
        }
    }

    /**
     * 슬라이딩 윈도우 TOP N 조회.
     *
     * @param windowHours 윈도우 크기 (1, 6, 24 등)
     * @param size        반환할 상위 N개
     */
    public List<ProductRankingResult> getTopProducts(int windowHours, int size) {
        List<String> keys = getHourlyKeys(windowHours);

        // 합산 결과를 임시 키에 저장 (ZUNIONSTORE)
        String destKey = WINDOW_KEY_PREFIX + windowHours + "h:" + System.currentTimeMillis();
        RScoredSortedSet<String> dest = redissonClient.getScoredSortedSet(destKey);

        // 각 시간대 키를 합산
        for (String key : keys) {
            RScoredSortedSet<String> hourSet = redissonClient.getScoredSortedSet(key);
            Collection<org.redisson.client.protocol.ScoredEntry<String>> entries = hourSet.entryRangeReversed(0, -1);
            for (org.redisson.client.protocol.ScoredEntry<String> entry : entries) {
                dest.addScore(entry.getValue(), entry.getScore());
            }
        }

        dest.expire(WINDOW_KEY_TTL);

        // Top N 조회 (score 내림차순)
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

        // 임시 키 즉시 삭제
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
