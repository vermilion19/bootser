package com.booster.storage.redis.repository;

import com.booster.storage.redis.config.TestRedisConfig;
import com.booster.storage.redis.domain.WaitingUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestRedisConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RedissonRankingRepositoryTest {

    @Autowired
    private RedissonRankingRepository redissonRankingRepository;

    @Autowired
    private RedissonClient redissonClient; // 데이터 초기화용

    @AfterEach
    void cleanUp() {
        redissonClient.getKeys().flushall();
    }

    @Test
    void 대기열_진입_및_순위_조회_테스트() {
        // given
        Long restaurantId = 2L;
        // 순서가 뒤죽박죽 들어와도 waitingNumber 기준으로 정렬되는지 확인
        WaitingUser user1 = WaitingUser.of(restaurantId, 10L, 1);  // 1번 (1등)
        WaitingUser user2 = WaitingUser.of(restaurantId, 20L, 5);  // 5번 (2등)
        WaitingUser user3 = WaitingUser.of(restaurantId, 30L, 10); // 10번 (3등)

        // when
        redissonRankingRepository.add(user3);
        redissonRankingRepository.add(user1);
        redissonRankingRepository.add(user2);

        // then
        assertThat(redissonRankingRepository.getRank(user1)).isEqualTo(1L);
        assertThat(redissonRankingRepository.getRank(user2)).isEqualTo(2L);
        assertThat(redissonRankingRepository.getRank(user3)).isEqualTo(3L);
    }

    @Test
    void 대기열_이탈_테스트() {
        // given
        Long restaurantId = 2L;
        WaitingUser user = WaitingUser.of(restaurantId, 10L, 1);
        redissonRankingRepository.add(user);

        // when
        redissonRankingRepository.remove(user);

        // then
        assertThat(redissonRankingRepository.getRank(user)).isNull();
    }
}
