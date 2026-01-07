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
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRedisApplication.class)
@Import(TestRedisConfig.class)
@Testcontainers(disabledWithoutDocker = true)
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
        Long restaurantId = 1L;
        // timestamp(시간) 대신 waitingNumber(번호)로 명확하게 순서를 지정합니다.
        WaitingUser user1 = WaitingUser.of(restaurantId, 100L, 1); // 1번 손님
        WaitingUser user2 = WaitingUser.of(restaurantId, 200L, 2); // 2번 손님

        // when
        redisRankingRepository.add(user1);
        // Thread.sleep(10); -> 필요 없음! 번호가 정렬 기준이므로 동시에 넣어도 순서 보장됨.
        redisRankingRepository.add(user2);

        // then
        Long rank1 = redisRankingRepository.getRank(user1);
        Long rank2 = redisRankingRepository.getRank(user2);

        // 로직에서 rank + 1을 반환하도록 수정했으므로 1부터 시작해야 함
        assertThat(rank1).isEqualTo(1L);
        assertThat(rank2).isEqualTo(2L);
    }

    @Test
    void 대기열_이탈_테스트() {
        // given
        Long restaurantId = 1L;
        WaitingUser user = WaitingUser.of(restaurantId, 100L, 5);
        redisRankingRepository.add(user);

        // when
        Long removedCount = redisRankingRepository.remove(user);

        // then
        assertThat(removedCount).isEqualTo(1L);

        Long rank = redisRankingRepository.getRank(user);
        assertThat(rank).isNull(); // 삭제 확인
    }

    // 만약 Repository에 getQueueSize 메서드가 없다면 이 테스트는 제거하거나 Repository에 추가해야 합니다.
    @Test
    void 전체_대기자_수_조회_테스트() {
        // given
        Long restaurantId = 1L;
        redisRankingRepository.add(WaitingUser.of(restaurantId, 10L, 1));
        redisRankingRepository.add(WaitingUser.of(restaurantId, 20L, 2));
        redisRankingRepository.add(WaitingUser.of(restaurantId, 30L, 3));

        // when
        // ZCard를 사용하는 메서드가 Repository에 있다고 가정 (없으면 추가 필요)
        Long size = redisTemplate.opsForZSet().zCard("waiting:ranking:" + restaurantId);
        // 또는: Long size = redisRankingRepository.getQueueSize(restaurantId);

        // then
        assertThat(size).isEqualTo(3L);
    }

    @Test
    void 존재하지_않는_유저_조회_시_null_반환_테스트() {
        // given
        Long restaurantId = 1L;
        WaitingUser unknownUser = WaitingUser.of(restaurantId, 999L, 1);

        // when
        Long rank = redisRankingRepository.getRank(unknownUser);

        // then
        assertThat(rank).isNull();
    }

}