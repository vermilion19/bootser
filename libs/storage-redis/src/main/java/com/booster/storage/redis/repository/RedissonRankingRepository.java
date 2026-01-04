package com.booster.storage.redis.repository;

import com.booster.storage.redis.domain.WaitingUser;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class RedissonRankingRepository {

    private final RedissonClient redissonClient;

    // 1. 대기열 등록
    public void add(WaitingUser user) {
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(user.getQueueKey());
        // Redisson은 객체 자체를 Member로 쓸 수 있지만,
        // 성능과 직렬화 이슈 최소화를 위해 Long(waitingId)만 저장하는 것을 추천합니다.
        set.add(user.waitingNumber(), user.waitingId());
    }

    // 2. 내 순서 조회
    public Long getRank(WaitingUser user) {
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(user.getQueueKey());
        Integer rank = set.rank(user.waitingId());

        return (rank == null) ? null : rank + 1L;
    }

    // 3. 대기열 삭제
    public void remove(WaitingUser user) {
        RScoredSortedSet<Long> set = redissonClient.getScoredSortedSet(user.getQueueKey());
        set.remove(user.waitingId());
    }
}
