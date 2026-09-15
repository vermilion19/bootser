package com.booster.dday.release.application;

import com.booster.dday.config.SportsSyncProperties;
import com.booster.dday.release.application.dto.SportEventRow;
import com.booster.dday.release.application.dto.SportEventUpsertResult;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.exception.SportSourceUnavailableException;
import com.booster.dday.release.infrastructure.SportsDbClient;
import com.booster.dday.release.infrastructure.SportsDbEvent;
import com.booster.dday.release.infrastructure.SportsDbTeam;
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncItemStatus;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 한 회차가 무엇을 부르고 무엇을 안 부르는가.
 *
 * <p>DB 도 HTTP 도 없다. 여기서 무는 것은 <b>순서와 가름</b>이다 — 키가 없을 때
 * 「실패」가 아니라 「건너뜀」인지, 같은 경기가 두 번 들어올 때 접히는지, 다른
 * 종목이 왔을 때 담기 전에 멈추는지.
 */
class SportEventSyncTaskTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SportsDbClient client;
    private SportEventUpsertService upsert;
    private League kbo;

    @BeforeEach
    void setUp() {
        client = mock(SportsDbClient.class);
        upsert = mock(SportEventUpsertService.class);

        kbo = League.of("thesportsdb", "4830", "Baseball", "Korean KBO League",
                "KR", ZoneId.of("Asia/Seoul"), true);
        when(upsert.register(any())).thenReturn(kbo);
        when(upsert.needsTeamSync(any())).thenReturn(false);
        when(upsert.apply(anyLong(), any(), any(), any()))
                .thenReturn(SportEventUpsertResult.empty());
    }

    private static SportsSyncProperties.LeagueSpec kboSpec() {
        return new SportsSyncProperties.LeagueSpec("4830", "Baseball",
                "Korean KBO League", "KR", ZoneId.of("Asia/Seoul"), "2026");
    }

    private SportEventSyncTask task(String apiKey, boolean bulk) {
        SportsSyncProperties properties = new SportsSyncProperties(
                null, apiKey, bulk, List.of(kboSpec()), null,
                Duration.ofSeconds(1), Duration.ofSeconds(3));

        return new SportEventSyncTask(client, upsert, properties, CLOCK);
    }

    private static SportsDbEvent event(String id, String date, String time) {
        return new SportsDbEvent(id, "Hanwha Eagles vs NC Dinos", date, time,
                date + "T" + (time == null ? "09:30:00" : time), "NS", "no",
                "2026", "Daejeon Hanbat Baseball Stadium", "4830", "140001", "140002");
    }

    @Test
    @DisplayName("맡은 대상은 경기다")
    void targetsSportEvent() {
        assertThat(task("3", false).target()).isEqualTo(SyncTarget.SPORT_EVENT);
    }

    @Nested
    @DisplayName("꺼져 있을 때")
    class Disabled {

        /**
         * <b>실패가 아니라 건너뜀이다.</b> 키 없이 부르면 원천이 401 을 주고,
         * 그것을 실패로 적으면 회차 기록이 「원천이 이상하다」를 뜻하지 않게 된다.
         */
        @Test
        @DisplayName("키가 없으면 원천을 부르지도 않고 건너뜀으로 적는다")
        void withoutKeyItSkips() {
            SyncOutcome outcome = task("", false).run(1L);

            assertThat(outcome.skipped()).isEqualTo(1);
            assertThat(outcome.failed()).isZero();
            verifyNoInteractions(client);
            verify(upsert, never()).apply(anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("리그가 없어도 건너뜀이다")
        void withoutLeaguesItSkips() {
            SportsSyncProperties empty = new SportsSyncProperties(
                    null, "3", false, List.of(), null, null, null);

            SyncOutcome outcome =
                    new SportEventSyncTask(client, upsert, empty, CLOCK).run(1L);

            assertThat(outcome.skipped()).isEqualTo(1);
            verifyNoInteractions(client);
        }
    }

    @Nested
    @DisplayName("창 누적")
    class Window {

        /**
         * 무료 키는 시즌 전수를 안 준다 (SPEC §12.2). <b>다음 · 지난 경기</b>를
         * 긁어 쌓는다.
         */
        @Test
        @DisplayName("무료 키면 다음 · 지난 경기를 부르고 시즌 질의는 안 부른다")
        void freeKeyUsesWindow() {
            when(client.nextEvents("4830")).thenReturn(List.of(event("1", "2026-09-11", "09:30:00")));
            when(client.pastEvents("4830")).thenReturn(List.of(event("2", "2026-09-09", "09:30:00")));

            task("3", false).run(1L);

            verify(client).nextEvents("4830");
            verify(client).pastEvents("4830");
            verify(client, never()).seasonEvents(anyString(), anyString());
        }

        @Test
        @DisplayName("유료 키라고 적혀 있으면 시즌 질의로 간다")
        void paidKeyUsesSeason() {
            when(client.seasonEvents("4830", "2026")).thenReturn(List.of());

            task("paid", true).run(1L);

            verify(client).seasonEvents("4830", "2026");
            verify(client, never()).nextEvents(anyString());
        }

        /**
         * <b>SPEC §12.2 의 검산점 「같은 경기 id 가 두 번 들어오지 않는가」가
         * 이 자리다.</b> 접지 않으면 한 트랜잭션에서 같은 경기를 두 번 만지고,
         * 첫 번째가 만든 이력을 두 번째가 되돌린다.
         */
        @Test
        @DisplayName("다음과 지난에 같은 경기가 있으면 하나로 접는다")
        void foldsDuplicateEventIds() {
            when(client.nextEvents("4830")).thenReturn(List.of(event("2400325", "2026-09-11", "09:30:00")));
            when(client.pastEvents("4830")).thenReturn(List.of(event("2400325", "2026-09-11", "09:30:00")));

            task("3", false).run(1L);

            ArgumentCaptor<List<SportEventRow>> rows = captor();
            verify(upsert).apply(anyLong(), eq(kbo), rows.capture(), any());

            assertThat(rows.getValue()).hasSize(1);
            assertThat(rows.getValue().get(0).externalId()).isEqualTo("2400325");
        }

        @Test
        @DisplayName("날짜를 못 읽는 건은 버리고 나머지는 담는다")
        void dropsUnreadableRows() {
            when(client.nextEvents("4830")).thenReturn(List.of(
                    event("2400325", "2026-09-11", "09:30:00"),
                    new SportsDbEvent("2400326", "x", null, null, null, "NS", "no",
                            "2026", null, "4830", null, null)));
            when(client.pastEvents("4830")).thenReturn(List.of());

            task("3", false).run(1L);

            ArgumentCaptor<List<SportEventRow>> rows = captor();
            verify(upsert).apply(anyLong(), eq(kbo), rows.capture(), any());

            assertThat(rows.getValue()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("팀 목록")
    class Teams {

        @Test
        @DisplayName("담긴 팀이 없으면 이름으로 받아 온다 — id 로 받지 않는다")
        void fetchesByName() {
            when(upsert.needsTeamSync(any())).thenReturn(true);
            when(client.teamsOf("Korean KBO League")).thenReturn(List.of(new SportsDbTeam(
                    "140001", "Hanwha Eagles", null, "Baseball", "Korean KBO League")));

            task("3", false).run(1L);

            verify(client).teamsOf("Korean KBO League");
            verify(upsert).syncTeams(eq(kbo.getId()), any());
        }

        @Test
        @DisplayName("다 담겼으면 팀을 부르지 않는다")
        void skipsWhenComplete() {
            task("3", false).run(1L);

            verify(client, never()).teamsOf(anyString());
        }

        /**
         * <b>SPEC §12.3 이 실측한 함정을 여기서 막는다.</b> 다른 종목이 오면
         * 담지 않고 회차를 실패로 적는다 — 조용히 담으면 야구 리그에 축구팀이
         * 들어앉고, 그 뒤로는 무엇이 틀렸는지 찾을 단서가 없다.
         */
        @Test
        @DisplayName("다른 종목의 팀이 오면 담지 않고 실패로 적는다")
        void rejectsWrongSport() {
            when(upsert.needsTeamSync(any())).thenReturn(true);
            when(client.teamsOf("Korean KBO League")).thenReturn(List.of(new SportsDbTeam(
                    "133604", "Barnsley", null, "Soccer", "English League 1")));

            SyncOutcome outcome = task("3", false).run(1L);

            verify(upsert, never()).syncTeams(anyLong(), any());
            verify(upsert, never()).apply(anyLong(), any(), any(), any());

            assertThat(outcome.failed()).isEqualTo(1);
            SyncRunItem item = outcome.items().get(0);
            assertThat(item.getStatus()).isEqualTo(SyncItemStatus.FAILED);
            assertThat(item.getErrorCode()).isEqualTo("WRONG_LEAGUE_DATA");
            assertThat(item.getErrorMessage()).contains("Soccer");
        }

        @Test
        @DisplayName("팀 목록이 비면 그냥 넘어간다 — 경기는 스텁으로 담힌다")
        void emptyTeamListIsNotAFailure() {
            when(upsert.needsTeamSync(any())).thenReturn(true);
            when(client.teamsOf(anyString())).thenReturn(List.of());

            SyncOutcome outcome = task("3", false).run(1L);

            assertThat(outcome.ok()).isEqualTo(1);
            verify(upsert, never()).syncTeams(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("실패")
    class Failures {

        @Test
        @DisplayName("원천을 못 읽으면 그 리그만 실패로 적고 던지지 않는다")
        void sourceFailureBecomesAnItem() {
            when(client.nextEvents("4830"))
                    .thenThrow(new SportSourceUnavailableException("타임아웃", null));

            SyncOutcome outcome = task("3", false).run(1L);

            assertThat(outcome.failed()).isEqualTo(1);
            assertThat(outcome.items().get(0).getErrorCode()).isEqualTo("SOURCE_UNAVAILABLE");
        }

        @Test
        @DisplayName("우리 쪽 실수여도 회차를 통째로 잃지 않는다")
        void ourBugBecomesAnItem() {
            when(upsert.apply(anyLong(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("내 실수"));

            SyncOutcome outcome = task("3", false).run(1L);

            assertThat(outcome.failed()).isEqualTo(1);
            assertThat(outcome.items().get(0).getErrorCode()).isEqualTo("IllegalStateException");
        }
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<SportEventRow>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
