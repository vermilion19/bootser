package com.booster.dday.release.infrastructure;

/**
 * 팀 한 건.
 *
 * <p>{@code strSport} 와 {@code strLeague} 를 받는 까닭은 표시가 아니라
 * <b>검사</b>다 — SPEC §12.3 이 실측한 함정 때문이다. 이름으로 찾는 길은 맞게 오지만,
 * 그것을 확인하지 않으면 언젠가 축구팀 24개가 야구 리그에 조용히 들어온다.
 */
public record SportsDbTeam(
        String idTeam,
        String strTeam,
        String strTeamAlternate,
        String strSport,
        String strLeague
) {
}
