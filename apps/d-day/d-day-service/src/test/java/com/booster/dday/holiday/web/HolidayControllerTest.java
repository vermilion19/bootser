package com.booster.dday.holiday.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.HolidayQueryService;
import com.booster.dday.holiday.application.dto.HolidayOnDateView;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.holiday.application.dto.LongWeekendYearView;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.shared.web.DDayErrorCode;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공휴일 조회의 HTTP 표면 (A-1 ~ A-4).
 *
 * <p>여기서 무는 것은 <b>응답에서 만들어지는 값들</b>이다 — D-day 와 연휴 3분기.
 * 둘 다 캐시에 없고 요청이 올 때 정해진다 (SPEC §9.9(2)).
 */
@WebMvcTest(HolidayController.class)
@Import({GlobalExceptionHandler.class, HolidayControllerTest.Fixed.class})
class HolidayControllerTest {

    /** 2026-06-15 (월) */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
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
    private HolidayQueryService holidayQueryService;

    @MockitoBean
    private CountryReader countries;

    private static HolidayYearView.Entry liberationDay() {
        return new HolidayYearView.Entry(LocalDate.of(2026, 8, 15), "Liberation Day",
                "광복절", "liberation-day", true, List.of(), 1945);
    }

    @BeforeEach
    void setUp() {
        when(holidayQueryService.zoneOf(anyString())).thenReturn(SEOUL);
    }

    @Nested
    @DisplayName("그 해의 공휴일 (A-1)")
    class Year {

        @Test
        @DisplayName("D-day 와 시간대가 붙어 나간다")
        void listsWithDDay() throws Exception {
            when(holidayQueryService.holidaysOf("KR", 2026))
                    .thenReturn(HolidayYearView.of("KR", 2026, List.of(liberationDay())));

            mockMvc.perform(get("/api/v1/dday/countries/KR/holidays").param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].date").value("2026-08-15"))
                    .andExpect(jsonPath("$.data[0].nameEn").value("Liberation Day"))
                    .andExpect(jsonPath("$.data[0].nameLocal").value("광복절"))
                    .andExpect(jsonPath("$.data[0].dDay").value(61))
                    .andExpect(jsonPath("$.data[0].zone").value("Asia/Seoul"))
                    .andExpect(jsonPath("$.data[0].launchYear").value(1945));
        }

        @Test
        @DisplayName("담고 있지 않은 해는 400 이고 보유 범위를 알려 준다")
        void outOfCoverage() throws Exception {
            when(holidayQueryService.holidaysOf(anyString(), anyInt()))
                    .thenThrow(new CoreException(DDayErrorCode.DATE_OUT_OF_COVERAGE,
                            "2035년은 담고 있지 않다. 지금 담은 해: 2025~2028"));

            mockMvc.perform(get("/api/v1/dday/countries/KR/holidays").param("year", "2035"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-HOLIDAY-001"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("2025~2028")));
        }
    }

    @Nested
    @DisplayName("다음 공휴일 (A-2)")
    class Next {

        @Test
        @DisplayName("가장 가까운 것 하나와 D-day")
        void picksTheNearest() throws Exception {
            when(holidayQueryService.upcomingOf("KR")).thenReturn(List.of(
                    new HolidayYearView.Entry(LocalDate.of(2026, 12, 25), "Christmas Day",
                            "크리스마스", "christmas-day", true, List.of(), null),
                    liberationDay()));

            mockMvc.perform(get("/api/v1/dday/countries/KR/holidays/next"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nameEn").value("Liberation Day"))
                    .andExpect(jsonPath("$.data.dDay").value(61));
        }

        /**
         * <b>오늘도 「다음」에 든다.</b> 오늘 쉬는 날을 건너뛰고 다음 달을 가리키면
         * 그게 고장이다.
         */
        @Test
        @DisplayName("오늘이 공휴일이면 D-day 가 0 이다")
        void todayCountsAsNext() throws Exception {
            when(holidayQueryService.upcomingOf("KR")).thenReturn(List.of(
                    new HolidayYearView.Entry(LocalDate.of(2026, 6, 15), "Today Holiday",
                            null, "today-holiday", true, List.of(), null)));

            mockMvc.perform(get("/api/v1/dday/countries/KR/holidays/next"))
                    .andExpect(jsonPath("$.data.dDay").value(0));
        }

        @Test
        @DisplayName("남은 것이 없으면 빈 성공이다")
        void emptyIsStillSuccess() throws Exception {
            when(holidayQueryService.upcomingOf("KR")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/dday/countries/KR/holidays/next"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("황금연휴 (A-3)")
    class Weekends {

        @Test
        @DisplayName("3분기와 길이가 응답에서 만들어진다")
        void phaseAndDaysAreComputed() throws Exception {
            when(holidayQueryService.longWeekendsOf("KR", 2026)).thenReturn(
                    LongWeekendYearView.of("KR", 2026, List.of(
                            new LongWeekendYearView.Entry(LocalDate.of(2026, 5, 1),
                                    LocalDate.of(2026, 5, 5), true,
                                    List.of(LocalDate.of(2026, 5, 4))))));

            mockMvc.perform(get("/api/v1/dday/countries/KR/long-weekends").param("year", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].days").value(5))
                    .andExpect(jsonPath("$.data[0].phase").value("AFTER"))
                    .andExpect(jsonPath("$.data[0].bridgeDays[0]").value("2026-05-04"));
        }

        @Test
        @DisplayName("연휴 중이면 DURING 이다")
        void duringTheHoliday() throws Exception {
            when(holidayQueryService.longWeekendsOf("KR", 2026)).thenReturn(
                    LongWeekendYearView.of("KR", 2026, List.of(
                            new LongWeekendYearView.Entry(LocalDate.of(2026, 6, 13),
                                    LocalDate.of(2026, 6, 16), false, List.of()))));

            mockMvc.perform(get("/api/v1/dday/countries/KR/long-weekends").param("year", "2026"))
                    .andExpect(jsonPath("$.data[0].phase").value("DURING"));
        }
    }

    @Nested
    @DisplayName("그 날 쉬는 나라 (A-4) · 국가")
    class Others {

        @Test
        @DisplayName("날짜로 나라를 찾는다")
        void onDate() throws Exception {
            when(holidayQueryService.onDate(LocalDate.of(2026, 12, 25))).thenReturn(
                    HolidayOnDateView.of(LocalDate.of(2026, 12, 25), List.of(
                            new HolidayOnDateView.Entry("KR", "Christmas Day", "christmas-day", true))));

            mockMvc.perform(get("/api/v1/dday/holidays/on/2026-12-25"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.countries[0].countryCode").value("KR"));
        }

        @Test
        @DisplayName("날짜 모양이 틀리면 400 이다")
        void badDate() throws Exception {
            mockMvc.perform(get("/api/v1/dday/holidays/on/어제"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"));
        }

        @Test
        @DisplayName("국가 메타에 대표 시간대와 「우리가 골랐다」 표시가 있다")
        void countryMeta() throws Exception {
            when(countries.find("US")).thenReturn(Optional.of(new CountryView(
                    "US", "United States", "미국", "America/New_York", true, (short) 96)));

            mockMvc.perform(get("/api/v1/dday/countries/US"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.zoneId").value("America/New_York"))
                    .andExpect(jsonPath("$.data.zoneAmbiguous").value(true));
        }

        @Test
        @DisplayName("없는 국가 코드는 404 다")
        void unknownCountry() throws Exception {
            when(countries.find("ZZ")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/dday/countries/ZZ"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COUNTRY-001"));
        }
    }
}
