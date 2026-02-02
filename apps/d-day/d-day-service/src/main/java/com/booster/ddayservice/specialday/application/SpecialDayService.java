package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.domain.Timezone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialDayService {

    private final SpecialDayRepository specialDayRepository;

    public TodayResult getToday(CountryCode countryCode, Timezone timezone, List<SpecialDayCategory> categories) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        List<SpecialDay> todaySpecialDays = categories.isEmpty()
                ? specialDayRepository.findByDateAndCountryCodeIn(today, countryCodes)
                : specialDayRepository.findByDateAndCountryCodeInAndCategoryIn(today, countryCodes, categories);

        Optional<SpecialDay> firstUpcoming = categories.isEmpty()
                ? specialDayRepository.findFirstByCountryCodeInAndDateAfterOrderByDateAsc(countryCodes, today)
                : specialDayRepository.findFirstByCountryCodeInAndCategoryInAndDateAfterOrderByDateAsc(countryCodes, categories, today);

        List<TodayResult.UpcomingItem> upcoming = firstUpcoming
                .map(first -> {
                    List<SpecialDay> sameDateEvents = categories.isEmpty()
                            ? specialDayRepository.findByDateAndCountryCodeIn(first.getDate(), countryCodes)
                            : specialDayRepository.findByDateAndCountryCodeInAndCategoryIn(first.getDate(), countryCodes, categories);
                    return sameDateEvents.stream()
                            .map(entity -> TodayResult.UpcomingItem.from(entity, today))
                            .toList();
                })
                .orElse(List.of());

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

    public PastResult getPast(CountryCode countryCode, Timezone timezone, List<SpecialDayCategory> categories) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        Optional<SpecialDay> pastEvent = categories.isEmpty()
                ? specialDayRepository.findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(countryCodes, today)
                : specialDayRepository.findFirstByCountryCodeInAndCategoryInAndDateBeforeOrderByDateDesc(countryCodes, categories, today);

        return pastEvent
                .map(entity -> PastResult.from(entity, today))
                .orElse(null);
    }
}
