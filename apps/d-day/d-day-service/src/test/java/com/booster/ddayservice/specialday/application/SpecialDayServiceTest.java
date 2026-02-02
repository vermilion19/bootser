package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.PastResult;
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
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

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
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

        // then
        assertThat(result.hasSpecialDay()).isFalse();
        assertThat(result.specialDays()).isEmpty();
        assertThat(result.upcoming()).isEmpty();
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
        given(specialDayRepository.findByDateAndCountryCodeIn(futureDate, countryCodes))
                .willReturn(List.of(upcoming));

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

        // then
        assertThat(result.upcoming()).hasSize(1);
        assertThat(result.upcoming().getFirst().name()).isEqualTo("삼일절");
        assertThat(result.upcoming().getFirst().daysUntil()).isEqualTo(7);
    }

    @Test
    @DisplayName("같은 날에 여러 upcoming이 있으면 모두 반환한다")
    void should_returnMultipleUpcoming_when_sameDateHasMultipleEvents() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate futureDate = today.plusDays(5);
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        SpecialDay event1 = SpecialDay.of("행사A", SpecialDayCategory.PUBLIC_HOLIDAY,
                futureDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "Event A");
        SpecialDay event2 = SpecialDay.of("행사B", SpecialDayCategory.PUBLIC_HOLIDAY,
                futureDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "Event B");

        given(specialDayRepository.findByDateAndCountryCodeIn(today, countryCodes))
                .willReturn(List.of());
        given(specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today))
                .willReturn(Optional.of(event1));
        given(specialDayRepository.findByDateAndCountryCodeIn(futureDate, countryCodes))
                .willReturn(List.of(event1, event2));

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

        // then
        assertThat(result.upcoming()).hasSize(2);
        assertThat(result.upcoming().get(0).name()).isEqualTo("행사A");
        assertThat(result.upcoming().get(1).name()).isEqualTo("행사B");
        assertThat(result.upcoming().get(0).daysUntil()).isEqualTo(5);
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
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.UTC, List.of());

        // then
        assertThat(result.date()).isEqualTo(utcToday);
        assertThat(result.countryCode()).isEqualTo("KR");
    }

    @Test
    @DisplayName("과거 특별한 날이 있으면 daysSince를 정확히 계산하여 반환한다")
    void should_returnPast_when_pastEventExists() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate pastDate = today.minusDays(10);
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        SpecialDay pastDay = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                pastDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

        given(specialDayRepository.findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(countryCodes, today))
                .willReturn(Optional.of(pastDay));

        // when
        PastResult result = specialDayService.getPast(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("신정");
        assertThat(result.daysSince()).isEqualTo(10);
        assertThat(result.date()).isEqualTo(pastDate);
        assertThat(result.category()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
    }

    @Test
    @DisplayName("과거 특별한 날이 없으면 null을 반환한다")
    void should_returnNull_when_noPastEventExists() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CountryCode> countryCodes = List.of(CountryCode.KR);

        given(specialDayRepository.findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(countryCodes, today))
                .willReturn(Optional.empty());

        // when
        PastResult result = specialDayService.getPast(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("카테고리 필터를 지정하면 해당 카테고리만 조회한다")
    void should_filterByCategory_when_categoriesProvided() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CountryCode> countryCodes = List.of(CountryCode.KR);
        List<SpecialDayCategory> categories = List.of(SpecialDayCategory.PUBLIC_HOLIDAY);

        SpecialDay holiday = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                today, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

        given(specialDayRepository.findByDateAndCountryCodeInAndCategoryIn(today, countryCodes, categories))
                .willReturn(List.of(holiday));
        given(specialDayRepository.findFirstByCountryCodeInAndCategoryInAndDateAfterOrderByDateAsc(countryCodes, categories, today))
                .willReturn(Optional.empty());

        // when
        TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, categories);

        // then
        assertThat(result.hasSpecialDay()).isTrue();
        assertThat(result.specialDays()).hasSize(1);
        assertThat(result.specialDays().getFirst().category()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
    }

    @Test
    @DisplayName("카테고리 필터로 과거 특별한 날을 조회한다")
    void should_filterPastByCategory_when_categoriesProvided() {
        // given
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate pastDate = today.minusDays(5);
        List<CountryCode> countryCodes = List.of(CountryCode.KR);
        List<SpecialDayCategory> categories = List.of(SpecialDayCategory.PUBLIC_HOLIDAY);

        SpecialDay pastDay = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                pastDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

        given(specialDayRepository.findFirstByCountryCodeInAndCategoryInAndDateBeforeOrderByDateDesc(countryCodes, categories, today))
                .willReturn(Optional.of(pastDay));

        // when
        PastResult result = specialDayService.getPast(CountryCode.KR, Timezone.ASIA_SEOUL, categories);

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("신정");
        assertThat(result.category()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
    }
}
