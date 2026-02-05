package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 공개 데이터 캐시 전용 서비스.
 * 카테고리 그룹별로 다른 TTL의 캐시를 적용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialDayCacheService {

    private final SpecialDayRepository specialDayRepository;

    // === 공휴일 그룹 (TTL: 6개월) ===

    @Cacheable(value = "public-holidays",
            key = "'date:' + #date + ':country:' + #countryCodes.hashCode()")
    public List<SpecialDay> findPublicHolidays(LocalDate date, List<CountryCode> countryCodes) {
        log.debug("[CACHE MISS] public-holidays - date={}, countries={}", date, countryCodes);
        return specialDayRepository.findPublicByDateAndCountryCodeAndCategories(
                date, countryCodes, SpecialDayCategory.HOLIDAY_GROUP);
    }

    @Cacheable(value = "public-holidays",
            key = "'upcoming:country:' + #countryCodes.hashCode() + ':after:' + #today")
    public Optional<SpecialDay> findFirstPublicHolidayUpcoming(List<CountryCode> countryCodes, LocalDate today) {
        log.debug("[CACHE MISS] public-holidays upcoming - countries={}, today={}", countryCodes, today);
        return specialDayRepository.findFirstPublicUpcomingByCategories(
                countryCodes, today, SpecialDayCategory.HOLIDAY_GROUP);
    }

    @Cacheable(value = "public-holidays",
            key = "'past:country:' + #countryCodes.hashCode() + ':before:' + #today")
    public Optional<SpecialDay> findFirstPublicHolidayPast(List<CountryCode> countryCodes, LocalDate today) {
        log.debug("[CACHE MISS] public-holidays past - countries={}, today={}", countryCodes, today);
        return specialDayRepository.findFirstPublicPastByCategories(
                countryCodes, today, SpecialDayCategory.HOLIDAY_GROUP);
    }

    // === 엔터테인먼트 그룹 (TTL: 1주일) ===

    @Cacheable(value = "public-entertainment",
            key = "'date:' + #date + ':country:' + #countryCodes.hashCode()")
    public List<SpecialDay> findPublicEntertainment(LocalDate date, List<CountryCode> countryCodes) {
        log.debug("[CACHE MISS] public-entertainment - date={}, countries={}", date, countryCodes);
        return specialDayRepository.findPublicByDateAndCountryCodeAndCategories(
                date, countryCodes, SpecialDayCategory.ENTERTAINMENT_GROUP);
    }

    @Cacheable(value = "public-entertainment",
            key = "'upcoming:country:' + #countryCodes.hashCode() + ':after:' + #today")
    public Optional<SpecialDay> findFirstPublicEntertainmentUpcoming(List<CountryCode> countryCodes, LocalDate today) {
        log.debug("[CACHE MISS] public-entertainment upcoming - countries={}, today={}", countryCodes, today);
        return specialDayRepository.findFirstPublicUpcomingByCategories(
                countryCodes, today, SpecialDayCategory.ENTERTAINMENT_GROUP);
    }

    @Cacheable(value = "public-entertainment",
            key = "'past:country:' + #countryCodes.hashCode() + ':before:' + #today")
    public Optional<SpecialDay> findFirstPublicEntertainmentPast(List<CountryCode> countryCodes, LocalDate today) {
        log.debug("[CACHE MISS] public-entertainment past - countries={}, today={}", countryCodes, today);
        return specialDayRepository.findFirstPublicPastByCategories(
                countryCodes, today, SpecialDayCategory.ENTERTAINMENT_GROUP);
    }

    // === 기타 공개 데이터 (TTL: 1일) ===

    @Cacheable(value = "public-others",
            key = "'date:' + #date + ':country:' + #countryCodes.hashCode()")
    public List<SpecialDay> findPublicOthers(LocalDate date, List<CountryCode> countryCodes) {
        log.debug("[CACHE MISS] public-others - date={}, countries={}", date, countryCodes);
        return specialDayRepository.findPublicByDateAndCountryCodeAndCategories(
                date, countryCodes, SpecialDayCategory.CUSTOM_GROUP);
    }

    // === 통합 조회 (모든 공개 데이터) ===

    public List<SpecialDay> findAllPublicByDate(LocalDate date, List<CountryCode> countryCodes) {
        List<SpecialDay> result = new ArrayList<>();
        result.addAll(findPublicHolidays(date, countryCodes));
        result.addAll(findPublicEntertainment(date, countryCodes));
        result.addAll(findPublicOthers(date, countryCodes));
        return result;
    }

    public List<SpecialDay> findAllPublicByDateAndCategories(
            LocalDate date, List<CountryCode> countryCodes, List<SpecialDayCategory> categories) {

        List<SpecialDay> result = new ArrayList<>();

        // 요청된 카테고리에 해당하는 그룹만 조회
        boolean needHolidays = categories.stream().anyMatch(SpecialDayCategory.HOLIDAY_GROUP::contains);
        boolean needEntertainment = categories.stream().anyMatch(SpecialDayCategory.ENTERTAINMENT_GROUP::contains);
        boolean needOthers = categories.stream().anyMatch(SpecialDayCategory.CUSTOM_GROUP::contains);

        if (needHolidays) {
            result.addAll(findPublicHolidays(date, countryCodes).stream()
                    .filter(s -> categories.contains(s.getCategory()))
                    .toList());
        }
        if (needEntertainment) {
            result.addAll(findPublicEntertainment(date, countryCodes).stream()
                    .filter(s -> categories.contains(s.getCategory()))
                    .toList());
        }
        if (needOthers) {
            result.addAll(findPublicOthers(date, countryCodes).stream()
                    .filter(s -> categories.contains(s.getCategory()))
                    .toList());
        }

        return result;
    }
}
