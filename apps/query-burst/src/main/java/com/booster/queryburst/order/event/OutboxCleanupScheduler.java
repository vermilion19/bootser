package com.booster.queryburst.order.event;

import com.booster.queryburst.lock.DistributedLock;
import com.booster.queryburst.lock.FencingToken;
import com.booster.queryburst.lock.LockAcquisitionException;
import com.booster.queryburst.order.application.OutboxAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private static final String CLEANUP_LOCK_KEY = "outbox:cleanup:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxAdminService outboxAdminService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgePublishedEvents() {
        FencingToken token;
        try {
            token = distributedLock.tryLock(CLEANUP_LOCK_KEY, LOCK_TTL);
        } catch (LockAcquisitionException e) {
            log.debug("[OutboxCleanup] 다른 인스턴스가 정리 중이라 스킵합니다.");
            return;
        }

        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(RETENTION);
            long deletedCount = outboxAdminService.purgePublishedEvents(cutoff);
            log.info("[OutboxCleanup] published event {}건 정리 완료. cutoff={}", deletedCount, cutoff);
        } finally {
            distributedLock.unlock(CLEANUP_LOCK_KEY, token);
        }
    }
}
