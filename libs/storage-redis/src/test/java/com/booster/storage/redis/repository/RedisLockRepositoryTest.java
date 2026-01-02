package com.booster.storage.redis.repository;

import com.booster.storage.redis.TestRedisApplication;
import com.booster.storage.redis.config.TestRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(classes = TestRedisApplication.class)
@Import(TestRedisConfig.class)
class RedisLockRepositoryTest {

    @Autowired
    private RedisLockRepository redisLockRepository;

    @Test
    @DisplayName("단일 쓰레드에서 락 획득 및 해제 성공 테스트")
    void lockAndUnlockTest() {
        // given
        String lockKey = "test-lock:1";

        // when
        Boolean isLocked = redisLockRepository.lock(lockKey);

        // then
        assertThat(isLocked).isTrue();

        // 해제 후 다시 획득 가능한지 확인
        redisLockRepository.unlock(lockKey);
        assertThat(redisLockRepository.lock(lockKey)).isTrue();
        redisLockRepository.unlock(lockKey);
    }

    @Test
    @DisplayName("동시에 50개의 요청이 올 때, 락을 획득한 쓰레드가 해제하기 전까지는 다른 쓰레드가 절대 진입할 수 없다")
    void concurrentLockTest() throws InterruptedException {
        // given
        String lockKey = "concurrent-lock";
        int threadCount = 50;
        // 쓰레드 재사용에 의한 재진입 문제를 방지하기 위해
        // 작업 개수만큼 쓰레드를 생성하거나, 락 획득 후 충분히 점유합니다.
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    // waitTime을 0으로 설정하여, 즉시 획득 못하면 바로 실패하도록 함
                    // (기존 RedisLockRepository 구현의 waitTime이 길면 성공 숫자가 늘어날 수 있음)
                    if (redisLockRepository.lock(lockKey)) {
                        successCount.incrementAndGet();
                        // 중요: 다른 모든 쓰레드가 도달할 때까지 락을 잡고 기다림
                        Thread.sleep(500);
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        // 락을 잡은 놈이 500ms 동안 안 놓아주기 때문에,
        // waitTime이 짧은 다른 쓰레드들은 모두 실패해야 정상입니다.
        assertThat(successCount.get()).isEqualTo(1);

        // 테스트 클린업
        redisLockRepository.unlock(lockKey);
    }

}