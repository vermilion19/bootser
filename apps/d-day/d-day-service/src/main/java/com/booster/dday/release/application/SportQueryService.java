package com.booster.dday.release.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.release.domain.DateChange;
import com.booster.dday.release.domain.DateChangeRepository;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.LeagueRepository;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.SportEventRepository;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 경기 조회 (D-2).
 *
 * <h2>캐시를 안 쓴다 — 공휴일과 다르다</h2>
 *
 * <p>공휴일은 반년에 한 번 바뀌므로 캐시가 거의 언제나 맞는다. 경기 일정은
 * <b>10분마다 갱신되고 그중 일부는 실제로 바뀐다</b> (우천 순연). 캐시를 두면
 * 「연기됐다는 알림을 받고 열어 봤는데 예전 시각이 보인다」가 생긴다 — 그것이
 * D-4 를 무의미하게 만든다.
 *
 * <p>버전 플립으로 맞출 수도 있지만 그러면 10분마다 전부 미스이고, 그것은 캐시가
 * 아니다. {@code CacheNamespace} 에 경기 이름이 없는 것이 이 결정의 흔적이다.
 *
 * <h2>팀 이름을 한 번에 붙인다</h2>
 *
 * <p>{@code SportEvent} 는 팀을 id 로만 들고 있다 (연관을 안 맺었다). 경기마다 팀을
 * 조회하면 <b>경기 수 × 2 번의 질의</b>가 된다 — N+1 을 연관으로 만들지 않고
 * 처음부터 안 만든다.
 */
@Service
@RequiredArgsConstructor
public class SportQueryService {

    private final LeagueRepository leagues;
    private final TeamRepository teams;
    private final SportEventRepository events;
    private final DateChangeRepository dateChanges;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<League> enabledLeagues() {
        return leagues.findAllByEnabledTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public League league(Long leagueId) {
        return leagues.findById(leagueId)
                .orElseThrow(() -> new CoreException(DDayErrorCode.LEAGUE_NOT_FOUND,
                        "그 리그가 없다: " + leagueId));
    }

    @Transactional(readOnly = true)
    public List<Team> teamsOf(Long leagueId) {
        league(leagueId);
        return teams.findAllByLeagueIdOrderByNameAsc(leagueId);
    }

    /**
     * 리그의 다가오는 경기.
     *
     * @param limit 몇 건까지. <b>무료 키에서는 애초에 몇 건 없다</b> (SPEC §12.2)
     */
    @Transactional(readOnly = true)
    public EventPage upcomingOfLeague(Long leagueId, int limit) {
        League league = league(leagueId);
        List<SportEvent> found = events.findUpcomingOfLeague(league.getId(), Instant.now(clock));
        return page(cut(found, limit));
    }

    @Transactional(readOnly = true)
    public EventPage upcomingOfTeam(Long teamId, int limit) {
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new CoreException(DDayErrorCode.TEAM_NOT_FOUND,
                        "그 팀이 없다: " + teamId));

        List<SportEvent> found = events.findUpcomingOfTeam(team.getId(), Instant.now(clock));
        return page(cut(found, limit));
    }

    /**
     * 한 경기가 그동안 어떻게 옮겨졌나 (D-4).
     *
     * <p>이 조회가 있는 까닭은 <b>알림을 놓친 사람</b>이다. 「연기됐다」를 못 받았어도
     * 경기를 열어 보면 무엇이 언제 바뀌었는지 보여야 한다 — 알림은 at-least-once 지만
     * <b>휴대폰은 꺼져 있을 수 있다.</b>
     */
    @Transactional(readOnly = true)
    public List<DateChange> changesOf(Long eventId) {
        return dateChanges.findAllBySubjectTypeAndSubjectIdOrderByDetectedAtDesc(
                SubjectType.SPORT_EVENT, eventId);
    }

    private static List<SportEvent> cut(List<SportEvent> found, int limit) {
        int size = Math.min(found.size(), Math.max(1, limit));
        return found.subList(0, size);
    }

    /** 경기들과 그 경기에 나온 팀 이름 — <b>한 번에 읽은 것</b> */
    public record EventPage(List<SportEvent> events, Map<Long, Team> teamsById) {

        public String nameOf(Long teamId) {
            Team team = teamsById.get(teamId);
            return team == null ? null : team.getName();
        }
    }

    private EventPage page(List<SportEvent> found) {
        if (found.isEmpty()) {
            return new EventPage(List.of(), Map.of());
        }

        Set<Long> teamIds = new LinkedHashSet<>();
        for (SportEvent event : found) {
            if (event.getHomeTeamId() != null) {
                teamIds.add(event.getHomeTeamId());
            }
            if (event.getAwayTeamId() != null) {
                teamIds.add(event.getAwayTeamId());
            }
        }

        Map<Long, Team> byId = new HashMap<>();
        for (Team team : teams.findAllById(teamIds)) {
            byId.put(team.getId(), team);
        }
        return new EventPage(found, byId);
    }
}
