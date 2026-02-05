package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.auth.web.CurrentMemberId;
import com.booster.ddayservice.specialday.application.SpecialDayService;
import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDay;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

import static com.booster.ddayservice.specialday.web.controller.SpecialDayParameterParser.*;

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
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) List<SpecialDayCategory> category,
            @CurrentMemberId(required = false) Long memberId
    ) {
        CountryCode country = parseCountryCode(countryCode);
        Timezone tz = timezone != null ? parseTimezone(timezone) : country.getDefaultTimezone();
        List<SpecialDayCategory> categories = category != null ? category : List.of();

        TodayResult result = specialDayService.getToday(country, tz, categories, memberId);
        return ApiResponse.success(TodayResponse.from(result));
    }

    @GetMapping("/past")
    public ResponseEntity<ApiResponse<PastResponse>> getPast(
            @RequestParam(defaultValue = "KR") String countryCode,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) List<SpecialDayCategory> category,
            @CurrentMemberId(required = false) Long memberId
    ) {
        CountryCode country = parseCountryCode(countryCode);
        Timezone tz = timezone != null ? parseTimezone(timezone) : country.getDefaultTimezone();
        List<SpecialDayCategory> categories = category != null ? category : List.of();

        PastResult result = specialDayService.getPast(country, tz, categories, memberId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(ApiResponse.success(PastResponse.from(result)));
    }

    @PostMapping
    public ApiResponse<SpecialDayResponse> create(
            @Valid @RequestBody CreateSpecialDayRequest request,
            @CurrentMemberId Long memberId
    ) {
        CountryCode country = parseCountryCode(request.countryCode());
        Timezone tz = request.timezone() != null ? parseTimezone(request.timezone()) : country.getDefaultTimezone();
        SpecialDayCategory cat = parseCategory(request.category());

        boolean isPublic = request.isPublic() != null ? request.isPublic() : true;

        SpecialDay created = specialDayService.createByMember(
                request.name(), cat, request.date(), request.eventTime(),
                tz, country, request.description(), memberId, isPublic);

        return ApiResponse.success(SpecialDayResponse.from(created));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @CurrentMemberId Long memberId
    ) {
        specialDayService.delete(id, memberId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<Void> toggleVisibility(
            @PathVariable Long id,
            @CurrentMemberId Long memberId
    ) {
        specialDayService.toggleVisibility(id, memberId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    public ApiResponse<SpecialDayResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSpecialDayRequest request,
            @CurrentMemberId Long memberId
    ) {
        CountryCode country = request.countryCode() != null ? parseCountryCode(request.countryCode()) : null;
        Timezone tz = request.timezone() != null ? parseTimezone(request.timezone()) : null;
        SpecialDayCategory cat = request.category() != null ? parseCategory(request.category()) : null;

        SpecialDay updated = specialDayService.update(
                id, memberId, request.name(), cat, request.date(), request.eventTime(),
                tz, country, request.description(), request.isPublic());

        return ApiResponse.success(SpecialDayResponse.from(updated));
    }
}
