package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.util.List;

public record TodayResponse(
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
        public static SpecialDayItem from(TodayResult.SpecialDayItem item) {
            return new SpecialDayItem(item.name(), item.category(), item.description());
        }
    }

    public record UpcomingItem(
            String name,
            LocalDate date,
            long daysUntil,
            SpecialDayCategory category
    ) {
        public static UpcomingItem from(TodayResult.UpcomingItem item) {
            return new UpcomingItem(item.name(), item.date(), item.daysUntil(), item.category());
        }
    }

    public static TodayResponse from(TodayResult result) {
        return new TodayResponse(
                result.date(),
                result.countryCode(),
                result.hasSpecialDay(),
                result.specialDays().stream()
                        .map(SpecialDayItem::from)
                        .toList(),
                result.upcoming().stream()
                        .map(UpcomingItem::from)
                        .toList()
        );
    }
}
