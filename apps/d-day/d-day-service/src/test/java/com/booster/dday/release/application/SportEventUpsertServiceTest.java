package com.booster.dday.release.application;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.application.dto.SportEventRow;
import com.booster.dday.release.application.dto.SportEventUpsertResult;
import com.booster.dday.release.domain.ChangedField;
import com.booster.dday.release.domain.DateChange;
import com.booster.dday.release.domain.DateChangeRepository;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.SportEventRepository;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.release.infrastructure.SportsDbTeam;
import com.booster.dday.shared.outbox.JpaDomainOutbox;
import com.booster.dday.shared.outbox.OutboxEvent;
import com.booster.dday.shared.outbox.OutboxEventRepository;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경기를 담는 한 트랜잭션 (ARCHITECTURE §3.5).
 *
 * <p>여기서 무는 것이 <b>D-4 의 전부</b>다.
 *
 * <ol>
 *   <li><b>안 바뀐 것을 다시 쓰지 않는가.</b> 창 누적은 같은 경기를 하루에 여러 번
 *       다시 본다 (SPEC §12.2) — 매번 쓰면 {@code updated_at} 이 흔들려
 *       「언제 바뀌었나」를 못 믿게 된다</li>
 *   <li><b>바뀌면 이력과 Outbox 가 <u>같이</u> 생기는가.</b> 둘이 갈라지면
 *       「이력에는 있는데 알림은 안 갔다」 또는 그 반대가 된다</li>
 *   <li><b>모르는 팀을 만나도 경기를 버리지 않는가</b> (SCHEMA §1.4)</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-sport;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({SportEventUpsertService.class, JpaDomainOutbox.class, JpaConfig.class})
class SportEventUpsertServiceTest {

    private static final long RUN = 7L;
    private static final Instant SEEN = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-11T09:30:00Z");

    @Autowired
    private SportEventUpsertService service;

    @Autowired
    private SportEventRepository events;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private DateChangeRepository dateChanges;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private EntityManager em;

    private void flush() {
        em.flush();
        em.clear();
    }

    private League kboLeague() {
        League league = service.register(new SportsSyncProperties.LeagueSpec(
                "4830", "Baseball", "Korean KBO League", "KR",
                ZoneId.of("Asia/Seoul"), "2026"));
        flush();
        return league;
    }

    private static SportEventRow row(String externalId, Instant startsAt, boolean postponed,
                                     String homeId, String awayId) {
        return new SportEventRow(externalId, "Hanwha Eagles vs NC Dinos", startsAt,
                false, postponed ? "PPD" : "NS", postponed, "2026",
                "Daejeon Hanbat Baseball Stadium", homeId, awayId);
    }

    @Nested
    @DisplayName("리그")
    class Leagues {

        @Test
        @DisplayName("두 번 등록해도 리그는 하나다 — id 가 바뀌면 경기가 고아가 된다")
        void registerIsIdempotent() {
            League first = kboLeague();
            League second = kboLeague();

            assertThat(second.getId()).isEqualTo(first.getId());
        }

        @Test
        @DisplayName("이름이 바뀌면 갱신하고 새로 만들지 않는다")
        void refreshesInsteadOfRecreating() {
            League first = kboLeague();

            League renamed = service.register(new SportsSyncProperties.LeagueSpec(
                    "4830", "Baseball", "KBO 리그", "KR", ZoneId.of("Asia/Seoul"), "2026"));
            flush();

            assertThat(renamed.getId()).isEqualTo(first.getId());
            assertThat(renamed.getName()).isEqualTo("KBO 리그");
        }
    }

    @Nested
    @DisplayName("팀")
    class Teams {

        @Test
        @DisplayName("담긴 팀이 없으면 받아 와야 한다고 답한다")
        void needsSyncWhenEmpty() {
            League league = kboLeague();

            assertThat(service.needsTeamSync(league.getId())).isTrue();
        }

        @Test
        @DisplayName("다 담겼으면 다시 받지 않는다 — 10분마다 받을 것이 아니다")
        void noSyncWhenComplete() {
            League league = kboLeague();
            service.syncTeams(league.getId(), List.of(
                    new SportsDbTeam("140001", "Hanwha Eagles", null, "Baseball", "Korean KBO League"),
                    new SportsDbTeam("140002", "NC Dinos", null, "Baseball", "Korean KBO League")));
            flush();

            assertThat(service.needsTeamSync(league.getId())).isFalse();
        }

