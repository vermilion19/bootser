package com.booster.ddayservice.config;

import com.booster.ddayservice.specialday.application.SpecialDaySyncService;
import com.booster.ddayservice.specialday.domain.CountryCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Year;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecialDayDataInitializer implements ApplicationRunner {

    private final SpecialDaySyncService specialDaySyncService;

    @Override
    public void run(ApplicationArguments args) {
        int currentYear = Year.now().getValue();
        log.info("공휴일 초기 동기화 시작: year={}", currentYear);

        try {
            int saved = specialDaySyncService.syncByYear(currentYear, CountryCode.KR);
            log.info("KR 공휴일 동기화 완료: {}건 저장", saved);
        } catch (Exception e) {
            log.warn("초기 동기화 실패 (서비스는 정상 기동): {}", e.getMessage());
        }
    }
}
