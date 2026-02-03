package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.infrastructure.NagerDateClient;
import com.booster.ddayservice.specialday.infrastructure.NagerHolidayDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpecialDaySyncServiceTest {

    @InjectMocks
    private SpecialDaySyncService specialDaySyncService;

    @Mock
    private NagerDateClient nagerDateClient;

    @Mock
    private SpecialDayRepository specialDayRepository;

    @Test
    @DisplayName("정상 동기화 시 저장 건수를 반환한다")
    void should_returnSavedCount_when_syncSucceeds() {
        // given
        List<NagerHolidayDto> holidays = List.of(
                new NagerHolidayDto("2026-01-01", "신정", "New Year's Day", "KR",
                        true, true, null, null, List.of("Public")),
                new NagerHolidayDto("2026-03-01", "삼일절", "Independence Movement Day", "KR",
                        true, true, null, null, List.of("Public"))
        );

        given(nagerDateClient.getPublicHolidays(2026, CountryCode.KR)).willReturn(holidays);
        given(specialDayRepository.existsByCountryCodeAndDateAndName(any(), any(), any()))
                .willReturn(false);
        given(specialDayRepository.save(any(SpecialDay.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        int count = specialDaySyncService.syncByYear(2026, CountryCode.KR);

        // then
        assertThat(count).isEqualTo(2);
        verify(specialDayRepository, org.mockito.Mockito.times(2)).save(any(SpecialDay.class));
    }

    @Test
    @DisplayName("이미 존재하는 데이터는 스킵한다")
    void should_skipDuplicate_when_dataAlreadyExists() {
        // given
        List<NagerHolidayDto> holidays = List.of(
                new NagerHolidayDto("2026-01-01", "신정", "New Year's Day", "KR",
                        true, true, null, null, List.of("Public"))
        );

        given(nagerDateClient.getPublicHolidays(2026, CountryCode.KR)).willReturn(holidays);
        given(specialDayRepository.existsByCountryCodeAndDateAndName(
                eq(CountryCode.KR), eq(LocalDate.of(2026, 1, 1)), eq("신정")))
                .willReturn(true);

        // when
        int count = specialDaySyncService.syncByYear(2026, CountryCode.KR);

        // then
        assertThat(count).isZero();
        verify(specialDayRepository, never()).save(any());
    }

    @Test
    @DisplayName("외부 API가 빈 응답을 반환하면 0을 반환한다")
    void should_returnZero_when_noHolidaysFromApi() {
        // given
        given(nagerDateClient.getPublicHolidays(2026, CountryCode.KR)).willReturn(List.of());

        // when
        int count = specialDaySyncService.syncByYear(2026, CountryCode.KR);

        // then
        assertThat(count).isZero();
        verify(specialDayRepository, never()).save(any());
    }
}
