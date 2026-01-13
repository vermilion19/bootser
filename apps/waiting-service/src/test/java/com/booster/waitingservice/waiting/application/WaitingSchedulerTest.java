package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.domain.WaitingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingScheduler 테스트")
class WaitingSchedulerTest {

    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RKeys rKeys;

    @InjectMocks
    private WaitingScheduler waitingScheduler;

    @Nested
    @DisplayName("cleanup 메서드")
    class Cleanup {

        @Test
        @DisplayName("성공: 미처리 대기(WAITING)를 CANCELED로 일괄 변경하고 Redis 키를 삭제한다")
        void cleanup_success() {
            // given
            given(waitingRepository.bulkUpdateStatusToCanceled()).willReturn(10);
            given(redissonClient.getKeys()).willReturn(rKeys);

            // when
            waitingScheduler.cleanup();

            // then
            verify(waitingRepository).bulkUpdateStatusToCanceled();
            verify(rKeys).deleteByPattern("waiting:ranking:*");
        }

        @Test
        @DisplayName("성공: 미처리 대기가 없어도 정상 실행된다")
        void cleanup_noData() {
            // given
            given(waitingRepository.bulkUpdateStatusToCanceled()).willReturn(0);
            given(redissonClient.getKeys()).willReturn(rKeys);

            // when
            waitingScheduler.cleanup();

            // then
            verify(waitingRepository).bulkUpdateStatusToCanceled();
            verify(rKeys).deleteByPattern("waiting:ranking:*");
        }
    }

    @Nested
    @DisplayName("checkNoShow 메서드")
    class CheckNoShow {

        @Test
        @DisplayName("성공: 5분 이상 경과한 CALLED 상태 대기를 CANCELED로 변경한다")
        void checkNoShow_success() {
            // given
            given(waitingRepository.updateStatusToNoShow(any(LocalDateTime.class))).willReturn(5);

            // when
            waitingScheduler.checkNoShow();

            // then
            verify(waitingRepository).updateStatusToNoShow(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("성공: 노쇼 대상이 없어도 정상 실행된다")
        void checkNoShow_noData() {
            // given
            given(waitingRepository.updateStatusToNoShow(any(LocalDateTime.class))).willReturn(0);

            // when
            waitingScheduler.checkNoShow();

            // then
            verify(waitingRepository).updateStatusToNoShow(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("성공: 여러 건의 노쇼 처리")
        void checkNoShow_multipleNoShows() {
            // given
            given(waitingRepository.updateStatusToNoShow(any(LocalDateTime.class))).willReturn(100);

            // when
            waitingScheduler.checkNoShow();

            // then
            verify(waitingRepository).updateStatusToNoShow(any(LocalDateTime.class));
        }
    }
}