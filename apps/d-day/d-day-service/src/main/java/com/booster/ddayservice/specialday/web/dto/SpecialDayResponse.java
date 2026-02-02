package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.time.LocalTime;

public record SpecialDayResponse(
        Long id,
        String name,
        SpecialDayCategory category,
        LocalDate date,
        LocalTime eventTime,
        String countryCode,
        String timezone,
        String description,
        boolean isPublic
) {
    public static SpecialDayResponse from(SpecialDay entity) {
        return new SpecialDayResponse(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getDate(),
                entity.getEventTime(),
                entity.getCountryCode().name(),
                entity.getEventTimeZone() != null ? entity.getEventTimeZone().name() : null,
                entity.getDescription(),
                entity.isPublic()
        );
    }
}
