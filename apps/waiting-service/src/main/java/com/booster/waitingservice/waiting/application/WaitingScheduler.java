package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.domain.WaitingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingScheduler {

    private final WaitingRepository waitingRepository;
    private final RedissonClient redissonClient;

    /**
     * 매일 자정(00:00:00) 실행
     * Cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "cleanupWaiting", lockAtMostFor = "50s", lockAtLeastFor = "30s")
    @Transactional
    public void cleanup() {
        log.info("[Scheduler] 대기열 초기화 작업 시작...");

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


    // 1분마다 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 * * * * *")
    // ShedLock: "checkNoShow"라는 이름으로 잠금을 걺.
    // lockAtMostFor: 작업이 아무리 길어져도 59초 뒤엔 락을 푼다 (데드락 방지)
    // lockAtLeastFor: 작업이 0.1초 만에 끝나도 최소 10초간은 락을 유지한다 (중복 실행 방지)
    @SchedulerLock(name = "checkNoShow", lockAtMostFor = "59s", lockAtLeastFor = "10s")
    @Transactional
    public void checkNoShow() {
        log.info("노쇼(No-Show) 처리 스케줄러 시작");

        // 기준 시간: 현재로부터 5분 전
        LocalDateTime limitTime = LocalDateTime.now().minusMinutes(5);

        // 쿼리 최적화: 건건이 조회해서 업데이트하지 말고, 벌크 업데이트(Bulk Update) 수행
        int updatedCount = waitingRepository.updateStatusToNoShow(limitTime);

        if (updatedCount > 0) {
            log.info("{}명의 대기자가 노쇼로 처리되었습니다.", updatedCount);
        }
    }
}
