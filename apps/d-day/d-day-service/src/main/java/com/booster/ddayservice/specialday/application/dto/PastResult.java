package com.booster.ddayservice.specialday.application.dto;

import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record PastResult(
        String name,
        LocalDate date,
        long daysSince,
        SpecialDayCategory category
) {

    public static PastResult from(SpecialDay entity, LocalDate today) {
        return new PastResult(
                entity.getName(),
                entity.getDate(),
                ChronoUnit.DAYS.between(entity.getDate(), today),
                entity.getCategory()
        );
    }
}
