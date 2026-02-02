package com.booster.ddayservice.specialday.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpecialDayRepository extends JpaRepository<SpecialDay,Long> {

    // === 가시성 조건 포함 조회 ===

    @Query("SELECT s FROM SpecialDay s WHERE s.date = :date AND s.countryCode IN :countryCodes " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId)")
    List<SpecialDay> findVisibleByDateAndCountryCode(
            @Param("date") LocalDate date,
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.date = :date AND s.countryCode IN :countryCodes " +
            "AND s.category IN :categories " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId)")
    List<SpecialDay> findVisibleByDateAndCountryCodeAndCategory(
            @Param("date") LocalDate date,
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("categories") List<SpecialDayCategory> categories,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date > :date " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId) " +
            "ORDER BY s.date ASC LIMIT 1")
    Optional<SpecialDay> findFirstVisibleUpcoming(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("date") LocalDate date,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.category IN :categories AND s.date > :date " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId) " +
            "ORDER BY s.date ASC LIMIT 1")
    Optional<SpecialDay> findFirstVisibleUpcomingByCategory(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("categories") List<SpecialDayCategory> categories,
            @Param("date") LocalDate date,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date < :date " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId) " +
            "ORDER BY s.date DESC LIMIT 1")
    Optional<SpecialDay> findFirstVisiblePast(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("date") LocalDate date,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.category IN :categories AND s.date < :date " +
            "AND (s.memberId IS NULL OR s.isPublic = true OR s.memberId = :memberId) " +
            "ORDER BY s.date DESC LIMIT 1")
    Optional<SpecialDay> findFirstVisiblePastByCategory(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("categories") List<SpecialDayCategory> categories,
            @Param("date") LocalDate date,
            @Param("memberId") Long memberId);

    // === 기존 메서드 (하위 호환) ===

    List<SpecialDay> findByDateAndCountryCodeIn(LocalDate date, List<CountryCode> countryCodes);

    Optional<SpecialDay> findFirstByCountryCodeInAndDateAfterOrderByDateAsc(List<CountryCode> countryCodes, LocalDate date);

    Optional<SpecialDay> findFirstByCountryCodeInAndDateBeforeOrderByDateDesc(List<CountryCode> countryCodes, LocalDate date);

    List<SpecialDay> findByDateAndCountryCodeInAndCategoryIn(LocalDate date, List<CountryCode> countryCodes, List<SpecialDayCategory> categories);

    Optional<SpecialDay> findFirstByCountryCodeInAndCategoryInAndDateAfterOrderByDateAsc(List<CountryCode> countryCodes, List<SpecialDayCategory> categories, LocalDate date);

    Optional<SpecialDay> findFirstByCountryCodeInAndCategoryInAndDateBeforeOrderByDateDesc(List<CountryCode> countryCodes, List<SpecialDayCategory> categories, LocalDate date);

    // 중복 체크
    boolean existsByCountryCodeAndDateAndName(CountryCode countryCode, LocalDate date, String name);
}
