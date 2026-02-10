package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.SpecialDayRepository;
import com.booster.ddayservice.specialday.infrastructure.TheSportsDbClient;
import com.booster.ddayservice.specialday.infrastructure.TheSportsDbEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SportsSyncService {

    private static final int DEFAULT_SYNC_DAYS = 7;

    private static final Map<String, CountryCode> COUNTRY_MAPPING = Map.ofEntries(
            Map.entry("South Korea", CountryCode.KR),
            Map.entry("Korea Republic", CountryCode.KR),
            Map.entry("Japan", CountryCode.JP),
            Map.entry("United States", CountryCode.US),
            Map.entry("England", CountryCode.GB),
            Map.entry("United Kingdom", CountryCode.GB),
            Map.entry("Spain", CountryCode.ES),
            Map.entry("Germany", CountryCode.DE),
            Map.entry("France", CountryCode.FR),
            Map.entry("Italy", CountryCode.IT),
            Map.entry("Brazil", CountryCode.BR),
            Map.entry("Argentina", CountryCode.AR),
            Map.entry("Australia", CountryCode.AU),
            Map.entry("Canada", CountryCode.CA),
            Map.entry("China", CountryCode.CN),
            Map.entry("Netherlands", CountryCode.NL),
            Map.entry("Portugal", CountryCode.PT),
            Map.entry("Belgium", CountryCode.BE),
            Map.entry("Mexico", CountryCode.MX),
            Map.entry("Turkey", CountryCode.TR)
    );

    private final TheSportsDbClient theSportsDbClient;
    private final SpecialDayRepository specialDayRepository;

    public SportsSyncResult syncUpcomingEvents(int days) {
        int syncDays = days > 0 ? days : DEFAULT_SYNC_DAYS;
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(syncDays - 1);

        log.info("스포츠 이벤트 동기화 시작: {} ~ {} ({}일)", from, to, syncDays);

        List<TheSportsDbEventDto> events = theSportsDbClient.getEventsByDateRange(from, to);

        int savedCount = 0;
        int skippedCount = 0;

        Set<String> existingKeys = specialDayRepository.findDateNameKeysByCountryCodeAndDateBetween(
                null, from, to);

        for (TheSportsDbEventDto event : events) {
            if (event.dateEvent() == null || event.dateEvent().isBlank()) {
                skippedCount++;
                continue;
            }

            LocalDate eventDate;
            try {
                eventDate = LocalDate.parse(event.dateEvent());
            } catch (DateTimeParseException e) {
                log.warn("스포츠 이벤트 날짜 파싱 실패: event={}, date={}", event.eventName(), event.dateEvent());
                skippedCount++;
                continue;
            }

            CountryCode countryCode = resolveCountryCode(event.country());

            String eventName = event.eventName();
            if (specialDayRepository.existsByCountryCodeAndDateAndName(countryCode, eventDate, eventName)) {
                skippedCount++;
                continue;
            }

            LocalTime eventTime = parseEventTime(event.time());

            String description = buildDescription(event);

            SpecialDay specialDay = SpecialDay.of(
                    eventName,
                    SpecialDayCategory.SPORTS,
                    eventDate,
                    eventTime,
                    countryCode.getDefaultTimezone(),
                    countryCode,
                    description
            );

            specialDayRepository.save(specialDay);
            savedCount++;
        }

        log.info("스포츠 이벤트 동기화 완료: saved={}, skipped={}, total={}", savedCount, skippedCount, events.size());
        return new SportsSyncResult(savedCount, skippedCount, events.size());
    }

    private CountryCode resolveCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return CountryCode.KR;
        }
        return COUNTRY_MAPPING.getOrDefault(country, CountryCode.KR);
    }

    private LocalTime parseEventTime(String time) {
        if (time == null || time.isBlank() || "00:00:00".equals(time)) {
            return null;
        }
        try {
            return LocalTime.parse(time);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String buildDescription(TheSportsDbEventDto event) {
        StringBuilder sb = new StringBuilder();
        if (event.sport() != null) {
            sb.append("[").append(event.sport()).append("] ");
        }
        if (event.league() != null) {
            sb.append(event.league());
        }
        if (event.venue() != null) {
            sb.append(" @ ").append(event.venue());
        }
        String desc = sb.toString().trim();
        return desc.isEmpty() ? null : desc;
    }

    public record SportsSyncResult(int saved, int skipped, int total) {}
}
