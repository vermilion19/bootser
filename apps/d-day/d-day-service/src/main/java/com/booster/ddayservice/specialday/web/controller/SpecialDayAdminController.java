package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.specialday.application.MovieSyncService;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService.SyncAllResult;
import com.booster.ddayservice.specialday.domain.*;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import com.booster.ddayservice.specialday.web.dto.CreateSpecialDayRequest;
import com.booster.ddayservice.specialday.web.dto.SpecialDayResponse;
import com.booster.ddayservice.specialday.web.dto.SyncResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Year;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-days/admin")
public class SpecialDayAdminController {

    private final SpecialDaySyncService specialDaySyncService;
    private final SpecialDayService specialDayService;
    private final MovieSyncService movieSyncService;

    @PostMapping("/sync")
    public ApiResponse<SyncResultResponse> syncAll(
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = (year != null) ? year : Year.now().getValue();
        SyncAllResult result = specialDaySyncService.syncAll(targetYear);
        return ApiResponse.success(SyncResultResponse.from(result));
    }

    @PostMapping
    public ApiResponse<SpecialDayResponse> create(
            @Valid @RequestBody CreateSpecialDayRequest request
    ) {
        CountryCode country = parseCountryCode(request.countryCode());
        Timezone tz = request.timezone() != null ? parseTimezone(request.timezone()) : country.getDefaultTimezone();
        SpecialDayCategory cat = parseCategory(request.category());

        SpecialDay created = specialDayService.createByAdmin(
                request.name(), cat, request.date(), request.eventTime(),
                tz, country, request.description());

        return ApiResponse.success(SpecialDayResponse.from(created));
    }

    @PostMapping("/sync/movies")
    public ApiResponse<MovieSyncService.MovieSyncResult> syncMovies(
            @RequestParam(defaultValue = "KR") String region
    ) {
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies(region);
        return ApiResponse.success(result);
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

    private SpecialDayCategory parseCategory(String category) {
        try {
            return SpecialDayCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_COUNTRY_CODE,
                    "유효하지 않은 카테고리: " + category);
        }
    }
}
