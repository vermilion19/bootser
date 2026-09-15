package com.booster.dday.sky.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.locale.Lang;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LunarCalendarPort;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyKind;
import com.booster.dday.sky.application.SkyService;
import com.booster.dday.sky.web.dto.LunarDateResponse;
import com.booster.dday.sky.web.dto.SkyEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * 하늘 (B-1 ~ B-4) — <b>이 서비스의 첫 번째 표면이다</b> (SPEC §10.3).
 *
 * <pre>
 *   GET /api/v1/dday/sky/terms?year=2026        절기 스물넷
 *   GET /api/v1/dday/sky/terms/next             다음 절기와 D-day
 *   GET /api/v1/dday/sky/moons?year=2026        삭 · 망
 *   GET /api/v1/dday/sky/meteors?year=2026      유성우 극대
 *   GET /api/v1/dday/sky/meteors/next
 *   GET /api/v1/dday/sky/lunar?date=2026-02-17  양력 → 음력
 *   GET /api/v1/dday/sky/lunar/to-solar?year=2026&month=1&day=1
 * </pre>
 *
 * <h2>D-day 는 여기서 만든다</h2>
 *
 * <p>SPEC §9.9(2) 가 «캐시에는 오늘에 의존하지 않는 것만 담고, 오늘에 의존하는 값은
 * <b>조립하는 자리</b>에서 만든다» 로 닫았다. 여기가 그 자리다 — 캐시에는 UTC 시각이
 * 들어 있고, 「며칠 남았나」는 요청이 올 때 {@link DDayCalculator} 가 센다.
 *
 * <h2>시간대를 인자로 받는다</h2>
 *
 * <p>같은 순간이라도 나라마다 날짜가 다르다. 시간대 없이 D-day 를 세면 하루씩
 * 어긋나고, <b>그것이 정적 사이트가 못 고친 고장(E-1)</b>이다. 기본은 한국이되
 * {@code ?zone=} 으로 바꿀 수 있다.
 */
@RestController
@RequestMapping("/api/v1/dday/sky")
@RequiredArgsConstructor
public class SkyController {

    private final SkyService skyService;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @GetMapping("/terms")
    public ApiResponse<List<SkyEventResponse>> terms(
            @RequestParam int year,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        return list(skyService.terms(year), zone, lang, acceptLanguage);
    }

    @GetMapping("/terms/next")
    public ApiResponse<SkyEventResponse> nextTerm(
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        return one(SkyKind.TERMS, zone, lang, acceptLanguage);
    }

    @GetMapping("/moons")
    public ApiResponse<List<SkyEventResponse>> moons(
            @RequestParam int year,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        return list(skyService.moons(year), zone, lang, acceptLanguage);
    }

    @GetMapping("/meteors")
    public ApiResponse<List<SkyEventResponse>> meteors(
            @RequestParam int year,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        return list(skyService.meteors(year), zone, lang, acceptLanguage);
    }

    @GetMapping("/meteors/next")
    public ApiResponse<SkyEventResponse> nextMeteor(
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        return one(SkyKind.METEORS, zone, lang, acceptLanguage);
    }

    /** 양력 → 음력 (B-4) */
    @GetMapping("/lunar")
    public ApiResponse<LunarDateResponse> lunar(@RequestParam String date) {
        LocalDate solar = parseDate(date);
        LunarDate lunar = skyService.toLunar(solar, LunarCalendarPort.KST);

        return ApiResponse.success(LunarDateResponse.of(solar, lunar));
    }

    /**
     * 음력 → 양력 (B-4).
     *
     * <p>없는 날짜면 404 다. <b>있는 척 가까운 날을 돌려주지 않는다</b> — 윤달이
     * 아닌 해에 윤달을 물었거나 29일까지인 달의 30일을 물은 것이고, 그 사실이
     * 답이다.
     */
    @GetMapping("/lunar/to-solar")
    public ApiResponse<LunarDateResponse> toSolar(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int day,
            @RequestParam(required = false, defaultValue = "false") boolean leap) {

        LunarDate lunar = leap
                ? LunarDate.leap(year, month, day)
                : LunarDate.of(year, month, day);

        LocalDate solar = skyService.toSolar(lunar, LunarCalendarPort.KST)
                .orElseThrow(() -> new CoreException(DDayErrorCode.SKY_LUNAR_DATE_NOT_FOUND,
                        "%s 은 그 해에 없다".formatted(lunar)));

        return ApiResponse.success(LunarDateResponse.of(solar, lunar));
    }

    private ApiResponse<List<SkyEventResponse>> list(List<SkyEvent> events, String zone,
                                                     String lang, String acceptLanguage) {
        ZoneId resolved = parseZone(zone);
        Lang language = Lang.resolve(lang, acceptLanguage);
        Instant now = Instant.now(clock);

        return ApiResponse.success(events.stream()
                .map(event -> SkyEventResponse.of(event, language, resolved, now, dDayCalculator))
                .toList());
    }

    /**
     * 「다음」이 없을 수 있다. 그때는 <b>{@code data} 가 비어 있는 성공</b>이다 —
     * 404 가 아니다. 물어본 것(다음이 무엇인가)에 «없다» 로 답한 것이지 자원이
     * 없는 것이 아니다.
     */
    private ApiResponse<SkyEventResponse> one(SkyKind kind, String zone,
                                              String lang, String acceptLanguage) {
        ZoneId resolved = parseZone(zone);
        Lang language = Lang.resolve(lang, acceptLanguage);
        Instant now = Instant.now(clock);

        Optional<SkyEvent> next = skyService.next(kind, resolved);
        return next
                .map(event -> ApiResponse.success(
                        SkyEventResponse.of(event, language, resolved, now, dDayCalculator)))
                .orElseGet(ApiResponse::success);
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "날짜가 yyyy-MM-dd 가 아니다: " + raw);
        }
    }

    /**
     * 모르는 시간대를 <b>조용히 기본값으로 바꾸지 않는다.</b>
     *
     * <p>«Asia/Seoull» 을 한국으로 읽어 주면 사용자는 자기가 물은 시간대의 답을
     * 받았다고 믿는다. E-1 이 고치려던 것이 «조용히 하루 어긋남» 이므로 여기서
     * 조용히 넘어가면 같은 고장을 자리만 옮겨 다시 만드는 셈이다.
     */
    private static ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return LunarCalendarPort.KST;
        }
        try {
            return ZoneId.of(raw);
        } catch (RuntimeException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "모르는 시간대다: " + raw);
        }
    }
}
