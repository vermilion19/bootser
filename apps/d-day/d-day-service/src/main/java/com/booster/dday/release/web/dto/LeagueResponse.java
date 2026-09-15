package com.booster.dday.release.web.dto;

import com.booster.dday.release.domain.League;

/**
 * 리그 하나.
 *
 * <p>{@code zoneId} 를 싣는 까닭은 <b>D-day 를 어느 오늘에서 셌는지</b>를 화면이
 * 알아야 하기 때문이다. 공휴일에서 나라가 정하는 것과 같은 자리인데 (SPEC §10.8),
 * 여기서는 리그가 정한다 — KBO 는 KST 하나다.
 */
public record LeagueResponse(
        Long id,
        String sport,
        String name,
        String countryCode,
        String zoneId
) {

    public static LeagueResponse of(League league) {
        return new LeagueResponse(league.getId(), league.getSport(), league.getName(),
                league.getCountryCode(), league.getZoneId());
    }
}