        /**
         * 스텁이 남아 있으면 다시 받는다. 스텁으로 남겨 두면 «신호» 가 영영 켜져
         * 있고, 그러면 아무도 그 신호를 안 본다.
         */
        @Test
        @DisplayName("스텁이 남아 있으면 다시 받는다")
        void syncsAgainWhileStubsRemain() {
            League league = kboLeague();
            teams.save(Team.stub(league.getId(), SportEventUpsertService.SOURCE, "999999"));
            flush();

            assertThat(service.needsTeamSync(league.getId())).isTrue();
        }

        @Test
        @DisplayName("이름을 알게 되면 스텁이 풀린다")
        void stubIsResolved() {
            League league = kboLeague();
            teams.save(Team.stub(league.getId(), SportEventUpsertService.SOURCE, "140001"));
            flush();

            service.syncTeams(league.getId(), List.of(new SportsDbTeam(
                    "140001", "Hanwha Eagles", null, "Baseball", "Korean KBO League")));
            flush();

            Team resolved = teams.findBySourceAndExternalId(
                    SportEventUpsertService.SOURCE, "140001").orElseThrow();
            assertThat(resolved.isStub()).isFalse();
            assertThat(resolved.getName()).isEqualTo("Hanwha Eagles");
            assertThat(teams.countByStubTrue()).isZero();
        }
    }

    @Nested
    @DisplayName("경기")
    class Events {

