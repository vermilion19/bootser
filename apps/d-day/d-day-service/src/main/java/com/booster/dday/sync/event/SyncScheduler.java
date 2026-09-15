package com.booster.dday.sync.event;

import com.booster.dday.sync.application.SyncOrchestrator;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공휴일 동기화 스케줄 — <b>주 1회</b> (ARCHITECTURE §5.1).
 *
 * <p>공휴일은 거의 안 변하고 실패 비용이 낮다(다음 주에 다시 돈다). 경기 일정처럼
 * 10분마다 돌 이유가 없고, 한 회차가 2,040 호출이라 자주 돌면 그 자체가 부담이다.
 *
 * <p><b>락은 여기서 잡지 않는다.</b> {@link SyncOrchestrator} 가 잡는다 — 운영자가
 * 누르는 수동 트리거와 <b>같은 이름의 락</b>이어야 하기 때문이다 (§5.6).
 *
 * <p>예외를 삼킨다. 스케줄 메서드에서 예외가 올라가면 Spring 이 다음 회차를
 * <b>계속 돌리기는 하지만</b> 로그가 스택트레이스로만 남는다. 여기서 받아
 * 한 줄로 적는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dday.sync.holiday.scheduled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private final SyncOrchestrator orchestrator;

    /** 매주 월요일 03:10 (서버 시간). 사람이 적은 시간대에 둔다 */
    @Scheduled(cron = "${dday.sync.holiday.cron:0 10 3 * * MON}")
    public void syncHolidays() {
        try {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE)
                    .ifPresentOrElse(
                            runId -> log.info("[SyncScheduler] 공휴일 회차 {} 를 마쳤다", runId),
                            () -> log.info("[SyncScheduler] 공휴일 동기화가 이미 돌고 있다"));
        } catch (RuntimeException e) {
            log.error("[SyncScheduler] 공휴일 동기화가 죽었다 — 다음 주에 다시 돈다", e);
        }
    }
}
