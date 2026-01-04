package com.booster.storage.redis.repository;

import com.booster.storage.redis.domain.WaitingUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisRankingRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private String getKey(Long restaurantId) {
        return "waiting:ranking:" + restaurantId;
    }

    // 1. 대기열 등록 (ZADD)
    // 기존: timestamp -> 수정: waitingNumber (중복 방지 및 정확한 순서 보장)
    public Boolean add(WaitingUser user) {
        return redisTemplate.opsForZSet().add(
                user.getQueueKey(),
                user.waitingId().toString(), // Member: waitingId
                user.waitingNumber()         // Score: waitingNumber
        );
    }

    // 2. 내 순서 조회 (ZRANK)
    public Long getRank(WaitingUser user) {
        Long rank = redisTemplate.opsForZSet().rank(
                user.getQueueKey(),
                user.waitingId().toString()
        );
        return (rank == null) ? null : rank + 1; // 0등 -> 1등으로 변환
    }

    // 3. 대기열 삭제 (ZREM)
    public Long remove(WaitingUser user) {
        return redisTemplate.opsForZSet().remove(
                user.getQueueKey(),
                user.waitingId().toString()
        );
    }

    // 4. (참고) 특정 유저 객체가 없을 때를 대비한 오버로딩 (필요 시)
    public Long getRankRaw(String queueKey, String userId) {
        return redisTemplate.opsForZSet().rank(queueKey, userId);
    }

    public Long getQueueSize(String queueKey) {
        // zCard는 Sorted Set의 전체 요소 개수를 반환합니다.
        return redisTemplate.opsForZSet().zCard(queueKey);
    }
}
