package com.booster.telemetryhub.ingestion.web;

import com.booster.core.webflux.response.ApiResponse;
import com.booster.telemetryhub.ingestion.application.IngestionMessage;
import com.booster.telemetryhub.ingestion.application.IngestionService;
import com.booster.telemetryhub.ingestion.application.NormalizedRawEvent;
import com.booster.telemetryhub.ingestion.web.dto.IngestMessageRequest;
import com.booster.telemetryhub.ingestion.web.dto.IngestionMetricsResponse;
import com.booster.telemetryhub.ingestion.web.dto.NormalizedRawEventResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/ingestion/v1")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/messages")
    public Mono<ApiResponse<NormalizedRawEventResponse>> ingest(@Valid @RequestBody IngestMessageRequest request) {
        NormalizedRawEvent normalized = ingestionService.ingest(
                new IngestionMessage(request.topic(), request.qos(), request.payload(), Instant.now())
        );
        return Mono.just(ApiResponse.success(NormalizedRawEventResponse.from(normalized)));
    }

    @GetMapping("/metrics")
    public Mono<ApiResponse<IngestionMetricsResponse>> metrics() {
        return Mono.just(ApiResponse.success(IngestionMetricsResponse.from(ingestionService.metrics())));
    }

    @GetMapping("/events")
    public Mono<ApiResponse<List<NormalizedRawEventResponse>>> recentEvents(
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        List<NormalizedRawEventResponse> response = ingestionService.recentEvents(limit).stream()
                .map(NormalizedRawEventResponse::from)
                .toList();
        return Mono.just(ApiResponse.success(response));
    }

    @PostMapping("/events/clear")
    public Mono<ApiResponse<Void>> clearEvents() {
        ingestionService.clearRecentEvents();
        return Mono.just(ApiResponse.success());
    }
}
