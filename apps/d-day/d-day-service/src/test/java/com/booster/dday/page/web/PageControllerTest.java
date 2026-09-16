package com.booster.dday.page.web;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.anniversary.application.AnniversaryFacade;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.HolidayQueryService;
import com.booster.dday.release.application.SportQueryService;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyKind;
import com.booster.dday.sky.application.SkyService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 사람이 보는 화면.
 *
 * <p>여기서 무는 것 셋.
 *
 * <ol>
 *   <li><b>오류가 HTML 로 나가는가.</b> {@code libs} 의 처리기는 JSON 을 내고,
 *       그것이 화면에 걸리면 브라우저에 {@code {"result":"ERROR"}} 가 찍힌다</li>
 *   <li><b>로그인이 필요한 화면이 로그인으로 보내는가.</b> 빈 401 은 흰 화면이다</li>
 *   <li><b>자료가 없을 때 「없다」고 말하는가.</b> 빈 표만 그리면 고장으로 보인다</li>
 * </ol>
 */
@WebMvcTest({PageController.class, AnniversaryPageController.class})
@Import({CurrentMemberArgumentResolver.class, PageErrorAdvice.class,
        com.booster.dday.config.WebConfig.class,
        com.booster.dday.shared.web.AdminOnlyInterceptor.class,
        PageControllerTest.Fixed.class})
class PageControllerTest {

    /** 2026-09-16 */
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
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
    private CountryReader countries;

    @MockitoBean
    private HolidayQueryService holidays;

    @MockitoBean
    private SkyService sky;

    @MockitoBean
    private SportQueryService sports;

    @MockitoBean
    private AnniversaryFacade anniversaries;

    private void emptyWorld() {
        when(countries.find(any())).thenReturn(Optional.of(new CountryView(
                "KR", "Korea, South", "대한민국", "Asia/Seoul", false, (short) 96)));
        when(holidays.upcomingOf(any())).thenReturn(List.of());
        when(sports.enabledLeagues()).thenReturn(List.of());
        when(sky.next(any(), any())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("홈")
    class Home {

        @Test
        @DisplayName("HTML 로 나간다 — JSON 이 아니다")
        void rendersHtml() throws Exception {
            emptyWorld();

            mockMvc.perform(get("/dday"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("page/home"))
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }

        /**
         * 공휴일도 경기도 자료가 없는 것이 지금의 정상이다. 빈 표만 그리면 보는
         * 사람은 우리가 고장 난 줄 안다.
         */
        @Test
        @DisplayName("자료가 없으면 「없다」고 말한다")
        void saysWhenEmpty() throws Exception {
            emptyWorld();

            mockMvc.perform(get("/dday"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("아직 공휴일 자료가 없다")))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("켜 둔 리그가 없다")));
        }

        /**
         * 하늘만은 자료가 없어도 답이 나온다 — 계산이라서다. 공휴일·경기가 빈
         * 화면일 때 이 줄이 서비스가 살아 있다는 것을 보여 준다.
         */
        @Test
        @DisplayName("하늘은 자료 없이도 나온다")
        void skyIsComputed() throws Exception {
            emptyWorld();
            when(sky.next(eq(SkyKind.TERMS), any())).thenReturn(Optional.of(new SkyEvent(
                    "terms", "AUTUMN_EQUINOX", "추분", "Autumn Equinox",
                    Instant.parse("2026-09-23T03:05:00Z"), null, false)));

            mockMvc.perform(get("/dday"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("추분")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("D-7")));
        }

        @Test
        @DisplayName("로그인 안 했으면 로그인 링크가 보인다")
        void showsLoginLink() throws Exception {
            emptyWorld();

            mockMvc.perform(get("/dday"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("/dday/login")));
        }
    }

    @Nested
    @DisplayName("개인 화면")
    class Personal {

        /** <b>이 테스트가 이 파일에서 제일 중요하다.</b> 빈 401 은 흰 화면이다 */
        @Test
        @DisplayName("로그인 안 했으면 로그인으로 보낸다 — 401 이 아니다")
        void redirectsToLogin() throws Exception {
            mockMvc.perform(get("/dday/me/anniversaries"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/dday/login"));

            verify(anniversaries, never()).listOf(anyLong(), any());
        }

        @Test
        @DisplayName("로그인했으면 목록이 나온다")
        void listsMine() throws Exception {
            Anniversary birthday = Anniversary.of(7L, "생일", LocalDate.of(1990, 5, 20),
                    CalendarType.SOLAR, com.booster.dday.sky.api.LeapPolicy.PLAIN_ONLY,
                    Recurrence.YEARLY, CountDirection.D_DAY, SEOUL, NotifyOffsets.of(List.of(7)));

            when(anniversaries.listOf(anyLong(), any())).thenReturn(List.of(
                    new AnniversaryDetail(birthday, LocalDate.of(2027, 5, 20), List.of())));

            mockMvc.perform(get("/dday/me/anniversaries").header(HEADER, "7"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("page/anniversaries"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("생일")));
        }

        /** 게이트웨이가 게스트에게 붙이는 값이다. 회원으로 읽으면 안 된다 */
        @Test
        @DisplayName("게스트(-1)도 로그인으로 보낸다")
        void guestIsRedirected() throws Exception {
            mockMvc.perform(get("/dday/me/anniversaries").header(HEADER, "-1"))
                    .andExpect(redirectedUrl("/dday/login"));
        }

        @Test
        @DisplayName("등록하면 목록으로 돌아간다")
        void registerRedirectsBack() throws Exception {
            mockMvc.perform(post("/dday/me/anniversaries")
                            .header(HEADER, "7")
                            .param("title", "생일")
                            .param("anchorDate", "1990-05-20"))
                    .andExpect(redirectedUrl("/dday/me/anniversaries"));

            verify(anniversaries).register(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("오류")
    class Errors {

        /**
         * {@code libs} 의 처리기는 {@code @RestControllerAdvice} 라 JSON 을 낸다.
         * 그것이 화면에 걸리면 브라우저에 그대로 찍힌다.
         */
        @Test
        @DisplayName("404 가 JSON 이 아니라 HTML 로 나간다")
        void notFoundIsHtml() throws Exception {
            org.mockito.Mockito.doThrow(new CoreException(DDayErrorCode.ANNIVERSARY_NOT_FOUND,
                            "그 기념일이 없다: 99"))
                    .when(anniversaries).remove(anyLong(), anyLong());

            mockMvc.perform(post("/dday/me/anniversaries/99/delete").header(HEADER, "7"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("page/error"))
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("그 기념일이 없다")));
        }
    }

    /**
     * <b>띄워 보고 찾았다.</b> 뷰 이름만 돌려주면 본문은 오류인데 응답이 200 이다 —
     * 브라우저에는 그럴듯해 보이지만 <b>크롤러도 모니터링도 「잘 됐다」로 읽는다.</b>
     */
    @Nested
    @DisplayName("상태 코드")
    class StatusCodes {

        @Test
        @DisplayName("오류 화면이 200 으로 나가지 않는다")
        void errorPageKeepsItsStatus() throws Exception {
            org.mockito.Mockito.doThrow(new CoreException(DDayErrorCode.ANNIVERSARY_NOT_FOUND,
                            "그 기념일이 없다: 99"))
                    .when(anniversaries).remove(anyLong(), anyLong());

            mockMvc.perform(post("/dday/me/anniversaries/99/delete").header(HEADER, "7"))
                    .andExpect(status().isNotFound());
        }
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
