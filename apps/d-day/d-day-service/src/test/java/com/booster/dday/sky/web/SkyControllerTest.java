package com.booster.dday.sky.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
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
import org.springframework.http.HttpHeaders;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이 서비스의 <b>첫 번째 HTTP 표면</b>. 실제로 나가는 JSON 을 본다.
 *
 * <p>여기서 무는 것은 셋이다. <b>D-day 가 응답에서 만들어지는가</b>(캐시에 든 것은
 * UTC 시각뿐이다), <b>시간대를 주면 그 나라 날짜로 답하는가</b>(E-1), 그리고
 * <b>정의역 밖을 본문에 범위를 실어 거절하는가</b>.
 */
@WebMvcTest(SkyController.class)
@Import({GlobalExceptionHandler.class, SkyControllerTest.Fixed.class})
class SkyControllerTest {

    /** 2026-06-15. 「다음」과 D-day 가 오늘에 달렸으므로 시계를 고정한다 */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

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
    private SkyService skyService;

    /** 2026년 하지 — 한국 시각으로 6월 21일 */
    private static SkyEvent summerSolstice() {
        return new SkyEvent("terms", "하지", "하지", "Summer Solstice",
                Instant.parse("2026-06-21T08:24:00Z"), null, false);
    }

    @Nested
    @DisplayName("절기")
    class Terms {

        @Test
        @DisplayName("목록이 나가고 D-day 가 붙는다")
        void listsTermsWithDDay() throws Exception {
            when(skyService.terms(2026)).thenReturn(List.of(summerSolstice()));

            mockMvc.perform(get("/api/v1/dday/sky/terms").param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].code").value("하지"))
                    .andExpect(jsonPath("$.data[0].date").value("2026-06-21"))
                    .andExpect(jsonPath("$.data[0].dDay").value(6))
                    .andExpect(jsonPath("$.data[0].zone").value("Asia/Seoul"));
        }

