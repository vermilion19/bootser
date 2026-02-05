package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.specialday.application.MovieSyncService;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService.SyncAllResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.web.dto.CreateSpecialDayRequest;
import com.booster.ddayservice.specialday.web.dto.SpecialDayResponse;
import com.booster.ddayservice.specialday.web.dto.SyncResultResponse;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Year;

import static com.booster.ddayservice.specialday.web.controller.SpecialDayParameterParser.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-days/admin")
public class SpecialDayAdminController {

    private final SpecialDaySyncService specialDaySyncService;
    private final SpecialDayService specialDayService;
    private final MovieSyncService movieSyncService;

    @PostMapping("/sync")
    @RateLimiter(name = "adminApi")
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
    @RateLimiter(name = "adminApi")
    public ApiResponse<MovieSyncService.MovieSyncResult> syncMovies(
            @RequestParam(defaultValue = "KR") String region
    ) {
        MovieSyncService.MovieSyncResult result = movieSyncService.syncUpcomingMovies(region);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/duplicates")
    @RateLimiter(name = "adminApi")
    public ApiResponse<java.util.Map<String, Integer>> removeDuplicates() {
        java.util.Map<String, Integer> result = specialDaySyncService.removeAllDuplicates();
        return ApiResponse.success(result);
    }
}
