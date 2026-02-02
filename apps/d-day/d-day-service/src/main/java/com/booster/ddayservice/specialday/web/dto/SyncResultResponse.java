package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.application.SpecialDaySyncService.SyncAllResult;

import java.util.List;
import java.util.Map;

public record SyncResultResponse(
        int totalSaved,
        int successCount,
        int failedCount,
        Map<String, Integer> savedPerCountry,
        List<String> failedCountries
) {
    public static SyncResultResponse from(SyncAllResult result) {
        return new SyncResultResponse(
                result.totalSaved(),
                result.successCount(),
                result.failedCount(),
                result.savedPerCountry(),
                result.failedCountries()
        );
    }
}
