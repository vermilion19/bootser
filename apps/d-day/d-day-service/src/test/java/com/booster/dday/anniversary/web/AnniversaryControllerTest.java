package com.booster.dday.anniversary.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.anniversary.application.AnniversaryFacade;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LeapPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개인 기념일의 HTTP 표면 — <b>로그인이 필요한 첫 자원.</b>
 *
 * <p>여기서 제일 중요한 것은 <b>헤더가 없으면 401</b> 이다. 게이트웨이가 막아 주기로
 * 돼 있지만 서비스가 그것에만 기대면, 게이트웨이를 거치지 않는 경로가 생기는 날
 * <b>개인 자원이 통째로 열린다.</b>
 */
@WebMvcTest(AnniversaryController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class,
        com.booster.dday.config.WebConfig.class, AnniversaryControllerTest.Fixed.class})
class AnniversaryControllerTest {

    /** 2026-06-15 */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String HEADER = "X-User-Id";

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
    private AnniversaryFacade anniversaryFacade;

    private static Anniversary birthday() {
        return Anniversary.of(1L, "생일", LocalDate.of(1990, 5, 20), CalendarType.SOLAR,
                LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY, CountDirection.D_DAY,
                SEOUL, NotifyOffsets.of(List.of(7)));
    }

    private static final String BODY = """
            {"title":"생일","anchorDate":"1990-05-20","calendarType":"SOLAR",
             "recurrence":"YEARLY","notifyOffsets":[7]}
            """;

    @Nested
    @DisplayName("로그인")
    class Authentication {

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 게이트웨이가 {@code /dday/me/**}
         * 를 게스트 통과에서 빼 두었지만(착수 0), 서비스가 그것에만 기대면 게이트웨이를
         * 거치지 않는 경로가 생기는 날 개인 자원이 통째로 열린다.
         */
        @Test
        @DisplayName("헤더가 없으면 401 이고 응용 계층을 부르지도 않는다")
        void withoutHeaderIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/dday/me/anniversaries"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-AUTH-001"));

            verify(anniversaryFacade, never()).listOf(anyLong(), any());
        }

        @Test
        @DisplayName("헤더가 숫자가 아니어도 401 이다")
        void nonNumericHeaderIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/dday/me/anniversaries").header(HEADER, "나"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("등록도 삭제도 헤더가 있어야 한다")
        void everyEndpointNeedsIt() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/anniversaries")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/v1/dday/me/anniversaries/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("목록 (C-3)")
    class Listing {

        @Test
        @DisplayName("D-day 가 붙어 나간다")
        void listsWithDDay() throws Exception {
            when(anniversaryFacade.listOf(1L, SEOUL)).thenReturn(List.of(
                    new AnniversaryDetail(birthday(), LocalDate.of(2027, 5, 20),
                            List.of(LocalDate.of(2027, 5, 20)))));

            mockMvc.perform(get("/api/v1/dday/me/anniversaries").header(HEADER, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].title").value("생일"))
                    .andExpect(jsonPath("$.data[0].nextDate").value("2027-05-20"))
                    .andExpect(jsonPath("$.data[0].dDay").value(339))
                    .andExpect(jsonPath("$.data[0].zone").value("Asia/Seoul"))
                    .andExpect(jsonPath("$.data[0].notifyOffsets[0]").value(7));
        }

        /**
         * "만난 지 100일" 은 <b>세는 방향이 반대</b>다 (C-8). 부호를 뒤집어 쓰라고
         * 하면 언젠가 누가 뒤집는 것을 잊는다.
         */
        @Test
        @DisplayName("D+N 은 기준일에서 오늘까지 센다")
        void countsForward() throws Exception {
            Anniversary met = Anniversary.of(1L, "만난 날", LocalDate.of(2026, 3, 7),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE,
                    CountDirection.D_PLUS, SEOUL, NotifyOffsets.NONE);

            when(anniversaryFacade.listOf(1L, SEOUL))
                    .thenReturn(List.of(new AnniversaryDetail(met, null, List.of())));

            mockMvc.perform(get("/api/v1/dday/me/anniversaries").header(HEADER, "1"))
                    .andExpect(jsonPath("$.data[0].direction").value("D_PLUS"))
                    .andExpect(jsonPath("$.data[0].dDay").value(100));
        }

        @Test
        @DisplayName("다음이 없으면 D-day 도 없다")
        void noNextNoDDay() throws Exception {
            Anniversary past = Anniversary.of(1L, "지난 일", LocalDate.of(2020, 1, 1),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE,
                    CountDirection.D_DAY, SEOUL, NotifyOffsets.NONE);

            when(anniversaryFacade.listOf(1L, SEOUL))
                    .thenReturn(List.of(new AnniversaryDetail(past, null, List.of())));

            mockMvc.perform(get("/api/v1/dday/me/anniversaries").header(HEADER, "1"))
                    .andExpect(jsonPath("$.data[0].dDay").doesNotExist())
                    .andExpect(jsonPath("$.data[0].nextDate").doesNotExist());
        }
    }

    @Nested
    @DisplayName("등록 · 삭제")
    class Writing {

        @Test
        @DisplayName("등록하면 201 이고 다음 발생일이 같이 온다")
        void registerReturnsCreated() throws Exception {
            when(anniversaryFacade.register(anyLong(), any())).thenReturn(birthday());
            when(anniversaryFacade.listOf(anyLong(), any())).thenReturn(List.of(
                    new AnniversaryDetail(birthday(), LocalDate.of(2027, 5, 20), List.of())));

            mockMvc.perform(post("/api/v1/dday/me/anniversaries")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("생일"));
        }

        @Test
        @DisplayName("남의 것을 지우려 하면 404 다 — 있다는 것도 안 알려 준다")
        void deletingOthersIsNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new CoreException(DDayErrorCode.ANNIVERSARY_NOT_FOUND,
                            "그 기념일이 없다: 99"))
                    .when(anniversaryFacade).remove(anyLong(), anyLong());

            mockMvc.perform(delete("/api/v1/dday/me/anniversaries/99").header(HEADER, "2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-ANNIV-001"));
        }

        @Test
        @DisplayName("모르는 시간대를 조용히 바꾸지 않는다")
        void unknownZoneIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/anniversaries")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"생일","anchorDate":"1990-05-20","zone":"Asia/Seoull"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"));
        }

        @Test
        @DisplayName("모르는 달력 값은 쓸 수 있는 것을 알려 주며 거절한다")
        void unknownEnumTellsWhatIsAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/anniversaries")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"생일","anchorDate":"1990-05-20","calendarType":"MOON"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("LUNAR")));
        }
    }
}
