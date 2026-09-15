package com.booster.dday.sync.application;

import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncTarget;

/**
 * 동기화 한 대상이 할 일 (ARCHITECTURE §5.1).
 *
 * <p><b>던지지 않는다.</b> 부분 실패를 담아 돌려준다 — 1,020 호출 중 30이 실패해도
 * 나머지 990 은 반영돼야 하므로, 실패가 예외로 올라가면 그 990 을 무엇으로 셀지가
 * 없어진다.
 *
 * <p>대상마다 주기와 실패 비용이 다르지만 <b>오케스트레이터는 공유한다</b> — 락 ·
 * 회차 기록 · 캐시 플립이 전부 같은 모양이기 때문이다.
 */
public interface SyncTask {

    SyncTarget target();

    /**
     * @param runId 이 회차의 번호. <b>소프트 삭제의 기준이기도 하다</b> —
     *              {@code last_seen_run_id} 가 이것을 가리킨다 (SCHEMA §3.1)
     */
    SyncOutcome run(long runId);
}
