package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.*;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialDayService {

    private final SpecialDayRepository specialDayRepository;

    public TodayResult getToday(CountryCode countryCode, Timezone timezone,
                                List<SpecialDayCategory> categories, Long memberId) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        List<SpecialDay> todaySpecialDays = categories.isEmpty()
                ? specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, memberId)
                : specialDayRepository.findVisibleByDateAndCountryCodeAndCategory(today, countryCodes, categories, memberId);

        Optional<SpecialDay> firstUpcoming = categories.isEmpty()
                ? specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, memberId)
                : specialDayRepository.findFirstVisibleUpcomingByCategory(countryCodes, categories, today, memberId);

        List<TodayResult.UpcomingItem> upcoming = firstUpcoming
                .map(first -> {
                    List<SpecialDay> sameDateEvents = categories.isEmpty()
                            ? specialDayRepository.findVisibleByDateAndCountryCode(first.getDate(), countryCodes, memberId)
                            : specialDayRepository.findVisibleByDateAndCountryCodeAndCategory(first.getDate(), countryCodes, categories, memberId);
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

    public TodayResult getToday(CountryCode countryCode, Timezone timezone, List<SpecialDayCategory> categories) {
        return getToday(countryCode, timezone, categories, null);
    }

    public PastResult getPast(CountryCode countryCode, Timezone timezone,
                              List<SpecialDayCategory> categories, Long memberId) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        Optional<SpecialDay> pastEvent = categories.isEmpty()
                ? specialDayRepository.findFirstVisiblePast(countryCodes, today, memberId)
                : specialDayRepository.findFirstVisiblePastByCategory(countryCodes, categories, today, memberId);

        return pastEvent
                .map(entity -> PastResult.from(entity, today))
                .orElse(null);
    }

    public PastResult getPast(CountryCode countryCode, Timezone timezone, List<SpecialDayCategory> categories) {
        return getPast(countryCode, timezone, categories, null);
    }

    @Transactional
    public SpecialDay createByAdmin(String name, SpecialDayCategory category, LocalDate date,
                                    LocalTime eventTime, Timezone eventTimeZone,
                                    CountryCode countryCode, String description) {
        return specialDayRepository.save(SpecialDay.of(name, category, date, eventTime, eventTimeZone, countryCode, description));
    }

    @Transactional
    public SpecialDay createByMember(String name, SpecialDayCategory category, LocalDate date,
                                     LocalTime eventTime, Timezone eventTimeZone,
                                     CountryCode countryCode, String description,
                                     Long memberId, boolean isPublic) {
        return specialDayRepository.save(
                SpecialDay.createByMember(name, category, date, eventTime, eventTimeZone, countryCode, description, memberId, isPublic));
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SpecialDayErrorCode.SPECIAL_DAY_NOT_FOUND));

        if (!specialDay.isOwnedBy(memberId)) {
            throw new SpecialDayException(SpecialDayErrorCode.FORBIDDEN);
        }

        specialDayRepository.delete(specialDay);
    }

    @Transactional
    public void toggleVisibility(Long id, Long memberId) {
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SpecialDayErrorCode.SPECIAL_DAY_NOT_FOUND));

        if (!specialDay.isOwnedBy(memberId)) {
            throw new SpecialDayException(SpecialDayErrorCode.FORBIDDEN);
        }

        specialDay.toggleVisibility();
    }
}