        @Test
        @DisplayName("처음 보는 경기는 새로 담는다")
        void createsNewEvent() {
            League league = kboLeague();

            SportEventUpsertResult result = service.apply(RUN, league,
                    List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            assertThat(result.created()).isEqualTo(1);
            assertThat(result.changesRecorded()).isZero();
            assertThat(events.findBySourceAndExternalId(
                    SportEventUpsertService.SOURCE, "2400325")).isPresent();
        }

        /**
         * <b>새로 담긴 경기는 이력도 이벤트도 안 남긴다.</b> 「처음 알게 됐다」는
         * 일정 변경이 아니다 — 그것을 변경으로 적으면 창 누적이 경기를 발견할
         * 때마다 모든 관심자에게 「일정이 바뀌었다」가 나간다.
         */
        @Test
        @DisplayName("새 경기는 변경 이력을 남기지 않는다")
        void newEventIsNotAChange() {
            League league = kboLeague();

            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            assertThat(dateChanges.findAll()).isEmpty();
            assertThat(outbox.findAll()).isEmpty();
        }

        @Test
        @DisplayName("같은 것을 또 받으면 그대로로 센다 — 이것이 정상이다")
        void secondPollIsUnchanged() {
            League league = kboLeague();
            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            SportEventUpsertResult second = service.apply(RUN + 1, league,
                    List.of(row("2400325", STARTS, false, null, null)),
                    Instant.parse("2026-09-10T00:10:00Z"));
            flush();

            assertThat(second.unchanged()).isEqualTo(1);
            assertThat(second.updated()).isZero();
            assertThat(second.created()).isZero();
            assertThat(dateChanges.findAll()).isEmpty();
        }

        /** <b>이 테스트가 D-4 다.</b> 이력과 Outbox 가 같이 생겨야 한다 */
        @Test
        @DisplayName("순연되면 이력과 Outbox 가 같이 생긴다")
        void postponementWritesBoth() {
            League league = kboLeague();
            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            Instant detected = Instant.parse("2026-09-11T08:00:00Z");
            SportEventUpsertResult result = service.apply(RUN + 1, league,
                    List.of(row("2400325", STARTS, true, null, null)), detected);
            flush();

            assertThat(result.updated()).isEqualTo(1);
            assertThat(result.changesRecorded()).isEqualTo(1);

            List<DateChange> history = dateChanges.findAll();
            assertThat(history).hasSize(1);
            assertThat(history.get(0).getField()).isEqualTo(ChangedField.POSTPONED);
            assertThat(history.get(0).getSubjectType()).isEqualTo(SubjectType.SPORT_EVENT);
            assertThat(history.get(0).getSyncRunId()).isEqualTo(RUN + 1);
            assertThat(history.get(0).getDetectedAt()).isEqualTo(detected);

            List<OutboxEvent> published = outbox.findAll();
            assertThat(published).hasSize(1);
            /* 파티션 키는 외부 경기 id 다 — 같은 경기의 변경이 순서대로 (§3.5) */
            assertThat(published.get(0).getPartitionKey()).isEqualTo("2400325");
            assertThat(published.get(0).getPayload()).contains("POSTPONED");
        }

        /**
         * 순연이 풀렸다 다시 걸리는 일이 야구에서는 일상이다 (SPEC §12.1).
         * 멱등키에 탐지한 날이 들어 있으므로 <b>다른 날의 같은 전이는 살아남는다</b> —
         * 넣지 않았을 때 조용히 사라지던 것이 이 자리다.
         */
        @Test
        @DisplayName("며칠 뒤에 같은 전이가 또 일어나면 그것도 남는다")
        void repeatedTransitionOnAnotherDaySurvives() {
            League league = kboLeague();
            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            service.apply(RUN + 1, league, List.of(row("2400325", STARTS, true, null, null)),
                    Instant.parse("2026-09-11T08:00:00Z"));
            flush();
            service.apply(RUN + 2, league, List.of(row("2400325", STARTS, false, null, null)),
                    Instant.parse("2026-09-12T08:00:00Z"));
            flush();
            service.apply(RUN + 3, league, List.of(row("2400325", STARTS, true, null, null)),
                    Instant.parse("2026-09-13T08:00:00Z"));
            flush();

            assertThat(dateChanges.findAll()).hasSize(3);
            assertThat(outbox.findAll()).hasSize(3);
        }

        /**
         * 같은 날 같은 전이가 두 번 오는 것은 우리 쪽 재실행일 때뿐이고, 그때는
         * <b>걸러져야 한다.</b> 이력은 남고 Outbox 만 접힌다 — 중복 제거는
         * {@code outbox_event.idempotency_key} 의 일이다 (SCHEMA §6.3).
         */
        @Test
        @DisplayName("같은 날 같은 전이는 Outbox 에서 한 번만 나간다")
        void sameDayRepeatIsFolded() {
            League league = kboLeague();
            Instant sameDay = Instant.parse("2026-09-11T08:00:00Z");

            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();
            service.apply(RUN + 1, league, List.of(row("2400325", STARTS, true, null, null)),
                    sameDay);
            flush();
            /* 되돌렸다가 같은 날 다시 — 재실행이 만들 수 있는 모양이다 */
            service.apply(RUN + 2, league, List.of(row("2400325", STARTS, false, null, null)),
                    sameDay);
            flush();
            service.apply(RUN + 3, league, List.of(row("2400325", STARTS, true, null, null)),
                    sameDay);
            flush();

            assertThat(dateChanges.findAll()).hasSize(3);
            assertThat(outbox.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("모르는 팀 (SCHEMA §1.4)")
    class UnknownTeams {

        /**
         * 경기를 버리면 <b>원천에 있는 경기가 우리에게 없게</b> 되고, D-day 를
         * 물어본 사람은 그 이유를 알 수 없다.
         */
        @Test
        @DisplayName("모르는 팀 id 를 만나면 스텁을 만들고 경기는 담는다")
        void createsStubAndKeepsEvent() {
            League league = kboLeague();

            SportEventUpsertResult result = service.apply(RUN, league,
                    List.of(row("2400325", STARTS, false, "140001", "140002")), SEEN);
            flush();

            assertThat(result.created()).isEqualTo(1);
            assertThat(result.stubsCreated()).isEqualTo(2);
            assertThat(teams.countByStubTrue()).isEqualTo(2);

            SportEvent stored = events.findBySourceAndExternalId(
                    SportEventUpsertService.SOURCE, "2400325").orElseThrow();
            assertThat(stored.getHomeTeamId()).isNotNull();
            assertThat(stored.getAwayTeamId()).isNotNull();
        }

        @Test
        @DisplayName("같은 팀이 두 경기에 나와도 스텁은 하나다")
        void stubIsCreatedOnce() {
            League league = kboLeague();

            SportEventUpsertResult result = service.apply(RUN, league, List.of(
                    row("2400325", STARTS, false, "140001", "140002"),
                    row("2400326", STARTS.plusSeconds(86400), false, "140001", "140003")), SEEN);
            flush();

            assertThat(result.stubsCreated()).isEqualTo(3);
            assertThat(teams.countByStubTrue()).isEqualTo(3);
        }

        @Test
        @DisplayName("팀을 모르는 경기도 담는다 — 원천이 id 를 안 줄 때가 있다")
        void keepsEventWithoutTeams() {
            League league = kboLeague();

            service.apply(RUN, league, List.of(row("2400325", STARTS, false, null, null)), SEEN);
            flush();

            SportEvent stored = events.findBySourceAndExternalId(
                    SportEventUpsertService.SOURCE, "2400325").orElseThrow();
            assertThat(stored.getHomeTeamId()).isNull();
            assertThat(teams.countByStubTrue()).isZero();
        }
    }
}
