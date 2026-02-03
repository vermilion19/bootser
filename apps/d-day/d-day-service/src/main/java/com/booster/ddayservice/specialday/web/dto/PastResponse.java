package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;

public record PastResponse(
        String name,
        LocalDate date,
        long daysSince,
        SpecialDayCategory category
) {

    public static PastResponse from(PastResult result) {
        return new PastResponse(
                result.name(),
                result.date(),
                result.daysSince(),
                result.category()
        );
    }
}
