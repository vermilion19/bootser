package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpecialDayServiceTest {

    @InjectMocks
    private SpecialDayService specialDayService;

    @Mock
    private SpecialDayRepository specialDayRepository;

    @Test
    @DisplayName("오늘 특별한 날이 있으면 hasSpecialDay=true와 항목을 반환한다")
    void should_returnSpecialDays_when_todayHasEvents() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        SpecialDay specialDay = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                today, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

        given(specialDayRepository.findByDateAndCountryCodeIn(today, countryCodes))
                .willReturn(List.of(specialDay));
        given(specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today))
                .willReturn(Optional.empty());

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL);

        // then
        assertThat(result.hasSpecialDay()).isTrue();
        assertThat(result.specialDays()).hasSize(1);
        assertThat(result.specialDays().getFirst().name()).isEqualTo("신정");
    }

    @Test
    @DisplayName("오늘 특별한 날이 없으면 hasSpecialDay=false와 빈 리스트를 반환한다")
    void should_returnEmpty_when_todayHasNoEvents() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        given(specialDayRepository.findByDateAndCountryCodeIn(today, countryCodes))
                .willReturn(List.of());
        given(specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today))
                .willReturn(Optional.empty());

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL);

        // then
        assertThat(result.hasSpecialDay()).isFalse();
        assertThat(result.specialDays()).isEmpty();
        assertThat(result.upcoming()).isNull();
    }

    @Test
    @DisplayName("upcoming이 있으면 daysUntil을 계산하여 반환한다")
    void should_returnUpcoming_when_futureEventExists() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate futureDate = today.plusDays(7);
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        SpecialDay upcoming = SpecialDay.of("삼일절", SpecialDayCategory.PUBLIC_HOLIDAY,
                futureDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "Independence Movement Day");

        given(specialDayRepository.findByDateAndCountryCodeIn(today, countryCodes))
                .willReturn(List.of());
        given(specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today))
                .willReturn(Optional.of(upcoming));

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL);

        // then
        assertThat(result.upcoming()).isNotNull();
        assertThat(result.upcoming().name()).isEqualTo("삼일절");
        assertThat(result.upcoming().daysUntil()).isEqualTo(7);
    }

    @Test
    @DisplayName("UTC 타임존으로 조회하면 UTC 기준 날짜를 사용한다")
    void should_useUtcDate_when_timezoneIsUtc() {
        // given
        LocalDate utcToday = LocalDate.now(ZoneId.of("UTC"));
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        given(specialDayRepository.findByDateAndCountryCodeIn(utcToday, countryCodes))
                .willReturn(List.of());
        given(specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, utcToday))
                .willReturn(Optional.empty());

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.UTC);

        // then
        assertThat(result.date()).isEqualTo(utcToday);
        assertThat(result.countryCode()).isEqualTo("KR");
    }
}
