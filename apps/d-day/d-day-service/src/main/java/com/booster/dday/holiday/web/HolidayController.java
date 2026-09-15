package com.booster.dday.holiday.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.holiday.application.HolidayQueryService;
import com.booster.dday.holiday.application.dto.HolidayOnDateView;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.holiday.application.dto.LongWeekendYearView;
import com.booster.dday.holiday.web.dto.HolidayResponse;
import com.booster.dday.holiday.web.dto.LongWeekendResponse;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 공휴일 조회 (A-1 ~ A-4) — <b>읽기 바닥</b> (SPEC §10.2).
 *
 * <pre>
 *   GET /api/v1/dday/countries/{cc}/holidays?year=2026       그 해의 공휴일
 *   GET /api/v1/dday/countries/{cc}/holidays/next            다음 공휴일과 D-day
 *   GET /api/v1/dday/countries/{cc}/long-weekends?year=2026  황금연휴와 3분기
 *   GET /api/v1/dday/holidays/on/2026-12-25                  그 날 쉬는 나라 전부
 *   GET /api/v1/dday/countries                               국가 목록
 *   GET /api/v1/dday/countries/{cc}                          국가 메타
 * </pre>
 *
 * <h2>시간대를 인자로 안 받는다 — 나라가 정한다</h2>
 *
 * <p>하늘 조회와 다른 점이다. 국가 자원의 「오늘」은 <b>그 나라 대표 시간대의
 * 오늘</b>이다 (SPEC §10.8). 한국에서 일본 공휴일을 봐도 D-day 는 일본 시각으로
 * 세야 맞고, 그것이 정적 사이트가 못 고친 고장(E-1)이다.
 *
 * <p>시간대가 여럿이라 우리가 하나를 고른 나라는 <b>응답에 그 사실이 실린다</b>
 * ({@code zoneAmbiguous}) — 조용히 대표로 퉁치면 고장을 자리만 옮겨 다시 만든다.
 */
@RestController
@RequestMapping("/api/v1/dday")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayQueryService holidayQueryService;
    private final CountryReader countries;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    /** A-1 — 그 나라 그 해의 공휴일 */
    @GetMapping("/countries/{countryCode}/holidays")
    public ApiResponse<List<HolidayResponse>> holidays(@PathVariable String countryCode,
                                                       @RequestParam int year) {
        HolidayYearView view = holidayQueryService.holidaysOf(countryCode, year);
        ZoneId zone = holidayQueryService.zoneOf(countryCode);
        Instant now = Instant.now(clock);

        return ApiResponse.success(view.getHolidays().stream()
                .map(entry -> HolidayResponse.of(entry, zone, now, dDayCalculator))
                .toList());
    }

    /**
     * A-2 — 다음 공휴일 하나.
     *
     * <p><b>오늘도 「다음」에 든다.</b> 오늘이 공휴일이면 그것이 답이고 D-day 는 0 이다 —
     * 오늘 쉬는 날을 건너뛰고 다음 달을 가리키면 그게 고장이다.
     */
    @GetMapping("/countries/{countryCode}/holidays/next")
    public ApiResponse<HolidayResponse> nextHoliday(@PathVariable String countryCode) {
        ZoneId zone = holidayQueryService.zoneOf(countryCode);
        Instant now = Instant.now(clock);

        Optional<HolidayYearView.Entry> next = dDayCalculator.pickNext(
                holidayQueryService.upcomingOf(countryCode), zone, now);

        return next
                .map(entry -> ApiResponse.success(HolidayResponse.of(entry, zone, now, dDayCalculator)))
                .orElseGet(ApiResponse::success);
    }

    /** A-3 — 황금연휴. <b>3분기가 여기서 붙는다</b> (시작 전 · 연휴 중 · 끝난 뒤) */
    @GetMapping("/countries/{countryCode}/long-weekends")
    public ApiResponse<List<LongWeekendResponse>> longWeekends(@PathVariable String countryCode,
                                                               @RequestParam int year) {
        LongWeekendYearView view = holidayQueryService.longWeekendsOf(countryCode, year);
        ZoneId zone = holidayQueryService.zoneOf(countryCode);
        Instant now = Instant.now(clock);

        return ApiResponse.success(view.getLongWeekends().stream()
                .map(entry -> LongWeekendResponse.of(entry, zone, now, dDayCalculator))
                .toList());
    }

    /**
     * A-4 — 그 날 쉬는 나라 전부.
     *
     * <p>날짜가 <b>경로에 있다.</b> 기본값을 「오늘」로 두면 오늘에 의존하는 응답을
     * 캐시에 담게 되고, 자정이 지나면 어제 것이 나간다 (SPEC §9.9(2)).
     */
    @GetMapping("/holidays/on/{date}")
    public ApiResponse<HolidayOnDateView> onDate(@PathVariable String date) {
        return ApiResponse.success(holidayQueryService.onDate(parseDate(date)));
    }

    /** 국가 목록 — 204개. 나눌 만큼 크지 않다 (SPEC §10.8) */
    @GetMapping("/countries")
    public ApiResponse<List<com.booster.dday.country.api.CountryView>> countries() {
        return ApiResponse.success(countries.findAll());
    }

    /** 국가 메타 — 대표 시간대와 주말이 여기 있다 */
    @GetMapping("/countries/{countryCode}")
    public ApiResponse<com.booster.dday.country.api.CountryView> country(
            @PathVariable String countryCode) {

        return ApiResponse.success(countries.find(countryCode.toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new CoreException(DDayErrorCode.COUNTRY_NOT_FOUND,
                        "그 국가 코드는 다루지 않는다: " + countryCode)));
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "날짜가 yyyy-MM-dd 가 아니다: " + raw);
        }
    }
}
