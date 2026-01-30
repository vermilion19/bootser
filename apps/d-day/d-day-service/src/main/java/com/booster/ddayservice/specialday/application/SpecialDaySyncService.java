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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SpecialDaySyncService {

    private final NagerDateClient nagerDateClient;
    private final SpecialDayRepository specialDayRepository;

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