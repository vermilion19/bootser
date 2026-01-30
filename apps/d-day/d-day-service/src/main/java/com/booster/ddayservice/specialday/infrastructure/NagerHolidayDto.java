package com.booster.ddayservice.specialday.infrastructure;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.util.List;

public record NagerHolidayDto(
        String date,
        String localName,
        String name,
        String countryCode,
        boolean fixed,
        boolean global,
        List<String> counties,
        Integer launchYear,
        List<String> types
) {

    public SpecialDay toEntity(CountryCode country) {
        return SpecialDay.of(
                localName,
                mapCategory(),
                LocalDate.parse(date),
                null,
                country.getDefaultTimezone(),
                country,
                name
        );
    }

    private SpecialDayCategory mapCategory() {
        if (types == null || types.isEmpty()) {
            return SpecialDayCategory.PUBLIC_HOLIDAY;
        }
        return switch (types.getFirst().toLowerCase()) {
            case "public" -> SpecialDayCategory.PUBLIC_HOLIDAY;
            default -> SpecialDayCategory.MEMORIAL_DAY;
        };
    }
}
