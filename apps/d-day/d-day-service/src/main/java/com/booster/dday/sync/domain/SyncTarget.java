package com.booster.dday.sync.domain;

/**
 * 동기화 대상. <b>주기도 실패 비용도 다르므로 한 스케줄러로 묶지 않는다</b> (ARCHITECTURE §5.1).
 *
 * <table>
 *   <caption>두 원천은 성격이 다르다</caption>
 *   <tr><td>{@link #HOLIDAY}</td>
 *       <td>(국가, 연도) 단위 · 한 회차 1,020 호출 · <b>주 1회</b> · 실패 비용 낮다(다음 주)</td></tr>
 *   <tr><td>{@link #SPORT_EVENT}</td>
 *       <td>리그 단위 · 회차당 2~3 호출 · <b>10분</b> · 창이 끊기면 경기를 놓친다</td></tr>
 * </table>
 */
public enum SyncTarget {

    HOLIDAY,
    SPORT_EVENT,
    MOVIE;

    /**
     * 분산 락 이름 (ARCHITECTURE §5.6).
     *
     * <p><b>스케줄 경로와 수동 트리거가 같은 이름을 잡아야 한다.</b> {@code @SchedulerLock}
     * 은 스케줄 메서드에만 붙는데 {@code POST /admin/sync/{target}} 은 스케줄 메서드가
     * 아니다 — 애노테이션만 믿으면 스케줄러가 도는 중에 운영자가 눌러 이중 실행이 된다.
     */
    public String lockName() {
        return "dday-sync-" + name();
    }
}
