package com.booster.dday.release.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.release.application.SportQueryService;
import com.booster.dday.release.domain.ChangedField;
import com.booster.dday.release.domain.DateChange;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Team;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.shared.web.DDayErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경기 조회의 HTTP 표면 (D-2 · D-4).
 *
 * <p>여기서 무는 것 둘.
 *
 * <ol>
 *   <li><b>D-day 를 리그 시간대로 세는가.</b> 보는 사람의 기기 날짜로 세면 정확히
 *       E-1 이 고치려던 고장이 된다</li>
 *   <li><b>시각을 모른다는 사실이 응답에 실리는가.</b> 안 실으면 화면이 자정을
 *       경기 시각으로 그린다 (SPEC §9.9(4))</li>
 * </ol>
 */
@WebMvcTest(SportController.class)
@Import({GlobalExceptionHandler.class, SportControllerTest.Fixed.class})
class SportControllerTest {

    /** 2026-09-10 21:00 UTC = KST 2026-09-11 06:00 — <b>날짜가 갈리는 순간</b> */
    private static final Instant NOW = Instant.parse("2026-09-10T21:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        DDayCalculator dDayCalculator() {
            return new ZoneAwareDDayCalculator();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SportQueryService sportQueryService;

    private static League kbo() {
        return League.of("thesportsdb", "4830", "Baseball", "Korean KBO League",
                "KR", SEOUL, true);
    }

    private static SportEvent event(Instant startsAt, boolean timeIsUnknown, boolean postponed) {
        return SportEvent.of("thesportsdb", "2400325", 1L, 10L, 20L,
                "Hanwha Eagles vs NC Dinos", startsAt, timeIsUnknown,
                postponed ? "PPD" : "NS", postponed, "2026",
                "Daejeon Hanbat Baseball Stadium", NOW);
    }

    private static Team team(String name) {
        return Team.of(1L, "thesportsdb", "140001", name);
    }

    @Nested
    @DisplayName("리그 · 팀")
    class Catalog {

        @Test
        @DisplayName("켜 둔 리그가 시간대와 함께 나간다")
        void listsLeagues() throws Exception {
            when(sportQueryService.enabledLeagues()).thenReturn(List.of(kbo()));

            mockMvc.perform(get("/api/v1/dday/leagues"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("Korean KBO League"))
                    .andExpect(jsonPath("$.data[0].sport").value("Baseball"))
                    .andExpect(jsonPath("$.data[0].zoneId").value("Asia/Seoul"));
        }

        @Test
        @DisplayName("없는 리그를 물으면 404 다")
        void unknownLeagueIsNotFound() throws Exception {
            when(sportQueryService.teamsOf(anyLong()))
                    .thenThrow(new CoreException(DDayErrorCode.LEAGUE_NOT_FOUND, "그 리그가 없다: 9"));

            mockMvc.perform(get("/api/v1/dday/leagues/9/teams"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-SPORT-001"));
        }

        /**
         * 스텁을 숨기지 않는다. 이름 자리에 id 가 들어가 있으므로, 화면이 그것을
         * 팀 이름으로 그리면 사용자가 <b>«(999999)» 라는 팀</b>을 보게 된다.
         */
        @Test
        @DisplayName("스텁 팀은 스텁이라고 알려 준다")
        void exposesStubFlag() throws Exception {
            when(sportQueryService.teamsOf(1L)).thenReturn(List.of(
                    Team.stub(1L, "thesportsdb", "999999")));

            mockMvc.perform(get("/api/v1/dday/leagues/1/teams"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].stub").value(true))
                    .andExpect(jsonPath("$.data[0].name").value("(999999)"));
        }
    }

    @Nested
    @DisplayName("경기 (D-2)")
    class Events {

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 지금은 UTC 로 9월 10일이고
         * KST 로 9월 11일이다. 경기는 KST 9월 11일 18:30 이므로 <b>D-0</b> 이어야
         * 한다 — UTC 의 오늘로 세면 D-1 이 나온다.
         */
        @Test
        @DisplayName("D-day 를 리그 시간대의 오늘에서 센다")
        void countsInLeagueZone() throws Exception {
            when(sportQueryService.league(1L)).thenReturn(kbo());
            when(sportQueryService.upcomingOfLeague(anyLong(), anyInt())).thenReturn(
                    new SportQueryService.EventPage(
                            List.of(event(Instant.parse("2026-09-11T09:30:00Z"), false, false)),
                            Map.of(10L, team("Hanwha Eagles"))));

            mockMvc.perform(get("/api/v1/dday/leagues/1/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].localDate").value("2026-09-11"))
                    .andExpect(jsonPath("$.data[0].dDay").value(0))
                    .andExpect(jsonPath("$.data[0].homeTeamName").value("Hanwha Eagles"))
                    .andExpect(jsonPath("$.data[0].startsAt").value("2026-09-11T09:30:00Z"));
        }

        @Test
        @DisplayName("이틀 뒤 경기는 D-2 다")
        void countsAhead() throws Exception {
            when(sportQueryService.league(1L)).thenReturn(kbo());
            when(sportQueryService.upcomingOfLeague(anyLong(), anyInt())).thenReturn(
                    new SportQueryService.EventPage(
                            List.of(event(Instant.parse("2026-09-13T09:30:00Z"), false, false)),
                            Map.of()));

            mockMvc.perform(get("/api/v1/dday/leagues/1/events"))
                    .andExpect(jsonPath("$.data[0].dDay").value(2));
        }

        @Test
        @DisplayName("시각을 모르면 그 사실이 응답에 실린다")
        void exposesUnknownTime() throws Exception {
            when(sportQueryService.league(1L)).thenReturn(kbo());
            when(sportQueryService.upcomingOfLeague(anyLong(), anyInt())).thenReturn(
                    new SportQueryService.EventPage(
                            List.of(event(LocalDate.of(2026, 9, 12)
                                    .atStartOfDay(SEOUL).toInstant(), true, false)),
                            Map.of()));

            mockMvc.perform(get("/api/v1/dday/leagues/1/events"))
                    .andExpect(jsonPath("$.data[0].timeIsUnknown").value(true))
                    .andExpect(jsonPath("$.data[0].localDate").value("2026-09-12"));
        }

        @Test
        @DisplayName("순연된 경기는 순연이라고 나온다")
        void exposesPostponed() throws Exception {
            when(sportQueryService.league(1L)).thenReturn(kbo());
            when(sportQueryService.upcomingOfLeague(anyLong(), anyInt())).thenReturn(
                    new SportQueryService.EventPage(
                            List.of(event(Instant.parse("2026-09-11T09:30:00Z"), false, true)),
                            Map.of()));

            mockMvc.perform(get("/api/v1/dday/leagues/1/events"))
                    .andExpect(jsonPath("$.data[0].postponed").value(true));
        }

        /**
         * 동기화가 한 번도 안 돌았으면 경기가 없다. <b>그것은 정상 상태</b>다 —
         * 창 누적은 아무것도 없는 상태에서 시작한다 (SPEC §12.2).
         */
        @Test
        @DisplayName("경기가 없으면 빈 목록이다 — 404 가 아니다")
        void emptyIsNotAnError() throws Exception {
            when(sportQueryService.league(1L)).thenReturn(kbo());
            when(sportQueryService.upcomingOfLeague(anyLong(), anyInt()))
                    .thenReturn(new SportQueryService.EventPage(List.of(), Map.of()));

            mockMvc.perform(get("/api/v1/dday/leagues/1/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("팀의 경기도 그 리그 시간대로 센다")
        void teamEventsUseLeagueZone() throws Exception {
            when(sportQueryService.upcomingOfTeam(anyLong(), anyInt())).thenReturn(
                    new SportQueryService.EventPage(
                            List.of(event(Instant.parse("2026-09-11T09:30:00Z"), false, false)),
                            Map.of()));
            when(sportQueryService.league(1L)).thenReturn(kbo());

            mockMvc.perform(get("/api/v1/dday/teams/10/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].dDay").value(0));
        }

        @Test
        @DisplayName("없는 팀을 물으면 404 다")
        void unknownTeamIsNotFound() throws Exception {
            when(sportQueryService.upcomingOfTeam(anyLong(), anyInt()))
                    .thenThrow(new CoreException(DDayErrorCode.TEAM_NOT_FOUND, "그 팀이 없다: 9"));

            mockMvc.perform(get("/api/v1/dday/teams/9/events"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-SPORT-002"));
        }
    }

    @Nested
    @DisplayName("변경 이력 (D-4)")
    class Changes {

        /**
         * 이 조회가 있는 까닭은 <b>알림을 놓친 사람</b>이다. 알림은 at-least-once
         * 지만 휴대폰은 꺼져 있을 수 있다.
         */
        @Test
        @DisplayName("무엇이 언제 바뀌었는지 최신순으로 나간다")
        void listsChanges() throws Exception {
            when(sportQueryService.changesOf(100L)).thenReturn(List.of(
                    DateChange.of(SubjectType.SPORT_EVENT, 100L, ChangedField.POSTPONED,
                            "false", "true", Instant.parse("2026-09-11T08:00:00Z"), 7L)));

            mockMvc.perform(get("/api/v1/dday/sport-events/100/changes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].field").value("POSTPONED"))
                    .andExpect(jsonPath("$.data[0].oldValue").value("false"))
                    .andExpect(jsonPath("$.data[0].newValue").value("true"))
                    .andExpect(jsonPath("$.data[0].detectedAt").value("2026-09-11T08:00:00Z"));
        }

        @Test
        @DisplayName("한 번도 안 바뀐 경기는 빈 목록이다")
        void emptyWhenNeverChanged() throws Exception {
            when(sportQueryService.changesOf(anyLong())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/dday/sport-events/100/changes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
