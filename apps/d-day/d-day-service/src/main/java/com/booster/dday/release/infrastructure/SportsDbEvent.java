package com.booster.dday.release.infrastructure;

/**
 * TheSportsDB 가 준 경기 한 건 — <b>쓰는 필드만</b> (SPEC §12.4).
 *
 * <p>{@code eventsnextleague} 는 49개, {@code eventsseason} 은 30개 필드를 준다.
 * 나머지는 받아서 버린다 — 적어 두면 «쓰지도 않는데 바뀌면 신경 쓰이는 값» 이 된다.
 *
 * <p><b>원천이 준 모양 그대로다.</b> 날짜도 시각도 문자열이고, 순연 여부도
 * {@code "yes"} / {@code "no"} 다. 여기서 해석하지 않는다 — 「원천이 그렇게
 * 말했다」와 「우리가 그렇게 읽었다」가 한 타입에 섞이면 값이 이상할 때 어느 쪽
 * 탓인지 알 수 없다. 해석은 {@code SportEventRow} 가 한다.
 *
 * @param strTimestamp {@code 2026-09-11T09:30:00} — <b>UTC 다.</b> KST 18:30 경기가
 *                     이렇게 온다. 현지 시각으로 읽으면 <b>D-day 가 9시간 어긋난다</b>
 * @param strTime      없을 때가 있다. 그때 자정으로 퉁치지 않는다 (SPEC §9.9(4))
 * @param strPostponed {@code "yes"} · {@code "no"}. <b>D-4 의 근거다</b> (§12.1)
 */
public record SportsDbEvent(
        String idEvent,
        String strEvent,
        String dateEvent,
        String strTime,
        String strTimestamp,
        String strStatus,
        String strPostponed,
        String strSeason,
        String strVenue,
        String idLeague,
        String idHomeTeam,
        String idAwayTeam
) {
}
