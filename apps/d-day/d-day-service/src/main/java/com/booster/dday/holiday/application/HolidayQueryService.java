package com.booster.dday.holiday.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.config.HolidaySyncProperties;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.dto.HolidayOnDateView;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.holiday.application.dto.LongWeekendYearView;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 공휴일 조회 (A-1 ~ A-4) — <b>이 서비스의 읽기 바닥.</b>
 *
 * <p>담는 것은 전부 <b>「오늘에 독립」이고 「언어 중립」</b>이다 (ARCHITECTURE §4.2).
 * D-day 도 3분기도 한국어 라벨도 여기 없고, 전부 응답을 만드는 자리에서 붙는다.
 * 그렇게 해야 캐시 한 벌이 모든 시간대와 모든 언어에 쓰인다.
 *
 * <h2>정의역을 캐시에 닿기 전에 자른다</h2>
 *
 * <p>사용자가 아무 날짜나 넣을 수 있다 ({@code /holidays/on/{date}}). 그대로 캐시에
 * 태우면 <b>크롤러 한 마리로 Redis 가 쓰레기로 찬다</b> (§4.1). 담고 있는 연도
 * 범위 밖은 400 이고, <b>본문에 보유 범위를 실어 준다.</b>
 *
 * <p>없는 국가 코드는 404 다. 코드 자체가 캐시 키의 일부라 그것도 유한 집합에서
 * 와야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayQueryService {

    private final HolidayRepository holidays;
    private final LongWeekendRepository longWeekends;
    private final CountryReader countries;
    private final VersionedCache cache;
    private final HolidaySyncProperties properties;
    private final Clock clock;

    /** 그 나라 그 해의 공휴일 (A-1) */
    public HolidayYearView holidaysOf(String countryCode, int year) {
        CountryView country = requireCountry(countryCode);
        requireYearInCoverage(year);

        return yearView(country.code(), year);
    }

    /**
     * 그 나라의 다음 공휴일 (A-2). <b>두 해를 읽는다</b> (ARCHITECTURE §4.5).
     *
     * <p>12월 20일에 물으면 답이 내년 1월에 있다. 올해만 읽으면 {@code empty} 가
     * 나오고 <b>그것은 조용한 고장</b>이다.
     *
     * <p>「올해」는 <b>그 나라 대표 시간대의 오늘</b>이 속한 해다 (E-1). UTC 의
     * 해가 아니다 — 이 한 줄이 E-1 을 조립 단계에 박는다.
     */
    public List<HolidayYearView.Entry> upcomingOf(String countryCode) {
        CountryView country = requireCountry(countryCode);
        int thisYear = LocalDate.now(clock.withZone(zoneOf(country))).getYear();

        List<HolidayYearView.Entry> candidates =
                new java.util.ArrayList<>(yearViewQuietly(country.code(), thisYear));
        candidates.addAll(yearViewQuietly(country.code(), thisYear + 1));
        return candidates;
    }

    /** 그 나라 그 해의 황금연휴 (A-3). 3분기는 응답에서 붙는다 */
    public LongWeekendYearView longWeekendsOf(String countryCode, int year) {
        CountryView country = requireCountry(countryCode);
        requireYearInCoverage(year);

        LongWeekendYearView cached = cache.getOrLoad(CacheName.HOLIDAY_LONG_WEEKEND,
                country.code() + ":" + year, LongWeekendYearView.class,
                () -> buildLongWeekends(country.code(), year));

        return cached == null
                ? LongWeekendYearView.of(country.code(), year, List.of())
                : cached;
    }

    /**
     * 그 날 쉬는 나라 전부 (A-4).
     *
     * <p>SPEC §9.9(2) 가 «{@code date} 는 필수, 없으면 400» 으로 닫았다. 기본값을
     * 「오늘」로 두면 <b>오늘에 의존하는 응답을 캐시에 담게 되고</b>, 자정이 지나면
     * 어제 것이 나간다.
     */
    public HolidayOnDateView onDate(LocalDate date) {
        requireYearInCoverage(date.getYear());

        HolidayOnDateView cached = cache.getOrLoad(CacheName.HOLIDAY_ON_DATE, date.toString(),
                HolidayOnDateView.class, () -> buildOnDate(date));

        return cached == null ? HolidayOnDateView.of(date, List.of()) : cached;
    }

    /** 그 나라의 대표 시간대 (E-1) */
    public ZoneId zoneOf(String countryCode) {
        return zoneOf(requireCountry(countryCode));
    }

    private static ZoneId zoneOf(CountryView country) {
        return ZoneId.of(country.zoneId());
    }

    private HolidayYearView yearView(String countryCode, int year) {
        HolidayYearView cached = cache.getOrLoad(CacheName.HOLIDAY_YEAR,
                countryCode + ":" + year, HolidayYearView.class,
                () -> buildYear(countryCode, year));

        return cached == null ? HolidayYearView.of(countryCode, year, List.of()) : cached;
    }

    /**
     * 「다음」을 고르느라 읽는 이듬해. <b>보유 범위 밖이면 빈손으로 넘어간다.</b>
     *
     * <p>여기서 터뜨리면 <b>보유 마지막 해의 12월에 「다음 공휴일」이 통째로 막힌다.</b>
     */
    private List<HolidayYearView.Entry> yearViewQuietly(String countryCode, int year) {
        try {
            requireYearInCoverage(year);
            return yearView(countryCode, year).getHolidays();
        } catch (CoreException e) {
            return List.of();
        }
    }

    private HolidayYearView buildYear(String countryCode, int year) {
        List<Holiday> found = holidays.findPublicAliveOf(countryCode, (short) year);
        if (found.isEmpty()) {
            return null;
        }
        return HolidayYearView.of(countryCode, year, found.stream()
                .map(HolidayQueryService::toEntry)
                .toList());
    }

    private LongWeekendYearView buildLongWeekends(String countryCode, int year) {
        List<LongWeekend> found = longWeekends.findAliveOf(countryCode, (short) year);
        if (found.isEmpty()) {
            return null;
        }
        return LongWeekendYearView.of(countryCode, year, found.stream()
                .map(weekend -> new LongWeekendYearView.Entry(
                        weekend.getStartDate(),
                        weekend.getEndDate(),
                        weekend.isNeedBridge(),
                        weekend.getBridgeDays().days()))
                .toList());
    }

    private HolidayOnDateView buildOnDate(LocalDate date) {
        List<Holiday> found = holidays.findPublicAliveOn(date);
        if (found.isEmpty()) {
            return null;
        }
        return HolidayOnDateView.of(date, found.stream()
                .map(holiday -> new HolidayOnDateView.Entry(
                        holiday.getCountryCode(),
                        holiday.getNameEn(),
                        holiday.getNameSlug().value(),
                        holiday.isGlobal()))
                .toList());
    }

    private static HolidayYearView.Entry toEntry(Holiday holiday) {
        return new HolidayYearView.Entry(
                holiday.getDate(),
                holiday.getNameEn(),
                holiday.getNameLocal(),
                holiday.getNameSlug().value(),
                holiday.isGlobal(),
                holiday.getSubdivisionKey().codes(),
                holiday.getLaunchYear() == null ? null : (int) holiday.getLaunchYear());
    }

    private CountryView requireCountry(String countryCode) {
        String normalized = countryCode == null ? "" : countryCode.trim().toUpperCase(java.util.Locale.ROOT);

        return countries.find(normalized)
                .orElseThrow(() -> new CoreException(DDayErrorCode.COUNTRY_NOT_FOUND,
                        "그 국가 코드는 다루지 않는다: " + countryCode));
    }

    /**
     * 담고 있는 연도 범위 밖이면 <b>보유 범위를 실어</b> 거절한다.
     *
     * <p>이 범위는 동기화 설정에서 온다 — 담은 것과 답할 수 있는 것이 같아야
     * 하므로 두 곳에 적지 않는다.
     */
    private void requireYearInCoverage(int year) {
        List<Integer> years = properties.years(LocalDate.now(clock).getYear());

        if (!years.contains(year)) {
            throw new CoreException(DDayErrorCode.DATE_OUT_OF_COVERAGE,
                    "%d년은 담고 있지 않다. 지금 담은 해: %d~%d"
                            .formatted(year, years.get(0), years.get(years.size() - 1)));
        }
    }
}
