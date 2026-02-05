package com.booster.ddayservice.specialday.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SpecialDayRepository extends JpaRepository<SpecialDay,Long> {

    // === 공개 데이터 조회 (캐시 대상) ===

    @Query("SELECT s FROM SpecialDay s WHERE s.date = :date AND s.countryCode IN :countryCodes " +
            "AND s.category IN :categories AND s.isPublic = true")
    List<SpecialDay> findPublicByDateAndCountryCodeAndCategories(
            @Param("date") LocalDate date,
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("categories") List<SpecialDayCategory> categories);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date > :today " +
            "AND s.category IN :categories AND s.isPublic = true " +
            "ORDER BY s.date ASC LIMIT 1")
    Optional<SpecialDay> findFirstPublicUpcomingByCategories(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("today") LocalDate today,
            @Param("categories") List<SpecialDayCategory> categories);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date < :today " +
            "AND s.category IN :categories AND s.isPublic = true " +
            "ORDER BY s.date DESC LIMIT 1")
    Optional<SpecialDay> findFirstPublicPastByCategories(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("today") LocalDate today,
            @Param("categories") List<SpecialDayCategory> categories);

    // === 비공개 데이터 조회 (본인 것만, 캐시 안 함) ===

    @Query("SELECT s FROM SpecialDay s WHERE s.date = :date AND s.countryCode IN :countryCodes " +
            "AND s.memberId = :memberId AND s.isPublic = false")
    List<SpecialDay> findPrivateByDateAndMemberId(
            @Param("date") LocalDate date,
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.date = :date AND s.countryCode IN :countryCodes " +
            "AND s.category IN :categories AND s.memberId = :memberId AND s.isPublic = false")
    List<SpecialDay> findPrivateByDateAndCategoriesAndMemberId(
            @Param("date") LocalDate date,
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("categories") List<SpecialDayCategory> categories,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date > :today " +
            "AND s.memberId = :memberId AND s.isPublic = false " +
            "ORDER BY s.date ASC LIMIT 1")
    Optional<SpecialDay> findFirstPrivateUpcoming(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("today") LocalDate today,
            @Param("memberId") Long memberId);

    @Query("SELECT s FROM SpecialDay s WHERE s.countryCode IN :countryCodes AND s.date < :today " +
            "AND s.memberId = :memberId AND s.isPublic = false " +
            "ORDER BY s.date DESC LIMIT 1")
    Optional<SpecialDay> findFirstPrivatePast(
            @Param("countryCodes") List<CountryCode> countryCodes,
            @Param("today") LocalDate today,
            @Param("memberId") Long memberId);

    // === 가시성 조건 포함 조회 (기존 - 하위 호환용) ===

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

    // 중복 체크
    boolean existsByCountryCodeAndDateAndName(CountryCode countryCode, LocalDate date, String name);

    // 날짜 기준 중복 체크 (동기화용)
    boolean existsByCountryCodeAndDate(CountryCode countryCode, LocalDate date);

    // 날짜+이름 조합 조회 (Bulk 중복 체크용)
    @Query("SELECT CONCAT(s.date, ':', s.name) FROM SpecialDay s WHERE s.countryCode = :countryCode AND s.date BETWEEN :startDate AND :endDate")
    Set<String> findDateNameKeysByCountryCodeAndDateBetween(
            @Param("countryCode") CountryCode countryCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 중복 데이터 삭제용 (날짜+이름 기준)
    @Modifying
    @Query("DELETE FROM SpecialDay s WHERE s.id IN " +
            "(SELECT s2.id FROM SpecialDay s2 WHERE s2.countryCode = :countryCode " +
            "AND s2.date = :date AND s2.name = :name AND s2.id != :keepId)")
    int deleteDuplicatesByCountryCodeAndDateAndName(
            @Param("countryCode") CountryCode countryCode,
            @Param("date") LocalDate date,
            @Param("name") String name,
            @Param("keepId") Long keepId);

    // 국가별 중복 날짜+이름 조회
    @Query("SELECT CONCAT(s.date, ':', s.name) FROM SpecialDay s WHERE s.countryCode = :countryCode " +
            "GROUP BY s.date, s.name HAVING COUNT(s) > 1")
    List<String> findDuplicateDateNameKeysByCountryCode(@Param("countryCode") CountryCode countryCode);

    // 특정 국가, 날짜, 이름의 첫 번째 데이터 조회
    Optional<SpecialDay> findFirstByCountryCodeAndDateAndName(CountryCode countryCode, LocalDate date, String name);
}
