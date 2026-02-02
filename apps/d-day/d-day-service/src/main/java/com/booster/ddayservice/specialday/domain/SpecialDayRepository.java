package com.booster.ddayservice.specialday.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpecialDayRepository extends JpaRepository<SpecialDay,Long> {

    // 오늘 특별한 날 조회 (해당 국가 + GLOBAL)
    List<SpecialDay> findByDateAndCountryCodeIn(LocalDate date, List<CountryCode> countryCodes);

    // 가장 가까운 미래 특별한 날
    Optional<SpecialDay> findFirstByCountryCodeInAndDateAfterOrderByDateAsc(List<CountryCode> countryCodes, LocalDate date);

    // 가장 가까운 과거 특별한 날
    Optional<SpecialDay> findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(List<CountryCode> countryCodes, LocalDate date);

    // 카테고리 필터 포함 조회
    List<SpecialDay> findByDateAndCountryCodeInAndCategoryIn(LocalDate date, List<CountryCode> countryCodes, List<SpecialDayCategory> categories);

    Optional<SpecialDay> findFirstByCountryCodeInAndCategoryInAndDateAfterOrderByDateAsc(List<CountryCode> countryCodes, List<SpecialDayCategory> categories, LocalDate date);

    Optional<SpecialDay> findFirstByCountryCodeInAndCategoryInAndDateBeforeOrderByDateDesc(List<CountryCode> countryCodes, List<SpecialDayCategory> categories, LocalDate date);

    // 중복 체크
    boolean existsByCountryCodeAndDateAndName(CountryCode countryCode, LocalDate date, String name);
}
