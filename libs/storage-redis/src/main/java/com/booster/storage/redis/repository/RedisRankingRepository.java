package com.booster.storage.redis.repository;

import com.booster.storage.redis.domain.WaitingUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisRankingRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    // 1. 대기열 진입 (객체 전체 활용)
    public Boolean addWaitQueue(WaitingUser user) {
        return redisTemplate.opsForZSet().add(user.getQueueKey(), user.getUserId(), user.getTimestamp());
    }

    // 2. 순번 조회 (객체의 멤버 활용)
    public Long getRank(WaitingUser user) {
        return redisTemplate.opsForZSet().rank(user.getQueueKey(), user.getUserId());
    }

    // 3. 대기열 이탈/삭제 (객체의 멤버 활용)
    public Long remove(WaitingUser user) {
        return redisTemplate.opsForZSet().remove(user.getQueueKey(), user.getUserId());
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
