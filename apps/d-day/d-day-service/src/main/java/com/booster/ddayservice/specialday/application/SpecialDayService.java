package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.domain.Timezone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialDayService {

    private final SpecialDayRepository specialDayRepository;

    public TodayResult getToday(CountryCode countryCode, Timezone timezone) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        List<SpecialDay> todaySpecialDays = specialDayRepository
                .findByDateAndCountryCodeIn(today, countryCodes);

        TodayResult.UpcomingItem upcoming = specialDayRepository
                .findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today)
                .map(entity -> TodayResult.UpcomingItem.from(entity, today))
                .orElse(null);

        List<TodayResult.SpecialDayItem> items = todaySpecialDays.stream()
                .map(TodayResult.SpecialDayItem::from)
                .toList();

        return new TodayResult(
                today,
                countryCode.name(),
                !items.isEmpty(),
                items,
                upcoming
        );
    }
}
