package com.booster.waitingservice.waiting.application;


import com.booster.storage.db.PostgresTestConfig;
import com.booster.storage.redis.RedisTestConfig;
import com.booster.waitingservice.support.IntegrationTestSupport;
import com.booster.waitingservice.waiting.domain.WaitingRepository;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import({PostgresTestConfig.class, RedisTestConfig.class})
public class WaitingConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private WaitingRegisterFacade waitingRegisterFacade;

    @Autowired
    private WaitingRepository waitingRepository;

    @Test
    @DisplayName("동시성 테스트: 100명이 동시에 줄을 서도 대기 번호는 중복 없이 순차적으로 발급되어야 한다.")
    void register_concurrency() throws InterruptedException {
        // given
        int threadCount = 100; // 동시에 100명 요청
        // 멀티스레드 환경 구성 (32개 스레드 풀)
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        // 모든 스레드가 준비될 때까지 기다렸다가 '땅!' 하고 동시에 출발시키기 위한 장치
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 성공 횟수, 실패 횟수 카운트
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    // 각자 다른 전화번호로 요청 생성
                    RegisterWaitingRequest request = new RegisterWaitingRequest(
                            1L, "010-0000-" + String.format("%04d", finalI), 2
                    );
                    waitingRegisterFacade.register(request);
                    successCount.getAndIncrement();
                } catch (Exception e) {
                    System.out.println("에러 발생: " + e.getMessage());
                    failCount.getAndIncrement();
                } finally {
                    latch.countDown(); // 작업 끝남을 알림
                }
            });
        }

        latch.await(); // 100명 다 끝날 때까지 대기

        // then
        // 1. 100명 모두 성공했는가? (락 타임아웃 등으로 실패가 없어야 함)
        assertThat(successCount.get()).isEqualTo(threadCount);

        // 2. DB에 저장된 마지막 대기 번호가 100번인가?
        Integer maxNumber = waitingRepository.findMaxWaitingNumber(1L, java.time.LocalDateTime.now().minusDays(1));
        assertThat(maxNumber).isEqualTo(100);

        // 3. (옵션) 실제 DB 카운트도 100개인가?
        long count = waitingRepository.count();
        assertThat(count).isEqualTo(100);

        System.out.println("동시성 테스트 통과! 성공: " + successCount.get() + ", 마지막 번호: " + maxNumber);
    }

}