        /**
         * <b>이 테스트가 E-1 이다.</b> 같은 순간인데 시간대가 다르면 날짜가 다르고,
         * 날짜가 다르면 D-day 도 다르다. 정적 사이트가 못 고친 고장이 이것이다.
         */
        @Test
        @DisplayName("시간대를 주면 그 나라 날짜로 답한다")
        void respectsRequestedZone() throws Exception {
            when(skyService.terms(2026)).thenReturn(List.of(summerSolstice()));

            /* 하와이(UTC-10)에서는 아직 6월 20일이다 */
            mockMvc.perform(get("/api/v1/dday/sky/terms")
                            .param("year", "2026")
                            .param("zone", "Pacific/Honolulu"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].date").value("2026-06-20"))
                    .andExpect(jsonPath("$.data[0].zone").value("Pacific/Honolulu"));
        }

        @Test
        @DisplayName("영어로 달라고 하면 영어 이름이 나간다")
        void honoursLanguage() throws Exception {
            when(skyService.terms(2026)).thenReturn(List.of(summerSolstice()));

            mockMvc.perform(get("/api/v1/dday/sky/terms")
                            .param("year", "2026")
                            .param("lang", "en"))
                    .andExpect(jsonPath("$.data[0].name").value("Summer Solstice"));

            mockMvc.perform(get("/api/v1/dday/sky/terms")
                            .param("year", "2026")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                    .andExpect(jsonPath("$.data[0].name").value("Summer Solstice"));
        }

        @Test
        @DisplayName("?lang= 이 Accept-Language 를 덮는다")
        void queryParamWins() throws Exception {
            when(skyService.terms(2026)).thenReturn(List.of(summerSolstice()));

            mockMvc.perform(get("/api/v1/dday/sky/terms")
                            .param("year", "2026")
                            .param("lang", "ko")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(jsonPath("$.data[0].name").value("하지"));
        }
    }

    @Nested
    @DisplayName("다음")
    class Next {

        @Test
        @DisplayName("다음 절기 하나와 D-day")
        void nextTerm() throws Exception {
            when(skyService.next(any(SkyKind.class), any(ZoneId.class)))
                    .thenReturn(Optional.of(summerSolstice()));

            mockMvc.perform(get("/api/v1/dday/sky/terms/next"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.code").value("하지"))
                    .andExpect(jsonPath("$.data.dDay").value(6));
        }

        /**
         * 없는 것이 404 가 아니다. <b>물어본 것(다음이 무엇인가)에 «없다» 로 답한
         * 것</b>이지 자원이 없는 것이 아니다.
         */
        @Test
        @DisplayName("다음이 없으면 빈 성공이다 — 404 가 아니다")
        void emptyNextIsStillSuccess() throws Exception {
            when(skyService.next(any(SkyKind.class), any(ZoneId.class))).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/dday/sky/meteors/next"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("음력")
    class Lunar {

        @Test
        @DisplayName("양력을 주면 음력을 준다 — 기준 자오선도 같이")
        void solarToLunar() throws Exception {
            when(skyService.toLunar(any(LocalDate.class), any(ZoneId.class)))
                    .thenReturn(LunarDate.of(2026, 1, 1));

            mockMvc.perform(get("/api/v1/dday/sky/lunar").param("date", "2026-02-17"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.solar").value("2026-02-17"))
                    .andExpect(jsonPath("$.data.lunarMonth").value(1))
                    .andExpect(jsonPath("$.data.lunarDay").value(1))
                    .andExpect(jsonPath("$.data.meridian")
                            .value("Asia/Seoul"));
        }

        @Test
        @DisplayName("음력을 주면 양력을 준다")
        void lunarToSolar() throws Exception {
            when(skyService.toSolar(any(LunarDate.class), any(ZoneId.class)))
                    .thenReturn(Optional.of(LocalDate.of(2026, 2, 17)));

            mockMvc.perform(get("/api/v1/dday/sky/lunar/to-solar")
                            .param("year", "2026").param("month", "1").param("day", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.solar").value("2026-02-17"));
        }

        /**
         * 있는 척 가까운 날을 돌려주지 않는다. 윤달이 아닌 해에 윤달을 물었거나
         * 29일까지인 달의 30일을 물은 것이고, <b>그 사실이 답</b>이다.
         */
        @Test
        @DisplayName("없는 음력 날짜는 404 다")
        void missingLunarDateIsNotFound() throws Exception {
            when(skyService.toSolar(any(LunarDate.class), any(ZoneId.class)))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/dday/sky/lunar/to-solar")
                            .param("year", "2026").param("month", "5").param("day", "1")
                            .param("leap", "true"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-SKY-003"));
        }

        @Test
        @DisplayName("날짜 모양이 틀리면 400 이다")
        void badDateIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/dday/sky/lunar").param("date", "2026년 2월 17일"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"));
        }
    }

    @Nested
    @DisplayName("막는 것")
    class Guards {

        /**
         * 범위를 <b>본문에 실어</b> 거절한다. 서버가 조용히 범위를 정해 놓고
         * 안 알리면 그것이 고장이다 (SPEC §9.9(4)).
         */
        @Test
        @DisplayName("정의역 밖 연도는 400 이고 허용 범위를 알려 준다")
        void yearOutOfRange() throws Exception {
            when(skyService.terms(anyInt())).thenThrow(new CoreException(
                    DDayErrorCode.SKY_YEAR_OUT_OF_RANGE, "3000년은 다루지 않는다. 1583~2999 안의 해를 달라"));

            mockMvc.perform(get("/api/v1/dday/sky/terms").param("year", "3000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-SKY-001"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("1583~2999")));
        }

        /**
         * 조용히 한국으로 바꿔 주면 사용자는 <b>자기가 물은 시간대의 답을 받았다고
         * 믿는다.</b> E-1 이 고치려던 「조용히 하루 어긋남」을 자리만 옮겨 다시
         * 만드는 셈이다.
         */
        @Test
        @DisplayName("모르는 시간대는 400 이다 — 조용히 기본값으로 바꾸지 않는다")
        void unknownZoneIsRejected() throws Exception {
            when(skyService.terms(2026)).thenReturn(List.of(summerSolstice()));

            mockMvc.perform(get("/api/v1/dday/sky/terms")
                            .param("year", "2026")
                            .param("zone", "Asia/Seoull"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"));
        }
    }
}
