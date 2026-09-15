package com.booster.dday.release.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.dday.release.application.SportQueryService;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.web.dto.DateChangeResponse;
import com.booster.dday.release.web.dto.LeagueResponse;
import com.booster.dday.release.web.dto.SportEventResponse;
import com.booster.dday.release.web.dto.TeamResponse;
import com.booster.dday.shared.dday.DDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기 조회 (D-2 · D-4).
 *
 * <pre>
 *   GET /api/v1/dday/leagues                       켜 둔 리그 목록
 *   GET /api/v1/dday/leagues/{id}/teams            그 리그의 팀
 *   GET /api/v1/dday/leagues/{id}/events           다가오는 경기
 *   GET /api/v1/dday/teams/{id}/events             그 팀의 다가오는 경기
 *   GET /api/v1/dday/sport-events/{id}/changes     그 경기가 옮겨진 이력
 * </pre>
 *
 * <h2>시간대를 인자로 안 받는다 — 리그가 정한다</h2>
 *
 * <p>공휴일에서 나라가 정하는 것과 같은 규칙이다 (SPEC §10.8). KBO 경기의 「오늘」은
 * KST 의 오늘이고, 한국에서 보든 뉴욕에서 보든 <b>D-1 은 같아야 한다.</b> 보는 사람의
 * 기기 날짜로 세면 정확히 E-1 이 고치려던 고장이 된다.
 *
 * <h2>동기화가 한 번도 안 돌았으면 빈 목록이다 — 404 가 아니다</h2>
 *
 * <p>리그 자체가 없으면 404 지만, 리그가 있고 경기가 없는 것은 <b>정상 상태</b>다.
 * 창 누적은 처음에 아무것도 없는 상태에서 시작한다 (SPEC §12.2).
 */
@RestController
@RequestMapping("/api/v1/dday")
@RequiredArgsConstructor
public class SportController {

    /** 한 번에 내보내는 상한. 무료 키에서는 애초에 몇 건 없다 */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SportQueryService sportQueryService;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @GetMapping("/leagues")
    public ApiResponse<List<LeagueResponse>> leagues() {
        return ApiResponse.success(sportQueryService.enabledLeagues().stream()
                .map(LeagueResponse::of)
                .toList());
    }

    @GetMapping("/leagues/{leagueId}/teams")
    public ApiResponse<List<TeamResponse>> teams(@PathVariable Long leagueId) {
        return ApiResponse.success(sportQueryService.teamsOf(leagueId).stream()
                .map(TeamResponse::of)
                .toList());
    }

    /** D-2 — 리그의 다가오는 경기 */
    @GetMapping("/leagues/{leagueId}/events")
    public ApiResponse<List<SportEventResponse>> leagueEvents(
            @PathVariable Long leagueId,
            @RequestParam(required = false) Integer limit) {

        League league = sportQueryService.league(leagueId);
        SportQueryService.EventPage page =
                sportQueryService.upcomingOfLeague(leagueId, capped(limit));

        return ApiResponse.success(render(page, league.zone()));
    }

    /**
     * D-2 — 한 팀의 다가오는 경기.
     *
     * <p>팀이 속한 리그의 시간대로 센다. 팀에서 리그를 한 번 더 읽는 까닭은
     * <b>D-day 가 시간대 없이 정해지지 않기</b> 때문이다.
     */
    @GetMapping("/teams/{teamId}/events")
    public ApiResponse<List<SportEventResponse>> teamEvents(
            @PathVariable Long teamId,
            @RequestParam(required = false) Integer limit) {

        SportQueryService.EventPage page =
                sportQueryService.upcomingOfTeam(teamId, capped(limit));

        if (page.events().isEmpty()) {
            return ApiResponse.success(List.of());
        }
        League league = sportQueryService.league(page.events().get(0).getLeagueId());
        return ApiResponse.success(render(page, league.zone()));
    }

    /** D-4 — 그 경기가 언제 어떻게 옮겨졌나 */
    @GetMapping("/sport-events/{eventId}/changes")
    public ApiResponse<List<DateChangeResponse>> changes(@PathVariable Long eventId) {
        return ApiResponse.success(sportQueryService.changesOf(eventId).stream()
                .map(DateChangeResponse::of)
                .toList());
    }

    private List<SportEventResponse> render(SportQueryService.EventPage page, ZoneId zone) {
        Instant now = Instant.now(clock);
        LocalDate today = dDayCalculator.today(zone, now);

        List<SportEventResponse> rendered = new ArrayList<>(page.events().size());
        for (SportEvent event : page.events()) {
            rendered.add(SportEventResponse.of(event,
                    page.nameOf(event.getHomeTeamId()),
                    page.nameOf(event.getAwayTeamId()),
                    zone, today));
        }
        return rendered;
    }

    private static int capped(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
