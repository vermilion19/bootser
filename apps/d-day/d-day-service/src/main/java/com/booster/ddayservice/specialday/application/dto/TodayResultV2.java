package com.booster.ddayservice.specialday.application.dto;

import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record TodayResultV2(
        LocalDate date,
        String countryCode,
        boolean hasSpecialDay,
        List<SpecialDayItem> specialDays,
        List<UpcomingItem> upcoming
) {

    public record SpecialDayItem(
            String name,
            SpecialDayCategory category,
            String description
    ) {
        public static SpecialDayItem from(SpecialDay entity) {
            return new SpecialDayItem(
                    entity.getName(),
                    entity.getCategory(),
                    entity.getDescription()
            );
        }
    }

    public record UpcomingItem(
            String group,
            String name,
            LocalDate date,
            long daysUntil,
            SpecialDayCategory category
    ) {
        public static UpcomingItem from(SpecialDay entity, LocalDate today, String group) {
            return new UpcomingItem(
                    group,
                    entity.getName(),
                    entity.getDate(),
                    ChronoUnit.DAYS.between(today, entity.getDate()),
                    entity.getCategory()
            );
        }
    }
}
