package com.booster.storage.redis.repository;

import com.booster.storage.redis.TestRedisApplication;
import com.booster.storage.redis.config.TestRedisConfig;
import com.booster.storage.redis.domain.WaitingUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRedisApplication.class)
@Import(TestRedisConfig.class)
class RedisRankingRepositoryTest {

    @Autowired
    private RedisRankingRepository redisRankingRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void cleanUp() {
        // 테스트에 사용된 키들을 싹 비워줍니다.
        // 실무에서는 flushAll 보다는 테스트에서 사용한 특정 키 패턴만 지우는 것을 권장합니다.
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 대기열_진입_및_순위_조회_테스트() {
        // given
        String queueKey = "event:concert";
        WaitingUser user1 = WaitingUser.of("user1", queueKey);
        WaitingUser user2 = WaitingUser.of("user2", queueKey);

        // when
        redisRankingRepository.addWaitQueue(user1);
        try { Thread.sleep(10); } catch (InterruptedException e) {} // 시간 차를 둠
        redisRankingRepository.addWaitQueue(user2);

        // then
        Long rank1 = redisRankingRepository.getRank(user1);
        Long rank2 = redisRankingRepository.getRank(user2);

        assertThat(rank1).isEqualTo(0L); // 첫 번째
        assertThat(rank2).isEqualTo(1L); // 두 번째
    }

    @Test
    void 대기열_이탈_테스트() {
        // given
        String queueKey = "event:concert";
        WaitingUser user = WaitingUser.of("user1", queueKey);
        redisRankingRepository.addWaitQueue(user);

        // when
        Long removedCount = redisRankingRepository.remove(user);

        // then
        assertThat(removedCount).isEqualTo(1L); // 삭제된 건수가 1이어야 함
        Long rank = redisRankingRepository.getRank(user);
        assertThat(rank).isNull(); // 삭제 후 조회하면 null이어야 함
    }

    @Test
    void 전체_대기자_수_조회_테스트() {
        // given
        String queueKey = "event:concert";
        redisRankingRepository.addWaitQueue(WaitingUser.of("user1", queueKey));
        redisRankingRepository.addWaitQueue(WaitingUser.of("user2", queueKey));
        redisRankingRepository.addWaitQueue(WaitingUser.of("user3", queueKey));

        // when
        Long size = redisRankingRepository.getQueueSize(queueKey);

        // then
        assertThat(size).isEqualTo(3L);
    }

    @Test
    void 존재하지_않는_유저_조회_시_null_반환_테스트() {
        // given
        String queueKey = "event:concert";
        WaitingUser unknownUser = WaitingUser.of("unknown", queueKey);

        // when
        Long rank = redisRankingRepository.getRank(unknownUser);

        // then
        assertThat(rank).isNull(); // 존재하지 않으면 null을 반환하는지 확인
    }

}