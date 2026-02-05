package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.aop.Auditable;
import com.booster.ddayservice.aop.CheckOwnership;
import com.booster.ddayservice.aop.LogExecutionTime;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialDayService {

    private final SpecialDayRepository specialDayRepository;
    private final SpecialDayCacheService cacheService;

    @LogExecutionTime
    public TodayResult getToday(CountryCode countryCode, Timezone timezone,
                                List<SpecialDayCategory> categories, Long memberId) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        // 1. 오늘의 특별일 조회 (공개: 캐시, 비공개: DB)
        List<SpecialDay> todaySpecialDays = findVisibleSpecialDays(today, countryCodes, categories, memberId);

        // 2. 다음 특별일 조회
        List<TodayResult.UpcomingItem> upcoming = findUpcomingItems(today, countryCodes, categories, memberId);

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

    private List<SpecialDay> findVisibleSpecialDays(LocalDate date, List<CountryCode> countryCodes,
                                                     List<SpecialDayCategory> categories, Long memberId) {
        // 공개 데이터 (캐시)
        List<SpecialDay> publicData = categories.isEmpty()
                ? cacheService.findAllPublicByDate(date, countryCodes)
                : cacheService.findAllPublicByDateAndCategories(date, countryCodes, categories);

        // 비공개 데이터 (DB 직접 조회)
        List<SpecialDay> privateData = List.of();
        if (memberId != null) {
            privateData = categories.isEmpty()
                    ? specialDayRepository.findPrivateByDateAndMemberId(date, countryCodes, memberId)
                    : specialDayRepository.findPrivateByDateAndCategoriesAndMemberId(date, countryCodes, categories, memberId);
        }

        // 병합
        List<SpecialDay> result = new ArrayList<>(publicData);
        result.addAll(privateData);
        return result;
    }

    private List<TodayResult.UpcomingItem> findUpcomingItems(LocalDate today, List<CountryCode> countryCodes,
                                                              List<SpecialDayCategory> categories, Long memberId) {
        // 공개 데이터에서 가장 가까운 upcoming 찾기
        Optional<SpecialDay> publicUpcoming = findFirstPublicUpcoming(countryCodes, today, categories);

        // 비공개 데이터에서 가장 가까운 upcoming 찾기
        Optional<SpecialDay> privateUpcoming = Optional.empty();
        if (memberId != null) {
            privateUpcoming = specialDayRepository.findFirstPrivateUpcoming(countryCodes, today, memberId);
        }

        // 둘 중 더 가까운 날짜 선택
        Optional<SpecialDay> firstUpcoming = Stream.of(publicUpcoming, privateUpcoming)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparing(SpecialDay::getDate));

        return firstUpcoming
                .map(first -> {
                    List<SpecialDay> sameDateEvents = findVisibleSpecialDays(first.getDate(), countryCodes, categories, memberId);
                    return sameDateEvents.stream()
                            .map(entity -> TodayResult.UpcomingItem.from(entity, today))
                            .toList();
                })
                .orElse(List.of());
    }

    private Optional<SpecialDay> findFirstPublicUpcoming(List<CountryCode> countryCodes, LocalDate today,
                                                          List<SpecialDayCategory> categories) {
        // 각 캐시 그룹에서 upcoming 조회 후 가장 가까운 것 선택
        List<Optional<SpecialDay>> upcomingCandidates = new ArrayList<>();

        if (categories.isEmpty() || categories.stream().anyMatch(SpecialDayCategory.HOLIDAY_GROUP::contains)) {
            upcomingCandidates.add(cacheService.findFirstPublicHolidayUpcoming(countryCodes, today));
        }
        if (categories.isEmpty() || categories.stream().anyMatch(SpecialDayCategory.ENTERTAINMENT_GROUP::contains)) {
            upcomingCandidates.add(cacheService.findFirstPublicEntertainmentUpcoming(countryCodes, today));
        }

        return upcomingCandidates.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(s -> categories.isEmpty() || categories.contains(s.getCategory()))
                .min(Comparator.comparing(SpecialDay::getDate));
    }

    public TodayResult getToday(CountryCode countryCode, Timezone timezone, List<SpecialDayCategory> categories) {
        return getToday(countryCode, timezone, categories, null);
    }

    public PastResult getPast(CountryCode countryCode, Timezone timezone,
                              List<SpecialDayCategory> categories, Long memberId) {
        LocalDate today = LocalDate.now(timezone.toZoneId());
        List<CountryCode> countryCodes = List.of(countryCode);

        // 공개 데이터에서 가장 최근 past 찾기
        Optional<SpecialDay> publicPast = findFirstPublicPast(countryCodes, today, categories);

        // 비공개 데이터에서 가장 최근 past 찾기
        Optional<SpecialDay> privatePast = Optional.empty();
        if (memberId != null) {
            privatePast = specialDayRepository.findFirstPrivatePast(countryCodes, today, memberId);
        }

        // 둘 중 더 최근 날짜 선택
        Optional<SpecialDay> pastEvent = Stream.of(publicPast, privatePast)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(SpecialDay::getDate));

        return pastEvent
                .map(entity -> PastResult.from(entity, today))
                .orElse(null);
    }

    private Optional<SpecialDay> findFirstPublicPast(List<CountryCode> countryCodes, LocalDate today,
                                                      List<SpecialDayCategory> categories) {
        List<Optional<SpecialDay>> pastCandidates = new ArrayList<>();

        if (categories.isEmpty() || categories.stream().anyMatch(SpecialDayCategory.HOLIDAY_GROUP::contains)) {
            pastCandidates.add(cacheService.findFirstPublicHolidayPast(countryCodes, today));
        }
        if (categories.isEmpty() || categories.stream().anyMatch(SpecialDayCategory.ENTERTAINMENT_GROUP::contains)) {
            pastCandidates.add(cacheService.findFirstPublicEntertainmentPast(countryCodes, today));
        }

        return pastCandidates.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(s -> categories.isEmpty() || categories.contains(s.getCategory()))
                .max(Comparator.comparing(SpecialDay::getDate));
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
    @Auditable(action = "특별일 생성")
    public SpecialDay createByMember(String name, SpecialDayCategory category, LocalDate date,
                                     LocalTime eventTime, Timezone eventTimeZone,
                                     CountryCode countryCode, String description,
                                     Long memberId, boolean isPublic) {
        return specialDayRepository.save(
                SpecialDay.createByMember(name, category, date, eventTime, eventTimeZone, countryCode, description, memberId, isPublic));
    }

    @Transactional
    @CheckOwnership
    @Auditable(action = "특별일 삭제")
    public void delete(Long id, Long memberId) {
        // 소유권 검증은 @CheckOwnership AOP에서 처리
        // JPA 1차 캐시로 인해 AOP에서 조회한 엔티티 재사용
        specialDayRepository.deleteById(id);
    }

    @Transactional
    @CheckOwnership
    @Auditable(action = "공개 설정 변경")
    public void toggleVisibility(Long id, Long memberId) {
        // 소유권 검증은 @CheckOwnership AOP에서 처리
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SpecialDayErrorCode.SPECIAL_DAY_NOT_FOUND));
        specialDay.toggleVisibility();
    }

    @Transactional
    @CheckOwnership
    @Auditable(action = "특별일 수정")
    public SpecialDay update(Long id, Long memberId, String name, SpecialDayCategory category,
                             LocalDate date, LocalTime eventTime, Timezone eventTimeZone,
                             CountryCode countryCode, String description, Boolean isPublic) {
        // 소유권 검증은 @CheckOwnership AOP에서 처리
        SpecialDay specialDay = specialDayRepository.findById(id)
                .orElseThrow(() -> new SpecialDayException(SpecialDayErrorCode.SPECIAL_DAY_NOT_FOUND));
        specialDay.update(name, category, date, eventTime, eventTimeZone, countryCode, description, isPublic);
        return specialDay;
    }
}
