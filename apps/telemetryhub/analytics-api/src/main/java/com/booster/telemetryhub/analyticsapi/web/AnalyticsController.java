package com.booster.telemetryhub.analyticsapi.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.telemetryhub.analyticsapi.application.query.AnalyticsQueryService;
import com.booster.telemetryhub.analyticsapi.web.dto.DrivingEventCounterResponse;
import com.booster.telemetryhub.analyticsapi.web.dto.EventsPerMinuteResponse;
import com.booster.telemetryhub.analyticsapi.web.dto.LatestDeviceResponse;
import com.booster.telemetryhub.analyticsapi.web.dto.RegionHeatmapResponse;
import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/analytics/v1")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    public AnalyticsController(AnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    @GetMapping("/devices/latest")
    public ApiResponse<List<LatestDeviceResponse>> latestDevices(
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        return ApiResponse.success(
                analyticsQueryService.getLatestDevices(limit).stream()
                        .map(LatestDeviceResponse::from)
                        .toList()
        );
    }

    @GetMapping("/devices/{deviceId}/latest")
    public ApiResponse<LatestDeviceResponse> latestDevice(
            @PathVariable String deviceId
    ) {
        return ApiResponse.success(
                LatestDeviceResponse.from(analyticsQueryService.getLatestDevice(deviceId))
        );
    }

    @GetMapping("/events-per-minute")
    public ApiResponse<List<EventsPerMinuteResponse>> eventsPerMinute(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) EventType eventType
    ) {
        return ApiResponse.success(
                analyticsQueryService.getEventsPerMinute(from, to, eventType).stream()
                        .map(EventsPerMinuteResponse::from)
                        .toList()
        );
    }

    @GetMapping("/driving-events/counters")
    public ApiResponse<List<DrivingEventCounterResponse>> drivingEventCounters(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) DrivingEventType drivingEventType
    ) {
        return ApiResponse.success(
                analyticsQueryService.getDrivingEventCounters(from, to, deviceId, drivingEventType).stream()
                        .map(DrivingEventCounterResponse::from)
                        .toList()
        );
    }

    @GetMapping("/heatmap/regions")
    public ApiResponse<List<RegionHeatmapResponse>> regionHeatmap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double maxLon
    ) {
        return ApiResponse.success(
                analyticsQueryService.getRegionHeatmap(from, to, minLat, maxLat, minLon, maxLon).stream()
                        .map(RegionHeatmapResponse::from)
                        .toList()
        );
    }
}
