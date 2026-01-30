package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import com.booster.ddayservice.specialday.web.dto.TodayResponse;
import com.booster.ddayservice.specialday.web.dto.CountryCodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-days")
public class SpecialDayController {

    private final SpecialDayService specialDayService;

    @GetMapping("/countries")
    public ApiResponse<List<CountryCodeResponse>> getCountryCodes(
            @RequestParam(required = false) String query
    ) {
        List<CountryCodeResponse> results = Arrays.stream(CountryCode.values())
                .filter(cc -> query == null || query.isBlank()
                        || cc.getDisplayName().toLowerCase().contains(query.toLowerCase()))
                .map(CountryCodeResponse::from)
                .toList();
        return ApiResponse.success(results);
    }

    @GetMapping("/today")
    public ApiResponse<TodayResponse> getToday(
            @RequestParam(defaultValue = "KR") String countryCode,
            @RequestParam(defaultValue = "UTC") String timezone
    ) {
        CountryCode country = parseCountryCode(countryCode);
        Timezone tz = parseTimezone(timezone);

        TodayResult result = specialDayService.getToday(country, tz);
        return ApiResponse.success(TodayResponse.from(result));
    }

    private CountryCode parseCountryCode(String countryCode) {
        try {
            return CountryCode.valueOf(countryCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_COUNTRY_CODE,
                    "유효하지 않은 국가 코드: " + countryCode);
        }
    }

    private Timezone parseTimezone(String timezone) {
        try {
            String enumName = timezone.replace("/", "_").replace("-", "_").toUpperCase();
            return Timezone.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_TIMEZONE,
                    "유효하지 않은 타임존: " + timezone);
        }
    }
}
