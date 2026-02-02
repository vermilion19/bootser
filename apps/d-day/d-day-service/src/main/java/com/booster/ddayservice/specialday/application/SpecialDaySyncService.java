package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.infrastructure.NagerDateClient;
import com.booster.ddayservice.specialday.infrastructure.NagerHolidayDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SpecialDaySyncService {

    private final NagerDateClient nagerDateClient;
    private final SpecialDayRepository specialDayRepository;

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

    public int syncByYear(int year, CountryCode countryCode) {
        log.info("공휴일 동기화 시작: year={}, country={}", year, countryCode);

        List<NagerHolidayDto> holidays = nagerDateClient.getPublicHolidays(year, countryCode);

        int savedCount = 0;
        for (NagerHolidayDto holiday : holidays) {
            SpecialDay entity = holiday.toEntity(countryCode);

            if (specialDayRepository.existsByCountryCodeAndDateAndName(
                    countryCode, entity.getDate(), entity.getName())) {
                continue;
            }

            specialDayRepository.save(entity);
            savedCount++;
        }

        log.info("공휴일 동기화 완료: year={}, country={}, saved={}/total={}",
                year, countryCode, savedCount, holidays.size());
        return savedCount;
    }
}