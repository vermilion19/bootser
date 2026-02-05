package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.application.dto.TodayResultV2;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;

import java.time.LocalDate;
import java.util.List;

public record TodayResponseV2(
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
        public static SpecialDayItem from(TodayResultV2.SpecialDayItem item) {
            return new SpecialDayItem(item.name(), item.category(), item.description());
        }
    }

    public record UpcomingItem(
            String group,
            String name,
            LocalDate date,
            long daysUntil,
            SpecialDayCategory category
    ) {
        public static UpcomingItem from(TodayResultV2.UpcomingItem item) {
            return new UpcomingItem(item.group(), item.name(), item.date(), item.daysUntil(), item.category());
        }
    }

    public static TodayResponseV2 from(TodayResultV2 result) {
        return new TodayResponseV2(
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
