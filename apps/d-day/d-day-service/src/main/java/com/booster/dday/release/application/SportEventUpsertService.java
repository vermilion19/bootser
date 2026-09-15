package com.booster.dday.release.application;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.application.dto.SportEventRow;
import com.booster.dday.release.application.dto.SportEventUpsertResult;
import com.booster.dday.release.domain.DateChange;
import com.booster.dday.release.domain.DateChangeRepository;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.LeagueRepository;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.SportEventRepository;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.release.event.ReleaseChangedEvent;
import com.booster.dday.release.infrastructure.SportsDbTeam;
import com.booster.dday.shared.outbox.AggregateType;
import com.booster.dday.shared.outbox.DomainOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 경기를 담는 자리 — <b>여기가 트랜잭션이고, 여기에 외부 호출이 하나도 없다.</b>
 *
 * <p>{@code SportEventSyncTask} 가 받아 오고 이 클래스가 쓴다. 공휴일 쪽과 같은
 * 규칙이다 (ARCHITECTURE §5.4) — <b>DB 커넥션을 쥔 채로 HTTP 를 기다리는 구간이
 * 한 번도 없게</b> 한다. 인자에 {@code List<SportEventRow>} 가 있는 것이 그 사실을
 * 강제한다.
 *
 * <h2>{@code DateChange} 와 Outbox 가 같은 트랜잭션에 있는 것이 D-4 의 전부다</h2>
 *
 * <p>ARCHITECTURE §3.5. 둘이 갈라지면 「이력에는 있는데 알림은 안 갔다」 또는 그
 * 반대가 생기고, 그것은 {@code DateChange} 가 존재하는 이유를 무너뜨린다. 그래서
 * <b>무엇이 바뀌었는지 판단하는 자리도 하나</b>여야 한다 —
 * {@link SportEvent#refresh} 가 바뀐 칸을 돌려주고, 이력과 이벤트를 그 하나의
 * 목록에서 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SportEventUpsertService {

    /** 원천 이름. {@code uk_sport_event_source} 의 앞 칼럼이다 */
    public static final String SOURCE = "thesportsdb";

    private final LeagueRepository leagues;
    private final TeamRepository teams;
    private final SportEventRepository events;
    private final DateChangeRepository dateChanges;
    private final DomainOutbox outbox;

    /**
     * 설정에 적힌 리그를 표로 만든다. <b>있으면 이름만 갱신한다.</b>
     *
     * <p>리그를 다시 만들지 않는 까닭은 {@code league.id} 가 {@code team} 과
     * {@code sport_event} 의 FK 이기 때문이다 — 새로 만들면 그 리그의 경기가 통째로
     * 고아가 된다.
     */
    @Transactional
    public League register(SportsSyncProperties.LeagueSpec spec) {
        return leagues.findBySourceAndExternalId(SOURCE, spec.externalId())
                .map(existing -> {
                    existing.refresh(spec.sport(), spec.name(), spec.countryCode(), spec.zone());
                    existing.enabled(true);
                    return existing;
                })
                .orElseGet(() -> leagues.save(League.of(SOURCE, spec.externalId(),
                        spec.sport(), spec.name(), spec.countryCode(), spec.zone(), true)));
    }

    /**
     * 팀 목록을 담는다.
     *
     * <p>이름을 알게 된 스텁은 스텁이 아니게 된다 ({@link Team#refresh}) — 스텁으로
     * 남겨 두면 «신호» 가 영영 켜져 있고, 그러면 아무도 그 신호를 안 본다.
     *
     * @return 담은 팀 수
     */
    @Transactional
    public int syncTeams(Long leagueId, List<SportsDbTeam> sourceTeams) {
        int stored = 0;
        for (SportsDbTeam source : sourceTeams) {
            if (source.idTeam() == null || source.idTeam().isBlank()) {
                continue;
            }
            String externalId = source.idTeam().trim();
            String name = source.strTeam() == null || source.strTeam().isBlank()
                    ? "(" + externalId + ")" : source.strTeam().trim();

            teams.findBySourceAndExternalId(SOURCE, externalId)
                    .ifPresentOrElse(
                            found -> found.refresh(leagueId, name, null),
                            () -> teams.save(Team.of(leagueId, SOURCE, externalId, name)));
            stored++;
        }
        return stored;
    }

    /**
     * 팀 목록을 받아 와야 하나.
     *
     * <p>10분마다 팀 목록까지 받으면 <b>바뀌지도 않는 것을 하루 144번</b> 받는다.
     * 팀은 시즌 중에 안 바뀌므로 <b>비었을 때와 스텁이 남았을 때만</b> 받는다 —
     * 스텁은 「원천 해석이 새고 있다」는 신호이고, 그 신호는 이름을 알게 되면
     * 저절로 꺼져야 한다.
     */
    @Transactional(readOnly = true)
    public boolean needsTeamSync(Long leagueId) {
        List<Team> known = teams.findAllByLeagueId(leagueId);
        if (known.isEmpty()) {
            return true;
        }
        return known.stream().anyMatch(Team::isStub);
    }

    /**
     * 받아 온 경기들을 반영한다.
     *
     * <h2>한 번에 읽는다 — 건마다 조회하지 않는다</h2>
     *
     * <p>회차 하나가 수십 건이고 그중 대부분은 «안 바뀌었다» 로 끝난다. 건마다
     * {@code findBySourceAndExternalId} 를 부르면 <b>안 바뀐 것을 확인하려고 질의를
     * 수십 번</b> 하는 셈이다.
     *
     * @param seenAt 이 회차가 본 시각. <b>안 바뀐 건도 이 값은 갱신된다</b> —
     *               「사라졌다」를 관측할 수 없으니 「오래 못 봤다」로 표현한다
     */
    @Transactional
    public SportEventUpsertResult apply(long runId, League league,
                                        List<SportEventRow> rows, Instant seenAt) {

        if (rows.isEmpty()) {
            return SportEventUpsertResult.empty();
        }

        Map<String, SportEvent> existing = new HashMap<>();
        for (SportEvent event : events.findAllBySourceAndExternalIdIn(SOURCE,
                rows.stream().map(SportEventRow::externalId).toList())) {
            existing.put(event.getExternalId(), event);
        }

        TeamIndex teamIndex = new TeamIndex(league.getId(),
                teams.findAllByLeagueId(league.getId()));

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int changesRecorded = 0;

        for (SportEventRow row : rows) {
            Long homeTeamId = teamIndex.idOf(row.homeTeamExternalId());
            Long awayTeamId = teamIndex.idOf(row.awayTeamExternalId());

            SportEvent event = existing.get(row.externalId());
            if (event == null) {
                events.save(SportEvent.of(SOURCE, row.externalId(), league.getId(),
                        homeTeamId, awayTeamId, row.name(), row.startsAt(),
                        row.timeIsUnknown(), row.status(), row.postponed(),
                        row.season(), row.venue(), seenAt));
                created++;
                continue;
            }

            List<SportEvent.Change> changes = event.refresh(homeTeamId, awayTeamId, row.name(),
                    row.startsAt(), row.timeIsUnknown(), row.status(), row.postponed(),
                    row.season(), row.venue(), seenAt);

            if (changes.isEmpty()) {
                unchanged++;
                continue;
            }
            updated++;
            changesRecorded += record(runId, event, changes, seenAt);
        }

        return new SportEventUpsertResult(rows.size(), created, updated, unchanged,
                changesRecorded, teamIndex.stubsCreated(), 0);
    }

    /**
     * 이력과 이벤트를 <b>같은 트랜잭션에서</b> 만든다.
     *
     * <h2>멱등키에 <b>탐지한 날</b>을 넣는다 — §3.5 와 다르다</h2>
     *
     * <p>ARCHITECTURE §3.5 는 경기 변경의 멱등키를 {@code (externalId, fromTs, toTs)}
     * 로 적었다. 그대로 두면 <b>순연이 풀렸다 다시 걸리는 건이 조용히 사라진다</b> —
     * {@code no→yes} 가 이미 적혀 있으므로 며칠 뒤의 같은 전이가 «이미 보냈다» 로
     * 걸러진다. 우천 순연은 야구에서 일상이고 (SPEC §12.1) 그 반복은 <b>서로 다른
     * 사실</b>이다. SCHEMA §6.3 이 {@code date_change} 에 유일 제약을 걸지 않은 것과
     * 같은 이유인데, 멱등키가 그것을 뒤에서 되돌리고 있었다.
     *
     * <p>날짜를 더해도 원래 목적은 그대로다. 창 누적이 같은 경기를 하루에 여러 번
     * 다시 보지만 <b>안 바뀐 건은 {@code sourceHash} 에서 이미 걸려</b> 여기까지
     * 오지 않는다 — 같은 날 같은 전이가 두 번 오는 것은 우리 쪽 재실행일 때뿐이고,
     * 그것은 여전히 걸러진다.
     */
    private int record(long runId, SportEvent event,
                       List<SportEvent.Change> changes, Instant seenAt) {

        List<Long> relatedTeamIds = new ArrayList<>(2);
        if (event.getHomeTeamId() != null) {
            relatedTeamIds.add(event.getHomeTeamId());
        }
        if (event.getAwayTeamId() != null) {
            relatedTeamIds.add(event.getAwayTeamId());
        }

        for (SportEvent.Change change : changes) {
            dateChanges.save(DateChange.of(SubjectType.SPORT_EVENT, event.getId(),
                    change.field(), change.oldValue(), change.newValue(), seenAt, runId));

            ReleaseChangedEvent payload = new ReleaseChangedEvent(
                    SubjectType.SPORT_EVENT, event.getId(), event.getExternalId(),
                    event.getName(), change.field(), change.oldValue(), change.newValue(),
                    event.getStartsAt(), seenAt, relatedTeamIds);

            outbox.append(AggregateType.SPORT_EVENT,
                    String.valueOf(event.getId()),
                    "CHANGED",
                    /* 파티션 키는 외부 경기 id 다 (§3.5) — 같은 경기의 변경이 순서대로 */
                    event.getExternalId(),
                    payload,
                    idempotencyKey(event.getExternalId(), change, seenAt));

            log.info("[SportSync] {} {} 이 {} 에서 {} 로 바뀌었다", event.getExternalId(),
                    change.field(), change.oldValue(), change.newValue());
        }
        return changes.size();
    }

    private static String idempotencyKey(String externalId, SportEvent.Change change,
                                         Instant detectedAt) {
        return String.join("|", externalId, change.field().name(),
                nullToDash(change.oldValue()), nullToDash(change.newValue()),
                detectedAt.atZone(ZoneOffset.UTC).toLocalDate().toString());
    }

    private static String nullToDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    /**
     * 원천의 팀 id → 우리 팀 id.
     *
     * <h2>모르는 팀을 만나면 스텁을 만든다 — FK 를 지키기 위해서</h2>
     *
     * <p>SCHEMA §1.4. {@code sport_event.home_team_id} 가 {@code team(id)} 를 가리키므로
     * 모르는 팀 id 를 그냥 넣을 수 없다. 그렇다고 그 경기를 버리면 <b>원천에 있는
     * 경기가 우리에게 없게</b> 되고, D-day 를 물어본 사람은 그 이유를 알 수 없다.
     *
     * <p>스텁을 만들면 경기는 담기고 {@code is_stub} 가 <b>「원천 해석이 새고 있다」는
     * 신호</b>로 남는다. 팀 목록 동기화가 이름을 알게 되면 스텁이 풀린다.
     */
    private final class TeamIndex {

        private final Long leagueId;
        private final Map<String, Long> byExternalId = new HashMap<>();
        private int stubsCreated;

        private TeamIndex(Long leagueId, List<Team> known) {
            this.leagueId = leagueId;
            for (Team team : known) {
                byExternalId.put(team.getExternalId(), team.getId());
            }
        }

        private Long idOf(String externalId) {
            if (externalId == null) {
                return null;
            }
            Long known = byExternalId.get(externalId);
            if (known != null) {
                return known;
            }
            /* 다른 리그에 이미 있는 팀일 수 있다 — 교류전이 그렇다 */
            Team team = teams.findBySourceAndExternalId(SOURCE, externalId)
                    .orElseGet(() -> {
                        log.warn("[SportSync] 모르는 팀 id {} — 스텁을 만든다 (SCHEMA §1.4)",
                                externalId);
                        stubsCreated++;
                        return teams.save(Team.stub(leagueId, SOURCE, externalId));
                    });
            byExternalId.put(externalId, team.getId());
            return team.getId();
        }

        private int stubsCreated() {
            return stubsCreated;
        }
    }
}
