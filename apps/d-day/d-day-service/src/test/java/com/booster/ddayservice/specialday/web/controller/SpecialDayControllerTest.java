package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.config.JacksonConfig;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.exception.SpecialDayExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpecialDayController.class)
@Import({SpecialDayExceptionHandler.class, JacksonConfig.class})
class SpecialDayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpecialDayService specialDayService;

    @Test
    @DisplayName("GET /today - 정상 응답을 반환한다")
    void should_returnTodayResponse_when_validRequest() throws Exception {
        // given
        LocalDate today = LocalDate.of(2026, 1, 1);
        TodayResult result = new TodayResult(
                today, "KR", true,
                List.of(new TodayResult.SpecialDayItem("신정", SpecialDayCategory.PUBLIC_HOLIDAY, "New Year's Day")),
                new TodayResult.UpcomingItem("삼일절", LocalDate.of(2026, 3, 1), 59, SpecialDayCategory.PUBLIC_HOLIDAY)
        );

        given(specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL)).willReturn(result);

        // when & then
        mockMvc.perform(get("/api/v1/special-days/today")
                        .param("countryCode", "KR")
                        .param("timezone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.hasSpecialDay").value(true))
                .andExpect(jsonPath("$.data.specialDays[0].name").value("신정"))
                .andExpect(jsonPath("$.data.upcoming.name").value("삼일절"));
    }

    @Test
    @DisplayName("GET /today - 잘못된 countryCode는 400을 반환한다")
    void should_return400_when_invalidCountryCode() throws Exception {
        mockMvc.perform(get("/api/v1/special-days/today")
                        .param("countryCode", "INVALID")
                        .param("timezone", "UTC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SD-001"));
    }

    @Test
    @DisplayName("GET /today - 잘못된 timezone은 400을 반환한다")
    void should_return400_when_invalidTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/special-days/today")
                        .param("countryCode", "KR")
                        .param("timezone", "Invalid/Zone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SD-002"));
    }

    @Test
    @DisplayName("GET /countries - 전체 국가 목록을 반환한다")
    void should_returnAllCountries_when_noQuery() throws Exception {
        mockMvc.perform(get("/api/v1/special-days/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(CountryCode.values().length));
    }

    @Test
    @DisplayName("GET /countries?query=south - 검색어로 필터링된 결과를 반환한다")
    void should_returnFiltered_when_queryProvided() throws Exception {
        mockMvc.perform(get("/api/v1/special-days/countries")
                        .param("query", "south"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());
    }
}
