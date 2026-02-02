package com.booster.ddayservice.specialday.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService;
import com.booster.ddayservice.specialday.application.SpecialDaySyncService.SyncAllResult;
import com.booster.ddayservice.specialday.web.dto.SyncResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-days/admin")
public class SpecialDayAdminController {

    private final SpecialDaySyncService specialDaySyncService;

    @PostMapping("/sync")
    public ApiResponse<SyncResultResponse> syncAll(
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = (year != null) ? year : Year.now().getValue();
        SyncAllResult result = specialDaySyncService.syncAll(targetYear);
        return ApiResponse.success(SyncResultResponse.from(result));
    }
}
