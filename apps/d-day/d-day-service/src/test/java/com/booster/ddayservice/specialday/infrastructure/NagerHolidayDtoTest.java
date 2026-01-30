package com.booster.ddayservice.specialday.infrastructure;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NagerHolidayDtoTest {

    @Test
    @DisplayName("Public 타입 공휴일을 SpecialDay 엔티티로 변환한다")
    void should_convertToEntity_when_publicHoliday() {
        // given
        NagerHolidayDto dto = new NagerHolidayDto(
                "2026-01-01", "신정", "New Year's Day", "KR",
                true, true, null, null, List.of("Public")
        );

        // when
        SpecialDay entity = dto.toEntity(CountryCode.KR);

        // then
        assertThat(entity.getName()).isEqualTo("신정");
        assertThat(entity.getCategory()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
        assertThat(entity.getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(entity.getEventTime()).isNull();
        assertThat(entity.getEventTimeZone()).isEqualTo(Timezone.ASIA_SEOUL);
        assertThat(entity.getCountryCode()).isEqualTo(CountryCode.KR);
        assertThat(entity.getDescription()).isEqualTo("New Year's Day");
    }

    @Test
    @DisplayName("types가 null이면 PUBLIC_HOLIDAY로 매핑한다")
    void should_mapToPublicHoliday_when_typesIsNull() {
        // given
        NagerHolidayDto dto = new NagerHolidayDto(
                "2026-12-25", "크리스마스", "Christmas Day", "KR",
                true, true, null, null, null
        );

        // when
        SpecialDay entity = dto.toEntity(CountryCode.KR);

        // then
        assertThat(entity.getCategory()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
    }

    @Test
    @DisplayName("types가 빈 리스트이면 PUBLIC_HOLIDAY로 매핑한다")
    void should_mapToPublicHoliday_when_typesIsEmpty() {
        // given
        NagerHolidayDto dto = new NagerHolidayDto(
                "2026-12-25", "크리스마스", "Christmas Day", "KR",
                true, true, null, null, List.of()
        );

        // when
        SpecialDay entity = dto.toEntity(CountryCode.KR);

        // then
        assertThat(entity.getCategory()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
    }

    @Test
    @DisplayName("Public이 아닌 타입은 MEMORIAL_DAY로 매핑한다")
    void should_mapToMemorialDay_when_nonPublicType() {
        // given
        NagerHolidayDto dto = new NagerHolidayDto(
                "2026-05-05", "어린이날", "Children's Day", "KR",
                true, true, null, null, List.of("Observance")
        );

        // when
        SpecialDay entity = dto.toEntity(CountryCode.KR);

        // then
        assertThat(entity.getCategory()).isEqualTo(SpecialDayCategory.MEMORIAL_DAY);
    }

    @Test
    @DisplayName("미국 국가 코드로 변환 시 미국 timezone이 설정된다")
    void should_setUSTimezone_when_countryIsUS() {
        // given
        NagerHolidayDto dto = new NagerHolidayDto(
                "2026-07-04", "Independence Day", "Independence Day", "US",
                true, true, null, null, List.of("Public")
        );

        // when
        SpecialDay entity = dto.toEntity(CountryCode.US);

        // then
        assertThat(entity.getCountryCode()).isEqualTo(CountryCode.US);
        assertThat(entity.getEventTimeZone()).isEqualTo(Timezone.AMERICA_NEW_YORK);
    }
}
