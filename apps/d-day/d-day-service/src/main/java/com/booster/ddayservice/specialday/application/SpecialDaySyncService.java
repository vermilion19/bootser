package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.aop.LogExecutionTime;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.infrastructure.NagerDateClient;
import com.booster.ddayservice.specialday.infrastructure.NagerHolidayDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SpecialDaySyncService {

    private final NagerDateClient nagerDateClient;
    private final SpecialDayRepository specialDayRepository;

    @LogExecutionTime
    public SyncAllResult syncAll(int year) {
        log.info("전체 국가 공휴일 동기화 시작: year={}", year);

        Map<String, Integer> successCounts = new LinkedHashMap<>();
        List<String> failedCountries = new ArrayList<>();

        for (CountryCode countryCode : CountryCode.values()) {
            try {
                int saved = syncByYear(year, countryCode);
                successCounts.put(countryCode.name(), saved);
            } catch (Exception e) {
                log.warn("공휴일 동기화 실패: country={}, error={}", countryCode, e.getMessage());
                failedCountries.add(countryCode.name());
            }
        }

        int totalSaved = successCounts.values().stream().mapToInt(Integer::intValue).sum();
        log.info("전체 국가 공휴일 동기화 완료: 성공={}, 실패={}, 총 저장={}",
                successCounts.size(), failedCountries.size(), totalSaved);

        return new SyncAllResult(totalSaved, successCounts.size(), failedCountries.size(), successCounts, failedCountries);
    }

    public record SyncAllResult(
            int totalSaved,
            int successCount,
            int failedCount,
            Map<String, Integer> savedPerCountry,
            List<String> failedCountries
    ) {}

    @LogExecutionTime
    @Caching(evict = {
            @CacheEvict(value = "special-days-read", allEntries = true),
            @CacheEvict(value = "external-holidays", key = "#year + ':' + #countryCode")
    })
    public int syncByYear(int year, CountryCode countryCode) {
        log.info("공휴일 동기화 시작: year={}, country={}", year, countryCode);

        List<NagerHolidayDto> holidays = nagerDateClient.getPublicHolidays(year, countryCode);

        if (holidays.isEmpty()) {
            return 0;
        }

        // 해당 연도의 기존 날짜+이름 조합을 한 번에 조회 (Bulk 조회로 성능 최적화)
        Set<String> existingKeys = specialDayRepository.findDateNameKeysByCountryCodeAndDateBetween(
                countryCode,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );

        // 날짜+이름 조합으로 중복 필터링 (h.date()는 String, localName 사용)
        List<SpecialDay> newHolidays = holidays.stream()
                .filter(h -> !existingKeys.contains(h.date() + ":" + h.localName()))
                .map(h -> h.toEntity(countryCode))
                .toList();

        if (!newHolidays.isEmpty()) {
            specialDayRepository.saveAll(newHolidays);
        }

        log.info("공휴일 동기화 완료: year={}, country={}, saved={}/total={}",
                year, countryCode, newHolidays.size(), holidays.size());
        return newHolidays.size();
    }

    /**
     * 국가별 중복 데이터 삭제 (같은 국가, 같은 날짜, 같은 이름이면 하나만 남기고 삭제)
     */
    public int removeDuplicates(CountryCode countryCode) {
        log.info("중복 데이터 삭제 시작: country={}", countryCode);

        List<String> duplicateKeys = specialDayRepository.findDuplicateDateNameKeysByCountryCode(countryCode);
        int totalDeleted = 0;

        for (String key : duplicateKeys) {
            String[] parts = key.split(":", 2);
            LocalDate date = LocalDate.parse(parts[0]);
            String name = parts[1];

            var keepEntity = specialDayRepository.findFirstByCountryCodeAndDateAndName(countryCode, date, name);
            if (keepEntity.isPresent()) {
                int deleted = specialDayRepository.deleteDuplicatesByCountryCodeAndDateAndName(
                        countryCode, date, name, keepEntity.get().getId());
                totalDeleted += deleted;
                log.debug("중복 삭제: country={}, date={}, name={}, deleted={}", countryCode, date, name, deleted);
            }
        }

        log.info("중복 데이터 삭제 완료: country={}, totalDeleted={}", countryCode, totalDeleted);
        return totalDeleted;
    }

    /**
     * 전체 국가 중복 데이터 삭제
     */
    public Map<String, Integer> removeAllDuplicates() {
        log.info("전체 국가 중복 데이터 삭제 시작");

        Map<String, Integer> result = new LinkedHashMap<>();
        for (CountryCode countryCode : CountryCode.values()) {
            int deleted = removeDuplicates(countryCode);
            if (deleted > 0) {
                result.put(countryCode.name(), deleted);
            }
        }

        log.info("전체 국가 중복 데이터 삭제 완료: {}", result);
        return result;
    }
}