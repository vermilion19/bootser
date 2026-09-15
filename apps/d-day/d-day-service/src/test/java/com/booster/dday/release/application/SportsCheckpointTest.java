package com.booster.dday.release.application;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.release.infrastructure.SportsDbTeam;
import com.booster.dday.shared.outbox.JpaDomainOutbox;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC §12.2 의 검산점.
 *
 * <p>공휴일에는 「시즌 720경기」처럼 셀 수 있는 수가 있었지만 <b>무료 키가
 * 시즌 전수를 안 준다</b> — 시즌 질의에 5건만 온다. 그래서 검산점을 넷으로 바꿨다.
 *
 * <table>
 *   <caption>넷의 현재 상태</caption>
 *   <tr><td>팀이 10개인가</td>
 *       <td><b>여기서 확인한다.</b> SPEC §12.1 이 실측해 이름까지 적어 둔 값이다</td></tr>
 *   <tr><td>같은 경기 id 가 두 번 들어오지 않는가</td>
 *       <td>{@code SportEventSyncTaskTest.foldsDuplicateEventIds}</td></tr>
 *   <tr><td>같은 날 같은 팀이 두 경기에 나오지 않는가</td>
 *       <td>{@code SportEventSyncTask} 가 로그로 적는다 — <b>실제 자료로는 아직 못 쟀다</b></td></tr>
 *   <tr><td>{@code strPostponed} no→yes 가 {@code DateChange} 에 남는가</td>
 *       <td>{@code SportEventUpsertServiceTest.postponementWritesBoth}</td></tr>
 * </table>
 *
 * <h2>⚠ 실제 원천으로는 한 번도 안 돌렸다</h2>
 *
 * <p>API 키가 없다. 여기 쓰는 팀 이름은 <b>SPEC §12.1 이 실측해 적어 둔 열 이름</b>
 * 이고, 팀 <b>id</b> 는 우리가 지어낸 값이다 — id 까지 실측한 것으로 보이면 다음
 * 사람이 그것을 근거로 쓴다. 키가 들어오면 이 테스트의 고정 자료를 실제 응답으로
 * 갈아 끼우는 것이 첫 할 일이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-sport-check;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({SportEventUpsertService.class, JpaDomainOutbox.class, JpaConfig.class})
class SportsCheckpointTest {

    /**
     * SPEC §12.1 이 실측한 열 팀. <b>이름은 실측이고 id 는 지어낸 것이다.</b>
     *
     * <p>이름이 열이라는 것이 검산점이다 — 아홉이나 열하나가 되면 우리가 리그를
     * 잘못 물었거나 원천이 바뀐 것이다.
     */
    private static final List<String> KBO_TEAMS = List.of(
            "Doosan Bears", "Hanwha Eagles", "Kia Tigers", "Kiwoom Heroes",
            "KT Wiz", "LG Twins", "Lotte Giants", "NC Dinos",
            "Samsung Lions", "SSG Landers");

    @Autowired
    private SportEventUpsertService service;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private EntityManager em;

    private static List<SportsDbTeam> sourceTeams() {
        List<SportsDbTeam> source = new ArrayList<>(KBO_TEAMS.size());
        for (int i = 0; i < KBO_TEAMS.size(); i++) {
            source.add(new SportsDbTeam(String.valueOf(140001 + i), KBO_TEAMS.get(i),
                    null, "Baseball", "Korean KBO League"));
        }
        return source;
    }

    /** 검산점 1 — 팀이 10개인가 */
    @Test
    @DisplayName("KBO 팀은 열이고 스텁이 하나도 없다")
    void tenTeamsAndNoStubs() {
        League kbo = service.register(new SportsSyncProperties.LeagueSpec(
                "4830", "Baseball", "Korean KBO League", "KR",
                ZoneId.of("Asia/Seoul"), "2026"));
        em.flush();

        int stored = service.syncTeams(kbo.getId(), sourceTeams());
        em.flush();
        em.clear();

        assertThat(stored).isEqualTo(10);
        assertThat(teams.findAllByLeagueId(kbo.getId())).hasSize(10);
        /* 스텁이 있으면 원천 해석이 새고 있다는 뜻이다 (SCHEMA §1.4) */
        assertThat(teams.countByStubTrue()).isZero();

        assertThat(teams.findAllByLeagueIdOrderByNameAsc(kbo.getId()))
                .extracting(Team::getName)
                .containsExactlyInAnyOrderElementsOf(KBO_TEAMS);
    }

    /**
     * 두 번 받아도 열이다. 창 누적은 팀 목록을 여러 번 받을 수 있으므로
     * (스텁이 남아 있으면 다시 받는다) <b>받는 횟수가 팀 수를 늘리면 안 된다.</b>
     */
    @Test
    @DisplayName("팀 목록을 두 번 받아도 열이다")
    void teamSyncIsIdempotent() {
        League kbo = service.register(new SportsSyncProperties.LeagueSpec(
                "4830", "Baseball", "Korean KBO League", "KR",
                ZoneId.of("Asia/Seoul"), "2026"));
        em.flush();

        service.syncTeams(kbo.getId(), sourceTeams());
        em.flush();
        service.syncTeams(kbo.getId(), sourceTeams());
        em.flush();
        em.clear();

        assertThat(teams.findAllByLeagueId(kbo.getId())).hasSize(10);
    }
}
