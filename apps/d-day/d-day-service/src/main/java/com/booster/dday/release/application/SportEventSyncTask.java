package com.booster.dday.release.application;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.application.dto.SportEventRow;
import com.booster.dday.release.application.dto.SportEventUpsertResult;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.exception.SportSourceUnavailableException;
import com.booster.dday.release.exception.WrongLeagueDataException;
import com.booster.dday.release.infrastructure.SportsDbClient;
import com.booster.dday.release.infrastructure.SportsDbEvent;
import com.booster.dday.release.infrastructure.SportsDbTeam;
import com.booster.dday.sync.application.SyncTask;
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 경기 일정 동기화 — <b>창 누적</b> (SPEC §12.2).
 *
 * <h2>왜 한 번에 시즌 전수를 받지 않나</h2>
 *
 * <p>무료 키가 안 준다. {@code eventsseason.php} 가 <b>5건</b>만 돌려준다 (실측).
 * 그래서 「다음 경기 · 지난 경기」를 10분마다 긁어 쌓는다. 벌크 적재 한 번이 아니라
 * 창을 계속 옮기는 것이다.
 *
 * <p><b>이것이 D-4 와 궁합이 좋다.</b> 같은 경기를 여러 번 보게 되므로 달라진 것을
 * 발견하는 구조가 자연스럽다 — 한 번만 받아 두는 방식이면 우천 순연을 영영 못 본다.
 *
 * <h2>가상 스레드를 쓰지 않는다 — 공휴일과 다르다</h2>
 *
 * <p>공휴일은 (국가 × 연도) 1,020 단위라 동시성이 회차 길이를 정했다. 여기는
 * <b>리그당 2~3 호출</b>이고 1차에 리그가 하나다. 병렬로 묶을 것이 없는데 executor 를
 * 세우면 <b>읽는 사람이 「여기 병렬성이 중요하다」고 오해한다.</b>
 *
 * <h2>키가 없으면 실패가 아니라 건너뜀이다</h2>
 *
 * <p>키 없이 부르면 원천이 401 을 주고, 그것을 실패로 적으면 <b>회차 기록이
 * 「원천이 이상하다」를 뜻하지 않게 된다.</b> 켤 수 없는 것은 꺼진 것으로 보여야
 * 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SportEventSyncTask implements SyncTask {

    private final SportsDbClient client;
    private final SportEventUpsertService upsert;
    private final SportsSyncProperties properties;
    private final Clock clock;

    @Override
    public SyncTarget target() {
        return SyncTarget.SPORT_EVENT;
    }

    @Override
    public SyncOutcome run(long runId) {
        if (!properties.enabled()) {
            log.info("[SportSync] 꺼져 있다 — 키 {} · 리그 {}개",
                    properties.apiKey().isBlank() ? "없음" : "있음", properties.leagues().size());
            return SyncOutcome.of(List.of(SyncRunItem.leagueSkipped(runId, null,
                    "dday.sync.sports.api-key 가 없거나 켠 리그가 없다")));
        }

        List<SyncRunItem> items = new ArrayList<>(properties.leagues().size());
        for (SportsSyncProperties.LeagueSpec spec : properties.leagues()) {
            items.add(syncOne(runId, spec));
        }

        SyncOutcome outcome = SyncOutcome.of(items);
        log.info("[SportSync] 회차 {} 끝 — 성공 {} · 실패 {} · 건너뜀 {}",
                runId, outcome.ok(), outcome.failed(), outcome.skipped());
        return outcome;
    }

    /**
     * 리그 하나. <b>던지지 않는다</b> — 무엇이 되든 {@link SyncRunItem} 한 건이다.
     *
     * <p>순서가 설계다. 리그를 표에 세우고 → (필요하면) 팀을 받고 → 경기를 받고 →
     * 반영한다. <b>받아 오는 구간과 쓰는 구간이 겹치지 않는다</b> — 커넥션을 쥔 채로
     * HTTP 를 기다리지 않기 위해서다 (ARCHITECTURE §5.4).
     */
    private SyncRunItem syncOne(long runId, SportsSyncProperties.LeagueSpec spec) {
        long startedAt = System.nanoTime();
        League league = null;
        try {
            league = upsert.register(spec);

            if (upsert.needsTeamSync(league.getId())) {
                syncTeams(league, spec);
            }

            List<SportsDbEvent> source = fetchEvents(spec);
            List<SportEventRow> rows = translate(source, league.zone());

            SportEventUpsertResult result = upsert.apply(runId, league, rows, Instant.now(clock));

            log.info("[SportSync] {} — 받음 {} · 새로 {} · 바뀜 {} · 그대로 {} · 이력 {} · 스텁 {} · 버림 {}",
                    spec.name(), result.received(), result.created(), result.updated(),
                    result.unchanged(), result.changesRecorded(), result.stubsCreated(),
                    source.size() - rows.size());

            return SyncRunItem.leagueOk(runId, league.getId(), source.size(),
                    result.stored(), elapsedMs(startedAt));

        } catch (SportSourceUnavailableException e) {
            log.warn("[SportSync] {} — 원천을 못 읽었다: {}", spec.name(), e.getMessage());
            return SyncRunItem.leagueFailed(runId, idOf(league), "SOURCE_UNAVAILABLE",
                    e.getMessage(), elapsedMs(startedAt));

        } catch (WrongLeagueDataException e) {
            /* 물어본 리그가 아닌 것이 왔다. 조용히 담으면 야구 리그에 축구팀이
               들어앉고, 그 뒤로는 무엇이 틀렸는지 찾을 단서가 없다 (SPEC §12.3) */
            log.error("[SportSync] {} — {}", spec.name(), e.getMessage());
            return SyncRunItem.leagueFailed(runId, idOf(league), "WRONG_LEAGUE_DATA",
                    e.getMessage(), elapsedMs(startedAt));

        } catch (RuntimeException e) {
            log.error("[SportSync] {} 를 반영하다 터졌다", spec.name(), e);
            return SyncRunItem.leagueFailed(runId, idOf(league), e.getClass().getSimpleName(),
                    e.getMessage(), elapsedMs(startedAt));
        }
    }

    /**
     * 팀 목록. <b>이름으로만 받고, 받은 것이 그 종목인지 확인한다</b> (SPEC §12.3).
     *
     * <p>{@code lookup_all_teams.php?id=} 가 id 를 무시하고 언제나 잉글랜드 3부
     * 24팀을 주는 것을 실측했다. 쓰는 길({@code search_all_teams.php?l=})은 맞게
     * 오지만, <b>확인하지 않으면 그것이 언제 어긋나는지 우리가 모른다.</b>
     */
    private void syncTeams(League league, SportsSyncProperties.LeagueSpec spec) {
        List<SportsDbTeam> sourceTeams = client.teamsOf(spec.name());
        if (sourceTeams.isEmpty()) {
            log.warn("[SportSync] {} 의 팀 목록이 비었다 — 스텁으로 채워질 것이다", spec.name());
            return;
        }

        for (SportsDbTeam team : sourceTeams) {
            if (team.strSport() != null && !team.strSport().equalsIgnoreCase(spec.sport())) {
                throw new WrongLeagueDataException(
                        "팀 목록에 다른 종목이 왔다: " + spec.name() + " (" + spec.sport()
                                + ") 를 물었는데 " + team.strSport() + " 의 "
                                + team.strTeam() + " 이 왔다");
            }
        }
        int stored = upsert.syncTeams(league.getId(), sourceTeams);
        log.info("[SportSync] {} 팀 {}개를 담았다", spec.name(), stored);
    }

    /**
     * 경기를 받아 온다.
     *
     * <p>유료 키가 있다고 설정에 적혀 있으면 시즌 질의로 가고, 없으면 창 누적이다.
     * <b>둘을 합치지 않는다</b> — 무료 키로 시즌을 물으면 5건이 오는데 그것이 에러가
     * 아니라서 «시즌에 5경기» 로 조용히 담긴다 (§12.2).
     */
    private List<SportsDbEvent> fetchEvents(SportsSyncProperties.LeagueSpec spec) {
        if (properties.bulk()) {
            return client.seasonEvents(spec.externalId(), seasonOf(spec));
        }

        List<SportsDbEvent> all = new ArrayList<>();
        all.addAll(client.nextEvents(spec.externalId()));
        all.addAll(client.pastEvents(spec.externalId()));
        return all;
    }

    private String seasonOf(SportsSyncProperties.LeagueSpec spec) {
        if (spec.season() != null && !spec.season().isBlank()) {
            return spec.season();
        }
        /* 야구는 연도 하나가 시즌이다. 유럽 축구는 2025-2026 이라 설정으로 받는다 */
        return String.valueOf(java.time.LocalDate.now(clock).getYear());
    }

    /**
     * 원천의 문자열을 값으로 읽고 <b>같은 경기 id 를 하나로 접는다.</b>
     *
     * <p>다음 · 지난 경기를 합쳐 받으므로 겹칠 수 있다. 접지 않으면 같은 경기를 한
     * 트랜잭션에서 두 번 만지는데, 그때 <b>첫 번째가 만든 {@code DateChange} 를
     * 두 번째가 되돌린다</b> — 이력에 남았는데 실제 값은 원래대로인 상태가 된다.
     *
     * <p>SPEC §12.2 의 검산점 «같은 경기 id 가 두 번 들어오지 않는가» 가 이 자리다.
     */
    private List<SportEventRow> translate(List<SportsDbEvent> source, ZoneId zone) {
        Map<String, SportEventRow> byExternalId = new LinkedHashMap<>();
        for (SportsDbEvent event : source) {
            SportEventRow row = SportEventRow.from(event, zone);
            if (row == null) {
                log.warn("[SportSync] 날짜를 못 읽어 버린다: id={} date={} ts={}",
                        event == null ? null : event.idEvent(),
                        event == null ? null : event.dateEvent(),
                        event == null ? null : event.strTimestamp());
                continue;
            }
            byExternalId.put(row.externalId(), row);
        }

        List<SportEventRow> rows = List.copyOf(byExternalId.values());
        warnSameDaySameTeam(rows, zone);
        return rows;
    }

    /**
     * SPEC §12.2 의 검산점 «같은 날 같은 팀이 두 경기에 나오지 않는가».
     *
     * <p>야구에서 더블헤더가 아니면 있을 수 없는 일이다. 있으면 <b>원천이 경기를
     * 중복으로 준 것이거나 우리가 팀 id 를 잘못 붙인 것</b>이고, 둘 다 조용히
     * 지나가면 찾을 수 없다 — 경기가 하나 더 보이는 것뿐이라 화면에서는 그럴듯하다.
     *
     * <p><b>막지 않고 적기만 한다.</b> 더블헤더가 실제로 있는 리그가 있고, 우리가
     * 그것을 「고장」으로 단정할 근거가 없다. 검산점은 「이럴 리 없다」가 아니라
     * 「이러면 봐야 한다」다.
     */
    private static void warnSameDaySameTeam(List<SportEventRow> rows, ZoneId zone) {
        Map<String, String> seen = new java.util.HashMap<>();
        for (SportEventRow row : rows) {
            if (row.startsAt() == null) {
                continue;
            }
            String day = row.startsAt().atZone(zone).toLocalDate().toString();
            for (String teamId : new String[]{row.homeTeamExternalId(), row.awayTeamExternalId()}) {
                if (teamId == null) {
                    continue;
                }
                String previous = seen.put(teamId + "@" + day, row.externalId());
                if (previous != null) {
                    log.warn("[SportSync] 같은 날 같은 팀이 두 경기에 나온다 — 팀 {} · {} · 경기 {} 와 {}",
                            teamId, day, previous, row.externalId());
                }
            }
        }
    }

    private static Long idOf(League league) {
        return league == null ? null : league.getId();
    }

    private static int elapsedMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
