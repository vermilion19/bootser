package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.auth.web.CurrentMemberId;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.dto.TodayResultV2;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.web.dto.TodayResponseV2;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.booster.ddayservice.specialday.web.controller.SpecialDayParameterParser.parseCountryCode;
import static com.booster.ddayservice.specialday.web.controller.SpecialDayParameterParser.parseTimezone;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/special-days")
public class SpecialDayControllerV2 {

    private final SpecialDayService specialDayService;

    @GetMapping("/today")
    public ApiResponse<TodayResponseV2> getToday(
            @RequestParam(defaultValue = "KR") String countryCode,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) List<SpecialDayCategory> category,
            @CurrentMemberId(required = false) Long memberId
    ) {
        CountryCode country = parseCountryCode(countryCode);
        Timezone tz = timezone != null ? parseTimezone(timezone) : country.getDefaultTimezone();
        List<SpecialDayCategory> categories = category != null ? category : List.of();

        TodayResultV2 result = specialDayService.getTodayV2(country, tz, categories, memberId);
        return ApiResponse.success(TodayResponseV2.from(result));
    }
}
