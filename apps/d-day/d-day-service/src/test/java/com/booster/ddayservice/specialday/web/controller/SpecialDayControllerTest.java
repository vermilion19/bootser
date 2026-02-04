package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.config.JacksonConfig;
import com.booster.ddayservice.auth.web.CurrentMemberIdResolver;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import com.booster.ddayservice.specialday.exception.SpecialDayExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpecialDayController.class)
@Import({SpecialDayExceptionHandler.class, JacksonConfig.class, CurrentMemberIdResolver.class})
class SpecialDayControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SpecialDayService specialDayService;

    @Nested
    @DisplayName("GET /today")
    class GetToday {

        @Test
        @DisplayName("정상 응답을 반환한다")
        void should_returnTodayResponse_when_validRequest() throws Exception {
            LocalDate today = LocalDate.of(2026, 1, 1);
            TodayResult result = new TodayResult(
                    today, "KR", true,
                    List.of(new TodayResult.SpecialDayItem("신정", SpecialDayCategory.PUBLIC_HOLIDAY, "New Year's Day")),
                    List.of(new TodayResult.UpcomingItem("삼일절", LocalDate.of(2026, 3, 1), 59, SpecialDayCategory.PUBLIC_HOLIDAY))
            );

            given(specialDayService.getToday(eq(CountryCode.KR), eq(Timezone.ASIA_SEOUL), eq(List.of()), isNull()))
                    .willReturn(result);

            mockMvc.perform(get("/api/v1/special-days/today")
                            .param("countryCode", "KR")
                            .param("timezone", "Asia/Seoul"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.hasSpecialDay").value(true))
                    .andExpect(jsonPath("$.data.specialDays[0].name").value("신정"))
                    .andExpect(jsonPath("$.data.upcoming[0].name").value("삼일절"));
        }

        @Test
        @DisplayName("잘못된 countryCode는 400을 반환한다")
        void should_return400_when_invalidCountryCode() throws Exception {
            mockMvc.perform(get("/api/v1/special-days/today")
                            .param("countryCode", "INVALID")
                            .param("timezone", "UTC"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("SD-001"));
        }

        @Test
        @DisplayName("잘못된 timezone은 400을 반환한다")
        void should_return400_when_invalidTimezone() throws Exception {
            mockMvc.perform(get("/api/v1/special-days/today")
                            .param("countryCode", "KR")
                            .param("timezone", "Invalid/Zone"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("SD-002"));
        }
    }

    @Nested
    @DisplayName("GET /countries")
    class GetCountries {

        @Test
        @DisplayName("전체 국가 목록을 반환한다")
        void should_returnAllCountries_when_noQuery() throws Exception {
            mockMvc.perform(get("/api/v1/special-days/countries"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(CountryCode.values().length));
        }
    }

    @Nested
    @DisplayName("GET /past")
    class GetPast {

        @Test
        @DisplayName("과거 특별한 날이 있으면 정상 응답을 반환한다")
        void should_returnPastResponse_when_pastEventExists() throws Exception {
            PastResult pastResult = new PastResult(
                    "신정", LocalDate.of(2026, 1, 1), 32, SpecialDayCategory.PUBLIC_HOLIDAY
            );

            given(specialDayService.getPast(eq(CountryCode.KR), eq(Timezone.ASIA_SEOUL), eq(List.of()), isNull()))
                    .willReturn(pastResult);

            mockMvc.perform(get("/api/v1/special-days/past")
                            .param("countryCode", "KR")
                            .param("timezone", "Asia/Seoul"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.name").value("신정"));
        }

        @Test
        @DisplayName("과거 특별한 날이 없으면 204 No Content를 반환한다")
        void should_return204_when_noPastEventExists() throws Exception {
            given(specialDayService.getPast(eq(CountryCode.KR), eq(Timezone.ASIA_SEOUL), eq(List.of()), isNull()))
                    .willReturn(null);

            mockMvc.perform(get("/api/v1/special-days/past")
                            .param("countryCode", "KR")
                            .param("timezone", "Asia/Seoul"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/special-days")
    class CreateByMember {

        @Test
        @DisplayName("인증된 사용자가 특별한 날을 등록한다")
        void should_createSpecialDay_when_authenticated() throws Exception {
            Long memberId = 100L;
            String requestBody = """
                    {
                        "name": "내 기념일",
                        "category": "CUSTOM",
                        "date": "2026-06-01",
                        "eventTime" :"10:10",
                        "countryCode": "KR",
                        "timezone" : "Asia/Seoul",
                        "description": "기념일입니다",
                        "isPublic": true
                    }
                    """;

            SpecialDay mockEntity = SpecialDay.builder()
                    .memberId(memberId)
                    .name("내 기념일")
                    .countryCode(CountryCode.KR)
                    .build();

            given(specialDayService.createByMember(
                    any(), any(), any(), any(), any(), any(), any(), eq(memberId), anyBoolean()
            )).willReturn(mockEntity);

            mockMvc.perform(post("/api/v1/special-days")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header(USER_ID_HEADER, memberId.toString()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/special-days/{id}")
    class DeleteByMember {

        @Test
        @DisplayName("본인 소유 삭제 시 성공한다")
        void should_delete_when_ownedByMember() throws Exception {
            Long memberId = 100L;

            mockMvc.perform(delete("/api/v1/special-days/12345")
                            .header(USER_ID_HEADER, memberId.toString()))
                    .andExpect(status().isOk());

            verify(specialDayService).delete(12345L, memberId);
        }

        @Test
        @DisplayName("타인 소유 삭제 시 403을 반환한다")
        void should_return403_when_notOwned() throws Exception {
            Long memberId = 100L;

            doThrow(new SpecialDayException(SpecialDayErrorCode.FORBIDDEN))
                    .when(specialDayService).delete(12345L, memberId);

            mockMvc.perform(delete("/api/v1/special-days/12345")
                            .header(USER_ID_HEADER, memberId.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("SD-006"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/special-days/{id}/visibility")
    class ToggleVisibility {

        @Test
        @DisplayName("공개/비공개 토글에 성공한다")
        void should_toggleVisibility_when_ownedByMember() throws Exception {
            Long memberId = 100L;

            mockMvc.perform(patch("/api/v1/special-days/12345/visibility")
                            .header(USER_ID_HEADER, memberId.toString()))
                    .andExpect(status().isOk());

            verify(specialDayService).toggleVisibility(12345L, memberId);
        }
    }
}
