package com.booster.ddayservice.specialday.infrastructure;

import com.booster.ddayservice.specialday.domain.CountryCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.booster.ddayservice.specialday.domain.CountryCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NagerDateClientTest {

    private NagerDateClient nagerDateClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        nagerDateClient = new NagerDateClient(builder);
    }

    @Test
    @DisplayName("한국 공휴일 조회 시 SpecialDay 목록을 반환한다")
    void should_returnHolidayList_when_validCountryCode() throws Exception {
        // given
        String responseJson = new ObjectMapper().writeValueAsString(List.of(
                new NagerHolidayDto("2026-01-01", "신정", "New Year's Day", "KR",
                        true, true, null, null, List.of("Public")),
                new NagerHolidayDto("2026-03-01", "삼일절", "Independence Movement Day", "KR",
                        true, true, null, null, List.of("Public"))
        ));

        mockServer.expect(requestTo("https://date.nager.at/api/v3/publicholidays/2026/KR"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // when
        List<NagerHolidayDto> result = nagerDateClient.getPublicHolidays(2026, KR);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).localName()).isEqualTo("신정");
        assertThat(result.get(0).date()).isEqualTo("2026-01-01");
        assertThat(result.get(1).localName()).isEqualTo("삼일절");

        mockServer.verify();
    }

    @Test
    @DisplayName("응답이 빈 배열이면 빈 리스트를 반환한다")
    void should_returnEmptyList_when_emptyResponse() {
        // given
        mockServer.expect(requestTo("https://date.nager.at/api/v3/publicholidays/2026/US"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // when
        List<NagerHolidayDto> result = nagerDateClient.getPublicHolidays(2026, US);

        // then
        assertThat(result).isEmpty();

        mockServer.verify();
    }

    @Test
    @DisplayName("API가 404를 반환하면 예외가 발생한다")
    void should_throwException_when_notFound() {
        // given
        mockServer.expect(requestTo("https://date.nager.at/api/v3/publicholidays/2026/KR"))
                .andRespond(withResourceNotFound());

        // when & then
        assertThatThrownBy(() -> nagerDateClient.getPublicHolidays(2026, KR))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("types가 null인 공휴일도 정상 파싱된다")
    void should_parseCorrectly_when_typesIsNull() throws Exception {
        // given
        String responseJson = """
                [{"date":"2026-12-25","localName":"크리스마스","name":"Christmas Day",
                  "countryCode":"KR","fixed":true,"global":true,
                  "counties":null,"launchYear":null,"types":null}]
                """;

        mockServer.expect(requestTo("https://date.nager.at/api/v3/publicholidays/2026/KR"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // when
        List<NagerHolidayDto> result = nagerDateClient.getPublicHolidays(2026, KR);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().localName()).isEqualTo("크리스마스");
        assertThat(result.getFirst().types()).isNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("다양한 국가 코드로 올바른 URL을 호출한다")
    void should_callCorrectUrl_when_differentCountryCode() throws Exception {
        // given
        String responseJson = new ObjectMapper().writeValueAsString(List.of(
                new NagerHolidayDto("2026-07-04", "Independence Day", "Independence Day", "US",
                        true, true, null, null, List.of("Public"))
        ));

        mockServer.expect(requestTo("https://date.nager.at/api/v3/publicholidays/2026/US"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // when
        List<NagerHolidayDto> result = nagerDateClient.getPublicHolidays(2026, US);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().countryCode()).isEqualTo("US");

        mockServer.verify();
    }
}
