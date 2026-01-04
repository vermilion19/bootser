package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.domain.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingCleanupScheduler {

    private final WaitingRepository waitingRepository;
    private final RedissonClient redissonClient;

    /**
     * 매일 자정(00:00:00) 실행
     * Cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanup() {
        log.info("🧹 [Scheduler] 대기열 초기화 작업 시작...");

        // 1. DB 정리: 아직도 'WAITING'인 상태인 것들 -> 'CANCELED'로 일괄 변경
        // (JPA 벌크 연산 사용)
        int updatedCount = waitingRepository.bulkUpdateStatusToCanceled();
        log.info("DB 정리 완료: {}건의 미처리 대기 상태를 취소 처리했습니다.", updatedCount);

        // 2. Redis 정리: 'waiting:ranking:*' 키 전체 삭제
        // Redisson의 deleteByPattern을 쓰면 패턴 매칭 삭제가 쉽습니다.
        redissonClient.getKeys().deleteByPattern("waiting:ranking:*");
        log.info("Redis 정리 완료: 모든 대기열 랭킹 키를 삭제했습니다.");

        log.info("✨ [Scheduler] 대기열 초기화 작업 완료!");
    }
}
