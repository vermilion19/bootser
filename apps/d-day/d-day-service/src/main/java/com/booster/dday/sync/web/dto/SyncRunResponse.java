package com.booster.dday.sync.web.dto;

import com.booster.dday.sync.domain.SyncRun;
import com.booster.dday.sync.domain.SyncRunStatus;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import com.booster.dday.sync.domain.WarmStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 회차 하나의 보고.
 *
 * <p>{@code aborted} 를 {@code failed} 와 따로 싣는다. 앞은 «받았는데 안 믿었다»
 * (급감 가드가 막았다) 이고 뒤는 «못 받았다» 다 — 한 칸에 묶으면 <b>사람이 봐야 할
 * 신호가 흔한 실패 속에 사라진다</b> (ARCHITECTURE §5.3).
 */
public record SyncRunResponse(
        Long id,
        SyncTarget target,
        TriggerSource triggerSource,
        SyncRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Integer durationSeconds,
        int itemTotal,
        int itemOk,
        int itemFailed,
        int itemAborted,
        int droppedTotal,
        Long cacheVersion,
        WarmStatus warmStatus
) {

    public static SyncRunResponse of(SyncRun run) {
        Integer duration = run.getFinishedAt() == null ? null
                : (int) ChronoUnit.SECONDS.between(run.getStartedAt(), run.getFinishedAt());

        return new SyncRunResponse(
                run.getId(), run.getTarget(), run.getTriggerSource(), run.getStatus(),
                run.getStartedAt().truncatedTo(ChronoUnit.SECONDS),
                run.getFinishedAt() == null ? null
                        : run.getFinishedAt().truncatedTo(ChronoUnit.SECONDS),
                duration,
                run.getItemTotal(), run.getItemOk(), run.getItemFailed(),
                run.getItemAborted(), run.getDroppedTotal(),
                run.getCacheVersion(), run.getWarmStatus());
    }
}
