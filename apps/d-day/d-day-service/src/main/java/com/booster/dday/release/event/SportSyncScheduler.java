package com.booster.dday.release.event;

import com.booster.dday.sync.application.SyncOrchestrator;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경기 일정 동기화 스케줄 — <b>10분</b> (ARCHITECTURE §5.1).
 *
 * <p>공휴일이 주 1회인 것과 대비된다. 근거가 다르다.
 *
 * <ul>
 *   <li><b>창이 끊기면 경기를 놓친다.</b> 무료 키는 「다음 1건 · 지난 1건」만 주므로
 *       (SPEC §12.2) 창을 자주 옮기지 않으면 그 사이 경기가 영영 안 들어온다</li>
 *   <li><b>우천 순연은 경기 당일에 정해진다.</b> 주 1회로 돌면 이미 지나간 뒤에
 *       알게 되고, 그러면 D-4 가 있어도 알림이 늦다</li>
 * </ul>
 *
 * <p>10분마다인데 호출은 리그당 2~3건이다 — 하루 400건 남짓이라 남의 서버에 부담이
 * 아니다. 공휴일이 한 회차에 2,040 호출인 것과 규모가 다르다.
 *
 * <p><b>키가 없으면 이 빈이 아예 안 뜬다.</b> 켤 수 없는 것이 10분마다 「건너뜀」
 * 회차를 쌓으면 회차 표가 그것으로 가득 찬다 — 꺼진 것은 조용해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dday.sync.sports.scheduled", havingValue = "true")
public class SportSyncScheduler {

    private final SyncOrchestrator orchestrator;

    @Scheduled(fixedDelayString = "${dday.sync.sports.fixed-delay-ms:600000}")
    public void syncSportEvents() {
        try {
            orchestrator.trigger(SyncTarget.SPORT_EVENT, TriggerSource.SCHEDULE)
                    .ifPresentOrElse(
                            runId -> log.debug("[SportSyncScheduler] 회차 {} 를 마쳤다", runId),
                            () -> log.info("[SportSyncScheduler] 이미 돌고 있다"));
        } catch (RuntimeException e) {
            log.error("[SportSyncScheduler] 죽었다 — 10분 뒤에 다시 돈다", e);
        }
    }
}
